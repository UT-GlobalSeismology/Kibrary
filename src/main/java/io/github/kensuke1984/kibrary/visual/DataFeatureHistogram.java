package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
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
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Operation that creates hisograms of normalized variance, amplitude ratio, and cross correlation (and in some cases S/N ratio)
 * between observed and synthetic waveforms.
 * <p>
 * Either a {@link DataFeatureListFile} or a {@link BasicIDFile} pair must be given as input.
 *
 * <p>
 * When a {@link DataFeatureListFile} is given as input,
 * all {@link DataFeature}s listed in this file that satisfy the following criteria will be counted:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> the (event, observer, component)-pair exists in the {@link DataEntryListFile} if it is given </li>
 * </ul>
 * If {@link DataFeature#isSelected()} is true, it will be counted in the 'main' list;
 * otherwise, it will be counted in the 'extra' list.
 * Histograms for S/N ratios will be created along with the 3 others in this case.
 *
 * <p>
 * When a {@link BasicIDFile} pair is given as input,
 * all {@link BasicID}s that satisfy the following criteria will be counted:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> the (event, observer, component)-pair exists in the {@link DataEntryListFile} if it is given </li>
 * </ul>
 * {@link BasicID}s in the 'main' {@link BasicIDFile} will be counted in the 'main' list.
 * If an 'extra' {@link BasicIDFile} is provided, {@link BasicID}s in there will be counted in the 'extra' list.
 * If a {@link TimewindowDataFile} containing information of 'improvement windows' is provided,
 * normalized variance, amplitude ratio, and cross correlation values will be computed within those windows.
 * Otherwise, they will be computed for the whole length included in the {@link BasicIDFile}.
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
     * set of {@link SACComponent}
     */
    private Set<SACComponent> components;

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
     * Path of a timewindow data file of improvement windows
     */
    private Path improvementWindowPath;

    /**
     * Color of histograms to create
     */
    private String color;
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
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##########Either a data feature file or a basicID&waveform file pair must be set.");
            pw.println("##########Settings using data feature file");
            pw.println("##Path of a data feature list file. If this is not set, the following basicID&waveform files will be used.");
            pw.println("##  The S/N ratio histogram can be created only when dataFeaturePath is set.");
            pw.println("#dataFeaturePath dataFeature.lst");
            pw.println("##########Settings using basic ID and waveform files");
            pw.println("##Path of a basic ID file, must be set if dataFeaturePath is not set");
            pw.println("#mainBasicIDPath ");
            pw.println("##Path of a basic waveform file, must be set if dataFeaturePath is not set");
            pw.println("#mainBasicPath ");
            pw.println("##Path of an additional basic ID file, if any (e.g. data not used in inversion)");
            pw.println("#extraBasicIDPath ");
            pw.println("##Path of an additional basic waveform file, if any (e.g. data not used in inversion)");
            pw.println("#extraBasicPath ");
            pw.println("##Path of a timewindow data file of improvement windows, if you want to use those windows");
            pw.println("##  This is only used when basic ID and waveform files are used, not a data feature file.");
            pw.println("#improvementWindowPath timewindow.dat");
            pw.println("##########Common settings");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Color of histograms to create, from {red, green, blue} (red)");
            pw.println("#color ");
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
            pw.println("##########The following are parameters that decide the range of the background shaded box.");
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
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

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
        if (property.containsKey("improvementWindowPath")) {
            improvementWindowPath = property.parsePath("improvementWindowPath", null, true, workPath);
        }

        color = property.parseString("color", "red");
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

       // read entry set for selection
       Set<DataEntry> entrySet = (dataEntryPath != null) ? DataEntryListFile.readAsSet(dataEntryPath) : null;

       // read data features
       Set<DataFeature> featureSet;
       Set<DataFeature> extraFeatureSet = null;
       if (dataFeaturePath != null) {
           // the DataFeatureListFile includes information of whether the timewindow is selected, so use that to filter the features
           Set<DataFeature> tempFeatureSet = DataFeatureListFile.read(dataFeaturePath).stream()
                   .filter(feature -> components.contains(feature.getTimewindow().getComponent()))
                   .filter((dataEntryPath == null) ? (feature -> true) : (feature -> entrySet.contains(feature.getTimewindow().toDataEntry())))
                   .collect(Collectors.toSet());
           featureSet = tempFeatureSet.stream().filter(feature -> feature.isSelected()).collect(Collectors.toSet());
           extraFeatureSet = tempFeatureSet.stream().filter(feature -> !feature.isSelected()).collect(Collectors.toSet());
       } else {
           // read the improvement windows if the file is given
           Set<TimewindowData> improvementWindowSet = null;
           if (improvementWindowPath != null) {
               improvementWindowSet = TimewindowDataFile.read(improvementWindowPath);
           }

           // read the main data features from the main basic files
           List<BasicID> mainBasicIDs = BasicIDFile.readAsList(mainBasicIDPath, mainBasicPath).stream()
                   .filter(id -> components.contains(id.getSacComponent()))
                   .filter((dataEntryPath == null) ? (feature -> true) : (id -> entrySet.contains(id.toDataEntry())))
                   .collect(Collectors.toList());
           featureSet = extractFeatures(mainBasicIDs, true, improvementWindowSet);
           // read extra data features if the extra basic files exist
           if (extraBasicIDPath != null) {
               List<BasicID> extraBasicIDs = BasicIDFile.readAsList(extraBasicIDPath, extraBasicPath).stream()
                       .filter(id -> components.contains(id.getSacComponent()))
                       .filter((dataEntryPath == null) ? (feature -> true) : (id -> entrySet.contains(id.toDataEntry())))
                       .collect(Collectors.toList());
               extraFeatureSet = extractFeatures(extraBasicIDs, false, improvementWindowSet);
           }
       }

       outPath = DatasetAid.createOutputFolder(workPath, "featureHistogram", folderTag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       // if input is in BasicID, export their features (for reference)
       if (mainBasicIDPath != null) {
           Path outFeaturePath = outPath.resolve("dataFeature.lst");
           DataFeatureListFile.write(featureSet, outFeaturePath);
           // if extra BasicID files are set, export for them as well
           if (extraFeatureSet != null) {
               Path outExtraFeaturePath = outPath.resolve("extraDataFeature.lst");
               DataFeatureListFile.write(extraFeatureSet, outExtraFeaturePath);
           }
       }

       createHistograms(featureSet, extraFeatureSet);
   }

   private Set<DataFeature> extractFeatures(List<BasicID> basicIDs, boolean selected, Set<TimewindowData> improvementWindowSet) {
       Set<DataFeature> featureSet = new HashSet<>();

       // sort observed and synthetic
       BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
       List<BasicID> obsIDs = pairer.getObsList();
       List<BasicID> synIDs = pairer.getSynList();

       // create dataFeatures from basicIDs
       for (int i = 0; i < obsIDs.size(); i++) {
           BasicID obsID = obsIDs.get(i);
           BasicID synID = synIDs.get(i);

           RealVector obsU;
           RealVector synU;
           TimewindowData timewindow;
           if (improvementWindowSet == null) {
               // if improvement window does not exist, use the whole window
               obsU = new ArrayRealVector(obsID.getData(), false);
               synU = new ArrayRealVector(synID.getData(), false);
               // Start time of synthetic waveform must be used, since it is the correct one when time shift is applied.
               double startTime = synID.getStartTime();
               double endTime = synID.computeEndTime();
               timewindow = new TimewindowData(startTime, endTime,
                       synID.getObserver(), synID.getGlobalCMTID(), synID.getSacComponent(), synID.getPhases());
               // snRatio cannot be decided, so set 0
               DataFeature feature = DataFeature.create(timewindow, obsU, synU, 0, selected);
               featureSet.add(feature);
           } else {
               // if improvement window exists, cut to that window
               Set<TimewindowData> improvementWindows = findImprovementWindows(synID, improvementWindowSet);
               for (TimewindowData improvementWindow : improvementWindows) {
                   // Time frame of synthetic waveform must be used, since it is the correct one when time shift is applied.
                   double[] x = synID.toTrace().cutWindow(improvementWindow).getX();
                   double startTime = x[0];
                   double endTime = x[x.length - 1];
                   // observed waveform must be shifted before cutting
                   obsU = obsID.toTrace().withXAs(synID.toTrace().getX()).cutWindow(startTime, endTime).getYVector();
                   synU = synID.toTrace().cutWindow(startTime, endTime).getYVector();
                   timewindow = new TimewindowData(startTime, endTime,
                           synID.getObserver(), synID.getGlobalCMTID(), synID.getSacComponent(), synID.getPhases());
                   // snRatio cannot be decided, so set 0
                   DataFeature feature = DataFeature.create(timewindow, obsU, synU, 0, selected);
                   featureSet.add(feature);
               }
           }
       }
       return featureSet;
   }

   private static Set<TimewindowData> findImprovementWindows(BasicID basicID, Set<TimewindowData> improvementWindowSet) {
       Set<TimewindowData> matchingWindows = improvementWindowSet.stream()
               .filter(window -> window.getGlobalCMTID().equals(basicID.getGlobalCMTID())
                       && window.getObserver().equals(basicID.getObserver())
                       && window.getComponent().equals(basicID.getSacComponent())
                       // there must be some overlap between the windows
                       && window.getStartTime() < basicID.computeEndTime()
                       && basicID.getStartTime() < window.getEndTime())
               .collect(Collectors.toSet());
       return matchingWindows;
   }

   /**
     * @param featureList (List of DataFeature) The main data feature list.
     * @param extraFeatureList (List of DataFeature) Extra list. This shall be null if there is no extra list.
     * @throws IOException
     */
    private void createHistograms(Set<DataFeature> featureList, Set<DataFeature> extraFeatureList) throws IOException {
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
       createPlot(corrFileNameRoot, "Correlation", dCorrelation, correlationLowerBound, correlationUpperBound,
               dCorrelation * 5, minCorrelation, maxCorrelation, true, extraExists);
       createPlot(varFileNameRoot, "Normalized variance", dVariance, 0, varianceUpperBound,
               dVariance * 5, minVariance, maxVariance, false, extraExists);
       createPlot(ratioFileNameRoot, "Syn/Obs amplitude ratio", dRatio, 0, ratioUpperBound,
               dRatio * 5, 1 / maxRatio, maxRatio, false, extraExists);

       if (dataFeaturePath != null) {
           try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(snRatioPath))) {
               for (int i = 0; i < nSNRatio; i++)
                   pw.println(Precision.round(i * dSNRatio, DataFeature.PRECISION)
                           + " " + snRatios[i] + " " + extraSnRatios[i]);
           }
           createPlot(snRatioFileNameRoot, "Signal/Noise ratio", dSNRatio, 0, snRatioUpperBound,
                   dSNRatio * 5, minSNRatio, snRatioUpperBound, false, extraExists);
       }
   }

   private void createPlot(String fileNameRoot, String xLabel, double interval, double minimum, double maximum,
           double xtics, double minRect, double maxRect, boolean keyLeft, boolean extraExists) throws IOException {
       Path scriptPath = outPath.resolve(fileNameRoot + ".plt");

       String mainColor;
       String extraColor;
       switch (color) {
       case "red":
           mainColor = "red"; extraColor = "pink"; break;
       case "green":
           mainColor = "web-green"; extraColor = "seagreen"; break;
       case "blue":
           mainColor = "web-blue"; extraColor = "light-cyan"; break;
       default:
           throw new IllegalArgumentException("Color unrecognizable");
       }

       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
           pw.println("set term pngcairo enhanced font 'Helvetica,20'");
           pw.println("set xlabel '" + xLabel + "'");
           pw.println("set ylabel '#timewindows'");
           pw.println("set xrange [" + minimum + ":" + maximum + "]");
           pw.println("#set yrange [0:1000]");
           pw.println("set xtics " + xtics + " nomirror");
           pw.println("set ytics nomirror");
           if (keyLeft) pw.println("set key left top");
           else pw.println("#set key left top");
           pw.println("set style fill solid border lc rgb 'black'");
           pw.println("set sample 11");
           pw.println("set output '" + fileNameRoot + ".png'");
           pw.println("set object 1 rect from first " + minRect + ",graph 0 to first " + maxRect + ",graph 1 behind lw 0 fillcolor rgb 'light-gray'");
           if (extraExists) {
               // "($2+$3)" is to stack up the amounts
               pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):($2+$3) w boxes lw 2.5 lc '"
                       + extraColor + "' title  \"unused\" ,\\");
               pw.println("     '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc '"
                       + mainColor + "' title \"used\"");
           } else {
               pw.println("plot '" + fileNameRoot + ".txt' u ($1+" + (interval / 2) + "):2 w boxes lw 2.5 lc '"
                       + mainColor + "' notitle");
           }
       }

       GnuplotFile histogramPlot = new GnuplotFile(scriptPath);
       histogramPlot.execute();
   }
}
