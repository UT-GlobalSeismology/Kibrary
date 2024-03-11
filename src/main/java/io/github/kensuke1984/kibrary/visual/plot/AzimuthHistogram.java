package io.github.kensuke1984.kibrary.visual.plot;

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
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
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
     * Creates histogram of azimuth based on a {@link DataEntryListFile}.
     * @param args Options.
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
        // histogram visual
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
        // type of azimuth to use
        OptionGroup azimuthOption = new OptionGroup();
        azimuthOption.addOption(Option.builder("b").longOpt("back")
                .desc("Use back azimuth").build());
        azimuthOption.addOption(Option.builder("t").longOpt("turning")
                .desc("Use turning point azimuth").build());
        options.addOptionGroup(azimuthOption);
        // TauP settings
        options.addOption(Option.builder("s").longOpt("structure").hasArg().argName("structure")
                .desc("Name of structure to use to compute turning point (prem)").build());
        options.addOption(Option.builder("p").longOpt("phase").hasArg().argName("phase")
                .desc("Name of phase to use to compute turning point (ScS)").build());

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
        double xtics = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("x")) : 30;
        double minimum = cmdLine.hasOption("m") ? Double.parseDouble(cmdLine.getOptionValue("m")) : 0;
        double maximum = cmdLine.hasOption("M") ? Double.parseDouble(cmdLine.getOptionValue("M")) : 180;
        boolean expand = cmdLine.hasOption("e");
        boolean useBackAzimuth = cmdLine.hasOption("b");
        boolean useTurningAzimuth = cmdLine.hasOption("t");
        String structureName = cmdLine.hasOption("s") ? cmdLine.getOptionValue("s") : "prem";
        String turningPointPhase = cmdLine.hasOption("p") ? cmdLine.getOptionValue("p") : "ScS";

        // if using turning point azimuth, compute using TauPPierce
        TauPPierceWrapper pierceTool = null;
        if (useTurningAzimuth) {
            try {
                pierceTool = new TauPPierceWrapper(structureName, turningPointPhase);
                pierceTool.compute(entrySet);
            } catch (TauModelException e) {
                throw new RuntimeException(e);
            }
        }

        // count number of records in each interval
        int[] numberOfRecords = new int[(int) Math.ceil(360 / interval)];
        for (DataEntry entry : entrySet) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();

            double azimuth;
            if (useTurningAzimuth) {
                if (pierceTool.hasRaypaths(entry)) {
                    // When there are several raypaths for a given phase name, the first arrival is chosen.
                    // When there are multiple bottoming points for a raypath, the first one is used.
                    // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                    azimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
                } else {
                    System.err.println("Cannot compute turning point for " + entry + ", skipping.");
                    continue;
                }
            } else if (useBackAzimuth) {
                azimuth = eventPosition.computeBackAzimuthDeg(observerPosition);
            } else {
                azimuth = eventPosition.computeAzimuthDeg(observerPosition);
            }

            if (!expand && azimuth > 180) azimuth -= 180;
            numberOfRecords[(int) (azimuth / interval)]++;
        }

        // output
        String fileNameRoot;
        String xlabel;
        if (useTurningAzimuth) {
            fileNameRoot = "turningAzimuthHistogram";
            xlabel = "Turning point azimuth";
        } else if (useBackAzimuth) {
            fileNameRoot = "backAzimuthHistogram";
            xlabel = "Back azimuth";
        } else {
            fileNameRoot = "azimuthHistogram";
            xlabel = "Azimuth";
        }
        Path outPath = Paths.get("");
        writeHistogramData(outPath, fileNameRoot, interval, numberOfRecords);
        createScript(outPath, fileNameRoot, xlabel, interval, minimum, maximum, xtics);

    }

    private static void writeHistogramData(Path outPath, String fileNameRoot, double interval, int[] numberOfRecords) throws IOException {
        Path txtPath = outPath.resolve(fileNameRoot + ".txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                pw.println(String.format("%.2f %d", i * interval, numberOfRecords[i]));
            }
        }
    }

    private static void createScript(Path outPath, String fileNameRoot, String xlabel,
            double interval, double minimum, double maximum, double xtics) throws IOException {
        Path scriptPath = outPath.resolve(fileNameRoot + ".plt");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,20'");
            pw.println("set xlabel '" + xlabel + " (deg)'");
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
