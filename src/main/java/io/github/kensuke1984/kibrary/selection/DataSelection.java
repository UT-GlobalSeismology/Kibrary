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

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that selects satisfactory observed and synthetic data
 * based on amplitude ratio, correlation, and/or variance.
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> observed waveform data exists for the (event, observer, component)-pair </li>
 * <li> synthetic waveform data exists for the (event, observer, component)-pair </li>
 * </ul>
 * Both observed and synthetic data must be in event folders under obsDir and synDir (they can be the same folder).
 * Resulting data selection entries will be created for each timewindow,
 * thus specified by a (event, observer, component, timeframe)-pair.
 * <p>
 * Selected timewindows will be written in binary format in "selectedTimewindow*.dat".
 * See {@link TimewindowDataFile}.
 * <p>
 * Information of data features used in data selection will be written in ascii format in "dataFeature*.lst".
 * See {@link DataFeatureListFile}.
 * <p>
 * Timewindows with no phases will be written in standard output.
 *
 * @author Kensuke Konishi
 * @version 0.1.2.1
 * @author anselme add additional selection critera
 */
public class DataSelection extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Path of the output information file
     */
    private Path outputFeaturePath;
    /**
     * Path of the output timewindow file
     */
    private Path outputSelectedPath;
    /**
     * components for computation
     */
    private Set<SACComponent> components;
    /**
     * sampling Hz [Hz] in sac files
     */
    private double sacSamplingHz;

    /**
     * the directory of observed data
     */
    private Path obsPath;
    /**
     * the directory of synthetic data
     */
    private Path synPath;
    /**
     * コンボリューションされている波形かそうでないか （両方は無理）
     */
    private boolean convolved;
    /**
     * Path of the input timewindow file
     */
    private Path timewindowPath;
    private Path staticCorrectionPath;

    /**
     * Maximum of static correction shift
     */
    private double maxStaticShift;
    /**
     * Minimum correlation coefficient
     */
    private double minCorrelation;
    /**
     * Maximum correlation coefficient
     */
    private double maxCorrelation;
    /**
     * Minimum normalized variance
     */
    private double minVariance;
    /**
     * Maximum normalized variance
     */
    private double maxVariance;
    /**
     * Minimum amplitude ratio
     */
    private double minRatio;
    /**
     * Maximum of amplitude ratio
     */
    private double maxRatio;
    /**
     * Threshold of S/N ratio that is selected
     */
    private double minSNratio;
    private boolean requirePhase;
    private boolean excludeSurfaceWave;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;
    private Set<DataFeature> dataFeatureSet;
    private Set<TimewindowData> goodTimewindowSet;

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
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##Sac components to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(double) sacSamplingHz (20)");
            pw.println("#sacSamplingHz cant change now");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved (true)");
            pw.println("#convolved ");
            pw.println("##Path of a timewindow file, must be defined");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a static correction file, if static correction time-shift shall be applied");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##(double) Threshold of static correction time shift (10.)");
            pw.println("#maxStaticShift ");
            pw.println("##(double) Lower threshold of correlation [-1:maxCorrelation] (0)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Upper threshold of correlation [minCorrelation:1] (1)");
            pw.println("#maxCorrelation ");
            pw.println("##(double) Lower threshold of normalized variance [0:maxVariance] (0)");
            pw.println("#minVariance ");
            pw.println("##(double) Upper threshold of normalized variance [minVariance:) (2)");
            pw.println("#maxVariance ");
            pw.println("##(double) Lower threshold of amplitude ratio [0:maxRatio] (0.5)");
            pw.println("#minRatio ");
            pw.println("##(double) Upper threshold of amplitude ratio [minRatio:) (2)");
            pw.println("#maxRatio ");
            pw.println("##(double) Threshold of S/N ratio (lower limit) [0:) (0)");
            pw.println("#minSNratio ");
            pw.println("##(boolean) Whether to require phases to be included in timewindow (true)");
            pw.println("#requirePhase ");
            pw.println("##(boolean) Whether to exclude surface wave (false)");
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
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        sacSamplingHz = 20; // TODO property.parseDouble("sacSamplingHz", "20");

        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("staticCorrectionPath")) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }

        maxStaticShift = property.parseDouble("maxStaticShift", "10.");
        if (maxStaticShift < 0)
            throw new IllegalArgumentException("Static shift threshold " + maxStaticShift + " is invalid, must be >= 0.");
        minCorrelation = property.parseDouble("minCorrelation", "0");
        maxCorrelation = property.parseDouble("maxCorrelation", "1");
        if (minCorrelation < -1 || minCorrelation > maxCorrelation || 1 < maxCorrelation)
            throw new IllegalArgumentException("Correlation range " + minCorrelation + " , " + maxCorrelation + " is invalid.");
        minVariance = property.parseDouble("minVariance", "0");
        maxVariance = property.parseDouble("maxVariance", "2");
        if (minVariance < 0 || minVariance > maxVariance)
            throw new IllegalArgumentException("Normalized variance range " + minVariance + " , " + maxVariance + " is invalid.");
        minRatio = property.parseDouble("minRatio", "0.5");
        maxRatio = property.parseDouble("maxRatio", "2");
        if (minRatio < 0 || minRatio > maxRatio)
            throw new IllegalArgumentException("Amplitude ratio range " + minRatio + " , " + maxRatio + " is invalid.");
        minSNratio = property.parseDouble("minSNratio", "0");
        if (minSNratio < 0)
            throw new IllegalArgumentException("S/N ratio threshold " + minSNratio + " is invalid, must be >= 0.");
        requirePhase = property.parseBoolean("requirePhase", "true");
        excludeSurfaceWave = property.parseBoolean("excludeSurfaceWave", "false");

        String dateStr = GadgetAid.getTemporaryString();
        outputFeaturePath = workPath.resolve(DatasetAid.generateOutputFileName("dataFeature", fileTag, dateStr, ".lst"));
        outputSelectedPath = workPath.resolve(DatasetAid.generateOutputFileName("selectedTimewindow", fileTag, dateStr, ".dat"));
        dataFeatureSet = Collections.synchronizedSet(new HashSet<>());
        goodTimewindowSet = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public void run() throws IOException {
        // gather all timewindows to be processed
        sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());
        // collect all events that exist in the timewindow set
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

        System.err.println(MathAid.switchSingularPlural(goodTimewindowSet.size(), "timewindow is", "timewindows are") + " selected.");
        TimewindowDataFile.write(goodTimewindowSet, outputSelectedPath);
        DataFeatureListFile.write(dataFeatureSet, outputFeaturePath);
    }

    /**
     * @param sac        {@link SACFileAccess} to cut
     * @param timeWindow time window
     * @return new Trace for the timewindow [tStart:tEnd]
     */
    private static RealVector cutSAC(SACFileAccess sac, Timewindow timeWindow) {
        Trace trace = sac.createTrace();
        double tStart = timeWindow.getStartTime();
        double tEnd = timeWindow.getEndTime();
        return new ArrayRealVector(trace.cutWindow(tStart, tEnd).getY(), false);
    }

    private StaticCorrectionData getStaticCorrection(TimewindowData window) {
        List<StaticCorrectionData> corrs = staticCorrectionSet.stream().filter(s -> s.isForTimewindow(window)).collect(Collectors.toList());
        if (corrs.size() > 1)
            throw new RuntimeException("Found more than 1 static correction for window " + window);
        if (corrs.size() == 0)
            throw new RuntimeException("Found no static correction for window " + window);
        return corrs.get(0);
    }

    /**
     * @param timewindow timewindow to shift
     * @return if there is time shift information for the input timewindow, then
     * creates new timewindow and returns it, otherwise, just returns
     * the input one.
     */
    private TimewindowData shift(TimewindowData timewindow) {
        if (staticCorrectionSet.isEmpty())
            return timewindow;
        StaticCorrectionData foundShift = getStaticCorrection(timewindow);
        double value = foundShift.getTimeshift();
        return new TimewindowData(timewindow.getStartTime() - value, timewindow.getEndTime() - value,
                foundShift.getObserver(), foundShift.getGlobalCMTID(), foundShift.getComponent(), timewindow.getPhases());
    }

    private boolean check(DataFeature feature) throws IOException {
        double posSideRatio = feature.getNegSideRatio();
        double negSideRatio = feature.getPosSideRatio();
        double absRatio = feature.getAbsRatio();
        double cor = feature.getCorrelation();
        double var = feature.getVariance();
        double sn = feature.getSNRatio();

        boolean isok = (minRatio <= posSideRatio && posSideRatio <= maxRatio)
                && (minRatio <= negSideRatio && negSideRatio <= maxRatio)
                && (minRatio <= absRatio && absRatio <= maxRatio) && (minCorrelation <= cor &&  cor <= maxCorrelation)
                && (minVariance <= var && var <= maxVariance) && (minSNratio <= sn);
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

                // check phase
                if (requirePhase && timewindow.getPhases().length == 0) {
                    System.out.println("!! No phase: " + timewindow);
                    return;
                }

                // check delta
                if (synSac.getValue(SACHeaderEnum.DELTA) != obsSac.getValue(SACHeaderEnum.DELTA)) {
                    System.err.println("!! DELTA does not match in obs and syn: " + timewindow);
                    return;
                }

                // check SAC file end time
                if (timewindow.getEndTime() > synSac.getValue(SACHeaderEnum.E) - 10) { // TODO should 10 be maxStaticShift ?
                    System.err.println("!! End time of timewindow too late: " + timewindow);
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
                    shift = correction.getTimeshift();
                }
                if (Math.abs(shift) > maxStaticShift) {
                    System.err.println("!! Time shift too large: " + timewindow);
                    return;
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
                System.err.println("!! " + timewindow + " is ignored because an error occurs.");
                e.printStackTrace();
            }
        }

    }

}
