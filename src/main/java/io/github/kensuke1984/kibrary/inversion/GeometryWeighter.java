package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Weight data based on (bounce point, distance, azimuth)-pair bins.
 *
 * @author otsuru
 * @since 2025/2/23
 */
public class GeometryWeighter extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of the input data entry list file.
     */
    private Path dataEntryPath;
    /**
     * Path of a voxel information file.
     */
    private Path voxelPath;

    private double distanceInterval;
    private double azimuthInterval;
    private boolean expandAzimuth;

    /**
     * Name of structure to use for calculating turning point.
     */
    private String structureName;
    /**
     * Phase to use when computing turning point.
     */
    private String turningPointPhase;

    private LinearRange distanceRange;
    private CircularRange azimuthRange;
    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;

    /**
     * Horizontal positions of center points of voxels.
     */
    private List<HorizontalPosition> voxelPositions;
    private double[] latitudes;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Sac components to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry list file, must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##Path of a voxel information file, when weighting by turning point position.");
            pw.println("#voxelPath voxel.inf");
            pw.println("##(double) The width of each epicentral distance bin [deg]. (5)");
            pw.println("#distanceInterval ");
            pw.println("##(double) The width of each azimuth bin [deg]. (30)");
            pw.println("#azimuthInterval ");
            pw.println("##(boolean) Whether to expand azimuth range to [0:360), not overlapping onto [0:180) range. (false)");
            pw.println("#expandAzimuth ");
            pw.println("##########Parameters for turning point computation##########");
            pw.println("##(String) Name of structure to use for calculating turning point. (prem)");
            pw.println("#structureName ");
            pw.println("##Phase to compute turning point for. (ScS)");
            pw.println("#turningPointPhase ");
            pw.println("##########Parameters for map##########");
            pw.println("##(double) Lower limit of range of epicentral distance to map [deg], inclusive; [0:upperDistance). (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to map [deg], exclusive; (lowerDistance:180] .(180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to map [deg]; [-180:360], inclusive. (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to map [deg]; [-180:360], exclusive. (360)");
            pw.println("#upperAzimuth ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax.");
            pw.println("#mapRegion -180/180/-90/90");
        }
        System.err.println(outPath + " is created.");
    }

    public GeometryWeighter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        if (property.containsKey("voxelPath")) {
            voxelPath = property.parsePath("voxelPath", null, true, workPath);
        }

        distanceInterval = property.parseDouble("distanceInterval", "5");
        azimuthInterval = property.parseDouble("azimuthInterval", "30");
        expandAzimuth = property.parseBoolean("expandAzimuth", "false");

        structureName = property.parseString("structureName", "prem");
        turningPointPhase = property.parseString("turningPointPhase", "ScS");

        double lowerDistance = property.parseDouble("lowerDistance", "0");
        double upperDistance = property.parseDouble("upperDistance", "180");
        distanceRange = new LinearRange("Distance", lowerDistance, upperDistance, 0.0, 180.0);
        double lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        double upperAzimuth = property.parseDouble("upperAzimuth", "360");
        azimuthRange = new CircularRange("Azimuth", lowerAzimuth, upperAzimuth, -180.0, 360.0);
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());

        // read voxel information
        if (voxelPath != null) {
            VoxelInformationFile vif = new VoxelInformationFile(voxelPath);
            voxelPositions = vif.getHorizontalPositions();
            latitudes = voxelPositions.stream().mapToDouble(HorizontalPosition::getLatitude).distinct().sorted().toArray();
        }

        // compute turning point azimuth using TauPPierce
        TauPPierceWrapper pierceTool = null;
        try {
            pierceTool = new TauPPierceWrapper(structureName, turningPointPhase);
            pierceTool.compute(entrySet);
        } catch (TauModelException e) {
            throw new RuntimeException(e);
        }

        // set up bins and counters
        int nDistanceBin = (int) MathAid.ceil(360 / distanceInterval);
        int azimuthDomainWidth = expandAzimuth ? 360 : 180;
        int nAzimuthBin = (int) MathAid.ceil(azimuthDomainWidth / azimuthInterval);
        int nVoxelBin = (voxelPositions != null ? voxelPositions.size() : 1);
        int[][][] numberOfRecords = new int[nDistanceBin][nAzimuthBin][nVoxelBin];
        Map<DataEntry, Integer> iDistanceMap = new HashMap<>();
        Map<DataEntry, Integer> iAzimuthMap = new HashMap<>();
        Map<DataEntry, Integer> iVoxelMap = new HashMap<>();

        // count number of records in each bin
        for (DataEntry entry : entrySet) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            double epicentralDistance = eventPosition.computeEpicentralDistanceDeg(observerPosition);

            double azimuth;
            HorizontalPosition turnPosition;
            if (pierceTool.hasRaypaths(entry)) {
                // When there are several raypaths for a given phase name, the first arrival is chosen.
                // When there are multiple bottoming points for a raypath, the first one is used.
                // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                azimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
                turnPosition = pierceTool.get(entry, 0).findTurningPoint(0);
            } else {
                System.err.println("Cannot compute turning point for " + entry + ", skipping.");
                continue;
            }
            if (!expandAzimuth && azimuth > 180) azimuth -= 180;

            int iDistance = (int) (epicentralDistance / distanceInterval);
            int iAzimuth = (int) (azimuth / azimuthInterval);
            int iVoxel = (voxelPositions != null ? findIVoxel(turnPosition) : 0);
            numberOfRecords[iDistance][iAzimuth][iVoxel]++;

            iDistanceMap.put(entry, iDistance);
            iAzimuthMap.put(entry, iAzimuth);
            iVoxelMap.put(entry, iVoxel);
        }

        // decide weights
        double[][][] weights = decideWeights(numberOfRecords);
        Map<DataEntry, Double> weightMap = new HashMap<>();
        for (DataEntry entry : entrySet) {
            if (!iDistanceMap.containsKey(entry) || !iAzimuthMap.containsKey(entry) || !iVoxelMap.containsKey(entry)) {
                // If raypath with no bin information exists, weights cannot be computed.
                throw new IllegalStateException("No information for " + entry + ".");
            }
            double weight = weights[iDistanceMap.get(entry)][iAzimuthMap.get(entry)][iVoxelMap.get(entry)];
            weightMap.put(entry, weight);
        }

        // output
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "geometry", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("geometryHistogram.txt");
        Path geometryScriptPath = outPath.resolve("geometryHistogram.sh");
        Path weightedScriptPath = outPath.resolve("weightedHistogram.sh");
        Path weightPath = outPath.resolve("entryWeight.lst");
        writeHistogramData(txtPath, distanceInterval, azimuthInterval, numberOfRecords, weights);
        createMapScript(geometryScriptPath, txtPath.getFileName(), nDistanceBin, nAzimuthBin, false);
        createMapScript(weightedScriptPath, txtPath.getFileName(), nDistanceBin, nAzimuthBin, true);
        EntryWeightListFile.write(weightMap, weightPath);
    }

    private int findIVoxel(HorizontalPosition turnPosition) {
        // decide latitude
        double latitude = findClosestValue(turnPosition.getLatitude(), latitudes);

        // extract longitudes on that latitude
        double[] longitudes = voxelPositions.stream().filter(pos -> pos.getLatitude() == latitude)
                .mapToDouble(HorizontalPosition::getLongitude).distinct().sorted().toArray();
        // decide longitude
        double longitude = findClosestValue(turnPosition.getLongitude(), longitudes);

        // find index of voxel position
        HorizontalPosition position = new HorizontalPosition(latitude, longitude);
        return voxelPositions.indexOf(position);
    }

    private static double findClosestValue(double x, double[] values) {
        double tmpValue = values[0];
        for (double value : values) {
            if (Math.abs(value - x) < Math.abs(tmpValue - x)) tmpValue = value;
        }
        return tmpValue;
    }

    private void writeHistogramData(Path txtPath, double distanceInterval, double azimuthInterval,
            int[][][] numberOfRecords, double[][][] weights) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                for (int j = 0; j < numberOfRecords[i].length; j++) {
                    double distance = i * distanceInterval;
                    double azimuth = j * azimuthInterval;

                    if (distanceRange.check(distance) && azimuthRange.check(azimuth)) {
                        for (int k = 0; k < numberOfRecords[i][j].length; k++) {
                            pw.println(String.format("%.2f %.2f %.2f %.2f %d %.1f", distance, azimuth,
                                    voxelPositions.get(k).getLatitude(), voxelPositions.get(k).getLongitude(),
                                    numberOfRecords[i][j][k], numberOfRecords[i][j][k] * weights[i][j][k]));
                        }
                    }

                }
            }
        }
    }

    private static double[][][] decideWeights(int[][][] numberOfRecords) {
        double[][][] weights = new double[numberOfRecords.length][numberOfRecords[0].length][numberOfRecords[0][0].length];

        // average number of records in each bin (only bins with at least 1 record are considered)
        int sum = 0;
        int nBin = 0;
        for (int i = 0; i < numberOfRecords.length; i++) {
            for (int j = 0; j < numberOfRecords[i].length; j++) {
                for (int k = 0; k < numberOfRecords[i][j].length; k++) {

                    if (numberOfRecords[i][j][k] > 0) {
                        sum += numberOfRecords[i][j][k];
                        nBin++;
                    }

                }
            }
        }
        double average = ((double) sum) / nBin;

        // weight for each bin
        for (int i = 0; i < numberOfRecords.length; i++) {
            for (int j = 0; j < numberOfRecords[i].length; j++) {
                for (int k = 0; k < numberOfRecords[i][j].length; k++) {

                    if (numberOfRecords[i][j][k] > 0) {
                        double x = numberOfRecords[i][j][k] / average;
                        double weight = (1.0 - Math.exp(-3.0 * x)) / (1.0 - Math.exp(-3.0)) / x;
                        weights[i][j][k] = Precision.round(weight, 3);
                    } else {
                        weights[i][j][k] = 0.0;
                    }

                }
            }
        }
        return weights;
    }

    private void createMapScript(Path scriptPath, Path txtPath, int nDistanceBin, int nAzimuthBin, boolean weighted) throws IOException {
        int nRow = 0;
        double[] distanceBounds = new double[nDistanceBin + 1];
        for (int i = 0; i < nDistanceBin; i++) {
            double distance = i * distanceInterval;
            if (distanceRange.check(distance)) {
                if (nRow == 0) distanceBounds[0] = Precision.round(distance, 2);
                nRow++;
                distanceBounds[nRow] = Precision.round(distance + distanceInterval, 2);
            }
        }
        int nColumn = 0;
        double[] azimuthBounds = new double[nAzimuthBin + 1];
        for (int j = 0; j < nAzimuthBin; j++) {
            double azimuth = j * azimuthInterval;
            if (azimuthRange.check(azimuth)) {
                if (nColumn == 0) azimuthBounds[0] = Precision.round(azimuth, 2);
                nColumn++;
                azimuthBounds[nColumn] = Precision.round(azimuth + azimuthInterval, 2);
            }
        }

        // decide map region
        if (mapRegion == null) mapRegion = decideMapRegion(voxelPositions);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("#------- GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set COLOR_BACKGROUND white");
            pw.println("gmt set COLOR_FOREGROUND darkred");
            pw.println("gmt set PS_MEDIA 1500x1500");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_FRAME_TYPE plain");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 30");
            pw.println("");
            pw.println("#------- Map parameters");
            pw.println("R='-R" + mapRegion + "'");
            pw.println("J='-JQ10'");
            pw.println("B='-Blrbt'");
            pw.println("SIZE=0.5");
            pw.println("");
            pw.println("#------- Color palette");
            pw.println("gmt makecpt -Q -Cturbo -G0.15/0.95 -T0/4 > cp.cpt");
            pw.println("");

            pw.println("#------- Begin main plot");
            pw.println("gmt begin " + scriptPath.getFileName().toString().replace(".sh", "") + " eps,pdf,png");
            pw.println("");
            pw.println("#------- Panels");
            pw.println("gmt subplot begin " + nRow + "x" + nColumn + " -Fs10/0 -M0/0 -Y10 $B $J $R");
            pw.println("");

            int iBlock = 0;
            for (int i = 0; i < nRow; i++) {
                for (int j = 0; j < nColumn; j++) {
                    iBlock++;
                    int startLine = voxelPositions.size() * (iBlock - 1) + 1;
                    int endLine = voxelPositions.size() * iBlock;
                    int nZ = weighted ? 6 : 5;
                    pw.println("gmt subplot set");
                    pw.println("  gmt pscoast -Wthinner,black -A500");
                    pw.println("  cat " + txtPath + " | sed -n " + startLine + "," + endLine + "p | \\");
                    pw.println("  awk '{print $3, $4, $" + nZ + "}' | \\");
                    pw.println("  gmt psxy -: -Ss$SIZE -Ccp.cpt -Wthinnest");

                    if (i == 0 && j == 0) {
                        pw.println("  echo " + MathAid.simplestString(azimuthBounds[0]) + " | gmt pstext -N -F+cTL+jBC -D0/5p");
                        pw.println("  echo " + MathAid.simplestString(distanceBounds[0]) + " | gmt pstext -N -F+cTL+jMR -D-5p/0");
                    }
                    if (i == 0) {
                        pw.println("  echo " + MathAid.simplestString(azimuthBounds[j + 1]) + " | gmt pstext -N -F+cTR+jBC -D0/5p");
                    }
                    if (j == 0) {
                        pw.println("  echo " + MathAid.simplestString(distanceBounds[i + 1]) + " | gmt pstext -N -F+cBL+jMR -D-5p/0");
                    }
                }
            }
            pw.println("gmt subplot end");
            pw.println("");
            pw.println("#------- Scale");
            pw.println("  echo \"Turning point azimuth (@.)\" | gmt pstext -N -F+cTC+jBC -D0/50p");
            pw.println("  echo \"Epicentral distance (@.)\" | gmt pstext -N -F+cML+jBC+a90 -D-50p/0");
            pw.println("gmt psscale -Ccp.cpt -DJCB+w15/0.6+h+e -Q -B+l\"# time window\"");
            pw.println("");
            pw.println("#------- Finalize");
            pw.println("gmt end");
            pw.println("");

            pw.println("#-------- Clear");
            pw.println("rm -rf gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
    }

    /**
     * Decides a rectangular region of a map that is sufficient to map all given positions.
     * @param positions (Set of {@link HorizontalPosition}) Positions that need to be included in map region.
     * @return (String) Rectangular region in form "lonMin/lonMax/latMin/latMax".
     */
    private static String decideMapRegion(List<? extends HorizontalPosition> positions) {
        if (positions.size() == 0) throw new IllegalArgumentException("No positions are given");
        // whether to use [0:360) instead of [-180:180)
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        // map to latitude and longitude values
        double[] latitudes = positions.stream().mapToDouble(HorizontalPosition::getLatitude).toArray();
        double[] longitudes = positions.stream().mapToDouble(pos -> pos.getLongitude(crossDateLine)).toArray();
        // find min and max latitude and longitude
        double latMin = Arrays.stream(latitudes).min().getAsDouble();
        double latMax = Arrays.stream(latitudes).max().getAsDouble();
        double lonMin = Arrays.stream(longitudes).min().getAsDouble();
        double lonMax = Arrays.stream(longitudes).max().getAsDouble();
        // expand the region a bit more
        double mapRim = HorizontalPosition.findLatitudeInterval(positions);
        latMin = MathAid.floor(latMin - mapRim);
        latMax = MathAid.ceil(latMax + mapRim);
        lonMin = MathAid.floor(lonMin - mapRim);
        lonMax = MathAid.ceil(lonMax + mapRim);
        if (latMin < -90) latMin = -90;
        if (latMax > 90) latMax = 90;
        // return as String
        return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
    }

}
