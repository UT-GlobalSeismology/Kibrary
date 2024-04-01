package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationListFile {
    /**
     * The number of decimal places to round off the percent value.
     */
    private static final int DECIMALS = 10;

    public static void writeAbsoluteForType(VariableType type, PerturbationModel model, Path outputPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + Precision.round(voxel.getAbsolute(type), DECIMALS));
            }
        }
    }

    public static void writePercentForType(VariableType type, PerturbationModel model, Path outputPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + Precision.round(voxel.getPercent(type), DECIMALS));
            }
        }
    }

    /**
     * Output file from map of position to value.
     * @param perturbationMap (Map of FullPosition to Double) Values at each position. Should be LinkedHashMap for the lines to be sorted.
     * @param outputPath (Path) Output file path
     * @param options (OpenOption...)
     * @throws IOException
     */
    public static void write(Map<FullPosition, Double> perturbationMap, Path outputPath, OpenOption... options) throws IOException {
        write(perturbationMap, false, outputPath, options);
    }

    /**
     * Output file from map of position to value.
     * @param perturbationMap (Map of FullPosition to Double) Values at each position. Should be LinkedHashMap for the lines to be sorted.
     * @param crossDateLine (boolean) Whether to use longitude range [0:360) instead of [-180:180).
     * @param outputPath (Path) Output file path
     * @param options (OpenOption...)
     * @throws IOException
     */
    public static void write(Map<FullPosition, Double> perturbationMap, boolean crossDateLine, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            // Do not sort here, because the input may be already sorted. (LinkedHashMap can be sorted.)
            perturbationMap.forEach((key, value) -> pw.println(key.toString(crossDateLine) + " " + Precision.round(value, DECIMALS)));
        }
    }

    /**
     * Read file as map of position to value.
     * @param inputPath (Path) Input file path
     * @param options (OpenOption...)
     * @return (Unmodifiable LinkedHashMap of FullPosition to Double) Map of perturbations
     * @throws IOException
     */
    public static Map<FullPosition, Double> read(Path inputPath, OpenOption... options) throws IOException {
        // This is created as LinkedHashMap to preserve the order of voxels
        Map<FullPosition, Double> perturbationMap = new LinkedHashMap<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while(reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            FullPosition position = new FullPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            double perturbation = Double.parseDouble(parts[3]);
            perturbationMap.put(position, perturbation);
        }

        return Collections.unmodifiableMap(perturbationMap);
    }
}
