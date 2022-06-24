package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.data.Raypath;

/**
 * File to specify the colors to use in visualizations.
 * <p>
 * Odd lines: name of color.
 * Even lines: value of limit of interval (int).
 * <p>
 *
 * @author otsuru
 * @since 2022/6/24
 */
public class ColorBinInformationFile {

    private int nSections;
    private int[] values;
    private String[] colors;

    /**
     * Writes a raypath list file given a set of raypaths.
     * @param raypathSet Set of raypaths
     * @param outPath  of write file
     * @param options  for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<Raypath> raypathSet, Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# Odd lines: name of color. Even lines: value of limit of interval (int).");
            raypathSet.stream().forEach(raypath -> {
                pw.println(raypath.getSource() + " " + raypath.getReceiver());
            });
        }
    }

    public ColorBinInformationFile(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath, true);

        int nLines = reader.getNumLines();
        if (nLines % 2 == 0) throw new IllegalStateException("The file should have odd number of lines");
        nSections = (nLines + 1) / 2;
        values = new int[nSections - 1];
        colors = new String[nSections];

        for (int i = 0 ; i < nSections; i++) {
            colors[i] = reader.next();
            if (i == nSections - 1) break;
            values[i] = Integer.parseInt(reader.next());
        }
    }

    public int getNSections() {
        return nSections;
    }

    public int getValueFor(int i) {
        return values[i];
    }

    public String getColorFor(int i) {
        return colors[i];
    }

}
