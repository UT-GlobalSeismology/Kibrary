package io.github.kensuke1984.kibrary.abandon;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;

/**
 * Class to clean data lobby folders when they are not needed any more.
 * This deletes the sac/ and resp/ folders.
 * Alternatively, this can copy mseed/ and station/ folders to a specified directory.
 * All event directories under the current directory will be processed.
 *
 * @since 2022/1/3
 * @author otsuru
 */
public class LobbyCleanup {

    /**
     * Clean up data lobby folders when they are not needed any more.
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

        options.addOption(Option.builder("c").hasArg().argName("outPath")
                .desc("Copy mseeds and stationXMLs into new dataset folder.").build());
        options.addOption(Option.builder("d").longOpt("delete")
                .desc("Delete sacs and resps.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        if (cmdLine.hasOption("c")) copyNeeded(cmdLine.getOptionValue("c"));
        if (cmdLine.hasOption("d")) deleteUnneeded();
    }

    /**
     * Copy mseed/ and station/ folders to a specified directory.
     * @param output
     * @throws IOException
     */
    private static void copyNeeded(String output) throws IOException {
        Path outPath = Paths.get(output);
        Path inPath = Paths.get(".");
        List<EventFolder> inEventDirs = DatasetAid.eventFolderSet(inPath).stream()
                .sorted(Comparator.comparing(EventFolder::getGlobalCMTID)).collect(Collectors.toList());
        if (!DatasetAid.checkNum(inEventDirs.size(), "event", "events")) {
            return;
        }

        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        int n = 0;
        for (EventFolder inEventDir : inEventDirs) {
            n++;
            System.err.print("\r " + inEventDir.getGlobalCMTID() + " (" + n + " / " + inEventDirs.size() + ")");

            // copy mseed/...
            Path outMseedDirPath = outPath.resolve(inEventDir.toString()).resolve("mseed");
            try (DirectoryStream<Path> inMseedPaths = Files.newDirectoryStream(inEventDir.toPath().resolve("mseed"), "*.mseed")) {
                for (Path inMseedPath : inMseedPaths) {
                    Files.createDirectories(outMseedDirPath);
                    Files.copy(inMseedPath, outMseedDirPath.resolve(inMseedPath.getFileName().toString()));
                }
            }
            // copy station/...
            Path outStationDirPath = outPath.resolve(inEventDir.toString()).resolve("station");
            try (DirectoryStream<Path> inXmlPaths = Files.newDirectoryStream(inEventDir.toPath().resolve("station"), "*.xml")) {
                for (Path inXmlPath : inXmlPaths) {
                    Files.createDirectories(outStationDirPath);
                    Files.copy(inXmlPath, outStationDirPath.resolve(inXmlPath.getFileName().toString()));
                }
            }
        }
        System.err.println("\r Finished copying all events.");
    }

    /**
     * Delete sac/ and resp/ folders.
     * @throws IOException
     */
    private static void deleteUnneeded() throws IOException {
        Path workPath = Paths.get(".");
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(workPath);
        if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
            return;
        }

        for (EventFolder eventDir : eventDirs) {
            System.err.println(eventDir.toString());

            Path sacDirPath = eventDir.toPath().resolve("sac");
            FileUtils.deleteDirectory(sacDirPath.toFile());

            Path respDirPath = eventDir.toPath().resolve("resp");
            FileUtils.deleteDirectory(respDirPath.toFile());

            //TODO the following will become unneeded
            Path sacDirPath2 = eventDir.toPath().resolve("old_sac");
            FileUtils.deleteDirectory(sacDirPath2.toFile());
            Path respDirPath2 = eventDir.toPath().resolve("old_resp");
            FileUtils.deleteDirectory(respDirPath2.toFile());
            Path stationDirPath2 = eventDir.toPath().resolve("old_station");
            FileUtils.deleteDirectory(stationDirPath2.toFile());
        }
    }

}
