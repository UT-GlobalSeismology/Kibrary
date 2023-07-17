package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

/**
 * Operation that converts a 1-D model to a {@link PolynomialStructure}.
 *
 * @author otsuru
 * @since 2023/7/17
 */
public class ModelStructureConverter extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The first part of the name of output structure file
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) The first part of the name of output structure file. (PREM)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#fileTag ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelStructureConverter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "PREM");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);
    }

   @Override
   public void run() throws IOException {
       // set structure
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
       PerturbationModel model = new PerturbationModel(knowns, initialStructure);

       PolynomialStructure structure = null;

       Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName(nameRoot, fileTag, GadgetAid.getTemporaryString(), ".structure"));
       PolynomialStructureFile.write(structure, outputPath);
   }
}
