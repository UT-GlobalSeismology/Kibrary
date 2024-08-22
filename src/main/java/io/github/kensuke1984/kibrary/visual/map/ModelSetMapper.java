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
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
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
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Creates shellscripts to map a set of inversion results.
 *
 * @see Interpolation#inEachMapLayer(Map, double, double, boolean, double, boolean, boolean)
 * @author otsuru
 * @since 2022/4/9
 * @version 2022/7/17 moved and renamed from model.VelocityModelMapper to visual.ModelSetMapper
 */
public class ModelSetMapper extends Operation {

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
     * The root folder containing results of inversion.
     */
    private Path resultPath;
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
    /**
     * Solvers for equation.
     */
    private Set<InverseMethodEnum> inverseMethods;
    private int maxNum;
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
            pw.println("##Path of a root folder containing results of inversion. (.)");
            pw.println("#resultPath ");
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
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LS,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(int) Maximum number of basis vectors to map. (10)");
            pw.println("#maxNum ");
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

    public ModelSetMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        resultPath = property.parsePath("resultPath", ".", true, workPath);
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
        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        maxNum = property.parseInt("maxNum", "10");

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

        // read parameters
        Path unknownsPath = resultPath.resolve("unknowns.lst");
        Set<FullPosition> positions = UnknownParameterFile.read(unknownsPath).stream()
                .map(unknown -> unknown.getPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // read fusion file
        FusionDesign fusionDesign = null;
        if (fusionPath != null) {
            fusionDesign = FusionInformationFile.read(fusionPath);
        }

        // decide map region
        if (mapRegion == null) mapRegion = ScalarMapShellscript.decideMapRegion(positions);
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        double gridInterval = ScalarMapShellscript.decideGridSampling(positions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMaps", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // write list files
        for (InverseMethodEnum method : inverseMethods) {
            Path methodPath = resultPath.resolve(method.simpleName());
            if (!Files.exists(methodPath)) {
                System.err.println("Results for " + method.simpleName() + " do not exist, skipping.");
                continue;
            }

            for (int k = 1; k <= maxNum; k++){
                Path answerPath = methodPath.resolve(method.simpleName() + k + ".lst");
                if (!Files.exists(answerPath)) {
                    System.err.println("Results for " + method.simpleName() + k + " do not exist, skipping.");
                    continue;
                }
                List<KnownParameter> knowns = KnownParameterFile.read(answerPath);

                if (fusionPath != null) knowns = fusionDesign.reverseFusion(knowns);

                PerturbationModel model = new PerturbationModel(knowns, initialStructure);
                if (!referenceStructure.equals(initialStructure)) {
                    model = model.withReferenceStructureAs(referenceStructure);
                }

                Path outBasisPath = outPath.resolve(method.simpleName() + k);
                Files.createDirectories(outBasisPath);

                for (VariableType variable : variableTypes) {
                    // output discrete perturbation file
                    Map<FullPosition, Double> discreteMap = model.getValueMap(variable, ScalarType.PERCENT);
                    Path outputDiscretePath = outBasisPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT));
                    ScalarListFile.write(discreteMap, outputDiscretePath);
                    // output interpolated perturbation file, in range [0:360) when crossDateLine==true so that mapping will succeed
                    Map<FullPosition, Double> interpolatedMap = Interpolation.inEachMapLayer(discreteMap, gridInterval,
                            marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, crossDateLine, mosaic);
                    Path outputInterpolatedPath = outBasisPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT, "XY"));
                    ScalarListFile.write(interpolatedMap, crossDateLine, outputInterpolatedPath);
                }
            }
        }

        // write shellscripts for mapping
        for (VariableType variable : variableTypes) {
            ScalarMapShellscript script = new ScalarMapShellscript(variable, ScalarType.PERCENT, radii, boundaries,
                    mapRegion, gridInterval, scale, nPanelsPerRow);
            if (displayLayers != null) script.setDisplayLayers(displayLayers);
            script.write(outPath);
            String fileNameRoot = script.getPlotFileNameRoot();
            writeParentShellscript(fileNameRoot, outPath.resolve(fileNameRoot + "AllMap.sh"));
            System.err.println("After this finishes, please enter " + outPath + "/ and run " + fileNameRoot + "AllMap.sh");
        }
    }

    private void writeParentShellscript(String fileNameRoot, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            for (InverseMethodEnum method : inverseMethods) {
                pw.println("");
                pw.println("for i in `seq 1 " + maxNum + "`");
                pw.println("do");
                pw.println("    cd " + method.simpleName() + "$i");
                pw.println("    ln -s ../" + fileNameRoot + "Grid.sh .");
                pw.println("    ln -s ../" + fileNameRoot + "Map.sh .");
                pw.println("    ln -s ../cp_master.cpt .");
                pw.println("    sh " + fileNameRoot + "Grid.sh");
                pw.println("    wait");
                pw.println("    sh " + fileNameRoot + "Map.sh");
                pw.println("    wait");
                pw.println("    rm -rf *.grd gmt.* cp.cpt");
                pw.println("    unlink " + fileNameRoot + "Grid.sh");
                pw.println("    unlink " + fileNameRoot + "Map.sh");
                pw.println("    unlink cp_master.cpt");
                pw.println("    cd ..");
                pw.println("done");
            }
        }
    }

}
