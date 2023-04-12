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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionHandler;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;

/**
 * Creates a pair of files containing 1-D partial derivatives
 *
 * TODO shとpsvの曖昧さ 両方ある場合ない場合等 現状では combineして対処している
 *
 * Time length (tlen) and the number of step in frequency domain (np) in DSM
 * software must be same. Those values are set in a parameter file.
 *
 * Only partials for radius written in a parameter file are computed.
 *
 *
 * @author Kensuke Konishi
 * @since version 0.2.0.3
 * @version 2021/12/24 renamed from Partial1DDatasetMaker to PartialWaveformAssembler1D
 */
public class PartialWaveformAssembler1D extends Operation {

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
     * output directory Path
     */
    private Path outPath;
    /**
     * components to be used
     */
    private Set<SACComponent> components;
    /**
     * spcFileをコンボリューションして時系列にする時のサンプリングHz デフォルトは２０ TODOまだ触れない
     */
    private double partialSamplingHz = 20;
    /**
     * 最後に時系列で切り出す時のサンプリングヘルツ(Hz)
     */
    private double finalSamplingHz;

    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a data entry list file
     */
    private Path dataEntryPath;
    /**
     * set of partial type for computation
     */
    private Set<PartialType> partialTypes;
    /**
     * radius of perturbation
     */
    private double[] bodyR;
    private Path shPath;
    private Path psvPath;
    /**
     * The SPC modes that shall be used: SH, PSV, or BOTH
     */
    private SpcFileAid.UsableSPCMode usableSPCMode;
    /**
     * the name of a folder containing SPC files (e.g. PREM)（""）
     */
    private String modelName;

    /**
     * source time function. 0: none, 1: boxcar, 2: triangle, 3: asymmetric triangle, 4: auto
     */
    private SourceTimeFunctionType sourceTimeFunctionType;
    /**
     * Folder containing user-defined source time functions
     */
    private Path userSourceTimeFunctionPath;
    /**
     * Catalog containing source time function durations
     */
    private Path sourceTimeFunctionCatalogPath;

    /**
     * time length (DSM parameter)
     */
    private double tlen;
    /**
     * step of frequency domain (DSM parameter)
     */
    private int np;
    /**
     * lower frequency of bandpass [Hz]
     */
    private double minFreq;
    /**
     * upper frequency of bandpass [Hz]
     */
    private double maxFreq;
    /**
     * see Saito, n
     */
    private int filterNp;
    /**
     * Whether to apply causal filter. true: causal, false: zero-phase
     */
    private boolean causal;


    /**
     * Timewindows to work for.
     */
    private Set<TimewindowData> timewindowSet;
    private Map<GlobalCMTID, SourceTimeFunction> sourceTimeFunctions;
    private ButterworthFilter filter;

    /**
     * Created {@link PartialID}s.
     */
    private List<PartialID> partialIDs = Collections.synchronizedList(new ArrayList<>());



    private int lsmooth;


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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Value of sac sampling Hz (20) can't be changed now");
            pw.println("#partialSamplingHz the value will be ignored");
            pw.println("##(double) Value of sampling Hz in output files, must be a factor of sacSamplingHz (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a timewindow data file, must be set");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##PartialTypes to compute for at each voxel, listed using spaces (PAR2)");
            pw.println("#partialTypes ");
            pw.println("##(double[]) Radii of perturbation points, listed using spaces, must be set");
            pw.println("#bodyR 3505 3555 3605");
            pw.println("##Path of an SH folder (.)");
            pw.println("#shPath ");
            pw.println("##Path of a PSV folder (.)");
            pw.println("#psvPath ");
            pw.println("##The mode of spc files that have been computed, from {SH, PSV, BOTH} (BOTH)");
            pw.println("#usableSPCMode ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used. (PREM)");
            pw.println("#modelName ");
            pw.println("##########Computation settings");
            pw.println("##Path of folder containing source time functions. If not set, the following sourceTimeFunctionType will be used.");
            pw.println("#userSourceTimeFunctionPath ");
            pw.println("##Type of source time function, from {0:none, 1:boxcar, 2:triangle, 3:asymmetricTriangle, 4:auto} (0)");
            pw.println("##  When 'auto' is selected, the function specified in the GCMT catalog will be used.");
            pw.println("#sourceTimeFunctionType ");
            pw.println("##Path of a catalog to set source time function durations. If unneeded, leave this unset.");
            pw.println("#sourceTimeFunctionCatalogPath ");
            pw.println("##Time length to be computed, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, must be a power of 2 (512)");
            pw.println("#np ");
            pw.println("##(double) Minimum value of passband (0.005)");
            pw.println("#minFreq ");
            pw.println("##(double) Maximum value of passband (0.08)");
            pw.println("#maxFreq ");
            pw.println("##(int) The value of np for the filter (4)");
            pw.println("#filterNp ");
            pw.println("##(boolean) Whether to apply causal filter. When false, zero-phase filter is applied. (false)");
            pw.println("#causal ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialWaveformAssembler1D(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialSamplingHz = 20;  // TODO property.parseDouble("sacSamplingHz", "20");
        finalSamplingHz = property.parseDouble("finalSamplingHz", "1");
        if (partialSamplingHz % finalSamplingHz != 0)
            throw new IllegalArgumentException("Must choose a finalSamplingHz that divides " + partialSamplingHz);

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "PAR2")).map(PartialType::valueOf)
                .collect(Collectors.toSet());
        for (PartialType type : partialTypes)
            if (type.isTimePartial()) throw new IllegalArgumentException("This class does not handle time partials.");
        bodyR = property.parseDoubleArray("bodyR", null);

        shPath = property.parsePath("shPath", ".", true, workPath);
        psvPath = property.parsePath("psvPath", ".", true, workPath);
        usableSPCMode = SpcFileAid.UsableSPCMode.valueOf(property.parseString("usableSPCMode", "BOTH").toUpperCase());
        modelName = property.parseString("modelName", "PREM");  //TODO: use the same system as SPC_SAC ?

        if (property.containsKey("userSourceTimeFunctionPath")) {
            userSourceTimeFunctionPath = property.parsePath("userSourceTimeFunctionPath", null, true, workPath);
        } else {
            sourceTimeFunctionType = SourceTimeFunctionType.valueOf(property.parseInt("sourceTimeFunctionType", "0"));
        }
        if (property.containsKey("sourceTimeFunctionCatalogPath")) {
            sourceTimeFunctionCatalogPath = property.parsePath("sourceTimeFunctionCatalogPath", null, true, workPath);
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        maxFreq = property.parseDouble("maxFreq", "0.08");
        minFreq = property.parseDouble("minFreq", "0.005");
        filterNp = property.parseInt("filterNp", "4");
        causal = property.parseBoolean("causal", "false");
    }

    private void setLsmooth() {
        int pow2np = Integer.highestOneBit(np);
        if (pow2np < np)
            pow2np *= 2;

        int lsmooth = (int) (0.5 * tlen * partialSamplingHz / pow2np);
        int ismooth = Integer.highestOneBit(lsmooth);
        this.lsmooth = ismooth == lsmooth ? lsmooth : ismooth * 2;
    }


    @Override
    public void run() throws IOException {
        System.err.println("Using mode " + usableSPCMode);
        System.err.println("Model name is " + modelName);
        // information about output partial types
        System.err.println(partialTypes.stream().map(Object::toString).collect(Collectors.joining(" ", "Computing for ", "")));

        setLsmooth();

        // read timewindow file and select based on component and entries
        timewindowSet = TimewindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

        // collect events
        Set<GlobalCMTID> eventSet = timewindowSet.stream().map(TimewindowData::getGlobalCMTID).collect(Collectors.toSet());

        // set source time functions
        SourceTimeFunctionHandler stfHandler = new SourceTimeFunctionHandler(sourceTimeFunctionType,
                sourceTimeFunctionCatalogPath, userSourceTimeFunctionPath, eventSet);
        sourceTimeFunctions = stfHandler.createSourceTimeFunctionMap(np, tlen, partialSamplingHz);

        // design bandpass filter
        filter = designBandPassFilter();

        // create output folder
        outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()){
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        // output in partial folder
        PartialIDFile.write(partialIDs, outPath.resolve("partial"));
    }

    private ButterworthFilter designBandPassFilter() throws IOException {
        System.err.println("Designing filter.");
        double omegaH = maxFreq * 2 * Math.PI / partialSamplingHz;
        double omegaL = minFreq * 2 * Math.PI / partialSamplingHz;
        ButterworthFilter filter = new BandPassFilter(omegaH, omegaL, filterNp);
        filter.setCausal(causal);
        return filter;
    }

    private class Worker implements Runnable {

        private final GlobalCMTID event;
        private final Path shModelPath;
        private final Path psvModelPath;

        private Worker(GlobalCMTID event) {
            this.event = event;
            shModelPath = shPath.resolve(event.toString()).resolve(modelName);
            psvModelPath = psvPath.resolve(event.toString()).resolve(modelName);
        }

        @Override
        public void run() {
            if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV && Files.exists(shModelPath) == false) {
                System.err.println(shModelPath + " does not exist...");
                return;
            }
            if (usableSPCMode != SpcFileAid.UsableSPCMode.SH && Files.exists(psvModelPath) == false) {
                System.err.println(psvModelPath + " does not exist...");
                return;
            }

            Set<Observer> correspondingObservers = timewindowSet.stream()
                    .filter(timewindow -> timewindow.getGlobalCMTID().equals(event))
                    .map(TimewindowData::getObserver).collect(Collectors.toSet());

            for (Observer observer : correspondingObservers) {
                for (PartialType partialType : partialTypes) {
                    try {
                        convertSPCToPartials(observer, partialType);
                    } catch (IOException e) {
                        // this println() is for starting new line after writing "."s
                        System.err.println();
                        System.err.println("Failure for " + observer + " " + partialType);
                        e.printStackTrace();
                    }
                }
            }
            System.err.print(".");
        }

        private void convertSPCToPartials(Observer observer, PartialType partialType) throws IOException {
            // collect SPC files
            SPCFileAccess shSPCFile = (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) ? findSPCFile(observer, partialType, SPCMode.SH) : null;
            SPCFileAccess psvSPCFile = (usableSPCMode != SpcFileAid.UsableSPCMode.SH) ? findSPCFile(observer, partialType, SPCMode.PSV) : null;

            // collect corresponding timewindows
            Set<TimewindowData> correspondingTimewindows = timewindowSet.stream()
                    .filter(timewindow -> timewindow.getGlobalCMTID().equals(event) && timewindow.getObserver().equals(observer))
                    .collect(Collectors.toSet());

            for (TimewindowData timewindow : correspondingTimewindows) {
                if (usableSPCMode == SpcFileAid.UsableSPCMode.SH) {
                    buildPartialWaveform(shSPCFile, timewindow, partialType);
                } else if (usableSPCMode == SpcFileAid.UsableSPCMode.PSV) {
                    buildPartialWaveform(psvSPCFile, timewindow, partialType);
                } else {
                    buildPartialWaveform(shSPCFile, psvSPCFile, timewindow, partialType);
                }
            }
        }

        private SPCFileAccess findSPCFile(Observer observer, PartialType partialType, SPCMode mode) throws IOException {
            Path modelPath = (mode == SPCMode.SH) ? shModelPath : psvModelPath;
            Path spcPath = modelPath.resolve(observer.getPosition().toCode() + "." + event + "." + partialType + "..." + mode + ".spc");
            if (!SPCFileName.isFormatted(spcPath)) {
                throw new IllegalStateException(spcPath + " has invalid SPC file name.");
            }
            SPCFileName spcName = new FormattedSPCFileName(spcPath);
            SPCFileAccess spcFile = spcName.read();
            if (spcFile.tlen() != tlen || spcFile.np() != np) {
                throw new IllegalStateException(spcFile + " has different np or tlen.");
            }
            process(spcFile);
            return spcFile;
        }

        private void buildPartialWaveform(SPCFileAccess spcFile, TimewindowData timewindow, PartialType partialType) {
            for (int k = 0; k < spcFile.nbody(); k++) { //TODO loop for r:bodyR, so that error can be thrown when data for a radius doesn't exist
                double currentBodyR = spcFile.getBodyR()[k];
                boolean exists = false;
                for (double radius : bodyR)
                    if (Precision.equals(radius, currentBodyR, FullPosition.RADIUS_EPSILON))
                        exists = true;
                if (!exists)
                    continue;
                double[] ut = spcFile.getSpcBodyList().get(k).getSpcComponent(timewindow.getComponent()).getTimeseries();

                // apply filter
                double[] filteredUt = filter.applyFilter(ut);

                cutAndWrite(timewindow.getObserver(), filteredUt, timewindow, currentBodyR, partialType);
            }
        }
        private void buildPartialWaveform(SPCFileAccess shSPCFile, SPCFileAccess psvSPCFile, TimewindowData timewindow, PartialType partialType) {
            for (int k = 0; k < shSPCFile.nbody(); k++) { //TODO loop for r:bodyR, so that error can be thrown when data for a radius doesn't exist
                if (!Precision.equals(shSPCFile.getBodyR()[k], psvSPCFile.getBodyR()[k], FullPosition.RADIUS_EPSILON)) {
                    throw new RuntimeException("SH and PSV bodyR differ " + shSPCFile.getBodyR()[k] + " " + psvSPCFile.getBodyR()[k]);
                }
                double currentBodyR = shSPCFile.getBodyR()[k];
                boolean exists = false;
                for (double radius : bodyR)
                    if (Precision.equals(radius, currentBodyR, FullPosition.RADIUS_EPSILON))
                        exists = true;
                if (!exists)
                    continue;
                double[] shUt = shSPCFile.getSpcBodyList().get(k).getSpcComponent(timewindow.getComponent()).getTimeseries();
                double[] psvUt = psvSPCFile.getSpcBodyList().get(k).getSpcComponent(timewindow.getComponent()).getTimeseries();

                if (shUt.length != psvUt.length)
                    throw new RuntimeException("sh and psv timeseries do not have the same length " + shUt.length + " " + psvUt.length);

                // apply filter
                double[] filteredSHUt = filter.applyFilter(shUt);
                double[] filteredPSVUt = filter.applyFilter(psvUt);
                double[] summedUt = new double[filteredSHUt.length];
                for (int it = 0; it < filteredSHUt.length; it++)
                    summedUt[it] = filteredSHUt[it] + filteredPSVUt[it];

                cutAndWrite(timewindow.getObserver(), summedUt, timewindow, currentBodyR, partialType);
            }
        }

        private void process(SPCFileAccess spcFile) {
            for (SACComponent component : components) {
                spcFile.getSpcBodyList().stream().map(body -> body.getSpcComponent(component))
                        .forEach(spcComponent -> {
                            spcComponent.applySourceTimeFunction(sourceTimeFunctions.get(event));
                            spcComponent.toTimeDomain(lsmooth);
                            spcComponent.applyGrowingExponential(spcFile.omegai(), tlen);
                            spcComponent.amplitudeCorrection(tlen);
                        });
            }
        }

        private void cutAndWrite(Observer observer, double[] filteredUt, TimewindowData timewindow, double bodyR, PartialType partialType) {
            double[] xs = IntStream.range(0, filteredUt.length).mapToDouble(i -> i / partialSamplingHz).toArray();
            Trace filteredTrace = new Trace(xs, filteredUt);
            Trace resampledTrace = filteredTrace.resampleInWindow(timewindow, partialSamplingHz, finalSamplingHz);

            PartialID partialID = new PartialID(observer, event, timewindow.getComponent(), finalSamplingHz,
                    timewindow.getStartTime(), resampledTrace.getLength(),1 / maxFreq, 1 / minFreq,
                    timewindow.getPhases(), sourceTimeFunctionType != SourceTimeFunctionType.NONE,
                    new FullPosition(0, 0, bodyR), partialType, resampledTrace.getY());
            partialIDs.add(partialID);
        }

    }

}
