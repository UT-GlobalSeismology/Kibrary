package io.github.kensuke1984.kibrary.fusion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation to design the fusion of {@link UnknownParameter}s to create a coarse grid.
 * <p>
 * The border latitudes, longitudes, and radii given as input are rough guidelines to decide which voxels to combine;
 * they will not be used to compute the positions or volumes of the fused voxels.
 * The positions/volumes will be instead computed by averaging/summing those of the original voxels.
 *
 * @author otsuru
 * @since 2022/1/19
 */
public class CoarseGridDesigner extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * Path of unknown parameter file
     */
    private Path unknownParameterPath;
    /**
     * Partial types of parameters to be fused
     */
    private List<PartialType> partialTypes;

    private boolean fuseVertically;
    private double[] borderRadii;

    private boolean fuseHorizontally;
    private double dLatitudeKm;
    private double dLatitudeDeg;
    private boolean setLatitudeByKm;
    private double latitudeOffset;

    private double dLongitudeKm;
    private double dLongitudeDeg;
    private boolean setLongitudeByKm;
    private double longitudeOffset;

    private FusionDesign fusionDesign;
    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##Path of an unknown parameter list file, must be set");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Partial types of parameters to fuse. If not set, all partial types will be used.");
            pw.println("#partialTypes ");
            pw.println("##########Settings for vertical fusion of voxels");
            pw.println("##(boolean) Whether to fuse voxels vertically (false)");
            pw.println("#fuseVertically true");
            pw.println("##(double) Radii of layer borders, listed using spaces [km] (3480 3530 3580 3630 3680 3730 3780 3830 3880)");
            pw.println("##  Parameters with radii outside this range will not be used.");
            pw.println("#borderRadii ");
            pw.println("##########Settings for horzontal fusion of voxels");
            pw.println("##(boolean) Whether to fuse voxels horizontally (false)");
            pw.println("#fuseHorizontally true");
            pw.println("##(double) Latitude spacing [km]. If this is unset, the following dLatitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees.");
            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg] (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(double) Offset of boundary latitude [deg], must be positive (2.5)");
            pw.println("#latitudeOffset ");
            pw.println("##(double) Longitude spacing [km]. If this is unset, the following dLongitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg] (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Offset of boundary longitude, when dLongitudeDeg is used [deg] [0:dLongitudeDeg) (2.5)");
            pw.println("#longitudeOffset ");
        }
        System.err.println(outPath + " is created.");
    }

    public CoarseGridDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);

        if (property.containsKey("partialTypes"))
            partialTypes = Arrays.stream(property.parseStringArray("partialTypes", null)).map(PartialType::valueOf)
                    .collect(Collectors.toList());

        fuseVertically = property.parseBoolean("fuseVertically", "false");
        borderRadii = Arrays.stream(property.parseDoubleArray("borderRadii", "3480 3530 3580 3630 3680 3730 3780 3830 3880"))
                .sorted().toArray();
        if (borderRadii.length < 2) throw new IllegalArgumentException("There must be at least 2 values for borderRadii");

        fuseHorizontally = property.parseBoolean("fuseHorizontally", "false");
        if (property.containsKey("dLatitudeKm")) {
            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
            if (dLatitudeKm <= 0)
                throw new IllegalArgumentException("dLatitudeKm must be positive");
            setLatitudeByKm = true;
        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0)
                throw new IllegalArgumentException("dLatitudeDeg must be positive");
            setLatitudeByKm = false;
        }
        latitudeOffset = property.parseDouble("latitudeOffset", "2.5");
        if (latitudeOffset < 0) {
            throw new IllegalArgumentException("latitudeOffset must be positive");
        }
        if (property.containsKey("dLongitudeKm")) {
            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
            if (dLongitudeKm <= 0)
                throw new IllegalArgumentException("dLongitudeKm must be positive");
            setLongitudeByKm = true;
        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0)
                throw new IllegalArgumentException("dLongitudeDeg must be positive");
            longitudeOffset = property.parseDouble("longitudeOffset", "2.5");
            if (longitudeOffset < 0 || dLongitudeDeg <= longitudeOffset)
                throw new IllegalArgumentException("longitudeOffset must be in [0:dLongitudeDeg)");
            setLongitudeByKm = false;
        }
    }

    @Override
    public void run() throws IOException {
        if (fuseHorizontally == false && fuseVertically == false) {
            System.err.println("Neither horizontal nor vertical fusion is assigned. Ending.");
            return;
        }

        // read input
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);

        // use all partial types when not specified
        if (partialTypes == null) {
            partialTypes = parameterList.stream().map(param -> param.getPartialType()).distinct().collect(Collectors.toList());
        }

        // fuse voxels
        fusionDesign = new FusionDesign();
        for (PartialType partialType : partialTypes) {
            List<UnknownParameter> correspondingParameters = parameterList.stream()
                    .filter(param -> param.getPartialType().equals(partialType)).collect(Collectors.toList());
            fuseHorizontally(correspondingParameters);
        }

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "coarseGrid", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output fusion design file
        Path outputFusionPath = outPath.resolve("fusion.inf");
        FusionInformationFile.write(fusionDesign, outputFusionPath);

        // output unknown parameter file
        Path outputUnknownsPath = outPath.resolve("unknowns.lst");
        UnknownParameterFile.write(fusionDesign.getFusedParameters(), outputUnknownsPath);
    }

    private void fuseHorizontally(List<UnknownParameter> parameterList) {
        if (fuseHorizontally) {
            Set<FullPosition> positions = parameterList.stream().map(UnknownParameter::getPosition).collect(Collectors.toSet());
            // whether to use longitude range [0:360) instead of [-180,180)
            boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
            // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
            double averageRadius = positions.stream().mapToDouble(FullPosition::getR).distinct().average().getAsDouble();
            double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / averageRadius) : dLatitudeDeg;

            // decide number of latitude intervals
            // When latitudeOffset > 0, intervals for that extra part is needed.
            int nLatitude = (int) Math.ceil((180 + latitudeOffset) / dLatitude);
            // loop for each latitude band (from north to south)
            for (int i = 0; i < nLatitude; i++) {
                double tmp;
                double maxLatitude = (tmp = 90 - (i * dLatitude - latitudeOffset)) > 90 ? 90 : tmp;
                double minLatitude = (tmp = maxLatitude - dLatitude) < -90 ? -90 : tmp;
                double averageLatitude = maxLatitude - dLatitude/2;
                if (averageLatitude < -90) averageLatitude = -90;
                if (averageLatitude > 90) averageLatitude = 90;

                // decide dLongitude for this latitude band
                double dLongitudeForRow;
                if (setLongitudeByKm) {
                    // voxel points are aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm
                    // the min and max borders are set close to the min and max longitudes of sample points
                    double smallCircleRadius = averageRadius * Math.cos(Math.toRadians(averageLatitude));
                    dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
                } else {
                    dLongitudeForRow = dLongitudeDeg;
                }

                // decide number of longitude intervals
                // When indivisible, the remaining range is not used so that the longitudes do not exceed 360.
                // It is not a good idea to include overlapped voxels 2 times (at both ends), anyway.
                int nLongitude = (int) Math.floor(360 / dLongitudeForRow);
                // loop for each longitude range
                for (int j = 0; j < nLongitude; j++) {
                    // decide longitude range, depending on crossDateLine
                    double minLongitude = crossDateLine ? (j * dLongitudeForRow + longitudeOffset)
                            : (-180 + j * dLongitudeForRow + longitudeOffset);
                    double maxLongitude = (tmp = minLongitude + dLongitudeForRow) > 360 ? 360 : tmp;

                    // fuse voxels vertically at this latitude and longitude range
                    List<UnknownParameter> correspondingParameters = parameterList.stream()
                            .filter(param -> param.getPosition().toHorizontalPosition().isInRange(minLatitude, maxLatitude, minLongitude, maxLongitude))
                            .collect(Collectors.toList());
                    if (correspondingParameters.size() == 0) continue;
                    fuseVertically(correspondingParameters);
                }
            }

        } else {
            List<HorizontalPosition> pixelPositions = parameterList.stream()
                    .map(param -> param.getPosition().toHorizontalPosition()).distinct().collect(Collectors.toList());
            // fuse voxels vertically at each horizontal position
            for (HorizontalPosition pixelPosition : pixelPositions) {
                List<UnknownParameter> correspondingParameters = parameterList.stream()
                        .filter(param -> param.getPosition().toHorizontalPosition().equals(pixelPosition))
                        .collect(Collectors.toList());
                if (correspondingParameters.size() == 0) continue;
                fuseVertically(correspondingParameters);
            }
        }
    }

    private void fuseVertically(List<UnknownParameter> parameterList) {
        if (fuseVertically) {
            // fuse voxels within each layer
            for (int i = 1; i < borderRadii.length; i++) {
                double lowerR = borderRadii[i - 1];
                double upperR = borderRadii[i];
                List<UnknownParameter> correspondingParameters = parameterList.stream()
                        .filter(param -> lowerR <= param.getPosition().getR() && param.getPosition().getR() < upperR)
                        .collect(Collectors.toList());
                if (correspondingParameters.size() == 0) continue;
                fusionDesign.addFusion(correspondingParameters);
            }

        } else {
            double[] radii = parameterList.stream().mapToDouble(param -> param.getPosition().getR()).distinct().sorted().toArray();
            // fuse voxels at each radius
            for (double radius : radii) {
                List<UnknownParameter> correspondingParameters = parameterList.stream()
                        .filter(param -> Precision.equals(param.getPosition().getR(), radius, FullPosition.RADIUS_EPSILON))
                        .collect(Collectors.toList());
                if (correspondingParameters.size() == 0) continue;
                fusionDesign.addFusion(correspondingParameters);
            }
        }
    }

}
