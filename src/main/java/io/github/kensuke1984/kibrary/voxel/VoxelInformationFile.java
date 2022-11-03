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
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * File of voxel information.
 * <p>
 * The file should be as below: <br>
 * h1 h2 h3..... hn (Layer thicknesses, from the ones closer to the center of planet)<br>
 * r1 r2 r3..... rn (Radii cannot have duplicate values, must be sorted)<br>
 * lat1 lon1 dLat1 dLon1<br>
 * lat2 lon2 dLat2 dLon2<br>
 * .<br>
 * .<br>
 * .<br>
 * latm lonm dLatm dLonm
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
    /**
     * Horizontal distribution of voxels
     */
    private List<HorizontalPiece> horizontalPieces = new ArrayList<>();


    /**
     * Writes a voxel information file given arrays of radii and positions.
     * @param layerThicknesses (double[]) Must be in the same order as voxelRadii.
     * @param voxelRadii (double[])  The radii should be sorted, and there should be no duplication.
     * @param spacingLatitude (double) [deg]
     * @param spacingLongitude (double) [deg]
     * @param voxelPositions (HorizontalPosition[])
     * @param outputPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(double[] layerThicknesses, double[] voxelRadii, List<HorizontalPiece> horizontalPieces,
            Path outputPath, OpenOption... options) throws IOException {
        if (layerThicknesses.length != voxelRadii.length)
            throw new IllegalArgumentException("The number of layers and radii does not match.");

        System.err.println("Outputting "
                + MathAid.switchSingularPlural(voxelRadii.length * horizontalPieces.size(), "voxel", "voxels")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
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

            pw.println("# horizontal rectangle on sphere [deg] (latitude longitude dLatitude dLongitude)");
            for (HorizontalPiece piece : horizontalPieces) {
                pw.println(piece.getPosition() + " " + piece.getDLatitude() + " " + piece.getDLongitude());
            }
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

        String line;
        while ((line = reader.next()) != null) {
            String[] parts = line.split("\\s+");
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            HorizontalPiece piece = new HorizontalPiece(position, Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            horizontalPieces.add(piece);
        }

        DatasetAid.checkNum(voxelRadii.length * horizontalPieces.size(), "voxel", "voxels");
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

    /**
     * Get horizontal position information. They may not be sorted. There may be duplication.
     * @return
     */
    public List<HorizontalPosition> getHorizontalPositions() {
        return horizontalPieces.stream().map(HorizontalPiece::getPosition).collect(Collectors.toList());
    }

    /**
     * Get horizontal position information. They may not be sorted. There may be duplication.
     * @return
     */
    public List<HorizontalPiece> getHorizontalPieces() {
        return horizontalPieces;
    }

    /**
     * Get set of full position information.
     * @return
     */
    public Set<FullPosition> fullPositionSet() {
        Set<FullPosition> voxelSet = new HashSet<>();
        for (HorizontalPosition position : getHorizontalPositions()) {
            for (double radius : voxelRadii) {
                voxelSet.add(position.toFullPosition(radius));
            }
        }
        return voxelSet;
    }

}
