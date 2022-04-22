package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

/**
 * File containing list of raypaths.
 * <p>
 * Each line: source[latitude, longitude, radius], reciever[latitude, longitude].
 * <p>
 *
 * @author otsuru
 * @since 2022/4/22
 */
public class RaypathListFile {
    private RaypathListFile() {}

    /**
     * Writes a raypath list file given a set of raypaths.
     * @param raypathSet Set of raypaths
     * @param outPath  of write file
     * @param options  for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<Raypath> raypathSet, Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# source[latitude, longitude, radius], reciever[latitude, longitude]");
            raypathSet.stream().sorted().forEach(raypath -> {
                pw.println(raypath.getSource() + " " + raypath.getReceiver());
            });
        }
    }


}
