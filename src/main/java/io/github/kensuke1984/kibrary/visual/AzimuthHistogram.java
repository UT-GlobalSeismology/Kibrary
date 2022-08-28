package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Creates histogram of records in a dataset by azimuth.
 * A {@link DataEntryListFile} is used as input.
 * <p>
 * By default, the number of records in the range [180:360) are overlapped on range [0:180).
 * (ex. A record with azimuth 240 is counted as having azimuth 60.)
 * This can be suppressed by setting the "expand" option.
 *
 * @since a long time ago
 * @version 2022/8/12 renamed and moved from util.statistics.HistogramAzimuth to visual.AzimuthHistogram
 */
public class AzimuthHistogram {

    /**
     * Creates histogram of azimuth based on a dataEntryFile.
     *
     * @param args
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // input
        options.addOption(Option.builder("d").longOpt("dataEntryFile").hasArg().argName("dataEntryFile").required()
                .desc("Path of data entry list file").build());

        // settings
        options.addOption(Option.builder("c").longOpt("components").hasArg().argName("components")
                .desc("Components to use, listed using commas (Z,R,T)").build());
        options.addOption(Option.builder("i").longOpt("interval").hasArg().argName("interval")
                .desc("Interval of azimuth in histogram (5)").build());
        options.addOption(Option.builder("x").longOpt("xtics").hasArg().argName("xtics")
                .desc("Interval of x tics (30)").build());
        options.addOption(Option.builder("m").longOpt("minAzimuth").hasArg().argName("minAzimuth")
                .desc("Minimum azimuth in histogram (0)").build());
        options.addOption(Option.builder("M").longOpt("maxAzimuth").hasArg().argName("maxAzimuth")
                .desc("Maximum azimuth in histogram (180)").build());
        options.addOption(Option.builder("e").longOpt("expand")
                .desc("Expand azimuth range to [0:360), not overlapping onto [0:180) range").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        Set<SACComponent> components = cmdLine.hasOption("c")
                ? Arrays.stream(cmdLine.getOptionValue("c").split(",")).map(SACComponent::valueOf).collect(Collectors.toSet())
                : SACComponent.componentSetOf("ZRT");

        Path dataEntryPath = Paths.get(cmdLine.getOptionValue("d"));
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());

        double interval = cmdLine.hasOption("i") ? Double.parseDouble(cmdLine.getOptionValue("i")) : 5;
        double xtics = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("i")) : 10;
        double minimum = cmdLine.hasOption("m") ? Double.parseDouble(cmdLine.getOptionValue("m")) : 0;
        double maximum = cmdLine.hasOption("M") ? Double.parseDouble(cmdLine.getOptionValue("M")) : 180;
        boolean expand = cmdLine.hasOption("e");

        // count number of records in each interval
        int[] numberOfRecords = new int[(int) Math.ceil(360 / interval)];
        for (DataEntry entry : entrySet) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtLocation();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            double azimuth = Math.toDegrees(eventPosition.computeAzimuth(observerPosition));
            if (!expand && azimuth > 180) azimuth -= 180;
            numberOfRecords[(int) (azimuth / interval)]++;
        }

        // output
        String fileNameRoot = "azimuthHistogram";
        Path outPath = Paths.get("");
        writeHistogramData(outPath, fileNameRoot, interval, numberOfRecords);
        createScript(outPath, fileNameRoot, interval, minimum, maximum, xtics);

    }

    private static void writeHistogramData(Path outPath, String fileNameRoot, double interval, int[] numberOfRecords) throws IOException {
        Path txtPath = outPath.resolve(fileNameRoot + ".txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                pw.println(String.format("%.2f %d", i * interval, numberOfRecords[i]));
            }
        }
    }

    private static void createScript(Path outPath, String fileNameRoot, double interval, double minimum, double maximum, double xtics) throws IOException {
        Path scriptPath = outPath.resolve(fileNameRoot + ".plt");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,14'");
            pw.println("set xlabel 'Azimuth (deg)'");
            pw.println("set ylabel 'Number of records'");
            pw.println("set xrange [" + minimum + ":" + maximum + "]");
            pw.println("set xtics " + xtics + " nomirror");
            pw.println("set ytics nomirror");
            pw.println("set style fill solid border lc rgb 'black'");
            pw.println("set sample 11");
            pw.println("set output '" + fileNameRoot + ".png'");
            pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'purple' notitle");
        }

        GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
        histogramPlot.execute();

//        GnuplotFile profilePlot = new GnuplotFile(scriptPath);
//
//        profilePlot.setOutput("png", fileNameRoot + ".png", 640, 480, false);
//        profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
//        profilePlot.unsetKey();
//        profilePlot.setXlabel("Azimuth (deg)");
//        profilePlot.setYlabel("Number of records");
//        profilePlot.setXrange(minimum, maximum);
    }

}
