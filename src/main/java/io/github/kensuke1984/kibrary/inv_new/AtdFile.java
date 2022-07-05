package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * @author otsuru
 * @since 2022/7/4
 */
public class AtdFile {

    public static void write(RealVector atd, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < atd.getDimension(); i++) {
                pw.println(atd.getEntry(i));
            }
        }
    }

    public static RealVector read(Path path) throws IOException {
        InformationFileReader reader = new InformationFileReader(path, true);

        int dimension = reader.getNumLines();
        RealVector atd = new ArrayRealVector(dimension);
        for (int i = 0; i < dimension; i++) {
            if (!reader.hasNext()) throw new IllegalStateException("Failed to read enough rows");

            double val = Double.parseDouble(reader.next());
            atd.setEntry(i, val);
        }

        return atd;
    }

}
