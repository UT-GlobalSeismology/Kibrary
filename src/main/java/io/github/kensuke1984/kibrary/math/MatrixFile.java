package io.github.kensuke1984.kibrary.math;

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
 * File with information of a matrix.
 *
 * @author otsuru
 * @since 2022/7/4
 * @version 2023/8/27 Renamed from inversion.setup.AtAFile to math.MatrixFile.
 */
public class MatrixFile {

    public static void write(RealMatrix matrix, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < matrix.getRowDimension(); i++) {
                String[] rowAsString = Arrays.stream(matrix.getRow(i)).mapToObj(String::valueOf).toArray(String[]::new);
                pw.println(String.join(" ", rowAsString));
            }
        }
    }

    public static RealMatrix read(Path inputPath) throws IOException {
        // read input file
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        String[] lines = reader.getNonCommentLines();

        // construct matrix
        int rowDimension = lines.length;
        int columnDimension = lines[0].split("\\s+").length;
        RealMatrix matrix = new Array2DRowRealMatrix(rowDimension, columnDimension);

        // fill in values
        for (int i = 0; i < rowDimension; i++) {
            String[] rowAsString = lines[i].split("\\s+");
            double[] rowVec = Stream.of(rowAsString).mapToDouble(Double::parseDouble).toArray();
            matrix.setRow(i, rowVec);
        }

        return matrix;
    }
}
