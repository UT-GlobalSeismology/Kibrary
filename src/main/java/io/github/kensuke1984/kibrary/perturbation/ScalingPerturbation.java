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
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.ConvertModelFileFormat;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Creates scalar files by adding or multiplying scaling value to the input scalar file.
 * Output files are created for each {@link VariableType} and three {@link ScalarType} (ABSOLUTE, DELTA, PERCENT).
 * Output files are placed under a output folder.
 *
 * @author rei
 * @since 2024/6/11
 */
public class ScalingPerturbation extends Operation {

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
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;
    /**
     * File of 1D structure used in inversion
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion
     */
    private String initialStructureName;
    /**
     * Model file with perturbation information
     */
    private Path modelPath;
    /**
     * The scalar type of input model file. {ABSOLUTE, DELTA, PERCENT}
     */
    private ScalarType scalarType;
    /**
     * A variable type to use for scaling
     */
    private VariableType inVariableType;
    /**
     * Variable types to be scaled.
     */
    private List<VariableType> outVariableTypes;
    /**
     * The type of scaling. (i.e. which calculation to perform on the input values.) {multiply, add}
     */
    private String scaleType;
    /**
     * The value of scale (= out value / in value) or (= out value - invalue).
     */
    private double[] scaleValues;

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
            pw.println("##(String) A tag to include in output folder names. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
            pw.println("##The type of scalars of input model file, from {ABSOLUTE, DELTA, PERCENT}. (PERCENT)");
            pw.println("#scalarType ");
            pw.println("##A variable type to use for scaling, from. (Vs)");
            pw.println("#inVariableType ");
            pw.println("##Variable types to be scaled, listed using spaces. (Vp)");
            pw.println("#outVariableTypes ");
            pw.println("##The type of scaling, from {multiply, add}. (multiply)");
            pw.println("#scaleType add");
            pw.println("##The values for scaling ((= out value / in value) or (= out value - in value)), listed using spaces in the order of partialTypes, must be set.");
            pw.println("#scaleValues ");
        }
        System.err.println(outPath + " is created.");
    }

    public ScalingPerturbation(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);

        scalarType = ScalarType.valueOf(property.parseString("scalarType", "PERCENT"));
        inVariableType = VariableType.valueOf(property.parseString("inVariableType", "Vs"));
        outVariableTypes = Arrays.stream(property.parseStringArray("outVariableTypes", "Vp")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        scaleType = property.parseString("scaleType", "multiply");
        scaleValues = Arrays.stream(property.parseDoubleArray("scaleValues", null)).toArray();
    }

   @Override
   public void run() throws IOException {
       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);

       // create output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "scaled", folderTag, appendFileDate, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       List<KnownParameter> scaledList = new ArrayList<>();
       for (KnownParameter known : knowns) {
           if (!known.getParameter().getVariableType().equals(inVariableType))
               continue;
           // compute scaled value for each out variable type
           for (int i = 0; i < outVariableTypes.size(); i++) {
               UnknownParameter parameter = UnknownParameterFile.convertVariableType(known.getParameter(), outVariableTypes.get(i));
               double scaled;
               if (scaleType.equals("multiply")) {
                   scaled = known.getValue() * scaleValues[i];
               } else if (scaleType.equals("add")) {
                   scaled = known.getValue() + scaleValues[i];
               } else {
                   throw new IllegalArgumentException("scaleType must be choosed from multiply or add");
               }
               scaledList.add(new KnownParameter(parameter, scaled));
           }
       }

       // read initial structure
       System.err.print("Initial structure: ");
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);
       ConvertModelFileFormat.convertAndOutputModelFiles(scaledList, initialStructure, scalarType, outVariableTypes, outPath);
   }
}
