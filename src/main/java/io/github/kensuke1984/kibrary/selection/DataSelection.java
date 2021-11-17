package io.github.kensuke1984.kibrary.selection;


import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Precision;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.aid.ThreadAid;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.Trace;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that selects satisfactory observed and synthetic data
 * based on amplitude ratio, correlation, and/or variance.
 *
 * <p>
 * Both observed and synthetic data must be in event folders under workDir.
 * {@link TimewindowDataFile} is necessary.
 *
 * <p>
 * Selected timewindows will be written in binary format in "selectedTimewindow*.dat".
 * See {@link TimewindowDataFile}.
 * <p>
 * Information of parameters used in data selection will be written in ascii format in "dataSelection*.inf".
 * See {@link DataSelectionInformationFile}.
 * <p>
 * Timewindows with no phases will be written in standard output, and will not be used.
 *
 * @author Kensuke Konishi
 * @version 0.1.2.1
 * @author anselme add additional selection critera
 */
public class DataSelection implements Operation {

    private final Properties property;
    private Path timewindowInformationFilePath;
    private Path staticCorrectionInformationFilePath;
    /**
     * the directory of observed data
     */
    private Path obsPath;
    /**
     * the directory of synthetic data
     */
    private Path synPath;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * Path of the information output file
     */
    private Path infoOutputpath;
    /**
     * Path of the timewindow output file
     */
    private Path outputGoodWindowPath;
    /**
     * Path of the result file that will be made inside each event folder
     */
    private String eachEventResultFile;

    private Set<SACComponent> components;
    /**
     * コンボリューションされている波形かそうでないか （両方は無理）
     */
    private boolean convolved;
    private boolean excludeSurfaceWave;
    /**
     * Minimum correlation coefficients
     */
    private double minCorrelation;
    /**
     * Maximum correlation coefficients
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
     * amplitude のしきい値
     */
    private double ratio;
    /**
     * Maximum of static correction shift
     */
    private double maxStaticShift;
    private double minSNratio;
    //private double minDistance;
    private boolean SnScSnPair;
    private Set<TimewindowData> sourceTimewindowInformationSet;
    private Set<TimewindowData> goodTimewindowInformationSet;
    private List<DataSelectionInformation> dataSelectionInfo;
    private Set<StaticCorrectionData> staticCorrectionSet;

    /**
     * ID for static correction and time window information Default is station
     * name, global CMT id, component, start time.
     */
    private BiPredicate<StaticCorrectionData, TimewindowData> isPair = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && s.getComponent() == t.getComponent() && Math.abs(t.getStartTime() - s.getSynStartTime()) < 1.01;
    private BiPredicate<StaticCorrectionData, TimewindowData> isPair_isotropic = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && (t.getComponent() == SACComponent.R ? s.getComponent() == SACComponent.T : s.getComponent() == t.getComponent())
                    && t.getStartTime() < s.getSynStartTime() + 1.01 && t.getStartTime() > s.getSynStartTime() - 1.01;
    private BiPredicate<StaticCorrectionData, TimewindowData> isPairRecord = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && s.getComponent() == t.getComponent();

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(DataSelection.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan DataSelection");
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##Sac components to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#synPath");
            pw.println("##Path of a time window information file, must be defined");
            pw.println("#timewindowInformationFilePath timewindow.dat");
            pw.println("##Path of a static correction file");
            pw.println("##If you do not want to consider static correction, then comment out the next line.");
            pw.println("#staticCorrectionInformationFilePath staticCorrection.dat");
            pw.println("##(boolean) Whether the synthetics have already been convolved (true)");
            pw.println("#convolved");
            pw.println("##Reject data with static correction greater than maxStaticShift (10.)");
            pw.println("#maxStaticShift");
            pw.println("##(double) sacSamplingHz (20)");
            pw.println("#sacSamplingHz cant change now");
            pw.println("##(double) minCorrelation (0)");
            pw.println("#minCorrelation");
            pw.println("##(double) maxCorrelation (1)");
            pw.println("#maxCorrelation");
            pw.println("##(double) minVariance (0)");
            pw.println("#minVariance");
            pw.println("##(double) maxVariance (2)");
            pw.println("#maxVariance");
            pw.println("##(double) ratio (2)");
            pw.println("#ratio");
            pw.println("##(double) minSNratio (0)");
            pw.println("#minSNratio");
            pw.println("#(boolean) Impose (s)ScSn in time window set if and only if (s)Sn is in the dataset (false)");
            pw.println("#SnScSnPair false");
            pw.println("##(boolean) Whether to exclude surface wave (false)");
            pw.println("#excludeSurfaceWave");
            pw.println("#minDistance");
        }
        System.err.println(outPath + " is created.");
    }

    public DataSelection(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("obsPath")) property.setProperty("obsPath", "");
        if (!property.containsKey("synPath")) property.setProperty("synPath", "");
        if (!property.containsKey("timewindowInformationFilePath"))
            throw new IllegalArgumentException("No timewindow specified");
        if (!property.containsKey("convolved")) property.setProperty("convolved", "true");
        if (!property.containsKey("maxStaticShift")) property.setProperty("maxStaticShift", "10.");
        if (!property.containsKey("minCorrelation")) property.setProperty("minCorrelation", "0");
        if (!property.containsKey("maxCorrelation")) property.setProperty("maxCorrelation", "1");
        if (!property.containsKey("minVariance")) property.setProperty("minVariance", "0");
        if (!property.containsKey("maxVariance")) property.setProperty("maxVariance", "2");
        if (!property.containsKey("ratio")) property.setProperty("ratio", "2");
        if (!property.containsKey("minSNratio")) property.setProperty("minSNratio", "0");
        if (!property.containsKey("SnScSnPair")) property.setProperty("SnScSnPair", "false");
        if (!property.containsKey("excludeSurfaceWave")) property.setProperty("excludeSurfaceWave", "false");
        //if (!property.containsKey("minDistance")) property.setProperty("minDistance", "0.");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        String dateStr = Utilities.getTemporaryString();
        infoOutputpath = workPath.resolve("dataSelection" + dateStr + ".inf");
        outputGoodWindowPath = workPath.resolve("selectedTimewindow" + dateStr + ".dat");
        eachEventResultFile = "selectionResult" + dateStr + ".txt";
        dataSelectionInfo = new ArrayList<>();
        goodTimewindowInformationSet = Collections.synchronizedSet(new HashSet<>());

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        obsPath = getPath("obsPath");
        if (!Files.exists(obsPath)) throw new NoSuchFileException("The obsPath " + obsPath + " does not exist");
        synPath = getPath("synPath");
        if (!Files.exists(synPath)) throw new NoSuchFileException("The synPath " + synPath + " does not exist");
        timewindowInformationFilePath = getPath("timewindowInformationFilePath");
        if (!Files.exists(timewindowInformationFilePath))
            throw new NoSuchFileException("The timewindow information " + timewindowInformationFilePath + " does not exist");
        if (property.containsKey("staticCorrectionInformationFilePath")) {
            staticCorrectionInformationFilePath = getPath("staticCorrectionInformationFilePath");
            if (!Files.exists(staticCorrectionInformationFilePath))
                throw new NoSuchFileException("The static correction" + staticCorrectionInformationFilePath + " does not exist");
        }

        convolved = Boolean.parseBoolean(property.getProperty("convolved"));
        maxStaticShift = Double.parseDouble(property.getProperty("maxStaticShift"));
        minCorrelation = Double.parseDouble(property.getProperty("minCorrelation"));
        maxCorrelation = Double.parseDouble(property.getProperty("maxCorrelation"));
        minVariance = Double.parseDouble(property.getProperty("minVariance"));
        maxVariance = Double.parseDouble(property.getProperty("maxVariance"));
        ratio = Double.parseDouble(property.getProperty("ratio"));
        minSNratio = Double.parseDouble(property.getProperty("minSNratio"));
        SnScSnPair = Boolean.parseBoolean(property.getProperty("SnScSnPair"));
        excludeSurfaceWave = Boolean.parseBoolean(property.getProperty("excludeSurfaceWave"));
        //minDistance = Double.parseDouble(property.getProperty("minDistance"));
        // sacSamplingHz
        // =Double.parseDouble(reader.getFirstValue("sacSamplingHz")); TODO
        // sacSamplingHz = 20;

    }

    /**
     * @param args [parameter file name]
     * @throws Exception if an I/O happens
     */
    public static void main(String[] args) throws IOException {
        DataSelection ds = new DataSelection(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(DataSelection.class.getName() + " is operating.");
        ds.run();
        System.err.println(DataSelection.class.getName() + " finished in " +
                Utilities.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws IOException {
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(obsPath);
        sourceTimewindowInformationSet = TimewindowDataFile.read(timewindowInformationFilePath);
        staticCorrectionSet = (staticCorrectionInformationFilePath == null ? Collections.emptySet()
                : StaticCorrectionDataFile.read(staticCorrectionInformationFilePath));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventDirs.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        System.err.println("Outputting values of criteria in " + infoOutputpath);
        System.err.println("Results are written in " + eachEventResultFile + " inside each event folder.");
        DataSelectionInformationFile.write(dataSelectionInfo, infoOutputpath);

        System.err.println("Outputting selected timewindows in " + outputGoodWindowPath);
        TimewindowDataFile.write(goodTimewindowInformationSet, outputGoodWindowPath);
        System.err.println(goodTimewindowInformationSet.size() + " timewindows were selected.");
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
        List<StaticCorrectionData> corrs = staticCorrectionSet.stream().filter(s -> isPair.test(s, window)).collect(Collectors.toList());
        if (corrs.size() != 1) throw new RuntimeException("Found no, or more than 1 static correction for window " + window);
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

    private boolean check(PrintWriter writer, Observer observer, GlobalCMTID id, SACComponent component,
            TimewindowData window, RealVector obsU, RealVector synU, double SNratio) throws IOException {
        if (obsU.getDimension() < synU.getDimension())
            synU = synU.getSubVector(0, obsU.getDimension() - 1);
        else if (synU.getDimension() < obsU.getDimension())
            obsU = obsU.getSubVector(0, synU.getDimension() - 1);

        // check
        double synMax = synU.getMaxValue();
        double synMin = synU.getMinValue();
        double obsMax = obsU.getMaxValue();
        double obsMin = obsU.getMinValue();
        double obs2 = obsU.dotProduct(obsU);
        double syn2 = synU.dotProduct(synU);
        double cor = obsU.dotProduct(synU);
        cor /= Math.sqrt(obs2 * syn2);
        double var = obs2 + syn2 - 2 * obsU.dotProduct(synU);
        var /= obs2;
        double maxRatio = Precision.round(synMax / obsMax, 2);
        double minRatio = Precision.round(synMin / obsMin, 2);
        double absRatio = (-synMin < synMax ? synMax : -synMin) / (-obsMin < obsMax ? obsMax : -obsMin);

        absRatio = Precision.round(absRatio, 2);
        var = Precision.round(var, 2);
        cor = Precision.round(cor, 2);

        SNratio = Precision.round(SNratio, 2);

        boolean isok = !(ratio < minRatio || minRatio < 1 / ratio || ratio < maxRatio || maxRatio < 1 / ratio
                || ratio < absRatio || absRatio < 1 / ratio || cor < minCorrelation || maxCorrelation < cor
                || var < minVariance || maxVariance < var
                || SNratio < minSNratio);

        Phases phases = new Phases(window.getPhases());

        if (window.getPhases().length == 0) {
            System.out.println("No phase: " + window);
        }
        else {
            writer.println(observer + " " + id + " " + component + " " + phases + " " + isok + " " + absRatio + " " + maxRatio + " "
                + minRatio + " " + var + " " + cor + " " + SNratio);

            dataSelectionInfo.add(new DataSelectionInformation(window, var, cor, maxRatio, minRatio, absRatio, SNratio));
        }

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

    @Override
    public Path getWorkPath() {
        return workPath;
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

    private class Worker implements Runnable {

        private EventFolder obsEventDirectory;
        private EventFolder synEventDirectory;

        private GlobalCMTID event;

        private Worker(EventFolder ed) {
            this.obsEventDirectory = ed;
            event = ed.getGlobalCMTID();
            synEventDirectory = new EventFolder(synPath.resolve(ed.getName()));
        }

        private Set<TimewindowData> imposeSn_ScSnPair(Set<TimewindowData> info) {
            Set<TimewindowData> infoNew = new HashSet<>();
            if (info.stream().map(tw -> tw.getObserver()).distinct().count() > 1)
                throw new RuntimeException("Info should contain time windows for a unique record");
            if (info.stream().map(tw -> tw.getGlobalCMTID()).distinct().count() > 1)
                throw new RuntimeException("Info should contain time windows for a unique record");
            Map<Phases, TimewindowData> map = new HashMap<>();
            info.stream().forEach(tw -> map.put(new Phases(tw.getPhases()), tw));
            for (int i = 1; i <= 4; i++) {
                Phases phase = new Phases(new Phase[] {Phase.create(new String(new char[i]).replace("\0", "S"))});
                Phases cmbPhase = new Phases(new Phase[] {Phase.create(new String(new char[i]).replace("\0", "ScS"))});
                Phases depthPhase = new Phases(new Phase[] {Phase.create("s" + phase)});
                Phases cmbDepthPhase = new Phases(new Phase[] {Phase.create("s" + cmbPhase)});

                if (map.containsKey(phase) && map.containsKey(cmbPhase)) {
                    infoNew.add(map.get(phase));
                    infoNew.add(map.get(cmbPhase));
                }
                if (map.containsKey(depthPhase) && map.containsKey(cmbDepthPhase)) {
                    infoNew.add(map.get(depthPhase));
                    infoNew.add(map.get(cmbDepthPhase));
                }
            }
            Phases cmbMerge = new Phases(new Phase[] {Phase.ScS, Phase.S});
            Phases depthCMBMerge = new Phases(new Phase[] {Phase.create("sScS"), Phase.create("sS")});
            if (map.containsKey(cmbMerge))
                infoNew.add(map.get(cmbMerge));
            if (map.containsKey(depthCMBMerge))
                infoNew.add(map.get(depthCMBMerge));
            return infoNew;
        }

        @Override
        public void run() {
            if (!synEventDirectory.exists()) {
                try {
                    FileUtils.moveDirectoryToDirectory(obsEventDirectory, workPath.resolve("withoutSyn").toFile(),
                            true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            // collect observed files
            Set<SACFileName> obsFiles;
            try {
                (obsFiles = obsEventDirectory.sacFileSet()).removeIf(s -> !s.isOBS());
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }

            try (PrintWriter lpw = new PrintWriter(
                    Files.newBufferedWriter(obsEventDirectory.toPath().resolve(eachEventResultFile),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                // all the observed files
                if (convolved)
                    lpw.println("#convolved");
                else
                    lpw.println("#not convolved");
                lpw.println("#s e c phase use ratio(syn/obs){abs max min} variance correlation SNratio");

                for (SACFileName obsName : obsFiles) {
                    // check components
                    SACComponent component = obsName.getComponent();
                    if (!components.contains(component))
                        continue;

                    // get observed
                    SACFileAccess obsSac = obsName.read();
                    Observer observer = obsSac.getObserver();

                    // get synthetic
                    SACExtension synExt = convolved ? SACExtension.valueOfConvolutedSynthetic(component)
                            : SACExtension.valueOfSynthetic(component);
                    SACFileName synName = new SACFileName(synEventDirectory.toPath().resolve(
                            SACFileName.generate(observer, event, synExt)));
                    if (!synName.exists()) {
                        System.err.println("Ignoring non-existing synthetics " + synName);
                        continue;
                    }
                    SACFileAccess synSac = synName.read();

                    if (synSac.getValue(SACHeaderEnum.DELTA) != obsSac.getValue(SACHeaderEnum.DELTA)) {
                        System.err.println("Ignoring differing DELTA " + obsName);
                        continue;
                    }

                    /*
                    double distance = Math.toDegrees(obsSac.getGlobalCMTID().getEvent().getCmtLocation().getEpicentralDistance(observer.getPosition()));
                    if (distance < minDistance)
                        continue;
                    */

                    // Pickup timewindows of obsName
                    Set<TimewindowData> windowInformations = sourceTimewindowInformationSet
                            .stream().filter(info -> info.getObserver().equals(observer)
                                    && info.getGlobalCMTID().equals(event) && info.getComponent() == component)
                            .collect(Collectors.toSet());

                    if (windowInformations.isEmpty())
                        continue;

                    // noise per second (in obs)
                    double noise = noisePerSecond(obsSac, component);

                    // Traces
                    Trace synTrace = synSac.createTrace();

                    Set<TimewindowData> tmpGoodWindows = new HashSet<>();
                    for (TimewindowData window : windowInformations) {
                        if (window.getEndTime() > synSac.getValue(SACHeaderEnum.E) - 10) // should 10 be maxStaticShift ?
                            continue;
                        double shift = 0.;
                        if (!staticCorrectionSet.isEmpty()) {
                            StaticCorrectionData foundShift = getStaticCorrection(window);
                            shift = foundShift.getTimeshift();
                        }
                        if (Math.abs(shift) > maxStaticShift)
                            continue;

                        // remove surface wave from window
                        if (excludeSurfaceWave) {
                            SurfaceWaveDetector detector = new SurfaceWaveDetector(synTrace, 20.);
                            Timewindow surfacewaveWindow = detector.getSurfaceWaveWindow();

                            if (surfacewaveWindow != null) {
                                double endTime = window.getEndTime();
                                double startTime = window.getStartTime();
                                if (startTime >= surfacewaveWindow.getStartTime() && endTime <= surfacewaveWindow.getEndTime())
                                    continue;
                                if (endTime > surfacewaveWindow.getStartTime() && startTime < surfacewaveWindow.getStartTime())
                                    endTime = surfacewaveWindow.getStartTime();
                                if (startTime < surfacewaveWindow.getEndTime() && endTime > surfacewaveWindow.getEndTime())
                                    startTime = surfacewaveWindow.getEndTime();

                                window = new TimewindowData(startTime
                                        , endTime, window.getObserver(), window.getGlobalCMTID()
                                        , window.getComponent(), window.getPhases());
                            }
                        }

                        TimewindowData shiftedWindow = new TimewindowData(window.getStartTime() - shift
                                , window.getEndTime() - shift, window.getObserver()
                                , window.getGlobalCMTID(), window.getComponent(), window.getPhases());

                        RealVector synU = cutSAC(synSac, window);
                        RealVector obsU = cutSAC(obsSac, shiftedWindow);

                        // signal-to-noise ratio
                        double signal = obsU.getNorm() / (window.getEndTime() - window.getStartTime());
                        double SNratio = signal / noise;

                        if (check(lpw, observer, event, component, window, obsU, synU, SNratio)) {
                            if (Stream.of(window.getPhases()).filter(p -> p == null).count() > 0) {
                                System.out.println("!! No phase: " + window);
                            }
                            tmpGoodWindows.add(window);
                        }
                    }
                    if (SnScSnPair)
                        tmpGoodWindows = imposeSn_ScSnPair(tmpGoodWindows);
                    for (TimewindowData window : tmpGoodWindows)
                        goodTimewindowInformationSet.add(window);

                }

                lpw.close();
                // spw.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("error on " + obsEventDirectory);
            }
            System.err.print(".");
            // System.out.println(obsEventDirectory + " is done");
        }
    }

}
