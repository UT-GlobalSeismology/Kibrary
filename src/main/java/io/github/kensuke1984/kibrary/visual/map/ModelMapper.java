package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.fusion.FusionDesign;
import io.github.kensuke1984.kibrary.fusion.FusionInformationFile;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

/**
 * Creates shellscripts to map {@link KnownParameterFile}.
 *
 * @see Interpolation#inEachMapLayer(Map, double, double, boolean, double, boolean, boolean)
 * @author otsuru
 * @since 2022/7/17
 */
public class ModelMapper extends Operation {

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
     * Path of model file.
     */
    private Path modelPath;
    /**
     * File of 1D structure used in inversion.
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion.
     */
    private String initialStructureName;
    /**
     * File of 1D structure to map perturbations against.
     */
    private Path referenceStructurePath;
    /**
     * Name of 1D structure to map perturbations against.
     */
    private String referenceStructureName;
    /**
     * Path of a {@link FusionInformationFile}.
     */
    private Path fusionPath;
    private Set<VariableType> variableTypes;

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
            pw.println("##Path of model file, must be set.");
            pw.println("#modelPath model.lst");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a structure file to map perturbations against. If this is unset, the following referenceStructureName will be referenced.");
            pw.println("#referenceStructurePath ");
            pw.println("##Name of a structure model to map perturbations against. (PREM)");
            pw.println("#referenceStructureName ");
            pw.println("##Path of a fusion information file, if adaptive grid inversion is conducted.");
            pw.println("#fusionPath fusion.inf");
            pw.println("##Variable types to map, listed using spaces. (Vs)");
            pw.println("#variableTypes ");
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
        }
        System.err.println(outPath + " is created.");
    }

    public ModelMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        modelPath = property.parsePath("modelPath", null, true, workPath);
        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        if (property.containsKey("referenceStructurePath")) {
            referenceStructurePath = property.parsePath("referenceStructurePath", null, true, workPath);
        } else {
            referenceStructureName = property.parseString("referenceStructureName", "PREM");
        }
        if (property.containsKey("fusionPath"))
            fusionPath = property.parsePath("fusionPath", null, true, workPath);

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());

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
    }

    @Override
    public void run() throws IOException {

        // read initial structure
        System.err.print("Initial structure: ");
        PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);
        // read reference structure
        System.err.print("Reference structure: ");
        PolynomialStructure referenceStructure = PolynomialStructure.setupFromFileOrName(referenceStructurePath, referenceStructureName);

        // read knowns
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        Set<FullPosition> positions = KnownParameter.extractParameterList(knowns).stream()
                .map(unknown -> unknown.getPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // read and apply fusion file
        if (fusionPath != null) {
            FusionDesign fusionDesign = FusionInformationFile.read(fusionPath);
            knowns = fusionDesign.reverseFusion(knowns);
        }

        // build model
        PerturbationModel model = new PerturbationModel(knowns, initialStructure);
        if (!referenceStructure.equals(initialStructure)) {
            model = model.withReferenceStructureAs(referenceStructure);
        }

        // decide map region
        if (mapRegion == null) mapRegion = ScalarMapShellscript.decideMapRegion(positions);
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        double gridInterval = ScalarMapShellscript.decideGridSampling(positions);

        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMap", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (VariableType variable : variableTypes) {
            // output discrete perturbation file
            Map<FullPosition, Double> discreteMap = model.getValueMap(variable, ScalarType.PERCENT);
            Path outputDiscretePath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT));
            ScalarListFile.write(discreteMap, outputDiscretePath);
            // output interpolated perturbation file, in range [0:360) when crossDateLine==true so that mapping will succeed
            Map<FullPosition, Double> interpolatedMap = Interpolation.inEachMapLayer(discreteMap, gridInterval,
                    marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, crossDateLine, mosaic);
            Path outputInterpolatedPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT, "XY"));
            ScalarListFile.write(interpolatedMap, crossDateLine, outputInterpolatedPath);

            // output shellscripts
            ScalarMapShellscript script = new ScalarMapShellscript(variable, ScalarType.PERCENT, radii, boundaries,
                    mapRegion, gridInterval, scale, nPanelsPerRow);
            if (displayLayers != null) script.setDisplayLayers(displayLayers);
            script.write(outPath);
            String fileNameRoot = script.getPlotFileNameRoot();
            System.err.println("After this finishes, please enter " + outPath
                    + "/ and run " + fileNameRoot + "Grid.sh and " + fileNameRoot + "Map.sh");
        }
    }

}
