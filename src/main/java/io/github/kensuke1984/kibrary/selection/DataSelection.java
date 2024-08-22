package io.github.kensuke1984.kibrary.selection;


import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that selects satisfactory observed and synthetic data
 * based on amplitude ratio, correlation, and/or variance.
 * <p>
 * Time windows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> observed waveform data exists for the (event, observer, component)-pair </li>
 * <li> synthetic waveform data exists for the (event, observer, component)-pair </li>
 * </ul>
 * Both observed and synthetic data must be in event folders under obsDir and synDir (they can be the same folder).
 * Resulting data selection entries will be created for each time window,
 * thus specified by a (event, observer, component, timeframe)-pair.
 * <p>
 * When a {@link StaticCorrectionDataFile} is given as input, time shifts will be applied to each time window.
 * <p>
 * Selected time windows will be written in binary format in "selectedTimewindow*.dat".
 * See {@link TimewindowDataFile}.
 * <p>
 * Information of data features used in data selection will be written in ascii format in "dataFeature*.lst".
 * See {@link DataFeatureListFile}.
 * <p>
 * Time windows with no phases will be written in standard output.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class DataSelection extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of the input time window file.
     */
    private Path timewindowPath;
    /**
     * Folder containing observed data.
     */
    private Path obsPath;
    /**
     * Folder containing synthetic data.
     */
    private Path synPath;
    /**
     * Whether the synthetics have already been convolved.
     */
    private boolean convolved;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;
    private Path staticCorrectionPath;

    /**
     * Maximum of static correction shift.
     */
    private double upperStaticShift;
    /**
     * Correlation coefficient range.
     */
    private LinearRange correlationRange;
    /**
     * Normalized variance range.
     */
    private LinearRange varianceRange;
    /**
     * Amplitude ratio range.
     */
    private LinearRange ratioRange;
    /**
     * Threshold of S/N ratio that is to be selected.
     */
    private double lowerSNratio;
    private boolean requirePhase;
    private boolean excludeSurfaceWave;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;
    private Set<DataFeature> dataFeatureSet = Collections.synchronizedSet(new HashSet<>());
    private Set<TimewindowData> goodTimewindowSet = Collections.synchronizedSet(new HashSet<>());

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Sac components to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved. (true)");
            pw.println("#convolved ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##Path of a static correction file, if static correction time-shift shall be applied.");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##(double) Threshold of static correction time shift [s]. (10.)");
            pw.println("#upperStaticShift ");
            pw.println("##(double) Lower threshold of correlation, inclusive; [-1:maxCorrelation). (0)");
            pw.println("#lowerCorrelation ");
            pw.println("##(double) Upper threshold of correlation, exclusive; (minCorrelation:1]. (1)");
            pw.println("#upperCorrelation ");
            pw.println("##(double) Lower threshold of normalized variance, inclusive; [0:maxVariance). (0)");
            pw.println("#lowerVariance ");
            pw.println("##(double) Upper threshold of normalized variance, exclusive; (minVariance:). (2)");
            pw.println("#upperVariance ");
            pw.println("##(double) Lower threshold of amplitude ratio, inclusive; [0:maxRatio). (0.5)");
            pw.println("#lowerRatio ");
            pw.println("##(double) Upper threshold of amplitude ratio, exclusive; (minRatio:). (2)");
            pw.println("#upperRatio ");
            pw.println("##(double) Threshold of S/N ratio (lower limit), inclusive; [0:). (0)");
            pw.println("#lowerSNratio ");
            pw.println("##(boolean) Whether to require phases to be included in timewindow. (true)");
            pw.println("#requirePhase ");
            pw.println("##(boolean) Whether to exclude surface wave. (false)");
            pw.println("#excludeSurfaceWave ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataSelection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");
        if (property.containsKey("staticCorrectionPath")) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }

        upperStaticShift = property.parseDouble("upperStaticShift", "10.");
        if (upperStaticShift < 0)
            throw new IllegalArgumentException("Static shift threshold " + upperStaticShift + " is invalid, must be >= 0.");
        double lowerCorrelation = property.parseDouble("lowerCorrelation", "0");
        double upperCorrelation = property.parseDouble("upperCorrelation", "1");
        correlationRange = new LinearRange("Correlation", lowerCorrelation, upperCorrelation, -1.0, 1.0);
        double lowerVariance = property.parseDouble("lowerVariance", "0");
        double upperVariance = property.parseDouble("upperVariance", "2");
        varianceRange = new LinearRange("Variance", lowerVariance, upperVariance, 0.0);
        double lowerRatio = property.parseDouble("lowerRatio", "0.5");
        double upperRatio = property.parseDouble("upperRatio", "2");
        ratioRange = new LinearRange("Ratio", lowerRatio, upperRatio, 0.0);
        lowerSNratio = property.parseDouble("lowerSNratio", "0");
        if (lowerSNratio < 0)
            throw new IllegalArgumentException("S/N ratio threshold " + lowerSNratio + " is invalid, must be >= 0.");
        requirePhase = property.parseBoolean("requirePhase", "true");
        excludeSurfaceWave = property.parseBoolean("excludeSurfaceWave", "false");
    }

    @Override
    public void run() throws IOException {
        // gather all time windows to be processed
        sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());
        // collect all events that exist in the time window set
        Set<GlobalCMTID> eventSet = sourceTimewindowSet.stream().map(TimewindowData::getGlobalCMTID)
                .collect(Collectors.toSet());

        // read static corrections
        staticCorrectionSet = (staticCorrectionPath == null ? Collections.emptySet()
                : StaticCorrectionDataFile.read(staticCorrectionPath));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        System.err.println(MathAid.switchSingularPlural(goodTimewindowSet.size(), "time window is", "time windows are") + " selected.");

        // output
        String dateString = GadgetAid.getTemporaryString();
        Path outputFeaturePath = DatasetAid.generateOutputFilePath(workPath, "dataFeature", fileTag, appendFileDate, dateString, ".lst");
        Path outputSelectedPath = DatasetAid.generateOutputFilePath(workPath, "selectedTimewindow", fileTag, appendFileDate, dateString, ".dat");
        if (goodTimewindowSet.size() > 0) TimewindowDataFile.write(goodTimewindowSet, outputSelectedPath);
        if (dataFeatureSet.size() > 0) DataFeatureListFile.write(dataFeatureSet, outputFeaturePath);
    }

    /**
     * @param sac        {@link SACFileAccess} to cut
     * @param timeWindow time window
     * @return new Trace for the timewindow [tStart:tEnd]
     */
    private RealVector cutSAC(SACFileAccess sac, Timewindow timewindow) {
        Trace trace = sac.createTrace();
        return trace.cutWindow(timewindow, sacSamplingHz).getYVector();
    }

    private StaticCorrectionData getStaticCorrection(TimewindowData window) {
        List<StaticCorrectionData> corrs = staticCorrectionSet.stream().filter(s -> s.isForTimewindow(window)).collect(Collectors.toList());
        if (corrs.size() > 1) {
            throw new RuntimeException("Found more than 1 static correction for window " + window);
        } else if (corrs.size() == 0) {
            return null;
        } else {
            return corrs.get(0);
        }
    }

    private boolean check(DataFeature feature) throws IOException {
        double posSideRatio = feature.getNegSideRatio();
        double negSideRatio = feature.getPosSideRatio();
        double absRatio = feature.getAbsRatio();
        double correlation = feature.getCorrelation();
        double variance = feature.getVariance();
        double snRatio = feature.getSNRatio();

        boolean isok = ratioRange.check(posSideRatio) && ratioRange.check(negSideRatio) && ratioRange.check(absRatio) &&
                correlationRange.check(correlation) && varianceRange.check(variance) && (lowerSNratio <= snRatio);
        return isok;
    }

    /**
     * @param sac
     * @param component
     * @return
     * @author anselme
     */
    private double noisePerSecond(SACFileAccess sac, SACComponent component) {
        double len = 50;
        double distance = sac.getValue(SACHeaderEnum.GCARC);
        double depth = sac.getValue(SACHeaderEnum.EVDP);
        double firstArrivalTime = 0;
        try {
            TauP_Time timeTool = new TauP_Time("prem");
            switch (component) {
            case T:
                timeTool.parsePhaseList("S, Sdiff, s");
                timeTool.setSourceDepth(depth);
                timeTool.calculate(distance);
                if (timeTool.getNumArrivals() == 0)
                    throw new IllegalArgumentException("No arrivals for " + sac.getObserver() + " " + sac.getGlobalCMTID()
                            + " " + String.format("(%.2f deg, %.2f km)", distance, depth));
                firstArrivalTime = timeTool.getArrival(0).getTime();
                break;
            case Z:
            case R:
                timeTool.parsePhaseList("P, Pdiff, p");
                timeTool.setSourceDepth(depth);
                timeTool.calculate(distance);
                if (timeTool.getNumArrivals() == 0)
                    throw new IllegalArgumentException("No arrivals for " + sac.getObserver() + " " + sac.getGlobalCMTID()
                            + " " + String.format("(%.2f deg, %.2f km)", distance, depth));
                firstArrivalTime = timeTool.getArrival(0).getTime();
                break;
            default:
                break;
            }
        } catch (TauModelException e) {
            e.printStackTrace();
        }

        return sac.createTrace().cutWindow(firstArrivalTime - 20 - len, firstArrivalTime - 20).getYVector().getNorm() / len;
    }

    private class Worker extends DatasetAid.FilteredDatasetWorker {

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sourceTimewindowSet);
        }

        @Override
        public void actualWork(TimewindowData timewindow, SACFileAccess obsSac, SACFileAccess synSac) {
            try {
                SACComponent component = timewindow.getComponent();

                // check delta
                double delta = MathAid.roundForPrecision(1.0 / sacSamplingHz);
                if (delta != obsSac.getValue(SACHeaderEnum.DELTA) || delta != synSac.getValue(SACHeaderEnum.DELTA)) {
                    System.err.println();
                    System.err.println("!! Deltas are invalid, skipping: " + timewindow);
                    System.err.println("   Obs " + obsSac.getValue(SACHeaderEnum.DELTA)
                            + " , Syn " + synSac.getValue(SACHeaderEnum.DELTA) + " ; must be " + delta);
                    return;
                }

                // check SAC file end time
                if (timewindow.getEndTime() > obsSac.getValue(SACHeaderEnum.E)
                        || timewindow.getEndTime() > synSac.getValue(SACHeaderEnum.E)) {
                    System.err.println();
                    System.err.println("!! End time of timewindow too late, skipping: " + timewindow);
                    return;
                }

                // check phase
                if (requirePhase && timewindow.getPhases().length == 0) {
                    System.err.println();
                    System.err.println("!! No phase, skipping: " + timewindow);
                    return;
                }

                // remove surface wave from window
                if (excludeSurfaceWave) {
                    Trace synTrace = synSac.createTrace();
                    SurfaceWaveDetector detector = new SurfaceWaveDetector(synTrace, 20.);
                    Timewindow surfacewaveWindow = detector.getSurfaceWaveWindow();

                    if (surfacewaveWindow != null) {
                        double endTime = timewindow.getEndTime();
                        double startTime = timewindow.getStartTime();
                        if (startTime >= surfacewaveWindow.getStartTime() && endTime <= surfacewaveWindow.getEndTime())
                            return;
                        if (endTime > surfacewaveWindow.getStartTime() && startTime < surfacewaveWindow.getStartTime())
                            endTime = surfacewaveWindow.getStartTime();
                        if (startTime < surfacewaveWindow.getEndTime() && endTime > surfacewaveWindow.getEndTime())
                            startTime = surfacewaveWindow.getEndTime();

                        timewindow = new TimewindowData(startTime
                                , endTime, timewindow.getObserver(), timewindow.getGlobalCMTID()
                                , timewindow.getComponent(), timewindow.getPhases());
                    }
                }

                // apply static correction
                double shift = 0.;
                if (!staticCorrectionSet.isEmpty()) {
                    StaticCorrectionData correction = getStaticCorrection(timewindow);
                    if (correction == null) {
                        System.err.println();
                        System.err.println("!! No static correction data, skipping: " + timewindow);
                        return;
                    }
                    shift = correction.getTimeshift();
                    if (Math.abs(shift) > upperStaticShift) {
                        System.err.println();
                        System.err.println("!! Time shift too large, skipping: " + timewindow);
                        return;
                    }
                }
                TimewindowData shiftedWindow = new TimewindowData(timewindow.getStartTime() - shift
                        , timewindow.getEndTime() - shift, timewindow.getObserver()
                        , timewindow.getGlobalCMTID(), timewindow.getComponent(), timewindow.getPhases());

                // cut out waveforms
                RealVector synU = cutSAC(synSac, timewindow);
                RealVector obsU = cutSAC(obsSac, shiftedWindow);

                // signal-to-noise ratio
                double noise = noisePerSecond(obsSac, component);
                double signal = obsU.getNorm() / (timewindow.getEndTime() - timewindow.getStartTime());
                double snRatio = signal / noise;

                // select by features
                DataFeature feature = DataFeature.create(timewindow, obsU, synU, snRatio, false);
                if (check(feature)) {
                    feature.setSelected(true);
                    goodTimewindowSet.add(timewindow);
                }
                dataFeatureSet.add(feature);

            } catch (Exception e) {
                System.err.println();
                System.err.println("!! Skipping because an error occurs: " + timewindow);
                e.printStackTrace();
            }
        }

    }

}
