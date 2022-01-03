package io.github.kensuke1984.kibrary.abandon;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.FileAid;

/**
 * @author otsuru
 * @since 2022/1/3
 */
public class LobbyCleanup {

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("-d")) {
            organize_temp();
        } else if (args.length == 1 && args[0].equals("-t")) {
            organize_temp();
        } else {
            System.err.println("Usage:");
            System.err.println(" [-d] : delete sacs and resps");
            return;
        }
    }

    private static void organize_temp() throws IOException {
        Path workPath = Paths.get(".");

        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(workPath);
        if (!DatasetAid.checkEventNum(eventDirs.size())) {
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
