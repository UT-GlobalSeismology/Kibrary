package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to create cross sections.
 *
 * @author otsuru
 * @since 2023/6/10 Separated from visual.map.CrossSectionCreator.
 */
public class CrossSectionWorker {

    /**
     * How much finer to make the grid.
     */
    public static final int GRID_SMOOTHING_FACTOR = 2;
    /**
     * Size of vertical grid with respect to horizontal grid.
     */
    public static final int VERTICAL_ENLARGE_FACTOR = 4;

    private final Map<Double, HorizontalPosition> samplePositionMap = new TreeMap<>();
    private final double distance;
    private final double horizontalGridInterval;
    private final double verticalGridInterval;
    private final double[] radii;
    private final double meanRadius;

    private final double marginLatitudeDeg;
    private final double marginLongitudeRaw;
    private final boolean setMarginLongitudeByKm;
    private final double marginRadius;

    /**
     * Radius of zero point of vertical axis.
     */
    private final double zeroPointRadius;
    /**
     * Name of zero point of vertical axis. (ex. "CMB")
     */
    private final String zeroPointName;
    /**
     * Whether to flip vertical axis.
     */
    private final boolean flipVerticalAxis;

    private final double scale;
    /**
     * Whether to display map as mosaic without smoothing.
     */
    private final boolean mosaic;
    private final VariableType variable;
    private final ScalarType scalarType;
    private final String tag;
    private final Set<FullPosition> discretePositions;

    private final String plotFileNameRoot;
    private final String scalarFileName;


    private boolean maskExists = false;
    private double maskThreshold;
    private String maskFileName;


    /**
     * Set parameters that should be used when creating cross sections.
     * @param pos0Latitude (double) Latitude of position 0.
     * @param pos0Longitude (double) Longitude of position 0.
     * @param pos1Latitude (double) Latitude of position 1.
     * @param pos1Longitude (double) Longitude of position 1.
     * @param beforePos0Deg (double) Distance of the starting point along arc before position 0.
     * @param afterPosDeg (double) Distance of the ending point along arc after either position 0 or position 1.
     * @param useAfterPos1 (double) Whether the ending point should be decided with respect to position 0 or position 1.
     * @param zeroPointRadius (double) Radius of zero point of vertical axis.
     * @param zeroPointName (String) Name of zero point of vertical axis. (ex. "CMB")
     * @param flipVerticalAxis (boolean) Whether to flip vertical axis.
     * @param marginLatitudeRaw (double) Latitude margin at both ends of region.
     * @param setMarginLatitudeByKm (boolean) Whether marginLatitudeRaw is set in [km] or [deg].
     * @param marginLongitudeRaw (double) Longitude margin at both ends of region.
     * @param setMarginLongitudeByKm (boolean) Whether marginLongitudeRaw is set in [km] or [deg].
     * @param marginRadius (double) Radius margin at both ends of region [km].
     * @param scale (double) Scale of contours.
     * @param mosaic (boolean) Whether to display map as mosaic without smoothing.
     * @param variable ({@link VariableType}) Variable that the cross section is for.
     * @param scalarType ({@link ScalarType}) Scalar type that the cross section is for.
     * @param tag (String) Tag in scalar file names.
     * @param discretePositions (Set of {@link FullPosition}) Positions where input model is defined.
     */
    CrossSectionWorker(double pos0Latitude, double pos0Longitude, double pos1Latitude, double pos1Longitude,
            double beforePos0Deg, double afterPosDeg, boolean useAfterPos1, double zeroPointRadius,
            String zeroPointName, boolean flipVerticalAxis, double marginLatitudeRaw, boolean setMarginLatitudeByKm,
            double marginLongitudeRaw, boolean setMarginLongitudeByKm, double marginRadius, double scale,
            boolean mosaic, VariableType variable, ScalarType scalarType, String tag, Set<FullPosition> discretePositions) {

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
        distance = Math.round(startPosition.computeEpicentralDistanceDeg(endPosition));
        double azimuth = startPosition.computeAzimuthDeg(endPosition);
        horizontalGridInterval = ScalarMapShellscript.decideGridSampling(discretePositions) / GRID_SMOOTHING_FACTOR;
        int nSamplePosition = (int) Math.round(distance / horizontalGridInterval) + 1;
        for (int i = 0; i < nSamplePosition; i++) {
            HorizontalPosition position = startPosition.pointAlongAzimuth(azimuth, i * horizontalGridInterval);
            samplePositionMap.put(i * horizontalGridInterval, position);
        }

        // decide vertical settings
        verticalGridInterval = horizontalGridInterval * VERTICAL_ENLARGE_FACTOR;
        radii = discretePositions.stream().mapToDouble(FullPosition::getR).distinct().sorted().toArray();

        // decide margins
        meanRadius = Arrays.stream(radii).average().getAsDouble();
        this.marginLatitudeDeg = setMarginLatitudeByKm ? Math.toDegrees(marginLatitudeRaw / meanRadius) : marginLatitudeRaw;
        this.marginLongitudeRaw = marginLongitudeRaw;
        this.setMarginLongitudeByKm =setMarginLongitudeByKm;
        this.marginRadius = marginRadius;

        // other settings
        this.zeroPointRadius = zeroPointRadius;
        this.zeroPointName = zeroPointName;
        this.flipVerticalAxis = flipVerticalAxis;
        this.scale = scale;
        this.mosaic = mosaic;
        this.variable = variable;
        this.scalarType = scalarType;
        this.tag = tag;
        this.discretePositions = discretePositions;

        // set file name root of output files
        this.plotFileNameRoot = variable.toString().toLowerCase() + scalarType.toNaturalString() + ((tag != null) ? ("_" + tag + "_") : "");
        // set scalar file name
        String tag1 = (tag != null) ? (tag + "_XZ") : "XZ";
        this.scalarFileName = ScalarListFile.generateFileName(variable, scalarType, tag1);
    }

    /**
     * Set mask.
     * @param maskVariable ({@link VariableType}) Variable of mask.
     * @param maskScalarType ({@link ScalarType}) Scalar type of mask.
     * @param maskThreshold (double) Threshold for mask.
     */
    void setMask(VariableType maskVariable, ScalarType maskScalarType, double maskThreshold) {
        this.maskExists = true;
        this.maskThreshold = maskThreshold;
        // set scalar file name
        String tag2 = (tag != null) ? (tag + "_forMaskXZ") : "forMaskXZ";
        this.maskFileName = ScalarListFile.generateFileName(maskVariable, maskScalarType, tag2);
    }

    /**
     * Compute and output the data for cross section.
     * @param discreteMap (Map of {@link FullPosition}, Double) Values of perturbations of the model that is to be mapped.
     * @param maskDiscreteMap (Map of {@link FullPosition}, Double) Values of perturbations of the model that is to be used as a mask.
     * @param outPath (Path) Output folder.
     * @throws IOException
     */
    void computeCrossSection(Map<FullPosition, Double> discreteMap, Map<FullPosition, Double> maskDiscreteMap, Path outPath) throws IOException {
        if (discreteMap.keySet().stream().anyMatch(pos -> !discretePositions.contains(pos))) {
            throw new IllegalArgumentException("discreteMap contains illegal positions.");
        }
        if (maskExists) {
            if (maskDiscreteMap == null) {
                throw new IllegalArgumentException("maskDiscreteMap must be set.");
            }
            if (maskDiscreteMap.keySet().stream().anyMatch(pos -> !discretePositions.contains(pos))) {
                throw new IllegalArgumentException("maskDiscreteMap contains illegal positions.");
            }
        }

        // compute cross section data and output
        computeCrossSectionData(discreteMap, radii, samplePositionMap, verticalGridInterval, outPath.resolve(scalarFileName));
        if (maskExists) {
            computeCrossSectionData(maskDiscreteMap, radii, samplePositionMap, verticalGridInterval, outPath.resolve(maskFileName));
        }
    }

    private void computeCrossSectionData(Map<FullPosition, Double> discreteMap, double[] radii, Map<Double, HorizontalPosition> samplePositionMap,
            double verticalGridInterval, Path outputPath) throws IOException {
        //~for each radius and latitude, resample values at sampleLongitudes
        boolean crossDateLine = HorizontalPosition.crossesDateLine(samplePositionMap.values());
        double[] sampleLongitudes = samplePositionMap.values().stream().mapToDouble(pos -> pos.getLongitude(crossDateLine))
                .distinct().sorted().toArray();
        Map<FullPosition, Double> resampledMap = Interpolation.inEachWestEastLine(discreteMap, sampleLongitudes,
                marginLongitudeRaw, setMarginLongitudeByKm, meanRadius, crossDateLine, mosaic);
        Set<FullPosition> resampledPositions = resampledMap.keySet();

        //~compute sampled trace at each sample point
        Map<Double, Trace> sampledTraceMap = Collections.synchronizedMap(new TreeMap<>());
        samplePositionMap.entrySet().parallelStream().forEach(sampleEntry -> {
            double sampleLatitude = sampleEntry.getValue().getLatitude();
            double sampleLongitude = sampleEntry.getValue().getLongitude();

            //~extract continuous sequence of latitudes in this meridian that will be used for interpolation
            Set<FullPosition> positionsInMeridian = resampledPositions.stream()
                    .filter(pos -> Precision.equals(pos.getLongitude(), sampleLongitude, HorizontalPosition.LONGITUDE_EPSILON))
                    .collect(Collectors.toSet());
            if (positionsInMeridian.size() == 0) return;
            double[] latitudesInMeridian = positionsInMeridian.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();
            double[] latitudesExtracted = extractContinuousLatitudeSequence(latitudesInMeridian, sampleLatitude, marginLatitudeDeg);
            // skip this sample point if sampleLatitude is not included in a latitude sequence (thus cannot be interpolated)
            if (latitudesExtracted == null) return;

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
        });

        outputData(samplePositionMap, sampledTraceMap, outputPath);
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
        for (double[] sequence : sequenceList) {
            if (sequence[0] - margin <= targetLatitude && targetLatitude < sequence[sequence.length - 1] + margin)
                return sequence;
        }
        // if targetLatitude is not in any sequence, return null
        return null;
    }

    private static void outputData(Map<Double, HorizontalPosition> samplePositionMap, Map<Double, Trace> sampleTraceMap,
            Path outputPath) throws IOException {
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
     * Writes shellscript, color palette, and annotation file.
     * @param scaleLabel (String) Label of scale that should be displayed.
     *  Special characters in shellscript ($, `, \) must be escaped with a backslash, which itself should be escaped (ex. $ -> \\$, \ -> \\\\).
     * @param outPath (Path) Folder where output files should be created.
     * @throws IOException
     */
    void writeScripts(Path outPath) throws IOException {
        Path cpMasterPath = outPath.resolve("cp_master.cpt");
        Path cpMaskPath = outPath.resolve("cp_mask.cpt");
        Path annotationPath = outPath.resolve("rAnnotation.txt");
        Path gmtPath = outPath.resolve(plotFileNameRoot + "Section.sh");

        ScalarMapShellscript.writeCpMaster(cpMasterPath);
        if (maskExists) {
            ScalarMapShellscript.writeCpMask(cpMaskPath, maskThreshold);
        }

        double lowerRadius = radii[0] - marginRadius;
        double upperRadius = radii[radii.length - 1] + marginRadius;
        double[] annotationRadii = {lowerRadius, upperRadius};
        writeAnnotationFile(annotationRadii, annotationPath);
        writeShellscript(distance, lowerRadius, upperRadius, horizontalGridInterval, verticalGridInterval, gmtPath);
    }

    /**
     * Write annotation file. This file specifies the annotations and ticks on the vertical axis of the figure.
     * @see <a href=https://docs.generic-mapping-tools.org/dev/cookbook/options.html#custom-axes>GMT manual</a>
     *
     * @param radii (double[]) Array of radii where annotations should be written.
     * @param outputPath (Path) Output file.
     * @throws IOException
     */
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

    private void writeShellscript(double sectionDistance, double lowerRadius, double upperRadius,
            double horizontalGridInterval, double verticalGridInterval, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("# create grid");
            pw.println("cat " + scalarFileName + " | \\");
            pw.println("awk '{print $1,$4,$5}' | \\");
            pw.println("gmt xyz2grd -G0model.grd -R0/" + MathAid.simplestString(sectionDistance)
                    + "/" + MathAid.simplestString(lowerRadius) + "/" + MathAid.simplestString(upperRadius)
                    + " -I" + MathAid.simplestString(horizontalGridInterval) + "/" + MathAid.simplestString(verticalGridInterval) + " -di0");
            if (maskExists) {
                pw.println("cat " + maskFileName + " | \\");
                pw.println("awk '{print $1,$4,$5}' | \\");
                pw.println("gmt xyz2grd -G0mask.grd -R0/" + MathAid.simplestString(sectionDistance)
                        + "/" + MathAid.simplestString(lowerRadius) + "/" + MathAid.simplestString(upperRadius)
                        + " -I" + MathAid.simplestString(horizontalGridInterval) + "/" + MathAid.simplestString(verticalGridInterval) + " -di0");
            }
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
            pw.println("outputps=" + plotFileNameRoot + "Section.eps");
            pw.println("MP=" + scale);
            pw.println("gmt makecpt -Ccp_master.cpt -T-$MP/$MP > cp.cpt");
            pw.println("");
            pw.println("#------- Panels");
            pw.println("gmt grdimage 0model.grd $B $J $R -Ccp.cpt -K -Y80 -X20> $outputps");
            if (maskExists) {
                pw.println("gmt grdimage 0mask.grd $J $R -Ccp_mask.cpt -G0/0/0 -t80 -K -O >> $outputps");
            }
            pw.println("");
            pw.println("#------- Scale");
            pw.println("gmt psscale -Ccp.cpt -Dx2/-4+w12/0.8+h -B$MP+l\"" + ScalarType.createScaleLabel(variable, scalarType)
                    + "\" -K -O -Y2 -X5 >> $outputps");
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

    String getPlotFileNameRoot() {
        return plotFileNameRoot;
    }

}
