package io.github.kensuke1984.kibrary.util.data;

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
 * Extracts {@link DataEntry}s that exist in all input {@link DataEntryListFile}s.
 *
 * @author otsuru
 * @since 2023/10/30
 */
public class DataEntryIntersection {

    /**
     * Extracts {@link DataEntry}s that exist in all input {@link DataEntryListFile}s.
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
        options.addOption(Option.builder("a").longOpt("entry1").hasArg().argName("dataEntryFile").required()
                .desc("First data entry list file.").build());
        options.addOption(Option.builder("b").longOpt("entry2").hasArg().argName("dataEntryFile").required()
                .desc("Second data entry list file.").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path entryPath1 = Paths.get(cmdLine.getOptionValue("a"));
        Path entryPath2 = Paths.get(cmdLine.getOptionValue("b"));

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("dataEntry" + GadgetAid.getTemporaryString() + ".lst");

        Set<DataEntry> entrySet1 = DataEntryListFile.readAsSet(entryPath1);
        Set<DataEntry> entrySet2 = DataEntryListFile.readAsSet(entryPath2);

        Set<DataEntry> outEntrySet = new HashSet<>();
        for (DataEntry entry : entrySet1) {
            if (entrySet2.contains(entry)) {
                outEntrySet.add(entry);
            }
        }

        DataEntryListFile.writeFromSet(outEntrySet, outputPath);
    }

}
