package io.github.kensuke1984.kibrary.math;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
        System.err.println("Writing in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < matrix.getRowDimension(); i++) {
                String[] rowAsString = Arrays.stream(matrix.getRow(i)).mapToObj(String::valueOf).toArray(String[]::new);
                pw.println(String.join(" ", rowAsString));
            }
        }
    }

    public static RealMatrix read(Path inputPath) throws IOException {
        System.err.println("Reading " + inputPath);

        // read input file
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        String[] lines = reader.getNonCommentLines();

        // construct matrix
        int rowDimension = lines.length;
        int columnDimension = lines[0].split("\\s+").length;
        RealMatrix matrix = MatrixUtils.createRealMatrix(rowDimension, columnDimension);

        // fill in values
        for (int i = 0; i < rowDimension; i++) {
            String[] rowAsString = lines[i].split("\\s+");
            double[] rowVec = Stream.of(rowAsString).mapToDouble(Double::parseDouble).toArray();
            matrix.setRow(i, rowVec);
        }

        return matrix;
    }

    /**
     * Create template matrix file.
     * @param args Options.
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // dimension
        options.addOption(Option.builder("s").longOpt("squareDimension").hasArg().argName("squareDimension")
                .desc("Dimension of square matrix.").build());
        options.addOption(Option.builder("r").longOpt("rowDimension").hasArg().argName("rowDimension")
                .desc("Row dimension of matrix.").build());
        options.addOption(Option.builder("c").longOpt("columnDimension").hasArg().argName("columnDimension")
                .desc("Column dimension of matrix.").build());

        // components
        options.addOption(Option.builder("d").longOpt("diagonal").hasArg().argName("diagonal")
                .desc("Value to add to diagonal components.").build());
        options.addOption(Option.builder("l").longOpt("lowerDiagonal").hasArg().argName("lowerDiagonal")
                .desc("Value to add to lower diagonal components.").build());
        options.addOption(Option.builder("u").longOpt("upperDiagonal").hasArg().argName("upperDiagonal")
                .desc("Value to add to upper diagonal components.").build());
        options.addOption(Option.builder("a").longOpt("all").hasArg().argName("all")
                .desc("Value to add to all components.").build());

        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        // decide matrix dimension
        int rowDimension, columnDimension;
        if (cmdLine.hasOption("s")) {
            rowDimension = columnDimension = Integer.parseInt(cmdLine.getOptionValue("s"));
        } else if (cmdLine.hasOption("r") && cmdLine.hasOption("c")) {
            rowDimension = Integer.parseInt(cmdLine.getOptionValue("r"));
            columnDimension = Integer.parseInt(cmdLine.getOptionValue("c"));
        } else {
            throw new IllegalArgumentException("Either -s, or both -r and -c, is needed.");
        }

        // construct zero-matrix
        RealMatrix matrix = MatrixUtils.createRealMatrix(rowDimension, columnDimension);

        // diagonal components
        if (cmdLine.hasOption("d")) {
            double value = Double.parseDouble(cmdLine.getOptionValue("d"));
            int n = Math.min(rowDimension, columnDimension);
            for (int i = 0; i < n; i++) {
                matrix.addToEntry(i, i, value);
            }
        }
        // lower diagonal components
        if (cmdLine.hasOption("l")) {
            double value = Double.parseDouble(cmdLine.getOptionValue("l"));
            int n = (rowDimension <= columnDimension) ? rowDimension - 1 : columnDimension;
            for (int i = 0; i < n; i++) {
                matrix.addToEntry(i + 1, i, value);
            }
        }
        // upper diagonal components
        if (cmdLine.hasOption("u")) {
            double value = Double.parseDouble(cmdLine.getOptionValue("u"));
            int n = (columnDimension <= rowDimension) ? columnDimension - 1 : rowDimension;
            for (int i = 0; i < n; i++) {
                matrix.addToEntry(i, i + 1, value);
            }
        }
        // all components
        if (cmdLine.hasOption("a")) {
            double value = Double.parseDouble(cmdLine.getOptionValue("a"));
            for (int i = 0; i < rowDimension; i++) {
                for (int j = 0; j < columnDimension; j++) {
                    matrix.addToEntry(i, j, value);
                }
            }
        }

        // output
        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("matrix" + GadgetAid.getTemporaryString() + ".lst");
        write(matrix, outputPath);
    }

}
