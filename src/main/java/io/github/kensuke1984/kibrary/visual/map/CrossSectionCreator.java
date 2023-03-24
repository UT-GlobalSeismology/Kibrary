package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

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
    private double beforePos0Deg;
    private double afterPosDeg;
    private boolean useAfterPos1;

    private double marginLatitude;
    private boolean setLatitudeByKm;
    private double marginLongitude;
    private boolean setLongitudeByKm;
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
            pw.println("##########Settings of great circle arc to display in the cross section");
            pw.println("##Latitude of position 0, must be set");
            pw.println("#pos0Latitude ");
            pw.println("##Longitude of position 0, must be set");
            pw.println("#pos0Longitude ");
            pw.println("##Latitude of position 1, must be set");
            pw.println("#pos1Latitude ");
            pw.println("##Longitude of position 1, must be set");
            pw.println("#pos1Longitude ");
            pw.println("##Distance along arc before position 0 (0)");
            pw.println("#beforePos0Deg ");
            pw.println("##Distance along arc after position 0. If not set, the following afterPos1Deg will be used.");
            pw.println("#afterPos0Deg ");
            pw.println("##Distance along arc after position 1 (0)");
            pw.println("#afterPos1Deg ");
            pw.println("##########The following should be set to half of dLatitude, dLongitude, and dRadius used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends of region [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends of region [deg] (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends of region [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends of region [deg] (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##(double) Radius margin at both ends of region [km] (25)");
            pw.println("#marginRadiusKm ");
            pw.println("##########Parameters for perturbation values");
            pw.println("##(double) Range of percent scale (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing (false)");
            pw.println("#mosaic ");
            pw.println("##(double) Threshold for mask (0.3)");
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

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitude = property.parseDouble("marginLatitudeKm", null);
            setLatitudeByKm = true;
        } else {
            marginLatitude = property.parseDouble("marginLatitudeDeg", "2.5");
            setLatitudeByKm = false;
        }
        if (marginLatitude <= 0) throw new IllegalArgumentException("marginLatitude must be positive");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitude = property.parseDouble("marginLongitudeKm", null);
            setLongitudeByKm = true;
        } else {
            marginLongitude = property.parseDouble("marginLongitudeDeg", "2.5");
            setLongitudeByKm = false;
        }
        if (marginLongitude <= 0) throw new IllegalArgumentException("marginLongitude must be positive");

        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

        Map<FullPosition, Double> discreteMap = PerturbationListFile.read(perturbationPath);
        Set<FullPosition> positions = discreteMap.keySet();

        // decide start and end positions of cross section
        HorizontalPosition pos0 = new HorizontalPosition(pos0Latitude, pos0Longitude);
        HorizontalPosition pos1 = new HorizontalPosition(pos1Latitude, pos1Longitude);
        HorizontalPosition startPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), -beforePos0Deg);
        HorizontalPosition endPosition;
        if (useAfterPos1) {
            endPosition = pos1.pointAlongAzimuth(pos1.computeAzimuthDeg(pos0), -afterPosDeg);
        } else {
            endPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), afterPosDeg);
        }

        // decide horizontal positions at which to sample values
        Map<Double, HorizontalPosition> samplePositionMap = new TreeMap<>();
        double distance = startPosition.computeEpicentralDistanceDeg(endPosition);
        double azimuth = startPosition.computeAzimuthDeg(endPosition);
        double gridInterval = PerturbationMapShellscript.decideGridSampling(positions);
        int nSamplePosition = (int) Math.floor(distance / gridInterval) + 1;
        for (int i = 0; i < nSamplePosition; i++) {
            HorizontalPosition position = startPosition.pointAlongAzimuth(azimuth, i * gridInterval);
            samplePositionMap.put(i * gridInterval, position);
        }

        // for each radius and latitude, resample values at sampleLongitudes
        double[] sampleLongitudes = samplePositionMap.values().stream().mapToDouble(HorizontalPosition::getLongitude)
                .distinct().sorted().toArray();
        Map<FullPosition, Double> resampledMap = Interpolation.inEachWestEastLine(discreteMap, sampleLongitudes,
                marginLongitude, setLongitudeByKm, mosaic);



    }

}
