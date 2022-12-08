package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.selection.DataFeatureListFile;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

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
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * Path of a data feature list file
     */
    private Path dataFeaturePath;
    /**
     * path of basic ID file
     */
    private Path mainBasicIDPath;
    /**
     * path of waveform data
     */
    private Path mainBasicPath;
    /**
     * path of basic ID file
     */
    private Path extraBasicIDPath;
    /**
     * path of waveform data
     */
    private Path extraBasicPath;
    /**
     * Path of a data entry file
     */
    private Path dataEntryPath;

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
     * Upper bound of S/N ratio to plot
     */
    private double snRatioUpperBound;
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
     * Interval of S/N ratio
     */
    private double dSNRatio;
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
    private double maxRatio;
    /**
     * Threshold of S/N ratio that is selected
     */
    private double minSNRatio;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, set this unset.");
            pw.println("#folderTag ");
            pw.println("##Path of a data feature list file. If this is not set, the following basicID&waveform files will be used.");
            pw.println("##  The S/N ratio histogram can be created only when dataFeaturePath is set.");
            pw.println("#dataFeaturePath dataFeature.lst");
            pw.println("##Path of a basic ID file, must be set if dataFeaturePath is not set");
            pw.println("#mainBasicIDPath ");
            pw.println("##Path of a basic waveform file, must be set if dataFeaturePath is not set");
            pw.println("#mainBasicPath ");
            pw.println("##Path of an additional basic ID file, if any (e.g. data not used in inversion)");
            pw.println("#extraBasicIDPath ");
            pw.println("##Path of an additional basic waveform file, if any (e.g. data not used in inversion)");
            pw.println("#extraBasicPath ");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##(double) Lower bound of correlation coefficient to plot [-1:correlationUpperBound) (-1)");
            pw.println("#correlationLowerBound ");
            pw.println("##(double) Upper bound of correlation coefficient to plot (correlationLowerBound:1] (1)");
            pw.println("#correlationUpperBound ");
            pw.println("##(double) Upper bound of normalized variance to plot (0:) (5)");
            pw.println("#varianceUpperBound ");
            pw.println("##(double) Upper bound of amplitude ratio to plot (0:) (5)");
            pw.println("#ratioUpperBound ");
            pw.println("##(double) Upper bound of S/N ratio to plot (0:) (5)");
            pw.println("#snRatioUpperBound ");
            pw.println("##(double) Interval of correlation coefficient, must be positive (0.1)");
            pw.println("#dCorrelation ");
            pw.println("##(double) Interval of normalized variance, must be positive (0.2)");
            pw.println("#dVariance ");
            pw.println("##(double) Interval of amplitude ratio, must be positive (0.2)");
            pw.println("#dRatio ");
            pw.println("##(double) Interval of S/N ratio, must be positive (0.2)");
            pw.println("#dSNRatio ");
            pw.println("##(double) Lower threshold of correlation [-1:maxCorrelation] (0)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Upper threshold of correlation [minCorrelation:1] (1)");
            pw.println("#maxCorrelation ");
            pw.println("##(double) Lower threshold of normalized variance [0:maxVariance] (0)");
            pw.println("#minVariance ");
            pw.println("##(double) Upper threshold of normalized variance [minVariance:) (2)");
            pw.println("#maxVariance ");
            pw.println("##(double) Threshold of amplitude ratio (upper limit; lower limit is its inverse) [1:) (2)");
            pw.println("#maxRatio ");
            pw.println("##(double) Threshold of S/N ratio (lower limit) [0:) (0)");
            pw.println("#minSNRatio ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataFeatureHistogram(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        if (property.containsKey("dataFeaturePath")) {
            dataFeaturePath = property.parsePath("dataFeaturePath", null, true, workPath);
        } else {
            mainBasicIDPath = property.parsePath("mainBasicIDPath", null, true, workPath);
            mainBasicPath = property.parsePath("mainBasicPath", null, true, workPath);
            if (property.containsKey("extraBasicIDPath") && property.containsKey("extraBasicPath")) {
                extraBasicIDPath = property.parsePath("extraBasicIDPath", null, true, workPath);
                extraBasicPath = property.parsePath("extraBasicPath", null, true, workPath);
            }
        }
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }

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
        snRatioUpperBound = property.parseDouble("snRatioUpperBound", "5");
        if (snRatioUpperBound <= 0)
            throw new IllegalArgumentException("S/N ratio bound " + snRatioUpperBound + " is invalid.");

        dCorrelation = property.parseDouble("dCorrelation", "0.1");
        if (dCorrelation <= 0) throw new IllegalArgumentException("dCorrelation must be positive.");
        dVariance = property.parseDouble("dVariance", "0.2");
        if (dVariance <= 0) throw new IllegalArgumentException("dVariance must be positive.");
        dRatio = property.parseDouble("dRatio", "0.2");
        if (dRatio <= 0) throw new IllegalArgumentException("dRatio must be positive.");
        dSNRatio = property.parseDouble("dSNRatio", "0.2");
        if (dSNRatio <= 0) throw new IllegalArgumentException("dSNRatio must be positive.");

        minCorrelation = property.parseDouble("minCorrelation", "0");
        maxCorrelation = property.parseDouble("maxCorrelation", "1");
        if (minCorrelation < -1 || minCorrelation > maxCorrelation || 1 < maxCorrelation)
            throw new IllegalArgumentException("Selected correlation range " + minCorrelation + " , " + maxCorrelation + " is invalid.");
        minVariance = property.parseDouble("minVariance", "0");
        maxVariance = property.parseDouble("maxVariance", "2");
        if (minVariance < 0 || minVariance > maxVariance)
            throw new IllegalArgumentException("Selected normalized variance range " + minVariance + " , " + maxVariance + " is invalid.");
        maxRatio = property.parseDouble("maxRatio", "2");
        if (maxRatio < 1)
            throw new IllegalArgumentException("Selected amplitude ratio threshold " + maxRatio + " is invalid, must be >= 1.");
        minSNRatio = property.parseDouble("minSNRatio", "0");
        if (minSNRatio < 0)
            throw new IllegalArgumentException("Selected S/N ratio threshold " + minSNRatio + " is invalid, must be >= 0.");
    }

   @Override
   public void run() throws IOException {

       // read data features
       List<DataFeature> featureList;
       List<DataFeature> extraFeatureList = null;
       if (dataFeaturePath != null) {
           // the DataFeatureListFile includes information of whether the timewindow is selected, so use that to filter the features
           List<DataFeature> tempFeatureList = DataFeatureListFile.read(dataFeaturePath);
           featureList = tempFeatureList.stream().filter(feature -> feature.isSelected()).collect(Collectors.toList());
           extraFeatureList = tempFeatureList.stream().filter(feature -> !feature.isSelected()).collect(Collectors.toList());
       } else {
           BasicID[] mainBasicIDs = BasicIDFile.read(mainBasicIDPath, mainBasicPath);
           featureList = extractFeatures(mainBasicIDs);
           // read extra data features if the extra basic files exist
           if (extraBasicIDPath != null) {
               BasicID[] extraBasicIDs = BasicIDFile.read(extraBasicIDPath, extraBasicPath);
               extraFeatureList = extractFeatures(extraBasicIDs);
           }
       }

       // read entry set and apply selection
       if (dataEntryPath != null) {
           Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);
           featureList = featureList.stream().filter(feature -> entrySet.contains(feature.getTimewindow().toEntry()))
                   .collect(Collectors.toList());
           extraFeatureList = extraFeatureList.stream().filter(feature -> entrySet.contains(feature.getTimewindow().toEntry()))
                   .collect(Collectors.toList());
       }

       outPath = DatasetAid.createOutputFolder(workPath, "featureHistogram", folderTag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       // if input is in BasicID, export their features (for reference)
       if (mainBasicIDPath != null) {
           Path outFeaturePath = outPath.resolve("dataFeature.lst");
           DataFeatureListFile.write(featureList, outFeaturePath);
           // if extra BasicID files are set, export for them as well
           if (extraFeatureList != null) {
               Path outExtraFeaturePath = outPath.resolve("extraDataFeature.lst");
               DataFeatureListFile.write(extraFeatureList, outExtraFeaturePath);
           }
       }

       createHistograms(featureList, extraFeatureList);
   }

   private List<DataFeature> extractFeatures(BasicID[] basicIDs) {
       List<DataFeature> featureList = new ArrayList<>();

       // sort observed and synthetic
       BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
       BasicID[] obsIDs = pairer.getObsList().toArray(new BasicID[0]);
       BasicID[] synIDs = pairer.getSynList().toArray(new BasicID[0]);

       // create dataFeatures from basicIDs
       for (int i = 0; i < obsIDs.length; i++) {
           BasicID obsID = obsIDs[i];
           BasicID synID = synIDs[i];

           RealVector obsU = new ArrayRealVector(obsID.getData(), false);
           RealVector synU = new ArrayRealVector(synID.getData(), false);

           // Start time of synthetic waveform must be used, since it is the correct one when time shift is applied.
           double startTime = synID.getStartTime();
           double endTime = startTime + synID.getNpts() / synID.getSamplingHz();
           TimewindowData timewindow = new TimewindowData(startTime, endTime,
                   synID.getObserver(), synID.getGlobalCMTID(), synID.getSacComponent(), synID.getPhases());

           // TODO snRatio and selected cannot be decided
           DataFeature feature = DataFeature.create(timewindow, obsU, synU, 0, true);
           featureList.add(feature);
       }

       return featureList;
   }

   /**
     * @param featureList (List of DataFeature) The main data feature list.
     * @param extraFeatureList (List of DataFeature) Extra list. This shall be null if there is no extra list.
     * @throws IOException
     */
    private void createHistograms(List<DataFeature> featureList, List<DataFeature> extraFeatureList) throws IOException {
       int nCorr = (int) Math.ceil((correlationUpperBound - correlationLowerBound) / dCorrelation);
       int nVar = (int) Math.ceil(varianceUpperBound / dVariance);
       int nRatio = (int) Math.ceil(ratioUpperBound / dRatio);
       int nSNRatio = (int) Math.ceil(snRatioUpperBound / dSNRatio);
       int[] corrs = new int[nCorr];
       int[] vars = new int[nVar];
       int[] ratios = new int[nRatio];
       int[] snRatios = new int[nSNRatio];
       int[] extraCorrs = new int[nCorr];
       int[] extraVars = new int[nVar];
       int[] extraRatios = new int[nRatio];
       int[] extraSnRatios = new int[nSNRatio];
       String corrFileNameRoot = "correlationHistogram";
       String varFileNameRoot = "varianceHistogram";
       String ratioFileNameRoot = "ratioHistogram";
       String snRatioFileNameRoot = "snRatioHistogram";
       Path corrPath = outPath.resolve(corrFileNameRoot + ".txt");
       Path varPath = outPath.resolve(varFileNameRoot + ".txt");
       Path ratioPath = outPath.resolve(ratioFileNameRoot + ".txt");
       Path snRatioPath = outPath.resolve(snRatioFileNameRoot + ".txt");

       // count up main features
       for (DataFeature feature : featureList) {
           // if the value is inside the plot range, count it at the corresponding interval
           // In the following lines, the decimal part is cut off when typecasting.
           // The "0 <= feature.**()" is to exclude -Infinity or any other inappropriate data.
           if (correlationLowerBound <= feature.getCorrelation() && feature.getCorrelation() < correlationUpperBound) {
               int iCorr = (int) ((feature.getCorrelation() - correlationLowerBound) / dCorrelation);
               corrs[iCorr]++;
           }
           if (0 <= feature.getVariance() && feature.getVariance() < varianceUpperBound) {
               int iVar = (int) (feature.getVariance() / dVariance);
               vars[iVar]++;
           }
           if (0 <= feature.getAbsRatio() && feature.getAbsRatio() < ratioUpperBound) {
               int iRatio = (int) (feature.getAbsRatio() / dRatio);
               ratios[iRatio]++;
           }
           if (0 <= feature.getSNRatio() && feature.getSNRatio() < snRatioUpperBound) {
               int iSNRatio = (int) (feature.getSNRatio() / dSNRatio);
               snRatios[iSNRatio]++;
           }
       }

       // count up extra features
       boolean extraExists = (extraFeatureList != null);
       if (extraExists) {
           for (DataFeature feature : extraFeatureList) {
               // if the value is inside the plot range, count it at the corresponding interval
               // In the following lines, the decimal part is cut off when typecasting.
               // The "0 <= feature.**()" is to exclude -Infinity or any other inappropriate data.
               if (correlationLowerBound <= feature.getCorrelation() && feature.getCorrelation() < correlationUpperBound) {
                   int iCorr = (int) ((feature.getCorrelation() - correlationLowerBound) / dCorrelation);
                   extraCorrs[iCorr]++;
               }
               if (0 <= feature.getVariance() && feature.getVariance() < varianceUpperBound) {
                   int iVar = (int) (feature.getVariance() / dVariance);
                   extraVars[iVar]++;
               }
               if (0 <= feature.getAbsRatio() && feature.getAbsRatio() < ratioUpperBound) {
                   int iRatio = (int) (feature.getAbsRatio() / dRatio);
                   extraRatios[iRatio]++;
               }
               if (0 <= feature.getSNRatio() && feature.getSNRatio() < snRatioUpperBound) {
                   int iSNRatio = (int) (feature.getSNRatio() / dSNRatio);
                   extraSnRatios[iSNRatio]++;
               }
           }
       }

       // output txt files
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(corrPath))) {
           for (int i = 0; i < nCorr; i++)
               pw.println(Precision.round(correlationLowerBound + i * dCorrelation, DataFeature.PRECISION)
                       + " " + corrs[i] + " " + extraCorrs[i]);
       }
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(varPath))) {
           for (int i = 0; i < nVar; i++)
               pw.println(Precision.round(i * dVariance, DataFeature.PRECISION) + " " + vars[i] + " " + extraVars[i]);
       }
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ratioPath))) {
           for (int i = 0; i < nRatio; i++)
               pw.println(Precision.round(i * dRatio, DataFeature.PRECISION) + " " + ratios[i] + " " + extraRatios[i]);
       }

       // plot histograms
       createPlot(outPath, corrFileNameRoot, "Correlation", dCorrelation, correlationLowerBound, correlationUpperBound,
               dCorrelation * 5, minCorrelation, maxCorrelation, extraExists);
       createPlot(outPath, varFileNameRoot, "Normalized variance", dVariance, 0, varianceUpperBound,
               dVariance * 5, minVariance, maxVariance, extraExists);
       createPlot(outPath, ratioFileNameRoot, "Syn/Obs amplitude ratio", dRatio, 0, ratioUpperBound,
               dRatio * 5, 1 / maxRatio, maxRatio, extraExists);

       if (dataFeaturePath != null) {
           try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(snRatioPath))) {
               for (int i = 0; i < nSNRatio; i++)
                   pw.println(Precision.round(i * dSNRatio, DataFeature.PRECISION)
                           + " " + snRatios[i] + " " + extraSnRatios[i]);
           }
           createPlot(outPath, snRatioFileNameRoot, "Signal/Noise ratio", dSNRatio, 0, snRatioUpperBound,
                   dSNRatio * 5, minSNRatio, snRatioUpperBound, extraExists);
       }
   }

   private static void createPlot(Path outPath, String fileNameRoot, String xLabel, double interval,
           double minimum, double maximum, double xtics, double minRect, double maxRect, boolean extraExists) throws IOException {
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
           pw.println("set object 1 rect from first " + minRect + ",graph 0 to first " + maxRect + ",graph 1 back lw 0 fillcolor rgb 'light-gray'");
           if (extraExists) {
               // "($2+$3)" is to stack up the amounts
               pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):($2+$3) w boxes lw 2.5 lc 'pink' title  \"unused\" ,\\");
               pw.println("     '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'red' title \"used\"");
           } else {
               pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc 'red' notitle");
           }
       }

       GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
       histogramPlot.execute();
   }
}
