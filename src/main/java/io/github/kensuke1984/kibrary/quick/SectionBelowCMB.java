package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.ArrayRealVector;

import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Draw record section of waveforms at points below CMB.
 *
 * Name of sac files: "depthBelowCMB_longitude.event.Zsc"
 *
 * @author otsuru
 * @since 2022/1/13
 */
public class SectionBelowCMB {

    private static double sacSamplingHz = 2.5;
    private static double finalSamplingHz = 2.5;
    private static final GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);

    /**
     * @param args [longitude, startTime, npts(=length)]
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            throw new IllegalArgumentException("specify longitude, startTime, and npts");
        }

        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(Paths.get(""));
        if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
            return;
        }

        for (EventFolder eventDir : eventDirs) {
            System.err.println(eventDir.toString());
            section(eventDir, args[0], Double.parseDouble(args[1]), Integer.parseInt(args[2]));
        }
    }

    public static void section(EventFolder eventDir, String longitudeStr, double startTime, int npts) throws IOException {

        // network code should correspond to longitude
        Set<SACFileName> sacfilenames = eventDir.sacFileSet().stream()
                .filter(sac -> sac.getNetworkCode().equals(longitudeStr)).collect(Collectors.toSet());
        System.err.println(sacfilenames.size() + " sac files found.");

        Path sectionEventPath = Paths.get("section").resolve(eventDir.getName());
        System.err.println("Creating " + sectionEventPath);
        Files.createDirectories(sectionEventPath);

        String profileFileNameRoot = "profile_" + longitudeStr;
        GnuplotFile profilePlot = new GnuplotFile(sectionEventPath.resolve(profileFileNameRoot + ".plt"));
        profilePlot.setOutput("pdf", profileFileNameRoot + ".pdf", 21, 29.7, true);
        profilePlot.setMarginH(15, 25);
        profilePlot.setMarginV(15, 15);
        profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
        profilePlot.unsetCommonKey();

        profilePlot.setCommonTitle(eventDir.toString());
        profilePlot.setCommonXlabel("Time (s)");
        profilePlot.setCommonYlabel("Depth (km)");

        for (SACFileName name : sacfilenames) {

            // station code should correspond to depth
            double depth = Double.parseDouble(name.getStationCode());

            SACFileAccess synSac;
            try {
                synSac = name.read();
            } catch (IOException e1) {
                System.err.println("error occured in reading " + name);
                e1.printStackTrace();
                continue;
            }

            double[] synData = cutDataSac(synSac, startTime, npts);

            // output waveform in txt
            Path outputPath = sectionEventPath.resolve(name.getName() + ".txt");
            try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
                for (int j = 0; j < synData.length; j++) {
                    double synTime = startTime + j / finalSamplingHz;
                    pwTrace.println(synTime + " " + synData[j]);
                }
            }

            double synAmp = new ArrayRealVector(synData).getLInfNorm();

            String synUsingString = String.format("1:($2/%.3e+%.2f) ", synAmp, depth);
            profilePlot.addLine(outputPath.getFileName().toString(), synUsingString, synAppearance, "synthetic");
        }

        profilePlot.write();
        if (!profilePlot.execute()) System.err.println("gnuplot failed!!");
    }

    private static double[] cutDataSac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] waveData = trace.getY();
        return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
    }

}
