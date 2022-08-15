package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPhase;
import io.github.kensuke1984.kibrary.external.TauPTimeReader;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that creates a data file of timewindows.
 * <p>
 * Timewindows are created for all SAC files in event folders under workDir that satisfy the following criteria:
 * <ul>
 * <li> is observed waveform </li>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * For each (event, observer, component) data pair of the selected SAC files,
 * several timewindows may be created for different phases.
 * Thus, output data can be specified as a (event, observer, component, timeframe)-pair.
 * <p>
 * This class creates a window for each specified phase,
 * starting from (arrival time - frontShift) and ending at (arrival time + rearShift).
 * It also creates a window for each exphase,
 * starting from (arrival time - exFrontShift) and ending at (arrival time + rearShift),
 * and abandons overlapped parts between these.
 * Arrival times are computed by TauP.
 * <p>
 * Start and end times of the windows are set to integer multiples of DELTA in SAC files.
 * <p>
 * Timewindow information is written in binary format in "timewindow*.dat".
 * Files that could not produce timewindows are written in "invalidTimewindow*.txt".
 * See {@link TimewindowDataFile}.
 *
 * @author Kensuke Konishi
 * @version 0.2.4
 * @author anselme add phase information, methods for corridor and MTZ inversion
 */
public class TimewindowMaker extends Operation {

    private static final double EX_FRONT_SHIFT = 5.;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output timewindow file
     */
    private Path outTimewindowPath;
    /**
     * タイムウインドウがおかしくて省いたリスト とりあえず startが０以下になるもの TODO:本当？
     */
    private Path invalidListPath;
    /**
     * Path of the output travel time file
     */
    private Path outTravelTimePath;
    /**
     * set of {@link SACComponent}
     */
    private Set<SACComponent> components;

    private boolean majorArc;
    /**
     * 使いたいフェーズ
     */
    private Set<Phase> usePhases;
    /**
     * 省きたいフェーズ
     */
    private Set<Phase> exPhases;
    /**
     * how many seconds it shifts the starting time [s] phase到着からどれだけずらすか if the
     * value is 5(not -5), then basically, each timewindow starts 5 sec before
     * each usePhase
     */
    private double frontShift;
    /**
     * phase到着から後ろ何秒を取るか if the value is 10, basically, each timewindow ends 10
     * secs after each usephase arrival
     */
    private double rearShift;
    private double minLength;
    /**
     * @author anselme
     */
    private boolean corridor;
    /**
     * exPhaseがusePhaseに割って入る時にTimewindowを分けるか(trueではtimewindowを捨てる)
     */
    private boolean separateWindow;
    /**
     * Name of structure to compute travel times
     */
    private String structureName;

    private Set<TimewindowData> timewindowSet;
    private Set<TravelTimeInformation> travelTimeSet;
    private double[][] catalogue_sS;
    private double[][] catalogue_pP;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(boolean) Whether or not to use major arc phases (false)");
            pw.println("#majorArc ");
            pw.println("##TauPPhases to be included in timewindow, listed using spaces (S)");
            pw.println("#usePhases ");
            pw.println("##TauPPhases not to be included in timewindow, listed using spaces, if any");
            pw.println("#exPhases ");
            pw.println("##(double) Time before first phase [sec]. If it is 10, then 10 s before arrival (0)");
            pw.println("#frontShift ");
            pw.println("##(double) Time after last phase [sec]. If it is 60, then 60 s after arrival (0)");
            pw.println("#rearShift ");
            pw.println("##(double) Minimum length for the timewindows [sec] (0)");
            pw.println("#minLength ");
            pw.println("##(boolean) Corridor (false)");
            pw.println("#corridor ");
            pw.println("##(boolean) Separate timewindows when timewindows of usePahses aren't overlapped or exPhases arrive between usePhases (false)");
            pw.println("#separateWindow ");
            pw.println("##(String) Name of structure to compute travel times using TauP (prem)");
            pw.println("#structureName ");
        }
        System.err.println(outPath + " is created.");
    }

    public TimewindowMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        majorArc = property.parseBoolean("majorArc", "false");
        usePhases = phaseSet(property.parseString("usePhases", "S"));
        exPhases = property.containsKey("exPhases") ? phaseSet(property.parseString("exPhases", null)) : phaseSet(null);
        frontShift = property.parseDouble("frontShift", "0");
        rearShift = property.parseDouble("rearShift", "0");
        minLength = property.parseDouble("minLength", "0");
        corridor = property.parseBoolean("corridor", "false");
        separateWindow = property.parseBoolean("separateWindow","false");
        structureName = property.parseString("structureName", "prem").toLowerCase();

        String catalogueName_sS =  "firstAppearance_sS." + structureName + ".catalogue";
        String catalogueName_pP =  "firstAppearance_pP." + structureName + ".catalogue";
        catalogue_sS = readCatalogue(catalogueName_sS);
        catalogue_pP = readCatalogue(catalogueName_pP);

        String dateStr = GadgetAid.getTemporaryString();
        outTimewindowPath = workPath.resolve(DatasetAid.generateOutputFileName("timewindow", tag, dateStr, ".dat"));
        invalidListPath = workPath.resolve(DatasetAid.generateOutputFileName("invalidTimewindow", tag, dateStr, ".txt"));
        outTravelTimePath = workPath.resolve(DatasetAid.generateOutputFileName("travelTime", tag, dateStr, ".inf"));
        timewindowSet = Collections.synchronizedSet(new HashSet<>());
        travelTimeSet = Collections.synchronizedSet(new HashSet<>());
    }

    private static Set<Phase> phaseSet(String arg) {
        return (arg == null || arg.isEmpty()) ? Collections.emptySet()
                : Arrays.stream(arg.split("\\s+")).map(Phase::create).collect(Collectors.toSet());
    }

    @Override
    public void run() throws IOException {
        System.out.println("Using exFrontShift = " + EX_FRONT_SHIFT);
        System.err.println("Invalid files, if any, will be listed in " + invalidListPath);
        ThreadAid.runEventProcess(workPath, eventDir -> {
            try {
                // set up taup_time tool
                // This is done per event (not reusing a single tool) because each thread needs its own instance.
                // This is done per event (not at each observer) because computation takes time when changing source depth (see TauP manual).
                String[] phaseNames = Stream.concat(usePhases.stream(), exPhases.stream()).map(Phase::toString).toArray(String[]::new);
                TauP_Time timeTool = new TauP_Time(structureName);
                timeTool.setPhaseNames(phaseNames);
                timeTool.setSourceDepth(eventDir.getGlobalCMTID().getEventData().getCmtLocation().getDepth()); //TODO use this for later calculation

                eventDir.sacFileSet().stream().filter(sfn -> sfn.isOBS() && components.contains(sfn.getComponent()))
                        .forEach(sfn -> {
                    try {
                        if (corridor)
                            makeTimeWindowForCorridor(sfn);
                        else
                            makeTimeWindow(sfn, timeTool);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (TauModelException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.err.print(".");
        } , 10, TimeUnit.HOURS);
        // this println() is for starting new line after writing "."s
        System.err.println();

        if (timewindowSet.isEmpty()) {
            System.err.println("No timewindow is created.");
        }
        else {
            System.err.println("Outputting timewindows in " + outTimewindowPath);
            TimewindowDataFile.write(timewindowSet, outTimewindowPath);
            System.err.println(timewindowSet.size() + " timewindows were made.");
        }

        System.err.println("Outputting travel time information in " + outTravelTimePath);
        TravelTimeInformationFile.write(usePhases, exPhases, travelTimeSet, outTravelTimePath);
    }

    private double[] avoidPhaseTimeFromCatalogue(List<Arrival> useArrivals, double epicentralDistance, double eventDepth) {
        double[] exPhaseTime = new double[0];

        if (this.exPhases.size() == 1 && this.exPhases.contains(Phase.create("pP")) && !this.usePhases.contains(Phase.PcP)
                && this.usePhases.contains(Phase.P)) {
            if (epicentralDistance > 30)
                throw new RuntimeException("Unexpected: pP should exist for epicentral distance " + epicentralDistance);

            double minDepth = catalogue_pP[0][0];
            double dDepth = catalogue_pP[0][2];
            int icat = (int) ((eventDepth - minDepth) / dDepth) + 1;

            double differentialTime = catalogue_pP[icat][2] - catalogue_pP[icat][3];
            double Ptime = useArrivals.stream()
                    .filter(arrival -> Phase.create(arrival.getPhase().getName()).equals(Phase.P))
                    .map(arrival -> arrival.getTime()).findFirst().get();

            exPhaseTime = new double[] {Ptime + differentialTime};
        }

        if (this.exPhases.size() == 1 && this.exPhases.contains(Phase.create("sS")) && !this.usePhases.contains(Phase.ScS)
                && this.usePhases.contains(Phase.S)) {
            if (epicentralDistance > 30)
                throw new RuntimeException("Unexpected: sS should exist for epicentral distance " + epicentralDistance);

            double minDepth = catalogue_sS[0][0];
            double dDepth = catalogue_sS[0][2];
            int icat = (int) ((eventDepth - minDepth) / dDepth) + 1;

            double differentialTime = catalogue_sS[icat][2] - catalogue_sS[icat][3];
            double Stime = useArrivals.stream()
                    .filter(arrival -> Phase.create(arrival.getPhase().getName()).equals(Phase.S))
                    .map(arrival -> arrival.getTime()).findFirst().get();

            exPhaseTime = new double[] {Stime + differentialTime};
        }

        return exPhaseTime;
    }

    /**
     * Make a timewindow which contains all usePhases.
     * In case of triplication of usePhases, use only the first arrival
     * When exPhases arrive between or close to usePhases, a timewindow is discarded
     * To use for sS, pP phases for the MTZ, read a catalog of travel times.
     * TODO check for SKS phase
     * @param sacFileName
     * @param timeTool {@link TauP_Time} instance to use
     * @throws IOException
     * @author rei
     */
    private void makeTimeWindow(SACFileName sacFileName, TauP_Time timeTool) throws IOException, TauModelException {
        SACFileAccess sacFile = sacFileName.read();
        // 震源観測点ペアの震央距離
        double epicentralDistance = sacFile.getValue(SACHeaderEnum.GCARC);

        try {
            // Compute phase arrivals
            timeTool.calculate(epicentralDistance);
            List<Arrival> arrivals = timeTool.getArrivals();
            List<Arrival> useArrivals = new ArrayList<>();
            List<Arrival> avoidArrivals = new ArrayList<>();
            if (separateWindow) {
                // for usePhases, use only the first arrival in case of triplication
                for (Phase phase : usePhases) {
                    arrivals.stream().filter(arrival -> Phase.create(arrival.getPhase().getName()).equals(phase)).findFirst().ifPresent(useArrivals::add);
                }
            } else {
                // for usePhases, use all arrivals if timewindows are not separated
                 arrivals.stream().filter(arrival -> usePhases.contains(Phase.create(arrival.getPhase().getName()))).forEach(useArrivals::add);
            }

            // for avoidPhases, use all arrivals
            arrivals.stream().filter(arrival -> exPhases.contains(Phase.create(arrival.getPhase().getName()))).forEach(avoidArrivals::add);
            // refine useArrivals
            if (!majorArc) {
                useArrivals.removeIf(arrival -> arrival.getDistDeg() >= 180.);
            }
            if (useArrivals.isEmpty()) {
                writeInvalid(sacFileName, "No usePhases arrive");
                return;
            }

            Timewindow[] windows;
            if (separateWindow) {
                // create window
                double[] usePhaseTimes = useArrivals.stream().mapToDouble(Arrival::getTime).toArray();
                double[] avoidPhaseTimes = avoidArrivals.stream().mapToDouble(Arrival::getTime).toArray();

//                if (avoidPhaseTimes.length == 0) {
//                    // 震源深さ depth
//                    double eventDepth = sacFile.getValue(SACHeaderEnum.EVDP);
//                    avoidPhaseTimes = avoidPhaseTimeFromCatalogue(useArrivals, epicentralDistance, eventDepth); // TODO is this needed?
//                }

                windows = createTimeWindows(usePhaseTimes, avoidPhaseTimes, EX_FRONT_SHIFT);
            } else {
                // find first and last usePhase arrival time
                Arrival firstUseArrival = null;
                Arrival lastUseArrival = null;
                for (Arrival arrival : useArrivals) {
                    if (firstUseArrival == null || arrival.getTime() < firstUseArrival.getTime())
                        firstUseArrival = arrival;
                    if (lastUseArrival == null || arrival.getTime() > lastUseArrival.getTime())
                        lastUseArrival = arrival;
                }
                double firstUseTime = firstUseArrival.getTime();
                double lastUseTime = lastUseArrival.getTime();

                // check if an avoidPhase is between or near usePhases
                for (Arrival arrival : avoidArrivals) {
                    double avoidTime = arrival.getTime();
                    if (firstUseTime <= (avoidTime + rearShift) && (avoidTime - EX_FRONT_SHIFT) <= lastUseTime) {
                        writeInvalid(sacFileName, arrival.getPhase().getName() + " arrives between or near "
                                + firstUseArrival.getPhase().getName() + " and " + lastUseArrival.getPhase().getName());
                        return;
                    }
                }

                // create window
                double[] avoidPhaseTimes = avoidArrivals.stream().mapToDouble(Arrival::getTime).toArray();
                windows = createTimeWindows(firstUseTime, lastUseTime, avoidPhaseTimes, EX_FRONT_SHIFT);
            }
            if (windows == null) {
                writeInvalid(sacFileName, "Failed to create timewindow");
                return;
            }

            // delta (time step) in SacFile
            double delta = sacFile.getValue(SACHeaderEnum.DELTA);
            double e = sacFile.getValue(SACHeaderEnum.E);
            // station of SacFile
            Observer observer = sacFile.getObserver();
            // global cmt id of SacFile
            GlobalCMTID event = sacFileName.getGlobalCMTID();
            // component of SacFile
            SACComponent component = sacFileName.getComponent();

            // window fix
            Arrays.stream(windows).map(wind -> fix(wind, delta)).filter(wind -> wind.getEndTime() <= e).map(
                    wind -> new TimewindowData(wind.getStartTime(), wind.getEndTime(), observer, event, component, containPhases(wind, useArrivals)))
                    .filter(tw ->  tw.getLength() > minLength)
                    .forEach(timewindowSet::add);

            // add travel time information
            travelTimeSet.add(new TravelTimeInformation(event, observer, useArrivals, avoidArrivals));

        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Make timewindow using corridor (ScSn phase)
     * @param sacFileName
     * @throws IOException
     * @author anselme
     */
    private void makeTimeWindowForCorridor(SACFileName sacFileName) throws IOException {
        SACFileAccess sacFile = sacFileName.read();
        // 震源深さ radius
        double eventR = 6371 - sacFile.getValue(SACHeaderEnum.EVDP);
        // 震源観測点ペアの震央距離
        double epicentralDistance = sacFile.getValue(SACHeaderEnum.GCARC);
        try {
            // group 1
//          this.frontShift = 20.;
//          this.rearShift = 50.;
//          Set<Phase> usePhasesCorridor = Arrays.stream("s S ScS Sdiff".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          Set<TauPPhase> usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, model);
//          if (!majorArc) {
//              usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
//          }
//          Set<Phase> exPhasesCorridor = Arrays.stream("sS sSdiff sScS SS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          Set<TauPPhase> exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
//                  : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, model);
//          if (usePhases.isEmpty()) {
//              writeInvalid(sacFileName, "None of s, S, ScS and Sdiff phase arrive");
//              return;
//          }
//          double[] phaseTime = toTravelTime(usePhases);
//          double[] exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);
//
//          Timewindow[] windows1 = createTimeWindows(phaseTime, exPhaseTime);
//          //
//
//          // group 2
//          this.frontShift = 20.;
//          this.rearShift = 50.;
//          usePhasesCorridor = Arrays.stream("sS sScS sSdiff".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, model);
//          if (!majorArc) {
//              usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
//          }
//          exPhasesCorridor = Arrays.stream("S ScS SS sSS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
//                  : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, model);
//          if (usePhases.isEmpty()) {
//              writeInvalid(sacFileName, "None of sS, sScS and sSdiff phase arrive");
//              return;
//          }
//          phaseTime = toTravelTime(usePhases);
//          exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);
//
//          Timewindow[] windows2 = createTimeWindows(phaseTime, exPhaseTime);
//          //
//
//          // group 3
//          this.frontShift = 20.;
//          this.rearShift = 70.;
//          usePhasesCorridor = Arrays.stream("SS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, model);
//          if (!majorArc) {
//              usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
//          }
//          exPhasesCorridor = Arrays.stream("sScS sSS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
//                  : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, model);
//          if (usePhases.isEmpty()) {
//              writeInvalid(sacFileName, "No SS phase arrive");
//              return;
//          }
//          phaseTime = toTravelTime(usePhases);
//          exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);
//
//          Timewindow[] windows3 = createTimeWindows(phaseTime, exPhaseTime);
//          //
//
//          // group 4
//          this.frontShift = 20.;
//          this.rearShift = 70.;
//          usePhasesCorridor = Arrays.stream("sSS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, model);
//          if (!majorArc) {
//              usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
//          }
//          exPhasesCorridor = Arrays.stream("SS SSS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
//                  : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, model);
//          if (usePhases.isEmpty()) {
//              writeInvalid(sacFileName ,"No sSS phase arrive");
//              return;
//          }
//          phaseTime = toTravelTime(usePhases);
//          exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);
//
//          Timewindow[] windows4 = createTimeWindows(phaseTime, exPhaseTime);
            //

            // group 1
            this.frontShift = 10.;
            this.rearShift = 60.;
//          double eventDepth = Earth.EARTH_RADIUS - sacFileName.getGlobalCMTID().getEvent().getCmtLocation().getR();
            Set<Phase> usePhasesCorridor = null;
//          if (eventDepth < 200)
//              usePhasesCorridor = Arrays.stream("s S ScS Sdiff sS sScS sSdiff SS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          else
                usePhasesCorridor = Arrays.stream("s S ScS Sdiff sS sScS sSdiff SS sSS".split(" "))
                .map(Phase::create).collect(Collectors.toSet());
            List<TauPPhase> usePhasesList = TauPTimeReader.getTauPPhaseList(eventR, epicentralDistance, usePhasesCorridor, structureName);
            if (!majorArc) {
                usePhasesList.removeIf(phase -> phase.getPuristDistance() >= 180.);
            }
            Set<Phase> exPhasesCorridor = null;
//          if (eventDepth < 200)
//              exPhasesCorridor = Arrays.stream("sSS SSS sSSS SSSS sSSSS".split(" "))
//                  .map(Phase::create).collect(Collectors.toSet());
//          else
                exPhasesCorridor = Arrays.stream("SSS sSSS SSSS sSSSS".split(" "))
                .map(Phase::create).collect(Collectors.toSet());
            Set<TauPPhase> exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
                    : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, structureName);
            if (usePhases.isEmpty()) {
                writeInvalid(sacFileName, "usePhase is empty");
                return;
            }
            double[] phaseTime = toTravelTime(usePhasesList);
            Phase[] phaseNames = toPhaseName(usePhasesList);
            double[] exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);

            Timewindow[] windows1 = createTimeWindowsAndSplit(phaseTime, phaseNames, exPhaseTime);
//          //

            // group 5
            this.frontShift = 6.;
            this.rearShift = 70.;
            usePhasesCorridor = Arrays.stream("ScSScS sScSScS".split(" "))
                    .map(Phase::create).collect(Collectors.toSet());
            Set<TauPPhase> usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, structureName);
            if (!majorArc) {
                usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
            }
            exPhasesCorridor = Arrays.stream("SSS sSSS SSSS sSSSS SSSSS sSSSSS SSSSSS sSSSSSS".split(" "))
                    .map(Phase::create).collect(Collectors.toSet());
            exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
                    : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, structureName);
            if (usePhases.isEmpty()) {
                writeInvalid(sacFileName, "Neither ScS2 nor sScS2 phase arrive");
                return;
            }
            phaseTime = toTravelTime(usePhases);
            phaseNames = toPhaseName(usePhasesList);
            exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);

            Timewindow[] windows5 = createTimeWindowsAndSplit(phaseTime, phaseNames, exPhaseTime);
            //

            // group 6
            this.frontShift = 6.;
            this.rearShift = 75.;
            usePhasesCorridor = Arrays.stream("ScSScSScS sScSScSScS".split(" "))
                    .map(Phase::create).collect(Collectors.toSet());
            usePhases = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, usePhasesCorridor, structureName);
            if (!majorArc) {
                usePhases.removeIf(phase -> phase.getPuristDistance() >= 180.);
            }
            exPhasesCorridor = Collections.emptySet();
            exPhases = exPhasesCorridor.size() == 0 ? Collections.emptySet()
                    : TauPTimeReader.getTauPPhase(eventR, epicentralDistance, exPhasesCorridor, structureName);
            if (usePhases.isEmpty()) {
                writeInvalid(sacFileName, "Neither ScS3 nor sScS3 phase arrive");
                return;
            }
            phaseTime = toTravelTime(usePhases);
            phaseNames = toPhaseName(usePhasesList);
            exPhaseTime = exPhases.isEmpty() ? null : toTravelTime(exPhases);

            Timewindow[] windows6 = createTimeWindowsAndSplit(phaseTime, phaseNames, exPhaseTime);
            //

            List<Timewindow> windowList = new ArrayList<>();
            if (windows1 != null) {
                for (Timewindow tw : windows1)
                    windowList.add(tw);
            }
//          if (windows2 != null) {
//              for (Timewindow tw : windows2)
//                  windowList.add(tw);
//          }
//          if (windows3 != null) {
//              for (Timewindow tw : windows3)
//                  windowList.add(tw);
//          }
//          if (windows4 != null) {
//              for (Timewindow tw : windows4)
//                  windowList.add(tw);
//          }
            if (windows5 != null) {
                for (Timewindow tw : windows5)
                    windowList.add(tw);
            }
            if (windows6 != null) {
                for (Timewindow tw : windows6)
                    windowList.add(tw);
            }
//          if (windows7 != null) {
//              for (Timewindow tw : windows7)
//                  windowList.add(tw);
//          }
//          if (windows8 != null) {
//              for (Timewindow tw : windows8)
//                  windowList.add(tw);
//          }
            Timewindow[] windows = windowList.toArray(new Timewindow[windowList.size()]);

            if (windows == null) {
                writeInvalid(sacFileName, "Timewindows are not created");
                return;
            }

            // System.out.println(sacFile.getValue(SacHeaderEnum.E));
            // delta (time step) in SacFile
            double delta = sacFile.getValue(SACHeaderEnum.DELTA);
            double e = sacFile.getValue(SACHeaderEnum.E);
            // station of SacFile
            Observer observer = sacFile.getObserver();
            // global cmt id of SacFile
            GlobalCMTID event = sacFileName.getGlobalCMTID();
            // component of SacFile
            SACComponent component = sacFileName.getComponent();

            // window fix
            Set<Phase> tmpUsePhases = Arrays.stream("s S ScS Sdiff sS sScS sSdiff SS sSS SSS sSSS SSSS sSSSS ScSScS sScSScS ScSScSScS sScSScSScS".split(" "))
                .map(Phase::create).collect(Collectors.toSet());
            Set<TauPPhase> usePhases_ = TauPTimeReader.getTauPPhase(eventR, epicentralDistance, tmpUsePhases, structureName);
            Arrays.stream(windows).map(window -> fix(window, delta)).filter(window -> window.getEndTime() <= e).map(
                    window -> new TimewindowData(window.getStartTime(), window.getEndTime(), observer, event, component, containPhases(window, usePhases_)))
                    .filter(tw -> tw.getPhases().length > 0)
                    .forEach(tw -> {
                        if (tw.endTime - tw.startTime >= 30.) {
                            timewindowSet.add(tw);
                        }
                        else
                            System.out.println("Ignored length<30s " + tw);
                    });

            // add travel time information
            travelTimeSet.add(new TravelTimeInformation(event, observer, usePhases, exPhases));

        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates timewindows. all phases have front and rear shifts. if exPhase
     * with front and rear shifts is in the timewindows of use-phases, then the
     * timewindow will be cut.
     *
     * @param phaseTime   must be in order.
     * @param exPhaseTime must be in order.
     * @return created {@link Timewindow} array
     */
    private Timewindow[] createTimeWindows(double[] phaseTime, double[] exPhaseTime) {
        Timewindow[] windows = Arrays.stream(phaseTime)
                .mapToObj(time -> new Timewindow(time - frontShift, time + rearShift)).sorted()
                .toArray(Timewindow[]::new);
        Timewindow[] exWindows = exPhaseTime == null ? null
                : Arrays.stream(exPhaseTime).mapToObj(time -> new Timewindow(time - frontShift, time + rearShift))
                        .sorted().toArray(Timewindow[]::new);

        windows = mergeWindow(windows);

        if (exWindows == null)
            return windows;
        exWindows = mergeWindow(exWindows);
        return considerExPhase(windows, exWindows);
    }

    /**
     * @param phaseTime
     * @param phaseNames
     * @param exPhaseTime
     * @return
     * @author anselme
     * TODO check it
     */
    private Timewindow[] createTimeWindowsAndSplit(double[] phaseTime, Phase[] phaseNames, double[] exPhaseTime) {
//      Timewindow[] windows = Arrays.stream(phaseTime)
//              .mapToObj(time -> new Timewindow(time - frontShift, time + rearShift)).sorted()
//              .toArray(Timewindow[]::new);
        if (phaseTime.length == 0)
            return null;

        Map<Phase, Timewindow[]> phaseWindowMap = new HashMap<>();
        for (int i = 0; i < phaseTime.length; i++) {
            double time = phaseTime[i];
            Phase phase = phaseNames[i];
            if (phaseWindowMap.containsKey(phase)) {
                Timewindow[] windows = phaseWindowMap.get(phase);
                Timewindow[] newWindows = Arrays.copyOf(windows, windows.length + 1);
                newWindows[newWindows.length - 1] = new Timewindow(time - frontShift, time + rearShift);
                phaseWindowMap.replace(phase, newWindows);
            }
            else {
                Timewindow[] windows = new Timewindow[] { new Timewindow(time - frontShift, time + rearShift) };
                phaseWindowMap.put(phase, windows);
            }
        }

        List<Timewindow> list = new ArrayList<>();
        for (Phase phase : phaseWindowMap.keySet()) {
            Timewindow[] windows = Arrays.stream(phaseWindowMap.get(phase)).sorted().toArray(Timewindow[]::new);
            windows = mergeWindow(windows);
            for (Timewindow window : windows)
                list.add(window);
        }

        Timewindow[] windows = list.stream().sorted().toArray(Timewindow[]::new);

        Timewindow[] exWindows = exPhaseTime == null ? null
                : Arrays.stream(exPhaseTime).mapToObj(time -> new Timewindow(time - frontShift, time + rearShift))
                        .sorted().toArray(Timewindow[]::new);

        if (exWindows != null) {
            exWindows = mergeWindow(exWindows);
            windows = considerExPhase(windows, exWindows, minLength);
        }

        return splitWindow(windows, minLength);
    }

    /**
     * @param phaseTime
     * @param exPhaseTime
     * @param exFrontShift
     * @return
     * @author anselme
     */
    private Timewindow[] createTimeWindows(double[] phaseTime, double[] exPhaseTime, double exFrontShift) {
        Timewindow[] windows = Arrays.stream(phaseTime)
                .mapToObj(time -> new Timewindow(time - frontShift, time + rearShift)).sorted()
                .toArray(Timewindow[]::new);
        Timewindow[] exWindows = exPhaseTime.length == 0 ? null
                : Arrays.stream(exPhaseTime).mapToObj(time -> new Timewindow(time - exFrontShift, time + rearShift))
                        .sorted().toArray(Timewindow[]::new);

        windows = mergeWindow(windows);

        if (exWindows == null)
            return windows;

//      System.out.println(exFrontShift + " " + exWindows[0].getStartTime());

        exWindows = mergeWindow(exWindows);
        return considerExPhase(windows, exWindows);
    }

    /**
     * @param initialPhaseTime
     * @param finalPhaseTime
     * @param exPhaseTime
     * @param exFrontShift
     * @return
     * @author rei
     */
    private Timewindow[] createTimeWindows(double initialPhaseTime, double finalPhaseTime, double[] exPhaseTimes, double exFrontShift) {
        Timewindow[] window = new Timewindow[1];
        window[0] = new Timewindow(initialPhaseTime - frontShift, finalPhaseTime + rearShift);
        Timewindow[] exWindows = exPhaseTimes == null ? null
                : Arrays.stream(exPhaseTimes).mapToObj(time -> new Timewindow(time - exFrontShift, time + rearShift))
                        .sorted().toArray(Timewindow[]::new);

        if (exWindows == null)
            return window;

        exWindows = mergeWindow(exWindows);
        return considerExPhase(window, exWindows);
    }

    /**
     * fix start and end time by delta these time must be (int) * delta
     *
     * @param window {@link Timewindow}
     * @param delta  time step
     * @return fixed {@link Timewindow}
     */
    private static Timewindow fix(Timewindow window, double delta) {
        double startTime = delta * (int) (window.startTime / delta);
        double endTime = delta * (int) (window.endTime / delta);
        return new Timewindow(startTime, endTime);
    }

    /**
     * @param useTimeWindow
     * @param exTimeWindow
     * @return useTimeWindowからexTimeWindowの重なっている部分を取り除く 何もなくなってしまったらnullを返す
     */
    private static Timewindow cutWindow(Timewindow useTimeWindow, Timewindow exTimeWindow) {
        // System.out.println(useTimeWindow+" "+exTimeWindow);
        if (!useTimeWindow.overlap(exTimeWindow)) return useTimeWindow;
        if (exTimeWindow.startTime <= useTimeWindow.startTime)
            return useTimeWindow.endTime <= exTimeWindow.endTime ? null :
                    new Timewindow(exTimeWindow.endTime, useTimeWindow.endTime);
        return new Timewindow(useTimeWindow.startTime, exTimeWindow.startTime);
    }

    /**
     * @param useTimeWindow
     * @param exTimeWindow
     * @param minLength
     * @return useTimeWindowからexTimeWindowの重なっている部分を取り除く 何もなくなってしまったらnullを返す
     * @author anselme
     */
    private static Timewindow cutWindow(Timewindow useTimeWindow, Timewindow exTimeWindow, double minLength) {
        // System.out.println(useTimeWindow+" "+exTimeWindow);
        if (!useTimeWindow.overlap(exTimeWindow)) return useTimeWindow;
        if (exTimeWindow.startTime <= useTimeWindow.startTime)
            return useTimeWindow.endTime <= exTimeWindow.endTime ? null
                    : new Timewindow(exTimeWindow.endTime, useTimeWindow.endTime);
        Timewindow newWindow = new Timewindow(useTimeWindow.startTime, exTimeWindow.startTime);
        return newWindow.getLength() < minLength ? null : newWindow;
    }

    /**
     * eliminate exTimeWindows from useTimeWindows
     *
     * @param useTimeWindows must be in order by start time
     * @param exTimeWindows  must be in order by start time
     * @return timewindows to use
     */
    private static Timewindow[] considerExPhase(Timewindow[] useTimeWindows, Timewindow[] exTimeWindows) {
        List<Timewindow> usable = new ArrayList<>();
        for (Timewindow window : useTimeWindows) {
            for (Timewindow ex : exTimeWindows) {
                window = cutWindow(window, ex);
                if (window == null) break;
            }
            if (window != null) usable.add(window);
        }

        return usable.size() == 0 ? null : usable.toArray(new Timewindow[0]);
    }

    /**
     * eliminate exTimeWindows from useTimeWindows
     *
     * @param useTimeWindows must be in order by start time
     * @param exTimeWindows  must be in order by start time
     * @param minLength
     * @return timewindows to use
     * @author anselme
     */
    private static Timewindow[] considerExPhase(Timewindow[] useTimeWindows, Timewindow[] exTimeWindows, double minLength) {
        List<Timewindow> usable = new ArrayList<>();
        for (Timewindow window : useTimeWindows) {
            for (Timewindow ex : exTimeWindows) {
                window = cutWindow(window, ex, minLength);
                if (window == null)
                    break;
            }
            if (window != null)
                usable.add(window);
        }

        return usable.size() == 0 ? null : usable.toArray(new Timewindow[0]);
    }

    /**
     * if there are any overlapping timeWindows, merge them. the start times
     * must be in order.
     *
     * @param windows to be merged
     * @return windows containing all the input windows in order
     */
    private static Timewindow[] mergeWindow(Timewindow[] windows) {
        if (windows.length == 1)
            return windows;
        List<Timewindow> windowList = new ArrayList<>();
        Timewindow windowA = windows[0];
        for (int i = 1; i < windows.length; i++) {
            Timewindow windowB = windows[i];
            if (windowA.overlap(windowB)) {
                windowA = windowA.merge(windowB);
                if (i == windows.length - 1)
                    windowList.add(windowA);
            } else {
                windowList.add(windowA);
                windowA = windows[i];
                if (i == windows.length - 1)
                    windowList.add(windows[i]);
            }
        }
        return windowList.toArray(new Timewindow[windowList.size()]);
    }

    /**
     * merge all windows. the start times must be in order.
     *
     * @param windows to be merged
     * @return a window containing all the input windows
     */
    private static Timewindow[] mergeAllWindow(Timewindow[] windows) {
        if (windows.length == 1)
            return windows;
        Timewindow[] window = new Timewindow[1];
        window[0] = windows[0];
        for (int i = 1; i < windows.length; i++) {
            window[0] = window[0].merge(windows[i]);
        }
        return window;
    }

    /**
     * @param windows
     * @param minLength
     * @return
     * @author anselme
     */
    private static Timewindow[] splitWindow(Timewindow[] windows, double minLength) {
        boolean mergeIfshort = true;
        if (windows.length == 1)
            return windows;
        List<Timewindow> windowList = new ArrayList<>();
        Timewindow windowA = windows[0];
        for (int i = 1; i < windows.length; i++) {
            Timewindow windowB = windows[i];
            if (windowA.overlap(windowB)) {
                Timewindow newA = new Timewindow(windowA.startTime, windowB.startTime);
                if (newA.getLength() < minLength) {
                    windowA = newA.merge(windowB);
                } else {
                    windowList.add(newA);
                    windowA = windowB;
                }
                if (i == windows.length - 1)
                    windowList.add(windowA);
            } else {
                windowList.add(windowA);
                windowA = windows[i];
                if (i == windows.length - 1)
                    windowList.add(windows[i]);
            }
        }

        return windowList.toArray(new Timewindow[0]);
    }

    /**
     * @param window
     * @param usePhases
     * @return
     * @author anselme
     */
    private Phase[] containPhases(Timewindow window, Set<TauPPhase> usePhases) {
        Set<Phase> phases = new HashSet<>();
        for (TauPPhase phase : usePhases) {
            double time = phase.getTravelTime();
            if (time <= window.endTime && time >= window.startTime)
                phases.add(phase.getPhaseName());
        }
        return phases.toArray(new Phase[phases.size()]);
    }

    /**
     * @param window
     * @param usePhases
     * @return
     * @author anselme
     */
    private Phase[] containPhases(Timewindow window, List<Arrival> useArrivals) {
        Set<Phase> phases = new HashSet<>();
        for (Arrival arrival : useArrivals) {
            double time = arrival.getTime();
            if (time <= window.endTime && time >= window.startTime)
                phases.add(Phase.create(arrival.getPhase().getName()));
        }
        return phases.toArray(new Phase[phases.size()]);
    }

    /**
     * @param phases Set of TauPPhases
     * @return travel times in {@link TauPPhase}
     */
    private static double[] toTravelTime(Set<TauPPhase> phases) {
        return phases.stream().mapToDouble(TauPPhase::getTravelTime).toArray();
    }

    private static double[] toTravelTime(List<TauPPhase> phases) {
        return phases.stream().mapToDouble(TauPPhase::getTravelTime).toArray();
    }

    /**
     * @param phases
     * @return
     * @author anselme
     */
    private static Phase[] toPhaseName(List<TauPPhase> phases) {
        Phase[] names = new Phase[phases.size()];
        for (int i = 0; i < names.length; i++)
            names[i] = phases.get(i).getPhaseName();
        return names;
    }

    /**
     * Write invalid sacFile on invalidList
     * @param sacFileName which is invalid
     * @param reason why the sacFile is invalid
     */
    private synchronized void writeInvalid(SACFileName sacFileName, String reason) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(invalidListPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            pw.println(sacFileName + " : " + reason);
        }
    }

    /**
     * Read a catalog of travel times. To use for sS, pP phases for the MTZ
     * @param catalog
     * @return
     * @author anselme
     */
    public static double[][] readCatalogue(String catalog) {
        try {
            List<String> lines = IOUtils.readLines(
                    TimewindowMaker.class.getClassLoader().getResourceAsStream(catalog),
                    Charset.defaultCharset());
            String[] ss = lines.get(0).split("\\s+");
            double hmin = Double.parseDouble(ss[0]);
            double hmax = Double.parseDouble(ss[1]);
            double dh = Double.parseDouble(ss[2]);
            int nh = lines.size() - 1;

            if ((hmax - hmin) / dh + 1 != nh)
                throw new Exception("Catalog is broken");

            double[][] catalogue = new double[nh + 1][4];
            catalogue[0] = new double[] {hmin, hmax, dh};
            for (int i = 1; i < nh + 1; i++) {
                ss = lines.get(i).split("\\s+");
                catalogue[i][0] = Double.parseDouble(ss[0]);
                catalogue[i][1] = Double.parseDouble(ss[1]);
                catalogue[i][2] = Double.parseDouble(ss[2]);
                catalogue[i][3] = Double.parseDouble(ss[3]);
            }

            return catalogue;
        } catch (NullPointerException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
