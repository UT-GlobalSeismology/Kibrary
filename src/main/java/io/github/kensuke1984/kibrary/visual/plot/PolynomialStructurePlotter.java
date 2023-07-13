package io.github.kensuke1984.kibrary.visual.plot;

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

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * Operation that plots polynomial structures.
 *
 * @author otsuru
 * @since 2022/8/30
 */
public class PolynomialStructurePlotter extends Operation {

    private static final int MAX_INPUT = 3;
    private static final int NUM_VARIABLES = 6;
    private static final String[] COLORS = {
            "dark-magenta", "web-green", "dark-orange", "web-blue", "red", "dark-gray",
            "purple", "greenyellow", "goldenrod", "skyblue", "salmon", "gray",
            "plum", "seagreen", "khaki", "light-cyan", "light-pink", "light-gray"};

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;

    private boolean colorByStructure;
    private boolean colorByVariable;
    private boolean dashByStructure;
    private boolean dashByVariable;
    /**
     * structure file instead of PREM
     */
    private Path[] structurePaths = new Path[MAX_INPUT];
    private String[] structureNames = new String[MAX_INPUT];


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
            pw.println("##(boolean) Whether to color structures differently. (true)");
            pw.println("#colorByStructure ");
            pw.println("##(boolean) Whether to color variables differently. (true)");
            pw.println("#colorByVariable ");
            pw.println("##(boolean) Whether to dash structures differently. (false)");
            pw.println("#dashByStructure ");
            pw.println("##(boolean) Whether to dash variables differently. (false)");
            pw.println("#dashByVariable ");
            pw.println("##########From here on, list up models to plot.");
            pw.println("########## Up to " + MAX_INPUT + " models can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " model");
                pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
                pw.println("#structurePath" + i + " ");
                if (i == 1) pw.println("##Name of a structure model you want to use. (PREM)");
                else pw.println("##Name of a structure model you want to use.");
                pw.println("#structureName" + i + " ");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public PolynomialStructurePlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        colorByStructure = property.parseBoolean("colorByStructure", "true");
        colorByVariable = property.parseBoolean("colorByVariable", "true");
        dashByStructure = property.parseBoolean("dashByStructure", "false");
        dashByVariable = property.parseBoolean("dashByVariable", "false");

        for (int i = 1; i <= MAX_INPUT; i++) {
            String pathKey = "structurePath" + i;
            if (property.containsKey(pathKey)) {
                structurePaths[i - 1] = property.parsePath(pathKey, null, true, workPath);
                continue;
            }
            String nameKey = "structureName" + i;
            if (property.containsKey(nameKey)) {
                structureNames[i - 1] = property.parseString(nameKey, null);
            } else if (i == 1) {
                structureNames[0] = "PREM";
            }
        }

    }

   @Override
   public void run() throws IOException {
       // set structure
       List<PolynomialStructure> structures = new ArrayList<>();
       for (int i = 0; i < MAX_INPUT; i++) {
           if (structurePaths[i] != null || structureNames[i] != null) {
               PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePaths[i], structureNames[i]);
               structures.add(structure);
           }
       }
       createScript(workPath, "polynomial", structures);
   }

   private void createScript(Path outPath, String fileNameRoot, List<PolynomialStructure> structures) throws IOException {
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

           // define functions
           for (int i = 0; i < structures.size(); i++) {
               PolynomialStructure structure = structures.get(i);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getRho(), "rho" + i, pw);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVpv(), "vpv" + i, pw);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVph(), "vph" + i, pw);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsv(), "vsv" + i, pw);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsh(), "vsh" + i, pw);
               writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getEta(), "eta" + i, pw);
           }

           pw.println("");

           // plot the defined functions
           int i = 0;
           pw.println("p rho" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 0) + " title '{/Symbol r}', \\");
           pw.println("  vpv" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 1) + " title 'Vpv', \\");
           pw.println("  vph" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 2) + " title 'Vph', \\");
           pw.println("  vsv" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 3) + " title 'Vsv', \\");
           pw.println("  vsh" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 4) + " title 'Vsh', \\");
           pw.print("  eta" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 5) + " title '{/Symbol h}'");
           for (i = 1; i < structures.size(); i++) {
               pw.println(", \\");
               pw.println("  rho" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 0) + " notitle, \\");
               pw.println("  vpv" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 1) + " notitle, \\");
               pw.println("  vph" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 2) + " notitle, \\");
               pw.println("  vsv" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 3) + " notitle, \\");
               pw.println("  vsh" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 4) + " notitle, \\");
               pw.print("  eta" + i + "(t),t w l lw 0.5 " + lineTypeFor(i, 5) + " notitle");
           }
           pw.println("");
       }

       GnuplotFile plot = new GnuplotFile(scriptPath);
       plot.execute();
   }

   private void writeFunction(double[] rmin, double[] rmax, double planetRadius, PolynomialFunction[] functions, String funcName, PrintWriter pw) throws IOException {
       // each layer
       for (int i = 0; i < functions.length; i++) {
           double[] coeffs = Arrays.copyOf(functions[i].getCoefficients(), 4);
           pw.print(funcName + "_" + i + "(x) = (x<" + rmin[i] + ") ? 0 : (x<" + rmax[i] + ") ? " + coeffs[0]);
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
           pw.print(funcName + "_" + i + "(x)");
       }
       pw.println();
   }

   private String lineTypeFor(int iStructure, int iVariable) {
       int iColor;
       if (colorByStructure && colorByVariable) iColor = iStructure * NUM_VARIABLES + iVariable;
       else if (colorByStructure) iColor = iStructure;
       else if (colorByVariable) iColor = iVariable;
       else iColor = 0;

       int iDash;
       if (dashByStructure && dashByVariable) iDash = iStructure * NUM_VARIABLES + iVariable + 1;
       else if (dashByStructure) iDash = iStructure + 1;
       else if (dashByVariable) iDash = iVariable + 1;
       else iDash = 1;

       String lineTypeString = "dt " + iDash + " lc rgb '" + COLORS[iColor] + "'";
       return lineTypeString;
   }

}
