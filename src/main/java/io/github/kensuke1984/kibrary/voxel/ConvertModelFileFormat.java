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
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.perturbation.PerturbationVoxel;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * For a given scalar file, create three kinds of file of the corresponding {@link ScalarType} (ABSOLUTE, DELTA, PERCENT).
 * Output files are created for each {@link VariableType} and placed under a output folder.
 *
 * @author rei
 * @since 2024/6/11
 */
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
     * Variable types to perturb
     */
    private List<VariableType> variables;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
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
            pw.println("##Variables to perturb, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#variables ");
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
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);
        scalarType = ScalarType.valueOf(property.parseString("scalarType", "PERCENT"));

        variables = Arrays.stream(property.parseStringArray("variables", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toList());
    }

   @Override
   public void run() throws IOException {
       // read initial structure
       System.err.print("Initial structure: ");
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);

       // create output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "models", folderTag, appendFileDate, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       convertAndOutputModelFiles(knowns, initialStructure, scalarType, variables, outPath);
   }

   public static void convertAndOutputModelFiles(List<KnownParameter> knowns, PolynomialStructure initialStructure,
           ScalarType scalarType, List<VariableType> variables, Path outPath) throws IOException {

       PerturbationModel model = new PerturbationModel(knowns, initialStructure, scalarType);

       for (VariableType variable : variables) {
           Path outDiferrencePath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.DELTA));
           Path outPercentPath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.PERCENT));
           Path outAbsolutePath = outPath.resolve(ScalarListFile.generateFileName(variable, ScalarType.ABSOLUTE));

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
               if (existing) continue;

               UnknownParameter originalParameter = known.getParameter();
               // compute values of the model for each variable type

                   UnknownParameter parameter = UnknownParameterFile.convertVariableType(originalParameter, variable);
                   PerturbationVoxel voxel = model.getVoxel(originalParameter);
                   double difference = voxel.getValue(variable, ScalarType.DELTA);
                   double percent = voxel.getValue(variable, ScalarType.PERCENT);
                   double absolute = voxel.getValue(variable, ScalarType.ABSOLUTE);
                   differenceList.add(new KnownParameter(parameter, difference));
                   percentList.add(new KnownParameter(parameter, percent));
                   absoluteList.add(new KnownParameter(parameter, absolute));
           }
           KnownParameterFile.write(differenceList, outDiferrencePath);
           KnownParameterFile.write(percentList, outPercentPath);
           KnownParameterFile.write(absoluteList, outAbsolutePath);
       }
   }
}
