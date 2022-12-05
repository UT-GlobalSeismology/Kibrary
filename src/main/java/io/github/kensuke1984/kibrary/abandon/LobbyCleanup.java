package io.github.kensuke1984.kibrary.abandon;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.FileAid;

/**
 * @author otsuru
 * @since 2022/1/3
 */
public class LobbyCleanup {


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

        options.addOption(Option.builder("d").longOpt("delete")//TODO required
                .desc("Delete sacs and resps").build());
        options.addOption(Option.builder("t")
                .desc("Old file structure to new").build());//TODO erase
        options.addOption(Option.builder("c").hasArg().argName("outPath")
                .desc("Copy mseeds into new dataset folder").build());//TODO erase

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        if (cmdLine.hasOption("t")) organize_temp();//TODO erase
        if (cmdLine.hasOption("c")) copyMseeds(cmdLine.getOptionValue("c"));//TODO erase

        if (!cmdLine.hasOption("d")) return;

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

    /**
     * @param input
     * @param output
     * @throws IOException
     * @deprecated
     */
    private static void copyMseeds(String input) throws IOException {
        Path inPath = Paths.get(input);
        Path outPath = Paths.get(input + "new");

        Set<EventFolder> inEventDirs = DatasetAid.eventFolderSet(inPath);
        if (!DatasetAid.checkNum(inEventDirs.size(), "event", "events")) {
            return;
        }

        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        for (EventFolder inEventDir : inEventDirs) {
            Path outMseedDirPath = outPath.resolve(inEventDir.toString()).resolve("mseed");
            try (DirectoryStream<Path> inMseedPaths = Files.newDirectoryStream(inEventDir.toPath().resolve("mseed"), "*.mseed")) {
                for (Path inMseedPath : inMseedPaths) {
                    Files.createDirectories(outMseedDirPath);
                    Files.copy(inMseedPath, outMseedDirPath.resolve(inMseedPath.getFileName().toString()));
                }
            }
        }

    }

    /**
     * @throws IOException
     * @deprecated
     */
    private static void organize_temp() throws IOException {
        Path workPath = Paths.get(".");

        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(workPath);
        if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
            return;
        }

        for (EventFolder eventDir : eventDirs) {

            Path mseedDirPath = eventDir.toPath().resolve("mseed");
            Files.createDirectories(mseedDirPath);
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    FileAid.moveToDirectory(mseedPath, mseedDirPath, true);
                }
            }

            Path sacDirPath = eventDir.toPath().resolve("old_sac");
            Files.createDirectories(sacDirPath);
            try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(eventDir.toPath(), "*.SAC")) {
                for (Path sacPath : sacPaths) {
                    FileAid.moveToDirectory(sacPath, sacDirPath, true);
                }
            }

            Path respDirPath = eventDir.toPath().resolve("old_resp");
            Files.createDirectories(respDirPath);
            try (DirectoryStream<Path> respPaths = Files.newDirectoryStream(eventDir.toPath(), "RESP.*")) {
                for (Path respPath : respPaths) {
                    FileAid.moveToDirectory(respPath, respDirPath, true);
                }
            }

            Path stationDirPath = eventDir.toPath().resolve("old_station");
            Files.createDirectories(stationDirPath);
            try (DirectoryStream<Path> stationPaths = Files.newDirectoryStream(eventDir.toPath(), "STATION.*")) {
                for (Path stationPath : stationPaths) {
                    FileAid.moveToDirectory(stationPath, stationDirPath, true);
                }
            }

        }
    }
}
