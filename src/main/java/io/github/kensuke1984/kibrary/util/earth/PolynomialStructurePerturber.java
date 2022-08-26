package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * @author otsuru
 * @since 2022/8/25
 */
public class PolynomialStructurePerturber extends Operation {

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
    private String tag;

    /**
     * Structure file to use instead of PREM
     */
    private Path structurePath;
    /**
     * Structure to use
     */
    private String structureName;

    private double lowerRadius;
    private double upperRadius;
    private VariableType variable;
    private double percent;


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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) The first part of the name of output structure file (PREM)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#tag ");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##(double) Lower radius of layer to perturb (3480)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper radius of layer to perturb (3580)");
            pw.println("#upperRadius ");
            pw.println("##Variable to perturb, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA,Qmu,Qkappa} (Vs)");
            pw.println("#variable ");
            pw.println("##(double) Size of perturbation [%] (2)");
            pw.println("#percent ");
        }
        System.err.println(outPath + " is created.");
    }

    public PolynomialStructurePerturber(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "structure");
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        lowerRadius = property.parseDouble("lowerRadius", "3480");
        upperRadius = property.parseDouble("upperRadius", "3580");
        if (lowerRadius > upperRadius)
            throw new IllegalArgumentException("Radius range " + lowerRadius + " , " + upperRadius + " is invalid.");
        variable = VariableType.valueOf(property.parseString("variable", "Vs"));
        percent = property.parseDouble("percent", "2");
    }

   @Override
   public void run() throws IOException {
       // set structure to use
       PolynomialStructure structure = null;
       if (structurePath != null) {
           structure = PolynomialStructureFile.read(structurePath);
       } else {
           structure = PolynomialStructure.of(structureName);
       }

       structure = structure.withPerturbation(lowerRadius, upperRadius, variable, percent);

       Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName(nameRoot, tag, GadgetAid.getTemporaryString(), ".structure"));
       PolynomialStructureFile.write(structure, outputPath);
   }

}
