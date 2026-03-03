package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.RecordEntry;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Weight data based on (turning point, distance, azimuth)-pair bins.
 * <b>
 * For turning point position and azimuth,
 * the first turning point of the phase that arrives first among the specified phases will be used.
 * Mid-points of diffraction are counted as turning points.
 *
 * @author otsuru
 * @since 2025/2/23
 */
public class GeometryWeighter extends Operation {
    private static final int DECIMALS = 3;

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
     * Phases to use.
     */
    private Phase[] phases;

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
     * Name of structure to use for computing turning points.
     */
    private String structureName;
    /**
     * Phases to use when computing turning points.
     */
    private String[] turningPointPhases;

    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;
    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;

    /**
     * Horizontal positions of center points of voxels.
     */
    private List<HorizontalPosition> voxelPositions;
    private double[] latitudes;

    private int nPlotDistance;
    private int nPlotAzimuth;
    private double[] plotDistanceBounds;
    private double[] plotAzimuthBounds;
    private int[] plotDistanceIndices;
    private int[] plotAzimuthIndices;

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
            pw.println("##(String) Name of structure to use for computing turning points. (prem)");
            pw.println("#structureName ");
            pw.println("##Phases to compute turning points for, listed using spaces. (ScS)");
            pw.println("#turningPointPhases ");
            pw.println("##########Parameters for map##########");
            pw.println("##(double) Lower limit of range of epicentral distance to map [deg], inclusive; [0:upperDistance). (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to map [deg], exclusive; (lowerDistance:180] .(180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to map [deg]; [-180:360], inclusive. (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to map [deg]; [-180:360], exclusive. (180)");
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
        turningPointPhases = property.parseStringArray("turningPointPhases", "ScS");

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        LinearRange.checkValidity("Distance", lowerDistance, upperDistance, 0.0, 180.0);
        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "180");
        LinearRange.checkValidity("Azimuth", lowerAzimuth, upperAzimuth, -180.0, 360.0);
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> dataEntrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());

        Set<RecordEntry> entrySet = new HashSet<>();
        for (DataEntry dataEntry : dataEntrySet) {
            entrySet.add(new RecordEntry(dataEntry.getEvent(), dataEntry.getObserver(), dataEntry.getComponent(), phases));
        }

        // read voxel information
        if (voxelPath != null) {
            VoxelInformationFile vif = new VoxelInformationFile(voxelPath);
            voxelPositions = vif.getHorizontalPositions();
            latitudes = voxelPositions.stream().mapToDouble(HorizontalPosition::getLatitude).distinct().sorted().toArray();
        } else {
            voxelPositions = new ArrayList<>();
            voxelPositions.add(new HorizontalPosition(0, 0));
        }

        // compute turning point azimuth using TauPPierce
        TauPPierceWrapper pierceTool = null;
        try {
            pierceTool = new TauPPierceWrapper(structureName, turningPointPhases);
            pierceTool.compute(dataEntrySet);
        } catch (TauModelException e) {
            throw new RuntimeException(e);
        }

        // set up bins and counters
        int nDistanceBin = (int) MathAid.ceil(360 / distanceInterval);
        int azimuthDomainWidth = expandAzimuth ? 360 : 180;
        int nAzimuthBin = (int) MathAid.ceil(azimuthDomainWidth / azimuthInterval);
        int nVoxelBin = (voxelPath != null ? voxelPositions.size() : 1);
        int[][][] numberOfRecords = new int[nDistanceBin][nAzimuthBin][nVoxelBin];
        Map<RecordEntry, Integer> iDistanceMap = new HashMap<>();
        Map<RecordEntry, Integer> iAzimuthMap = new HashMap<>();
        Map<RecordEntry, Integer> iVoxelMap = new HashMap<>();

        // count number of records in each bin
        for (DataEntry dataEntry : dataEntrySet) {
            FullPosition eventPosition = dataEntry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = dataEntry.getObserver().getPosition();
            double epicentralDistance = eventPosition.computeEpicentralDistanceDeg(observerPosition);

            double azimuth;
            HorizontalPosition turnPosition;
            if (pierceTool.hasRaypaths(dataEntry)) {
                // When there are several raypaths for a given phase name, the first arrival is chosen.
                // When there are multiple bottoming points for a raypath, the first one is used.
                // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                azimuth = pierceTool.get(dataEntry, 0).computeTurningAzimuthDeg(0);
                turnPosition = pierceTool.get(dataEntry, 0).findTurningPoint(0, true, true, false, false);
            } else {
                System.err.println("Cannot compute turning point for " + dataEntry + ", skipping.");
                continue;
            }
            if (!expandAzimuth && azimuth > 180) azimuth -= 180;

            int iDistance = (int) (epicentralDistance / distanceInterval);
            int iAzimuth = (int) (azimuth / azimuthInterval);
            int iVoxel = (voxelPath != null ? findIVoxel(turnPosition) : 0);
            numberOfRecords[iDistance][iAzimuth][iVoxel]++;

            RecordEntry entry = new RecordEntry(dataEntry.getEvent(), dataEntry.getObserver(), dataEntry.getComponent(), phases);
            iDistanceMap.put(entry, iDistance);
            iAzimuthMap.put(entry, iAzimuth);
            iVoxelMap.put(entry, iVoxel);
        }

        // decide weights
        double[][][] weights = decideWeights(numberOfRecords);
        Map<RecordEntry, Double> weightMap = new HashMap<>();
        for (RecordEntry entry : entrySet) {
            if (!iDistanceMap.containsKey(entry) || !iAzimuthMap.containsKey(entry) || !iVoxelMap.containsKey(entry)) {
                // If raypath with no bin information exists, weights cannot be computed.
                throw new IllegalStateException("No information for " + entry + ".");
            }
            double weight = weights[iDistanceMap.get(entry)][iAzimuthMap.get(entry)][iVoxelMap.get(entry)];
            weightMap.put(entry, weight);
        }

        // output
        defineBounds(nDistanceBin, nAzimuthBin, azimuthDomainWidth);
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "geometry", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("geometryHistogram.txt");
        Path geometryScriptPath = outPath.resolve("geometryHistogram.sh");
        Path weightedScriptPath = outPath.resolve("weightedHistogram.sh");
        Path weightPath = outPath.resolve("entryWeight.lst");
        writeHistogramData(txtPath, numberOfRecords, weights);
        createMapScript(geometryScriptPath, txtPath.getFileName(), false);
        createMapScript(weightedScriptPath, txtPath.getFileName(), true);
        EntryWeightListFile.write(weightMap, weightPath);

        System.err.println("To plot maps, please enter " + outPath
                + "/ and run " + geometryScriptPath.getFileName() + " and " + weightedScriptPath.getFileName());
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
                        weights[i][j][k] = Precision.round(weight, DECIMALS);
                    } else {
                        weights[i][j][k] = 0.0;
                    }

                }
            }
        }
        return weights;
    }

    private void defineBounds(int nDistanceBin, int nAzimuthBin, int azimuthDomainWidth) {
        nPlotDistance = 0;
        nPlotAzimuth = 0;
        plotDistanceBounds = new double[nDistanceBin + 1];
        plotAzimuthBounds = new double[nAzimuthBin + 1];
        plotDistanceIndices = new int[nDistanceBin];
        plotAzimuthIndices = new int[nAzimuthBin];

        // record all distance bins needed in the plot
        for (int i = 0; i < nDistanceBin; i++) {
            double distance = Precision.round(i * distanceInterval, DECIMALS);
            double nextDistance = Precision.round(distance + distanceInterval, DECIMALS);
            if (lowerDistance < nextDistance && distance < upperDistance) {
                plotDistanceIndices[nPlotDistance] = i;
                if (nPlotDistance == 0) plotDistanceBounds[0] = distance;
                nPlotDistance++;
                plotDistanceBounds[nPlotDistance] = nextDistance;
            }
        }

        // figure out which domain the minimum and maximum azimuths are in
        int minLoop = (int) MathAid.floor(lowerAzimuth / azimuthDomainWidth);
        int maxLoop = (int) MathAid.ceil(upperAzimuth / azimuthDomainWidth) - 1;
        // record all azimuth bins needed in the plot
        for (int loop = minLoop; loop <= maxLoop; loop++) {
            for (int j = 0; j < nAzimuthBin; j++) {
                double azimuth = Precision.round(j * azimuthInterval + loop * azimuthDomainWidth, DECIMALS);
                double nextAzimuth = Precision.round(azimuth + azimuthInterval, DECIMALS);
                if (lowerAzimuth < nextAzimuth && azimuth < upperAzimuth) {
                    plotAzimuthIndices[nPlotAzimuth] = j;
                    if (nPlotAzimuth == 0) plotAzimuthBounds[0] = azimuth;
                    nPlotAzimuth++;
                    plotAzimuthBounds[nPlotAzimuth] = nextAzimuth;
                }
                if (nPlotAzimuth == nAzimuthBin) break;
            }
            if (nPlotAzimuth == nAzimuthBin) break;
        }
    }

    private void writeHistogramData(Path txtPath, int[][][] numberOfRecords, double[][][] weights) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int p = 0; p < nPlotDistance; p++) {
                int i = plotDistanceIndices[p];
                for (int q = 0; q < nPlotAzimuth; q++) {
                    int j = plotAzimuthIndices[q];

                    for (int k = 0; k < voxelPositions.size(); k++) {
                        pw.println(MathAid.padToString(plotDistanceBounds[p], 3, DECIMALS, false) + " "
                                + MathAid.padToString(plotAzimuthBounds[q], 4, DECIMALS, false) + " "
                                + voxelPositions.get(k).toString() + " "
                                + numberOfRecords[i][j][k] + " " + (numberOfRecords[i][j][k] * weights[i][j][k]));
                    }

                }
            }
        }
    }

    private void createMapScript(Path scriptPath, Path txtPath, boolean weighted) throws IOException {
        // decide map region
        if (mapRegion == null) {
            if (voxelPath != null) {
                mapRegion = decideMapRegion(voxelPositions);
            } else {
                mapRegion = "-1/1/-1/1";
            }
        }

        // decide panel width
        int panelWidth = (int) Math.ceil(40.0 / nPlotAzimuth);

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
            pw.println("WIDTH=" + panelWidth);
            pw.println("SIZE=" + (panelWidth / 20.0));
            pw.println("R='-R" + mapRegion + "'");
            pw.println("J=\"-JQ$WIDTH\"");
            pw.println("B='-Blrbt'");
            pw.println("");
            pw.println("#------- Color palette");
            pw.println("gmt makecpt -Q -Cturbo -G0.15/0.95 -T0/4 > cp.cpt");
            pw.println("");

            pw.println("#------- Begin main plot");
            pw.println("gmt begin " + scriptPath.getFileName().toString().replace(".sh", "") + " eps,pdf,png");
            pw.println("");
            pw.println("#------- Panels");
            pw.println("gmt subplot begin " + nPlotDistance + "x" + nPlotAzimuth + " -Fs$WIDTH/0 -M0/0 -X10 -Y10 $B $J $R");
            pw.println("");

            int iBlock = 0;
            for (int p = 0; p < nPlotDistance; p++) {
                for (int q = 0; q < nPlotAzimuth; q++) {
                    iBlock++;
                    int startLine = voxelPositions.size() * (iBlock - 1) + 1;
                    int endLine = voxelPositions.size() * iBlock;
                    int nZ = weighted ? 6 : 5;
                    pw.println("gmt subplot set");
                    pw.println("  gmt pscoast -Wthinner,black -A500");
                    pw.println("  cat " + txtPath + " | sed -n " + startLine + "," + endLine + "p | \\");
                    pw.println("  awk '{print $3, $4, $" + nZ + "}' | \\");
                    pw.println("  gmt psxy -: -Ss$SIZE -Ccp.cpt -Wthinnest");

                    if (p == 0 && q == 0) {
                        pw.println("  echo " + MathAid.simplestString(plotAzimuthBounds[0]) + " | gmt pstext -N -F+cTL+jBL -D0/5p");
                        pw.println("  echo " + MathAid.simplestString(plotDistanceBounds[0]) + " | gmt pstext -N -F+cTL+jTR -D-5p/0");
                    }
                    if (p == 0) {
                        pw.println("  echo " + MathAid.simplestString(plotAzimuthBounds[q + 1]) + " | gmt pstext -N -F+cTR+jBC -D0/5p");
                    }
                    if (q == 0) {
                        pw.println("  echo " + MathAid.simplestString(plotDistanceBounds[p + 1]) + " | gmt pstext -N -F+cBL+jMR -D-5p/0");
                    }
                }
            }
            pw.println("gmt subplot end");
            pw.println("");
            pw.println("#------- Scale");
            pw.println("  echo \"Turning point azimuth (@.)\" | gmt pstext -N -F+cTC+jBC -D0/50p");
            pw.println("  echo \"Epicentral distance (@.)\" | gmt pstext -N -F+cML+jBC+a90 -D-70p/0");
            pw.println("gmt psscale -Ccp.cpt -DJCB+w15/0.6+h+e0.5 -Q -B+l\"# time window\"");
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
