package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation to resample model at a given set of voxels.
 * <p>
 * Currently, only resampling for longitude can be done. Resampling for radius and latitude is not supported.
 *
 * @author otsuru
 * @since 2025/3/12
 */
public class ModelResampler extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * Path of model file.
     */
    private Path modelPath;
    /**
     * Path of voxel information file.
     */
    private Path voxelPath;

    private double marginLongitudeRaw;
    private boolean setMarginLongitudeByKm;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Path of model file, must be set.");
            pw.println("#modelPath model.lst");
            pw.println("##Path of a voxel information file, must be set.");
            pw.println("#voxelPath voxel.inf");
            pw.println("##########The following should be set to half of dLongitude used to design voxels (or smaller).");
            pw.println("##(double) Longitude margin at both ends [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelResampler(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        modelPath = property.parsePath("modelPath", null, true, workPath);
        voxelPath = property.parsePath("voxelPath", null, true, workPath);

        if (property.containsKey("marginLongitudeKm")) {
            marginLongitudeRaw = property.parseDouble("marginLongitudeKm", null);
            setMarginLongitudeByKm = true;
        } else {
            marginLongitudeRaw = property.parseDouble("marginLongitudeDeg", "2.5");
            setMarginLongitudeByKm = false;
        }
        if (marginLongitudeRaw <= 0) throw new IllegalArgumentException("marginLongitude must be positive");
    }

    @Override
    public void run() throws IOException {
        // read knowns
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        Set<VariableType> variables = knowns.stream().map(known -> known.getParameter().getVariableType()).collect(Collectors.toSet());

        // read voxel file
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        double meanRadius = Arrays.stream(radii).average().getAsDouble();
        List<HorizontalPixel> pixels = file.getHorizontalPixels();
        Set<HorizontalPosition> positions = pixels.stream().map(pixel -> pixel.getPosition()).collect(Collectors.toSet());
        double[] latitudes = positions.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);

        List<KnownParameter> interpolatedKnowns = new ArrayList<>();

        for (VariableType variable : variables) {
            // This is created as LinkedHashMap to preserve the order of grid points
            Map<FullPosition, Double> originalMap = new LinkedHashMap<>();
            // extract model values for this parameter
            knowns = knowns.stream().filter(known -> known.getParameter().getVariableType().equals(variable)).collect(Collectors.toList());
            // set model values, with latitude values in voxel set
            for (KnownParameter known : knowns) {
                FullPosition knownPosition = known.getParameter().getPosition();
                double knownLatitude = knownPosition.getLatitude();
                OptionalDouble latitudeOpt = Arrays.stream(latitudes).filter(lat -> Math.abs(lat - knownLatitude) < 0.01).distinct().sorted().findFirst();
                if (latitudeOpt.isEmpty()) continue;
                double latitude = latitudeOpt.getAsDouble();

                FullPosition position = new FullPosition(latitude, knownPosition.getLongitude(), knownPosition.getR());
                originalMap.put(position, known.getValue());
            }

            // This is created as LinkedHashMap to preserve the order of grid points
            Map<FullPosition, Double> interpolatedMap = new LinkedHashMap<>();
            // interpolate at each radius and latitude
            for (double radius : radii) {
                for (double latitude : latitudes) {
                    double[] sampleLongitudes = pixels.stream().map(pixel -> pixel.getPosition())
                            .filter(pos -> Precision.equals(pos.getLatitude(), latitude, FullPosition.LATITUDE_EPSILON))
                            .mapToDouble(pos -> pos.getLongitude(crossDateLine)).sorted().toArray();
                    interpolatedMap.putAll(Interpolation.forWestEastLine(originalMap, radius, latitude, sampleLongitudes,
                            marginLongitudeRaw, setMarginLongitudeByKm, meanRadius, crossDateLine, false));
                }
            }

            // add value for each voxel
            pixels.stream().forEach(pixel -> {
                // extract information of horizontal pixel
                HorizontalPosition horizontalPosition = pixel.getPosition();
                double dLatitude = pixel.getDLatitude();
                double dLongitude = pixel.getDLongitude();
                // loop for each layer
                for (int i = 0; i < radii.length; i++) {
                    // construct unknown parameter
                    FullPosition voxelPosition = horizontalPosition.toFullPosition(radii[i]);
                    double volume = Earth.computeVolume(voxelPosition, layerThicknesses[i], dLatitude, dLongitude);
                    Physical3DParameter parameter = new Physical3DParameter(variable, voxelPosition, volume);
                    // construct known parameter and add to list
                    if (!interpolatedMap.containsKey(voxelPosition)) {
                        System.err.println("No value for " + voxelPosition);
                        continue;
                    }
                    double value = interpolatedMap.get(voxelPosition);
                    KnownParameter known = new KnownParameter(parameter, value);
                    interpolatedKnowns.add(known);
                }
            });
        }

        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "model", fileTag, appendFileDate, null, ".lst");
        KnownParameterFile.write(interpolatedKnowns, outputPath);
    }

}
