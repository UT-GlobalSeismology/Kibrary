package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.HilbertTransform;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Operation that exports dataset containing observed and synthetic waveforms. <br>
 * Output is written in the format of {@link BasicIDFile}.
 * <p>
 * Time windows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> the (event, observer, component)-pair is included in the input data entry file, if it is specified </li>
 * <li> observed waveform data exists for the (event, observer, component)-pair </li>
 * <li> synthetic waveform data exists for the (event, observer, component)-pair </li>
 * </ul>
 * Both observed and synthetic data must be in event folders under obsDir and synDir (they can be the same folder).
 * Only SAC files with sample rates of {@link #sacSamplingHz} are used.
 * <p>
 * Resulting entries can be specified by a (event, observer, component, sacType, timeframe)-pair.
 * The sample rate of the resulting data is {@link #finalSamplingHz}.
 * Static correction is applied as described in {@link FujiStaticCorrection}.
 * <p>
 * In output {@link BasicID}s, the start time of the synthetic and observed IDs will differ when time shift is applied.
 * The start time of the synthetic is the one that shall be used hereafter.
 * <p>
 * This class does not apply a digital filter, but extracts information about the passband written in SAC files.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2021/11/3 renamed from waveformdata.ObservedSyntheticDatasetMaker to waveform.ActualWaveformCompiler
 */
public class ActualWaveformCompiler extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Components to be included in the dataset.
     */
    private Set<SACComponent> components;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;
    /**
     * Sampling frequency of waveform data to create [Hz].
     */
    private double finalSamplingHz;

    /**
     * Path of a root folder containing observed dataset.
     */
    private Path obsPath;
    /**
     * Path of a root folder containing synthetic dataset.
     */
    private Path synPath;
    /**
     * Whether the synthetic waveforms are convolved.
     */
    private boolean convolved;
    /**
     * Path of a time window information file.
     */
    private Path timewindowPath;
    /**
     * Path of a time window information file for a reference phase use to correct spectral amplitude.
     */
    private Path timewindowRefPath;
    /**
     * Whether to correct time.
     */
    private boolean correctTime;
    /**
     * How to correct amplitude ratio. {0: none, 1: each trace, 2: event average}
     */
    private int amplitudeCorrectionType;
    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;
    /**
     * Path of a static correction file.
     */
    private Path staticCorrectionPath;
    /**
     * Whether to time-shift data to correct for 3-D mantle.
     */
    private boolean correctMantle;
    /**
     * Path of time shifts due to the 3-D mantle.
     */
    private Path mantleCorrectionPath;

    /**
     * Low frequency cut-off for spectrum data.
     */
    double lowFreq;
    /**
     * High frequency cut-off for spectrum data.
     */
    double highFreq;
    /**
     * Whether to add white noise to synthetics data (for synthetic tests).
     */
    private boolean addNoise;
    private double noisePower;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<TimewindowData> refTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;
    private Set<StaticCorrectionData> mantleCorrectionSet;
    private int finalFreqSamplingHz;
    /**
     * Event-averaged amplitude corrections, used if amplitudeCorrection is false.
     */
    private Map<GlobalCMTID, Double> amplitudeCorrEventMap;

    private List<BasicID> actualIDs = Collections.synchronizedList(new ArrayList<>());
    private List<BasicID> envelopeIDs = Collections.synchronizedList(new ArrayList<>());
    private List<BasicID> hyIDs = Collections.synchronizedList(new ArrayList<>());
    private List<BasicID> spcAmpIDs = Collections.synchronizedList(new ArrayList<>());
    private List<BasicID> spcReIDs = Collections.synchronizedList(new ArrayList<>());
    private List<BasicID> spcImIDs = Collections.synchronizedList(new ArrayList<>());

    /**
     * Number of OUTPUT pairs (excluding ignored traces).
     */
    private AtomicInteger numberOfPairs = new AtomicInteger();

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##(double) Sampling frequency in output files [Hz], must be a factor of sacSamplingHz. (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timewindowPath selectedTimewindow.dat");
            pw.println("##Path of a time window file for a reference phase used to correct spectral amplitude, can be ignored.");
            pw.println("#timewindowRefPath ");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved. (true)");
            pw.println("#convolved false");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Path of a static correction file.");
            pw.println("##  If the following correctTime is true or amplitudeCorrectionType > 0, this path must be defined.");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##(boolean) Whether time should be corrected. (false)");
            pw.println("#correctTime true");
            pw.println("##(int) Type of amplitude correction to apply, from {0: none, 1: each trace, 2: event average}. (0)");
            pw.println("#amplitudeCorrectionType ");
            pw.println("##Path of a mantle correction file.");
            pw.println("## If the following correctMantle is true, this path must be defined.");
            pw.println("#mantleCorrectionPath mantleCorrectionPath.dat");
            pw.println("##(boolean) Whether mantle should be corrected for. (false)");
            pw.println("#correctMantle ");
            pw.println("#lowFreq "); // TODO: add explanation
            pw.println("#highFreq "); // TODO: add explanation
            pw.println("##(boolean) Whether to add noise for synthetic test. (false)");
            pw.println("#addNoise ");
            pw.println("##Power of noise for synthetic test. (1)"); //TODO what is the unit?
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
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");
        finalSamplingHz = property.parseDouble("finalSamplingHz", "1");
        if (!MathAid.isDivisible(sacSamplingHz, finalSamplingHz))
            throw new IllegalArgumentException("sacSamplingHz/finalSamplingHz must be integer.");

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("timewindowRefPath")) {
            timewindowRefPath = property.parsePath("timewindowRefPath", null, true, workPath);
        }
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");

        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }

        correctTime = property.parseBoolean("correctTime", "false");
        amplitudeCorrectionType = property.parseInt("amplitudeCorrectionType", "0");
        if (correctTime || amplitudeCorrectionType > 0) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }

        correctMantle = property.parseBoolean("correctMantle", "false");
        if (correctMantle) {
            mantleCorrectionPath = property.parsePath("mantleCorrectionPath", null, true, workPath);
        }

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

   @Override
   public void run() throws IOException {
       // read timewindow file and select based on component and entries
       sourceTimewindowSet = TimewindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

       // read static correction data
       if (correctTime || amplitudeCorrectionType > 0) {
           Set<StaticCorrectionData> tmpset = StaticCorrectionDataFile.read(staticCorrectionPath);
           // choose only static corrections that have a pair timewindow
           staticCorrectionSet = tmpset.stream()
                   .filter(c -> sourceTimewindowSet.parallelStream()
                           .map(t -> c.isForTimewindow(t)).distinct().collect(Collectors.toSet()).contains(true))
                   .collect(Collectors.toSet());

           if (amplitudeCorrectionType == 2) {
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
       }

       if (correctMantle) {
           System.err.println("Using mantle corrections.");
           mantleCorrectionSet = StaticCorrectionDataFile.read(mantleCorrectionPath);
       }

       if (timewindowRefPath != null)
           refTimewindowSet = TimewindowDataFile.read(timewindowRefPath)
                   .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());

       Set<GlobalCMTID> eventSet = sourceTimewindowSet.stream().map(TimewindowData::getGlobalCMTID).collect(Collectors.toSet());
       Set<Observer> observerSet = sourceTimewindowSet.stream().map(TimewindowData::getObserver).collect(Collectors.toSet());
       Set<DataEntry> entrySet = sourceTimewindowSet.stream().map(TimewindowData::toDataEntry).collect(Collectors.toSet());

       Path outPath = DatasetAid.createOutputFolder(workPath, "compiled", folderTag, appendFolderDate, null);
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       EventListFile.write(eventSet, outPath.resolve("event.lst"));
       ObserverListFile.write(observerSet, outPath.resolve("observer.lst"));
       DataEntryListFile.writeFromSet(entrySet, outPath.resolve("dataEntry.lst"));

       Path actualPath = outPath.resolve("actual");
       Path envelopePath = outPath.resolve("envelope");
       Path hyPath = outPath.resolve("hy");
       Path spcAmpPath = outPath.resolve("spcAmp");
       Path spcRePath = outPath.resolve("spcRe");
       Path spcImPath = outPath.resolve("spcIm");

       ExecutorService es = ThreadAid.createFixedThreadPool();
       System.err.println("Working for " + eventSet.size() + " events.");
       // for each event, execute run() of class Worker, which is defined at the bottom of this java file
       eventSet.stream().map(Worker::new).forEach(es::execute);
       es.shutdown();
       while (!es.isTerminated()){
           ThreadAid.sleep(1000);
       }
       // this println() is for starting new line after writing "."s
       System.err.println();

       BasicIDFile.write(actualIDs, actualPath);
       BasicIDFile.write(envelopeIDs, envelopePath);
       BasicIDFile.write(hyIDs, hyPath);
       BasicIDFile.write(spcAmpIDs, spcAmpPath);
       BasicIDFile.write(spcReIDs, spcRePath);
       BasicIDFile.write(spcImIDs, spcImPath);

       System.err.println(" " + numberOfPairs.get() + " pairs of observed and synthetic waveforms are output.");
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

    /**
     * @param window
     * @author anselme
     * @return
     */
    private StaticCorrectionData getMantleCorrection(TimewindowData window) {
        List<StaticCorrectionData> corrs = mantleCorrectionSet.stream().filter(s -> s.isForTimewindow(window)).collect(Collectors.toList());
        if (corrs.size() > 1) {
            throw new RuntimeException("Found more than 1 mantle correction for window " + window);
        } else if (corrs.size() == 0) {
            return null;
        } else {
            return corrs.get(0);
        }
    }

    private double[] cutDataSac(SACFileAccess sac, Timewindow window) {
        Trace trace = sac.createTrace();
        return trace.resampleInWindow(window, sacSamplingHz, finalSamplingHz).getY();
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
        int startPoint = trace.findNearestXIndex(startTime);
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
        int startPoint = trace.findNearestXIndex(startTime);
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
        int startPoint = trace.findNearestXIndex(startTime);
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
        int startPoint = trace.findNearestXIndex(startTime);
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
            int j0 = refSpcAmp.findNearestXIndex(x);
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
        int startPoint = trace.findNearestXIndex(startTime);
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
        int startPoint = trace.findNearestXIndex(startTime);
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

    private class Worker extends DatasetAid.FilteredDatasetWorker {

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sourceTimewindowSet);
        }

        @Override
        public void actualWork(TimewindowData timewindow, SACFileAccess obsSac, SACFileAccess synSac) {
            Observer observer = timewindow.getObserver();
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

            // check and read bandpass
            if (obsSac.getValue(SACHeaderEnum.USER0) != synSac.getValue(SACHeaderEnum.USER0)
                    || obsSac.getValue(SACHeaderEnum.USER1) != synSac.getValue(SACHeaderEnum.USER1)) {
                System.err.println();
                System.err.println("!! Band pass filter difference, skipping: " + timewindow);
                return;
            }
            double minPeriod = obsSac.getValue(SACHeaderEnum.USER0) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER0);
            double maxPeriod = obsSac.getValue(SACHeaderEnum.USER1) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER1);

            //TODO delete following line by using Trace.resampleInWindow()
            int npts = (int) MathAid.floor((timewindow.getEndTime() - timewindow.getStartTime()) * finalSamplingHz) + 1;

            double startTime = timewindow.getStartTime();
            double shift = 0;
            double ratio = 1;
            if (correctTime || amplitudeCorrectionType > 0) {
                StaticCorrectionData sc = getStaticCorrection(timewindow);
                if (sc == null) {
                    System.err.println();
                    System.err.println("!! No static correction data, skipping: " + timewindow);
                    return;
                }
                if (correctTime) shift = sc.getTimeshift();
                switch (amplitudeCorrectionType) {
                case 1: ratio = sc.getAmplitudeRatio(); break;
                case 2: ratio = amplitudeCorrEventMap.get(timewindow.getGlobalCMTID()); break;
                }
            }
            if (correctMantle) {
                StaticCorrectionData sc = getMantleCorrection(timewindow);
                if (sc == null) {
                    System.err.println();
                    System.err.println("!! No mantle correction data, skipping: " + timewindow);
                    return;
                }
                shift += sc.getTimeshift();
            }

            TimewindowData windowRef = null;
            int nptsRef = 0;
            if (refTimewindowSet != null) {
                List<TimewindowData> tmpwindows = refTimewindowSet.stream().filter(tw ->
                        tw.getGlobalCMTID().equals(timewindow.getGlobalCMTID())
                        && tw.getObserver().equals(timewindow.getObserver())
                        && tw.getComponent().equals(timewindow.getComponent())).collect(Collectors.toList());
                if (tmpwindows.size() != 1) {
                    System.err.println();
                    System.err.println("!! Reference timewindow does not exist, skipping: " + timewindow);
                    return;
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
                obsData = cutDataSac(obsSac, timewindow.shift(-shift));
            double[] synData = cutDataSac(synSac, timewindow);

            // check
            RealVector obsVec = new ArrayRealVector(obsData);
            if (Double.isNaN(obsVec.getLInfNorm()) || obsVec.getLInfNorm() == 0) {
                System.err.println();
                System.err.println("!! Obs is 0 or NaN, skipping: " + timewindow);
                return;
            }

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

                if (amplitudeCorrectionType > 0) {
                    obsSpcAmp = correctSpcAmp(obsSpcAmpTrace, refObsSpcAmpTrace);
                    synSpcAmp = correctSpcAmp(synSpcAmpTrace, refSynSpcAmpTrace);
                }
                else {
                    obsSpcAmp = obsSpcAmpTrace.getY();
                    synSpcAmp = synSpcAmpTrace.getY();
                    double corrratio = amplitudeCorrEventMap.get(timewindow.getGlobalCMTID());
                    obsSpcAmp = Arrays.stream(obsSpcAmp).map(d -> d - Math.log(corrratio)).toArray();
                }
            }
            else {
                obsSpcAmp = obsSpcAmpTrace.getY();
                synSpcAmp = synSpcAmpTrace.getY();
            }

            double correctionRatio = ratio;

            Phase[] includePhases = timewindow.getPhases();

            obsData = Arrays.stream(obsData).map(d -> d / correctionRatio).toArray();
            BasicID synID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synData);
            BasicID obsID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsData);

            obsEnvelope = Arrays.stream(obsEnvelope).map(d -> d / correctionRatio).toArray();
            BasicID synEnvelopeID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synEnvelope);
            BasicID obsEnvelopeID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsEnvelope);

            obsHy = Arrays.stream(obsHy).map(d -> d / correctionRatio).toArray();
            BasicID synHyID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synHy);
            BasicID obsHyID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsHy);

            int fnpts = synSpcAmp.length;

            BasicID synSpcAmpID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synSpcAmp);
            BasicID obsSpcAmpID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsSpcAmp);

            BasicID synSpcReID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synSpcRe);
            BasicID obsSpcReID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsSpcRe);

            BasicID synSpcImID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, synSpcIm);
            BasicID obsSpcImID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, convolved, obsSpcIm);

            try {
                actualIDs.add(obsID);
                actualIDs.add(synID);
                envelopeIDs.add(obsEnvelopeID);
                envelopeIDs.add(synEnvelopeID);
                hyIDs.add(obsHyID);
                hyIDs.add(synHyID);
                spcAmpIDs.add(obsSpcAmpID);
                spcAmpIDs.add(synSpcAmpID);
                spcReIDs.add(obsSpcReID);
                spcReIDs.add(synSpcReID);
                spcImIDs.add(obsSpcImID);
                spcImIDs.add(synSpcImID);
                numberOfPairs.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
