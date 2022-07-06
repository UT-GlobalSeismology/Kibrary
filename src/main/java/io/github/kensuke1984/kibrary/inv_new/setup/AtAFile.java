package io.github.kensuke1984.kibrary.inv_new.setup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * @author otsuru
 * @since 2022/7/4
 */
public class AtAFile {

    public static void write(RealMatrix ata, Path outputPath, OpenOption... options) throws IOException {
        if (!ata.isSquare()) throw new IllegalArgumentException("AtA must be square");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < ata.getRowDimension(); i++) {
                String[] rowAsString = Arrays.stream(ata.getRow(i)).mapToObj(String::valueOf).toArray(String[]::new);
                pw.println(String.join(" ", rowAsString));
            }
        }
    }

    public static RealMatrix read(Path path) throws IOException {
        InformationFileReader reader = new InformationFileReader(path, true);

        int dimension = reader.getNumLines();
        RealMatrix ata = new Array2DRowRealMatrix(dimension, dimension);
        for (int i = 0; i < dimension; i++) {
            if (!reader.hasNext()) throw new IllegalStateException("Failed to read enough rows");

            String[] rowAsString = reader.next().split("\\s+");
            double[] rowVec = Stream.of(rowAsString).mapToDouble(Double::parseDouble).toArray();
            ata.setRow(i, rowVec);
        }

        return ata;
    }
}
