package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
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
 * Operation that creates a cross section from a {@link PerturbationListFile}.
 *
 * @author otsuru
 * @since 2023/3/24
 */
public class CrossSectionCreator extends Operation {

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

    private double pos0Latitude;
    private double pos0Longitude;
    private double pos1Latitude;
    private double pos1Longitude;
    /**
     * Distance of the starting point along arc before position 0.
     */
    private double beforePos0Deg;
    /**
     * Distance of the ending point along arc after either position 0 or position 1.
     */
    private double afterPosDeg;
    /**
     * Whether the ending point should be decided with respect to position 0 or position 1.
     */
    private boolean useAfterPos1;

    /**
     * Radius of zero point of vertical axis.
     */
    private double zeroPointRadius;
    /**
     * Name of zero point of vertical axis. (ex. "CMB")
     */
    private String zeroPointName;
    /**
     * Whether to flip vertical axis.
     */
    private boolean flipVerticalAxis;

    /**
     * Latitude margin at both ends of region.
     */
    private double marginLatitudeRaw;
    /**
     * Whether marginLatitudeRaw is set in [km] or [deg].
     */
    private boolean setMarginLatitudeByKm;
    /**
     * Longitude margin at both ends of region.
     */
    private double marginLongitudeRaw;
    /**
     * Whether marginLongitudeRaw is set in [km] or [deg].
     */
    private boolean setMarginLongitudeByKm;
    /**
     * Radius margin at both ends of region [km].
     */
    private double marginRadius;

    private double scale;
    /**
     * Whether to display map as mosaic without smoothing.
     */
    private boolean mosaic;
    /**
     * Threshold for mask.
     */
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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#folderTag ");
            pw.println("##Path of perturbation file, must be set.");
            pw.println("#perturbationPath vsPercent.lst");
            pw.println("##Path of perturbation file for mask, when mask is to be applied.");
            pw.println("#maskPath vsPercentRatio.lst");
            pw.println("##Variable type of perturbation file. (Vs)");
            pw.println("#variable ");
            pw.println("##########Settings of great circle arc to display in the cross section.");
            pw.println("##(double) Latitude of position 0, must be set.");
            pw.println("#pos0Latitude ");
            pw.println("##(double) Longitude of position 0, must be set.");
            pw.println("#pos0Longitude ");
            pw.println("##(double) Latitude of position 1, must be set.");
            pw.println("#pos1Latitude ");
            pw.println("##(double) Longitude of position 1, must be set.");
            pw.println("#pos1Longitude ");
            pw.println("##(double) Distance along arc before position 0. (0)");
            pw.println("#beforePos0Deg ");
            pw.println("##(double) Distance along arc after position 0. If not set, the following afterPos1Deg will be used.");
            pw.println("#afterPos0Deg ");
            pw.println("##(double) Distance along arc after position 1. (0)");
            pw.println("#afterPos1Deg ");
            pw.println("##########Radius display settings");
            pw.println("##(double) Radius of zero point of vertical axis. (0)");
            pw.println("#zeroPointRadius 3480");
            pw.println("##Name of zero point of vertical axis. (0)");
            pw.println("#zeroPointName CMB");
            pw.println("##(boolean) Whether to flip vertical axis. (false)");
            pw.println("#flipVerticalAxis true");
            pw.println("##########The following should be set to half of dLatitude, dLongitude, and dRadius used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends of region [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends of region [deg]. (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends of region [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends of region [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##(double) Radius margin at both ends of region [km]. (25)");
            pw.println("#marginRadiusKm ");
            pw.println("##########Parameters for perturbation values");
            pw.println("##(double) Range of percent scale. (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing. (false)");
            pw.println("#mosaic true");
            pw.println("##(double) Threshold for mask. (0.3)");
            pw.println("#maskThreshold ");
        }
        System.err.println(outPath + " is created.");
    }

    public CrossSectionCreator(Property property) throws IOException {
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

        pos0Latitude = property.parseDouble("pos0Latitude", null);
        pos0Longitude = property.parseDouble("pos0Longitude", null);
        pos1Latitude = property.parseDouble("pos1Latitude", null);
        pos1Longitude = property.parseDouble("pos1Longitude", null);
        beforePos0Deg = property.parseDouble("beforePos0Deg", "0");
        if (property.containsKey("afterPos0Deg")) {
            afterPosDeg = property.parseDouble("afterPos0Deg", null);
            useAfterPos1 = false;
        } else {
            afterPosDeg = property.parseDouble("afterPos1Deg", "0");
            useAfterPos1 = true;
        }

        zeroPointRadius = property.parseDouble("zeroPointRadius", "0");
        zeroPointName = property.parseString("zeroPointName", "0");
        if (zeroPointRadius < 0) throw new IllegalArgumentException("zeroPointRadius must be positive.");
        flipVerticalAxis = property.parseBoolean("flipVerticalAxis", "false");

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitudeRaw = property.parseDouble("marginLatitudeKm", null);
            setMarginLatitudeByKm = true;
        } else {
            marginLatitudeRaw = property.parseDouble("marginLatitudeDeg", "2.5");
            setMarginLatitudeByKm = false;
        }
        if (marginLatitudeRaw <= 0) throw new IllegalArgumentException("marginLatitude must be positive.");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitudeRaw = property.parseDouble("marginLongitudeKm", null);
            setMarginLongitudeByKm = true;
        } else {
            marginLongitudeRaw = property.parseDouble("marginLongitudeDeg", "2.5");
            setMarginLongitudeByKm = false;
        }
        if (marginLongitudeRaw <= 0) throw new IllegalArgumentException("marginLongitude must be positive.");
        marginRadius = property.parseDouble("marginRadiusKm", "25");
        if (marginRadius <= 0) throw new IllegalArgumentException("marginRadius must be positive.");

        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

        // read perturbation file
        Map<FullPosition, Double> discreteMap = PerturbationListFile.read(perturbationPath);
        Set<FullPosition> discretePositions = discreteMap.keySet();

        // read mask perturbation file
        Map<FullPosition, Double> maskDiscreteMap = null;
        boolean maskExists = false;
        if (maskPath != null) {
            maskExists = true;
            maskDiscreteMap = PerturbationListFile.read(maskPath);
        }

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "crossSection", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        String modelFileNameRoot = FileAid.extractNameRoot(perturbationPath);
        String scaleLabel = "@~d@~" + variable + "/" + variable + " \\(\\%\\)";

        CrossSectionWorker worker = new CrossSectionWorker(pos0Latitude, pos0Longitude, pos1Latitude, pos1Longitude,
                beforePos0Deg, afterPosDeg, useAfterPos1, zeroPointRadius, zeroPointName, flipVerticalAxis,
                marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, marginRadius,
                scale, mosaic, maskExists, maskThreshold, modelFileNameRoot, discretePositions);
        worker.computeCrossSection(discreteMap, maskDiscreteMap, outPath);
        worker.writeScripts(scaleLabel, outPath);

        System.err.println("After this finishes, please enter " + outPath + "/ and run " + modelFileNameRoot + "Section.sh");
    }

}
