package io.github.kensuke1984.kibrary.util.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.apache.commons.io.input.CloseShieldInputStream;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
        try (BufferedReader br = Files.newBufferedReader(infoPath)) {
            br.lines().map(String::trim).filter(line -> !line.startsWith("#")).forEach(line -> {
                String[] parts = line.split("\\s+");
                GlobalCMTID event = new GlobalCMTID(parts[0]);
                if (!eventSet.add(event))
                    throw new RuntimeException("There is duplication of " + event + " in " + infoPath + ".");
            });
        }
        return Collections.unmodifiableSet(eventSet);
    }

    /**
     * Finds event directories under a working directory,
     * and creates an event information file under the working directory.
     *
     * @param args [working directory to collect events from]
     * @throws IOException if an I/O error occurs
     */
    public static void main (String[] args) throws IOException {
        if (0 < args.length) {
            String path = args[0];
            if (!path.startsWith("/"))
                path = System.getProperty("user.dir") + "/" + path;
            Path f = Paths.get(path);
            if (Files.exists(f) && Files.isDirectory(f))
                createEventInformationFile(f);
            else
                System.err.println(f + " does not exist or is not a directory.");
        } else {
            Path workPath;
            String path = "";
            do {
                try {
                    path = JOptionPane.showInputDialog("Working folder?", path);
                } catch (Exception e) {
                    System.err.println("Working folder?");
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(CloseShieldInputStream.wrap(System.in)))) {
                        path = br.readLine().trim();
                        if (!path.startsWith("/"))
                            path = System.getProperty("user.dir") + "/" + path;
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        throw new RuntimeException();
                    }
                }
                if (path == null || path.isEmpty())
                    return;
                workPath = Paths.get(path);
                if (!Files.isDirectory(workPath))
                    continue;
            } while (!Files.exists(workPath) || !Files.isDirectory(workPath));
            createEventInformationFile(workPath);
        }
    }

    private static void createEventInformationFile(Path workPath, OpenOption... options) throws IOException {
        Path outPath = workPath.resolve("event" + GadgetAid.getTemporaryString() + ".inf");
        Set<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(workPath);
        Set<GlobalCMTID> eventSet = eventFolderSet.stream().map(ef -> ef.getGlobalCMTID()).collect(Collectors.toSet());

        write(eventSet, outPath, options);
    }

}
