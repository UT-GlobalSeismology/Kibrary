package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to compare 2 models.
 * The difference and ratio of the two models are exported.
 * The cosine similarity and L2 model distance is also computed.
 * A scatter plot between perturbations of the 2 models can be created.
 *
 * @since 2022/12/1
 * @author otsuru
 */
public class PerturbationComparison extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    /**
     * Path of numerator scalar file.
     */
    private Path numeratorPath;
    /**
     * Path of denominator scalar file.
     */
    private Path denominatorPath;
    /**
     * Whether to create scatter plot.
     */
    private boolean createScatter;

    /**
     * Path of scalar file to be used as mask.
     */
    private Path maskPath;
    private double maskThreshold;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of numerator scalar file, must be set.");
            pw.println("#numeratorPath scalar.Vs.PERCENT.lst");
            pw.println("##Path of denominator scalar file, must be set.");
            pw.println("#denominatorPath scalar.Vs.PERCENT.lst");
            pw.println("##(boolean) Whether to create scatter plot. (false)");
            pw.println("#createScatter true");
            pw.println("##Path of scalar file for mask, when mask is to be applied.");
            pw.println("#maskPath scalar.Vs.PERCENT_RATIO.lst");
            pw.println("##(double) Threshold for mask. (0.3)");
            pw.println("#maskThreshold ");
        }
        System.err.println(outPath + " is created.");
    }

    public PerturbationComparison(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        numeratorPath = property.parsePath("numeratorPath", null, true, workPath);
        denominatorPath = property.parsePath("denominatorPath", null, true, workPath);
        createScatter = property.parseBoolean("createScatter", "true");

        if (property.containsKey("maskPath")) {
            maskPath = property.parsePath("maskPath", null, true, workPath);
        }
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

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

        // select positions that exist in both maps
        List<FullPosition> positions = numeratorMap.keySet().stream()
                .filter(pos -> denominatorMap.containsKey(pos)).collect(Collectors.toList());
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);

        // extract positions based on mask
        List<FullPosition> discardedPositions = null;
        if (maskPath != null) {
            ScalarListFile maskInputFile = new ScalarListFile(maskPath);
            Map<FullPosition, Double> maskMap = maskInputFile.getValueMap();
            discardedPositions = positions.stream().filter(pos -> maskMap.get(pos) < maskThreshold).collect(Collectors.toList());
            positions.removeAll(discardedPositions);
        }

        // reconstruct the list of values in each map
        // This is done because the number of voxels and/or their order may be different.
        // Only voxels that exist in both maps are used.
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
        Path outPath = DatasetAid.createOutputFolder(workPath, "comparison", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output ratio and difference maps as perturbation list files
        Path differenceMapPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT_DIFFERENCE));
        Path ratioMapPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT_RATIO));
        ScalarListFile.write(constructMapFromVector(positions, differenceVector), crossDateLine, differenceMapPath);
        ScalarListFile.write(constructMapFromVector(positions, ratioVector), crossDateLine, ratioMapPath);

        // output similarity and distance in a txt file
        Path comparisonPath = outPath.resolve("comparison.txt");
        outputComparison(comparisonPath, numeratorPath, denominatorPath, cosineSimilarity, l2Distance, l2Average, l2Denominator);

        // output values in a txt file
        if (createScatter) {
            Path valuesPath = outPath.resolve("values.txt");
            outputValues(valuesPath, positions, crossDateLine, numeratorValues, denominatorValues);

            // output values at discarded positions
            if (maskPath != null) {
                double[] discardedNumeratorValues = discardedPositions.stream().mapToDouble(pos -> numeratorMap.get(pos)).toArray();
                double[] discardedDenominatorValues = discardedPositions.stream().mapToDouble(pos -> denominatorMap.get(pos)).toArray();

                Path discardedValuesPath = outPath.resolve("discardedValues.txt");
                outputValues(discardedValuesPath, discardedPositions, crossDateLine, discardedNumeratorValues, discardedDenominatorValues);
            }

            // create scatter plot
            Path scatterPath = outPath.resolve("valueScatterPlot.plt");
            createScatterPlot(scatterPath, (maskPath != null));
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

    private static void outputValues(Path outputPath, List<FullPosition> positions, boolean crossDateLine, double[] numeratorValues, double[] denominatorValues) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < positions.size(); i++) {
                pw.println(positions.get(i).toString(crossDateLine) + " " + denominatorValues[i] + " " + numeratorValues[i]);
            }
        }
    }

    private static void createScatterPlot(Path scatterPath, boolean hasDiscarded) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scatterPath))) {
            pw.println("set term pngcairo enhanced size 600,600 font 'Helvetica,20'");
            pw.println("set output 'valueScatterPlot.png'");
            pw.println("set xlabel \"{/Symbol d}Vs/Vs (%)\"");
            pw.println("set ylabel \"{/Symbol d}Vs/Vs (%)\"");
            pw.println("#set xrange [-5:5]");
            pw.println("#set yrange [-5:5]");
            pw.println("set zeroaxis lt 1 lc \"black\"");
            pw.println("p x w l lc rgb \"black\" notitle,\\");
            if (hasDiscarded) {
                pw.println("  \"discardedValues.txt\" u 4:5 w p pt 7 lc \"light-gray\" notitle,\\");
            }
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
