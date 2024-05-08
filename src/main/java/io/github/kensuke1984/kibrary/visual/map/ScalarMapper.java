package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Creates shellscripts to map {@link ScalarListFile}.
 *
 * @see Interpolation#inEachMapLayer(Map, double, double, boolean, double, boolean, boolean)
 * @author otsuru
 * @since 2022/7/18
 */
public class ScalarMapper extends Operation {

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
     * Path of scalar file to be used as mask.
     */
    private Path maskPath;

    private double[] boundaries;
    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private int nPanelsPerRow;
    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;
    private double marginLatitudeRaw;
    private boolean setMarginLatitudeByKm;
    private double marginLongitudeRaw;
    private boolean setMarginLongitudeByKm;
    private double scale;
    /**
     * Whether to display map as mosaic without smoothing.
     */
    private boolean mosaic;
    private double maskThreshold;

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
            pw.println("##Path of scalar file, must be set.");
            pw.println("#scalarPath scalar.Vs.PERCENT.lst");
            pw.println("##Path of scalar file for mask, when mask is to be applied.");
            pw.println("#maskPath scalar.Vs.PERCENT_RATIO.lst");
            pw.println("##(double[]) The display values of each layer boundary, listed from the inside using spaces. (0 50 100 150 200 250 300 350 400)");
            pw.println("#boundaries ");
            pw.println("##(int[]) Indices of layers to display, listed from the inside using spaces, when specific layers are to be displayed.");
            pw.println("##  Layers are numbered 0, 1, 2, ... from the inside.");
            pw.println("#displayLayers ");
            pw.println("##(int) Number of panels to display in each row. (4)");
            pw.println("#nPanelsPerRow ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax.");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##########The following should be set to half of dLatitude and dLongitude used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##########Parameters for perturbation values.");
            pw.println("##(double) Range of percent scale. (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing. (false)");
            pw.println("#mosaic ");
            pw.println("##(double) Threshold for mask. (0.3)");
            pw.println("#maskThreshold ");
        }
        System.err.println(outPath + " is created.");
    }

    public ScalarMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        scalarPath = property.parsePath("scalarPath", null, true, workPath);
        if (property.containsKey("maskPath")) {
            maskPath = property.parsePath("maskPath", null, true, workPath);
        }

        boundaries = property.parseDoubleArray("boundaries", "0 50 100 150 200 250 300 350 400");
        if (property.containsKey("displayLayers")) displayLayers = property.parseIntArray("displayLayers", null);
        nPanelsPerRow = property.parseInt("nPanelsPerRow", "4");
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitudeRaw = property.parseDouble("marginLatitudeKm", null);
            setMarginLatitudeByKm = true;
        } else {
            marginLatitudeRaw = property.parseDouble("marginLatitudeDeg", "2.5");
            setMarginLatitudeByKm = false;
        }
        if (marginLatitudeRaw <= 0) throw new IllegalArgumentException("marginLatitude must be positive");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitudeRaw = property.parseDouble("marginLongitudeKm", null);
            setMarginLongitudeByKm = true;
        } else {
            marginLongitudeRaw = property.parseDouble("marginLongitudeDeg", "2.5");
            setMarginLongitudeByKm = false;
        }
        if (marginLongitudeRaw <= 0) throw new IllegalArgumentException("marginLongitude must be positive");

        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

        // read input file
        ScalarListFile inputFile = new ScalarListFile(scalarPath);
        VariableType variable = inputFile.getVariable();
        ScalarType scalarType = inputFile.getScalarType();
        Map<FullPosition, Double> discreteMap = inputFile.getValueMap();
        Set<FullPosition> positions = discreteMap.keySet();
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // decide map region
        if (mapRegion == null) mapRegion = ScalarMapShellscript.decideMapRegion(positions);
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        double gridInterval = ScalarMapShellscript.decideGridSampling(positions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "scalarMap", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // copy discrete perturbation file to outPath
        Path outputDiscretePath = outPath.resolve(ScalarListFile.generateFileName(variable, scalarType));
        Files.copy(scalarPath, outputDiscretePath);
        // output interpolated perturbation file
        Map<FullPosition, Double> interpolatedMap = Interpolation.inEachMapLayer(discreteMap, gridInterval,
                marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, crossDateLine, mosaic);
        Path outputInterpolatedPath = outPath.resolve(ScalarListFile.generateFileName(variable, scalarType, "XY"));
        ScalarListFile.write(interpolatedMap, crossDateLine, outputInterpolatedPath);

        VariableType maskVariable = null;
        ScalarType maskScalarType = null;
        if (maskPath != null) {
            ScalarListFile maskInputFile = new ScalarListFile(maskPath);
            maskVariable = maskInputFile.getVariable();
            maskScalarType = maskInputFile.getScalarType();
            Map<FullPosition, Double> discreteMaskMap = maskInputFile.getValueMap();
            // copy discrete mask file to outPath
            Path outMaskPath = outPath.resolve(ScalarListFile.generateFileName(maskVariable, maskScalarType, "forMask"));
            Files.copy(maskPath, outMaskPath);
            // output interpolated perturbation file, in range [0:360) when crossDateLine==true so that mapping will succeed
            Map<FullPosition, Double> interpolatedMaskMap = Interpolation.inEachMapLayer(discreteMaskMap, gridInterval,
                    marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, crossDateLine, mosaic);
            Path outputInterpolatedMaskPath = outPath.resolve(ScalarListFile.generateFileName(maskVariable, maskScalarType, "forMaskXY"));
            ScalarListFile.write(interpolatedMaskMap, crossDateLine, outputInterpolatedMaskPath);
        }

        // output shellscripts
        ScalarMapShellscript script = new ScalarMapShellscript(variable, scalarType, radii, boundaries,
                mapRegion, gridInterval, scale, nPanelsPerRow);
        if (displayLayers != null) script.setDisplayLayers(displayLayers);
        if (maskPath != null) script.setMask(maskVariable, maskScalarType, maskThreshold);
        script.write(outPath);
        String fileNameRoot = script.getPlotFileNameRoot();
        System.err.println("After this finishes, please enter " + outPath
                + "/ and run " + fileNameRoot + "Grid.sh and " + fileNameRoot + "Map.sh");
    }

}
