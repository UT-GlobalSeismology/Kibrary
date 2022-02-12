package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Create unknown parameter file.
 * @author ???
 * @since a long time ago
 * @version 2022/2/12 moved & renamed from inversion.addons.MakeUnknownParameterFile
 */
public class UnknownParameterSetter {

    /**
     * Create unknown parameter file.
     * @param args [option]
     *      [voxelPath  partialTypes...]
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage:");
            System.err.println(" [voxelPath  partialTypes...] : creates unknown parameter file");
            return;
        }

        Path voxelPath = Paths.get(args[0]);
        // partial types
        int nType = args.length - 1;
        PartialType[] types = new PartialType[nType];
        for (int i = 0; i < nType; i++) types[i] = PartialType.valueOf(args[i+1]);

        output(voxelPath, types);

    }

    private static void output(Path voxelPath, PartialType[] types) throws IOException {
        // read voxel information
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        double dLatitude = file.getSpacingLatitude();
        double dLongitude = file.getSpacingLongitude();
        HorizontalPosition[] positions = file.getHorizontalPositions();

        if (layerThicknesses.length != radii.length)
            throw new IllegalArgumentException("The number of layers and radii does not match.");

        List<UnknownParameter> parameterList = new ArrayList<>();
        for (HorizontalPosition position : positions) {
            for (int i = 0; i < radii.length; i++) {
                FullPosition pointPosition = position.toFullPosition(radii[i]);
                double volume = getVolume(pointPosition, layerThicknesses[i], dLatitude, dLongitude);
                for (PartialType type : types) {
                    Physical3DParameter parameter = new Physical3DParameter(type, pointPosition, volume);
                    parameterList.add(parameter);
                }
            }
        }

        Path outputPath = Paths.get("unknowns" + GadgetAid.getTemporaryString() + ".inf");
        System.err.println("Outputting in "+ outputPath);
        UnknownParameterFile.write(outputPath, parameterList);
    }

    private static double getVolume(FullPosition point, double dr, double dLatitude, double dLongitude) {
        double r = point.getR();
        if (r <= 0) {
            System.err.println("location has no R information or invalid R: " + r);
        }
        double latitude = point.getLatitude();// 地理緯度
        double longitude = point.getLongitude();
        double startA = Earth.getExtendedShaft(point.toFullPosition(r - 0.5 * dr));
        double endA = Earth.getExtendedShaft(point.toFullPosition(r + 0.5 * dr));
        double v = Earth.getVolume(startA, endA, latitude - 0.5 * dLatitude, latitude + 0.5 * dLatitude,
                longitude - 0.5 * dLongitude, longitude + 0.5 * dLongitude);

        return v;
    }
}
