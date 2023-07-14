package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

public class ModelStructurePlotter extends Operation {

    private static final int NUM_VARIABLES = 6;
    private static final String[] COLORS = {
            "dark-magenta", "dark-orange", "web-green", "red", "web-blue", "dark-gray",
            "purple", "goldenrod", "greenyellow", "salmon", "skyblue", "gray",
            "plum", "khaki", "seagreen", "light-pink", "light-cyan", "light-gray"};

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;

    /**
     * The root folder containing results of inversion
     */
    private Path resultPath;
    /**
     * file of 1D structure used in inversion
     */
    private Path initialStructurePath;
    /**
     * name of 1D structure used in inversion
     */
    private String initialStructureName;

    private Set<VariableType> variableTypes;
    /**
     * Solvers for equation
     */
    private Set<InverseMethodEnum> inverseMethods;
    private int maxNum;

    private boolean colorByStructure;
    private boolean colorByVariable;
    private boolean dashByStructure;
    private boolean dashByVariable;

    private double lowerRadius;
    private double upperRadius;
    private double lowerValue;
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
            pw.println("##Path of a root folder containing results of inversion. (.)");
            pw.println("#resultPath ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Variable types to plot, listed using spaces. (Vs)");
            pw.println("#variableTypes ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
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
            pw.println("##(double) Lower limit of radius [km]; [0:upperRadius). (0)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper limit of radius [km]; (lowerRadius:). (6371)");
            pw.println("#upperRadius ");
            pw.println("##(double) Lower limit of value; (:upperValue). (0)");
            pw.println("#lowerValue ");
            pw.println("##(double) Upper limit of value; (lowerValue:). (15)");
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
        maxNum = property.parseInt("maxNum", "10");

        colorByStructure = property.parseBoolean("colorByStructure", "true");
        colorByVariable = property.parseBoolean("colorByVariable", "true");
        dashByStructure = property.parseBoolean("dashByStructure", "false");
        dashByVariable = property.parseBoolean("dashByVariable", "false");

        lowerRadius = property.parseDouble("lowerRadius", "0");
        upperRadius = property.parseDouble("upperRadius", "6371");
        lowerValue = property.parseDouble("lowerValue", "0");
        upperValue = property.parseDouble("upperValue", "15");
    }

    @Override
    public void run() throws IOException {

        // read initial structure
        System.err.print("Initial structure: ");
        PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "modelPlots", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // write list files
        for (InverseMethodEnum method : inverseMethods) {
            Path methodPath = resultPath.resolve(method.simpleName());
            if (!Files.exists(methodPath)) {
                System.err.println("Results for " + method.simpleName() + " do not exist, skipping.");
                continue;
            }

            for (int k = 1; k <= maxNum; k++){
                Path answerPath = methodPath.resolve(method.simpleName() + k + ".lst");
                if (!Files.exists(answerPath)) {
                    System.err.println("Results for " + method.simpleName() + k + " do not exist, skipping.");
                    continue;
                }
                List<KnownParameter> knowns = KnownParameterFile.read(answerPath);
                PerturbationModel model = new PerturbationModel(knowns, initialStructure);

                Path outBasisPath = outPath.resolve(method.simpleName() + k);
                Files.createDirectories(outBasisPath);

                for (VariableType variable : variableTypes) {
                    String variableName = variable.toString().toLowerCase();
                    // output discrete perturbation file
                    Map<FullPosition, Double> discreteMap = model.getAbsoluteForType(variable);
                    Path outputDiscretePath = outBasisPath.resolve(variableName + "Absolute.lst");
                    PerturbationListFile.write(discreteMap, outputDiscretePath);
                }

                Path outputScriptPath = outBasisPath.resolve("modelPlot.plt");
                createScript(outputScriptPath, initialStructure);
            }
        }

    }

    private void createScript(Path scriptPath, PolynomialStructure structure) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set samples 1000");
            pw.println("set trange [0:6371]");
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
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getRho(), "rho", pw);
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVpv(), "vpv", pw);
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVph(), "vph", pw);
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsv(), "vsv", pw);
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getVsh(), "vsh", pw);
            PolynomialStructurePlotter.writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(), structure.getEta(), "eta", pw);

            pw.println("");

            // plot the defined functions
            pw.print("p");
            if (variableTypes.contains(VariableType.RHO)) pw.println("  rho(t),t w l lw 1 " + lineTypeFor(1, 0) + " title '{/Symbol r}', \\");
            if (variableTypes.contains(VariableType.Vpv))
                pw.println("  vpv(t),t w l lw 1 " + lineTypeFor(1, 1) + " title 'Vpv', \\");
            if (variableTypes.contains(VariableType.Vph) || variableTypes.contains(VariableType.Vp))
                pw.println("  vph(t),t w l lw 1 " + lineTypeFor(1, 2) + " title 'Vph', \\");
            if (variableTypes.contains(VariableType.Vsv))
                pw.println("  vsv(t),t w l lw 1 " + lineTypeFor(1, 3) + " title 'Vsv', \\");
            if (variableTypes.contains(VariableType.Vsh) || variableTypes.contains(VariableType.Vs))
                pw.println("  vsh(t),t w l lw 1 " + lineTypeFor(1, 4) + " title 'Vsh', \\");
            if (variableTypes.contains(VariableType.ETA)) pw.println("  eta(t),t w l lw 1 " + lineTypeFor(1, 5) + " title '{/Symbol h}', \\");

            // plot model
            if (variableTypes.contains(VariableType.RHO)) pw.println("  \"rhoAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 0) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vpv)) pw.println("  \"vpvAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 1) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vph)) pw.println("  \"vphAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 2) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vp)) pw.println("  \"vpAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 2) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vsv)) pw.println("  \"vsvAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 3) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vsh)) pw.println("  \"vshAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 4) + " notitle, \\");
            if (variableTypes.contains(VariableType.Vs)) pw.println("  \"vsAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 4) + " notitle, \\");
            if (variableTypes.contains(VariableType.ETA)) pw.println("  \"etaAbsolute.lst\" u 4:3 w l lw 1 " + lineTypeFor(0, 5) + " notitle, \\");

            pw.println("  0,t w l lw 0.5 dt 1 lc rgb 'black' notitle");
        }

        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
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
