package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Creates shellscripts to map {@link PerturbationListFile}.
 * The values of input files should be in percent.
 *
 * @author otsuru
 * @since 2022/7/18
 */
public class PerturbationMapper extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;

    /**
     * Path of perturbation file
     */
    private Path perturbationPath;

    private VariableType variable;
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
            pw.println("##Path of perturbation file, must be directly below workDir, must be set.");
            pw.println("#perturbationPath vsPercent.lst");
            pw.println("##Variable type of perturbation file (Vs)");
            pw.println("#variable ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of percent scale (3)");
            pw.println("#scale ");
        }
        System.err.println(outPath + " is created.");
    }

    public PerturbationMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        perturbationPath = property.parsePath("perturbationPath", null, true, workPath);

        variable = VariableType.valueOf(property.parseString("variable", "Vs"));
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
    }

    @Override
    public void run() throws IOException {

        Set<FullPosition> positions = PerturbationListFile.read(perturbationPath).keySet();
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);

        String fileName = perturbationPath.getFileName().toString();
        String fileNameRoot = fileName.substring(0, fileName.lastIndexOf("."));

        // copy perturbation file to current directory
        Path outPerturbationPath = workPath.resolve(fileName);
        if (!Files.exists(outPerturbationPath)) {
            Files.copy(perturbationPath, outPerturbationPath);
        }

        // output shellscripts
        PerturbationMapShellscript script = new PerturbationMapShellscript(variable, radii, mapRegion, scale, fileNameRoot);
        script.write(workPath);
        System.err.println("After this finishes, please run " + fileNameRoot + "Grid.sh and " + fileNameRoot + "Map.sh");
    }


}
