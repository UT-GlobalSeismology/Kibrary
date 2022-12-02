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
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationListFile {

    public static void writeAbsoluteForType(VariableType type, PerturbationModel model, Path outputPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + voxel.getAbsolute(type));
            }
        }
    }

    public static void writePercentForType(VariableType type, PerturbationModel model, Path outputPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + voxel.getPercent(type));
            }
        }
    }

    public static void write(Map<FullPosition, Double> perturbationMap, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
//            perturbationMap.entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
//                    .forEach(entry -> pw.println(entry.getKey() + " " + entry.getValue()));
            perturbationMap.forEach((key, value) -> pw.println(key + " " + value));
        }
    }

    /**
     * @param inputPath
     * @param options
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
