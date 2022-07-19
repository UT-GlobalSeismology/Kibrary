package io.github.kensuke1984.kibrary.external;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * <p>
 * Utility class to handle with taup_time in TauP package
 * </p>
 *
 * taup_time must be in PATH and it must run correctly.<br>
 * All the standard output and errors will go to the bit bucket
 *
 * PREM is used for travel times.
 *
 * @version 0.3.2.1
 * @see <a href=http://www.seis.sc.edu/taup/>TauP</a>
 *
 *
 * TODO phase
 *
 * @author Kensuke Konishi
 *
 */
public final class TauPPierceReader {
    private TauPPierceReader() {}

    private static final String cmdName = "taup_pierce";

    static {
        try {
            checkExistence();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static void checkExistence() throws RuntimeException {
        if (!ExternalProcess.isInPath(cmdName))
            throw new RuntimeException(cmdName + " is not in PATH");
    }

    /**
     * Gets information of turning points of specified phases.
     * @param eventPositionF (FullPosition)
     * @param stationPositionH (HorizontalPosition)
     * @param modelName (String) Name of structure model to use
     * @param phaseSet (Set of Phase) Phases to calculate for
     * @return (List of Info) MAY BE NULL
     */
    public static Info getTurningInfo(FullPosition eventPositionF, HorizontalPosition stationPositionH, String modelName, Set<Phase> phaseSet) {
        List<String> lines = operate(makeCMDForTurningInfo(eventPositionF, stationPositionH, phaseSet, modelName));

        if (lines == null || lines.size() == 0) {
            return null;
        } else if (lines.size() != 2) {
            throw new UnsupportedOperationException("Phases with multiple turning points not supported");
        }
        String[] tmpLines = lines.toArray(new String[0]);
        return new Info(tmpLines);
    }

    /**
     * Gets information of turning points of specified phases.
     * @param eventPositionF (FullPosition)
     * @param stationPositionH (HorizontalPosition)
     * @param modelName (String) Name of structure model to use
     * @param phases (Phase...) Phases to calculate for
     * @return (List of Info)
     */
    public static Info getTurningInfo(FullPosition eventPositionF, HorizontalPosition stationPositionH, String modelName, Phase... phases) {
        Set<Phase> phaseSet = new HashSet<>(Arrays.asList(phases));
        return getTurningInfo(eventPositionF, stationPositionH, modelName, phaseSet);
    }

    /**
     * Gets information of piercing points at a specified depth, in addition to turning points, of specified phases.
     * @param eventPositionF (FullPosition)
     * @param stationPositionH (HorizontalPosition)
     * @param modelName (String) Name of structure model to use
     * @param pierceDepth (double) Depth of piercing point
     * @param phases (Phase...) Phases to calculate for
     * @return (List:Info)
     */
    public static Info getPierceInfo(FullPosition eventPositionF, HorizontalPosition stationPositionH, String modelName, double pierceDepth, Phase... phases) {
        Set<Phase> phaseSet = new HashSet<>(Arrays.asList(phases));

        List<String> lines = operate(makeCMDForPierceInfo(eventPositionF, stationPositionH, phaseSet, modelName, pierceDepth));

        if (lines == null || lines.size() == 0) {
            return null;
        } else if (lines.size() != 4) {
            throw new UnsupportedOperationException("Phases that do not have 1 enter, turning, and leaving points each are not supported");
        }
        String[] tmpLines = lines.toArray(new String[0]);
        return new Info(tmpLines, pierceDepth);
    }

    private static List<String> operate(String cmd) {
        try {
            // launch process
            ExternalProcess process = ExternalProcess.launch(cmd, Paths.get(""));
            // return the output
            return process.getStandardOutputThread().waitAndGetStringList();
        } catch (Exception e) {
            System.err.println("Error occured; could not find the pierce points");
            e.printStackTrace();
            return null;
        }
    }

    private static String makeCMDForTurningInfo(FullPosition eventPositionF, HorizontalPosition stationPositionH, Set<Phase> phases, String modelName) {
        String phase = phases.stream().map(Object::toString).collect(Collectors.joining(","));
        String cmd = cmdName
                + " -h " + eventPositionF.getDepth()
                + " -evt " + eventPositionF.getLatitude() + " " + eventPositionF.getLongitude()
                + " -sta " + stationPositionH.getLatitude() + " " + stationPositionH.getLongitude()
                + " -model " + modelName
                + " -ph " + phase
                + " -turn -nodiscon";
        return cmd;
    }

    private static String makeCMDForPierceInfo(FullPosition eventPositionF, HorizontalPosition stationPositionH, Set<Phase> phases, String modelName, double pierceDepth) {
        String phase = phases.stream().map(Object::toString).collect(Collectors.joining(","));
        String cmd = cmdName
                + " -h " + eventPositionF.getDepth()
                + " -evt " + eventPositionF.getLatitude() + " " + eventPositionF.getLongitude()
                + " -sta " + stationPositionH.getLatitude() + " " + stationPositionH.getLongitude()
                + " -model " + modelName
                + " -ph " + phase
                + " -pierce " + pierceDepth
                + " -turn -nodiscon";
        return cmd;
    }

    public static class Info {
        private Phase phase;
        private double travelTime;
        private double distance;
        private FullPosition turningPoint;
        private FullPosition leavePoint;
        private FullPosition enterPoint;

        public Info(String[] lines, double pierceDepth) {
            if (lines.length == 4)
                parseOutputDppPierce(lines);
            else
                parseOutputS(lines, pierceDepth);
        }

        public Info(String[] lines) {
            if (lines.length == 2)
                parseOutputDppTurn(lines);
            else
                throw new RuntimeException("Error: Specify a pierce depth");
        }

        private void parseOutputDppPierce(String[] lines) {
            String[] parts0 = lines[0].trim().split("\\s+");
            phase = Phase.create(parts0[1], false);
            travelTime = Double.parseDouble(parts0[3]);
            distance = Double.parseDouble(parts0[6]);
            turningPoint = createPositionForLine(lines[2]);
            enterPoint = createPositionForLine(lines[1]);
            leavePoint = createPositionForLine(lines[3]);
        }
        private void parseOutputDppTurn(String[] lines) {
            String[] parts0 = lines[0].trim().split("\\s+");
            phase = Phase.create(parts0[1], false);
            travelTime = Double.parseDouble(parts0[3]);
            distance = Double.parseDouble(parts0[6]);
            turningPoint = createPositionForLine(lines[1]);
        }

        private FullPosition createPositionForLine(String line) {
            String[] parts = line.trim().split("\\s+");
            return new FullPosition(Double.parseDouble(parts[3])
                    ,Double.parseDouble(parts[4])
                    ,6371. - Double.parseDouble(parts[1]));
        }

        private void parseOutputS(String[] lines, double pierceDepth) {
            FullPosition[] enterPoints = new FullPosition[6];
            FullPosition[] leavePoints = new FullPosition[6];
            double[] rayparams = new double[6];
            int count = -1;
            int iMinRayParam = -1;
            double minRayParam = Double.MIN_VALUE;
            boolean foundEnterPoint = false;
            FullPosition previousLoc = new FullPosition(0, 0, 7000);
            for (String line : lines) {
                if (line.startsWith(">")) {
                    foundEnterPoint = false;
                    count++;
                    rayparams[count] = Double.parseDouble(line.split("rayParam")[1].trim().split("\\s+")[0]);
                    if (rayparams[count] > minRayParam) {
                        iMinRayParam = count;
                        minRayParam = rayparams[count];
                    }
//					System.out.println(rayparams[count]);
                }
                else {
//					System.out.println(line);
//					String[] tmps = line.trim().split("\\s+");
//					for (String tmp : tmps)
//						System.out.println(tmp);
                    double[] s = Arrays.stream(line.trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
                    double depth = s[1];
                    if (MathAid.equalWithinEpsilon(depth, pierceDepth, 1.)) {
                        if (!foundEnterPoint) {
                            enterPoints[count] = new FullPosition(s[3], s[4], Earth.EARTH_RADIUS - depth);
                            foundEnterPoint = true;
                        }
                        else
                            leavePoints[count] = new FullPosition(s[3], s[4], Earth.EARTH_RADIUS - depth);
                    }
                    if (previousLoc.getR() > depth) {
                        turningPoint = previousLoc;
                    }
                }
            }

            enterPoint = enterPoints[iMinRayParam];
            leavePoint = leavePoints[iMinRayParam];

//			System.out.println(enterPoint + " " + leavePoint);
        }

        public Phase getPhase() {
            return phase;
        }

        public double getTravelTime() {
            return travelTime;
        }

        public double getDistance() {
            return distance;
        }

        public FullPosition getTurningPoint() {
            return turningPoint;
        }

        public FullPosition getEnterPoint() {
            return enterPoint;
        }

        public FullPosition getLeavePoint() {
            return leavePoint;
        }
    }
/*
 *
    public static TimewindowInformation timewindow(SACFileName sacname) throws IOException, TauModelException {
        double startTime = 0;
        double endTime = 0;
        double GCARC = sacname.readHeader().getValue(SACHeaderEnum.GCARC);
        Station station = sacname.read().getStation();

        TauP_Time timetool = new TauP_Time("prem");
        timetool.parsePhaseList("SKKS");
        timetool.depthCorrect(Earth.EARTH_RADIUS - sacname.read().getEventLocation().getR());

        timetool.calculate(GCARC);

//		List<Double> times = new ArrayList<>();
//
//		timetool.getArrivals().stream()
//			.filter(arrival -> arrival.getDistDeg() == GCARC)
//			.forEach(arrival -> times.add(arrival.getTime()));
//
//		Collections.sort(times);

//		startTime = times.get(0);
//		endTime = times.get(times.size() - 1);

        startTime = timetool.getArrival(0).getTime() - 170;
        endTime = startTime + 340;

        TimewindowInformation tw = new TimewindowInformation(startTime, endTime
                , station, sacname.getGlobalCMTID(), sacname.getComponent());

        return tw;
    }
 */
}
