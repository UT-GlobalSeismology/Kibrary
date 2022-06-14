package io.github.kensuke1984.kibrary.model;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.ParameterType;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationModelFile {

    public static void writeAbsoluteForType(ParameterType type, PerturbationModel model, Path outPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.println(voxel.getPosition() + " " + voxel.getAbsolute(type));
            }
        }
    }

    public static void writePercentForType(List<ParameterType> types, PerturbationModel model, Path outPath, OpenOption... options)
            throws IOException {

        List<PerturbationVoxel> voxels = model.getVoxels();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            for (PerturbationVoxel voxel : voxels) {
                pw.print(voxel.getPosition());
                for (ParameterType type : types) {
                    pw.print(" " + voxel.getPercent(type));
                }
                pw.println();
            }
        }
    }

}
