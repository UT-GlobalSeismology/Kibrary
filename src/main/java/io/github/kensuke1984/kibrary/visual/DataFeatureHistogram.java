package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.selection.DataFeatureListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Operation that creates hisograms of normalized variance, amplitude ratio, and cross correlation
 * between observed and synthetic waveforms.
 *
 * @author otsuru
 * @since 2022/8/27
 */
public class DataFeatureHistogram extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * Path of a data feature list file
     */
    private Path dataFeaturePath;

    /**
     * Lower bound of correlation coefficient to plot
     */
    private double correlationLowerBound;
    /**
     * Upper bound of correlation coefficient to plot
     */
    private double correlationUpperBound;
    /**
     * Upper bound of normalized variance to plot
     */
    private double varianceUpperBound;
    /**
     * Upper bound of amplitude ratio to plot
     */
    private double ratioUpperBound;
    /**
     * Interval of correlation coefficient
     */
    private double dCorrelation;
    /**
     * Interval of normalized variance
     */
    private double dVariance;
    /**
     * Interval of amplitude ratio
     */
    private double dRatio;
    /**
     * Minimum correlation coefficient that is selected
     */
    private double minCorrelation;
    /**
     * Maximum correlation coefficient that is selected
     */
    private double maxCorrelation;
    /**
     * Minimum normalized variance that is selected
     */
    private double minVariance;
    /**
     * Maximum normalized variance that is selected
     */
    private double maxVariance;
    /**
     * Threshold of amplitude ratio that is selected
     */
    private double ratio;

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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#tag ");
            pw.println("##Path of a data feature list file, must be set");
            pw.println("#dataFeaturePath ");
            pw.println("##(double) Lower bound of correlation coefficient to plot [-1:correlationUpperBound) (-1)");
            pw.println("#correlationLowerBound ");
            pw.println("##(double) Upper bound of correlation coefficient to plot (correlationLowerBound:1] (1)");
            pw.println("#correlationUpperBound ");
            pw.println("##(double) Upper bound of normalized variance to plot (0:) (5)");
            pw.println("#varianceUpperBound ");
            pw.println("##(double) Upper bound of amplitude ratio to plot (0:) (5)");
            pw.println("#ratioUpperBound ");
            pw.println("##(double) Interval of correlation coefficient, must be positive (0.1)");
            pw.println("#dCorrelation ");
            pw.println("##(double) Interval of normalized variance, must be positive (0.2)");
            pw.println("#dVariance ");
            pw.println("##(double) Interval of amplitude ratio, must be positive (0.2)");
            pw.println("#dRatio ");
            pw.println("##(double) Lower threshold of correlation [-1:maxCorrelation] (0)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Upper threshold of correlation [minCorrelation:1] (1)");
            pw.println("#maxCorrelation ");
            pw.println("##(double) Lower threshold of normalized variance [0:maxVariance] (0)");
            pw.println("#minVariance ");
            pw.println("##(double) Upper threshold of normalized variance [minVariance:) (2)");
            pw.println("#maxVariance ");
            pw.println("##(double) Threshold of amplitude ratio (upper limit; lower limit is its inverse) [1:) (2)");
            pw.println("#ratio ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataFeatureHistogram(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        dataFeaturePath = property.parsePath("dataFeaturePath", null, true, workPath);

        correlationLowerBound = property.parseDouble("correlationLowerBound", "-1");
        correlationUpperBound = property.parseDouble("correlationUpperBound", "1");
        if (correlationLowerBound < -1 || correlationLowerBound >= correlationUpperBound || 1 < correlationUpperBound)
            throw new IllegalArgumentException("Correlation bound range " + correlationLowerBound + " , " + correlationUpperBound + " is invalid.");
        varianceUpperBound = property.parseDouble("varianceUpperBound", "5");
        if (varianceUpperBound <= 0)
            throw new IllegalArgumentException("Normalized variance bound " + varianceUpperBound + " is invalid.");
        ratioUpperBound = property.parseDouble("ratioUpperBound", "5");
        if (ratioUpperBound <= 0)
            throw new IllegalArgumentException("Amplitude ratio bound " + ratioUpperBound + " is invalid.");

        dCorrelation = property.parseDouble("dCorrelation", "0.1");
        if (dCorrelation <= 0) throw new IllegalArgumentException("dCorrelation must be positive.");
        dVariance = property.parseDouble("dVariance", "0.2");
        if (dVariance <= 0) throw new IllegalArgumentException("dVariance must be positive.");
        dRatio = property.parseDouble("dRatio", "0.2");
        if (dRatio <= 0) throw new IllegalArgumentException("dRatio must be positive.");

        minCorrelation = property.parseDouble("minCorrelation", "0");
        maxCorrelation = property.parseDouble("maxCorrelation", "1");
        if (minCorrelation < -1 || minCorrelation > maxCorrelation || 1 < maxCorrelation)
            throw new IllegalArgumentException("Selected correlation range " + minCorrelation + " , " + maxCorrelation + " is invalid.");
        minVariance = property.parseDouble("minVariance", "0");
        maxVariance = property.parseDouble("maxVariance", "2");
        if (minVariance < 0 || minVariance > maxVariance)
            throw new IllegalArgumentException("Selected normalized variance range " + minVariance + " , " + maxVariance + " is invalid.");
        ratio = property.parseDouble("ratio", "2");
        if (ratio < 1)
            throw new IllegalArgumentException("Selected amplitude ratio threshold " + ratio + " is invalid, must be >= 1.");
    }

   @Override
   public void run() throws IOException {
       List<DataFeature> featureList = DataFeatureListFile.read(dataFeaturePath);

       outPath = DatasetAid.createOutputFolder(workPath, "featureHistogram", tag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       createHistograms(featureList);
   }

   private void createHistograms(List<DataFeature> featureList) throws IOException {
       int nCorr = (int) Math.ceil((correlationUpperBound - correlationLowerBound) / dCorrelation);
       int nVar = (int) Math.ceil(varianceUpperBound / dVariance);
       int nRatio = (int) Math.ceil(ratioUpperBound / dRatio);
       int[] corrs = new int[nCorr];
       int[] vars = new int[nVar];
       int[] ratios = new int[nRatio];
       String corrFileNameRoot = "correlationHistogram";
       String varFileNameRoot = "varianceHistogram";
       String ratioFileNameRoot = "ratioHistogram";
       Path corrPath = outPath.resolve(corrFileNameRoot + ".txt");
       Path varPath = outPath.resolve(varFileNameRoot + ".txt");
       Path ratioPath = outPath.resolve(ratioFileNameRoot + ".txt");

       for (DataFeature feature : featureList) {
           // if the value is inside the plot range, count it at the corresponding interval
           // in the following lines, the decimal part is cut off when typecasting
           if (correlationLowerBound <= feature.getCorrelation() && feature.getCorrelation() < correlationUpperBound) {
               int iCorr = (int) ((feature.getCorrelation() - correlationLowerBound) / dCorrelation);
               corrs[iCorr]++;
           }
           if (feature.getVariance() < varianceUpperBound) {
               int iVar = (int) (feature.getVariance() / dVariance);
               vars[iVar]++;
           }
           if (feature.getAbsRatio() < ratioUpperBound) {
               int iRatio = (int) (feature.getAbsRatio() / dRatio);
               ratios[iRatio]++;
           }
       }
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(corrPath))) {
           for (int i = 0; i < nCorr; i++)
               pw.println((correlationLowerBound + i * dCorrelation) + " " + corrs[i]);
       }
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(varPath))) {
           for (int i = 0; i < nVar; i++)
               pw.println((i * dVariance) + " " + vars[i]);
       }
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ratioPath))) {
           for (int i = 0; i < nRatio; i++)
               pw.println((i * dRatio) + " " + ratios[i]);
       }

       createPlot(outPath, corrFileNameRoot, "Correlation", dCorrelation, correlationLowerBound, correlationUpperBound, dCorrelation * 5);
       createPlot(outPath, varFileNameRoot, "Normalized variance", dVariance, 0, varianceUpperBound, dVariance * 5);
       createPlot(outPath, ratioFileNameRoot, "Syn/Obs amplitude ratio", dRatio, 0, ratioUpperBound, dRatio * 5);
   }

   private static void createPlot(Path outPath, String fileNameRoot, String xLabel, double interval, double minimum, double maximum, double xtics) throws IOException {
       Path scriptPath = outPath.resolve(fileNameRoot + ".plt");

       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
           pw.println("set term pngcairo enhanced font 'Helvetica,14'");
           pw.println("set xlabel '" + xLabel + "'");
           pw.println("set ylabel '#timewindows'");
           pw.println("set xrange [" + minimum + ":" + maximum + "]");
           pw.println("set xtics " + xtics + " nomirror");
           pw.println("set ytics nomirror");
           pw.println("set style fill solid border lc rgb 'black'");
           pw.println("set sample 11");
           pw.println("set output '" + fileNameRoot + ".png'");
           pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'red' notitle");
       }

       GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
       histogramPlot.execute();
   }
}
