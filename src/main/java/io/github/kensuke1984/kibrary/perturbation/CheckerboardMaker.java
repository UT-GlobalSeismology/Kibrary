package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation that creates a checkerboard model file.
 * <p>
 * Perturbations are applied to each voxel in the input voxel file.
 * At each voxel, each given {@link VariableType} is perturbed.
 * Then, the model parameter value for each {@link PartialType} is computed and exported as a {@link KnownParameterFile}.
 * <p>
 * For the checkerboard pattern to be created properly,
 * dLatitudes must all be uniform,
 * and dLongitudes must be uniform at each latitude.
 *
 * @author otsuru
 * @since 2022/3/4
 */
public class CheckerboardMaker extends Operation {

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
     * Path of voxel information file
     */
    private Path voxelPath;
    /**
     * Structure file to use instead of PREM
     */
    private Path structurePath;
    /**
     * Structure to use
     */
    private String structureName;

    private List<VariableType> variableTypes;
    private double[] percents;
    private boolean[] signFlips;
    private double[] suppressFlipLatitudes;
    private double[] suppressFlipLongitudes;
    private List<PartialType> partialTypes;

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
            pw.println("##Path of a voxel information file, must be set");
            pw.println("#voxelPath voxel.inf");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##Variable types to perturb, listed using spaces, must be set.");
            pw.println("#variableTypes ");
            pw.println("##(double) Percentage of perturbation, listed using spaces in the order of partialTypes, must be set.");
            pw.println("#percents ");
            pw.println("##(boolean) Whether to flip the sign, listed using spaces in the order of partialTypes, must be set.");
            pw.println("#signFlips ");
            pw.println("##Latitudes to suppress sign flip, listed using spaces, if needed.");
            pw.println("#suppressFlipLatitudes ");
            pw.println("##Longitudes to suppress sign flip, listed using spaces, if needed.");
            pw.println("#suppressFlipLongitudes ");
            pw.println("##Partial types to set in model, listed using spaces, must be set.");
            pw.println("#partialTypes ");
        }
        System.err.println(outPath + " is created.");
    }

    public CheckerboardMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        voxelPath = property.parsePath("voxelPath", null, true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", null)).map(VariableType::valueOf)
                .collect(Collectors.toList());
        percents = property.parseDoubleArray("percents", null);
        if (percents.length != variableTypes.size())
            throw new IllegalArgumentException("Number of percents does not match number of variableTypes.");
        signFlips = property.parseBooleanArray("signFlips", null);
        if (signFlips.length != variableTypes.size())
            throw new IllegalArgumentException("Number of signFlips does not match number of variableTypes.");

        if (property.containsKey("suppressFlipLatitudes"))
            suppressFlipLatitudes = property.parseDoubleArray("suppressFlipLatitudes", null);
        if (property.containsKey("suppressFlipLongitudes"))
            suppressFlipLongitudes = property.parseDoubleArray("suppressFlipLongitudes", null);

        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", null)).map(PartialType::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public void run() throws IOException {

        // set structure to use
        PolynomialStructure initialStructure = null;
        if (structurePath != null) {
            initialStructure = PolynomialStructureFile.read(structurePath);
        } else {
            initialStructure = PolynomialStructure.of(structureName);
        }

        // read voxel file
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        List<HorizontalPixel> pixels = file.getHorizontalPixels();

        // set checkerboard model
        System.err.println("Creating checkerboard perturbations.");
        PerturbationModel model = new PerturbationModel();
        HorizontalPosition referencePosition = pixels.get(0).getPosition();
        for (HorizontalPixel pixel : pixels) {
            // extract information of each horizontal pixel
            HorizontalPosition horizontalPosition = pixel.getPosition();
            double dLatitude = pixel.getDLatitude();
            double dLongitude = pixel.getDLongitude();
            // loop for each layer
            for (int i = 0; i < radii.length; i++) {
                FullPosition position = horizontalPosition.toFullPosition(radii[i]);
                // find the sign shift with respect to referencePosition
                int numDiff = (int) Math.round((position.getLatitude() - referencePosition.getLatitude()) / dLatitude)
                        + (int) Math.round((position.getLongitude() - referencePosition.getLongitude()) / dLongitude)
                        + i + numForSuppressFlip(position);

                // construct voxel
                double volume = Earth.computeVolume(position, layerThicknesses[i], dLatitude, dLongitude);
                PerturbationVoxel voxel = new PerturbationVoxel(position, volume, initialStructure);
                for (int k = 0; k < variableTypes.size(); k++) {
                    // CAUTION: (numdiff % 2) can be either 1 or -1 !!
                    double percent = ((numDiff % 2 != 0) ^ signFlips[k]) ? -percents[k] : percents[k]; // ^ is XOR
                    voxel.setPercent(variableTypes.get(k), percent);
                    // rho must be set to default if it is not in variableTypes  TODO: should this be done to other variables?
                    voxel.setDefaultIfUndefined(VariableType.RHO);
                }
                model.add(voxel);
            }
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "checkerboard", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        System.err.println("Outputting perturbation list files.");
        for (VariableType variable : variableTypes) {
            Path paramPath = outPath.resolve(variable.toString().toLowerCase() + "Percent.lst");
            PerturbationListFile.writePercentForType(variable, model, paramPath);
        }

        // set known parameters
        System.err.println("Setting checkerboard model parameters.");
        List<KnownParameter> knowns = new ArrayList<>();
        for (PartialType partial : partialTypes) {
            for (PerturbationVoxel voxel : model.getVoxels()) {
                UnknownParameter unknown = new Physical3DParameter(partial, voxel.getPosition(), voxel.getVolume());
                KnownParameter known = new KnownParameter(unknown, voxel.getDelta(VariableType.of(partial)));
                knowns.add(known);
            }
        }

        Path knownPath = outPath.resolve("model.lst");
        KnownParameterFile.write(knowns, knownPath);
    }

    private int numForSuppressFlip(FullPosition position) {
        int num = 0;
        if (suppressFlipLatitudes != null) {
            for (double lat : suppressFlipLatitudes) {
                if (position.getLatitude() > lat) num++;
            }
        }
        if (suppressFlipLongitudes != null) {
            for (double lon : suppressFlipLongitudes) {
                if (position.getLongitude() > lon) num++;
            }
        }
        return num;
    }
}
