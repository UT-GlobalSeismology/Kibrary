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
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * Operation that plots {@link PolynomialStructure}s.
 * <p>
 * 6 variables (RHO, Vpv, Vph, Vsv, Vsh, ETA) can be plotted on a single graph.
 * Multiple {@link PolynomialStructure}s can be overlaid on the same graph.
 *
 * @author otsuru
 * @since 2022/8/30
 */
public class PolynomialStructurePlotter extends Operation {

    private static final int MAX_INPUT = 6;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    private Set<VariableType> variableTypes;

    private boolean colorByStructure;
    private boolean colorByVariable;
    private boolean dashByStructure;
    private boolean dashByVariable;

    private double lowerRadius;
    private double upperRadius;
    private double lowerValue;
    private double upperValue;

    /**
     * Structure file instead of PREM.
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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Variable types to map, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (RHO Vpv Vph Vsv Vsh ETA)");
            pw.println("#variableTypes ");
            pw.println("##(boolean) Whether to color structures differently. (true)");
            pw.println("#colorByStructure ");
            pw.println("##(boolean) Whether to color variables differently. (true)");
            pw.println("#colorByVariable ");
            pw.println("##(boolean) Whether to dash structures differently. (false)");
            pw.println("#dashByStructure ");
            pw.println("##(boolean) Whether to dash variables differently. (false)");
            pw.println("#dashByVariable ");
            pw.println("##(double) Lower limit of radius [km]; [0:upperRadius). (0)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper limit of radius [km]; (lowerRadius:). (6371)");
            pw.println("#upperRadius ");
            pw.println("##(double) Lower limit of value; (:upperValue). (0)");
            pw.println("#lowerValue ");
            pw.println("##(double) Upper limit of value; (lowerValue:). (15)");
            pw.println("#upperValue ");
            pw.println("##########From here on, list up models to plot.");
            pw.println("########## Up to " + MAX_INPUT + " models can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " model.");
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
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "RHO Vpv Vph Vsv Vsh ETA")).map(VariableType::valueOf)
                .collect(Collectors.toSet());

        colorByStructure = property.parseBoolean("colorByStructure", "true");
        colorByVariable = property.parseBoolean("colorByVariable", "true");
        dashByStructure = property.parseBoolean("dashByStructure", "false");
        dashByVariable = property.parseBoolean("dashByVariable", "false");

        lowerRadius = property.parseDouble("lowerRadius", "0");
        upperRadius = property.parseDouble("upperRadius", "6371");
        LinearRange.checkValidity("Radius", lowerRadius, upperRadius, 0.0);
        lowerValue = property.parseDouble("lowerValue", "0");
        upperValue = property.parseDouble("upperValue", "15");
        LinearRange.checkValidity("Value", lowerValue, upperValue);

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
       // set structures
       // Structures existing in the input properties file are set in reverse order.
       List<PolynomialStructure> structures = new ArrayList<>();
       for (int i = MAX_INPUT - 1; i >= 0; i--) {
           if (structurePaths[i] != null || structureNames[i] != null) {
               PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePaths[i], structureNames[i]);
               structures.add(structure);
           }
       }

       // create script
       Path scriptPath = DatasetAid.generateOutputFilePath(workPath, "polynomial", fileTag, appendFileDate, null, ".plt");
       createScript(scriptPath, structures);
   }

   private void createScript(Path scriptPath, List<PolynomialStructure> structures) throws IOException {
       String fileNameRoot = FileAid.extractNameRoot(scriptPath);
       StructurePlotAid plotAid = new StructurePlotAid(colorByStructure, colorByVariable, dashByStructure, dashByVariable);

       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
           pw.println("set samples 1000");
           pw.println("set trange [" + lowerRadius + ":" + upperRadius + "]");
           pw.println("set yrange [" + lowerRadius + ":" + upperRadius + "]");
           pw.println("set xrange [" + lowerValue + ":" + upperValue + "]");
           pw.println("#set ytics 1000");
           pw.println("#set xtics 2");
           pw.println("set xlabel \"Velocity (km/s)\\nDensity (g/cm^3)\"");
           pw.println("set ylabel 'Radius (km)'");
           pw.println("set parametric");
           pw.println("set term pngcairo enhanced size 600,1200 font 'Helvetica,20'");
           pw.println("set output '" + fileNameRoot + ".png'");
           pw.println("set xlabel font 'Helvetica,20");
           pw.println("set ylabel font 'Helvetica,20");
           pw.println("set tics font 'Helvetica,20");
           pw.println("set key font 'Helvetica,20");
           pw.println("set key samplen 1");
           pw.println("");

           // define functions
           for (int i = 0; i < structures.size(); i++) {
               // define functions
               PolynomialStructure structure = structures.get(i);
               for (VariableType variable : variableTypes) {
                   StructurePlotAid.defineFunction(variable, structure, i, pw);
               }
           }
           pw.println("");

           // plot the defined functions
           int i = 0;
           pw.print("p");
           for (VariableType variable : variableTypes) {
               pw.println("  " + variable.toString().toLowerCase() + i + "(t),t w l lw 1 " + plotAid.lineTypeFor(i, variable)
                       + " title '" + StructurePlotAid.labelStringFor(variable) + "', \\");
           }
           for (i = 1; i < structures.size(); i++) {
               for (VariableType variable : variableTypes) {
                   pw.println("  " + variable.toString().toLowerCase() + i + "(t),t w l lw 1 " + plotAid.lineTypeFor(i, variable)
                           + " notitle, \\");
               }
           }

           pw.println("  0,t w l lw 0.5 dt 1 lc rgb 'black' notitle");
       }

       GnuplotFile plot = new GnuplotFile(scriptPath);
       plot.execute();
   }

}
