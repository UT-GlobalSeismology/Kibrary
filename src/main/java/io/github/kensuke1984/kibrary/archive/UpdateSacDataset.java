package io.github.kensuke1984.kibrary.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Update SAC file name style of dataset to new style.
 *
 * @since 2026/7/30
 * @author otsuru
 */
public class UpdateSacDataset {

    /**
     * Update SAC file name style of dataset to new style.
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
        options.addOption(Option.builder("d").longOpt("dataset").hasArg().argName("datasetFolder").required()
                .desc("Input dataset folder containing event folders.").build());
        options.addOption(Option.builder("y").longOpt("minYear").hasArg().argName("minYear")
                .desc("Minimum year to copy. (0)").build());
        options.addOption(Option.builder("Y").longOpt("maxYear").hasArg().argName("maxYear")
                .desc("Maximum year to copy. (5000)").build());

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

        Path inPath = Paths.get(cmdLine.getOptionValue("d"));
        double minYear = cmdLine.hasOption("y") ? Double.parseDouble(cmdLine.getOptionValue("y")) : 0;
        double maxYear = cmdLine.hasOption("Y") ? Double.parseDouble(cmdLine.getOptionValue("Y")) : 5000;

        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "renamed", folderTag, appendFolderDate, null);
        Files.write(outPath.resolve("_UpdateSacDataset.txt"), ("Copied from " + inPath).getBytes());

        for (EventFolder eventDir : DatasetAid.eventFolderSet(inPath)) {
            // skip if year is not in range
            int year = eventDir.getGlobalCMTID().getEventData().getCMTTime().getYear();
            if (year < minYear || maxYear < year) continue;

            System.err.println(eventDir.getGlobalCMTID().toString());

            // collect SAC files
            Set<SACFileName> sacFileNames = eventDir.sacFileSet().stream().collect(Collectors.toSet());

            // create output event folder
            Path outEventPath = outPath.resolve(eventDir.getGlobalCMTID().toString());
            Files.createDirectories(outEventPath);

            for (SACFileName sacFileName : sacFileNames) {
                GlobalCMTID event = sacFileName.getGlobalCMTID();
                SACExtension extension = sacFileName.getExtension();
                Observer observer = sacFileName.readHeader().getObserver();

                // generate new name including network
                String newSacFileString = SACFileName.generate(observer, event, extension);

                //copy SAC file
                Files.copy(sacFileName.toPath(), outEventPath.resolve(newSacFileString));
            }
        }

    }

}
