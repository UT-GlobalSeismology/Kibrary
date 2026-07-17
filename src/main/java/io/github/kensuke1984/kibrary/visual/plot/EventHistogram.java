package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Creates histogram of events, classified by depth and magnitude.
 * An {@link EventListFile} is used as input.
 *
 * @since 2024/11/1
 * @author otsuru
 */
public class EventHistogram {

    private static final int MAX_DEPTH = 800;
    private static final String[] COLORS = {"gray", "purple", "web-blue", "web-green", "gold", "orange", "red", "brown", "black"};

    /**
     * Creates histogram of events from an {@link EventListFile}.
     * @param args (String[]) Options.
     * @throws IOException
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
     * @return (Options) Options that can be specified by user.
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // input
        options.addOption(Option.builder("e").longOpt("eventFile").hasArg().argName("eventFile").required()
                .desc("Path of event list file.").build());

        // histogram visual
        options.addOption(Option.builder("i").longOpt("interval").hasArg().argName("interval")
                .desc("Interval of depth in histogram. (50)").build());
        options.addOption(Option.builder("x").longOpt("xtics").hasArg().argName("xtics")
                .desc("Interval of x tics. (100)").build());
        options.addOption(Option.builder("m").longOpt("minDepth").hasArg().argName("minDepth")
                .desc("Minimum depth in histogram. (0)").build());
        options.addOption(Option.builder("M").longOpt("maxDepth").hasArg().argName("maxDepth")
                .desc("Maximum depth in histogram. (" + MAX_DEPTH + ")").build());
        options.addOption(Option.builder("B").longOpt("boundaries").hasArg().argName("boundaries")
                .desc("Boundaries of magnitude, listed using commas. (5.0,5.5,6.0,6.5,7.0,7.5,8.0)").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("folderTag")
                .desc("A tag to include in output folder name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output folder name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine (CommandLine) Options specified by user.
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String folderTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFolderDate = !cmdLine.hasOption("O");

        Path eventPath = Paths.get(cmdLine.getOptionValue("e"));
        Set<GlobalCMTID> eventSet = EventListFile.read(eventPath);

        double dDepth = cmdLine.hasOption("i") ? Double.parseDouble(cmdLine.getOptionValue("i")) : 50;
        double xtics = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("x")) : 100;
        double minimum = cmdLine.hasOption("m") ? Double.parseDouble(cmdLine.getOptionValue("m")) : 0;
        double maximum = cmdLine.hasOption("M") ? Double.parseDouble(cmdLine.getOptionValue("M")) : MAX_DEPTH;
        double[] mwBorders = {5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0};
        if (cmdLine.hasOption("B")) {
            mwBorders = Arrays.stream(cmdLine.getOptionValue("B").split(",")).mapToDouble(Double::parseDouble).toArray();
        }

        // create 2-D array to store results
        int nMw = mwBorders.length - 1;
        int nDepth = (int) MathAid.ceil(MAX_DEPTH / dDepth);
        int[][] numberOfEvents = new int[nDepth][nMw];

        // classify each event in (depth, Mw) bins and count up
        for (GlobalCMTID event : eventSet) {
            // get depth and Mw of event
            GlobalCMTAccess eventData = event.getEventData();
            double depth = eventData.getCmtPosition().getDepth();
            double mw = eventData.getCmt().getMw();
            // skip if out of bounds of Mw range
            if (mw < mwBorders[0] || mwBorders[nMw] <= mw) continue;
            // find which interval the depth and Mw is in
            int iDepth = (int) MathAid.floor(depth / dDepth);
            int iMw = 0;
            for (int j = 1; j < nMw; j++) if (mw < mwBorders[j]) iMw++;
            // count up
            numberOfEvents[iDepth][iMw]++;
        }

        // output
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "eventHistogram", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("eventHistogram.txt");
        Path scriptPath = outPath.resolve("eventHistogram.plt");
        writeHistogramData(txtPath, dDepth, numberOfEvents);
        createScript(scriptPath, dDepth, minimum, maximum, xtics, mwBorders);
    }

    private static void writeHistogramData(Path txtPath, double interval, int[][] numberOfEvents) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfEvents.length; i++) {
                int nMw = numberOfEvents[i].length;
                String lineString = String.format("%.2f", i * interval);
                for (int j = 0; j < nMw; j++) lineString += " " + numberOfEvents[i][j];
                pw.println(lineString);
            }
        }
    }

    private static void createScript(Path scriptPath, double interval, double minimum, double maximum, double xtics, double[] mwBorders) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);
        int nMw = mwBorders.length - 1;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,20'");
            pw.println("set xlabel 'Depth (km)'");
            pw.println("set ylabel 'Number of events'");
            pw.println("set xrange [" + minimum + ":" + maximum + "]");
            pw.println("set xtics " + xtics);
            pw.println("set style fill solid border lc rgb 'black'");
            pw.println("set sample 11");
            pw.println("set output '" + fileNameRoot + ".png'");

            String numString = "$2";
            for (int j = 1; j < nMw; j++) {
                numString += "+$" + (j + 2);
            }
            for (int j = 0; j < nMw; j++) {
                String lineString = (j == 0) ? "plot '" : "     '";
                lineString += fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):(" + numString;
                lineString += ") w boxes lw 1 lc '" + COLORS[j];
                lineString += "' title 'Mw " + mwBorders[j] + "-" + mwBorders[j + 1];
                lineString += (j < nMw - 1) ? "', \\" : "'";
                pw.println(lineString);
                if (j < nMw - 1) numString = numString.substring(0, numString.lastIndexOf("+"));
            }
        }

        GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
        histogramPlot.execute();
    }

}
