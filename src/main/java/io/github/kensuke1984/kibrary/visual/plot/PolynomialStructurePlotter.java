package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;

/**
 * Operation that plots polynomial structures.
 *
 * @author otsuru
 * @since 2022/8/30
 */
public class PolynomialStructurePlotter extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;

    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;


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
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
        }
        System.err.println(outPath + " is created.");
    }

    public PolynomialStructurePlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

    }

   @Override
   public void run() throws IOException {
       // set structure
       PolynomialStructure structure = null;
       String fileNameRoot = null;
       if (structurePath != null) {
           structure = PolynomialStructureFile.read(structurePath);
           fileNameRoot = FileAid.extractNameRoot(structurePath);
       } else {
           structure = PolynomialStructure.of(structureName);
           fileNameRoot = structureName;
       }

       createScript(workPath, fileNameRoot, structure);
   }

   private void createScript(Path outPath, String fileNameRoot, PolynomialStructure structure) throws IOException {
       Path scriptPath = outPath.resolve(fileNameRoot + ".plt");

       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
           pw.println("set samples 1000");
           pw.println("set trange [0:6371]");
           pw.println("set yrange [0:6371]");
           pw.println("set xrange [3.5:15]");
           pw.println("set ytics 1000");
           pw.println("set xtics 2");
           pw.println("set mytics 2");
           pw.println("set mxtics 2");
           pw.println("set size ratio 2.0");
           pw.println("set xlabel \"Velocity (km/s)\\nDensity (g/cm^3)\"");
           pw.println("set ylabel 'Radius (km)'");
           pw.println("set parametric");
           pw.println("set term pngcairo enhanced font 'Helvetica,10'");
           pw.println("set output '" + fileNameRoot + ".png'");
           pw.println("set xlabel font 'Helvetica,10");
           pw.println("set ylabel font 'Helvetica,10");
           pw.println("set tics font 'Helvetica,10");
           pw.println("set key font 'Helvetica,10");
           pw.println("set key samplen 1");
           pw.println("");

           writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getRho(), "f", pw);
           writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVpv(), "g", pw);
           writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsv(), "h", pw);
           writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsh(), "k", pw);

           pw.println("");
           pw.println("p f(t),t w l lw 0.5 lc rgb 'green' title '{/Symbol r}', \\");
           pw.println("  g(t),t w l lw 0.5 lc rgb 'blue' title 'Vp', \\");
           pw.println("  h(t),t w l lw 0.5 lc rgb 'red' title 'Vsv', \\");
           pw.println("  k(t),t w l lw 0.5 lc rgb 'black' title 'Vsh'");
       }

       GnuplotFile plot = new GnuplotFile(scriptPath);
       plot.execute();
   }

   private void writeFunction(double[] rmin, double[] rmax, double planetRadius, PolynomialFunction[] functions, String funcName, PrintWriter pw) throws IOException {
       // each layer
       for (int i = 0; i < functions.length; i++) {
           double[] coeffs = Arrays.copyOf(functions[i].getCoefficients(), 4);
           pw.print(funcName + i + "(x) = (x<" + rmin[i] + ") ? 0 : (x<" + rmax[i] + ") ? " + coeffs[0]);
           for (int j = 1; j < 4; j++) {
               pw.print("+(" + coeffs[j] + ")*(x/" + planetRadius + ")");
               if (j > 1) pw.print("**" + j);
           }
           pw.println(" : 0");
       }

       // sum
       pw.print(funcName + "(x)=");
       for (int i = 0; i < functions.length; i++) {
           if (i > 0) pw.print("+");
           pw.print(funcName + i + "(x)");
       }
       pw.println();
   }

}
