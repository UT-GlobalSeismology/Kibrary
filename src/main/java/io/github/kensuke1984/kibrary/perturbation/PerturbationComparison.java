package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Class to compare 2 models.
 * The difference and ratio of the two models are exported.
 * The cosine similarity and L2 model distance is also computed.
 *
 * @author otsuru
 * @since 2022/12/1
 */
public class PerturbationComparison {

    /**
     * Compare two perturbation models.
     * @param args Options.
     * @throws IOException
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

        options.addOption(Option.builder("n").longOpt("numerator").hasArg().argName("scalarFile").required()
                .desc("Path of scalar file to compare.").build());
        options.addOption(Option.builder("d").longOpt("denominator").hasArg().argName("scalarFile").required()
                .desc("Path of scalar file to compare to.").build());

        options.addOption(Option.builder("S").longOpt("scatter")
                .desc("Create scatter plot.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("folderTag")
                .desc("A tag to include in output folder name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output folder name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path numeratorPath = Paths.get(cmdLine.getOptionValue("n"));
        Path denominatorPath = Paths.get(cmdLine.getOptionValue("d"));

        // read input files
        ScalarListFile numeratorFile = new ScalarListFile(numeratorPath);
        ScalarListFile denominatorFile = new ScalarListFile(denominatorPath);
        VariableType variable = numeratorFile.getVariable();
        if (denominatorFile.getVariable() != variable)
            throw new IllegalArgumentException("Variable types do not match: " + variable + " " + denominatorFile.getVariable());
        if (numeratorFile.getScalarType() != ScalarType.PERCENT || denominatorFile.getScalarType() != ScalarType.PERCENT)
            throw new IllegalArgumentException("Scalar type must be PERCENT: " + numeratorFile.getScalarType() + " " + denominatorFile.getScalarType());
        // These will be obtained as unmodifiable LinkedHashMap:
        Map<FullPosition, Double> numeratorMap = numeratorFile.getValueMap();
        Map<FullPosition, Double> denominatorMap = denominatorFile.getValueMap();

        // reconstruct the list of values in each map
        // This is done because the number of voxels and/or their order may be different.
        // Only voxels that exist in both maps are used.
        List<FullPosition> positions = numeratorMap.keySet().stream()
                .filter(pos -> denominatorMap.containsKey(pos)).collect(Collectors.toList());
        double[] numeratorValues = positions.stream().mapToDouble(pos -> numeratorMap.get(pos)).toArray();
        double[] denominatorValues = positions.stream().mapToDouble(pos -> denominatorMap.get(pos)).toArray();

        // transform into vector
        RealVector numeratorVector = new ArrayRealVector(numeratorValues);
        RealVector denominatorVector = new ArrayRealVector(denominatorValues);

        // computations
        RealVector ratioVector = numeratorVector.ebeDivide(denominatorVector);
        RealVector differenceVector = numeratorVector.subtract(denominatorVector);
        double cosineSimilarity = numeratorVector.dotProduct(denominatorVector) / numeratorVector.getNorm() / denominatorVector.getNorm();
        double l2Distance = differenceVector.getNorm();
        double l2Average = numeratorVector.add(denominatorVector).mapDivide(2).getNorm();
        double l2Denominator = denominatorVector.getNorm();

        // create output folder
        String folderTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFolderDate = !cmdLine.hasOption("O");
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "comparison", folderTag, appendFolderDate, null);

        // output ratio and difference maps as perturbation list files
        Path differenceMapPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT_DIFFERENCE));
        Path ratioMapPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT_RATIO));
        ScalarListFile.write(constructMapFromVector(positions, differenceVector), differenceMapPath);
        ScalarListFile.write(constructMapFromVector(positions, ratioVector), ratioMapPath);

        // output similarity and distance in a txt file
        Path comparisonPath = outPath.resolve("comparison.txt");
        outputComparison(comparisonPath, numeratorPath, denominatorPath, cosineSimilarity, l2Distance, l2Average, l2Denominator);

        // output values in a txt file
        if (cmdLine.hasOption("S")) {
            Path valuesPath = outPath.resolve("values.txt");
            Path scatterPath = outPath.resolve("valueScatterPlot.plt");
            outputValues(valuesPath, positions, numeratorValues, denominatorValues);
            createScatterPlot(scatterPath);
        }
    }

    private static Map<FullPosition, Double> constructMapFromVector(List<FullPosition> positions, RealVector vector) {
        if (positions.size() != vector.getDimension()) throw new IllegalArgumentException("Sizes of keys and values do not match");

        // This is created as LinkedHashMap to preserve the order of voxels
        Map<FullPosition, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            map.put(positions.get(i), vector.getEntry(i));
        }
        return map;
    }

    private static void outputValues(Path outputPath, List<FullPosition> positions, double[] numeratorValues, double[] denominatorValues) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < positions.size(); i++) {
                pw.println(positions.get(i) + " " + denominatorValues[i] + " " + numeratorValues[i]);
            }
        }
    }

    private static void createScatterPlot(Path scatterPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scatterPath))) {
            pw.println("set term pngcairo enhanced size 600,600 font 'Helvetica,20'");
            pw.println("set output 'valueScatterPlot.png'");
            pw.println("set xlabel \"{/Symbol d}Vs/Vs (%)\"");
            pw.println("set ylabel \"{/Symbol d}Vs/Vs (%)\"");
            pw.println("#set xrange [-5:5]");
            pw.println("#set yrange [-5:5]");
            pw.println("set zeroaxis lt 1 lc \"black\"");
            pw.println("p x w l lc rgb \"black\" notitle,\\");
            pw.println("  \"values.txt\" u 4:5 w p pt 7 lc \"dark-violet\" notitle");
        }
        GnuplotFile plot = new GnuplotFile(scatterPath);
        plot.execute();
    }

    private static void outputComparison(Path outputPath, Path numeratorPath, Path denominatorPath,
            double cosineSimilarity, double l2Distance, double l2Average, double l2Denominator) throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("numeratorPath: " + numeratorPath);
            pw.println("denominatorPath: " + denominatorPath);
            pw.println("");
            pw.println("cosine similarity (something like model correlation):");
            pw.println("  " + cosineSimilarity);
            pw.println("L2 model distance normalized to L2 amplitude of average model of the two:");
            pw.println("  " + l2Distance + " / " + l2Average + " = " + (l2Distance / l2Average));
            pw.println("L2 model distance normalized to L2 amplitude of denominator model:");
            pw.println("  " + l2Distance + " / " + l2Denominator + " = " + (l2Distance / l2Denominator));
        }
    }

}
