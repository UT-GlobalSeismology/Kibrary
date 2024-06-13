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
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

public class ScalingPerturbation extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file name. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * File of 1D structure used in inversion
     */
    private Path initialStructurePath; //TODO
    /**
     * Name of 1D structure used in inversion
     */
    private String initialStructureName; //TODO
    /**
     * Model file with perturbation information
     */
    private Path modelPath;
    /**
     * The format of values of model file. {difference, percent, absolute}
     */
    private String valueFormat; //TODO
    /**
     * A variable type to use for scaling
     */
    private VariableType inVariableType;
    /**
     * Variable types to be scaled.
     */
    private List<VariableType> outVariableTypes;
    /**
     * The value of scale (= out value / in value).
     */
    private double[] scaleValues;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
            pw.println("##A variable type to use for scaling, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#inVariableType ");
            pw.println("##Variable types to be scaled, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vp)");
            pw.println("#outVariableTypes ");
            pw.println("##The values for scaling (= out value / in value), listed using spaces in the order of partialTypes, must be set.");
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
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        modelPath = property.parsePath("modelPath", null, true, workPath);
        inVariableType = VariableType.valueOf(property.parseString("inVariableType", "Vs"));
        outVariableTypes = Arrays.stream(property.parseStringArray("outVariableTypes", "Vp")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        scaleValues = Arrays.stream(property.parseDoubleArray("scaleValues", null)).toArray();
    }

   @Override
   public void run() throws IOException {
       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);

       // output
       Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName("percent", fileTag, GadgetAid.getTemporaryString(), ".lst"));

       List<KnownParameter> percentList = new ArrayList<>();
       for (KnownParameter known : knowns) {
           if (!known.getParameter().getVariableType().equals(inVariableType))
               continue;
           // compute scaled value for each out variable type
           for (int i = 0; i < outVariableTypes.size(); i++) {
               UnknownParameter parameter = UnknownParameterFile.convertVariableType(known.getParameter(), outVariableTypes.get(i));
               double percent = known.getValue() * scaleValues[i];
               percentList.add(new KnownParameter(parameter, percent));
           }
       }
       KnownParameterFile.write(percentList, outputPath);
   }
}
