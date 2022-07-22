package io.github.kensuke1984.kibrary.util.addons;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;


/**
 *
 *
 * TODO : component
 */
public class ProfileWaveform {
    public static void main(String[] args) {
        Path workingDir = Paths.get(".");
        Path basicIDPath = Paths.get(args[0]);
        Path basicPath = Paths.get(args[1]);
        SACComponent component = null;
        switch(args[2]) {
        case "Z":
            component = SACComponent.getComponent(1);
            break;
        case "R":
            component = SACComponent.getComponent(2);
            break;
        case "T":
            component = SACComponent.getComponent(3);
            break;
        default:
            System.err.println("Component is illegal");
            return;
        }

        try {
            BasicID[] waveforms = BasicIDFile.read(basicIDPath, basicPath);

            Path profilePath = workingDir.resolve("profile");
            Path stackPath = workingDir.resolve("stack");
            Files.createDirectories(profilePath);
            Files.createDirectories(stackPath);

            BasicID[] obsIDs = new BasicID[waveforms.length / 2];
            BasicID[] synIDs = new BasicID[waveforms.length / 2];

            // extract only observed basicIDs from 'waveforms[]' and enter them in 'obsIDs[]'
            int counter = 0;
            for (BasicID id : waveforms) {
                if (id.getWaveformType().equals(WaveformType.OBS)) {
                    obsIDs[counter] = id;
                    counter++;
                }
            }
            // enter synthetic basicIDs in 'synIDs[]' in the same order as 'obsIDs[]'
            for (int i = 0; i < obsIDs.length; i++) {
                BasicID id = obsIDs[i];
                for (int j = 0; j < waveforms.length; j++) {
                    BasicID tmpid = waveforms[j];
                    if (!tmpid.getWaveformType().equals(WaveformType.SYN))
                        continue;
                    if (tmpid.getGlobalCMTID().equals(id.getGlobalCMTID())
                            && tmpid.getObserver().equals(id.getObserver())
                            && tmpid.getSacComponent().equals(id.getSacComponent())) {
                        synIDs[i] = tmpid;
                        break;
                    }
                }
            }

            int maxDistance = 180; // TODO change

            List<GlobalCMTID> events = Stream.of(obsIDs).map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toList());

            for (GlobalCMTID event : events) {

                // create plt files under each event directory in profilePath
                Path profileEventPath = profilePath.resolve(event.toString());
                Files.createDirectories(profileEventPath);
                PrintWriter pwProfile = new PrintWriter(Files.newBufferedWriter(
                        profileEventPath.resolve(event.toString() + ".plt"),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                pwProfile.println("set terminal postscript enhanced color font \"Helvetica,14\"");
                pwProfile.println("set output \"" + event.toString() + ".ps\"");
                pwProfile.println("unset key");
                pwProfile.println("set xlabel 'Time aligned on S-wave arrival (s)'");
                pwProfile.println("set ylabel 'Distance (deg)'");
                pwProfile.println("set size .5,1");
                pwProfile.print("p ");

                RealVector[] obsStacks = new ArrayRealVector[maxDistance];
                RealVector[] synStacks = new ArrayRealVector[maxDistance];

                for (int i = 0; i < obsIDs.length; i++) {
                    if (obsIDs[i].getGlobalCMTID().equals(event) && obsIDs[i].getSacComponent().equals(component)) {

                        // read waveform data
                        BasicID obsID = obsIDs[i];
                        BasicID synID = synIDs[i];
                        double[] obsData = obsID.getData();
                        double[] synData = synID.getData();
                        RealVector obsDataVector = new ArrayRealVector(obsData);
                        RealVector synDataVector = new ArrayRealVector(synData);

                        // output waveform data to txt file
                        String filename = obsID.getObserver() + "." + obsID.getGlobalCMTID() + "." + obsID.getSacComponent() + ".txt";
                        Path tracePath = profileEventPath.resolve(filename);
                        PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(tracePath,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                        for (int j = 0; j < obsData.length; j++)
                            pwTrace.printf("%d %.6e %.6e\n", j, obsData[j], synData[j]);
                        pwTrace.close();

                        // print gnuplot script for profile
                        double maxObs = obsDataVector.getLInfNorm();
                        double distance = obsID.getGlobalCMTID().getEventData().getCmtLocation()
                                .computeEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
                        pwProfile.println("\"" + filename + "\" " + String.format("u 1:($2/%.3e+%.2f) ", maxObs, distance)
                                + "w lines lw 1 lt 1 lc \"black\",\\");
                        pwProfile.println("\"" + filename + "\" " + String.format("u 1:($3/%.3e+%.2f) ", maxObs, distance)
                                + "w lines lw 1 lt 2 lc \"red\",\\");

                        // add to stack waveform
                        int k = (int) distance;
                        if (k < maxDistance) {
                            obsStacks[k] = (obsStacks[k] == null ? obsDataVector : add(obsStacks[k], obsDataVector));
                            synStacks[k] = (synStacks[k] == null ? synDataVector : add(synStacks[k], synDataVector));
                        }
                    }
                }
                pwProfile.println();
                pwProfile.close();

                // create plt files under each event directory in stackPath
                Path stackEventPath = stackPath.resolve(event.toString());
                Files.createDirectories(stackEventPath);
                PrintWriter pwStack = new PrintWriter(Files.newBufferedWriter(
                        stackEventPath.resolve(event.toString() + ".plt"),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                pwStack.println("set terminal postscript enhanced color font \"Helvetica,14\"");
                pwStack.println("set output \"" + event + ".ps\"");
                pwStack.println("unset key");
                pwStack.println("set xlabel 'Time aligned on S-wave arrival (s)'");
                pwStack.println("set ylabel 'Distance (deg)'");
                pwStack.println("set size .5,1");
                pwStack.print("p ");

                for (int i = 0; i < maxDistance; i++) {
                    if (obsStacks[i] != null) {

                        // print stack traces
                        String filename = i + "." + event.toString() + "." + component + ".txt";
                        Path tracePath = stackEventPath.resolve(filename);
                        PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(tracePath,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                        for (int j = 0; j < obsStacks[i].getDimension(); j++)
                            pwTrace.printf("%d %.6e %.6e\n", j, obsStacks[i].getEntry(j), synStacks[i].getEntry(j));
                        pwTrace.close();

                        // print gnuplot script for stack
                        double distance = i;
                        double maxObs = obsStacks[i].getLInfNorm();
                        pwStack.println("\"" + filename + "\" " + String.format("u 1:($2/%.3e+%.2f) ", maxObs, distance)
                                + "w lines lw 1 lt 1 lc \"black\",\\");
                        pwStack.println("\"" + filename + "\" " + String.format("u 1:($3/%.3e+%.2f) ", maxObs, distance)
                                + "w lines lw 1 lt 2 lc \"red\",\\");
                    }
                }
                pwStack.println();
                pwStack.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//	private static Trace stackTrace(List<Trace> traces) {
//		if (traces.size() == 0)
//			return null;
//		if (traces.size() == 1)
//			return traces.get(0);
//
//		Trace res = traces.get(0);
//		double xmin =
//		for (int i = 1; i < traces.size(); i++) {
//			if ()
//		}
//
//		return res;
//	}

    private static Trace add(Trace trace1, Trace trace2) {
        double[] x1 = trace1.getX();
        double[] x2 = trace2.getX();
        double start = x1[0] < x2[0] ? x2[0] : x1[0];
        double end = x1[x1.length] > x2[x2.length] ? x2[x2.length] : x1[x1.length];

        return trace1.cutWindow(start, end).add((trace2).cutWindow(start, end));
    }

    private static RealVector add(RealVector v1, RealVector v2) {
        RealVector res = null;

        if (v1.getDimension() == 0)
            res = v2;
        else if (v2.getDimension() == 0)
            res = v1;
        else
            res = v1.getDimension() > v2.getDimension() ? v2.add(v1.getSubVector(0, v2.getDimension()))
                    : v1.add(v2.getSubVector(0, v1.getDimension()));

        return res;
    }

    private static List<TimewindowData> findWindow(Set<TimewindowData> timewindows, SACFileName sacname) throws IOException {
        SACFileAccess data = sacname.read();
        Observer station = data.getObserver();
        GlobalCMTID id = data.getGlobalCMTID();
        SACComponent component = sacname.getComponent();
        return timewindows.stream().filter(tw -> tw.getGlobalCMTID().equals(id)
                && tw.getObserver().equals(station)
                && tw.getComponent().equals(component))
                .collect(Collectors.toList());
    }

//	private static Trace concat(List<Trace> traces) {
//		Trace res;
//
//		double timemax = Double.MIN_VALUE;
//		for (Trace tmp : traces) {
//			if (tmp.getX()[tmp.getLength() - 1] > timemax)
//				timemax = tmp.getX()[tmp.getLength() - 1];
//		}
//		double dt = traces.get(0).getXAt(1) - traces.get(0).getXAt(0);
//		if (dt != 0.05)
//			System.err.println("Warning: dt != 0.05");
//
//		int n = (int) (timemax / dt) + 1;
//		double[] x = new double[n];
//		double[] y = new double[n];
//
//		for (int i = 0; i < n; i++) {
//			x[i] = dt * i;
//
//		}
//	}
}
