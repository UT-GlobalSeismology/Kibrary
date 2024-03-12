package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Utilities for handling a dataset folder that includes event folders.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class DatasetAid {

    /**
     * Create a new output folder in specified path, with specified name root, tag, and date string.
     * Fails if a folder with the same name, including the same date string, already exists.
     * @param workPath (Path) Path to create the output folder under.
     * @param nameRoot (String) First part of output folder name.
     * @param tag (String) Additional comment to include in folder name. If null, this part will be excluded.
     * @param appendDate (boolean) Whether to append the date string in output folder name.
     *    Even if this is false, the date string will be appended if a folder with same name already exists.
     * @param dateString (String) The date string part of output folder name, must be set even when it will not be appended.
     * @return (Path) Path of created output folder.
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/4/13
     */
    public static Path createOutputFolder(Path workPath, String nameRoot, String tag, boolean appendDate, String dateString) throws IOException {
        String nondatedName = (tag == null) ? nameRoot : nameRoot + "_" + tag;
        String datedName = (tag == null) ? nameRoot + dateString : nameRoot + "_" + tag + "_" + dateString;

        // decide which output folder name to use
        // Even if appendDate is false, append date string if a folder with same name already exists.
        Path outPath;
        if (appendDate == false) {
            outPath = workPath.resolve(nondatedName);
            if (Files.exists(outPath)) {
                System.err.println("! " + outPath + " exists; appending date string.");
                outPath = workPath.resolve(datedName);
            }
        } else {
            outPath = workPath.resolve(datedName);
        }

        // check that a folder with same name does not exist
        if (Files.exists(outPath)) throw new IllegalStateException(outPath + " already exists!");

        // create folder
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);
        return outPath;
    }

    /**
     * Generates the name of a file with specified name root, tag, and date string.
     * Fails if a file with the same name, including the same date string, already exists.
     * @param workPath (Path) Path to create the output file under.
     * @param nameRoot (String) First part of output file name.
     * @param tag (String) Additional comment to include in file name. If null, this part will be excluded.
     * @param appendDate (boolean) Whether to append the date string in output file name.
     *    Even if this is false, the date string will be appended if a file with same name already exists.
     * @param dateString (String) The date string part of output file name.
     * @param extension (String) File extension.
     * @return (Path) Generated path of file.
     *
     * @author otsuru
     * @since 2022/4/13
     */
    public static Path generateOutputFilePath(Path workPath, String nameRoot, String tag, boolean appendDate, String dateString, String extension) {
        String nondatedName = ((tag == null) ? nameRoot : nameRoot + "_" + tag) + extension;
        String datedName = ((tag == null) ? nameRoot + dateString : nameRoot + "_" + tag + "_" + dateString) + extension;

        // decide which output file name to use
        // Even if appendDate is false, append date string if a file with same name already exists.
        Path outPath;
        if (appendDate == false) {
            outPath = workPath.resolve(nondatedName);
            if (Files.exists(outPath)) {
                System.err.println("! " + outPath + " exists; appending date string.");
                outPath = workPath.resolve(datedName);
            }
        } else {
            outPath = workPath.resolve(datedName);
        }

        // check that a file with same name does not exist
        if (Files.exists(outPath)) throw new IllegalStateException(outPath + " already exists!");

        return outPath;
    }

    /**
     * Collect GlobalCMTIDs of event folders that exist under a given folder.
     * @param path {@link Path} for search of {@link GlobalCMTID}
     * @return <b>unmodifiable</b> Set of Global CMT IDs in the path
     * @throws IOException if an I/O error occurs
     */
    public static Set<GlobalCMTID> globalCMTIDSet(Path path) throws IOException {
        // CAUTION: Files.list() must be in try-with-resources.
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
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(file -> GlobalCMTID.isGlobalCMTID(file.getFileName().toString()))
                    .map(file -> new EventFolder(file.toString())).collect(Collectors.toSet());
        }
    }

    /**
     * Checks whether a given number of some object is non-zero, and displays the number.
     * @param num (int) Number of some object
     * @param singular (String) Singular form of name of object
     * @param plural (String) Plural form of name of object
     * @return  (boolean) true of the number is non-zero
     *
     * @author otsuru
     * @since 2021/11/25
     */
    public static boolean checkNum(int num, String singular, String plural) {
        if (num == 0) {
            System.err.println("No " + plural + " found.");
            return false;
        } else {
            System.err.println(MathAid.switchSingularPlural(num, singular + " is", plural + " are") + " found.");
            return true;
        }
    }

    /**
     * Collect all SAC files inside event folders under a given folder.
     * Errors in reading each event folder is just noticed. Such event folders will be ignored.
     *
     * @param path of a folder containing event folders which have SAC files.
     * @return <b>Unmodifiable</b> Set of sac file names in event folders under the path
     * @throws IOException if an I/O error occurs.
     */
    public static Set<SACFileName> sacFileNameSet(Path path) throws IOException {
        Set<SACFileName> sacNameSet = Collections.unmodifiableSet(eventFolderSet(path).stream().flatMap(eDir -> {
            try {
                return eDir.sacFileSet().stream();
            } catch (Exception e) {
                e.printStackTrace();
                return Stream.empty();
            }
        }).collect(Collectors.toSet()));

        checkNum(sacNameSet.size(), "sac file", "sac files");
        return sacNameSet;
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
        String directoryName = "movedSacfiles" + GadgetAid.getTemporaryString();
        // System.out.println(directoryName);
        Consumer<EventFolder> moveProcess = eventDirectory -> {
            try {
                eventDirectory.moveSacFile(predicate, eventDirectory.toPath().resolve(directoryName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        ThreadAid.runEventProcess(path, moveProcess, 30, TimeUnit.MINUTES);
    }

    /**
     * Set up Map of event and observer pair information
     * by either reading a {@link DataEntryListFile} or collecting from a dataset folder.
     * Only entries with a component in the specified components will be considered.
     * @param entryPath (Path) A {@link DataEntryListFile}.
     * @param obsPath (Path) A dataset folder.
     * @param components (Set of {@link SACComponent}) Components to consider.
     * @return (Map of {@link GlobalCMTID} to Set of {@link Observer}) Created map of events to thier corresponding observers.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/4/5
     */
    public static Map<GlobalCMTID, Set<Observer>> setupArcMapFromFileOrFolder(Path entryPath, Path obsPath,
            Set<SACComponent> components) throws IOException {
        // create set of events and observers to set up DSM for
        Map<GlobalCMTID, Set<Observer>> arcMap = new HashMap<>();
        if (entryPath != null) {
            Map<GlobalCMTID, Set<DataEntry>> entryMap = DataEntryListFile.readAsMap(entryPath);
            // refill into Map<GlobalCMTID, Set<Observer>>
            for (GlobalCMTID event : entryMap.keySet()) {
                Set<Observer> observers = entryMap.get(event).stream()
                        .filter(entry -> components.contains(entry.getComponent()))
                        .map(DataEntry::getObserver).collect(Collectors.toSet());
                arcMap.put(event, observers);
            }
        } else if (obsPath != null){
            Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(obsPath);
            // collect observers for each event
            for (EventFolder eventDir : eventDirs) {
                Set<Observer> observers = eventDir.sacFileSet().stream()
                        .filter(name -> name.isOBS() && components.contains(name.getComponent()))
                        .map(name -> name.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                        .map(Observer::of).collect(Collectors.toSet());
                arcMap.put(eventDir.getGlobalCMTID(), observers);
            }
        } else {
            throw new IllegalStateException("Either entryPath or obsPath must be specified.");
        }
        return arcMap;
    }

    /**
     * An abstract class that can be used to execute tasks in filtered datasets for a set of timewindows.
     * @author otsuru
     * @since 2022/6/20
     */
    public abstract static class FilteredDatasetWorker implements Runnable {
        protected GlobalCMTID eventID;
        private Path obsEventPath;
        private Path synEventPath;
        private boolean convolved;
        private Set<TimewindowData> sourceTimewindowSet;

        public FilteredDatasetWorker(GlobalCMTID eventID, Path obsPath, Path synPath, boolean convolved, Set<TimewindowData> sourceTimewindowSet) {
            this.eventID = eventID;
            obsEventPath = obsPath.resolve(eventID.toString());
            synEventPath = synPath.resolve(eventID.toString());
            this.convolved = convolved;
            this.sourceTimewindowSet = sourceTimewindowSet;
        }

        /**
         * A class to implement the actual work that needs to be done to each timewindow
         * @param timewindow
         * @param obsSac
         * @param synSac
         */
        public abstract void actualWork(TimewindowData timewindow, SACFileAccess obsSac, SACFileAccess synSac);

        @Override
        public void run() {
            if (!Files.exists(obsEventPath)) {
                new NoSuchFileException(obsEventPath.toString()).printStackTrace();
                return;
            }
            if (!Files.exists(synEventPath)) {
                new NoSuchFileException(synEventPath.toString()).printStackTrace();
                return;
            }

            // pick out time windows of this event
            Set<TimewindowData> timewindows = sourceTimewindowSet.stream()
                    .filter(info -> info.getGlobalCMTID().equals(eventID)).collect(Collectors.toSet());

            for (TimewindowData timewindow : timewindows) {
                Observer observer = timewindow.getObserver();
                SACComponent component = timewindow.getComponent();

                // get observed data
                SACExtension obsExt = SACExtension.valueOfObserved(component);
                SACFileName obsName = new SACFileName(obsEventPath.resolve(SACFileName.generate(observer, eventID, obsExt)));
                if (!obsName.exists()) {
                    System.err.println();
                    System.err.println("!! " + obsName + " does not exist, skippping.");
                    continue;
                }
                SACFileAccess obsSac;
                try {
                    obsSac = obsName.read();
                } catch (Exception e) {
                    System.err.println();
                    System.err.println("!! Could not read " + obsName + " , skipping.");
                    e.printStackTrace();
                    continue;
                }

                // get synthetic data
                SACExtension synExt = convolved ? SACExtension.valueOfConvolutedSynthetic(component)
                        : SACExtension.valueOfSynthetic(component);
                SACFileName synName = new SACFileName(synEventPath.resolve(SACFileName.generate(observer, eventID, synExt)));
                if (!synName.exists()) {
                    System.err.println();
                    System.err.println("!! " + synName + " does not exist, skippping.");
                    continue;
                }
                SACFileAccess synSac;
                try {
                    synSac = synName.read();
                } catch (Exception e) {
                    System.err.println();
                    System.err.println("!! Could not read " + synName + " , skipping.");
                    e.printStackTrace();
                    continue;
                }

                actualWork(timewindow, obsSac, synSac);
            }

            System.err.print(".");
        }
    }
}
