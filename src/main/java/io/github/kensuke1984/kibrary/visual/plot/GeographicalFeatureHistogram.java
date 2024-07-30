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
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.inversion.EntryWeightListFile;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
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
            pw.println("##Dumping parameters to calculate weights, listed using spaces. (1 10 100)");
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
        lambdas = property.parseDoubleArray("lambdas", "1 10 100");
    }

   @Override
   public void run() throws IOException {

        entryList = DataEntryListFile.readAsSet(dataEntryPath).stream()
                .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toList());

        int nDistance = (int) Math.ceil(360 / distanceInterval);
        int nAzimuth = (int) Math.ceil(360 / azimuthInterval);
        int n = nDistance + nAzimuth;
        int m = entryList.size();
        // set h0 (average of each histogram) & w0 (initial weighting for each entry) vector
        double[] h0Array = new double[n];
        double[] w0Array = new double[m];
        Arrays.fill(h0Array, 0, nDistance - 1, (double) m / nDistance);
        Arrays.fill(h0Array, nDistance, n - 1, (double) m / nAzimuth);
        Arrays.fill(w0Array, 1.);
        RealVector h0 = new ArrayRealVector(h0Array, false);
        RealVector w0 = new ArrayRealVector(w0Array, false);
        // set G (counting each entry belongs to which bin of each histograms) matrix
        ParallelizedMatrix g = initializeGMatrix(nDistance, nAzimuth, m);

        double[] distanceBins = new double[nDistance];
        double[] azimuthBins = new double[nAzimuth];
        for (int i = 0; i < nDistance; i++) {
            distanceBins[i] = Arrays.stream(g.getRow(i)).sum();
        }
        for (int i = 0; i < nAzimuth; i++) {
            azimuthBins[i] = Arrays.stream(g.getRow(nDistance + i)).sum();
        }
        // plot raw histograms
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "histogram", folderTag, appendFolderDate, GadgetAid.getTemporaryString());
        plotHistograms(distanceBins, azimuthBins, outPath, "raw");

        double[] abics = new double[lambdas.length];
        for (int i = 0; i < lambdas.length; i++) {
            abics[i] = computeWeights(g, h0, w0, lambdas[i], nDistance, nAzimuth, outPath);
        }
        Path abicPath = outPath.resolve("abic_dist.lst");
        writeABIC(abicPath, lambdas, abics);
    }

   private ParallelizedMatrix initializeGMatrix(int nDistance, int nAzimuth, int m) {
       ParallelizedMatrix g = new ParallelizedMatrix(nDistance + nAzimuth, m);
       g.scalarMultiply(0);
       // if using turning point azimuth, compute using TauPPierce
       TauPPierceWrapper pierceTool = null;
       if (azimuthType.equals("turning")) {
           try {
               pierceTool = new TauPPierceWrapper(structureName, turningPointPhase);
               pierceTool.compute(new HashSet<>(entryList));
           } catch (TauModelException e) {
               throw new RuntimeException(e);
           }
       }
       for (int j = 0; j < m; j++) {
           DataEntry entry = entryList.get(j);
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
           g.setEntry(iDistance, j, 1.0);
           g.setEntry(nDistance + iAzimuth, j, 1.0);
       }
       return g;
   }

   private void plotHistograms(double[] distanceBins, double[] azimuthBins, Path outPath, String tag) throws IOException {
       String typeName;
       String xlabel;
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
       Path distTxtPath = outPath.resolve("distHistogram_" + tag + ".txt");
       Path distScriptPath = outPath.resolve("distHistogram_" + tag + ".plt");
       Path azTxtPath = outPath.resolve(typeName + "Histogram_" + tag + ".txt");
       Path azScriptPath = outPath.resolve(typeName + "Histogram_" + tag + ".plt");
       writeHistogramData(distTxtPath, distanceInterval, distanceBins);
       createScript(distScriptPath, xlabel, distanceInterval, distanceMin, distanceMax, distanceXtics);
       writeHistogramData(azTxtPath, azimuthInterval, azimuthBins);
       createScript(azScriptPath, xlabel, azimuthInterval, azimuthMin, azimuthMax, azimuthXtics);
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

    private static void writeABIC(Path abicPath, double[] lambdas, double[] abics) throws IOException {
        if (lambdas.length != abics.length) throw new RuntimeException("The number of lambdas and ABIC values are different");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(abicPath))) {
            pw.println("# lambda ABIC");
            for (int i = 0; i < lambdas.length; i++) {
                pw.println(lambdas[i] + " " + abics[i]);
            }
        }
    }

    private double computeWeights(ParallelizedMatrix g, RealVector h0, RealVector w0, double lambda, int nDistance, int nAzimuth, Path outPath) throws IOException {
        int n = g.getRowDimension();
        int m = g.getColumnDimension();
        // compute "Gt G + lambda I" matrix
        RealMatrix gtg = g.computeAtA();
        for (int j = 0; j < m; j++) {
            gtg.addToEntry(j, j, lambda);
        }
        //compute "Gt h0 + lambda w0" vector
        RealVector gth0 = g.preMultiply(h0);
        gth0.mapAdd(lambda);
        // compute w & h vector
        System.err.println("compute w vector"); //TODO
        RealVector w = MatrixUtils.inverse(gtg).operate(gth0);
        System.err.println("compute h vector"); //TODO
        RealVector h = g.operate(w);
        // compute ABIC value
        System.err.println("compute ABIC"); //TODO
        double norm = Math.pow(h.subtract(h0).getNorm(), 2) + lambda * Math.pow(w.subtract(w0).getNorm(), 2);
        double det = new LUDecomposition(gtg).getDeterminant();
        double abic = n * Math.log(norm) - m * Math.log(lambda) + Math.log(det);

        double[] hArray = h.toArray();
        double[] wArray = w.toArray();
        Map<DataEntry, Double> weightMap = new HashMap<>();
        for (int j = 0; j < m; j++) {
            weightMap.put(entryList.get(j), wArray[j]);
        }
        double[] distanceBins = Arrays.copyOfRange(hArray, 0, nDistance - 1);
        double[] azimuthBins = Arrays.copyOfRange(hArray, nDistance, n - 1);
        // output
        String lambdaCode = MathAid.simplestString(lambda, "d");
        plotHistograms(distanceBins, azimuthBins, outPath, lambdaCode);
        Path weightPath = outPath.resolve("entryWeight_" + lambdaCode + ".lst");
        EntryWeightListFile.write(weightMap, weightPath);

        return abic;
    }
}
