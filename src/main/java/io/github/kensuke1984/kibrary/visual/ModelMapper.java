package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.multigrid.MultigridDesign;
import io.github.kensuke1984.kibrary.multigrid.MultigridInformationFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

/**
 * Creates shellscripts to map {@link KnownParameterFile}.
 *
 * @author otsuru
 * @since 2022/7/17
 */
public class ModelMapper extends Operation {

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
     * Path of model file
     */
    private Path modelPath;
    /**
     * file of 1D structure used in inversion
     */
    private Path initialStructurePath;
    /**
     * name of 1D structure used in inversion
     */
    private String initialStructureName;
    /**
     * file of 1D structure to map perturbations against
     */
    private Path referenceStructurePath;
    /**
     * name of 1D structure to map perturbations against
     */
    private String referenceStructureName;
    /**
     * Path of a {@link MultigridInformationFile}
     */
    private Path multigridPath;
    private Set<VariableType> variableTypes;

    private String mapRegion;
    private double scale;

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#folderTag ");
            pw.println("##Path of model file, must be set.");
            pw.println("#modelPath model.lst");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a structure file to map perturbations against. If this is unset, the following referenceStructureName will be referenced.");
            pw.println("#referenceStructurePath ");
            pw.println("##Name of a structure model to map perturbations against (PREM)");
            pw.println("#referenceStructureName ");
            pw.println("##Path of a multigrid information file, if multigrid inversion is conducted");
            pw.println("#multigridPath multigrid.inf");
            pw.println("##Variable types to map, listed using spaces (Vs)");
            pw.println("#variableTypes ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of percent scale (3)");
            pw.println("#scale ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        modelPath = property.parsePath("modelPath", null, true, workPath);
        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        if (property.containsKey("referenceStructurePath")) {
            referenceStructurePath = property.parsePath("referenceStructurePath", null, true, workPath);
        } else {
            referenceStructureName = property.parseString("referenceStructureName", "PREM");
        }
        if (property.containsKey("multigridPath"))
            multigridPath = property.parsePath("multigridPath", null, true, workPath);

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());

        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
    }

    @Override
    public void run() throws IOException {

        // read initial structure
        System.err.print("Initial structure: ");
        PolynomialStructure initialStructure = null;
        if (initialStructurePath != null) {
            initialStructure = PolynomialStructureFile.read(initialStructurePath);
        } else {
            initialStructure = PolynomialStructure.of(initialStructureName);
        }
        // read reference structure
        System.err.print("Reference structure: ");
        PolynomialStructure referenceStructure = null;
        if (referenceStructurePath != null) {
            referenceStructure = PolynomialStructureFile.read(referenceStructurePath);
        } else {
            referenceStructure = PolynomialStructure.of(referenceStructureName);
        }

        // read knowns
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        Set<FullPosition> positions = KnownParameter.extractParameterList(knowns).stream()
                .map(unknown -> unknown.getPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // read and apply multigrid file
        if (multigridPath != null) {
            MultigridDesign multigrid = MultigridInformationFile.read(multigridPath);
            knowns = multigrid.reverseFusion(knowns);
        }

        // build model
        PerturbationModel model = new PerturbationModel(knowns, initialStructure);
        if (!referenceStructure.equals(initialStructure)) {
            model = model.withReferenceStructureAs(referenceStructure);
        }

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);

        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMap", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (VariableType variable : variableTypes) {
            String variableName = variable.toString().toLowerCase();
            // output perturbation file
            Path outputPercentPath = outPath.resolve(variableName + "Percent.lst");
            PerturbationListFile.writePercentForType(variable, model, outputPercentPath);
            // output shellscripts
            PerturbationMapShellscript script = new PerturbationMapShellscript(variable, radii, mapRegion, scale, variableName + "Percent");
            script.write(outPath);
            System.err.println("After this finishes, please run " + outPath + "/" + variableName + "PercentGrid.sh and "
                    + outPath + "/" + variableName + "PercentMap.sh");
        }
    }

}
