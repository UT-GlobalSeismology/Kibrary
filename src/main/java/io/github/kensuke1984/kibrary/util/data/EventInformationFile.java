package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * File containing information of events.
 * <p>
 * Each line: globalCMTID, latitude, longitude, radius.
 * <p>
 * Only the globalCMTID is the part used to convey data;
 * the rest of the information is just for the users to see.
 *
 * @author ???
 * @since a long time ago
 */
public class EventInformationFile {

    /**
     * Writes an event information file given a set of GlobalCMTIDs.
     * @param eventSet Set of events
     * @param outPath  of write file
     * @param options  for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<GlobalCMTID> eventSet, Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# GCMTID latitude longitude radius");
            eventSet.stream().sorted().forEach(event -> {
                pw.println(event.toPaddedString() + " " + event.getEvent().getCmtLocation());
            });
        }
    }

    /**
     * Reads an event information file. Only the GlobalCMTID is read in; other information are ignored.
     * @param infoPath of event information file
     * @return (<b>unmodifiable</b>) Set of events
     * @throws IOException if an I/O error occurs
     *
     * @author otsuru
     * @since 2022/2/5
     */
    public static Set<GlobalCMTID> read(Path infoPath) throws IOException {
        Set<GlobalCMTID> eventSet = new HashSet<>();
        InformationFileReader reader = new InformationFileReader(infoPath, true);
        while(reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            GlobalCMTID event = new GlobalCMTID(parts[0]);
            if (!eventSet.add(event))
                throw new RuntimeException("There is duplication of " + event + " in " + infoPath + ".");
        }
        return Collections.unmodifiableSet(eventSet);
    }

    /**
     * Finds event directories under an input directory,
     * and creates an event information file under the working directory.
     *
     * @param args [input directory to collect events from]
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println("-----");
            usage().forEach(System.err::println);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return usage
     */
    public static List<String> usage() {
        List<String> usageList = new ArrayList<>();
        usageList.add("Usage: [datasetFolder]");
        usageList.add("  datasetFolder : Path of dataset folder containing event folders");
        return usageList;
    }

    /**
     * To be called from {@link Summon}.
     * @param args
     * @throws IOException
     */
    public static void run(String[] args) throws IOException {
        Path inPath;

        if (args.length == 1) {
            inPath = Paths.get(args[0]);
            if (!Files.exists(inPath) || !Files.isDirectory(inPath)) {
                System.err.println(inPath + " does not exist or is not a directory.");
                return;
            }
        } else if (args.length == 0){
            String pathString = "";
            do {
                pathString = GadgetAid.readInputDialogOrLine("Input folder?", pathString);
                if (pathString == null || pathString.isEmpty()) return;
                inPath = Paths.get(pathString);
            } while (!Files.exists(inPath) || !Files.isDirectory(inPath));
        } else {
            throw new IllegalArgumentException("Too many arguments");
        }

        createEventInformationFile(inPath);
    }

    private static void createEventInformationFile(Path inPath) throws IOException {
        Path outPath = Paths.get("event" + GadgetAid.getTemporaryString() + ".inf");
        Set<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(inPath);
        Set<GlobalCMTID> eventSet = eventFolderSet.stream().map(ef -> ef.getGlobalCMTID()).collect(Collectors.toSet());

        write(eventSet, outPath);
    }

}
