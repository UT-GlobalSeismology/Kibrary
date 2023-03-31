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
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class CrossSectionCreator extends Operation {

    /**
     * How much finer to make the grid
     */
    public static final int GRID_SMOOTHING_FACTOR = 5;
    /**
     * Size of vertical grid with respect to horizontal grid
     */
    public static final int VERTICAL_ENLARGE_FACTOR = 2;

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

    private double zeroPointRadius;
    private String zeroPointName;
    private boolean flipVerticalAxis;

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
            pw.println("##(double) Latitude of position 0, must be set");
            pw.println("#pos0Latitude ");
            pw.println("##(double) Longitude of position 0, must be set");
            pw.println("#pos0Longitude ");
            pw.println("##(double) Latitude of position 1, must be set");
            pw.println("#pos1Latitude ");
            pw.println("##(double) Longitude of position 1, must be set");
            pw.println("#pos1Longitude ");
            pw.println("##(double) Distance along arc before position 0 (0)");
            pw.println("#beforePos0Deg ");
            pw.println("##(double) Distance along arc after position 0. If not set, the following afterPos1Deg will be used.");
            pw.println("#afterPos0Deg ");
            pw.println("##(double) Distance along arc after position 1 (0)");
            pw.println("#afterPos1Deg ");
            pw.println("##########Radius display settings");
            pw.println("##(double) Radius of zero point of vertical axis (0)");
            pw.println("#zeroPointRadius ");
            pw.println("##Name of zero point of vertical axis (0)");
            pw.println("#zeroPointName ");
            pw.println("##(boolean) Whether to flip vertical axis (false)");
            pw.println("#flipVerticalAxis ");
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
        double horizontalGridInterval = PerturbationMapShellscript.decideGridSampling(discretePositions) / GRID_SMOOTHING_FACTOR;
        int nSamplePosition = (int) Math.round(distance / horizontalGridInterval) + 1;
        for (int i = 0; i < nSamplePosition; i++) {
            HorizontalPosition position = startPosition.pointAlongAzimuth(azimuth, i * horizontalGridInterval);
            samplePositionMap.put(i * horizontalGridInterval, position);
        }

        // decide vertical settings
        double verticalGridInterval = horizontalGridInterval * VERTICAL_ENLARGE_FACTOR;
        double[] radii = discretePositions.stream().mapToDouble(FullPosition::getR).distinct().sorted().toArray();
        double lowerRadius = radii[0] - marginRadius;
        double upperRadius = radii[radii.length - 1] + marginRadius;

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "crossSection", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output file names
        String variableName = variable.toString().toLowerCase();
        String modelFileNameRoot = variableName + "PercentSection";
        Path interpolatedPath = outPath.resolve(modelFileNameRoot+ "XZ.txt");
        Path maskInterpolatedPath = outPath.resolve(modelFileNameRoot + "_forMaskXZ.txt");
        Path cpMasterPath = outPath.resolve("cp_master.cpt");
        Path cpMaskPath = outPath.resolve("cp_mask.cpt");
        Path annotationPath = outPath.resolve("rAnnotation.txt");
        Path gridPath = outPath.resolve(modelFileNameRoot + "Grid.sh");
        Path gmtPath = outPath.resolve(modelFileNameRoot + "Map.sh");

        // compute cross section data and output
        computeCrossSectionData(discreteMap, samplePositionMap, verticalGridInterval, interpolatedPath);
        PerturbationMapShellscript.writeCpMaster(cpMasterPath);

        if (maskPath != null) {
            // read perturbation file
            Map<FullPosition, Double> maskDiscreteMap = PerturbationListFile.read(maskPath);
            // compute cross section data and output
            computeCrossSectionData(maskDiscreteMap, samplePositionMap, verticalGridInterval, maskInterpolatedPath);
            PerturbationMapShellscript.writeCpMask(cpMaskPath, maskThreshold);
        }

        double[] annotationRadii = {lowerRadius, upperRadius};
        writeAnnotationFile(annotationRadii, annotationPath);
        writeGridMaker(distance, lowerRadius, upperRadius, horizontalGridInterval, verticalGridInterval, modelFileNameRoot, gridPath);
        writeMakeMap(distance, lowerRadius, upperRadius, modelFileNameRoot, gmtPath);
    }

    private void computeCrossSectionData(Map<FullPosition, Double> discreteMap, Map<Double, HorizontalPosition> samplePositionMap,
            double verticalGridInterval, Path outputPath) throws IOException {
        //~for each radius and latitude, resample values at sampleLongitudes
        double[] sampleLongitudes = samplePositionMap.values().stream().mapToDouble(HorizontalPosition::getLongitude)
                .distinct().sorted().toArray();
        Map<FullPosition, Double> resampledMap = Interpolation.inEachWestEastLine(discreteMap, sampleLongitudes,
                marginLongitudeRaw, setMarginLongitudeByKm, mosaic);

        // acquire some information
        Set<FullPosition> resampledPositions = resampledMap.keySet();
        double[] radii = resampledPositions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();
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
            if (positionsInMeridian.size() == 0) {
//                System.err.println("No positions for longitude " + sampleLongitude);
                continue;
            }
            double[] latitudesInMeridian = positionsInMeridian.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();
            double[] latitudesExtracted = extractContinuousLatitudeSequence(latitudesInMeridian, sampleLatitude, marginLatitudeDeg);
            // skip this sample point if sampleLatitude is not included in a latitude sequence (thus cannot be interpolated)
            if (latitudesExtracted == null) {
//                System.err.println("No data for longitude " + sampleLongitude);
                continue;
            }

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
            verticalTrace = Interpolation.interpolateTraceOnGrid(verticalTrace, verticalGridInterval, marginRadius, mosaic);
            sampledTraceMap.put(sampleEntry.getKey(), verticalTrace);
        }

        output(samplePositionMap, sampledTraceMap, outputPath);
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
     * @param latitudesInMeridian (double[]) Array of latitudes which may include gaps. Must be distinct and sorted.
     * @param targetLatitude (double) A sequence including this latitude in its domain shall be searched for.
     * @param margin (double) The length of margin to add at either end of the domain of each sequence.
     * @return (double[]) A continuous sequence of latitudes that includes targetLatitude in its range.
     *                      If no sequence includes targetLatitude, null.
     */
    private double[] extractContinuousLatitudeSequence(double[] latitudesInMeridian, double targetLatitude, double margin) {
        if (latitudesInMeridian.length == 0) return null;

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

    private void writeAnnotationFile(double[] radii, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < radii.length; i++) {
                if (Precision.equals(radii[i], zeroPointRadius, FullPosition.RADIUS_EPSILON)) {
                    pw.println(radii[i] + " a " + zeroPointName);
                } else if (i == 0 || i == radii.length - 1) {
                    double zValue = flipVerticalAxis ? -(radii[i] - zeroPointRadius) : (radii[i] - zeroPointRadius);
                    pw.println(radii[i] + " a " + MathAid.simplestString(zValue));
                } else {
                    pw.println(radii[i] + " f");
                }
            }
        }
    }

    private void writeGridMaker(double sectionDistance, double lowerRadius, double upperRadius,
            double horizontalGridInterval, double verticalGridInterval, String modelFileNameRoot, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("cat " + modelFileNameRoot + "XZ.txt | \\");
            pw.println("awk '{print $1,$4,$5}' | \\");
            pw.println("gmt xyz2grd -G0model.grd -R0/" + MathAid.simplestString(sectionDistance)
                    + "/" + MathAid.simplestString(lowerRadius) + "/" + MathAid.simplestString(upperRadius)
                    + " -I" + MathAid.simplestString(horizontalGridInterval) + "/" + MathAid.simplestString(verticalGridInterval) + " -di0");
            if (maskPath != null) {
                pw.println("cat " + modelFileNameRoot + "_forMaskXZ.txt | \\");
                pw.println("awk '{print $1,$4,$5}' | \\");
                pw.println("gmt xyz2grd -G0mask.grd -R0/" + MathAid.simplestString(sectionDistance)
                        + "/" + MathAid.simplestString(lowerRadius) + "/" + MathAid.simplestString(upperRadius)
                        + " -I" + MathAid.simplestString(horizontalGridInterval) + "/" + MathAid.simplestString(verticalGridInterval) + " -di0");
            }
        }
    }

    private void writeMakeMap(double sectionDistance, double lowerRadius, double upperRadius, String modelFileNameRoot, Path outputPath) throws IOException {
        String paramName = variable.toString();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 6000x6000");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 50");
            pw.println("gmt set FONT_LABEL 50p,Helvetica,black");
            pw.println("gmt set MAP_ANNOT_OFFSET_PRIMARY 10p");
            pw.println("gmt set MAP_TICK_LENGTH_PRIMARY 10p");
            pw.println("");
            pw.println("# map parameters");
            pw.println("R='-R0/" + MathAid.simplestString(sectionDistance)
                    + "/" + MathAid.simplestString(lowerRadius) + "/" + MathAid.simplestString(upperRadius) + "'");
            pw.println("J='-JP60+a+t" + MathAid.simplestString(sectionDistance / 2) + "'");
            pw.println("B='-BWeSn -Bx30f10 -BycrAnnotation.txt'");
            pw.println("");
            pw.println("outputps=" + modelFileNameRoot + ".eps");
            pw.println("MP=" + scale);
            pw.println("gmt makecpt -Ccp_master.cpt -T-$MP/$MP > cp.cpt");
            pw.println("");
            pw.println("#------- Panels");
            pw.println("gmt grdimage 0model.grd $B $J $R -Ccp.cpt -K -Y80 -X20> $outputps");
            if (maskPath != null) {
                pw.println("gmt grdimage 0mask.grd $J $R -Ccp_mask.cpt -G0/0/0 -t80 -K -O >> $outputps");
            }
            pw.println("");
            pw.println("#------- Scale");
            pw.println("gmt psscale -Ccp.cpt -Dx2/-4+w12/0.8+h -B1.0+l\"@~d@~" + paramName + "/" + paramName + " \\(\\%\\)\" -K -O -Y2 -X5 >> $outputps");
            pw.println("");
            pw.println("#------- Finalize");
            pw.println("gmt pstext -N -F+jLM+f30p,Helvetica,black $J $R -O << END >> $outputps");
            pw.println("END");
            pw.println("");
            pw.println("gmt psconvert $outputps -E100 -Tf -A -Qg4");
            pw.println("gmt psconvert $outputps -E100 -Tg -A -Qg4");
            pw.println("");
            pw.println("#-------- Clear");
            pw.println("rm -rf cp.cpt gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
    }
}
