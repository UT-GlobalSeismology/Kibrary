package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

/**
 * Operation that plots the 1-D models obtained from the inversion, along with its reference {@link PolynomialStructure}.
 * <p>
 * The models should be provided as {@link KnownParameterFile}s with path 'resultPath/method/{method}{vectorNum}.lst'.
 * 8 types of variables (RHO, Vp, Vpv, Vph, Vs, Vsv, Vsh, ETA) can be plotted.
 *
 * @author otsuru
 * @since 2023/7/12
 */
public class ModelStructurePlotter extends Operation {

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
     * The root folder containing results of inversion.
     */
    private Path resultPath;
    /**
     * File of 1D structure used in inversion.
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion.
     */
    private String initialStructureName;

    /**
     * Variable types to plot.
     */
    private Set<VariableType> variableTypes;
    /**
     * Solvers for equation.
     */
    private Set<InverseMethodEnum> inverseMethods;
    private List<String> indexStrings;
    private int maxNum;

    private boolean colorByStructure;
    private boolean colorByVariable;
    private boolean dashByStructure;
    private boolean dashByVariable;

    private boolean setLowerRadius = false;
    private double lowerRadius;
    private boolean setUpperRadius = false;
    private double upperRadius;
    private boolean setLowerValue = false;
    private double lowerValue;
    private boolean setUpperValue = false;
    private double upperValue;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of a root folder containing results of inversion. (.)");
            pw.println("#resultPath ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Variable types to plot, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#variableTypes ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LS,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
            pw.println("##Indices of result models to map. If this is unset, the following maxNum will be referenced.");
            pw.println("#indexStrings ");
            pw.println("##(int) Maximum number of basis vectors to map. (10)");
            pw.println("#maxNum ");
            pw.println("##(boolean) Whether to color structures differently. (true)");
            pw.println("#colorByStructure ");
            pw.println("##(boolean) Whether to color variables differently. (true)");
            pw.println("#colorByVariable ");
            pw.println("##(boolean) Whether to dash structures differently. (false)");
            pw.println("#dashByStructure ");
            pw.println("##(boolean) Whether to dash variables differently. (false)");
            pw.println("#dashByVariable ");
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

    public ModelStructurePlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        resultPath = property.parsePath("resultPath", ".", true, workPath);
        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        if (property.containsKey("indexStrings")) {
            indexStrings = Arrays.stream(property.parseStringArray("indexStrings", null)).collect(Collectors.toList());
        } else {
            int maxNum = property.parseInt("maxNum", "10");
            indexStrings = IntStream.range(1, maxNum).mapToObj(String::valueOf).collect(Collectors.toList());
        }

        colorByStructure = property.parseBoolean("colorByStructure", "true");
        colorByVariable = property.parseBoolean("colorByVariable", "true");
        dashByStructure = property.parseBoolean("dashByStructure", "false");
        dashByVariable = property.parseBoolean("dashByVariable", "false");

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

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "modelPlots", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // instance to decide plot ranges for each variable
        Map<VariableType, PlotRange> variablePlotRanges = new HashMap<>();
        for (VariableType variable : variableTypes) variablePlotRanges.put(variable, new PlotRange());

        //~write list files
        // loop for each inversion method
        for (InverseMethodEnum method : inverseMethods) {
            Path methodPath = resultPath.resolve(method.simpleName());
            if (!Files.exists(methodPath)) {
                System.err.println("!! Results for " + method.simpleName() + " do not exist, skipping.");
                continue;
            }

            // loop for each vector
            for (String indexString : indexStrings){
                Path answerPath = methodPath.resolve(method.simpleName() + indexString + ".lst");
                if (!Files.exists(answerPath)) {
                    System.err.println("!! Results for " + method.simpleName() + indexString + " do not exist, skipping.");
                    continue;
                }

                // read model
                List<KnownParameter> knowns = KnownParameterFile.read(answerPath);
                PerturbationModel model = new PerturbationModel(knowns, initialStructure);

                // create output folder for this model
                Path outBasisPath = outPath.resolve(method.simpleName() + indexString);
                Files.createDirectories(outBasisPath);

                // instance to decide plot range for this model
                PlotRange modelPlotRange = new PlotRange();

                // compute values of the model for each variable type
                for (VariableType variable : variableTypes) {
                    String variableName = variable.toString().toLowerCase();
                    // output discrete perturbation file
                    Map<FullPosition, Double> discreteMap = model.getAbsoluteForType(variable);
                    Path outputDiscretePath = outBasisPath.resolve(variableName + "Absolute.lst");
                    PerturbationListFile.write(discreteMap, outputDiscretePath);
                    // update plot range based on these values
                    modelPlotRange.update(discreteMap);
                    variablePlotRanges.get(variable).update(discreteMap);
                }

                // create gnuplot script
                Path outputScriptPath = outBasisPath.resolve("modelPlot.plt");
                createModelScript(outputScriptPath, initialStructure, modelPlotRange);
            }
        }

        // loop for each variable
        for (VariableType variable : variableTypes) {

            // create output folder for this variable
            Path outBasisPath = outPath.resolve(variable.toString());
            Files.createDirectories(outBasisPath);

            // create gnuplot script
            Path outputScriptPath = outBasisPath.resolve("modelPlot.plt");
            createVariableScript(outputScriptPath, variable, initialStructure, variablePlotRanges.get(variable));
        }
    }

    private void createModelScript(Path scriptPath, PolynomialStructure structure, PlotRange plotRange) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);
        StructurePlotAid plotAid = new StructurePlotAid(colorByStructure, colorByVariable, dashByStructure, dashByVariable);

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

            // define functions
            for (VariableType variable : variableTypes) {
                StructurePlotAid.defineFunction(variable, structure, 0, pw);
            }
            pw.println("");

            // plot the defined functions
            pw.print("p");
            for (VariableType variable : variableTypes) {
                pw.println("  " + variable.toString().toLowerCase() + "0(t),t w l lw 1 " + plotAid.lineTypeFor(1, variable)
                        + " title '" + StructurePlotAid.labelStringFor(variable) + "', \\");
            }

            // plot model
            for (VariableType variable : variableTypes) {
                pw.println("  \"" + variable.toString().toLowerCase() + "Absolute.lst\" u 4:3 w l lw 1 " + plotAid.lineTypeFor(0, variable)
                        + " notitle, \\");
            }

            pw.println("  0,t w l lw 0.5 dt 1 lc rgb 'black' notitle");
        }

        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
    }

    private void createVariableScript(Path scriptPath, VariableType variable, PolynomialStructure structure, PlotRange plotRange) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);
        StructurePlotAid plotAid = new StructurePlotAid(colorByStructure, colorByVariable, dashByStructure, dashByVariable);

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
            pw.println("  " + variable.toString().toLowerCase() + "0(t),t w l lw 1 " + plotAid.lineTypeFor(1, variable) + " title 'initial', \\");

            // plot models
            for (InverseMethodEnum method : inverseMethods) {
                for (String indexString : indexStrings){
                    String modelName = method.simpleName() + indexString;
                    pw.println("  \"../" + modelName + "/" + variable.toString().toLowerCase() + "Absolute.lst\" u 4:3 w l lw 1 "
                            + plotAid.lineTypeFor(0, variable) + " title '" + modelName + "', \\");
                }
            }

            pw.println("  0,t w l lw 0.5 dt 1 lc rgb 'black' notitle");
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
