package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * File of voxel information.
 * <p>
 * The file should be as below: <br>
 * h1 h2 h3..... hn (Layer thicknesses, from the ones closer to the center of planet)<br>
 * r1 r2 r3..... rn (Radii cannot have duplicate values, must be sorted)<br>
 * dLat dLon<br>
 * lat1 lon1<br>
 * lat2 lon2<br>
 * .<br>
 * .<br>
 * .<br>
 * latm lonm
 *
 * @author otsuru
 * @since 2022/2/11
 */
public class VoxelInformationFile {

    /**
     * thickness of each layer
     */
    private double[] layerThicknesses;
    /**
     * Radii of voxel center points, sorted, no duplication
     */
    private double[] voxelRadii;
    private double dLatitude;
    private double dLongitude;
    /**
     * horizontal positions of voxel center points
     */
    private HorizontalPosition[] voxelPositions;


    /**
     * Writes a voxel information file given arrays of radii and positions.
     * @param layerThicknesses (double[]) Must be in the same order as voxelRadii.
     * @param voxelRadii (double[])  The radii should be sorted, and there should be no duplication.
     * @param spacingLatitude (double) [deg]
     * @param spacingLongitude (double) [deg]
     * @param voxelPositions (HorizontalPosition[])
     * @param outPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(double[] layerThicknesses, double[] voxelRadii,
            double spacingLatitude, double spacingLongitude, HorizontalPosition[] voxelPositions,
            Path outPath, OpenOption... options) throws IOException {
        if (layerThicknesses.length != voxelRadii.length)
            throw new IllegalArgumentException("The number of layers and radii does not match.");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# thicknesses of each layer [km]");
            for (double thickness : layerThicknesses) {
                pw.print(thickness + " ");
            }
            pw.println("");

            pw.println("# radii of center points of voxels [km]");
            for (double radius : voxelRadii) {
                pw.print(radius + " ");
            }
            pw.println("");

            pw.println("# spacing [deg] (lat lon)");
            pw.println(spacingLatitude + " " + spacingLongitude);

            pw.println("# horizontal positions [deg] (lat lon)");
            Arrays.stream(voxelPositions).forEach(pw::println);
        }
    }

    /**
     * Reads in a voxel information file.
     * @param filePath (Path)
     * @throws IOException
     */
    public VoxelInformationFile(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath, true);

        layerThicknesses = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();

        voxelRadii = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();

        if (layerThicknesses.length != voxelRadii.length)
            throw new IllegalArgumentException("The number of layers and radii does not match.");

        String[] part = reader.next().split("\\s+");
        dLatitude = Double.parseDouble(part[0]);
        dLongitude = Double.parseDouble(part[1]);

        List<HorizontalPosition> positionList = new ArrayList<>();
        String line;
        while ((line = reader.next()) != null) {
            part = line.split("\\s+");
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(part[0]), Double.parseDouble(part[1]));
            positionList.add(position);
        }
        voxelPositions = positionList.toArray(new HorizontalPosition[0]);

        System.err.println("Number of voxels read in: " + (voxelRadii.length * voxelPositions.length));
    }

    /**
     * Get information of layer thicknesses.
     * @return (double[])
     */
    public double[] getThicknesses() {
        return layerThicknesses;
    }

    /**
     * Get radii information. The radii should be sorted, and there should be no duplication.
     * @return (double[])
     */
    public double[] getRadii() {
        return voxelRadii;
    }

    public double getSpacingLatitude() {
        return dLatitude;
    }

    public double getSpacingLongitude() {
        return dLongitude;
    }

    /**
     * Get horizontal position information. They may not be sorted. There may be duplication.
     * @return
     */
    public HorizontalPosition[] getHorizontalPositions() {
        return voxelPositions;
    }

    /**
     * Get set of full position information.
     * @return
     */
    public Set<FullPosition> fullPositionSet() {
        Set<FullPosition> voxelSet = new HashSet<>();
        for (HorizontalPosition position : voxelPositions) {
            for (double radius : voxelRadii) {
                voxelSet.add(position.toFullPosition(radius));
            }
        }
        return voxelSet;
    }

}
