package io.github.kensuke1984.kibrary.math;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
        System.err.println("Writing in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < vector.getDimension(); i++) {
                pw.println(vector.getEntry(i));
            }
        }
    }

    public static RealVector read(Path inputPath) throws IOException {
        System.err.println("Reading " + inputPath);

        // read input file
        InformationFileReader reader = new InformationFileReader(inputPath, true);
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

    /**
     * Create template vector file.
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

        // size
        options.addOption(Option.builder("s").longOpt("size").hasArg().argName("size").required()
                .desc("Size of vector.").build());

        // components
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

        // construct zero-vector
        int size = Integer.parseInt(cmdLine.getOptionValue("s"));
        RealVector vector = new ArrayRealVector(size);

        // all components
        if (cmdLine.hasOption("a")) {
            double value = Double.parseDouble(cmdLine.getOptionValue("a"));
            for (int i = 0; i < size; i++) {
                vector.addToEntry(i, value);
            }
        }

        // output
        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("vector" + GadgetAid.getTemporaryString() + ".lst");
        write(vector, outputPath);
    }

}
