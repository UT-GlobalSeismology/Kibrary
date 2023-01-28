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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;

    /**
     * Path of perturbation file
     */
    private Path perturbationPath;
    /**
     * Path of perturbation file to be used as mask
     */
    private Path maskPath;

    private VariableType variable;
    private double[] boundaries;
    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private int nPanelsPerRow;
    private String mapRegion;
    private double scale;
    /**
     * Whether to display map as mosaic without smoothing
     */
    private boolean mosaic;
    private double maskThreshold;

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
            pw.println("##Path of perturbation file, must be set");
            pw.println("#perturbationPath vsPercent.lst");
            pw.println("##Path of perturbation file for mask, when mask is to be applied");
            pw.println("#maskPath vsPercentRatio.lst");
            pw.println("##Variable type of perturbation file (Vs)");
            pw.println("#variable ");
            pw.println("##(double[]) The display values of each layer boundary, listed from the inside using spaces (0 50 100 150 200 250 300 350 400)");
            pw.println("#boundaries ");
            pw.println("##(int[]) Indices of layers to display, listed from the inside using spaces, when specific layers are to be displayed");
            pw.println("##  Layers are numbered 0, 1, 2, ... from the inside.");
            pw.println("#displayLayers ");
            pw.println("##(int) Number of panels to display in each row (4)");
            pw.println("#nPanelsPerRow ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of percent scale (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing (false)");
            pw.println("#mosaic ");
            pw.println("##(double) Threshold for mask (0.3)");
            pw.println("#maskThreshold ");
        }
        System.err.println(outPath + " is created.");
    }

    public PerturbationMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        perturbationPath = property.parsePath("perturbationPath", null, true, workPath);
        if (property.containsKey("maskPath")) {
            maskPath = property.parsePath("maskPath", null, true, workPath);
        }

        variable = VariableType.valueOf(property.parseString("variable", "Vs"));
        boundaries = property.parseDoubleArray("boundaries", "0 50 100 150 200 250 300 350 400");
        if (property.containsKey("displayLayers")) displayLayers = property.parseIntArray("displayLayers", null);
        nPanelsPerRow = property.parseInt("nPanelsPerRow", "4");
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

        Set<FullPosition> positions = PerturbationListFile.read(perturbationPath).keySet();
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);
        double positionInterval = PerturbationMapShellscript.findPositionInterval(positions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "perturbationMap", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // copy perturbation file to outPath
        String fileNameRoot = FileAid.extractNameRoot(perturbationPath);
        Path outPerturbationPath = outPath.resolve(fileNameRoot + ".lst");
        if (!Files.exists(outPerturbationPath)) {
            Files.copy(perturbationPath, outPerturbationPath);
        }

        // copy mask file to outPath
        String maskFileNameRoot = null;
        if (maskPath != null) {
            maskFileNameRoot = FileAid.extractNameRoot(maskPath) + "_forMask";
            Path outMaskPath = outPath.resolve(maskFileNameRoot + ".lst");
            if (!Files.exists(outMaskPath)) {
                Files.copy(maskPath, outMaskPath);
            }
        }

        // output shellscripts
        PerturbationMapShellscript script;
        script = new PerturbationMapShellscript(variable, radii, boundaries, mapRegion, positionInterval, scale, fileNameRoot, nPanelsPerRow);
        script.setMosaic(mosaic);
        if (displayLayers != null) script.setDisplayLayers(displayLayers);
        if (maskPath != null) script.setMask(maskFileNameRoot, maskThreshold);
        script.write(outPath);
        System.err.println("After this finishes, please enter " + outPath
                + "/ and run " + fileNameRoot + "Grid.sh and " + fileNameRoot + "Map.sh");
    }


}
