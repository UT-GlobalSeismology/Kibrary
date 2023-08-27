package io.github.kensuke1984.kibrary.math;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * File with information of a vector.
 *
 * @author otsuru
 * @since 2022/7/4
 * @version 2023/8/27 Renamed from inversion.setup.AtdFile to math.VectorFile.
 */
public class VectorFile {

    public static void write(RealVector vector, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < vector.getDimension(); i++) {
                pw.println(vector.getEntry(i));
            }
        }
    }

    public static RealVector read(Path path) throws IOException {
        // read input file
        InformationFileReader reader = new InformationFileReader(path, true);
        String[] lines = reader.getNonCommentLines();

        // construct vector
        int dimension = lines.length;
        RealVector vector = new ArrayRealVector(dimension);

        // fill in values
        for (int i = 0; i < dimension; i++) {
            double val = Double.parseDouble(lines[i]);
            vector.setEntry(i, val);
        }

        return vector;
    }

}
