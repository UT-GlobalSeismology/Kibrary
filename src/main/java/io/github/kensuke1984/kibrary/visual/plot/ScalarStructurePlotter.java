package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.apache.commons.math3.util.Precision;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * Plot 1D profiles from {@link ScalarListFile}, using its its reference {@link PolynomialStructure}.
 *
 * @author otsuru
 * @since 2024/11/6
 */
public class ScalarStructurePlotter extends Operation {

    /**
     * Margin in radius direction (y-axis).
     */
    private static final double MARGIN_RAD = 50;
    /**
     * Margin in value direction (x-axis).
     */
    private static final double MARGIN_VAL = 0.2;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    /**
     * Path of scalar file.
     */
    private Path scalarPath;
    /**
     * File of 1D structure used in inversion.
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion.
     */
    private String initialStructureName;

    /**
     * Name of model to plot.
     */
    private String modelName;

    private StructurePlotAid.Distinguisher structureDistinguisher;

    private boolean setLowerRadius = false;
    private double lowerRadius;
    private boolean setUpperRadius = false;
    private double upperRadius;
    private boolean setLowerValue = false;
    private double lowerValue;
    private boolean setUpperValue = false;
    private double upperValue;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of scalar file, must be set.");
            pw.println("#scalarPath scalar.Vs.PERCENT.lst");
            pw.println("##Name of model to plot, must be set.");
            pw.println("#modelName ");
            pw.println("##Path of an initial structure file. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##(boolean) How to distinguish structures, from {COLOR, SHADE, DASH, NONE}. (COLOR)");
            pw.println("#structureDistinguisher ");
            pw.println("##(double) Lower limit of radius [km], when setting manually; [0:upperRadius).");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper limit of radius [km], when setting manually; (lowerRadius:).");
            pw.println("#upperRadius ");
            pw.println("##(double) Lower limit of value, when setting manually; (:upperValue).");
            pw.println("#lowerValue ");
            pw.println("##(double) Upper limit of value, when setting manually; (lowerValue:).");
            pw.println("#upperValue ");
        }
        System.err.println(outPath + " is created.");
    }

    public ScalarStructurePlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        scalarPath = property.parsePath("scalarPath", null, true, workPath);
        modelName = property.parseString("modelName", null);
        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }

        structureDistinguisher = StructurePlotAid.Distinguisher.valueOf(property.parseString("structureDistinguisher", "COLOR"));

        if (property.containsKey("lowerRadius")) {
            lowerRadius = property.parseDouble("lowerRadius", null);
            setLowerRadius = true;
            if (lowerRadius < 0)
                throw new IllegalArgumentException("Lower radius " + lowerRadius + " is invalid; must be positive.");
        }
        if (property.containsKey("upperRadius")) {
            upperRadius = property.parseDouble("upperRadius", null);
            setUpperRadius = true;
            if (upperRadius < 0)
                throw new IllegalArgumentException("Upper radius " + upperRadius + " is invalid; must be positive.");
        }
        if (setLowerRadius && setUpperRadius) LinearRange.checkValidity("Radius", lowerRadius, upperRadius);
        if (property.containsKey("lowerValue")) {
            lowerValue = property.parseDouble("lowerValue", null);
            setLowerValue = true;
        }
        if (property.containsKey("upperValue")) {
            upperValue = property.parseDouble("upperValue", null);
            setUpperValue = true;
        }
        if (setLowerValue && setUpperValue) LinearRange.checkValidity("Value", lowerValue, upperValue);
    }

    @Override
    public void run() throws IOException {
        // read initial structure
        System.err.print("Initial structure: ");
        PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

        // read input file
        ScalarListFile inputFile = new ScalarListFile(scalarPath);
        VariableType variable = inputFile.getVariable();
        ScalarType scalarType = inputFile.getScalarType();
        Map<FullPosition, Double> discreteMap = inputFile.getValueMap();

        // set up model
        // Here, voxel size is set to 0.0 because it will not be referenced anywhere.
        PerturbationModel model = new PerturbationModel(variable, scalarType, 0.0, discreteMap, initialStructure);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "scalarPlot", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // instance to decide plot range for this model
        PlotRange modelPlotRange = new PlotRange();

        // output discrete perturbation file
        String variableName = variable.toString().toLowerCase();
        Map<FullPosition, Double> absoluteMap = model.getValueMap(variable, ScalarType.ABSOLUTE);
        Path outputAbsolutePath = outPath.resolve(variableName + "Absolute.lst");
        ScalarListFile.write(absoluteMap, outputAbsolutePath);
        // update plot range based on these values
        modelPlotRange.update(absoluteMap);

        // create gnuplot script
        Path outputScriptPath = outPath.resolve("modelPlot.plt");
        createScript(outputScriptPath, variable, initialStructure, modelPlotRange);
    }

    private void createScript(Path scriptPath, VariableType variable, PolynomialStructure structure, PlotRange plotRange) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);
        StructurePlotAid plotAid = new StructurePlotAid(structureDistinguisher, StructurePlotAid.Distinguisher.NONE, StructurePlotAid.Distinguisher.NONE, null);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set samples 1000");
            pw.println("set trange [0:6371]");
            pw.println("set yrange [" + plotRange.lowerRadius() + ":" + plotRange.upperRadius() + "]");
            pw.println("set xrange [" + plotRange.lowerValue() + ":" + plotRange.upperValue() + "]");
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

            // define function
            StructurePlotAid.defineFunction(variable, structure, 0, pw);
            pw.println("");

            // plot the defined function
            pw.print("p");
            pw.println("  " + variable.toString().toLowerCase() + "0(t),t w l lw 2 " + plotAid.lineTypeFor(0, 0, null, 2) + " title 'initial', \\");

            // plot models
            pw.println("  \"" + variable.toString().toLowerCase() + "Absolute.lst\" u 4:3 w l lw 2 "
                    + plotAid.lineTypeFor(1, 0, null, 2) + " title '" + modelName + "', \\");

            pw.println("  0,t w l lw 1 dt 1 lc rgb 'black' notitle");
        }

        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
    }

    private class PlotRange {
        private boolean first = true;
        private double currentMinRadius;
        private double currentMaxRadius;
        private double currentMinValue;
        private double currentMaxValue;

        private PlotRange() {}

        private void update(Map<FullPosition, Double> discreteMap) {
            if (setLowerRadius == false) {
                double minRadius = discreteMap.keySet().stream().mapToDouble(pos -> pos.getR()).min().getAsDouble();
                if (first || minRadius < currentMinRadius) currentMinRadius = minRadius;

            }
            if (setUpperRadius == false) {
                double maxRadius = discreteMap.keySet().stream().mapToDouble(pos -> pos.getR()).max().getAsDouble();
                if (first || maxRadius > currentMaxRadius) currentMaxRadius = maxRadius;
            }
            if (setLowerValue == false) {
                double minValue = discreteMap.values().stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                if (first || minValue < currentMinValue) currentMinValue = minValue;

            }
            if (setUpperValue == false) {
                double maxValue = discreteMap.values().stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                if (first || maxValue > currentMaxValue) currentMaxValue = maxValue;
            }
            first = false;
        }

        private double lowerRadius() {
            return setLowerRadius ? lowerRadius : currentMinRadius - MARGIN_RAD;
        }
        private double upperRadius() {
            return setUpperRadius ? upperRadius : currentMaxRadius + MARGIN_RAD;
        }
        private double lowerValue() {
            return setLowerValue ? lowerValue : Precision.round(currentMinValue - MARGIN_VAL, 2);
        }
        private double upperValue() {
            return setUpperValue ? upperValue : Precision.round(currentMaxValue + MARGIN_VAL, 2);
        }
    }

}
