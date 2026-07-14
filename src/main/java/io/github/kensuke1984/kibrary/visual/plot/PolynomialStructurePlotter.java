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
 * @since 2022/8/30
 * @author otsuru
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

    private List<VariableType> variableTypes;

    private StructurePlotAid.Distinguisher structureDistinguisher;
    private StructurePlotAid.Distinguisher variableDistinguisher;

    private double lowerRadius;
    private double upperRadius;
    private double lowerValue;
    private double upperValue;

    /**
     * Structure file instead of PREM.
     */
    private Path[] structurePaths = new Path[MAX_INPUT];
    private String[] structureNames = new String[MAX_INPUT];
    private StructurePlotAid.Color[] structureColors = new StructurePlotAid.Color[MAX_INPUT];

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Variable types to plot, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (RHO Vpv Vph Vsv Vsh ETA)");
            pw.println("#variableTypes ");
            pw.println("##(boolean) How to distinguish structures, from {COLOR, SHADE, DASH, NONE}. (SHADE)");
            pw.println("#structureDistinguisher ");
            pw.println("##(boolean) How to distinguish variables, from {COLOR, SHADE, DASH, NONE}. (COLOR)");
            pw.println("#variableDistinguisher ");
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
                pw.println("##Color for this structure, from {RED, ORANGE, GREEN, BLUE, PURPLE, GRAY}, when specifying.");
                pw.println("#structureColor" + i + " ");
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
                .collect(Collectors.toList());

        structureDistinguisher = StructurePlotAid.Distinguisher.valueOf(property.parseString("structureDistinguisher", "SHADE"));
        variableDistinguisher = StructurePlotAid.Distinguisher.valueOf(property.parseString("variableDistinguisher", "COLOR"));

        lowerRadius = property.parseDouble("lowerRadius", "0");
        upperRadius = property.parseDouble("upperRadius", "6371");
        LinearRange.checkValidity("Radius", lowerRadius, upperRadius, 0.0);
        lowerValue = property.parseDouble("lowerValue", "0");
        upperValue = property.parseDouble("upperValue", "15");
        LinearRange.checkValidity("Value", lowerValue, upperValue);

        for (int i = 1; i <= MAX_INPUT; i++) {
            String pathKey = "structurePath" + i;
            String nameKey = "structureName" + i;
            String colorKey = "structureColor" + i;
            if (property.containsKey(pathKey)) {
                structurePaths[i - 1] = property.parsePath(pathKey, null, true, workPath);
            } else if (property.containsKey(nameKey)) {
                structureNames[i - 1] = property.parseString(nameKey, null);
            } else if (i == 1) {
                structureNames[0] = "PREM";
            }
            if (property.containsKey(colorKey)) {
                structureColors[i - 1] = StructurePlotAid.Color.valueOf(property.parseString(colorKey, null));
                // when a structure color is set, distinguish structures by color
                structureDistinguisher = StructurePlotAid.Distinguisher.COLOR;
            } else {
                structureColors[i - 1] = StructurePlotAid.Color.NONE;
            }
        }
    }

    @Override
    public void run() throws IOException {
        // set structures
        List<PolynomialStructure> structures = new ArrayList<>();
        List<StructurePlotAid.Color> colors = new ArrayList<>();
        for (int i = 0; i < MAX_INPUT; i++) {
            if (structurePaths[i] != null || structureNames[i] != null) {
                PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePaths[i], structureNames[i]);
                structures.add(structure);
                colors.add(structureColors[i]);
            }
        }

        // create script
        Path scriptPath = DatasetAid.generateOutputFilePath(workPath, "polynomial", fileTag, appendFileDate, null, ".plt");
        createScript(scriptPath, structures, colors);
    }

    private void createScript(Path scriptPath, List<PolynomialStructure> structures, List<StructurePlotAid.Color> colors) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);
        StructurePlotAid plotAid = new StructurePlotAid(structureDistinguisher, StructurePlotAid.Distinguisher.NONE, variableDistinguisher, variableTypes);
        plotAid.setColors(colors);

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
            pw.println("set xlabel font 'Helvetica,20'");
            pw.println("set ylabel font 'Helvetica,20'");
            pw.println("set tics font 'Helvetica,20'");
            pw.println("set key font 'Helvetica,20'");
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
            pw.print("p");
            for (int i = 0; i < structures.size(); i++) {
                for (VariableType variable : variableTypes) {
                    // show key for last structure
                    String titleString = (i == structures.size() - 1) ? ("title '" + StructurePlotAid.labelStringFor(variable) + "'") : "notitle";
                    pw.println("  " + variable.toString().toLowerCase() + i + "(t),t w l lw 2 " + plotAid.lineTypeFor(i, 0, variable, structures.size())
                            + " " + titleString + ", \\");
                }
            }

            pw.println("  0,t w l lw 1 dt 1 lc rgb 'black' notitle");
        }

        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
    }

}
