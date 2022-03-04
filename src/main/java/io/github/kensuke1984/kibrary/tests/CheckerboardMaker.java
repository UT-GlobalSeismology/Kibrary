package io.github.kensuke1984.kibrary.tests;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Creates a checkerboard model file.
 * @author otsuru
 * @since 2022/3/4
 */
public class CheckerboardMaker {


    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: voxelInformationFile(Path) amplitude(double) flipSign(boolean)");
        }

        Path voxelPath = Paths.get(args[0]);
        double amplitude = Double.parseDouble(args[1]);
        boolean flipSign = Boolean.parseBoolean(args[2]);

//        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);

        // read voxel file
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        //double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        double dLatitude = file.getSpacingLatitude();
        double dLongitude = file.getSpacingLongitude();
        HorizontalPosition[] positions = file.getHorizontalPositions();

        List<Double> perturbations = new ArrayList<>();
        HorizontalPosition referencePosition = positions[0];
        for (HorizontalPosition position : positions) {
            for (int i = 0; i < radii.length; i++) {
                int numDiff = (int) Math.round((position.getLatitude() - referencePosition.getLatitude()) / dLatitude
                        + (position.getLongitude() - referencePosition.getLongitude()) / dLongitude) + i;

                if ((numDiff % 2 == 1) ^ flipSign) { // ^ is XOR
                    perturbations.add(-amplitude);
                } else {
                    perturbations.add(amplitude);
                }
            }
        }

        Path outputPath = Paths.get("model" + GadgetAid.getTemporaryString() + ".inf");
        System.err.println("Outputting in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            perturbations.forEach(pw::println);
        }

    }
}
