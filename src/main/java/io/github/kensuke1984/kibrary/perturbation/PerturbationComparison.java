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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
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

        options.addOption(Option.builder("n").longOpt("numerator").hasArg().argName("perturbationFile").required()
                .desc("Path of perturbation file to compare").build());
        options.addOption(Option.builder("d").longOpt("denominator").hasArg().argName("perturbationFile").required()
                .desc("Path of perturbation file to compare to").build());
        options.addOption(Option.builder("t").longOpt("tag").hasArg().argName("tag")
                .desc("A tag to include in output folder name.").build());

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
        // These will be obtained as unmodifiable LinkedHashMap
        Map<FullPosition, Double> numeratorMap = PerturbationListFile.read(numeratorPath);
        Map<FullPosition, Double> denominatorMap = PerturbationListFile.read(denominatorPath);

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

        String folderTag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "comparison", folderTag, GadgetAid.getTemporaryString());

        // output ratio and difference maps as perturbation list files
        String fileNameRoot = FileAid.extractNameRoot(numeratorPath);
        Path ratioMapPath = outPath.resolve(fileNameRoot + "Ratio.lst");
        Path differenceMapPath = outPath.resolve(fileNameRoot + "Difference.lst");
        PerturbationListFile.write(constructMapFromVector(positions, ratioVector), ratioMapPath);
        PerturbationListFile.write(constructMapFromVector(positions, differenceVector), differenceMapPath);

        // output similarity and distance in a txt file
        Path comparisonPath = outPath.resolve("comparison.txt");
        outputComparison(comparisonPath, numeratorPath, denominatorPath, cosineSimilarity, l2Distance, l2Average, l2Denominator);
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
