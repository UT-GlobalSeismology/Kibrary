package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.inversion.EntryWeightListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

public class GeographicalFeatureHistogram extends Operation {

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
      * Set of components.
      */
     Set<SACComponent> components;
     /**
      * Path of a data entry file.
      */
     private Path dataEntryPath;
     /**
      * Interval of distance in histogram.
      */
     private double distanceInterval;
     /**
      * Interval of x tics in distance histogram.
      */
     private double distanceXtics;
     /**
      * Minimum distance in histogram.
      */
     private double distanceMin;
     /**
      * Maximum distance in histogram.
      */
     private double distanceMax;
     /**
      * Interval of azimuth in histogram.
      */
     private double azimuthInterval;
     /**
      * Interval of x ticsin in azimuth histogram.
      */
     private double azimuthXtics;
     /**
      * Minimum azimuth in histogram.
      */
     private double azimuthMin;
     /**
      * Maximum azimuth in histogram.
      */
     private double azimuthMax;
     /**
      * Expand azimuth range to [0:360), not overlapping onto [0:180) range.
      */
     private boolean expandAzimuth;
     /**
      * Type of azimuth to compute. {forward, back, turning}
      */
     private String azimuthType;
     /**
      * Name of structure to use to compute turning point.
      */
     private String structureName;
     /**
      * "Name of phase to use to compute turning point.
      */
     private String turningPointPhase;
     /**
      * Dumping parameters to calculate weights.
      */
     private double[] lambdas;
     private List<DataEntry> entryList;


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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry list file. must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##Interval of distance in histogram. (2)");
            pw.println("#distanceInterval ");
            pw.println("##Interval of x tics in distance histogram. (10)");
            pw.println("#distanceXtics ");
            pw.println("##Minimum distance in histogram. (0)");
            pw.println("#distanceMin ");
            pw.println("##Maximum distance in histogram. (180)");
            pw.println("#distanceMax ");
            pw.println("##Interval of azimuth in histogram. (5)");
            pw.println("#azimuthInterval ");
            pw.println("##Interval of x tics in azimuth histogram. (30)");
            pw.println("#azimuthXtics ");
            pw.println("##Minimum azimuth in histogram. (0)");
            pw.println("#azimuthMin ");
            pw.println("##Maximum distance in histogram. (180)");
            pw.println("#azimuthMax ");
            pw.println("##(boolean) Expand azimuth range to [0:360), not overlapping onto [0:180) range. (false)");
            pw.println("#expandAzimuth true");
            pw.println("##(String) Type of azimuth to compute. {forward, back, turning} (forward)");
            pw.println("#azimuthType back");
            pw.println("##(String) Name of structure to compute travel times using TauP. (prem)");
            pw.println("#structureName ");
            pw.println("##(String) Name of phase to use to compute turning point. (ScS)");
            pw.println("#turningPointPhase ");
            pw.println("##Dumping parameters to calculate weights, listed using spaces. (0.01 1 100)");
            pw.println("#lambdas ");
        }
        System.err.println(outPath + " is created.");
    }

    public GeographicalFeatureHistogram(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        distanceInterval = property.parseDouble("distanceInterval", "2");
        distanceXtics= property.parseDouble("distanceXtics", "10");
        distanceMin = property.parseDouble("distanceMin", "0");
        distanceMax = property.parseDouble("distanceMax", "180");
        azimuthInterval = property.parseDouble("azimuthInterval", "5");
        azimuthXtics= property.parseDouble("azimuthXtics", "30");
        azimuthMin = property.parseDouble("azimuthMin", "0");
        azimuthMax = property.parseDouble("azimuthMax", "180");
        expandAzimuth = property.parseBoolean("expandAzimuth", "false");
        azimuthType = property.parseString("azimuthType", "forward");
        if (azimuthType.equals("turning")) {
            structureName = property.parseString("structureName", "prem");
            turningPointPhase = property.parseString("turningPointPhase", "ScS");
        }
        lambdas = property.parseDoubleArray("lambdas", "0.01 1 100");
    }

   @Override
   public void run() throws IOException {

        entryList = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toList());

        int nDistance = (int) Math.ceil(360 / distanceInterval);
        int nAzimuth = (int) Math.ceil(360 / azimuthInterval);
        double[] distanceBins = new double[nDistance];
        double[] azimuthBins = new double[nAzimuth];
        Map<DataEntry, Integer> distanceMap = new HashMap<>();
        Map<DataEntry, Integer> azimuthMap = new HashMap<>();

        Arrays.fill(distanceBins, 0.);
        Arrays.fill(azimuthBins, 0.);

        TauPPierceWrapper pierceTool = null;
        if (azimuthType.equals("turning")) {
            try {
                pierceTool = new TauPPierceWrapper(structureName, turningPointPhase);
                pierceTool.compute(new HashSet<>(entryList));
            } catch (TauModelException e) {
                throw new RuntimeException(e);
            }
        }
        for (DataEntry entry : entryList) {
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            double distance = Math.toDegrees(eventPosition.computeEpicentralDistanceRad(observerPosition));
            double azimuth;
            if (azimuthType.equals("turning")) {
                if (pierceTool.hasRaypaths(entry)) {
                    // When there are several raypaths for a given phase name, the first arrival is chosen.
                    // When there are multiple bottoming points for a raypath, the first one is used.
                    // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                    azimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
                } else {
                    System.err.println("Cannot compute turning point for " + entry + ", skipping.");
                    continue;
                }
            } else if (azimuthType.equals("back")) {
                azimuth = eventPosition.computeBackAzimuthDeg(observerPosition);
            } else if (azimuthType.equals("forward")) {
                azimuth = eventPosition.computeAzimuthDeg(observerPosition);
            } else {
                throw new IllegalArgumentException("azimuthType must be choosed from {forward, back, turning}");
            }
            if (!expandAzimuth && azimuth > 180) azimuth -= 180;
            int iDistance = (int) (distance / distanceInterval);
            int iAzimuth = (int) (azimuth / azimuthInterval);
            distanceBins[iDistance]++;
            azimuthBins[iAzimuth]++;
            distanceMap.put(entry, iDistance);
            azimuthMap.put(entry, iAzimuth);
        }
        // plot raw histograms
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "histogram", folderTag, appendFolderDate, GadgetAid.getTemporaryString());
        plotHistograms(distanceBins, outPath, "raw", "distance");
        plotHistograms(azimuthBins, outPath, "raw", "azimuth");

        double[][] normsDist = new double[lambdas.length][2];
        double[][] normsAz = new double[lambdas.length][2];
        for (int i = 0; i < lambdas.length; i++) {
            normsDist[i] = computeWeights(lambdas[i], distanceBins, distanceMap, outPath, "distance");
            normsAz[i] = computeWeights(lambdas[i], azimuthBins, azimuthMap, outPath, "azimuth");
//            computeWeights(lambdas[i], distanceBins, distanceMap, outPath, "distance");
 //           computeWeights(lambdas[i], azimuthBins, azimuthMap, outPath, "azimuth");
        }
        Path normDistPath = outPath.resolve("norm_dist.lst");
        writeNorms(normDistPath, lambdas, normsDist);
        Path normAzPath = outPath.resolve("norm_az.lst");
        writeNorms(normAzPath, lambdas, normsAz);
    }

   private void plotHistograms(double[] bins, Path outPath, String tag, String type) throws IOException {
       String typeName = null;
       String xlabel = null;
       if (type.equals("distance")) {
           typeName = "dist";
           xlabel = "Epicentral distance";
       } else if (type.equals("azimuth")) {
           switch (azimuthType) {
           case "turning":
               typeName = "turnAz";
               xlabel = "Turning point azimuth";
               break;
           case "back":
               typeName = "backAz";
               xlabel = "Back azimuth";
               break;
           case "forward" :
               typeName = "sourceAz";
               xlabel = "Source azimuth";
               break;
           default:
               throw new IllegalArgumentException("azimuthType must be choosed from {forward, back, turning}");
           }
       }
       if (typeName == null || xlabel == null)
           throw new RuntimeException("typeName and/or xlabel of plot file is NULL");
       double min = (type.equals("distance")) ? distanceMin : azimuthMin;
       double max = (type.equals("distance")) ? distanceMax : azimuthMax;
       double interval = (type.equals("distance")) ? distanceInterval : azimuthInterval;
       double xtics = (type.equals("distance")) ? distanceXtics : azimuthXtics;
       Path txtPath = outPath.resolve(typeName + "Histogram_" + tag + ".txt");
       Path scriptPath = outPath.resolve(typeName + "Histogram_" + tag + ".plt");
       writeHistogramData(txtPath, interval, bins);
       createScript(scriptPath, xlabel, interval, min, max, xtics);
   }

    private static void writeHistogramData(Path txtPath, double interval, double[] numberOfRecords) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(txtPath))) {
            for (int i = 0; i < numberOfRecords.length; i++) {
                pw.println(String.format("%.2f %.1f", i * interval, numberOfRecords[i]));
            }
        }
    }

    private static void createScript(Path scriptPath, String xlabel, double interval, double minimum, double maximum, double xtics) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,20'");
            pw.println("set xlabel '" + xlabel + " (deg)'");
            pw.println("set ylabel 'Number of records'");
            pw.println("set xrange [" + minimum + ":" + maximum + "]");
            pw.println("set xtics " + xtics + " nomirror");
            pw.println("set ytics nomirror");
            pw.println("set style fill solid border lc rgb 'black'");
            pw.println("set sample 11");
            pw.println("set output '" + fileNameRoot + ".png'");
            pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'sea-green' notitle");
        }
        GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
        histogramPlot.execute();
    }

    private static void writeNorms(Path normPath, double[] lambdas, double[][] norms) throws IOException {
        if (lambdas.length != norms.length) throw new RuntimeException("The number of lambdas and ABIC values are different");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(normPath))) {
            pw.println("# lambda norm(h_weight - h_ave) norm(h_weight - h_raw)");
            for (int i = 0; i < lambdas.length; i++) {
                pw.println(lambdas[i] + " " + norms[i][0] + " " + norms[i][1]);
            }
        }
    }

    private double[] computeWeights(double lambda, double[] bins, Map<DataEntry,Integer> map, Path outPath, String type) throws IOException {
        int num = Arrays.stream(bins).filter(b -> b != 0.).toArray().length;
        double average = (double) entryList.size() / num;
        double[] weightArray = new double[bins.length];
        double[] averageArray = new double[bins.length];
        Arrays.fill(weightArray, 0.);
        Arrays.fill(averageArray, 0.);
        for (int i = 0; i < bins.length; i++) {
            double rawBin = bins[i];
            if (rawBin != 0.) {
                weightArray[i] = (average + lambda * rawBin) / (1 + lambda);
                averageArray[i] = average;
            }
        }
        String lambdaCode = MathAid.simplestString(lambda, "d");
        plotHistograms(weightArray, outPath, lambdaCode, type);

        Map<DataEntry, Double> weightMap = new HashMap<>();
        for (DataEntry entry : entryList) {
            int index = map.get(entry);
            double weight = weightArray[index] / bins[index];
            weightMap.put(entry, weight);
        }
        Path weightPath = outPath.resolve("entryWeight_" + type + "_" + lambdaCode + ".lst");
        EntryWeightListFile.write(weightMap, weightPath);

        RealVector rawVec = new ArrayRealVector(bins, false);
        RealVector aveVec = new ArrayRealVector(averageArray, false);
        RealVector weightVec = new ArrayRealVector(weightArray, false);
        double[] norms = new double[2];
        norms[0] = weightVec.subtract(aveVec).getNorm();
        norms[1] = weightVec.subtract(rawVec).getNorm();
        return norms;
    }
}
