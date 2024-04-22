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

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * File with perturbation values for each voxel position.
 *
 * @author otsuru
 * @since 2022/4/9
 */
public class ScalarListFile {

    private final VariableType variable;
    private final ScalarType scalarType;
    // This is created as LinkedHashMap to preserve the order of voxels
    private final Map<FullPosition, Double> valueMap = new LinkedHashMap<>();

    public static String generateFileName(VariableType variable, ScalarType scalarType) {
        return generateFileName(variable, scalarType, null);
    }

    public static String generateFileName(VariableType variable, ScalarType scalarType, String tag) {
        return "scalar" + ((tag != null) ? ("_" + tag) : "") + "." + variable.toString() + "." + scalarType.toString() + ".lst";
    }

    /**
     * Write file of a certain variable of a model in the specified scalar type.
     * @param model ({@link PerturbationModel}) Model to write.
     * @param variable ({@link VariableType}) Variable to write for.
     * @param scalarType ({@link ScalarType}) Scalar type to write values in.
     * @param outputPath (Path) Output file path.
     * @param options (OpenOption...)
     * @throws IOException
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public static void write(PerturbationModel model, VariableType variable, ScalarType scalarType, Path outputPath, OpenOption... options)
            throws IOException {
        // get list of voxels
        List<PerturbationVoxel> voxels = model.getVoxels();
        // write for each voxel
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + MathAid.roundForPrecision(voxel.getValue(variable, scalarType)));
            }
        }
    }

    /**
     * Output file from map of position to value.
     * @param valueMap (Map of FullPosition to Double) Values at each position. Should be LinkedHashMap for the lines to be sorted.
     * @param outputPath (Path) Output file path.
     * @param options (OpenOption...)
     * @throws IOException
     */
    public static void write(Map<FullPosition, Double> valueMap, Path outputPath, OpenOption... options) throws IOException {
        write(valueMap, false, outputPath, options);
    }

    /**
     * Output file from map of position to value.
     * @param valueMap (Map of FullPosition to Double) Values at each position. Should be LinkedHashMap for the lines to be sorted.
     * @param crossDateLine (boolean) Whether to use longitude range [0:360) instead of [-180:180).
     * @param outputPath (Path) Output file path.
     * @param options (OpenOption...)
     * @throws IOException
     */
    public static void write(Map<FullPosition, Double> valueMap, boolean crossDateLine, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            // Do not sort here, because the input may be already sorted. (LinkedHashMap can be sorted.)
            valueMap.forEach((key, value) -> pw.println(key.toString(crossDateLine) + " " + MathAid.roundForPrecision(value)));
        }
    }

    /**
     * Read file as map of position to value.
     * @param inputPath (Path) Input file path.
     * @param options (OpenOption...)
     * @return (Unmodifiable LinkedHashMap of {@link FullPosition}, Double) Correspondence of position and values.
     * @throws IOException
     */
    public static Map<FullPosition, Double> read(Path inputPath, OpenOption... options) throws IOException {
        return new ScalarListFile(inputPath, options).getValueMap();
    }

    /**
     * Read in a scalar list file.
     * @param inputPath (Path) Input file path.
     * @param options (OpenOption...)
     * @throws IOException
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public ScalarListFile(Path inputPath, OpenOption... options) throws IOException {
        String fileName = inputPath.getFileName().toString();
        String[] fileNameParts = fileName.split("\\.");
        if (fileNameParts.length != 4) throw new IllegalArgumentException("Invalid file name: " + fileName);
        variable = VariableType.valueOf(fileNameParts[1]);
        scalarType = ScalarType.valueOf(fileNameParts[2]);

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while(reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            FullPosition position = new FullPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            double perturbation = Double.parseDouble(parts[3]);
            valueMap.put(position, perturbation);
        }
    }

    public VariableType getVariable() {
        return variable;
    }

    public ScalarType getScalarType() {
        return scalarType;
    }

    /**
     * Get all values.
     * @return (Unmodifiable LinkedHashMap of {@link FullPosition}, Double) Correspondence of position and values.
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public Map<FullPosition, Double> getValueMap() {
        return Collections.unmodifiableMap(valueMap);
    }

}
