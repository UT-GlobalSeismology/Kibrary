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
 * Pick up time windows which have same events and observers of 2 specified time window files.
 *
 * @author Rei
 * @since 2022/10/6
 */
public class TimewindowIntersection  {

    /**
     * Removes time windows of a time window file from those of another.
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
        options.addOption(Option.builder("i1").longOpt("input1").hasArg().argName("inputTimewindowFile1").required()
                .desc("The time window file to be intersected").build());
        options.addOption(Option.builder("i2").longOpt("input2").hasArg().argName("inputTimewindowFile2").required()
                .desc("Another time window file to be intersected").build());
        // output
        options.addOption(Option.builder("o1").longOpt("output1").hasArg().argName("outputTimewindowFile1")
                .desc("Set path of output time window file1").build());
        options.addOption(Option.builder("o2").longOpt("output2").hasArg().argName("outputTimewindowFile2")
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
                : Paths.get("timewindow1_" + GadgetAid.getTemporaryString() + ".dat");
        Path outputPath2 = cmdLine.hasOption("o2") ? Paths.get(cmdLine.getOptionValue("o2"))
                : Paths.get("timewindow2_" + GadgetAid.getTemporaryString() + ".dat");

        boolean phase = cmdLine.hasOption("ph") ? true : false;
        boolean component = cmdLine.hasOption("c") ? true : false;

        Set<TimewindowData> windows1 = TimewindowDataFile.read(inputPath1);
        Set<TimewindowData> windows2 = TimewindowDataFile.read(inputPath2);

        Set<TimewindowData> outWindows1 = new HashSet<>();
        Set<TimewindowData> outWindows2 = new HashSet<>();

        // take intersections
        for (TimewindowData window1 : windows1) {
            boolean exitIntersect = false;
            for (TimewindowData window2 : windows2) {
                if (window1.getGlobalCMTID().equals(window2.getGlobalCMTID()) && window1.getObserver().equals(window2.getObserver())) {
                    if (phase && !window1.getPhases().equals(window2.getPhases())) continue;
                    if (component && !window1.getComponent().equals(window2.getComponent())) continue;

                    if(!outWindows2.contains(window2)) outWindows2.add(window2);
                    exitIntersect = true;
                }
            }
            if (exitIntersect)
                outWindows1.add(window1);
        }
        if (outWindows1.size() != outWindows2.size())
            throw new RuntimeException("Falled to make intersections");

        // output
        TimewindowDataFile.write(outWindows1, outputPath1);
        TimewindowDataFile.write(outWindows2, outputPath2);
    }

}
