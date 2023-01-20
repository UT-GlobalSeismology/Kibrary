package io.github.kensuke1984.kibrary.fusion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation to design the fusion of {@link UnknownParameter}s to create a coarse grid.
 *
 * TODO fuse horizontally
 * TODO use partialTypes
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

    private double[] borderRadii;

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
            pw.println("##(double) Radii of layer borders, listed using spaces [km] (3480 3530 3580 3630 3680 3730 3780 3830 3880)");
            pw.println("##  Parameters with radii outside this range will not be used.");
            pw.println("#borderRadii ");
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

        borderRadii = Arrays.stream(property.parseDoubleArray("borderRadii", "3480 3530 3580 3630 3680 3730 3780 3830 3880"))
                .sorted().toArray();
        if (borderRadii.length < 2) throw new IllegalArgumentException("There must be at least 2 values for borderRadii");
    }

    @Override
    public void run() throws IOException {
        String dateStr = GadgetAid.getTemporaryString();

        // read input
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);
        List<HorizontalPosition> pixelPositions = parameterList.stream()
                .map(param -> param.getPosition().toHorizontalPosition()).distinct().collect(Collectors.toList());

        FusionDesign fusionDesign = new FusionDesign();

        for (HorizontalPosition pixelPosition : pixelPositions) {

            // fuse voxels within each layer
            for (int i = 1; i < borderRadii.length; i++) {
                double lowerR = borderRadii[i - 1];
                double upperR = borderRadii[i];
                List<UnknownParameter> correspondingParameters = parameterList.stream()
                        .filter(param -> param.getPosition().toHorizontalPosition().equals(pixelPosition)
                                && lowerR <= param.getPosition().getR() && param.getPosition().getR() < upperR)
                        .collect(Collectors.toList());
                fusionDesign.addFusion(correspondingParameters);
            }

        }

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "coarseGrid", folderTag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output fusion design file
        Path outputFusionPath = outPath.resolve("fusion.inf");
        FusionInformationFile.write(fusionDesign, outputFusionPath);

        // output unknown parameter file
        List<UnknownParameter> fusedParameterList = parameterList.stream()
                .filter(param -> !fusionDesign.fuses(param)).collect(Collectors.toList());
        fusedParameterList.addAll(fusionDesign.getFusedParameters());
        Path outputUnknownsPath = outPath.resolve("unknowns.lst");
        UnknownParameterFile.write(fusedParameterList, outputUnknownsPath);
    }

}
