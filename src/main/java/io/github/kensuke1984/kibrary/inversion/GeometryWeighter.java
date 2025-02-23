package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
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
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());


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
        int[][] numberOfRecords = new int[nDistanceBin][nAzimuthBin];
        Map<DataEntry, Integer> iDistanceMap = new HashMap<>();
        Map<DataEntry, Integer> iAzimuthMap = new HashMap<>();

        // count number of records in each bin
        for (DataEntry entry : entrySet) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            double epicentralDistance = eventPosition.computeEpicentralDistanceDeg(observerPosition);

            double azimuth;
            if (pierceTool.hasRaypaths(entry)) {
                // When there are several raypaths for a given phase name, the first arrival is chosen.
                // When there are multiple bottoming points for a raypath, the first one is used.
                // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                azimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
            } else {
                System.err.println("Cannot compute turning point for " + entry + ", skipping.");
                continue;
            }
            if (!expandAzimuth && azimuth > 180) azimuth -= 180;

            int iDistance = (int) (epicentralDistance / distanceInterval);
            int iAzimuth = (int) (azimuth / azimuthInterval);
            numberOfRecords[iDistance][iAzimuth]++;

            iDistanceMap.put(entry, iDistance);
            iAzimuthMap.put(entry, iAzimuth);
        }

        // decide weights
        double[][] weights = decideWeights(numberOfRecords);
        Map<DataEntry, Double> weightMap = new HashMap<>();
        for (DataEntry entry : entrySet) {
            if (!iAzimuthMap.containsKey(entry)) {
                // If raypath with no azimuth information exists, weights cannot be computed.
                throw new IllegalStateException("No information for " + entry + ".");
            }
            double weight = weights[iDistanceMap.get(entry)][iAzimuthMap.get(entry)];
            weightMap.put(entry, weight);
        }

        // output
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "geometry", folderTag, appendFolderDate, null);
        Path txtPath = outPath.resolve("geometryHistogram.txt");
        Path weightPath = outPath.resolve("entryWeight.lst");
        writeHistogramData(txtPath, distanceInterval, azimuthInterval, numberOfRecords, weights);
        EntryWeightListFile.write(weightMap, weightPath);
    }

    private static void writeHistogramData(Path txtPath, double distanceInterval, double azimuthInterval,
            int[][] numberOfRecords, double[][] weights) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                for (int j = 0; j < numberOfRecords[i].length; j++) {
                    pw.println(String.format("%.2f %.2f %d %.1f", i * distanceInterval, j * azimuthInterval,
                            numberOfRecords[i][j], numberOfRecords[i][j] * weights[i][j]));
                }
            }
        }
    }

    private static double[][] decideWeights(int[][] numberOfRecords) {
        double[][] weights = new double[numberOfRecords.length][numberOfRecords[0].length];

        // average number of records in each bin (only bins with at least 1 record are considered)
        int sum = 0;
        int nBin = 0;
        for (int i = 0; i < numberOfRecords.length; i++) {
            for (int j = 0; j < numberOfRecords[i].length; j++) {
                if (numberOfRecords[i][j] > 0) {
                    sum += numberOfRecords[i][j];
                    nBin++;
                }
            }
        }
        double average = ((double) sum) / nBin;

        // weight for each bin
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i].length; j++) {
                if (numberOfRecords[i][j] > 0) {
                    double x = numberOfRecords[i][j] / average;
                    double weight = (1.0 - Math.exp(-3.0 * x)) / (1.0 - Math.exp(-3.0)) / x;
                    weights[i][j] = Precision.round(weight, 3);
                } else {
                    weights[i][j] = 0.0;
                }
            }
        }
        return weights;
    }

}
