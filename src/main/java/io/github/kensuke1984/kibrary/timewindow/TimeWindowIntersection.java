package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;


/**
 * Pick up time windows for the same events and observers of 2 specified time window files.
 *
 * @since 2022/10/6
 * @author Rei
 */
public class TimeWindowIntersection {

    /**
     * Pick up time windows for the same events and observers.
     *
     * @param args [information file name]
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
        options.addOption(Option.builder("i1").longOpt("input1").hasArg().argName("inputTimeWindowFile1").required()
                .desc("The time window file to be intersected").build());
        options.addOption(Option.builder("i2").longOpt("input2").hasArg().argName("inputTimeWindowFile2").required()
                .desc("Another time window file to be intersected").build());
        // output
        options.addOption(Option.builder("o1").longOpt("output1").hasArg().argName("outputTimeWindowFile1")
                .desc("Set path of output time window file1").build());
        options.addOption(Option.builder("o2").longOpt("output2").hasArg().argName("outputTimeWindowFile2")
                .desc("Set path of output time window file2").build());

        options.addOption(Option.builder("ph").longOpt("phase").hasArg().argName("phase")
                .desc("Pick up same phase").build());
        options.addOption(Option.builder("c").longOpt("component").hasArg().argName("component")
                .desc("Pick up same component").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path inputPath1 = Paths.get(cmdLine.getOptionValue("i1"));
        Path inputPath2 = Paths.get(cmdLine.getOptionValue("i2"));

        Path outputPath1 = cmdLine.hasOption("o1") ? Paths.get(cmdLine.getOptionValue("o1"))
                : Paths.get("timeWindow1_" + GadgetAid.getTemporaryString() + ".dat");
        Path outputPath2 = cmdLine.hasOption("o2") ? Paths.get(cmdLine.getOptionValue("o2"))
                : Paths.get("timeWindow2_" + GadgetAid.getTemporaryString() + ".dat");

        boolean phase = cmdLine.hasOption("ph") ? true : false;
        boolean component = cmdLine.hasOption("c") ? true : false;

        Set<TimeWindowData> windows1 = TimeWindowDataFile.read(inputPath1);
        Set<TimeWindowData> windows2 = TimeWindowDataFile.read(inputPath2);

        Set<TimeWindowData> outWindows1 = new HashSet<>();
        Set<TimeWindowData> outWindows2 = new HashSet<>();

        // take intersections
        for (TimeWindowData window1 : windows1) {
            boolean exitIntersect = false;
            for (TimeWindowData window2 : windows2) {
                if (window1.getGlobalCMTID().equals(window2.getGlobalCMTID()) && window1.getObserver().equals(window2.getObserver())) {
                    if (phase && !window1.getPhases().equals(window2.getPhases())) continue;
                    if (component && !window1.getComponent().equals(window2.getComponent())) continue;

                    if (!outWindows2.contains(window2)) outWindows2.add(window2);
                    exitIntersect = true;
                }
            }
            if (exitIntersect)
                outWindows1.add(window1);
        }
        if (outWindows1.size() != outWindows2.size())
            throw new RuntimeException("Falled to make intersections");

        // output
        TimeWindowDataFile.write(outWindows1, outputPath1);
        TimeWindowDataFile.write(outWindows2, outputPath2);
    }

}
