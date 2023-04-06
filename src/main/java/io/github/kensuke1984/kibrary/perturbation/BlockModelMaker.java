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
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation to create input models for 3D block tests.
 * <p>
 * Shapes of the block input is to be given as a combination of boxes.
 * <p>
 * Perturbations are applied to each voxel in the input voxel file.
 * At each voxel, each given {@link VariableType} is perturbed.
 * Then, the model parameter value for each {@link PartialType} is computed and exported as a {@link KnownParameterFile}.
 *
 * @author otsuru
 * @since 2022/10/11
 */
public class BlockModelMaker extends Operation {

    private static final int MAX_BOX = 10;

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
    private List<PartialType> partialTypes;

    private List<Box> boxes = new ArrayList<>();

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
            pw.println("##Variable types to perturb, listed using spaces (Vs)");
            pw.println("#variableTypes ");
            pw.println("##Partial types to set in model, listed using spaces (MU)");
            pw.println("#partialTypes ");
            pw.println("##########From here on, set percentages of perturbations and the borders of boxes to place them.");
            pw.println("########## Percentages of perturbations must be listed using spaces in the order of variableTypes.");
            pw.println("########## Defaults of borders are -90, 90, -180, 180, 0, and Double.MAX_VALUE, respectively.");
            pw.println("########## A box is recongized if the percentage values are properly set.");
            pw.println("########## Up to " + MAX_BOX + " boxes can be managed. Any box may be left blank.");
            for (int i = 1; i <= MAX_BOX; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " box");
                pw.println("#percents" + i + " ");
                pw.println("#lowerLatitude" + i + " ");
                pw.println("#upperLatitude" + i + " ");
                pw.println("#lowerLongitude" + i + " ");
                pw.println("#upperLongitude" + i + " ");
                pw.println("#lowerRadius" + i + " ");
                pw.println("#upperRadius" + i + " ");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public BlockModelMaker(Property property) throws IOException {
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

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "MU")).map(PartialType::valueOf)
                .collect(Collectors.toList());

        for (int i = 1; i <= MAX_BOX; i++) {
            if (property.containsKey("percents" + i)) {
                double[] percents = property.parseDoubleArray("percents" + i, null);
                if (percents.length != variableTypes.size())
                    throw new IllegalArgumentException("Number of percents" + i + " does not match number of variableTypes.");
                boxes.add(new Box(
                        percents,
                        property.parseDouble("lowerLatitude" + i, "-90"),
                        property.parseDouble("upperLatitude" + i, "90"),
                        property.parseDouble("lowerLongitude" + i, "-180"),
                        property.parseDouble("upperLongitude" + i, "180"),
                        property.parseDouble("lowerRadius" + i, "0"),
                        property.parseDouble("upperRadius" + i, String.valueOf(Double.MAX_VALUE))));
            }

        }
    }

    @Override
    public void run() throws IOException {

        // set structure to use
        PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        // read voxel file
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        List<HorizontalPixel> pixels = file.getHorizontalPixels();

        // set checkerboard model
        System.err.println("Creating block perturbations.");
        PerturbationModel model = new PerturbationModel();
        for (HorizontalPixel pixel : pixels) {
            // extract information of each horizontal pixel
            HorizontalPosition horizontalPosition = pixel.getPosition();
            double dLatitude = pixel.getDLatitude();
            double dLongitude = pixel.getDLongitude();
            // loop for each layer
            for (int i = 0; i < radii.length; i++) {
                FullPosition position = horizontalPosition.toFullPosition(radii[i]);

                // construct voxel
                double volume = Earth.computeVolume(position, layerThicknesses[i], dLatitude, dLongitude);
                PerturbationVoxel voxel = new PerturbationVoxel(position, volume, initialStructure);
                for (int k = 0; k < variableTypes.size(); k++) {
                    double percent = findPercentage(position, k);
                    voxel.setPercent(variableTypes.get(k), percent);
                    // rho must be set to default if it is not in variableTypes  TODO: should this be done to other variables?
                    voxel.setDefaultIfUndefined(VariableType.RHO);
                }
                model.add(voxel);
            }
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "block", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        System.err.println("Outputting perturbation list files.");
        for (VariableType variable : variableTypes) {
            Path paramPath = outPath.resolve(variable.toString().toLowerCase() + "Percent.lst");
            PerturbationListFile.writePercentForType(variable, model, paramPath);
        }

        // set known parameters
        System.err.println("Setting model parameters.");
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

    private double findPercentage(FullPosition position, int variableNum) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box box = boxes.get(i);
            if (position.isInRange(box.lowerLatitude, box.upperLatitude, box.lowerLongitude, box.upperLongitude,
                    box.lowerRadius, box.upperRadius))
                return box.percents[variableNum];
        }
        return 0;
    }

    private class Box {
        private double[] percents;
        private double lowerLatitude;
        private double upperLatitude;
        private double lowerLongitude;
        private double upperLongitude;
        private double lowerRadius;
        private double upperRadius;

        private Box(double percents[], double lowerLatitude, double upperLatitude, double lowerLongitude, double upperLongitude,
                double lowerRadius, double upperRadius) {
            this.percents = percents;
            this.lowerLatitude = lowerLatitude;
            this.upperLatitude = upperLatitude;
            this.lowerLongitude = lowerLongitude;
            this.upperLongitude = upperLongitude;
            this.lowerRadius = lowerRadius;
            this.upperRadius = upperRadius;
        }

    }
}
