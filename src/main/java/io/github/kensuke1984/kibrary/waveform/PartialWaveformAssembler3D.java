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
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionHandler;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.util.spc.SPCFile;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.ThreeDPartialMaker;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation that assembles partial derivative waveforms for 3-D parameters
 * from SPC files created by shfp、shbp、psvfp, and psvbp.
 * Output is written in the format of {@link PartialIDFile}.
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> the (event, observer, component)-pair is included in the input data entry file, if it is specified </li>
 * </ul>
 * <p>
 * SPC files for FP must be inside fpPath/eventDir/modelName/.
 * SPC files for BP must be inside bpPath/observerPositionCode/modelName/ (default) or bpPath/modelName (when using BP catalog).
 * For information about observerPositionCode, see {@link HorizontalPosition#toCode}.
 * Input SPC file names should take the form:<br>
 * voxelPointID.(observerPositionCode or eventID).(PB or PF)...(SH or PSV).spc<br>
 * It is possible to use only SH or only PSV, as well as to use both.
 * <p>
 * A set of partialTypes to work for must be specified.
 * When a voxel information file is provided, only the voxels included in it will be handled;
 * otherwise, all voxel points that have been computed for in FP and BP folders will be used.
 * This class does NOT handle time partials.
 * <p>
 * Time length (tlen) and the number of steps in frequency domain (np) must be same as the values used when running DSM.
 * <p>
 * Source time functions and filters can be applied to the waveforms.
 * The sample rate of the resulting data is {@link #finalSamplingHz}.
 * <p>
 * When using BP catalog, the BP waveforms will be interpolated from waveforms in the catalog
 * based on the epicentral distance from the source (= observer) to the voxel position.
 * <p>
 * Resulting entries can be specified by a (event, observer, component, partialType, voxelPosition, timeframe)-pair.
 *
 * @author Kensuke Konishi
 * @since version 2.3.0.5
 * @version 2021/12/24 renamed from waveformdata.PartialDatasetMaker_v2 to waveform.PartialWaveformAssembler3D
 */
public class PartialWaveformAssembler3D extends Operation {

    /**
     * Number of SPC files to take from BP catalog for interpolation
     */
    private static final int NUM_FROM_CATALOG = 3;

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
     * Information file about voxels for perturbations
     */
    private Path voxelPath;
    /**
     * set of partial type for computation
     */
    private Set<VariableType> variableTypes;
    /**
     * FPpool folder, containig event folders
     */
    private Path fpPath;
    /**
     * BPpool folder, containig event folders with observers as sources
     */
    private Path bpPath;
    /**
     * BPcat folder, to be used in catalog mode
     */
    private Path bpCatalogPath;
    /**
     * Name of folder, under the event folders, which contains the SPC files.
     * If SPC files are directly under the event folders, set as "".
     */
    private String modelName;
    /**
     * The SPC modes that shall be used: SH, PSV, or BOTH
     */
    private SpcFileAid.UsableSPCMode usableSPCMode;

    /**
     * Whether to use BP catalog
     */
    private boolean bpCatalogMode;
    private double thetamin;
    private double thetamax;
    private double dtheta;

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
    private double lowFreq;
    /**
     * upper frequency of bandpass [Hz]
     */
    private double highFreq;
    /**
     * see Saito, n
     */
    private int filterNp;
    /**
     * Whether to apply causal filter. true: causal, false: zero-phase
     */
    private boolean causal;
    /**
     * structure file for Q partial
     */
    private Path qStructurePath;


    private int nThreads;
    /**
     * Timewindows to work for.
     */
    private Set<TimewindowData> timewindowSet;
    private Set<FullPosition> voxelPositionSet;
    private Map<GlobalCMTID, SourceTimeFunction> sourceTimeFunctions;
    private ButterworthFilter filter;
    /**
     * バンドパスを安定させるためwindowを左右に ext = max period(s) ずつ伸ばす
     */
    private int ext;
    /**
     * structure for Q partial
     */
    private PolynomialStructure qStructure;
    /**
     * Created {@link PartialID}s.
     */
    private List<PartialID> partialIDs = Collections.synchronizedList(new ArrayList<>());

    private int bpCatNum;
    private List<SPCFileName> bpCatalogSH;
    private List<SPCFileName> bpCatalogPSV;

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
            pw.println("##SacComponents to be used. (Z R T)");
            pw.println("#components ");
            pw.println("##(double) SAC sampling frequency [Hz]. (20)");
            pw.println("#partialSamplingHz cant change now");
            pw.println("##(double) Sampling frequency in output files [Hz], must be a factor of sacSamplingHz. (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a timewindow data file, must be set.");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Path of a voxel information file, if you want to select the voxels to be worked for.");
            pw.println("#voxelPath voxel.inf");
            pw.println("##VariableTypes to compute for at each voxel, listed using spaces. (MU)");
            pw.println("#variableTypes ");
            pw.println("##Path of a forward propagate spc folder. (FPpool)");
            pw.println("#fpPath ");
            pw.println("##Path of a back propagate spc folder. (BPpool)");
            pw.println("#bpPath ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used. (PREM)");
            pw.println("#modelName ");
            pw.println("##The mode of spc files that have been computed, from {SH, PSV, BOTH}. (BOTH)");
            pw.println("#usableSPCMode ");
            pw.println("##########Settings for BP catalog.");
            pw.println("##(boolean) Whether to interpolate BP from a catalog. (false)");
            pw.println("#bpCatalogMode ");
            pw.println("##Path of a back propagate spc catalog folder, to be used in bpCatalog mode. (BPcat)");
            pw.println("#bpCatalogPath ");
            pw.println("##Theta range and sampling for the BP catalog in the format: thetamin thetamax dtheta. (1. 50. 2e-2)");
            pw.println("#thetaRange ");
            pw.println("##########Computation settings.");
            pw.println("##Path of folder containing source time functions. If not set, the following sourceTimeFunctionType will be used.");
            pw.println("#userSourceTimeFunctionPath ");
            pw.println("##Type of source time function, from {0:none, 1:boxcar, 2:triangle, 3:asymmetricTriangle, 4:auto}. (0)");
            pw.println("##  When 'auto' is selected, the function specified in the GCMT catalog will be used.");
            pw.println("#sourceTimeFunctionType ");
            pw.println("##Path of a catalog to set source time function durations. If unneeded, leave this unset.");
            pw.println("#sourceTimeFunctionCatalogPath ");
            pw.println("##Time length to be computed, must be a power of 2 over 10. (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, must be a power of 2. (512)");
            pw.println("#np ");
            pw.println("##Lower limit of the frequency band [Hz]. (0.005)");
            pw.println("#lowFreq ");
            pw.println("##Higher limit of the frequency band [Hz]. (0.08)");
            pw.println("#highFreq ");
            pw.println("##(int) The value of NP for the filter. (4)");
            pw.println("#filterNp ");
            pw.println("##(boolean) Whether to apply causal filter. When false, zero-phase filter is applied. (false)");
            pw.println("#causal ");
            pw.println("##File for Qstructure (if no file, then PREM).");
            pw.println("#qStructurePath ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialWaveformAssembler3D(Property property) throws IOException {
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
        if (property.containsKey("voxelPath")) {
            voxelPath = property.parsePath("voxelPath", null, true, workPath);
        }

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        for (VariableType type : variableTypes)
            if (type.equals(VariableType.TIME)) throw new IllegalArgumentException("This class does not handle time partials.");

        modelName = property.parseString("modelName", "PREM");  //TODO: use the same system as SPC_SAC ?
        usableSPCMode = SpcFileAid.UsableSPCMode.valueOf(property.parseString("usableSPCMode", "BOTH").toUpperCase());

        fpPath = property.parsePath("fpPath", "FPpool", true, workPath);
        bpCatalogMode = property.parseBoolean("bpCatalogMode", "false");
        if (bpCatalogMode) {
            bpCatalogPath = property.parsePath("bpCatalogPath", "BPcat", true, workPath);
            double[] tmpthetainfo = Stream.of(property.parseStringArray("thetaRange", null))
                    .mapToDouble(Double::parseDouble).toArray();
            thetamin = tmpthetainfo[0];
            thetamax = tmpthetainfo[1];
            dtheta = tmpthetainfo[2];
        } else {
            bpPath = property.parsePath("bpPath", "BPpool", true, workPath);
        }

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
        lowFreq = property.parseDouble("lowFreq", "0.005");
        highFreq = property.parseDouble("highFreq", "0.08");
        filterNp = property.parseInt("filterNp", "4");
        causal = property.parseBoolean("causal", "false");

        if (property.containsKey("qStructurePath")) {
            qStructurePath = property.parsePath("qinf", null, true, workPath);
        }
    }

    @Override
    public void run() throws IOException {
        System.err.println("Using mode " + usableSPCMode);
        System.err.println("Model name is " + modelName);
        // information about output partial types
        System.err.println(variableTypes.stream().map(Object::toString).collect(Collectors.joining(" ", "Computing for ", "")));

        // read timewindow file and select based on component and entries
        timewindowSet = TimewindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

        Set<GlobalCMTID> eventSet = timewindowSet.stream().map(TimewindowData::getGlobalCMTID).collect(Collectors.toSet());
        Set<Observer> observerSet = timewindowSet.stream().map(TimewindowData::getObserver).collect(Collectors.toSet());

        // check that all necessary FP and BP folders exist
        checkSPCExistence(eventSet, observerSet);

        // read BP catalog
        // This is independent of event or observer, thus is read here (not later in the loops).
        if (bpCatalogMode) {
            System.err.println("Using BP catalog");
            if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV)
                bpCatalogSH = SpcFileAid.collectOrderedSpcFileNamePFPB(bpCatalogPath.resolve(modelName), SPCMode.SH);
            if (usableSPCMode != SpcFileAid.UsableSPCMode.SH)
                bpCatalogPSV = SpcFileAid.collectOrderedSpcFileNamePFPB(bpCatalogPath.resolve(modelName), SPCMode.PSV);
            bpCatNum = (int) ((thetamax - thetamin) / dtheta) + 1;
        }

        // read voxel file
        if (voxelPath != null) voxelPositionSet = new VoxelInformationFile(voxelPath).fullPositionSet();

        // design bandpass filter
        filter = designBandPassFilter();
        // to stablize bandpass filtering, extend window at both ends for ext = max period(s) each
        ext = (int) (1 / lowFreq * partialSamplingHz);

        // set source time functions
        SourceTimeFunctionHandler stfHandler = new SourceTimeFunctionHandler(sourceTimeFunctionType,
                sourceTimeFunctionCatalogPath, userSourceTimeFunctionPath, eventSet);
        sourceTimeFunctions = stfHandler.createSourceTimeFunctionMap(np, tlen, partialSamplingHz);

        // read Q structure
        if (qStructurePath != null)
            qStructure = PolynomialStructureFile.read(qStructurePath);

        // create output folder
        outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        nThreads = Runtime.getRuntime().availableProcessors();
        System.err.println(nThreads + " processors available.");

        // loop for each event
        int num = 0;
        for (GlobalCMTID event : eventSet.stream().sorted().collect(Collectors.toList())) {
            System.err.println("Working for " + event.toPaddedString() + " : " + (++num) + "/" + eventSet.size());

            // assemble all partials for this event
            workForEvent(event);
        }

        // output in partial folder
        PartialIDFile.write(partialIDs, outPath.resolve("partial"));
    }

    private void workForEvent(GlobalCMTID event) throws IOException {
        // collect observers paired with this event
        Set<Observer> observersForEvent = timewindowSet.stream()
                .filter(info -> info.getGlobalCMTID().equals(event)).map(TimewindowData::getObserver)
                .collect(Collectors.toSet());
        if (observersForEvent.isEmpty())
            return;

        Path fpModelPath = fpPath.resolve(event.toString()).resolve(modelName);

        for (VariableType variableType : variableTypes) {

            // list of FP spc files, collected for each pixel
            // Up to 2 files (SH and PSV) can exist for each pixel.
            List<List<SPCFileName>> fpNames = collectSPCFileNames(fpModelPath, variableType);

            for (Observer observer : observersForEvent) {
                Set<TimewindowData> correspondingTimewindows = timewindowSet.stream()
                        .filter(info -> info.getGlobalCMTID().equals(event) && info.getObserver().equals(observer)).collect(Collectors.toSet());

                // list of BP spc files, collected for each pixel
                // Up to 2 files (SH and PSV) can exist for each pixel.
                List<List<SPCFileName>> bpNames = null;
                if (!bpCatalogMode) {
                    Path bpModelPath = bpPath.resolve(observer.getPosition().toCode()).resolve(modelName);
                    bpNames = collectSPCFileNames(bpModelPath, variableType);
                }

                // create ThreadPool for each set of corresponding FP and BP files (= for each pixel)
                ExecutorService execs = Executors.newFixedThreadPool(nThreads);
                for (int i = 0; i < fpNames.size(); i++) {
                    PartialComputation pc = null;
                    if (bpCatalogMode) {
                        pc = new PartialComputation(fpNames.get(i), correspondingTimewindows, event, observer, variableType);
                    } else {
                        pc = new PartialComputation(fpNames.get(i), bpNames.get(i), correspondingTimewindows, event, observer, variableType);
                    }
                    execs.execute(pc);
                }
                execs.shutdown();
                while (!execs.isTerminated()) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                 }
            }
        }
    }

    private List<List<SPCFileName>> collectSPCFileNames(Path spcModelPath, VariableType type) throws IOException {
        List<List<SPCFileName>> spcNames = new ArrayList<>();

        // collect all psv and sh files
        List<SPCFileName> shList = null;
        List<SPCFileName> psvList = null;
        if (type.equals(VariableType.RHO)) {
            if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) shList = SpcFileAid.collectOrderedSpcFileNameUFUB(spcModelPath, SPCMode.SH);
            if (usableSPCMode != SpcFileAid.UsableSPCMode.SH) psvList = SpcFileAid.collectOrderedSpcFileNameUFUB(spcModelPath, SPCMode.PSV);
        } else {
            if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) shList = SpcFileAid.collectOrderedSpcFileNamePFPB(spcModelPath, SPCMode.SH);
            if (usableSPCMode != SpcFileAid.UsableSPCMode.SH) psvList = SpcFileAid.collectOrderedSpcFileNamePFPB(spcModelPath, SPCMode.PSV);
        }

        // organize for each pixel
        int num = (shList != null) ? shList.size() : psvList.size();
        for (int i = 0; i < num; i++) {
            List<SPCFileName> spcsForPixel = new ArrayList<>();
            if (shList != null) spcsForPixel.add(shList.get(i));
            if (psvList != null) spcsForPixel.add(psvList.get(i));
            spcNames.add(spcsForPixel);
        }
        return spcNames;
    }

    private void checkSPCExistence(Set<GlobalCMTID> eventSet, Set<Observer> observerSet) {
        Set<GlobalCMTID> fpNonExistingEvents = eventSet.stream()
                .filter(id -> !Files.exists(fpPath.resolve(id.toString()).resolve(modelName)))
                .collect(Collectors.toSet());
        if (fpNonExistingEvents.size() > 0) {
            fpNonExistingEvents.forEach(event -> System.err.println(event));
            throw new IllegalStateException("FP files are not enough for " + timewindowPath);
        }
        if (!bpCatalogMode) {
            Set<Observer> bpNonExistingObservers = observerSet.stream()
                    .filter(observer -> !Files.exists(bpPath.resolve(observer.getPosition().toCode()).resolve(modelName)))
                    .collect(Collectors.toSet());
            if (bpNonExistingObservers.size() > 0) {
                bpNonExistingObservers.forEach(observer -> System.err.println(observer));
                throw new IllegalStateException("BP files are not enough for " + timewindowPath);
            }
        }
    }

    private ButterworthFilter designBandPassFilter() throws IOException {
        System.err.println("Designing filter. " + lowFreq + " - " + highFreq);
        double omegaH = highFreq * 2 * Math.PI / partialSamplingHz;
        double omegaL = lowFreq * 2 * Math.PI / partialSamplingHz;
        ButterworthFilter filter = new BandPassFilter(omegaH, omegaL, filterNp);
        filter.setCausal(causal);
        return filter;
    }

    /**
     * Computation for a set of corresponding FP and BP files.
     * @author Kensuke
     */
    private class PartialComputation implements Runnable {
        private List<SPCFileName> fpNames;
        private List<SPCFileAccess> fpFiles = new ArrayList<>();
        private List<SPCFileName> bpNames;
        private List<SPCFileAccess> bpFiles = new ArrayList<>();
        private Set<TimewindowData> timewindows;
        private GlobalCMTID event;
        private Observer observer;
        private VariableType variableType;
        /**
         * Coefficients for interpolation
         */
        private double[] dhBP = new double[NUM_FROM_CATALOG];

        /**
         * Constructor for non-BPCatalogMode.
         * @param fpNames
         * @param bpNames
         * @param timewindows
         * @param event
         * @param observer
         * @param variableType
         */
        private PartialComputation(List<SPCFileName> fpNames, List<SPCFileName> bpNames, Set<TimewindowData> timewindows,
                GlobalCMTID event, Observer observer, VariableType variableType) {
            this.fpNames = fpNames;
            this.bpNames = bpNames;
            this.timewindows = timewindows;
            this.event = event;
            this.observer = observer;
            this.variableType = variableType;
            if (bpCatalogMode) throw new IllegalStateException("Constructor for non-BPCatalogMode has been called.");
        }

        /**
         * Constructor for BPCatalogMode.
         * @param fpNames
         * @param timewindows
         * @param event
         * @param observer
         * @param variableType
         */
        private PartialComputation(List<SPCFileName> fpNames, Set<TimewindowData> timewindows,
                GlobalCMTID event, Observer observer, VariableType variableType) {
            this.fpNames = fpNames;
            this.timewindows = timewindows;
            this.event = event;
            this.observer = observer;
            this.variableType = variableType;
            if (!bpCatalogMode) throw new IllegalStateException("Constructor for BPCatalogMode has been called.");
        }

        /**
         * Read bp catalogue files to be used.
         * For iterpolation, get {@link #NUM_FROM_CATALOG} bp files.
         * @throws IOException
         */
        private void selectBPFromCatalog() throws IOException {
            HorizontalPosition voxelPos = fpFiles.get(0).getReceiverPosition();
            FullPosition observerPos = observer.getPosition().toFullPosition(Earth.EARTH_RADIUS);
            double distanceBP = observerPos.computeEpicentralDistanceDeg(voxelPos);
            double phiBP = Math.PI - observerPos.computeAzimuthRad(voxelPos);
            if (Double.isNaN(phiBP))
                throw new RuntimeException("PhiBP is NaN: " + fpFiles.get(0));

            // Gain index of epicentral distance catalogue
            int ipointBP = (int) ((distanceBP - thetamin) / dtheta);
            if (ipointBP < 0) {
                System.err.println("Warning: BP distance = " + distanceBP + " is smaller than thetamin = " + thetamin + " : " + fpFiles.get(0));
                ipointBP = 0;
            } else if (ipointBP > bpCatNum - NUM_FROM_CATALOG) {
                System.err.println("Warning: BP distance = " + distanceBP + " is greater than thetamax = " + thetamax + " : " + fpFiles.get(0));
                ipointBP = bpCatNum - NUM_FROM_CATALOG;
            }

            for (int i = 0; i < NUM_FROM_CATALOG; i++) {
                // Compute coefficients for interpolation
                double theta = thetamin + (ipointBP + i) * dtheta;
                dhBP[i] = (distanceBP - theta) / dtheta;

                // add BP file to list
                if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) {
                    bpFiles.add(SPCFile.getInstance(bpCatalogSH.get(ipointBP + i), phiBP, voxelPos, observerPos));
                }
                if (usableSPCMode != SpcFileAid.UsableSPCMode.SH) {
                    bpFiles.add(SPCFile.getInstance(bpCatalogPSV.get(ipointBP + i), phiBP, voxelPos, observerPos));
                }
            }
        }

        @Override
        public void run() {
            // read fp and bp files
            try {
                for (SPCFileName fpName : fpNames) {
                    fpFiles.add(fpName.read());
                }
                if (bpCatalogMode) {
                    selectBPFromCatalog();
                } else {
                    for (SPCFileName bpName : bpNames) {
                        bpFiles.add(bpName.read());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // check that the FP and BP files are pairs and are valid
            if (fpFiles.size() > 2) throw new IllegalStateException("Too many FP files; must be 1 or 2");
            if (fpFiles.size() == 2 && forSamePixel(fpFiles.get(0), fpFiles.get(1)) == false)
                throw new IllegalStateException("FP files do not match: " + fpFiles.get(0) + " " + fpFiles.get(1));
            if (fpFiles.get(0).tlen() != tlen || fpFiles.get(0).np() != np)
                throw new IllegalStateException(fpFiles.get(0).toString() + " has invalid tlen or np: "
                        + tlen + " " + fpFiles.get(0).tlen() + " " + np + " " + fpFiles.get(0).np());
            if (bpCatalogMode) {
                if (fpFiles.size() * NUM_FROM_CATALOG != bpFiles.size())
                    throw new IllegalStateException("Number of FP and BP files do not match");
            } else {
                if (fpFiles.size() != bpFiles.size()) throw new IllegalStateException("Number of FP and BP files do not match");
                for (int i = 0; i < bpFiles.size(); i++) {
                    if (!forSamePixel(fpFiles.get(0), bpFiles.get(i)))
                        throw new IllegalStateException("FP and BP files are not for same pixel: " + fpFiles.get(0) + " " + bpFiles.get(i));
                }
            }

            ThreeDPartialMaker threedPartialMaker = null;
            if (bpCatalogMode) {
                // when usableSCPMode==BOTH, fpFiles=[fpSH, fpPSV] and bpFiles=[bpSH0, bpPSV0, bpSH1, bpPSV1, bpSH2, bpPSV2]
                // otherwise, fpFiles=[fp] and bpFiles=[bp0, bp1, bp2]
                if (fpFiles.size() == 2) {
                    threedPartialMaker = new ThreeDPartialMaker(fpFiles.get(0), fpFiles.get(1), bpFiles.get(0), bpFiles.get(1),
                            bpFiles.get(2), bpFiles.get(3), bpFiles.get(4), bpFiles.get(5), dhBP);
                } else {
                    threedPartialMaker = new ThreeDPartialMaker(fpFiles.get(0), bpFiles.get(0), bpFiles.get(1), bpFiles.get(2), dhBP);
                }
            } else {
                if (fpFiles.size() == 2) {
                    threedPartialMaker = new ThreeDPartialMaker(fpFiles.get(0), fpFiles.get(1), bpFiles.get(0), bpFiles.get(1));
                } else {
                    threedPartialMaker = new ThreeDPartialMaker(fpFiles.get(0), bpFiles.get(0));
                }
            }
            threedPartialMaker.setSourceTimeFunction(sourceTimeFunctions.get(event));
            if (qStructure != null)
                threedPartialMaker.setStructure(qStructure);

            // assemble partial derivatives for waveform at i-th depth
            Set<SACComponent> neededComponents = timewindows.stream().map(TimewindowData::getComponent).collect(Collectors.toSet());
            for (int ibody = 0, nbody = fpFiles.get(0).nbody(); ibody < nbody; ibody++) {
                FullPosition voxelPosition = fpFiles.get(0).getReceiverPosition().toFullPosition(fpFiles.get(0).getBodyR()[ibody]);
                if (voxelPositionSet != null && voxelPositionSet.contains(voxelPosition) == false)
                    continue;

                for (SACComponent component : neededComponents) {
                    double[] partial = threedPartialMaker.createPartialSerial(component, ibody, PartialType.of(ParameterType.VOXEL, variableType));

                    timewindows.stream().filter(timewindow -> timewindow.getComponent() == component).forEach(window -> {
                        Trace cutTrace = cutAndFilter(partial, window);
                        PartialID partialID = new PartialID(observer, event, component, finalSamplingHz, cutTrace.getMinX(),
                                cutTrace.getLength(), 1 / highFreq, 1 / lowFreq, window.getPhases(),
                                sourceTimeFunctionType != SourceTimeFunctionType.NONE,
                                ParameterType.VOXEL, variableType, voxelPosition, cutTrace.getY());
                        partialIDs.add(partialID);
                    });
                }
            }
        }

        private Trace cutAndFilter(double[] partial, Timewindow timewindow) {
            // cut to long window for filtering
            int iStart = (int) (timewindow.getStartTime() * partialSamplingHz) - ext;
            int iEnd = (int) (timewindow.getEndTime() * partialSamplingHz) + ext;
            double[] cutPartial = new double[iEnd - iStart];
            // if cutstart < 0 (i.e. before event time), zero-pad the beginning part
            Arrays.parallelSetAll(cutPartial, i -> (i + iStart < 0 ? 0 : partial[i + iStart]));

            // filter
            double[] filteredPartial = filter.applyFilter(cutPartial);

            // cut and resample in timewindow
            double[] xs = IntStream.range(0, iEnd - iStart).mapToDouble(i -> (i + iStart) / partialSamplingHz).toArray();
            Trace filteredTrace = new Trace(xs, filteredPartial);
            return filteredTrace.resampleInWindow(timewindow, partialSamplingHz, finalSamplingHz);
        }

        private boolean forSamePixel(SPCFileAccess spc1, SPCFileAccess spc2) {
            if (!spc1.getReceiverPosition().equals(spc2.getReceiverPosition())) return false;
            else return true;
        }

    }

}
