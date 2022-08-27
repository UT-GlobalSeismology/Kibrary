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
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.selection.DataFeatureListFile;

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
     * Path of a data feature list file
     */
    private Path dataFeaturePath;

    /**
     * Minimum correlation coefficient
     */
    private double minCorrelation;
    /**
     * Maximum correlation coefficient
     */
    private double maxCorrelation;
    /**
     * Minimum variance
     */
    private double minVariance;
    /**
     * Maximum variance
     */
    private double maxVariance;
    /**
     * Threshold of amplitude ratio
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
            pw.println("##(double) Lower threshold of correlation [-1:maxCorrelation) (0)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Upper threshold of correlation (minCorrelation:1] (1)");
            pw.println("#maxCorrelation ");
            pw.println("##(double) Lower threshold of variance [0:maxVariance) (0)");
            pw.println("#minVariance ");
            pw.println("##(double) Upper threshold of variance (minVariance:) (2)");
            pw.println("#maxVariance ");
            pw.println("##(double) Threshold of amplitude ratio (upper limit) [1:) (2)");
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

        minCorrelation = property.parseDouble("minCorrelation", "0");
        maxCorrelation = property.parseDouble("maxCorrelation", "1");
        if (minCorrelation < -1 || minCorrelation > maxCorrelation || 1 < maxCorrelation)
            throw new IllegalArgumentException("Correlation range " + minCorrelation + " , " + maxCorrelation + " is invalid.");
        minVariance = property.parseDouble("minVariance", "0");
        maxVariance = property.parseDouble("maxVariance", "2");
        if (minVariance < 0 || minVariance > maxVariance)
            throw new IllegalArgumentException("Variance range " + minVariance + " , " + maxVariance + " is invalid.");
        ratio = property.parseDouble("ratio", "2");
        if (ratio < 1)
            throw new IllegalArgumentException("Ratio threshold " + ratio + " is invalid, must be >= 1.");

    }

   @Override
   public void run() throws IOException {
       List<DataFeature> selectionInfo = DataFeatureListFile.read(dataFeaturePath);

   }

}
