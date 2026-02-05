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
 * This class assumes that the input grid can be recasted onto an integer grid.
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
     * Grid interval of input scalar file [deg].
     */
    private double gridInterval;

    /**
     * Path of voxel information file.
     */
    private Path resampleVoxelPath;

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
            pw.println("##(double) Grid interval of input scalar file [deg]. (5)");
            pw.println("#gridInterval ");
            pw.println("##Path of a voxel information file defining the points at which to resample, must be set.");
            pw.println("#resampleVoxelPath voxel.inf");
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
        gridInterval = property.parseDouble("gridInterval", "5");
        resampleVoxelPath = property.parsePath("resampleVoxelPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {

        // read input file
        ScalarListFile scalarFile = new ScalarListFile(scalarPath);
        VariableType variable = scalarFile.getVariable();
        ScalarType scalarType = scalarFile.getScalarType();
        Map<FullPosition, Double> discreteMap = scalarFile.getValueMap();
        Set<FullPosition> discretePositions = discreteMap.keySet();

        // read voxel file
        VoxelInformationFile resampleVoxelFile = new VoxelInformationFile(resampleVoxelPath);
        double[] resampleRadii = resampleVoxelFile.getRadii();
        List<HorizontalPixel> resamplePixels = resampleVoxelFile.getHorizontalPixels();
        Set<HorizontalPosition> resamplePositions = resamplePixels.stream().map(pixel -> pixel.getPosition()).collect(Collectors.toSet());
        boolean crossDateLine = HorizontalPosition.crossesDateLine(resamplePositions);

        // find smallest longitude and latitude of input discrete map
        double minLatitude = discretePositions.stream().mapToDouble(FullPosition::getLatitude).min().getAsDouble();
        double minLongitude = discretePositions.stream().mapToDouble(FullPosition::getLongitude).min().getAsDouble();

        // calculate coordinate of sample points on the integer grid
        List<XY> resampleCoordinates = new ArrayList<>();
        Map<XY, HorizontalPosition> resampleCoordinateMap = new HashMap<>();
        for (HorizontalPosition position : resamplePositions) {
            double y = (position.getLatitude() - minLatitude) / gridInterval;
            double x = (position.getLongitude() - minLongitude) / gridInterval;
            XY xy = new XY(x, y);
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

            // recast input map as map on integer grid
            Map<IntegerXY, Double> integerGridMap = new LinkedHashMap<>();
            for (FullPosition position : inLayerDiscretePositions) {
                int y = (int) Math.round((position.getLatitude() - minLatitude) / gridInterval);
                int x = (int) Math.round((position.getLongitude() - minLongitude) / gridInterval);
                IntegerXY integerXY = new IntegerXY(x, y);
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
