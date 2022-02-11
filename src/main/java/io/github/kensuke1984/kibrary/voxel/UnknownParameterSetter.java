package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * @author ???
 * @since a long time ago
 * @version 2022/2/12 moved & renamed from inversion.addons.MakeUnknownParameterFile
 */
public class UnknownParameterSetter {

    public static void main(String[] args) {
        // each line: partial latitude, partial longitude, partial depth
        // number of line = number of voxels
        Path perturbationPointPath = Paths.get(args[0]);
        // each line: perturbation depth, thickness (in the vertical direction)
        // number of line = number of depth layers
        Path perturbationLayerPath = Paths.get(args[1]);
        // voxel size in degree
        double voxelSize = Double.parseDouble(args[2]);
        // partial types
        int nType = args.length - 3;
        PartialType[] types = new PartialType[nType];
        for (int i = 0; i < nType; i++) types[i] = PartialType.valueOf(args[i+3]);

        try {
            // read perturbation points lat lon r
            List<FullPosition> perturbations = Files.readAllLines(perturbationPointPath)
                    .stream().map(s -> new FullPosition(Double.parseDouble(s.trim().split(" ")[0])
                            ,Double.parseDouble(s.trim().split(" ")[1])
                            ,Double.parseDouble(s.trim().split(" ")[2])))
                    .collect(Collectors.toList());

            // read layer thickness
            Map<Double, Double> layerMap = new HashMap<>();
            Files.readAllLines(perturbationLayerPath).stream().forEach(s -> {
                Double r = Double.parseDouble(s.trim().split(" ")[0]);
                Double d = Double.parseDouble(s.trim().split(" ")[1]);
                layerMap.put(r, d);
            });

            // create unknowns file
            Path unknownPath = Paths.get("unknowns" + GadgetAid.getTemporaryString() + ".inf");
            Files.deleteIfExists(unknownPath);
            Files.createFile(unknownPath);

//			int nDigit = (int) Math.log10(perturbations.size()) + 1;
            for (PartialType type : types) {
                for (int i = 0; i < perturbations.size(); i++) {
                    FullPosition perturbation = perturbations.get(i);
                    double dR = 0;
                    try {
                         dR = layerMap.get(perturbation.getR());
                    } catch (NullPointerException e) {
                        System.err.format("Ignoring radius %.4f%n", perturbation.getR());
                        continue;
                    }
                    double volume = getVolume(perturbation, dR, voxelSize, voxelSize);
                    Files.write(unknownPath, String.format("%s %.8f %.8f %.8f %.8f\n"
                            , type.toString()
                            , perturbation.getLatitude()
                            , perturbation.getLongitude()
                            , perturbation.getR()
                            , volume).getBytes(), StandardOpenOption.APPEND);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static double getVolume(FullPosition point, double dr, double dLatitude, double dLongitude) {
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
