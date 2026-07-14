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
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Plots number of records in {@link DataEntryListFile} by year.
 *
 * @since 2025/10/14
 * @author otsuru
 */
public class RecordNumberPlot {

    private static final int MIN_YEAR = 1950;
    private static final int MAX_YEAR = 2100;

    /**
     * Plots number of records in {@link DataEntryListFile} by year.
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
        options.addOption(Option.builder("e").longOpt("dataEntryFile").hasArg().argName("dataEntryFile")
                .desc("Path of data entry list file.").build());

        // settings
        options.addOption(Option.builder("c").longOpt("components").hasArg().argName("components")
                .desc("Components to use, listed using commas. (Z,R,T)").build());
        // histogram visual
        options.addOption(Option.builder("i").longOpt("interval").hasArg().argName("interval")
                .desc("Interval of year in plot. (1)").build());
        options.addOption(Option.builder("x").longOpt("xtics").hasArg().argName("xtics")
                .desc("Interval of x tics. (5)").build());
        options.addOption(Option.builder("m").longOpt("minYear").hasArg().argName("minYear")
                .desc("Minimum year in x axis. (1990)").build());
        options.addOption(Option.builder("M").longOpt("maxYear").hasArg().argName("maxYear")
                .desc("Maximum year in x axis. (2020)").build());

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

        int interval = cmdLine.hasOption("i") ? Integer.parseInt(cmdLine.getOptionValue("i")) : 1;
        int xtics = cmdLine.hasOption("x") ? Integer.parseInt(cmdLine.getOptionValue("x")) : 5;
        int minPlotYear = cmdLine.hasOption("m") ? Integer.parseInt(cmdLine.getOptionValue("m")) : 1990;
        int maxPlotYear = cmdLine.hasOption("M") ? Integer.parseInt(cmdLine.getOptionValue("M")) : 2020;

        // count up number of records per year
        int[] numberPerYear = new int[MAX_YEAR - MIN_YEAR + 1];
        int minExistingYear = MAX_YEAR;
        int maxExistingYear = MIN_YEAR;
        for (DataEntry entry : entrySet) {
            int year = entry.getEvent().getEventData().getCMTTime().getYear();
            if (year < MIN_YEAR || MAX_YEAR < year) throw new IllegalStateException("Year out of allowed range.");

            numberPerYear[year - MIN_YEAR]++;
            if (year < minExistingYear) minExistingYear = year;
            if (year > maxExistingYear) maxExistingYear = year;
        }

        // output
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "recordNumber", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("recordNumber.txt");
        Path scriptPath = outPath.resolve("recordNumber.plt");
        writeNumberData(txtPath, numberPerYear, minExistingYear, maxExistingYear);
        createScript(scriptPath, minPlotYear, maxPlotYear, xtics);
    }

    private static void writeNumberData(Path txtPath, int[] numberPerYear, int minExistingYear, int maxExistingYear) throws IOException {
        int sum = 0;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = minExistingYear; i <= maxExistingYear; i++) {
                sum += numberPerYear[i - MIN_YEAR];
                pw.println(i + " " + numberPerYear[i - MIN_YEAR] + " " + sum);
            }
        }
    }

    private static void createScript(Path scriptPath, double minPlotYear, double maxPlotYear, double xtics) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,20'");
            pw.println("set xlabel 'Year'");
            pw.println("set ylabel 'Cumulative number of records'");
            pw.println("set xrange [" + minPlotYear + ":" + maxPlotYear + "]");
            pw.println("set xtics " + xtics + " nomirror");
            pw.println("set ytics nomirror");
//            pw.println("set style fill solid border lc rgb 'black'");
//            pw.println("set sample 11");
            pw.println("set output '" + fileNameRoot + ".png'");
            pw.println("plot '" + fileNameRoot + ".txt' u 1:3 w lp lw 2.5 lc 'purple' notitle");
        }

        GnuplotFile numberPlot = new GnuplotFile(scriptPath);
        numberPlot.execute();
    }

}
