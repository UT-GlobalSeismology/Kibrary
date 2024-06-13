package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.perturbation.PerturbationVoxel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

public class ConvertModelFileFormat extends Operation {

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
     * The format of values of model file. {difference, percent, absolute}
     */
    private String valueFormat;
    /**
     * Variable types to perturb
     */
    private Set<VariableType> variableTypes;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#folderTag ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
            pw.println("##The format of values of model file, from {difference, percent, absolute}. (difference)");
            pw.println("#valueFormat ");
            pw.println("##Variable types to perturb, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#variableTypes ");
        }
        System.err.println(outPath + " is created.");
    }

    public ConvertModelFileFormat(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);
        valueFormat = property.parseString("valueFormat", "difference");

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
    }

   @Override
   public void run() throws IOException {
       // read initial structure
       System.err.print("Initial structure: ");
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
       PerturbationModel model = new PerturbationModel(knowns, initialStructure, valueFormat);

       // create output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "models", folderTag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
       Path outDiferrencePath = outPath.resolve("difference.lst");
       Path outPercentPath = outPath.resolve("percent.lst");
       Path outAbsolutePath = outPath.resolve("absolute.lst");

       List<KnownParameter> differenceList = new ArrayList<>();
       List<KnownParameter> percentList = new ArrayList<>();
       List<KnownParameter> absoluteList = new ArrayList<>();

       // compute values of the model for each voxel
       for (KnownParameter known : knowns) {
           boolean existing = false;
           // check whether the parameter is already existing
           for (KnownParameter exitParameter : differenceList) {
               if (known.getParameter().getPosition().equals(exitParameter.getParameter().getPosition()) &&
                       known.getParameter().getSize() == exitParameter.getParameter().getSize())
                   existing = true;
           }
           if (existing)
               continue;
           UnknownParameter originalParameter = known.getParameter();
           // compute values of the model for each variable type
           for (VariableType variable : variableTypes) {
               UnknownParameter parameter = UnknownParameterFile.convertVariableType(originalParameter, variable);
               PerturbationVoxel voxel = model.getVoxel(originalParameter);
               double difference = voxel.getDelta(variable);
               double percent = voxel.getPercent(variable);
               double absolute = voxel.getAbsolute(variable);
               differenceList.add(new KnownParameter(parameter, difference));
               percentList.add(new KnownParameter(parameter, percent));
               absoluteList.add(new KnownParameter(parameter, absolute));
           }
       }
       KnownParameterFile.write(differenceList, outDiferrencePath);
       KnownParameterFile.write(percentList, outPercentPath);
       KnownParameterFile.write(absoluteList, outAbsolutePath);
   }
}
