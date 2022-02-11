package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * File of voxel information.
 * <p>
 * The file should be as below: <br>
 * r1 r2 r3..... rn (Radii cannot have duplicate values, they will be sorted)<br>
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
     * Radii of voxel center points, sorted, no duplication
     */
    private double[] voxelRadii;
    /**
     * horizontal positions of voxel center points
     */
    private HorizontalPosition[] voxelPositions;


    /**
     * Writes a voxel information file given arrays of radii and positions.
     * @param voxelRadii (double[])
     * @param voxelPositions (HorizontalPosition[])
     * @param outPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(double[] voxelRadii, HorizontalPosition[] voxelPositions, Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# radii");
            for (double radius : voxelRadii) {
                pw.print(radius + " ");
            }
            pw.println("");

            pw.println("# distribution (lat lon)");
            Arrays.stream(voxelPositions).forEach(pw::println);
        }
    }

    /**
     * Reads in a voxel information file.
     * @param filePath (Path)
     * @throws IOException
     */
    public VoxelInformationFile(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath);
        voxelRadii = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).sorted().distinct()
                .toArray();

        List<HorizontalPosition> positionList = new ArrayList<>();
        String line;
        while ((line = reader.next()) != null) {
            String[] part = line.split("\\s+");
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(part[0]), Double.parseDouble(part[1]));
            positionList.add(position);
        }
        voxelPositions = positionList.toArray(new HorizontalPosition[0]);

    }

    /**
     * Get radii information. The radii are sorted, and there is no duplication.
     * @return (double[])
     */
    public double[] getRadii() {
        return voxelRadii;
    }

    /**
     * Get horizontal position information. They are not sorted. There may be duplication.
     * @return
     */
    public HorizontalPosition[] getPositions() {
        return voxelPositions;
    }

}
