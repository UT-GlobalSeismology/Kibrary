package io.github.kensuke1984.kibrary.correction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowData;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that computes values of Static correction after Obayashi <i>et al</i>., in prep.
 * <p>
 * Time windows in the input {@link TimeWindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> observed waveform data exists for the (event, observer, component)-pair </li>
 * <li> synthetic waveform data exists for the (event, observer, component)-pair </li>
 * </ul>
 * Both observed and synthetic data must be in event folders under obsDir and synDir (they can be the same folder).
 * Resulting static correction entries will be created for each time window,
 * thus specified by a (event, observer, component, timeframe)-pair.
 * <p>
 * The time shift value <i>t</i> indicates how much time the observed waveform should be shifted in the positive direction,
 * which means how much time the observed time window should be shifted in the negative direction.
 * So, use synthetic time window [t1 : t2] and observed time window [t1-t : t2-t].
 * <p>
 * The time shift values are computed as follows:
 * <blockquote>Go to event folders under the working directory.<br>
 * -------------------TODOここから書く---------------------------------
 * </blockquote>
 * <p>
 * Static correction information is written in binary format in "staticCorrection*.dat".
 * See {@link StaticCorrectionDataFile}.
 *
 * @author Rei Sato
 * @since 2025/12/24 <i>Merry Christmas!<i>
 */
public class ObayashiStaticCorrection extends Operation {
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
     * The time window data file to work for.
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
     * Names of phases to use to align the record section. The fastest of these arrivals is used.
     */
    private String[] alignPhases;
    /**
     * Name of structure to compute travel times.
     */
    private String structureName;
    /**
     * シグナルとみなすかどうかの最大振幅から見ての比率
     */
    private double threshold;
    /**
     * Range for search [s]. ±searchRange
     */
    private double searchRange;
    private double peakWidth;
    private boolean positivePeak;

    private Set<TimeWindowData> sourceTimeWindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet = Collections.synchronizedSet(new HashSet<>());

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
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timeWindowPath timeWindow.dat");
            pw.println("##Path of a root directory containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root directory containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved. (true)");
            pw.println("#convolved ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##Names of phases of which peaks are used for time shifting, listed using spaces.");
            pw.println("## When multiple phases are set, the fastest arrival of them will be used for alignment. (PKiKP PKP)");
            pw.println("#alignPhases ");
            pw.println("##(String) Name of structure to compute travel times using TauP. (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Threshold for peak finder. (0.5)");
            pw.println("#threshold ");
            pw.println("##(double) Search range [s]. (5)");
            pw.println("#searchRange ");
            pw.println("##(double) The width of trace to use for cross correlation measurment [s]. (3)");
            pw.println("#peakWidth ");
        }
        System.err.println(outPath + " is created.");
    }

    public ObayashiStaticCorrection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        timeWindowPath = property.parsePath("timeWindowPath", null, true, workPath);
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");

        alignPhases = property.parseStringArray("alignPhases", "PKiKP PKP");
        structureName = property.parseString("structureName", "prem").toLowerCase();
        threshold = property.parseDouble("threshold", "0.5");
        searchRange = property.parseDouble("searchRange", "5");
        peakWidth = property.parseDouble("peakWidth", "3");
    }

    @Override
    public void run() throws IOException {
        // gather all time windows to be processed
        sourceTimeWindowSet = TimeWindowDataFile.read(timeWindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());
        // collect all events that exist in the time window set
        Set<GlobalCMTID> eventSet = sourceTimeWindowSet.stream().map(TimeWindowData::getGlobalCMTID).collect(Collectors.toSet());
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

        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "staticCorrection", fileTag, appendFileDate, null, ".dat");
        StaticCorrectionDataFile.write(staticCorrectionSet, outputPath);
    }

    /**
     * synthetic ->[t1, t2], observed ->[t1-t(returning value), t2-t]
     *
     * @param obsSac observed sac data
     * @param synSac synthetic sac data
     * @param window time window
     * @param phaseArrival the arrival of phase to read peak
     * @return value for time shift
     */
    private double computeTimeshift(SACFileAccess obsSac, SACFileAccess synSac, TimeWindow window, double phaseArrival) {
        // create synthetic trace
        Trace synTrace = synSac.createTrace().cutWindow(window, sacSamplingHz);
        // find peak in synthetic trace
        double peakSyn = findPeak(synTrace, phaseArrival);

        // ready to find peak in observed trace
        synTrace = synSac.createTrace().cutWindow(peakSyn - peakWidth, peakSyn + peakWidth, sacSamplingHz);
        Trace obsTrace = obsSac.createTrace();
        // find peak in observed trace
        double peakObs = measurePeak(synTrace, obsTrace, peakSyn);
//        System.err.println("peaks of : " + window + " /SYN: " + peakSyn + " /OBS: " + peakObs);
        double timeshift = peakSyn - peakObs;
        return Precision.round(timeshift, 2);
    }

    private double findPeak(Trace synTrace, double phaseArrival) {
        double max = synTrace.getYVector().getLInfNorm();
        int[] indexPeak = synTrace.getIndicesOfPeak();
        List<Integer> indexList = new ArrayList<>();
        for (int i : indexPeak) {
            if (Math.abs(synTrace.getYAt(i)) > (max * threshold)) indexList.add(i);
        }
        int[] sampledIndex = indexList.stream().mapToInt(Integer::intValue).toArray();
        Trace peakSynTrace = synTrace.resample(sampledIndex);
        return peakSynTrace.findNearestX(phaseArrival);
    }

    private double measurePeak(Trace synTrace, Trace obsTrace, double peak) {
        int traceLength = synTrace.getLength();
        int istart = obsTrace.findNearestXIndex(peak - searchRange);
        int iend = obsTrace.findNearestXIndex(peak + searchRange);

        int ipeak = istart;
        double cor = 0;

        for (int i = istart; i < iend + 1; i++) {
            double time = obsTrace.getXAt(i);
            int istartSub = obsTrace.findNearestXIndex(time - peakWidth);
            Trace obsSubTrace = obsTrace.resampleByStep(istartSub, 1, traceLength);
            double tmpcor = synTrace.correlation(obsSubTrace);
            if (tmpcor > cor) {
                ipeak = i;
                cor = tmpcor;
            }
        }
        return obsTrace.getXAt(ipeak);
    }

    /**
     * Compute obs/syn ratio of peak-to-peak amplitude.
     * @param obsSac
     * @param synSac
     * @param shift
     * @param window
     * @return
     */
    private double computeP2PRatio(SACFileAccess obsSac, SACFileAccess synSac, double shift, TimeWindow window) {
        // peak-to-peak amplitude of synthetic time window
        Trace synTrace = synSac.createTrace().cutWindow(window, sacSamplingHz);
        double synP2P = synTrace.getMaxY() - synTrace.getMinY();

        // peak-to-peak amplitude of observed time window
        Trace obsTrace = obsSac.createTrace().cutWindow(window.shift(-shift), sacSamplingHz);
        double obsP2P = obsTrace.getMaxY() - obsTrace.getMinY();

        return obsP2P / synP2P;
    }

    private class Worker extends DatasetAid.FilteredDatasetWorker {

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sacSamplingHz, sourceTimeWindowSet);
        }

        @Override
        public void actualWork(TimeWindowData timeWindow, SACFileAccess obsSac, SACFileAccess synSac) {
            Observer observer = timeWindow.getObserver();
            SACComponent component = timeWindow.getComponent();

            // check SAC file end time
            if (timeWindow.getEndTime() > obsSac.getValue(SACHeaderEnum.E) - searchRange
                    || timeWindow.getEndTime() > synSac.getValue(SACHeaderEnum.E) - searchRange) {
                System.err.println();
                System.err.println("!! End of time window too late, skipping: " + timeWindow);
                return;
            }

            // calculate the arrivals of align phases
            double phaseArrival = 0;
            try {
                TauP_Time timeTool = new TauP_Time(structureName);
                timeTool.setSourceDepth(eventID.getEventData().getCmtPosition().getDepth());
                timeTool.setPhaseNames(alignPhases);
                timeTool.calculate(obsSac.getValue(SACHeaderEnum.GCARC));
                if (timeTool.getNumArrivals() < 1) {
                    System.err.println("Could not get arrival time of " + String.join(",", alignPhases) + " for " + timeWindow + " , skipping.");
                    return;
                }
                phaseArrival = timeTool.getArrival(0).getTime();
            } catch (TauModelException e) {
                e.printStackTrace();
            }

            // compute correction
            try {
                double shift = 0;
                shift = computeTimeshift(obsSac, synSac, timeWindow, phaseArrival);
                double ratio = computeP2PRatio(obsSac, synSac, 0.0, timeWindow);
                StaticCorrectionData correction = new StaticCorrectionData(observer, eventID, component,
                        timeWindow.getStartTime(), shift, ratio, timeWindow.getPhases());
                staticCorrectionSet.add(correction);
            } catch (Exception e) {
                System.err.println();
                System.err.println("!! Skipping because an error occurs: " + timeWindow);
                e.printStackTrace();
            }
        }
    }
}
