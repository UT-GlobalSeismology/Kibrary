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
import java.util.stream.Stream;

import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Test_temp;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowData;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowDataFile;
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
 * Time windows in the input {@link TimeWindowDataFile} that satisfy the following criteria will be worked for:
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
 * Selected time windows will be written in binary format in "selectedTimeWindow*.dat".
 * See {@link TimeWindowDataFile}.
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
    private static final Set<Phase> PSV_PHASES = Arrays.stream("p P Pdiff".split("\\s+")).map(Phase::create).collect(Collectors.toSet());
    private static final Set<Phase> SH_PHASES = Arrays.stream("s S Sdiff".split("\\s+")).map(Phase::create).collect(Collectors.toSet());
    private static final String[] PHASE_NAMES = Stream.concat(PSV_PHASES.stream(), SH_PHASES.stream()).map(Phase::toString).toArray(String[]::new);
    private static final double NOISE_WINDOW_OFFSET = 20;
    private static final double NOISE_WINDOW_LENGTH = 50;

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
    private Path timeWindowPath;
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
    /**
     * Path of static correction file.
     */
    private Path staticCorrectionPath;
    /**
     * Name of structure to compute travel times.
     */
    private String structureName;

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
     * Threshold of Sobs/Nobs norm ratio that is to be selected.
     */
    private double lowerSNRatio;
    /**
     * Threshold of Sobs/Nobs ratio that is to be selected.
     */
    private double lowerObsSNRatio;
    /**
     * Threshold of Ssyn/Nobs ratio that is to be selected.
     */
    private double lowerSynSNRatio;
    private boolean requirePhase;
    private boolean excludeSurfaceWave;

    private Set<TimeWindowData> sourceTimeWindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;
    private Set<DataFeature> dataFeatureSet = Collections.synchronizedSet(new HashSet<>());
    private Set<TimeWindowData> goodTimeWindowSet = Collections.synchronizedSet(new HashSet<>());

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Sac components to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timeWindowPath timeWindow.dat");
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
            pw.println("##(String) Name of structure to compute travel times using TauP. (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Threshold of static correction time shift [s]. (10.)");
            pw.println("#upperStaticShift ");
            pw.println("##(double) Lower threshold of correlation, inclusive; [-1:maxCorrelation). (0)");
            pw.println("#lowerCorrelation ");
            pw.println("##(double) Upper threshold of correlation, exclusive; (minCorrelation:1]. (1)");
            pw.println("#upperCorrelation ");
            pw.println("##(double) Lower threshold of normalized variance, inclusive; [0:maxVariance). (0)");
            pw.println("#lowerVariance ");
            pw.println("##(double) Upper threshold of normalized variance, exclusive; (minVariance:). (2)");
            pw.println("#upperVariance 2.5");
            pw.println("##(double) Lower threshold of amplitude ratio, inclusive; [0:maxRatio). (0.5)");
            pw.println("#lowerRatio ");
            pw.println("##(double) Upper threshold of amplitude ratio, exclusive; (minRatio:). (2)");
            pw.println("#upperRatio ");
            pw.println("##(double) Threshold of Sobs/Nobs norm ratio (lower limit), inclusive; [0:). (0)");
            pw.println("#lowerSNRatio ");
            pw.println("##(double) Threshold of Sobs/Nobs ratio (lower limit), inclusive; [0:). (0)");
            pw.println("#lowerObsSNRatio 1.2");
            pw.println("##(double) Threshold of Ssyn/Nobs ratio (lower limit), inclusive; [0:). (0)");
            pw.println("#lowerSynSNRatio 1.4");
            pw.println("##(boolean) Whether to require phases to be included in time window. (true)");
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

        timeWindowPath = Test_temp.getTimeWindowPath_temp(property, workPath);  //TODO delete (This is here for backward compatibility.)
//      timeWindowPath = property.parsePath("timeWindowPath", null, true, workPath);
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");
        if (property.containsKey("staticCorrectionPath")) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }
        structureName = property.parseString("structureName", "prem").toLowerCase();

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
        lowerSNRatio = property.parseDouble("lowerSNRatio", "0");
        if (lowerSNRatio < 0)
            throw new IllegalArgumentException("Sobs/Nobs norm ratio threshold " + lowerSNRatio + " is invalid, must be >= 0.");
        lowerObsSNRatio = property.parseDouble("lowerObsSNRatio", "0");
        if (lowerObsSNRatio < 0)
            throw new IllegalArgumentException("Sobs/Nobs ratio threshold " + lowerObsSNRatio + " is invalid, must be >= 0.");
        lowerSynSNRatio = property.parseDouble("lowerSynSNRatio", "0");
        if (lowerSynSNRatio < 0)
            throw new IllegalArgumentException("Ssyn/Nobs ratio threshold " + lowerSynSNRatio + " is invalid, must be >= 0.");
        requirePhase = property.parseBoolean("requirePhase", "true");
        excludeSurfaceWave = property.parseBoolean("excludeSurfaceWave", "false");
    }

    @Override
    public void run() throws IOException {
        // gather all time windows to be processed
        sourceTimeWindowSet = TimeWindowDataFile.read(timeWindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());
        // collect all events that exist in the time window set
        Set<GlobalCMTID> eventSet = sourceTimeWindowSet.stream().map(TimeWindowData::getGlobalCMTID).collect(Collectors.toSet());

        // read static corrections
        staticCorrectionSet = (staticCorrectionPath == null ? Collections.emptySet() :
                StaticCorrectionDataFile.read(staticCorrectionPath));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        System.err.println("Working for " + eventSet.size() + " events.");
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        System.err.println(MathAid.switchSingularPlural(goodTimeWindowSet.size(), "time window is", "time windows are") + " selected.");

        // output
        String dateString = GadgetAid.getTemporaryString();
        Path outputFeaturePath = DatasetAid.generateOutputFilePath(workPath, "dataFeature", fileTag, appendFileDate, dateString, ".lst");
        Path outputSelectedPath = DatasetAid.generateOutputFilePath(workPath, "selectedTimeWindow", fileTag, appendFileDate, dateString, ".dat");
        if (goodTimeWindowSet.size() > 0) TimeWindowDataFile.write(goodTimeWindowSet, outputSelectedPath);
        if (dataFeatureSet.size() > 0) DataFeatureListFile.write(dataFeatureSet, outputFeaturePath);
    }

    /**
     * @param sac ({@link SACFileAccess}) SAC file to cut out from.
     * @param timeWindow ({@link TimeWindow}) Time window to cut out.
     * @return ({@link Trace}) Waveform cut out for the time window.
     */
    private RealVector cutSAC(SACFileAccess sac, TimeWindow timeWindow) {
        Trace trace = sac.createTrace();
        return trace.cutWindow(timeWindow, sacSamplingHz).getYVector();
    }

    private boolean check(DataFeature feature) throws IOException {
        double correlation = feature.getCorrelation();
        double variance = feature.getVariance();
        double posSideRatio = feature.getNegSideRatio();
        double negSideRatio = feature.getPosSideRatio();
        double absRatio = feature.getAbsRatio();
        double snRatio = feature.getSNRatio();
        double obsSNRatio = feature.getObsSNRatio();
        double synSNRatio = feature.getSynSNRatio();

        boolean isok = correlationRange.check(correlation) && varianceRange.check(variance) &&
                ratioRange.check(posSideRatio) && ratioRange.check(negSideRatio) && ratioRange.check(absRatio) &&
                (lowerSNRatio <= snRatio) && (lowerObsSNRatio <= obsSNRatio) && (lowerSynSNRatio <= synSNRatio);
        return isok;
    }

    /**
     * @param sac ({@link SACFileAccess}) SAC file.
     * @param component ({@link SACComponent}) Component.
     * @param timeTool (TauP_Time) TauP tool instance for this event.
     * @return (double) Norm of vector of noise per second.
     *
     * @author anselme
     */
    private double noisePerSecond(SACFileAccess sac, SACComponent component, TauP_Time timeTool, boolean forMax) {
        double firstArrivalTime = 0;
        try {
            double distance = sac.getValue(SACHeaderEnum.GCARC);
            timeTool.calculate(distance);
            List<Arrival> arrivals = timeTool.getArrivals();
            switch (component) {
            case T:
                List<Arrival> shArrivals = arrivals.stream()
                        .filter(arrival -> SH_PHASES.contains(Phase.create(arrival.getPhase().getName()))).collect(Collectors.toList());
                if (shArrivals.size() == 0)
                    throw new IllegalArgumentException("No arrivals for " + sac.getObserver() + " " + sac.getGlobalCMTID()
                            + " (" + distance + " deg)");
                firstArrivalTime = shArrivals.get(0).getTime();
                break;
            case Z:
            case R:
                List<Arrival> psvArrivals = arrivals.stream()
                        .filter(arrival -> PSV_PHASES.contains(Phase.create(arrival.getPhase().getName()))).collect(Collectors.toList());
                if (psvArrivals.size() == 0)
                    throw new IllegalArgumentException("No arrivals for " + sac.getObserver() + " " + sac.getGlobalCMTID()
                            + " (" + distance + " deg)");
                firstArrivalTime = psvArrivals.get(0).getTime();
                break;
            default:
                break;
            }
        } catch (TauModelException e) {
            e.printStackTrace();
        }
        if (forMax) {
            return sac.createTrace().cutWindow(firstArrivalTime - NOISE_WINDOW_OFFSET - NOISE_WINDOW_LENGTH, firstArrivalTime - NOISE_WINDOW_OFFSET)
                    .getYVector().getLInfNorm();
        } else {
            return sac.createTrace().cutWindow(firstArrivalTime - NOISE_WINDOW_OFFSET - NOISE_WINDOW_LENGTH, firstArrivalTime - NOISE_WINDOW_OFFSET)
                    .getYVector().getNorm() / NOISE_WINDOW_LENGTH;
        }
    }

    private class Worker extends DatasetAid.FilteredDatasetWorker {
        private TauP_Time timeTool;

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sacSamplingHz, sourceTimeWindowSet);

            try {
                timeTool = new TauP_Time(structureName);
                timeTool.setPhaseNames(PHASE_NAMES);
                timeTool.setSourceDepth(eventID.getEventData().getCmtPosition().getDepth());
            } catch (TauModelException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void actualWork(TimeWindowData timeWindow, SACFileAccess obsSac, SACFileAccess synSac) {
            SACComponent component = timeWindow.getComponent();

            // check SAC file end time
            if (timeWindow.getEndTime() > obsSac.getValue(SACHeaderEnum.E)
                    || timeWindow.getEndTime() > synSac.getValue(SACHeaderEnum.E)) {
                System.err.println();
                System.err.println("!! End of time window too late, skipping: " + timeWindow);
                return;
            }

            // check phase
            if (requirePhase && timeWindow.getPhases().length == 0) {
                System.err.println();
                System.err.println("!! No phase, skipping: " + timeWindow);
                return;
            }

            try {
                // remove surface wave from window
                if (excludeSurfaceWave) {
                    Trace synTrace = synSac.createTrace();
                    SurfaceWaveDetector detector = new SurfaceWaveDetector(synTrace, 20.);
                    TimeWindow surfaceWaveWindow = detector.getSurfaceWaveWindow();

                    if (surfaceWaveWindow != null) {
                        double endTime = timeWindow.getEndTime();
                        double startTime = timeWindow.getStartTime();
                        if (startTime >= surfaceWaveWindow.getStartTime() && endTime <= surfaceWaveWindow.getEndTime())
                            return;
                        if (endTime > surfaceWaveWindow.getStartTime() && startTime < surfaceWaveWindow.getStartTime())
                            endTime = surfaceWaveWindow.getStartTime();
                        if (startTime < surfaceWaveWindow.getEndTime() && endTime > surfaceWaveWindow.getEndTime())
                            startTime = surfaceWaveWindow.getEndTime();

                        timeWindow = new TimeWindowData(startTime, endTime, timeWindow.getObserver(),
                                timeWindow.getGlobalCMTID(), timeWindow.getComponent(), timeWindow.getPhases());
                    }
                }

                // apply static correction
                double shift = 0.;
                if (!staticCorrectionSet.isEmpty()) {
                    StaticCorrectionData correction = StaticCorrectionData.findForTimeWindow(staticCorrectionSet, timeWindow);
                    if (correction == null) {
                        System.err.println();
                        System.err.println("!! No static correction data, skipping: " + timeWindow);
                        return;
                    }
                    shift = correction.getTimeshift();
                    if (Math.abs(shift) > upperStaticShift) {
                        System.err.println();
                        System.err.println("!! Time shift too large, skipping: " + timeWindow);
                        return;
                    }
                }

                // cut out waveforms
                RealVector synU = cutSAC(synSac, timeWindow);
                RealVector obsU = cutSAC(obsSac, timeWindow.shift(-shift));

                // signal-to-noise ratio
                double noiseNorm = noisePerSecond(obsSac, component, timeTool, false);
                double obsSignal = obsU.getNorm() / (timeWindow.getEndTime() - timeWindow.getStartTime());
                double snRatio = obsSignal / noiseNorm;

                double noiseMax = noisePerSecond(obsSac, component, timeTool, true);
                double obsMax = obsU.getLInfNorm();
                double obsSNRatio = obsMax / noiseMax;
                double synMax = synU.getLInfNorm();
                double synSNRatio = synMax / noiseMax;

                // select by features
                DataFeature feature = DataFeature.create(timeWindow, obsU, synU, snRatio, obsSNRatio, synSNRatio, false);
                if (check(feature)) {
                    feature.setSelected(true);
                    goodTimeWindowSet.add(timeWindow);
                }
                dataFeatureSet.add(feature);

            } catch (Exception e) {
                System.err.println();
                System.err.println("!! Skipping because an error occurs: " + timeWindow);
                e.printStackTrace();
            }
        }

    }

}
