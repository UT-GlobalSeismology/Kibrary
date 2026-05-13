package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.math.geometry.CoordinateConverter;
import io.github.kensuke1984.kibrary.math.geometry.IntegerXY;
import io.github.kensuke1984.kibrary.math.geometry.XY;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Class to resample a {@link ScalarListFile} at specified positions.
 * <p>
 * This class assumes that the input grid can be recasted onto a curvilinear grid.
 * The resampling points are arbitrary.
 *
 * @author otsuru
 * @since 2026/2/5
 */
public class ScalarResampler extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    /**
     * Path of scalar file.
     */
    private Path scalarPath;

    /**
     * Path of voxel information file.
     */
    private Path resampleVoxelPath;

    private double dLatitudeKm;
    private double dLatitudeDeg;
    private boolean setLatitudeByKm;
    private double baseLatitude;

    private double dLongitudeKm;
    private double dLongitudeDeg;
    private boolean setLongitudeByKm;
    private double baseLongitude;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of scalar file to resample, must be set.");
            pw.println("#scalarPath scalar.Vs.PERCENT.lst");
            pw.println("##Path of a voxel information file defining the points at which to resample, must be set.");
            pw.println("#resampleVoxelPath voxel.inf");
            pw.println("##########In the following, provide information about INPUT scalar file.");
            pw.println("##(double) Latitude spacing [km]; (0:). If unset, the following dLatitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees.");
            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg]; (0:). (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(double) Arbitrary voxel latitude [deg]. (0)");
            pw.println("#baseLatitude ");
            pw.println("##(double) Longitude spacing [km]; (0:). If unset, the following dLongitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg]; (0:). (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Longitude at which voxels are aligned [deg]. (0)");
            pw.println("#baseLongitude ");
        }
        System.err.println(outPath + " is created.");
    }

    public ScalarResampler(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        scalarPath = property.parsePath("scalarPath", null, true, workPath);
        resampleVoxelPath = property.parsePath("resampleVoxelPath", null, true, workPath);

        if (property.containsKey("dLatitudeKm")) {
            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
            if (dLatitudeKm <= 0.0) throw new IllegalArgumentException("dLatitudeKm must be positive.");
            setLatitudeByKm = true;
        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0.0) throw new IllegalArgumentException("dLatitudeDeg must be positive.");
            setLatitudeByKm = false;
        }
        baseLatitude = property.parseDouble("baseLatitude", "0");

        if (property.containsKey("dLongitudeKm")) {
            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
            if (dLongitudeKm <= 0.0) throw new IllegalArgumentException("dLongitudeKm must be positive.");
            setLongitudeByKm = true;
        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0.0) throw new IllegalArgumentException("dLongitudeDeg must be positive.");
            setLongitudeByKm = false;
        }
        baseLongitude = property.parseDouble("baseLongitude", "0");
    }

    @Override
    public void run() throws IOException {

        // read input file
        ScalarListFile scalarFile = new ScalarListFile(scalarPath);
        VariableType variable = scalarFile.getVariable();
        ScalarType scalarType = scalarFile.getScalarType();
        Map<FullPosition, Double> discreteMap = scalarFile.getValueMap();
        Set<FullPosition> discretePositions = discreteMap.keySet();
        double centerRadius = discretePositions.stream().mapToDouble(FullPosition::getR).distinct().average().getAsDouble();

        // read voxel file
        VoxelInformationFile resampleVoxelFile = new VoxelInformationFile(resampleVoxelPath);
        double[] resampleRadii = resampleVoxelFile.getRadii();
        List<HorizontalPixel> resamplePixels = resampleVoxelFile.getHorizontalPixels();
        List<HorizontalPosition> resamplePositions = resamplePixels.stream().map(pixel -> pixel.getPosition()).collect(Collectors.toList());
        boolean crossDateLine = HorizontalPosition.crossesDateLine(resamplePositions);

        CoordinateConverter converter = new CoordinateConverter(dLatitudeKm, dLatitudeDeg, setLatitudeByKm, baseLatitude,
                dLongitudeKm, dLongitudeDeg, setLongitudeByKm, baseLongitude, centerRadius, crossDateLine);

        // calculate coordinate of sample points on the curvilinear grid
        List<XY> resampleCoordinates = new ArrayList<>();
        Map<XY, HorizontalPosition> resampleCoordinateMap = new HashMap<>();
        for (HorizontalPosition position : resamplePositions) {
            XY xy = converter.computeXY(position);
            resampleCoordinates.add(xy);
            resampleCoordinateMap.put(xy, position);
        }

        Map<FullPosition, Double> resampledMap = new LinkedHashMap<>();

        // process for each radius in set of sample positions
        for (double radius : resampleRadii) {
            List<FullPosition> inLayerDiscretePositions = discretePositions.stream()
                    .filter(pos -> Precision.equals(pos.getR(), radius, FullPosition.RADIUS_EPSILON)).sorted().collect(Collectors.toList());
            if (inLayerDiscretePositions.size() == 0) {
                System.err.println("No input data for radius " + radius);
                continue;
            }

            // recast input map as map on curvilinear grid
            Map<IntegerXY, Double> integerGridMap = new LinkedHashMap<>();
            for (FullPosition position : inLayerDiscretePositions) {
                XY xy = converter.computeXY(position);
                IntegerXY integerXY = xy.toNearestIntegerXY();
                integerGridMap.put(integerXY, discreteMap.get(position));
            }

            // resample
            Map<XY, Double> resampledXYMap = Interpolation.onIntegerGrid(integerGridMap, resampleCoordinates, false);

            // recast resampled map on 3-D spherical coordinate
            for (Map.Entry<XY, Double> entry : resampledXYMap.entrySet()) {
                FullPosition position = resampleCoordinateMap.get(entry.getKey()).toFullPosition(radius);
                resampledMap.put(position, entry.getValue());
            }
        }

        if (resampledMap.isEmpty()) return;

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "scalarResampled", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // write resampled scalar file
        Path outputInterpolatedPath = outPath.resolve(ScalarListFile.generateFileName(variable, scalarType));
        ScalarListFile.write(resampledMap, crossDateLine, outputInterpolatedPath);
    }

}
