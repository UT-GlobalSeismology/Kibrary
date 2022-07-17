package io.github.kensuke1984.kibrary.tests;

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
import io.github.kensuke1984.kibrary.model.PerturbationModel;
import io.github.kensuke1984.kibrary.model.PerturbationModelFile;
import io.github.kensuke1984.kibrary.model.PerturbationVoxel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Creates a checkerboard model file.
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
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;

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

    private List<ParameterType> parameterTypes;
    private double[] percents;
    private boolean[] signFlips;
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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#tag ");
            pw.println("##Path of a voxel information file, must be set");
            pw.println("#voxelPath voxel.lst");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##Parameter types to perturb, listed using spaces, must be set.");
            pw.println("#parameterTypes ");
            pw.println("##(double) Percentage of perturbation, listed using spaces in the order of partialTypes, must be set.");
            pw.println("#percents ");
            pw.println("##(boolean) Whether to flip the sign, listed using spaces in the order of partialTypes, must be set.");
            pw.println("#signFlips ");
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
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        voxelPath = property.parsePath("voxelPath", null, true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        parameterTypes = Arrays.stream(property.parseStringArray("parameterTypes", null)).map(ParameterType::valueOf)
                .collect(Collectors.toList());
        percents = property.parseDoubleArray("percents", null);
        if (percents.length != parameterTypes.size())
            throw new IllegalArgumentException("Number of percents does not match number of parameterTypes.");
        signFlips = property.parseBooleanArray("signFlips", null);
        if (signFlips.length != parameterTypes.size())
            throw new IllegalArgumentException("Number of signFlips does not match number of parameterTypes.");
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
        double dLatitude = file.getSpacingLatitude();
        double dLongitude = file.getSpacingLongitude();
        HorizontalPosition[] positions = file.getHorizontalPositions();

        // set checkerboard model
        PerturbationModel model = new PerturbationModel();
        HorizontalPosition referencePosition = positions[0];
        for (HorizontalPosition horizontalPosition : positions) {
            for (int i = 0; i < radii.length; i++) {
                FullPosition position = horizontalPosition.toFullPosition(radii[i]);
                int numDiff = (int) Math.round((position.getLatitude() - referencePosition.getLatitude()) / dLatitude
                        + (position.getLongitude() - referencePosition.getLongitude()) / dLongitude) + i;

                double volume = Earth.getVolume(position, layerThicknesses[i], dLatitude, dLongitude);
                PerturbationVoxel voxel = new PerturbationVoxel(position, volume, initialStructure);
                for (int p = 0; p < parameterTypes.size(); p++) {
                    int sign = ((numDiff % 2 == 1) ^ signFlips[i]) ? -1 : 1; // ^ is XOR
                    voxel.setPercent(parameterTypes.get(i), percents[i] * sign);
                }
                model.add(voxel);
            }
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "checkerboard", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        System.err.println("Outputting perturbation model files.");
        for (ParameterType param : parameterTypes) {
            Path paramPath = outPath.resolve(param.toString().toLowerCase() + "Percent.lst");
            PerturbationModelFile.writePercentForType(param, model, paramPath);
        }

        // set known parameters
        List<KnownParameter> knowns = new ArrayList<>();
        for (PartialType partial : partialTypes) {
            for (PerturbationVoxel voxel : model.getVoxels()) {
                UnknownParameter unknown = new Physical3DParameter(partial, voxel.getPosition(), voxel.getVolume());
                KnownParameter known = new KnownParameter(unknown, voxel.getDelta(ParameterType.of(partial)));
                knowns.add(known);
            }
        }

        System.err.println("Outputting known parameter file.");
        Path knownPath = outPath.resolve("knowns.lst");
        KnownParameterFile.write(knowns, knownPath);

//
//        List<UnknownParameter> parameters = UnknownParameterFile.read(unknownParameterPath).stream()
//                .filter(param -> partialTypes.contains(param.getPartialType())).collect(Collectors.toList());
//        if (parameters.isEmpty()) {
//            System.err.println("No parameters with specified partialTypes exist.");
//            return;
//        }
//
//        double[] radii = parameters.stream().mapToDouble(param -> param.getPosition().getR()).distinct().sorted().toArray();
//        double[] latitudes = parameters.stream().mapToDouble(param -> param.getPosition().getLatitude()).distinct().sorted().toArray();
//        double[] longitudes = parameters.stream().mapToDouble(param -> param.getPosition().getLongitude()).distinct().sorted().toArray();
//
//        for (int i = 0; i < latitudes.length; i++) {
//            for (int j = 0; j < longitudes.length; j++) {
//                for (int k = 0; k < radii.length; k++) {
//
//                    FullPosition position = new FullPosition(latitudes[i], longitudes[j], radii[k]);
//                    List<UnknownParameter> paramsHere = parameters.stream()
//                            .filter(param -> param.getPosition().equals(position))
//                            .collect(Collectors.toList());
//
//                    for (UnknownParameter param : paramsHere) {
//
//                    }
//                }
//            }
//        }
    }

}
