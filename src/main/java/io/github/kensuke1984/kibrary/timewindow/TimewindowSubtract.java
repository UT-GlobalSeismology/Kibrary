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
 * Removes timewindows of a timewindow file from those of another.
 *
 * @since a long time ago
 * @version 2022/8/29 moved & renamed from quick.Subtractwindow to timewindow.TimewindowSubtract
 */
public class TimewindowSubtract {

    /**
     * Removes timewindows of a timewindow file from those of another.
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
        options.addOption(Option.builder("a").longOpt("original").hasArg().argName("originalTimewindowFile").required()
                .desc("The original timewindow file").build());
        options.addOption(Option.builder("b").longOpt("subtract").hasArg().argName("subtractTimewindowFile").required()
                .desc("The timewindow file to be subtracted").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path originalPath = Paths.get(cmdLine.getOptionValue("a"));
        Path subtractPath = Paths.get(cmdLine.getOptionValue("b"));

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("timewindow" + GadgetAid.getTemporaryString() + ".dat");

        Set<TimewindowData> originalWindows = TimewindowDataFile.read(originalPath);
        Set<TimewindowData> subtractWindows = TimewindowDataFile.read(subtractPath);

        Set<TimewindowData> outWindows = new HashSet<>();
        for (TimewindowData window : originalWindows) {
            if (!subtractWindows.contains(window))
                outWindows.add(window);
        }
        TimewindowDataFile.write(outWindows, outputPath);
    }

}
