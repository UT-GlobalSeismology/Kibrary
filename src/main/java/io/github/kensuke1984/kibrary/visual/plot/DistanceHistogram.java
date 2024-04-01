package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.inversion.EntryWeightListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Creates histogram of records in a dataset by epicentral distance.
 * A {@link DataEntryListFile} is used as input.
 * <p>
 * Weights for each bin can be decided in "weighting" mode. The weights will be exported in {@link EntryWeightListFile}.
 *
 * @since a long time ago
 * @version 2022/8/12 renamed and moved from util.statistics.Histogram to visual.DistanceHistogram
 */
public class DistanceHistogram {

    /**
     * Creates histogram of epicentral distance based on a {@link DataEntryListFile}.
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
        options.addOption(Option.builder("e").longOpt("dataEntryFile").hasArg().argName("dataEntryFile").required()
                .desc("Path of data entry list file.").build());

        // settings
        options.addOption(Option.builder("c").longOpt("components").hasArg().argName("components")
                .desc("Components to use, listed using commas. (Z,R,T)").build());
        // histogram visual
        options.addOption(Option.builder("i").longOpt("interval").hasArg().argName("interval")
                .desc("Interval of distance in histogram. (2)").build());
        options.addOption(Option.builder("x").longOpt("xtics").hasArg().argName("xtics")
                .desc("Interval of x tics. (10)").build());
        options.addOption(Option.builder("m").longOpt("minDistance").hasArg().argName("minDistance")
                .desc("Minimum distance in histogram. (0)").build());
        options.addOption(Option.builder("M").longOpt("maxDistance").hasArg().argName("maxDistance")
                .desc("Maximum distance in histogram. (180)").build());
        // weighting
        options.addOption(Option.builder("w").longOpt("weight")
                .desc("Decide weights.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("folderTag")
                .desc("A tag to include in output folder name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output folder name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String folderTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFolderDate = !cmdLine.hasOption("O");
        Set<SACComponent> components = cmdLine.hasOption("c")
                ? Arrays.stream(cmdLine.getOptionValue("c").split(",")).map(SACComponent::valueOf).collect(Collectors.toSet())
                : SACComponent.componentSetOf("ZRT");

        Path dataEntryPath = Paths.get(cmdLine.getOptionValue("e"));
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());

        double interval = cmdLine.hasOption("i") ? Double.parseDouble(cmdLine.getOptionValue("i")) : 2;
        double xtics = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("i")) : 10;
        double minimum = cmdLine.hasOption("m") ? Double.parseDouble(cmdLine.getOptionValue("m")) : 0;
        double maximum = cmdLine.hasOption("M") ? Double.parseDouble(cmdLine.getOptionValue("M")) : 180;
        boolean conductWeighting = cmdLine.hasOption("w");

        // count number of records in each interval
        int[] numberOfRecords = new int[(int) MathAid.ceil(360 / interval)];
        Map<DataEntry, Double> distanceMap = new HashMap<>();
        for (DataEntry entry : entrySet) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            double epicentralDistance = Math.toDegrees(eventPosition.computeEpicentralDistanceRad(observerPosition));
            numberOfRecords[(int) (epicentralDistance / interval)]++;
            distanceMap.put(entry, epicentralDistance);
        }

        // decide weights
        double[] weights = decideWeights(numberOfRecords, conductWeighting);
        Map<DataEntry, Double> weightMap = new HashMap<>();
        for (DataEntry entry : entrySet) {
            double weight = weights[(int) (distanceMap.get(entry) / interval)];
            weightMap.put(entry, weight);
        }

        // output
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "distHistogram", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("distHistogram.txt");
        Path scriptPath = outPath.resolve("distHistogram.plt");
        Path weightPath = outPath.resolve("entryWeight_dist.lst");
        writeHistogramData(txtPath, interval, numberOfRecords, weights);
        createScript(scriptPath, interval, minimum, maximum, xtics, conductWeighting);
        if (conductWeighting) EntryWeightListFile.write(weightMap, weightPath);
    }

    private static void writeHistogramData(Path txtPath, double interval, int[] numberOfRecords, double[] weights) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                pw.println(String.format("%.2f %d %.1f", i * interval, numberOfRecords[i], numberOfRecords[i] * weights[i]));
            }
        }
    }

    private static void createScript(Path scriptPath, double interval, double minimum, double maximum, double xtics,
            boolean conductWeighting) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,20'");
            pw.println("set xlabel 'Epicentral distance (deg)'");
            pw.println("set ylabel 'Number of records'");
            pw.println("set xrange [" + minimum + ":" + maximum + "]");
            pw.println("set xtics " + xtics + " nomirror");
            pw.println("set ytics nomirror");
            pw.println("set style fill solid border lc rgb 'black'");
            pw.println("set sample 11");
            pw.println("set output '" + fileNameRoot + ".png'");
            if (conductWeighting) {
                pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'sea-green' title 'raw', \\");
                pw.println("     '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):3 w boxes fs transparent pattern 4 "
                        + "lw 1.0 lc 'red' title 'weighted'");
            } else {
                pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'sea-green' notitle");
            }
        }

        GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
        histogramPlot.execute();

//        profilePlot.setOutput("png", fileNameRoot + ".png", 640, 480, false);
//        profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
//        profilePlot.unsetKey();
//        profilePlot.setXlabel("Epicentral distance (deg)");
//        profilePlot.setYlabel("Number of records");
//        profilePlot.setXrange(minimum, maximum);
    }

    private static double[] decideWeights(int[] numberOfRecords, boolean weigh) {
        double[] weights = new double[numberOfRecords.length];
        if (weigh) {
            // average number of records in each bin (only bins with at least 1 record are considered)
            double average = Arrays.stream(numberOfRecords).filter(n -> n > 0).asDoubleStream().average().getAsDouble();
            // weight for each bin
            for (int i = 0; i < weights.length; i++) {
                if (numberOfRecords[i] > 0) {
                    double x = numberOfRecords[i] / average;
                    double weight = (1.0 - Math.exp(-3.0 * x)) / (1.0 - Math.exp(-3.0)) / x;
                    weights[i] = Precision.round(weight, 3);
                } else {
                    weights[i] = 0.0;
                }
            }
        } else {
            // when not weighting, set all weights to 1
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1.0;
            }
        }
        return weights;
    }

}
