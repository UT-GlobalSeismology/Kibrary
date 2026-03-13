package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
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

    private double lowerTieInRadius;
    private double lowerRadius;
    private double upperRadius;
    private double upperTieInRadius;
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
            pw.println("##(double) Tie-in radius beneath layer to perturb; [0:lowerRadius]. (3480)");
            pw.println("#lowerTieInRadius ");
            pw.println("##(double) Lower radius of layer to perturb; [lowerTieInRadius:upperRadius]. (3480)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper radius of layer to perturb; [lowerRadius:upperTieInRadius]. (3580)");
            pw.println("#upperRadius ");
            pw.println("##(double) Tie-in radius above layer to perturb; [upperRadius:). (3680)");
            pw.println("#upperTieInRadius ");
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

        lowerTieInRadius = property.parseDouble("lowerTieInRadius", "3480");
        lowerRadius = property.parseDouble("lowerRadius", "3480");
        upperRadius = property.parseDouble("upperRadius", "3580");
        upperTieInRadius = property.parseDouble("upperTieInRadius", "3680");
        if (0.0 > lowerTieInRadius) throw new IllegalArgumentException("Does not satisfy 0 <= lowerTieInRadius.");
        if (lowerTieInRadius > lowerRadius) throw new IllegalArgumentException("Does not satisfy lowerTieInRadius <= lowerRadius.");
        if (lowerRadius > upperRadius) throw new IllegalArgumentException("Does not satisfy lowerRadius <= upperRadius.");
        if (upperRadius > upperTieInRadius) throw new IllegalArgumentException("Does not satisfy upperRadius <= upperTieInRadius.");

        variable = VariableType.valueOf(property.parseString("variable", "Vs"));
        percent = property.parseDouble("percent", "2");
    }

   @Override
   public void run() throws IOException {
       // set structure
       PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

       // list up each individual variable for Vp and Vs
       List<VariableType> variableList = transformVariable(variable);
       // decide factor
       double factor = 1.0 + percent / 100.0;

       if (lowerTieInRadius < lowerRadius) {
           if (variable == VariableType.Qkappa || variable == VariableType.Qmu) {
               throw new IllegalArgumentException("Qkappa and Qmu cannot be tied in.");

           } else {
               // get x values
               double x0 = structure.xFor(lowerTieInRadius);
               double x1 = structure.xFor(lowerRadius);
               // crete x matrix
               double[] row0 = {1.0,  x0,  x0 * x0,  x0 * x0 * x0};
               double[] row1 = {0.0, 1.0, 2.0 * x0, 3.0 * x0 * x0};
               double[] row2 = {1.0,  x1,  x1 * x1,  x1 * x1 * x1};
               double[] row3 = {0.0, 1.0, 2.0 * x1, 3.0 * x1 * x1};
               RealMatrix xMatrix = new Array2DRowRealMatrix(4, 4);
               xMatrix.setRow(0, row0);
               xMatrix.setRow(1, row1);
               xMatrix.setRow(2, row2);
               xMatrix.setRow(3, row3);
               // compute inverse
               RealMatrix inverseMatrix = MatrixUtils.inverse(xMatrix).transpose();

               // compute and set new function for each individual variable
               for (VariableType currentVariable : variableList) {
                   // get y values
                   double y0 = structure.getAtRadius(currentVariable, lowerTieInRadius);
                   double y0p = structure.getDerivativeAtRadius(currentVariable, lowerTieInRadius);
                   double y1 = structure.getAtRadius(currentVariable, lowerRadius) * factor;
                   double y1p = structure.getDerivativeAtRadius(currentVariable, lowerRadius) * factor;
                   // create y vector
                   double[] yArray = {y0, y0p, y1, y1p};
                   // compute coefficients
                   double[] coefArray = inverseMatrix.preMultiply(yArray);
                   coefArray = Arrays.stream(coefArray).map(v -> Precision.round(v, 4)).toArray();
                   // set new function
                   PolynomialFunction function = new PolynomialFunction(coefArray);
                   structure = structure.withFunction(lowerTieInRadius, lowerRadius, currentVariable, function);
               }
           }
       }

       if (lowerRadius < upperRadius) {
           structure = structure.withPerturbation(lowerRadius, upperRadius, variable, percent);
       }

       if (upperRadius < upperTieInRadius) {
           if (variable == VariableType.Qkappa || variable == VariableType.Qmu) {
               throw new IllegalArgumentException("Qkappa and Qmu cannot be tied in.");

           } else {
               // get x values
               double x0 = structure.xFor(upperRadius);
               double x1 = structure.xFor(upperTieInRadius);
               // crete x matrix
               double[] row0 = {1.0,  x0,  x0 * x0,  x0 * x0 * x0};
               double[] row1 = {0.0, 1.0, 2.0 * x0, 3.0 * x0 * x0};
               double[] row2 = {1.0,  x1,  x1 * x1,  x1 * x1 * x1};
               double[] row3 = {0.0, 1.0, 2.0 * x1, 3.0 * x1 * x1};
               RealMatrix xMatrix = new Array2DRowRealMatrix(4, 4);
               xMatrix.setRow(0, row0);
               xMatrix.setRow(1, row1);
               xMatrix.setRow(2, row2);
               xMatrix.setRow(3, row3);
               // compute inverse
               RealMatrix inverseMatrix = MatrixUtils.inverse(xMatrix).transpose();

               // compute and set new function for each individual variable
               for (VariableType currentVariable : variableList) {
                   // get y values
                   double y0 = structure.getAtRadius(currentVariable, upperRadius) * factor;
                   double y0p = structure.getDerivativeAtRadius(currentVariable, upperRadius) * factor;
                   double y1 = structure.getAtRadius(currentVariable, upperTieInRadius);
                   double y1p = structure.getDerivativeAtRadius(currentVariable, upperTieInRadius);
                   // create y vector
                   double[] yArray = {y0, y0p, y1, y1p};
                   // compute coefficients
                   double[] coefArray = inverseMatrix.preMultiply(yArray);
                   coefArray = Arrays.stream(coefArray).map(v -> Precision.round(v, 4)).toArray();
                   // set new function
                   PolynomialFunction function = new PolynomialFunction(coefArray);
                   structure = structure.withFunction(upperRadius, upperTieInRadius, currentVariable, function);
               }
           }
       }

       Path outputPath = DatasetAid.generateOutputFilePath(workPath, nameRoot, fileTag, appendFileDate, null, ".structure");
       PolynomialStructureFile.write(structure, outputPath);
   }

   private List<VariableType> transformVariable(VariableType variable) {
       List<VariableType> variableList = new ArrayList<>();
       switch (variable) {
       case Vp:
           variableList.add(VariableType.Vpv);
           variableList.add(VariableType.Vph);
           break;
       case Vs:
           variableList.add(VariableType.Vsv);
           variableList.add(VariableType.Vsh);
           break;
       default:
           variableList.add(variable);
       }
       return variableList;
   }

}
