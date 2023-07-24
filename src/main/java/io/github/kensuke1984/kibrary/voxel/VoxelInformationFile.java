package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * r1 r2 r3..... rn (Radii, cannot have duplicate values, must be sorted)<br>
 * lat1 lon1 dLat1 dLon1<br>
 * lat2 lon2 dLat2 dLon2<br>
 * .<br>
 * .<br>
 * .<br>
 * latm lonm dLatm dLonm
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2022/2/11
 */
public class VoxelInformationFile {

    /**
     * thickness of each layer
     */
    private final double[] layerThicknesses;
    /**
     * Radii of voxel center points, sorted, no duplication
     */
    private final double[] layerRadii;
    /**
     * Horizontal distribution of voxels
     */
    private final List<HorizontalPixel> horizontalPixels = new ArrayList<>();


    /**
     * Writes a voxel information file given arrays of radii and positions.
     * @param layerThicknesses (double[]) Must be in the same order as layerRadii.
     * @param layerRadii (double[])  The radii. They should be sorted, and there should be no duplication.
     * @param horizontalPixels (List of {@link HorizontalPixel}) Pixels.
     * @param outputPath (Path) The output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException if an I/O error occurs
     */
    public static void write(double[] layerThicknesses, double[] layerRadii, List<HorizontalPixel> horizontalPixels,
            Path outputPath, OpenOption... options) throws IOException {
        if (layerThicknesses.length != layerRadii.length)
            throw new IllegalArgumentException("The number of thicknesses and radii does not match.");

        System.err.println("Outputting "
                + MathAid.switchSingularPlural(layerRadii.length * horizontalPixels.size(), "voxel", "voxels")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# thicknesses of each layer [km]");
            for (double thickness : layerThicknesses) {
                pw.print(thickness + " ");
            }
            pw.println("");

            pw.println("# radii of center points of each layer [km]");
            for (double radius : layerRadii) {
                pw.print(radius + " ");
            }
            pw.println("");

            pw.println("# horizontal rectangle on sphere [deg] (latitude longitude dLatitude dLongitude)");
            for (HorizontalPixel pixel : horizontalPixels) {
                pw.println(pixel.getPosition() + " " + pixel.getDLatitude() + " " + pixel.getDLongitude());
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
        layerRadii = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        if (layerThicknesses.length != layerRadii.length)
            throw new IllegalArgumentException("The number of thicknesses and radii does not match.");

        String line;
        while ((line = reader.next()) != null) {
            String[] parts = line.split("\\s+");
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            HorizontalPixel pixel = new HorizontalPixel(position, Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            horizontalPixels.add(pixel);
        }

        DatasetAid.checkNum(layerRadii.length * horizontalPixels.size(), "voxel", "voxels");
    }

    /**
     * Get information of layer thicknesses.
     * @return (double[])
     */
    public double[] getThicknesses() {
        return layerThicknesses.clone();
    }

    /**
     * Get radii information. The radii should be sorted, and there should be no duplication.
     * @return (double[])
     */
    public double[] getRadii() {
        return layerRadii.clone();
    }

    /**
     * Get horizontal position information. They may not be sorted. There may be duplication.
     * @return
     */
    public List<HorizontalPosition> getHorizontalPositions() {
        return horizontalPixels.stream().map(HorizontalPixel::getPosition).collect(Collectors.toList());
    }

    /**
     * Get horizontal pixels. They may not be sorted. There may be duplication.
     * @return
     */
    public List<HorizontalPixel> getHorizontalPixels() {
        return Collections.unmodifiableList(horizontalPixels);
    }

    /**
     * Get set of full position information.
     * @return
     */
    public Set<FullPosition> fullPositionSet() {
        Set<FullPosition> voxelSet = new HashSet<>();
        for (HorizontalPosition position : getHorizontalPositions()) {
            for (double radius : layerRadii) {
                voxelSet.add(position.toFullPosition(radius));
            }
        }
        return voxelSet;
    }

}
