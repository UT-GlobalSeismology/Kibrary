package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.inversion.addons.RandomNoiseMaker;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.HilbertTransform;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Operation that creates dataset containing observed and synthetic waveforms. <br>
 * The write is a set of an ID and waveform files.
 * <p>
 * Observed and synthetic waveforms in SAC files are collected from the obsDir
 * and synDir, respectively. Only SAC files with sample rates of
 * {@link #sacSamplingHz} are used.
 * Both folders must have event folders inside which have waveforms.
 * <p>
 * The static correction is applied as described in {@link StaticCorrection}.
 * <p>
 * The sample rates of the resulting data is {@link #finalSamplingHz}.
 * Timewindow information in {@link #timewindowPath} is used for cutting windows.
 * <p>
 * Only pairs of a seismic source and a receiver with both an observed and
 * synthetic waveform are collected.
 * <p>
 * This class does not apply a digital filter, but extracts information about
 * pass band written in SAC files.
 *
 */
public class ActualWaveformCompiler extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;
    /**
     * Sacのサンプリングヘルツ （これと異なるSACはスキップ）
     */
    private double sacSamplingHz;
    /**
     * 切り出すサンプリングヘルツ
     */
    private double finalSamplingHz;

    /**
     * {@link Path} of a root folder containing observed dataset
     */
    private Path obsPath;
    /**
     * {@link Path} of a root folder containing synthetic dataset
     */
    private Path synPath;
    /**
     * if it is true, the dataset contains synthetic waveforms after
     * convolution
     */
    private boolean convolved;
    /**
     * {@link Path} of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * {@link Path} of a timewindow information file for a reference phase use to correct spectral amplitude
     */
    private Path timewindowRefPath;
    /**
     * Whether to correct time
     */
    private boolean correctTime;
    /**
     * Whether to correct amplitude ratio
     */
    private boolean correctAmplitude;
    /**
     * {@link Path} of a static correction file
     */
    private Path staticCorrectionPath;
    /**
     * [bool] time-shift data to correct for 3-D mantle
     */
    private boolean correctMantle;
    /**
     * {@link Path} of time shifts due to the 3-D mantle
     */
    private Path mantleCorrectionPath;

    /**
     * minimum epicentral distance
     */
    private double minDistance;
    /**
     * low frequency cut-off for spectrum data
     */
    double lowFreq;
    /**
     * high frequency cut-off for spectrum data
     */
    double highFreq;
    /**
     * [bool] add white noise to synthetics data (for synthetic tests)
     */
    private boolean addNoise;
    private double noisePower;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<TimewindowData> refTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;
    private Set<StaticCorrectionData> mantleCorrectionSet;
    private Set<EventFolder> eventDirs;
    private Set<Observer> observerSet;
    private Set<GlobalCMTID> eventSet;
    private Phase[] phases;
    private double[][] periodRanges;
    private int finalFreqSamplingHz;
    /**
     * event-averaged amplitude corrections, used if amplitudeCorrection is False
     */
    private Map<GlobalCMTID, Double> amplitudeCorrEventMap;
    /**
     * writers
     */
    private WaveformDataWriter dataWriter;
    private WaveformDataWriter envelopeWriter;
    private WaveformDataWriter spcAmpWriter;
    private WaveformDataWriter spcReWriter;
    private WaveformDataWriter spcImWriter;
    private WaveformDataWriter hyWriter;

    /**
     * number of OUTPUT pairs. (excluding ignored traces)
     */
    private AtomicInteger numberOfPairs = new AtomicInteger();
    /**
     * ID for static correction and time window information Default is station
     * name, global CMT id, component.
     */
    private BiPredicate<StaticCorrectionData, TimewindowData> isPair = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && s.getComponent() == t.getComponent() && t.getStartTime() < s.getSynStartTime() + 1.01 && t.getStartTime() > s.getSynStartTime() - 1.01;

    private BiPredicate<StaticCorrectionData, TimewindowData> isPair2 = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && s.getComponent() == t.getComponent();

    private BiPredicate<StaticCorrectionData, TimewindowData> isPair_isotropic = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                && (t.getComponent() == SACComponent.R ? s.getComponent() == SACComponent.T : s.getComponent() == t.getComponent())
                && t.getStartTime() < s.getSynStartTime() + 1.01 && t.getStartTime() > s.getSynStartTime() - 1.01;

    private BiPredicate<StaticCorrectionData, TimewindowData> isPair_record = (s,
            t) -> s.getObserver().equals(t.getObserver()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
                    && s.getComponent() == t.getComponent();

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
            pw.println("##Path of a working directory (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Value of sac sampling Hz (20) can't be changed now");
            pw.println("#sacSamplingHz the value will be ignored");
            pw.println("##(double) Value of sampling Hz in output files, must be a factor of sacSamplingHz (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved (true)");
            pw.println("#convolved ");
            pw.println("##Path of a timewindow file, must be defined");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a timewindow file for a reference phase use to correct spectral amplitude, can be ignored");
            pw.println("#timewindowRefPath ");
            pw.println("##(boolean) Whether time should be corrected (false)");
            pw.println("#correctTime ");
            pw.println("##(boolean) Whether amplitude should be corrected (false)");
            pw.println("#correctAmplitude ");
            pw.println("##Path of a static correction file");
            pw.println("## If correctTime or correctAmplitude is true, the path must be defined.");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##(boolean) Whether mantle should be corrected for (false)");
            pw.println("#correctMantle ");
            pw.println("##Path of a mantle correction file");
            pw.println("## If correctMantle is true, the path must be defined.");
            pw.println("#mantleCorrectionPath mantleCorrectionPath.dat");
            pw.println("#minDistance "); // TODO: should not be done in this class?
            pw.println("#lowFreq "); // TODO: add explanation
            pw.println("#highFreq "); // TODO: add explanation
            pw.println("##(boolean) Whether to add noise for synthetic test (false)");
            pw.println("#addNoise ");
            pw.println("##(boolean) Whether to add noise for synthetic test (false)");
            pw.println("#noisePower ");
        }
        System.err.println(outPath + " is created.");
    }

    public ActualWaveformCompiler(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        sacSamplingHz = 20;  // TODO property.parseDouble("sacSamplingHz", "20");
        finalSamplingHz = property.parseDouble("finalSamplingHz", "1");
        if (sacSamplingHz % finalSamplingHz != 0)
            throw new IllegalArgumentException("Must choose a finalSamplingHz that divides " + sacSamplingHz);

        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("timewindowRefPath")) {
            timewindowRefPath = property.parsePath("timewindowRefPath", null, true, workPath);
        }

        correctTime = property.parseBoolean("correctTime", "false");
        correctAmplitude = property.parseBoolean("correctAmplitude", "false");
        if (correctTime || correctAmplitude) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }

        correctMantle = property.parseBoolean("correctMantle", "false");
        if (correctMantle) {
            mantleCorrectionPath = property.parsePath("mantleCorrectionPath", null, true, workPath);
        }

        minDistance = property.parseDouble("minDistance", "0.");
        lowFreq = property.parseDouble("lowFreq", "0.01");
        highFreq = property.parseDouble("highFreq", "0.08");

        addNoise = property.parseBoolean("addNoise", "false");
        noisePower = property.parseDouble("noisePower", "1");
        if (addNoise) {
            System.err.println("Adding noise.");
            System.err.println("Noise power: " + noisePower);
        }

        finalFreqSamplingHz = 8;
    }
/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", ".");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("obsPath")) property.setProperty("obsPath", ".");
        if (!property.containsKey("synPath")) property.setProperty("synPath", ".");
        if (!property.containsKey("timewindowPath"))
            throw new IllegalArgumentException("There is no information about timewindowPath.");
        if (!property.containsKey("convolved")) property.setProperty("convolved", "true");
        if (!property.containsKey("sacSamplingHz")) property.setProperty("sacSamplingHz", "20");
        if (!property.containsKey("finalSamplingHz")) property.setProperty("finalSamplingHz", "1");
        if (!property.containsKey("correctAmplitude")) property.setProperty("correctAmplitude", "false");
        if (!property.containsKey("correctTime")) property.setProperty("correctTime", "false");
        if (!property.containsKey("correctMantle")) property.setProperty("correctMantle", "false");
        if (!property.containsKey("minDistance")) property.setProperty("minDistance", "0.");
        if (!property.containsKey("lowFreq")) property.setProperty("lowFreq", "0.01");
        if (!property.containsKey("highFreq")) property.setProperty("highFreq", "0.08");
        if (!property.containsKey("addNoise")) property.setProperty("addNoise", "false");
        if (!property.containsKey("noisePower")) property.setProperty("noisePower", "1");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        obsPath = getPath("obsPath");
        if (!Files.exists(obsPath)) throw new NoSuchFileException("The obsPath " + obsPath + " does not exist");
        synPath = getPath("synPath");
        if (!Files.exists(synPath)) throw new NoSuchFileException("The synPath " + synPath + " does not exist");
        timewindowPath = getPath("timewindowPath");
        if (!Files.exists(timewindowPath))
            throw new NoSuchFileException("The timewindow file " + timewindowPath + " does not exist");

        if (property.containsKey("timewindowRefPath")) {
            timewindowRefPath = getPath("timewindowRefPath");
            if (!Files.exists(timewindowRefPath))
                throw new NoSuchFileException("The timewindow ref file" + timewindowRefPath + " does not exist");
        }

        correctTime = Boolean.parseBoolean(property.getProperty("correctTime"));
        correctAmplitude = Boolean.parseBoolean(property.getProperty("correctAmplitude"));
        if (correctTime || correctAmplitude) {
            if (!property.containsKey("staticCorrectionPath"))
                throw new IllegalArgumentException("staticCorrectionPath is blank");
            staticCorrectionPath = getPath("staticCorrectionPath");
            if (!Files.exists(staticCorrectionPath))
                throw new NoSuchFileException("The static correction file " + staticCorrectionPath + " does not exist");
        }

        correctMantle = Boolean.parseBoolean(property.getProperty("correctMantle"));
        if (correctMantle) {
            if (!property.containsKey("mantleCorrectionPath"))
                throw new IllegalArgumentException("mantleCorrectionPath is blank");
            mantleCorrectionPath = getPath("mantleCorrectionPath");
            if (!Files.exists(mantleCorrectionPath))
                throw new NoSuchFileException("The mantle correction file " + mantleCorrectionPath + " does not exist");
        }

        convolved = Boolean.parseBoolean(property.getProperty("convolved"));

        // sacSamplingHz
        // =Double.parseDouble(reader.getFirstValue("sacSamplingHz"));
        sacSamplingHz = 20;
        finalSamplingHz = Double.parseDouble(property.getProperty("finalSamplingHz"));
        if (sacSamplingHz % finalSamplingHz != 0)
            throw new IllegalArgumentException("Must choose a finalSamplingHz that divides " + sacSamplingHz);

        minDistance = Double.parseDouble(property.getProperty("minDistance"));
        lowFreq = Double.parseDouble(property.getProperty("lowFreq"));
        highFreq = Double.parseDouble(property.getProperty("highFreq"));

        addNoise = Boolean.parseBoolean(property.getProperty("addNoise"));
        noisePower = Double.parseDouble(property.getProperty("noisePower"));
        if (addNoise) {
            System.err.println("Adding noise.");
            System.err.println("Noise power: " + noisePower);
        }

        finalFreqSamplingHz = 8;
    }
*/

   @Override
   public void run() throws IOException {
       sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
               .stream().filter(tw -> {
                   double distance = Math.toDegrees(tw.getGlobalCMTID().getEvent().getCmtLocation()
                           .calculateEpicentralDistance(tw.getObserver().getPosition()));
                   if (distance < minDistance)
                       return false;
                   return true;
               }).collect(Collectors.toSet());

       if (correctTime || correctAmplitude) {
           Set<StaticCorrectionData> tmpset = StaticCorrectionDataFile.read(staticCorrectionPath);
           staticCorrectionSet = tmpset.stream()
                   .filter(c -> sourceTimewindowSet.parallelStream()
                           .map(t -> isPair_record.test(c, t)).distinct().collect(Collectors.toSet()).contains(true))
                   .collect(Collectors.toSet());

           // average amplitude correction
           amplitudeCorrEventMap = new HashMap<>();
           for (GlobalCMTID event : staticCorrectionSet.stream().map(s -> s.getGlobalCMTID()).collect(Collectors.toSet())) {
               double avgCorr = 0;
               Set<StaticCorrectionData> eventCorrs = staticCorrectionSet.stream()
                       .filter(s -> s.getGlobalCMTID().equals(event)).collect(Collectors.toSet());
               for (StaticCorrectionData corr : eventCorrs)
                   avgCorr += corr.getAmplitudeRatio();
               avgCorr /= eventCorrs.size();
               amplitudeCorrEventMap.put(event, avgCorr);
           }
       }

       if (correctMantle) {
           System.err.println("Using mantle corrections.");
           mantleCorrectionSet = StaticCorrectionDataFile.read(mantleCorrectionPath);
       }

       if (timewindowRefPath != null)
           refTimewindowSet = TimewindowDataFile.read(timewindowRefPath)
               .stream().filter(tw -> {
                   double distance = Math.toDegrees(tw.getGlobalCMTID().getEvent().getCmtLocation().calculateEpicentralDistance(tw.getObserver().getPosition()));
                   if (distance < minDistance)
                       return false;
                   return true;
               }).collect(Collectors.toSet());

       observerSet = sourceTimewindowSet.stream().map(TimewindowData::getObserver)
               .collect(Collectors.toSet());
       eventSet = sourceTimewindowSet.stream().map(TimewindowData::getGlobalCMTID)
               .collect(Collectors.toSet());
       phases = sourceTimewindowSet.stream().map(TimewindowData::getPhases).flatMap(p -> Arrays.stream(p))
               .distinct().toArray(Phase[]::new);

       // obsDirからイベントフォルダを指定
       eventDirs = DatasetAid.eventFolderSet(obsPath).stream()
               .filter(dir -> eventSet.contains(dir.getGlobalCMTID())).collect(Collectors.toSet());

       readPeriodRanges();

       String dateStr = GadgetAid.getTemporaryString();
       outPath = DatasetAid.createOutputFolder(workPath, "compiled", tag, dateStr);

       ObserverListFile.write(observerSet, outPath.resolve("observer.lst"));
       EventListFile.write(eventSet, outPath.resolve("event.lst"));

       Path waveIDPath = null;
       Path waveformPath = null;
       Path envelopeIDPath = null;
       Path envelopePath = null;
       Path hyIDPath = null;
       Path hyPath = null;
       Path spcAmpIDPath = null;
       Path spcAmpPath = null;
       Path spcReIDPath = null;
       Path spcRePath = null;
       Path spcImIDPath = null;
       Path spcImPath = null;

       waveIDPath = outPath.resolve("actualID.dat");
       waveformPath = outPath.resolve("actual.dat");
       envelopeIDPath = outPath.resolve("envelopeID" + dateStr + ".dat");
       envelopePath = outPath.resolve("envelope" + dateStr + ".dat");
       hyIDPath = outPath.resolve("hyID" + dateStr + ".dat");
       hyPath = outPath.resolve("hy" + dateStr + ".dat");
       spcAmpIDPath = outPath.resolve("spcAmpID" + dateStr + ".dat");
       spcAmpPath = outPath.resolve("spcAmp" + dateStr + ".dat");
       spcReIDPath = outPath.resolve("spcReID" + dateStr + ".dat");
       spcRePath = outPath.resolve("spcRe" + dateStr + ".dat");
       spcImIDPath = outPath.resolve("spcImID" + dateStr + ".dat");
       spcImPath = outPath.resolve("spcIm" + dateStr + ".dat");

       try (WaveformDataWriter bdw = new WaveformDataWriter(waveIDPath, waveformPath, observerSet, eventSet,
               periodRanges, phases)) {
           envelopeWriter = new WaveformDataWriter(envelopeIDPath, envelopePath, observerSet, eventSet,
                   periodRanges, phases);
           hyWriter = new WaveformDataWriter(hyIDPath, hyPath, observerSet, eventSet,
                   periodRanges, phases);
           spcAmpWriter = new WaveformDataWriter(spcAmpIDPath, spcAmpPath,
                   observerSet, eventSet, periodRanges, phases);
           spcReWriter = new WaveformDataWriter(spcReIDPath, spcRePath,
                   observerSet, eventSet, periodRanges, phases);
           spcImWriter = new WaveformDataWriter(spcImIDPath, spcImPath,
                   observerSet, eventSet, periodRanges, phases);
           dataWriter = bdw;

           ExecutorService es = ThreadAid.createFixedThreadPool();

           for (EventFolder eventDir : eventDirs)
           es.execute(new Worker(eventDir));
           es.shutdown();

           while (!es.isTerminated()){
               ThreadAid.sleep(1000);
           }
           // this println() is for starting new line after writing "."s
           System.err.println();

           envelopeWriter.close();
           hyWriter.close();
           spcAmpWriter.close();
           spcImWriter.close();
           spcReWriter.close();
           System.err.println(numberOfPairs.get() + " pairs of observed and synthetic waveforms are output.");
       }
   }

   private void readPeriodRanges() {
        try {
            List<double[]> ranges = new ArrayList<>();
            Set<SACFileName> sacfilenames = DatasetAid.sacFileNameSet(obsPath).stream().limit(20).collect(Collectors.toSet());
            for (SACFileName name : sacfilenames) {
                if (!name.isOBS()) continue;
                SACHeaderAccess header = name.readHeader();
                double[] range = new double[] { header.getValue(SACHeaderEnum.USER0),
                        header.getValue(SACHeaderEnum.USER1) };
                boolean exists = false;
                if (ranges.size() == 0) ranges.add(range);
                for (int i = 0; !exists && i < ranges.size(); i++)
                    if (Arrays.equals(range, ranges.get(i))) exists = true;
                if (!exists) ranges.add(range);
            }
            periodRanges = ranges.toArray(new double[0][]);
        } catch (Exception e) {
            throw new RuntimeException("Error in reading period ranges from SAC files.");
        }
    }


    private StaticCorrectionData getStaticCorrection(TimewindowData window) {
        List<StaticCorrectionData> corrs = staticCorrectionSet.stream().filter(s -> isPair_record.test(s, window)).collect(Collectors.toList());
        if (corrs.size() > 1)
            throw new RuntimeException("Found more than 1 static correction for window " + window);
        if (corrs.size() == 0)
            throw new RuntimeException("Found no static correction for window " + window);
        return corrs.get(0);
    }
    /**
     * @param window
     * @author anselme
     * @return
     */
    private StaticCorrectionData getMantleCorrection(TimewindowData window) {
        List<StaticCorrectionData> corrs = mantleCorrectionSet.stream().filter(s -> isPair_record.test(s, window)).collect(Collectors.toList());
        if (corrs.size() > 1)
            throw new RuntimeException("Found more than 1 mantle correction for window " + window);
        if (corrs.size() == 0)
            throw new RuntimeException("Found no mantle correction for window " + window);
        return corrs.get(0);
    }

    private double[] cutDataSac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] waveData = trace.getY();
        return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private double[] cutEnvelopeSac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        HilbertTransform hilbert = new HilbertTransform(trace.getY());
        double[] waveData = hilbert.getEnvelope();
        return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private double[] cutHySac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        HilbertTransform hilbert = new HilbertTransform(trace.getY());
        double[] waveData = hilbert.getHy();
        return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private Trace cutSpcAmpSac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
        FourierTransform fourier = new FourierTransform(cutY, finalFreqSamplingHz);
        double df = fourier.getFreqIncrement(sacSamplingHz);
        if (highFreq > sacSamplingHz)
            throw new RuntimeException("f1 must be <= sacSamplingHz");
        int iStart = (int) (lowFreq / df) - 1;
        int fnpts = (int) ((highFreq - lowFreq) / df);
        double[] spcAmp = fourier.getLogA();
        return new Trace(IntStream.range(0, fnpts).mapToDouble(i -> (i + iStart) * df).toArray(),
            IntStream.range(0, fnpts).mapToDouble(i -> spcAmp[i + iStart]).toArray());
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private Trace cutSpcAmpSacAddNoise(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
        Trace tmp = createNoiseTrace(new ArrayRealVector(cutY).getLInfNorm());
        Trace noiseTrace = new Trace(trace.getX(), Arrays.copyOf(tmp.getY(), trace.getLength()));
        trace = trace.add(noiseTrace);
        cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
        FourierTransform fourier = new FourierTransform(cutY, finalFreqSamplingHz);
        double df = fourier.getFreqIncrement(sacSamplingHz);
        if (highFreq > sacSamplingHz)
            throw new RuntimeException("f1 must be <= sacSamplingHz");
        int iStart = (int) (lowFreq / df) - 1;
        int fnpts = (int) ((highFreq - lowFreq) / df);
        double[] spcAmp = fourier.getLogA();
        return new Trace(IntStream.range(0, fnpts).mapToDouble(i -> (i + iStart) * df).toArray(),
            IntStream.range(0, fnpts).mapToDouble(i -> spcAmp[i + iStart]).toArray());
    }
    /**
     * @param spcAmp
     * @param refSpcAmp
     * @author anselme
     * @return
     */
    private double[] correctSpcAmp(Trace spcAmp, Trace refSpcAmp) {
        double[] spcAmpCorr = new double[spcAmp.getLength()];
        for (int i = 0; i < spcAmp.getLength(); i++) {
            double x = spcAmp.getXAt(i);
            int j0 = refSpcAmp.getNearestXIndex(x);
            int j1;
            if (j0 == 0) {
                j1 = 1;
            }
            else if (j0 == refSpcAmp.getLength() - 1) {
                j1 = refSpcAmp.getLength() - 2;
            }
            else
                j1 = refSpcAmp.getXAt(j0) < x ? j0 + 1 : j0 - 1;
            double refSpcAmpAti = Interpolation.linear(x, new double[] {refSpcAmp.getXAt(j0), refSpcAmp.getXAt(j1)}
                , new double[] {refSpcAmp.getYAt(j0), refSpcAmp.getYAt(j1)});
            spcAmpCorr[i] = spcAmp.getYAt(i) - refSpcAmpAti;
        }
        return spcAmpCorr;
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private Complex[] cutSpcFySac(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
        FourierTransform fourier = new FourierTransform(cutY, finalFreqSamplingHz);
        double df = fourier.getFreqIncrement(sacSamplingHz);
        if (highFreq > sacSamplingHz)
            throw new RuntimeException("f1 must be <= sacSamplingHz");
        int iStart = (int) (lowFreq / df) - 1;
        int fnpts = (int) ((highFreq - lowFreq) / df);
        Complex[] Fy = fourier.getFy();
        return IntStream.range(0, fnpts).parallel().mapToObj(i -> Fy[i + iStart])
                .collect(Collectors.toList()).toArray(new Complex[0]);
    }
    /**
     * @param sac
     * @param startTime
     * @param npts
     * @author anselme
     * @return
     */
    private double[] cutDataSacAddNoise(SACFileAccess sac, double startTime, int npts) {
        Trace trace = sac.createTrace();
        int step = (int) (sacSamplingHz / finalSamplingHz);
        int startPoint = trace.getNearestXIndex(startTime);
        double[] waveData = trace.getY();
        RealVector vector = new ArrayRealVector(IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray());
        Trace tmp = createNoiseTrace(vector.getLInfNorm());
        Trace noiseTrace = new Trace(trace.getX(), Arrays.copyOf(tmp.getY(), trace.getLength()));
        trace = trace.add(noiseTrace);

        double signal = trace.getYVector().getNorm() / trace.getLength();
        double noise = noiseTrace.getYVector().getNorm() / noiseTrace.getLength();
        double snratio = signal / noise;
        System.err.println("snratio " + snratio + " noise " + noise);

        double[] waveDataNoise = trace.getY();
        return IntStream.range(0, npts).parallel().mapToDouble(i -> waveDataNoise[i * step + startPoint]).toArray();
    }

    /**
     * @param normalize
     * @author anselme
     * @return
     */
    private Trace createNoiseTrace(double normalize) {
        double maxFreq = 0.125;
        double minFreq = 0.005;
        int np = 4;
        ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * 0.05 * maxFreq, 2 * Math.PI * 0.05 * minFreq, np);
        Trace tmp = RandomNoiseMaker.create(1., sacSamplingHz, 1638.4, 512);
        double[] u = tmp.getY();
        RealVector uvec = new ArrayRealVector(bpf.applyFilter(u));
        return new Trace(tmp.getX(), uvec.mapMultiply(noisePower * normalize / uvec.getLInfNorm()).toArray());
    }

    /**
     * 与えられたイベントフォルダの観測波形と理論波形を書き込む 両方ともが存在しないと書き込まない
     */
    private class Worker implements Runnable {

        private EventFolder obsEventDir;
        GlobalCMTID event;

        private Worker(EventFolder eventDir) {
            obsEventDir = eventDir;
            event = obsEventDir.getGlobalCMTID();
        }

        @Override
        public void run() {
            Path synEventPath = synPath.resolve(obsEventDir.getGlobalCMTID().toString());
            if (!Files.exists(synEventPath))
                throw new RuntimeException(synEventPath + " does not exist.");


            Set<SACFileName> obsFiles;
            try {
                (obsFiles = obsEventDir.sacFileSet()).removeIf(sfn -> !sfn.isOBS());
            } catch (IOException e2) {
                e2.printStackTrace();
                return;
            }

            for (SACFileName obsFileName : obsFiles) {
                // check components
                SACComponent component = obsFileName.getComponent();
                if (!components.contains(component))
                    continue;

                // get observed
                SACFileAccess obsSac;
                try {
                    obsSac = obsFileName.read();
                } catch (IOException e1) {
                    System.err.println("error occured in reading " + obsFileName);
                    e1.printStackTrace();
                    continue;
                }
                Observer observer = obsSac.getObserver();

                // get synthetic
                SACExtension synExt = convolved ? SACExtension.valueOfConvolutedSynthetic(component)
                        : SACExtension.valueOfSynthetic(component);
                SACFileName synFileName = new SACFileName(synEventPath.resolve(
                        SACFileName.generate(observer, event, synExt)));
                if (!synFileName.exists()) {
                    System.err.println(synFileName + " does not exist. ");
                    continue;
                }
                SACFileAccess synSac;
                try {
                    synSac = synFileName.read();
                } catch (IOException e1) {
                    System.err.println("error occured in reading " + synFileName);
                    e1.printStackTrace();
                    continue;
                }

                // get timewindows
                Set<TimewindowData> windows = sourceTimewindowSet.stream()
                        .filter(info -> info.getObserver().equals(observer))
                        .filter(info -> info.getGlobalCMTID().equals(event))
                        .filter(info -> info.getComponent() == component).collect(Collectors.toSet());
                // タイムウインドウの情報が入っていなければ次へ
                if (windows.isEmpty()) continue;

                // Sampling Hz of observed and synthetic must be same as the
                // value declared in the input file
                if (obsSac.getValue(SACHeaderEnum.DELTA) != 1 / sacSamplingHz
                        && obsSac.getValue(SACHeaderEnum.DELTA) == synSac.getValue(SACHeaderEnum.DELTA)) {
                    System.err.println("Values of sampling Hz of observed and synthetic "
                            + (1 / obsSac.getValue(SACHeaderEnum.DELTA)) + ", "
                            + (1 / synSac.getValue(SACHeaderEnum.DELTA)) + " are invalid, they should be "
                            + sacSamplingHz);
                    continue;
                }

                // bandpassの読み込み 観測波形と理論波形とで違えばスキップ
                double minPeriod = 0;
                double maxPeriod = Double.POSITIVE_INFINITY;
                if (obsSac.getValue(SACHeaderEnum.USER0) != synSac.getValue(SACHeaderEnum.USER0)
                        || obsSac.getValue(SACHeaderEnum.USER1) != synSac.getValue(SACHeaderEnum.USER1)) {
                    System.err.println("band pass filter difference");
                    continue;
                }
                minPeriod = obsSac.getValue(SACHeaderEnum.USER0) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER0);
                maxPeriod = obsSac.getValue(SACHeaderEnum.USER1) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER1);

                for (TimewindowData window : windows) {
                    int npts = (int) ((window.getEndTime() - window.getStartTime()) * finalSamplingHz);
                    if (window.getEndTime() > synSac.getValue(SACHeaderEnum.E) - 10) continue;
                    double startTime = window.getStartTime();
                    double shift = 0;
                    double ratio = 1;
                    if (correctTime || correctAmplitude) {
                        try {
                            StaticCorrectionData sc = getStaticCorrection(window);
                            shift = correctTime ? sc.getTimeshift() : 0;
//							ratio = amplitudeCorrection ? sc.getAmplitudeRatio() : 1;
                            ratio = correctAmplitude ? sc.getAmplitudeRatio() : amplitudeCorrEventMap.get(window.getGlobalCMTID());
                        } catch (NoSuchElementException e) {
                            System.err.println("There is no static correction information for\\n " + window);
                            continue;
                        }
                    }

                    if (correctMantle) {
                        try {
                            StaticCorrectionData sc = getMantleCorrection(window);
                            shift += sc.getTimeshift();
                        } catch (NoSuchElementException e) {
                            System.err.println("There is no mantle correction information for\\n " + window);
                            continue;
                        }
                    }

                    TimewindowData windowRef = null;
                    int nptsRef = 0;
                    if (refTimewindowSet != null) {
                        List<TimewindowData> tmpwindows = refTimewindowSet.stream().filter(tw ->
                                tw.getGlobalCMTID().equals(window.getGlobalCMTID())
                                && tw.getObserver().equals(window.getObserver())
                                && tw.getComponent().equals(window.getComponent())).collect(Collectors.toList());
                        if (tmpwindows.size() != 1) {
                            System.err.println("Reference timewindow does not exist " + window);
                            continue;
                        }
                        else {
                            windowRef = tmpwindows.get(0);
                        }

                        nptsRef = (int) ((windowRef.getEndTime() - windowRef.getStartTime()) * finalSamplingHz);
                    }

                    double[] obsData = null;
                    if (addNoise)
                        obsData = cutDataSacAddNoise(obsSac, startTime - shift, npts);
                    else
                        obsData = cutDataSac(obsSac, startTime - shift, npts);
                    double[] synData = cutDataSac(synSac, startTime, npts);

                    double[] obsEnvelope = cutEnvelopeSac(obsSac, startTime - shift, npts);
                    double[] synEnvelope = cutEnvelopeSac(synSac, startTime, npts);

                    double[] obsHy = cutHySac(obsSac, startTime - shift, npts);
                    double[] synHy = cutHySac(synSac, startTime, npts);

                    Trace obsSpcAmpTrace = null;
                    Trace synSpcAmpTrace = null;

                    if (addNoise) {
                        obsSpcAmpTrace = cutSpcAmpSacAddNoise(obsSac, startTime - shift, npts);
                        synSpcAmpTrace = cutSpcAmpSac(synSac, startTime, npts);
                    }
                    else {
                        obsSpcAmpTrace = cutSpcAmpSac(obsSac, startTime - shift, npts);
                        synSpcAmpTrace = cutSpcAmpSac(synSac, startTime, npts);
                    }

                    Complex[] obsFy = cutSpcFySac(obsSac, startTime - shift, npts);
                    Complex[] synFy = cutSpcFySac(synSac, startTime, npts);

                    double[] obsSpcRe = Arrays.stream(obsFy).mapToDouble(Complex::getReal).toArray();
                    double[] synSpcRe = Arrays.stream(synFy).mapToDouble(Complex::getReal).toArray();

                    double[] obsSpcIm = Arrays.stream(obsFy).mapToDouble(Complex::getImaginary).toArray();
                    double[] synSpcIm = Arrays.stream(synFy).mapToDouble(Complex::getImaginary).toArray();

                    double[] obsSpcAmp = null;
                    double[] synSpcAmp = null;

                    Trace refObsSpcAmpTrace = null;
                    Trace refSynSpcAmpTrace = null;
                    if (windowRef != null) {
                        if (addNoise) {
                            refObsSpcAmpTrace = cutSpcAmpSacAddNoise(obsSac, windowRef.getStartTime(), nptsRef);
                            refSynSpcAmpTrace = cutSpcAmpSac(synSac, windowRef.getStartTime(), nptsRef);
                        }
                        else {
                            refObsSpcAmpTrace = cutSpcAmpSac(obsSac, windowRef.getStartTime(), nptsRef);
                            refSynSpcAmpTrace = cutSpcAmpSac(synSac, windowRef.getStartTime(), nptsRef);
                        }

                        if (correctAmplitude) {
                            obsSpcAmp = correctSpcAmp(obsSpcAmpTrace, refObsSpcAmpTrace);
                            synSpcAmp = correctSpcAmp(synSpcAmpTrace, refSynSpcAmpTrace);
                        }
                        else {
                            obsSpcAmp = obsSpcAmpTrace.getY();
                            synSpcAmp = synSpcAmpTrace.getY();
                            double corrratio = amplitudeCorrEventMap.get(window.getGlobalCMTID());
                            obsSpcAmp = Arrays.stream(obsSpcAmp).map(d -> d - Math.log(corrratio)).toArray();
                        }
                    }
                    else {
                        obsSpcAmp = obsSpcAmpTrace.getY();
                        synSpcAmp = synSpcAmpTrace.getY();
                    }

                    double correctionRatio = ratio;

                    Phase[] includePhases = window.getPhases();

                    obsData = Arrays.stream(obsData).map(d -> d / correctionRatio).toArray();
                    BasicID synID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synData);
                    BasicID obsID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsData);

                    obsEnvelope = Arrays.stream(obsEnvelope).map(d -> d / correctionRatio).toArray();
                    BasicID synEnvelopeID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synEnvelope);
                    BasicID obsEnvelopeID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsEnvelope);

                    obsHy = Arrays.stream(obsHy).map(d -> d / correctionRatio).toArray();
                    BasicID synHyID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synHy);
                    BasicID obsHyID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsHy);

                    int fnpts = synSpcAmp.length;

                    BasicID synSpcAmpID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcAmp);
                    BasicID obsSpcAmpID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsSpcAmp);

                    BasicID synSpcReID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcRe);
                    BasicID obsSpcReID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsSpcRe);

                    BasicID synSpcImID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcIm);
                    BasicID obsSpcImID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, event,
                            component, minPeriod, maxPeriod, includePhases, 0, convolved, obsSpcIm);

                    try {
                        dataWriter.addBasicID(obsID);
                        dataWriter.addBasicID(synID);
                        envelopeWriter.addBasicID(obsEnvelopeID);
                        envelopeWriter.addBasicID(synEnvelopeID);
                        hyWriter.addBasicID(obsHyID);
                        hyWriter.addBasicID(synHyID);
                        spcAmpWriter.addBasicID(obsSpcAmpID);
                        spcAmpWriter.addBasicID(synSpcAmpID);
                        spcReWriter.addBasicID(obsSpcReID);
                        spcReWriter.addBasicID(synSpcReID);
                        spcImWriter.addBasicID(obsSpcImID);
                        spcImWriter.addBasicID(synSpcImID);
                        numberOfPairs.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            System.err.print(".");
        }
    }

}
