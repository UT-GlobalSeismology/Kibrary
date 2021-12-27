package io.github.kensuke1984.kibrary.util.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 *
 * @author ???
 */
public class EventInformationFile {

    /**
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
