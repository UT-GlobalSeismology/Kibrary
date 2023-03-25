package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class CrossSectionCreator extends Operation {

    /**
     * Number of nodes to divide an original node when smoothing in vertical direction
     */
    public static final int VERTICAL_SMOOTHING_FACTOR = 2;

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

    private double marginLatitudeRaw;
    private boolean setMarginLatitudeByKm;
    private double marginLongitudeRaw;
    private boolean setMarginLongitudeByKm;
    private double marginRadius;
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
            marginLatitudeRaw = property.parseDouble("marginLatitudeKm", null);
            setMarginLatitudeByKm = true;
        } else {
            marginLatitudeRaw = property.parseDouble("marginLatitudeDeg", "2.5");
            setMarginLatitudeByKm = false;
        }
        if (marginLatitudeRaw <= 0) throw new IllegalArgumentException("marginLatitude must be positive");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitudeRaw = property.parseDouble("marginLongitudeKm", null);
            setMarginLongitudeByKm = true;
        } else {
            marginLongitudeRaw = property.parseDouble("marginLongitudeDeg", "2.5");
            setMarginLongitudeByKm = false;
        }
        if (marginLongitudeRaw <= 0) throw new IllegalArgumentException("marginLongitude must be positive");
        marginRadius = property.parseDouble("marginRadiusKm", "25");
        if (marginRadius <= 0) throw new IllegalArgumentException("marginRadius must be positive");

        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
        maskThreshold = property.parseDouble("maskThreshold", "0.3");
    }

    @Override
    public void run() throws IOException {

        // read perturbation file
        Map<FullPosition, Double> discreteMap = PerturbationListFile.read(perturbationPath);
        Set<FullPosition> discretePositions = discreteMap.keySet();

        //~decide start and end positions of cross section
        HorizontalPosition pos0 = new HorizontalPosition(pos0Latitude, pos0Longitude);
        HorizontalPosition pos1 = new HorizontalPosition(pos1Latitude, pos1Longitude);
        HorizontalPosition startPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), -beforePos0Deg);
        HorizontalPosition endPosition;
        if (useAfterPos1) {
            endPosition = pos1.pointAlongAzimuth(pos1.computeAzimuthDeg(pos0), -afterPosDeg);
        } else {
            endPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), afterPosDeg);
        }

        //~decide horizontal positions at which to sample values
        Map<Double, HorizontalPosition> samplePositionMap = new TreeMap<>();
        double distance = startPosition.computeEpicentralDistanceDeg(endPosition);
        double azimuth = startPosition.computeAzimuthDeg(endPosition);
        double gridInterval = PerturbationMapShellscript.decideGridSampling(discretePositions);
        int nSamplePosition = (int) Math.floor(distance / gridInterval) + 1;
        for (int i = 0; i < nSamplePosition; i++) {
            HorizontalPosition position = startPosition.pointAlongAzimuth(azimuth, i * gridInterval);
            samplePositionMap.put(i * gridInterval, position);
        }

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "crossSection", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // compute cross section data and output
        computeCrossSectionData(discreteMap, samplePositionMap, outPath.resolve("crossSection.txt"));

        if (maskPath != null) {
            // read perturbation file
            Map<FullPosition, Double> maskDiscreteMap = PerturbationListFile.read(perturbationPath);
            // compute cross section data and output
            computeCrossSectionData(maskDiscreteMap, samplePositionMap, outPath.resolve("crossSectionMask.txt"));
        }
    }

    private void computeCrossSectionData(Map<FullPosition, Double> discreteMap, Map<Double, HorizontalPosition> samplePositionMap, Path outputPath) throws IOException {
        //~for each radius and latitude, resample values at sampleLongitudes
        double[] sampleLongitudes = samplePositionMap.values().stream().mapToDouble(HorizontalPosition::getLongitude)
                .distinct().sorted().toArray();
        Map<FullPosition, Double> resampledMap = Interpolation.inEachWestEastLine(discreteMap, sampleLongitudes,
                marginLongitudeRaw, setMarginLongitudeByKm, mosaic);

        // acquire some information
        Set<FullPosition> resampledPositions = resampledMap.keySet();
        double[] radii = resampledPositions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();
        double radialGridInterval = decideRadialGridSampling(radii);
        double marginLatitudeDeg = decideMarginLatitudeDeg(radii);

        //~compute sampled trace at each sample point
        Map<Double, Trace> sampledTraceMap = new TreeMap<>();
        for (Map.Entry<Double, HorizontalPosition> sampleEntry : samplePositionMap.entrySet()) {
            double sampleLatitude = sampleEntry.getValue().getLatitude();
            double sampleLongitude = sampleEntry.getValue().getLongitude();

            //~extract continuous sequence of latitudes in this meridian that will be used for interpolation
            Set<FullPosition> positionsInMeridian = resampledPositions.stream()
                    .filter(pos -> Precision.equals(pos.getLongitude(), sampleLongitude, HorizontalPosition.LONGITUDE_EPSILON))
                    .collect(Collectors.toSet());
            double[] latitudesInMeridian = positionsInMeridian.stream().mapToDouble(pos -> pos.getLatitude()).toArray();
            double[] latitudesExtracted = extractContinuousLatitudeSequence(latitudesInMeridian, sampleLatitude, marginLatitudeDeg);
            // skip this sample point if sampleLatitude is not included in a latitude sequence (thus cannot be interpolated)
            if (latitudesExtracted == null) continue;

            //~create vertical trace at this sample point
            double[] values = new double[radii.length];
            for (int i = 0; i < radii.length; i++) {
                double radius = radii[i];
                Trace meridionalTrace = formMeridionalTrace(latitudesExtracted, sampleLongitude, radius, resampledMap);
                // interpolate for value at sampleLatitude
                values[i] = Interpolation.interpolateTraceAtPoint(meridionalTrace, sampleLatitude, marginLatitudeDeg, mosaic);
            }
            Trace verticalTrace = new Trace(radii, values);

            //~interpolate vertical trace on grid
            verticalTrace = Interpolation.interpolateTraceOnGrid(verticalTrace, radialGridInterval, marginRadius, mosaic);
            sampledTraceMap.put(sampleEntry.getKey(), verticalTrace);
        }

        output(samplePositionMap, sampledTraceMap, outputPath);
    }

    private static void output(Map<Double, HorizontalPosition> samplePositionMap, Map<Double, Trace> sampleTraceMap, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (Map.Entry<Double, Trace> sampleTraceEntry : sampleTraceMap.entrySet()) {
                Trace sampleTrace = sampleTraceEntry.getValue();
                HorizontalPosition horizontalPosition = samplePositionMap.get(sampleTraceEntry.getKey());
                for (int i = 0; i < sampleTrace.getLength(); i++) {
                    // "distanceAlongArc latitude longitude radius value"
                    pw.println(sampleTraceEntry.getKey() + " " + horizontalPosition + " " + sampleTrace.getXAt(i) + " " + sampleTrace.getYAt(i));
                }
            }
        }
    }

    /**
     * @see PerturbationMapShellscript#decideGridSampling(Set)
     * @param radii
     * @return
     */
    private static double decideRadialGridSampling(double[] radii) {
        // average radius interval
        double radiusInterval = (radii[radii.length - 1] - radii[0]) / (radii.length - 1);
        // decide interval to resample
        int power = (int) Math.floor(Math.log10(radiusInterval));
        double coef = radiusInterval / Math.pow(10, power);
        if (coef < 1) throw new IllegalStateException("Grid interval decision went wrong");
        else if (coef < 2) return 1.0 * Math.pow(10, power) / VERTICAL_SMOOTHING_FACTOR;
        else if (coef < 5) return 2.0 * Math.pow(10, power) / VERTICAL_SMOOTHING_FACTOR;
        else if (coef < 10) return 5.0 * Math.pow(10, power) / VERTICAL_SMOOTHING_FACTOR;
        else throw new IllegalStateException("Grid interval decision went wrong");
    }

    private double decideMarginLatitudeDeg(double[] radii) {
        double meanRadius = Arrays.stream(radii).average().getAsDouble();
        return setMarginLatitudeByKm ? Math.toDegrees(marginLatitudeRaw / meanRadius) : marginLatitudeRaw;
    }

    private Trace formMeridionalTrace(double[] latitudesExtracted, double longitude, double radius, Map<FullPosition, Double> resampledMap) {
        double[] values = new double[latitudesExtracted.length];
        for (int i = 0; i < latitudesExtracted.length; i++) {
            double latitude = latitudesExtracted[i];
            FullPosition position = new FullPosition(latitude, longitude, radius);
            values[i] = resampledMap.get(position);
        }
        return new Trace(latitudesExtracted, values);
    }

    /**
     * Split a series of latitudes at gaps so that they become continuous sequences,
     * and return the one that includes a specified latitude.
     * @see Interpolation#splitTraceAtGaps(Trace, double)
     * @param latitudesInMeridian (double[]) Array of latitudes which may include gaps.
     * @param targetLatitude (double) A sequence including this latitude in its domain shall be searched for.
     * @param margin (double) The length of margin to add at either end of the domain of each sequence.
     * @return (double[]) A continuous sequence of latitudes that includes targetLatitude in its range.
     *                      If no sequence includes targetLatitude, null.
     */
    private double[] extractContinuousLatitudeSequence(double[] latitudesInMeridian, double targetLatitude, double margin) {
        List<double[]> sequenceList = new ArrayList<>();
        int iStart = 0;
        // from i=1, check if [x(i-1),x(i)] is much larger than margin*2
        for (int i = 1; i < latitudesInMeridian.length; i++) {
            if (latitudesInMeridian[i] - latitudesInMeridian[i - 1] > margin * 2.5) {
                sequenceList.add(Arrays.copyOfRange(latitudesInMeridian, iStart, i));
                iStart = i;
            }
        }
        // add last sequence
        sequenceList.add(Arrays.copyOfRange(latitudesInMeridian, iStart, latitudesInMeridian.length));

        // find which sequence targetLatitude is in
        for (int k = 0; k < sequenceList.size(); k++) {
            double[] sequence = sequenceList.get(k);
            if (sequence[0] - margin <= targetLatitude && targetLatitude < sequence[sequence.length - 1] + margin)
                return sequence;
        }
        // if targetLatitude is not in any sequence, return null
        return null;
    }

}
