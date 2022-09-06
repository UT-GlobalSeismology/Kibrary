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
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.HilbertTransform;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Operation that exports dataset containing observed and synthetic waveforms. <br>
 * The write is a set of an ID and waveform files.
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
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
 * Static correction is applied as described in {@link StaticCorrection}.
 * <p>
 * This class does not apply a digital filter, but extracts information about the passband written in SAC files.
 *
 * @since a long time ago
 * @version 2021/11/3 renamed from waveformdata.ObservedSyntheticDatasetMaker to waveform.ActualWaveformCompiler
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
     * Path of a root folder containing observed dataset
     */
    private Path obsPath;
    /**
     * Path of a root folder containing synthetic dataset
     */
    private Path synPath;
    /**
     * if it is true, the dataset contains synthetic waveforms after
     * convolution
     */
    private boolean convolved;
    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a timewindow information file for a reference phase use to correct spectral amplitude
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
     * Path of a data entry file
     */
    private Path dataEntryPath;
    /**
     * Path of a static correction file
     */
    private Path staticCorrectionPath;
    /**
     * [bool] time-shift data to correct for 3-D mantle
     */
    private boolean correctMantle;
    /**
     * Path of time shifts due to the 3-D mantle
     */
    private Path mantleCorrectionPath;

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
            pw.println("##Path of a timewindow file, must be defined");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a timewindow file for a reference phase used to correct spectral amplitude, can be ignored");
            pw.println("#timewindowRefPath ");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved (true)");
            pw.println("#convolved ");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Path of a static correction file");
            pw.println("## If the following correctTime or correctAmplitude is true, this path must be defined.");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##(boolean) Whether time should be corrected (false)");
            pw.println("#correctTime ");
            pw.println("##(boolean) Whether amplitude should be corrected (false)");
            pw.println("#correctAmplitude ");
            pw.println("##Path of a mantle correction file");
            pw.println("## If the following correctMantle is true, this path must be defined.");
            pw.println("#mantleCorrectionPath mantleCorrectionPath.dat");
            pw.println("##(boolean) Whether mantle should be corrected for (false)");
            pw.println("#correctMantle ");
            pw.println("#lowFreq "); // TODO: add explanation
            pw.println("#highFreq "); // TODO: add explanation
            pw.println("##(boolean) Whether to add noise for synthetic test (false)");
            pw.println("#addNoise ");
            pw.println("##Power of noise for synthetic test (1)"); //TODO what is the unit?
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
        correctAmplitude = property.parseBoolean("correctAmplitude", "false");
        if (correctTime || correctAmplitude) {
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
       if (dataEntryPath != null) {
           // read entry set to be used for selection
           Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

           // read timewindows and select based on component and entries
           sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                   .stream().filter(window -> components.contains(window.getComponent()) &&
                           entrySet.contains(new DataEntry(window.getGlobalCMTID(), window.getObserver(), window.getComponent())))
                   .collect(Collectors.toSet());
       } else {
           // read timewindows and select based on component
           sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                   .stream().filter(window -> components.contains(window.getComponent()))
                   .collect(Collectors.toSet());
       }

       // read static correction data
       if (correctTime || correctAmplitude) {
           Set<StaticCorrectionData> tmpset = StaticCorrectionDataFile.read(staticCorrectionPath);
           // choose only static corrections that have a pair timewindow
           staticCorrectionSet = tmpset.stream()
                   .filter(c -> sourceTimewindowSet.parallelStream()
                           .map(t -> c.isForTimewindow(t)).distinct().collect(Collectors.toSet()).contains(true))
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
               .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());

       eventSet = sourceTimewindowSet.stream().map(TimewindowData::getGlobalCMTID)
               .collect(Collectors.toSet());
       observerSet = sourceTimewindowSet.stream().map(TimewindowData::getObserver)
               .collect(Collectors.toSet());
       phases = sourceTimewindowSet.stream().map(TimewindowData::getPhases).flatMap(p -> Arrays.stream(p))
               .distinct().toArray(Phase[]::new);

       readPeriodRanges();

       String dateStr = GadgetAid.getTemporaryString();
       outPath = DatasetAid.createOutputFolder(workPath, "compiled", tag, dateStr);
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       EventListFile.write(eventSet, outPath.resolve("event.lst"));
       ObserverListFile.write(observerSet, outPath.resolve("observer.lst"));

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

           for (GlobalCMTID event : eventSet)
               es.execute(new Worker(event));
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
            Set<SACFileName> sacfilenames = DatasetAid.sacFileNameSet(obsPath).stream().limit(20).collect(Collectors.toSet()); // TODO is this limit(20) OK?
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
        List<StaticCorrectionData> corrs = staticCorrectionSet.stream().filter(s -> s.isForTimewindow(window)).collect(Collectors.toList());
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
        List<StaticCorrectionData> corrs = mantleCorrectionSet.stream().filter(s -> s.isForTimewindow(window)).collect(Collectors.toList());
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

    private class Worker extends DatasetAid.FilteredDatasetWorker {

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sourceTimewindowSet);
        }

        @Override
        public void actualWork(TimewindowData timewindow, SACFileAccess obsSac, SACFileAccess synSac) {
            Observer observer = timewindow.getObserver();
            SACComponent component = timewindow.getComponent();

            // check delta
            double delta = 1 / sacSamplingHz;
            if (delta != obsSac.getValue(SACHeaderEnum.DELTA) || delta != synSac.getValue(SACHeaderEnum.DELTA)) {
                System.err.println(
                        "!! Deltas are invalid. " + obsSac + " " + obsSac.getValue(SACHeaderEnum.DELTA) + " , " +
                                synSac + " " + synSac.getValue(SACHeaderEnum.DELTA) + " ; must be " + delta);
                return;
            }

            // check and read bandpass
            if (obsSac.getValue(SACHeaderEnum.USER0) != synSac.getValue(SACHeaderEnum.USER0)
                    || obsSac.getValue(SACHeaderEnum.USER1) != synSac.getValue(SACHeaderEnum.USER1)) {
                System.err.println("band pass filter difference");
                return;
            }
            double minPeriod = obsSac.getValue(SACHeaderEnum.USER0) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER0);
            double maxPeriod = obsSac.getValue(SACHeaderEnum.USER1) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER1);

            if (timewindow.getEndTime() > synSac.getValue(SACHeaderEnum.E) - 10) { // TODO should 10 be maxStaticShift ?
                System.err.println("!! End time of timewindow " + timewindow + " is too late.");
                return;
            }

            int npts = (int) ((timewindow.getEndTime() - timewindow.getStartTime()) * finalSamplingHz);
            double startTime = timewindow.getStartTime();
            double shift = 0;
            double ratio = 1;
            if (correctTime || correctAmplitude) {
                try {
                    StaticCorrectionData sc = getStaticCorrection(timewindow);
                    shift = correctTime ? sc.getTimeshift() : 0;
//                  ratio = amplitudeCorrection ? sc.getAmplitudeRatio() : 1;
                    ratio = correctAmplitude ? sc.getAmplitudeRatio() : amplitudeCorrEventMap.get(timewindow.getGlobalCMTID());
                } catch (NoSuchElementException e) {
                    System.err.println("There is no static correction information for");
                    System.err.println("   " + timewindow);
                    return;
                }
            }
            if (correctMantle) {
                try {
                    StaticCorrectionData sc = getMantleCorrection(timewindow);
                    shift += sc.getTimeshift();
                } catch (NoSuchElementException e) {
                    System.err.println("There is no mantle correction information for");
                    System.err.println("   " + timewindow);
                    return;
                }
            }

            TimewindowData windowRef = null;
            int nptsRef = 0;
            if (refTimewindowSet != null) {
                List<TimewindowData> tmpwindows = refTimewindowSet.stream().filter(tw ->
                        tw.getGlobalCMTID().equals(timewindow.getGlobalCMTID())
                        && tw.getObserver().equals(timewindow.getObserver())
                        && tw.getComponent().equals(timewindow.getComponent())).collect(Collectors.toList());
                if (tmpwindows.size() != 1) {
                    System.err.println("Reference timewindow does not exist " + timewindow);
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
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synData);
            BasicID obsID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, obsData);

            obsEnvelope = Arrays.stream(obsEnvelope).map(d -> d / correctionRatio).toArray();
            BasicID synEnvelopeID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synEnvelope);
            BasicID obsEnvelopeID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, obsEnvelope);

            obsHy = Arrays.stream(obsHy).map(d -> d / correctionRatio).toArray();
            BasicID synHyID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synHy);
            BasicID obsHyID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, obsHy);

            int fnpts = synSpcAmp.length;

            BasicID synSpcAmpID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcAmp);
            BasicID obsSpcAmpID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, obsSpcAmp);

            BasicID synSpcReID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcRe);
            BasicID obsSpcReID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, obsSpcRe);

            BasicID synSpcImID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, observer, eventID,
                    component, minPeriod, maxPeriod, includePhases, 0, convolved, synSpcIm);
            BasicID obsSpcImID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, observer, eventID,
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

}
