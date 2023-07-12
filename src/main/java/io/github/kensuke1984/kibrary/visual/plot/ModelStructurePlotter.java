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
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

public class ModelStructurePlotter extends Operation {

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

    }

    @Override
    public void run() throws IOException {

        // read initial structure
        System.err.print("Initial structure: ");
        PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

        // read parameters
        Path unknownsPath = resultPath.resolve("unknowns.lst");
        Set<FullPosition> positions = UnknownParameterFile.read(unknownsPath).stream()
                .map(unknown -> unknown.getPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

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
            }
        }

    }

}
