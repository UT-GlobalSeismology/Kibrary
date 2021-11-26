package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Utilities for handling a dataset folder that includes event folders.
 *
 * @since 2021/11/25 - created when Utilities.java was split up.
 */
public final class DatasetUtils {

    /**
     * Collect GlobalCMTIDs of event folders that exist under a given folder.
     * @param path {@link Path} for search of {@link GlobalCMTID}
     * @return <b>unmodifiable</b> Set of Global CMT IDs in the path
     * @throws IOException if an I/O error occurs
     */
    public static Set<GlobalCMTID> globalCMTIDSet(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            return Collections
                    .unmodifiableSet(stream.filter(dir -> GlobalCMTID.isGlobalCMTID(dir.getFileName().toString()))
                            .map(dir -> new GlobalCMTID(dir.getFileName().toString())).collect(Collectors.toSet()));
        }
    }

    /**
     * Collect EventFolders that exist under a given folder.
     * @param path Path of a folder containing event folders.
     * @return Set of {@link EventFolder} in the workPath
     * @throws IOException if an I/O error occurs
     */
    public static Set<EventFolder> eventFolderSet(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(file -> GlobalCMTID.isGlobalCMTID(file.getFileName().toString()))
                    .map(file -> new EventFolder(file.toString())).collect(Collectors.toSet());
        }
    }

    /**
     * Checks whether a given number of events is non-zero, and displays the number.
     * @param num (int) Number of events
     * @return  (boolean) true of the number is non-zero
     *
     * @author otsuru
     * @since 2021/11/25
     */
    public static boolean checkEventNum(int num) {
        if (num == 0) {
            System.err.println("No events found.");
            return false;
        } else if (num == 1) {
            System.err.println("1 event is found.");
            return true;
        } else {
            System.err.println(num + " events are found.");
            return true;
        }
    }

    /**
     * Collect all SAC files inside event folders under a given folder.
     * Errors in reading each event folder is just noticed. Such event folders
     * will be ignored.
     *
     * @param path of a folder containing event folders which have SAC files.
     * @return <b>Unmodifiable</b> Set of sac in event folders under the path
     * @throws IOException if an I/O error occurs.
     */
    public static Set<SACFileName> sacFileNameSet(Path path) throws IOException {
        return Collections.unmodifiableSet(eventFolderSet(path).stream().flatMap(eDir -> {
            try {
                return eDir.sacFileSet().stream();
            } catch (Exception e) {
                e.printStackTrace();
                return Stream.empty();
            }
        }).collect(Collectors.toSet()));
    }

    /**
     * Move SAC files that satisfies sacPredicate in event folders under the
     * path
     *
     * @param path      working path
     * @param predicate if true with a sacfile in event folders, the file is moved to
     *                  the directory.
     * @throws InterruptedException if the process takes over 30 minutes
     * @throws IOException          if an I/O error occurs
     */
    public static void moveSacfile(Path path, Predicate<SACFileName> predicate)
            throws IOException, InterruptedException {
        String directoryName = "movedSacfiles" + GadgetUtils.getTemporaryString();
        // System.out.println(directoryName);
        Consumer<EventFolder> moveProcess = eventDirectory -> {
            try {
                eventDirectory.moveSacFile(predicate, eventDirectory.toPath().resolve(directoryName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        ThreadUtils.runEventProcess(path, moveProcess, 30, TimeUnit.MINUTES);
    }

}
