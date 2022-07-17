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
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * @author otsuru
 * @since 2022/7/17
 */
public class ModelMapper extends Operation {

    /**
     * The interval of deciding map size
     */
    private static final int INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map
     */
    private static final int MAP_RIM = 5;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * Path of model file
     */
    private Path modelPath;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;
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
            pw.println("#tag ");
            pw.println("##Path of model file, must be set.");
            pw.println("#modelPath model.lst");
            pw.println("##Path of an initial structure file used. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of an initial structure model used (PREM)");
            pw.println("#structureName ");
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
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        modelPath = property.parsePath("modelPath", null, true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());

        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
    }

    @Override
    public void run() throws IOException {

        // read initial structure
        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = PolynomialStructureFile.read(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        // read model
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        List<UnknownParameter> unknowns = KnownParameter.extractParameterList(knowns);
        double[] radii = unknowns.stream().mapToDouble(unknown -> unknown.getPosition().getR()).distinct().sorted().toArray();
        PerturbationModel model = new PerturbationModel(knowns, structure);

        // decide map region
        if (mapRegion == null) mapRegion = decideMapRegion(unknowns);

        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMap", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (VariableType variable : variableTypes) {
            String variableName = variable.toString().toLowerCase();
            // output perturbation file
            Path outputPercentPath = outPath.resolve(variableName + "Percent.lst");
            PerturbationListFile.writePercentForType(variable, model, outputPercentPath);
            // output shellscripts
            PerturbationMapShellscript script = new PerturbationMapShellscript(variable, radii, mapRegion, scale, variableName + "Percent");
            script.write(outPath);
            System.err.println("After this finishes, please run " + outPath + "/" + variableName + "PercentMap.sh");
        }
    }

    /**
     * Decides a rectangular region of a map that is sufficient to plot all parameter points.
     * @param unknowns (List of UnknownParameter) Parameters that need to be included in map region
     * @return (String) "lonMin/lonMax/latMin/latMax"
     * @throws IOException
     */
    static String decideMapRegion(List<UnknownParameter> unknowns) throws IOException {
        double latMin = Double.MAX_VALUE;
        double latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE;
        double lonMax = -Double.MAX_VALUE;
        // search all unknowns
        for (UnknownParameter unknown : unknowns) {
            HorizontalPosition pos = unknown.getPosition();
            if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
            if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
            if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
            if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
        }
        // expand the region a bit more
        latMin = Math.floor(latMin / INTERVAL) * INTERVAL - MAP_RIM;
        latMax = Math.ceil(latMax / INTERVAL) * INTERVAL + MAP_RIM;
        lonMin = Math.floor(lonMin / INTERVAL) * INTERVAL - MAP_RIM;
        lonMax = Math.ceil(lonMax / INTERVAL) * INTERVAL + MAP_RIM;
        // return as String
        return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
    }

}
