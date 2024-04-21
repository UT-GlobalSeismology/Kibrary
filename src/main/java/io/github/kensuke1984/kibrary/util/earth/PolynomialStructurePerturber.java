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
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;

/**
 * Operation that adds perturbations to a {@link PolynomialStructure}.
 * <p>
 * Perturbations can be added to a certain variable within a specified radius range.
 *
 * @author otsuru
 * @since 2022/8/25
 */
public class PolynomialStructurePerturber extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * The first part of the name of output structure file.
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * Structure file to use.
     */
    private Path structurePath;
    /**
     * Structure to use.
     */
    private String structureName;

    private double lowerRadius;
    private double upperRadius;
    private VariableType variable;
    private double percent;

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
            pw.println("##(String) The first part of the name of output structure file. (PREM)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use. (PREM)");
            pw.println("#structureName ");
            pw.println("##(double) Lower radius of layer to perturb; [0:upperRadius). (3480)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper radius of layer to perturb; (lowerRadius:). (3580)");
            pw.println("#upperRadius ");
            pw.println("##Variable to perturb, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA,Qmu,Qkappa}. (Vs)");
            pw.println("#variable ");
            pw.println("##(double) Size of perturbation [%]. (2)");
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
        nameRoot = property.parseStringSingle("nameRoot", "PREM");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        lowerRadius = property.parseDouble("lowerRadius", "3480");
        upperRadius = property.parseDouble("upperRadius", "3580");
        LinearRange.checkValidity("Radius", lowerRadius, upperRadius, 0.0);

        variable = VariableType.valueOf(property.parseString("variable", "Vs"));
        percent = property.parseDouble("percent", "2");
    }

   @Override
   public void run() throws IOException {
       // set structure
       PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

       structure = structure.withPerturbation(lowerRadius, upperRadius, variable, percent);

       Path outputPath = DatasetAid.generateOutputFilePath(workPath, nameRoot, fileTag, appendFileDate, null, ".structure");
       PolynomialStructureFile.write(structure, outputPath);
   }

}
