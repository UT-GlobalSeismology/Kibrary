package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionHandler;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalog;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.util.spc.SPCFile;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.ThreeDPartialMaker;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation that assembles partial waveforms from SPC files created by shfp、shbp、psvfp, and psvbp.
 * <p>
 * SPC files for FP must be inside fpPath/eventDir/modelName/.
 * SPC files for BP must be inside bpPath/observerPositionCode/modelName/ (default) or bpPath/modelName (when using epicentral distance catalogue).
 * For information about observerPositionCode, see {@link HorizontalPosition#toCode}.
 * Input SPC file names should take the form:
 * (point name).(observerPositionCode or eventID).(PB or PF)...(sh or psv).spc
 * <p>
 * A timewindow data file, a voxel information file, and a set of partialTypes to work for must be specified.
 * TODO organize this program file and decide which set of data is worked for.
 *
 * <p>
 * halfDurationはevent informationファイルから読み取る
 *
 * time window informationファイルの中からtime windowを見つける。 その中に入っている震源観測点成分の組み合わせのみ計算する
 *
 * バンドパスをかけて保存する
 *
 *
 * TODO station とかの書き出し
 *
 * 例： directory/19841006/*spc directory/0000KKK/*spc
 *
 * 摂動点の情報がない摂動点に対しては計算しない
 *
 *
 * @author Kensuke Konishi
 * @since version 2.3.0.5
 * @version 2021/12/24 renamed from waveformdata.PartialDatasetMaker_v2 to waveform.PartialWaveformAssembler3D
 */
public class PartialWaveformAssembler3D extends Operation {

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
     * Path of a data entry file
     */
    private Path dataEntryPath;
    /**
     * Information file about voxels for perturbations
     */
    private Path voxelPath;
    /**
     * set of partial type for computation
     */
    private Set<PartialType> partialTypes;
    /**
     * FPpool このフォルダの直下に イベントフォルダ（FP）を置く
     */
    private Path fpPath;
    /**
     * BPpool このフォルダの直下に 観測点をソースとしたフォルダ（BP）を置く
     */
    private Path bpPath;
    /**
     * bp, fp フォルダの下のどこにspcファイルがあるか 直下なら何も入れない（""）
     */
    private String modelName;
    private String mode;

    /**
     *  For epicentral distance catalogue
     */
    private boolean catalogue;
    private double thetamin;
    private double thetamax;
    private double dtheta;
    private int bpCatNum;
    private SPCFileName[] bpNames;
    private SPCFileName[] bpNames_PSV;

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
     * bandpassの最小周波数（Hz）
     */
    private double minFreq;

    /**
     * bandpassの最大周波数（Hz）
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
     * structure for Q partial
     */
    private PolynomialStructure structure;
    private Path timePartialPath;

    private ButterworthFilter filter;
    /**
     * バンドパスを安定させるためwindowを左右に ext = max period(s) ずつ伸ばす
     */
    private int ext;
    /**
     * sacdataを何ポイントおきに取り出すか
     */
    private int step;
    private Set<TimewindowData> timewindowSet;
    private Set<GlobalCMTID> touchedSet = new HashSet<>();

    private String dateString;

    private WaveformDataWriter partialDataWriter;

    private Path logPath;

    private Set<Observer> observerSet;
    private Set<GlobalCMTID> eventSet;
    private double[][] periodRanges;
    private Phase[] phases;
    private Set<FullPosition> voxelPositionSet;
    private Map<GlobalCMTID, SourceTimeFunction> sourceTimeFunctions;


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
            pw.println("##(double) Sac sampling Hz (20)");
            pw.println("#partialSamplingHz cant change now");
            pw.println("##(double) Value of sampling Hz in output files, must be a factor of sacSamplingHz (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a time window file, must be set");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Voxel file path, must be set");
            pw.println("#voxelPath voxel.inf");
            pw.println("##PartialTypes to compute for, listed using spaces (MU)");
            pw.println("#partialTypes ");
            pw.println("##Path of a forward propagate spc folder (FPpool)");
            pw.println("#fpPath ");
            pw.println("##Path of a back propagate spc folder (default:BPpool, catalogue:BPcat)");
            pw.println("#bpPath ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used. (PREM)");
            pw.println("#modelName ");
            pw.println("##The mode of spc files that have been computed, from {PSV, SH, BOTH} (SH)");
            pw.println("#mode ");
            pw.println("##(boolean) Whether to interpolate fp and bp from a catalogue (false)");
            pw.println("#catalogue ");
            pw.println("##Theta- range and sampling for the BP catalog in the format: thetamin thetamax dtheta. (1. 50. 2e-2)");
            pw.println("#thetaRange ");
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
            pw.println("##File for Qstructure (if no file, then PREM)");
            pw.println("#qinf ");
            pw.println("##Path of the time partials directory, must be set if PartialType contains TIME_SOURCE or TIME_RECEIVER");
            pw.println("#timePartialPath ");
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

        catalogue = property.parseBoolean("catalogue", "false");
        if (catalogue) {
            double[] tmpthetainfo = Stream.of(property.parseStringArray("thetaRange", null))
                    .mapToDouble(Double::parseDouble).toArray();
            thetamin = tmpthetainfo[0];
            thetamax = tmpthetainfo[1];
            dtheta = tmpthetainfo[2];

            bpCatNum = (int) ((thetamax - thetamin) / dtheta) + 1;
        }

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        voxelPath = property.parsePath("voxelPath", null, true, workPath);
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "MU")).map(PartialType::valueOf)
                .collect(Collectors.toSet());
        fpPath = property.parsePath("fpPath", "FPpool", true, workPath);
        if (catalogue) {
            bpPath = property.parsePath("bpPath", "BPcat", true, workPath);
        } else {
            bpPath = property.parsePath("bpPath", "BPpool", true, workPath);
        }

        modelName = property.parseString("modelName", "PREM");  //TODO: use the same system as SPC_SAC ?

        mode = property.parseString("mode", "SH").toUpperCase();
        if ((mode.equals("SH") || mode.equals("PSV") || mode.equals("BOTH")) == false)
                throw new RuntimeException("Error: mode should be one of the following: SH, PSV, BOTH");
        System.err.println("Using mode " + mode);

        if (catalogue)
            readBpCatNames();

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

        if (partialTypes.contains(PartialType.TIME_RECEIVER) || partialTypes.contains(PartialType.TIME_SOURCE)) {
            timePartialPath = property.parsePath("timePartialPath", null, true, workPath);
        }
        if (property.containsKey("qinf")) {
            structure = PolynomialStructureFile.read(property.parsePath("qinf", null, true, workPath));
        }
    }

    private void readBpCatNames() throws IOException {
        if (mode.equals("SH"))
            bpNames = SpcFileAid.collectOrderedSHSpcFileName(bpPath.resolve(modelName)).toArray(new FormattedSPCFileName[0]);
        else if (mode.equals("PSV"))
            bpNames = SpcFileAid.collectOrderedPSVSpcFileName(bpPath.resolve(modelName)).toArray(new FormattedSPCFileName[0]);
        else if (mode.equals("BOTH"))
            bpNames = SpcFileAid.collectOrderedSHSpcFileName(bpPath.resolve(modelName)).toArray(new FormattedSPCFileName[0]);
            bpNames_PSV = SpcFileAid.collectOrderedPSVSpcFileName(bpPath.resolve(modelName)).toArray(new FormattedSPCFileName[0]);
    }

    @Override
    public void run() throws IOException {
        setOutput();
        final int N_THREADS = Runtime.getRuntime().availableProcessors();
//		final int N_THREADS = 1;
        writeLog("Running " + N_THREADS + " threads");
        writeLog("CMTcatalogue: " + GlobalCMTCatalog.getCatalogPath().toString());
        collectTimewindowInformation();
        // design bandpass filter
        setBandPassFilter();
        // read a file for perturbation points.
        readPerturbationPoints();

        // バンドパスを安定させるためwindowを左右に ext = max period(s) ずつ伸ばす
        ext = (int) (1 / minFreq * partialSamplingHz);

        // sacdataを何ポイントおきに取り出すか
        step = (int) (partialSamplingHz / finalSamplingHz);
        setPartialFile();
        int fpnum = 0;

        // set source time functions
        if (sourceTimeFunctionCatalogPath != null) {
            writeLog("STFcatalogue: " + sourceTimeFunctionCatalogPath);
        }
        if (userSourceTimeFunctionPath != null) {
            writeLog("User STF: " + userSourceTimeFunctionPath);
        } else {
            writeLog("SourceTimeFunctionType: " + sourceTimeFunctionType);
        }
        SourceTimeFunctionHandler stfHandler = new SourceTimeFunctionHandler(sourceTimeFunctionType,
                sourceTimeFunctionCatalogPath, userSourceTimeFunctionPath, eventSet);
        sourceTimeFunctions = stfHandler.createSourceTimeFunctionMap(np, tlen, partialSamplingHz);

        // time partials for each event
        if (timePartialPath != null) {
            computeTimePartial(N_THREADS);
        }

        int num = 0;
        for (GlobalCMTID event : eventSet) {
            System.err.println("Working for " + event.toPaddedString() + " " + ++num + "/" + eventSet.size());

         // Set of observers for the components and events in the timewindow.
            Set<Observer> observerSet = readObserver(event);
            if (observerSet.isEmpty())
                continue;

            Path fpEventPath = fpPath.resolve(event.toString());
            Path fpModelPath = fpEventPath.resolve(modelName);
            // spectorfile in fpModelFolder
            List<SPCFileName> fpNames = null;
            List<SPCFileName> fpNames_PSV = null;

            for (PartialType type : partialTypes) {

                if (type.isTimePartial())
                    continue;
                else if (type.isDensity()) {
                    if (mode.equals("SH"))
                        fpNames = SpcFileAid.collectOrderedUFUBSHSpcFileName(fpModelPath);
                    else if (mode.equals("PSV"))
                        fpNames = SpcFileAid.collectOrderedUFUBPSVSpcFileName(fpModelPath);
                    else if (mode.equals("BOTH")) {
                        fpNames = SpcFileAid.collectOrderedUFUBSHSpcFileName(fpModelPath);
                        fpNames_PSV = SpcFileAid.collectOrderedUFUBPSVSpcFileName(fpModelPath);
                    }
                } else {
                    if (mode.equals("SH"))
                        fpNames = SpcFileAid.collectOrderedSHSpcFileName(fpModelPath);
                    else if (mode.equals("PSV"))
                        fpNames = SpcFileAid.collectOrderedPSVSpcFileName(fpModelPath);
                    else if (mode.equals("BOTH")) {
                        fpNames = SpcFileAid.collectOrderedSHSpcFileName(fpModelPath);
                        fpNames_PSV = SpcFileAid.collectOrderedPSVSpcFileName(fpModelPath);
                    }
                }
                writeLog(fpNames.size() + " fpfiles are found");

                for (Observer observer : observerSet) {
//                    System.err.println("Working for " + event + " " + observer + " " + observer.getPosition());

                    // create ThreadPool for each bpFiles in bpFolder
                    ExecutorService execs = Executors.newFixedThreadPool(N_THREADS);
                    for (int i = 0; i < fpNames.size(); i++) {
                        PartialComputation pc = null;
                        if (mode.equals("BOTH"))
                            pc = new PartialComputation(fpNames.get(i), fpNames_PSV.get(i), observer, event, type);
                        else
                            pc = new PartialComputation(fpNames.get(i), observer, event, type);

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
                    partialDataWriter.flush();
                    //System.err.println();
                    writeLog(touchedSet.size() + " events are processed");
                    writeLog(fpnum++ + "th " + fpEventPath + " for " + event + " was done ");
                } // end observer loop
            } // end partila type loop
        } // end event loop
        terminate();
    }

    /**
     * Reads timewindow information include observer and GCMTid
     *
     * @throws IOException if any
     */
    private void collectTimewindowInformation() throws IOException {
        // タイムウインドウの情報を読み取る。
        System.err.println("Reading timewindow information");
        if (dataEntryPath != null) {
            // read entry set to be used for selection
            Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

            // read timewindows and select based on component and entries
            timewindowSet = TimewindowDataFile.read(timewindowPath)
                    .stream().filter(window -> components.contains(window.getComponent()) &&
                            entrySet.contains(new DataEntry(window.getGlobalCMTID(), window.getObserver(), window.getComponent())))
                    .collect(Collectors.toSet());
        } else {
            // read timewindows and select based on component
            timewindowSet = TimewindowDataFile.read(timewindowPath)
                    .stream().filter(window -> components.contains(window.getComponent()))
                    .collect(Collectors.toSet());
        }
        eventSet = new HashSet<>();
        observerSet = new HashSet<>();
        timewindowSet.forEach(t -> {
            eventSet.add(t.getGlobalCMTID());
            observerSet.add(t.getObserver());
        });
        phases = timewindowSet.parallelStream().map(TimewindowData::getPhases).flatMap(p -> Stream.of(p))
                .distinct().toArray(Phase[]::new);

        boolean fpExistence = eventSet.stream().allMatch(id -> Files.exists(fpPath.resolve(id.toString())));
        boolean bpExistence = observerSet.stream().allMatch(observer -> Files.exists(bpPath.resolve(observer.getPosition().toCode())));
        if (!fpExistence) {
            eventSet.stream().filter(id -> !Files.exists(fpPath.resolve(id.toString())))
                .forEach(id -> System.err.println(id));
            throw new RuntimeException("propagation spectors are not enough for " + timewindowPath);
        }
        if (!catalogue && !bpExistence) {
            observerSet.stream().filter(observer -> !Files.exists(bpPath.resolve(observer.getPosition().toCode())))
                .forEach(observer -> System.err.println(observer));
            throw new RuntimeException("propagation spectors are not enough for " + timewindowPath);
        }
        writeLog(timewindowSet.size() + " timewindows are found in " + timewindowPath + ". " + eventSet.size()
                + " events and " + observerSet.size() + " stations.");
    }

    /**
     * 一つのBackPropagationに対して、あるFPを与えた時の計算をさせるスレッドを作る
     *
     * @author Kensuke
     *
     */
    private class PartialComputation implements Runnable {

        private SPCFileName fpName;
        private SPCFileName fpName_other;
        private SPCFileAccess fp;
        private SPCFileAccess fp_other;
        private SPCFileAccess bp;
        private SPCFileAccess bp_other;

        /**
         * use only for catalogue mode
         */
        private SPCFileAccess bp2;
        private SPCFileAccess bp2_other;
        private SPCFileAccess bp3;
        private SPCFileAccess bp3_other;

        private Observer observer;
        private GlobalCMTID event;
        private HorizontalPosition voxelPosition;
        private String mode;
        private PartialType type;

        /**
         * Coefficients for interpolation
         */
        private double[] dhBP = new double[3];

        /**
         * @param fp
         * @param bpFile
         */
        private PartialComputation(SPCFileName fpName, Observer observer, GlobalCMTID event, PartialType type) {
            this.fpName = fpName;
            this.observer = observer;
            this.event = event;
            this.type = type;
            if (fpName.getMode().equals(SPCMode.SH))
                mode = "SH";
            else if (fpName.getMode().equals(SPCMode.PSV))
                mode = "PSV";
            else
                throw new RuntimeException("Invalid mode : " + fpName);
        }

        private PartialComputation(SPCFileName fpName_SH, SPCFileName fpName_PSV, Observer observer, GlobalCMTID event, PartialType type) {
            fpName = fpName_SH;
            fpName_other = fpName_PSV;
            this.observer = observer;
            this.event = event;
            this.type = type;
            mode = "BOTH";
        }

        /**
         * check two spc files are for same voxel
         *
         * @param spc1
         * @param spc2
         * @return
         */
        private boolean checkPair(SPCFileAccess spc1, SPCFileAccess spc2) {
            boolean res = true;
            if (!spc1.getObserverPosition().equals(spc2.getObserverPosition()))
                res = false;
            return res;
        }

        /**
         * Cut partial derivative in [start-ext, start+ext]. The ext is for filtering.
         *
         * @param u
         * @param property
         * @return
         */
        private Complex[] cutPartial(double[] u, TimewindowData timewindowInformation) {
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz) - ext;
            int cutend = (int) (timewindowInformation.getEndTime() * partialSamplingHz) + ext;
            Complex[] cut = new Complex[cutend - cutstart];
            // if cutstart < 0 (i.e. before event time), zero-pad the beginning part
            Arrays.parallelSetAll(cut, i -> (i + cutstart < 0 ? new Complex(0) : new Complex(u[i + cutstart])));

            return cut;
        }

        private Complex[] cutPartial(double[] u, TimewindowData timewindowInformation, double shift) {
            int cutstart = (int) ((timewindowInformation.getStartTime() - shift) * partialSamplingHz) - ext;
            // cutstartが振り切れた場合0 からにする
            if (cutstart < 0)
                return null;
            int cutend = (int) ((timewindowInformation.getEndTime() - shift) * partialSamplingHz) + ext;
            Complex[] cut = new Complex[cutend - cutstart];
            Arrays.parallelSetAll(cut, i -> new Complex(u[i + cutstart]));

            return cut;
        }

        private double[] sampleOutput(Complex[] u, TimewindowData timewindowInformation) {
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * finalSamplingHz);
            double[] sampleU = new double[outnpts];

            // cutting a waveform for outputting
            Arrays.parallelSetAll(sampleU, j -> u[ext + j * step].getReal());
            return sampleU;
        }

        private SourceTimeFunction getSourceTimeFunction() {
            return sourceTimeFunctions.get(event);
        }

        @Override
        public void run() {
            // Count the number of events
            touchedSet.add(event);

            // System.out.println("I am " + Thread.currentThread().getName());
            // Read fp file
            try {
                fp = fpName.read();
                if (mode.equals("BOTH")) {
                    fp_other = fpName_other.read();
                    checkPair(fp, fp_other);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            if (fp.tlen() != tlen || fp.np() != np)
                throw new RuntimeException("FP for " + event + " has invalid tlen or np: " + tlen + " " + fp.tlen() + " " + np + " " + fp.np());

            // Read bp correspond to fp and observer
            try {
                if (catalogue) {
                    getBpCat();
                } else {
                    getBp();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // Pickup timewindows
            Set<TimewindowData> timewindowList = timewindowSet.stream()
                    .filter(info -> info.getObserver().equals(observer))
                    .filter(info -> info.getGlobalCMTID().equals(event)).collect(Collectors.toSet());
            if (timewindowList.isEmpty())
                return;

//          System.err.println(id + " " + timewindowList.size() + " " + observerSourceCode);

            if (!checkPair(bp, fp))
                throw new RuntimeException("BP and FP files are not a pair" + bp + " " + fp);
            if (mode.equals("BOTH")) {
                if (!checkPair(bp_other, fp_other))
                    throw new RuntimeException("BP and FP files are not a pair" + bp_other + " " + fp_other);
            }
            if (catalogue) {
                if (!checkPair(bp2, fp))
                    throw new RuntimeException("BP and FP files are not a pair" + bp2 + " " + fp);
                if (!checkPair(bp3, fp))
                    throw new RuntimeException("BP and FP files are not a pair" + bp3 + " " + fp);
                if (mode.equals("BOTH")) {
                    if (!checkPair(bp2_other, fp_other))
                        throw new RuntimeException("BP and FP files are not a pair" + bp2_other + " " + fp_other);
                    if (!checkPair(bp3_other, fp_other))
                        throw new RuntimeException("BP and FP files are not a pair" + bp3_other + " " + fp_other);
                }
            }

            ThreeDPartialMaker threedPartialMaker = null;
            if (catalogue) {
                if (mode.equals("BOTH")) {
                    threedPartialMaker = new ThreeDPartialMaker(fp, fp_other, bp, bp_other, bp2, bp2_other, bp3, bp3_other, dhBP);
                } else {
                    threedPartialMaker = new ThreeDPartialMaker(fp, bp, bp2, bp3, dhBP);
                }
            } else {
                if (mode.equals("BOTH")) {
                    threedPartialMaker = new ThreeDPartialMaker(fp, fp_other, bp, bp_other);
                } else {
                    threedPartialMaker = new ThreeDPartialMaker(fp, bp);
                }
            }

            threedPartialMaker.setSourceTimeFunction(getSourceTimeFunction());

            if (structure != null)
                threedPartialMaker.setStructure(structure);

            // i番目の深さの偏微分波形を作る
            for (int ibody = 0, nbody = bp.nbody(); ibody < nbody; ibody++) {
                FullPosition location = bp.getObserverPosition().toFullPosition(bp.getBodyR()[ibody]);
//				System.err.println(location);

                if (!voxelPositionSet.contains(location))
                    continue;
                    for (SACComponent component : components) {
                        if (timewindowList.stream().noneMatch(info -> info.getComponent() == component))
                            continue;
//						System.err.println(bp.getBodyR()[ibody] + " " + fpname);
                        double[] partial = threedPartialMaker.createPartialSerial(component, ibody, type);
//						System.err.println(component + " " + type + " " + new ArrayRealVector(partial).getLInfNorm());

                        timewindowList.stream().filter(info -> info.getComponent() == component).forEach(info -> {
//							System.err.println(component + " " + info.getComponent());
                            Complex[] u;
                            u = cutPartial(partial, info);

                            u = filter.applyFilter(u);
                            double[] cutU = sampleOutput(u, info);

                            PartialID pid = new PartialID(observer, event, component, finalSamplingHz, info.getStartTime(),
                                    cutU.length, 1 / maxFreq, 1 / minFreq, info.getPhases(), 0,
                                    sourceTimeFunctionType != SourceTimeFunctionType.NONE, location, type, cutU);

                            try {
                                partialDataWriter.addPartialID(pid);
                                //System.err.print(".");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
            }
        }

        /**
         * get bp files correspond to the fp files.
         * @throws IOException
         */
        private void getBp() throws IOException {
             Path bpModelPath = bpPath.resolve(observer.getPosition().toCode() + "/" + modelName);

             String observerID = bpModelPath.getParent().getFileName().toString();
             String pointName = fp.getObserverID();

             SPCFileName bpName;
             SPCFileName bpName_PSV = null;
             if (type.isDensity()) {
                 bpName = new FormattedSPCFileName(bpModelPath.resolve(pointName + "." + observerID + ".UB..." + fpName.getMode() + ".spc"));
                 if (mode.equals("BOTH"))
                     bpName_PSV = new FormattedSPCFileName(bpModelPath.resolve(pointName + "." + observerID + ".UB...PSV.spc"));
             } else {
                 bpName = new FormattedSPCFileName(bpModelPath.resolve(pointName + "." + observerID + ".PB..." + fpName.getMode() + ".spc"));
                 if (mode.equals("BOTH"))
                     bpName_PSV = new FormattedSPCFileName(bpModelPath.resolve(pointName + "." + observerID + ".PB...PSV.spc"));
             }
             if (!bpName.exists())
                 throw new RuntimeException("Bp file not found " + bpName);
             if (mode.equals("BOTH") && !bpName_PSV.exists())
                 throw new RuntimeException("Bp file not found " + bpName_PSV);
                 bp = bpName.read();
                 bp_other = mode.equals("BOTH") ?  bpName_PSV.read() : null;

             if (!observer.getPosition().toFullPosition(0).equals(bp.getSourcePosition()))
                 throw new RuntimeException("There may be a station with the same name but other networks.");
         }

        /**
         * Read bp catalogue files correspond to fp files.
         * For iterpolation, get 3 bp files.
         * @throws IOException
         */
        private void getBpCat() throws IOException {
            Path bpModelPath = bpPath.resolve(modelName);
            HorizontalPosition voxelPos = fp.getObserverPosition();
            String voxelName = fp.getObserverID();
            FullPosition observerPos = observer.getPosition().toFullPosition(Earth.EARTH_RADIUS);
            double distanceBP = observerPos.computeEpicentralDistance(voxelPos) * 180. / Math.PI;
            double phiBP = Math.PI - observerPos.computeAzimuth(voxelPos);
            if (Double.isNaN(phiBP))
                throw new RuntimeException("PhiBP is NaN " + fpName + " " + observer);

            // Gain index of epicentral distance catalogue
            int ipointBP = (int) ((distanceBP - thetamin) / dtheta);
            if (ipointBP < 0) {
                System.err.println("Warning: BP distance = " + distanceBP + " is smaller than thetamin: " + fpName);
                ipointBP = 0;
            } else if (ipointBP > bpCatNum - 3) {
                System.err.println("Warning: BP distance = " + distanceBP + " is greater than thetamax: " + fpName);
                ipointBP = bpCatNum - 3;
            }

            // Compute coefficients for interpolation
            double theta1 = thetamin + ipointBP * dtheta;
            double theta2 = theta1 + dtheta;
            double theta3 = theta2 + dtheta;
            dhBP[0] = (distanceBP - theta1) / dtheta;
            dhBP[1] = (distanceBP - theta2) / dtheta;
            dhBP[2] = (distanceBP - theta3) / dtheta;

            bp = SPCFile.getInstance(bpNames[ipointBP], phiBP, voxelPos, observerPos, voxelName);
            bp2 = SPCFile.getInstance(bpNames[ipointBP + 1], phiBP, voxelPos, observerPos, voxelName);
            bp3 = SPCFile.getInstance(bpNames[ipointBP + 2], phiBP, voxelPos, observerPos, voxelName);
            if (mode.equals("BOTH")) {
                bp_other = SPCFile.getInstance(bpNames_PSV[ipointBP], phiBP, voxelPos, observerPos, voxelName);
                bp2_other = SPCFile.getInstance(bpNames_PSV[ipointBP + 1], phiBP, voxelPos, observerPos, voxelName);
                bp3_other = SPCFile.getInstance(bpNames_PSV[ipointBP + 2], phiBP, voxelPos, observerPos, voxelName);
            }
        }
    }


    private void setOutput() throws IOException {
        synchronized (PartialWaveformAssembler3D.class) {
            do {
                dateString = GadgetAid.getTemporaryString();
                outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, dateString);
                logPath = outPath.resolve("assembler" + dateString + ".log");
            } while (Files.exists(logPath));
            Files.createFile(logPath);
        }
    }

    private void setPartialFile() throws IOException {

        // 書き込み準備
        Path idPath = outPath.resolve("partialID.dat");
        Path datasetPath = outPath.resolve("partial.dat");

        partialDataWriter = new WaveformDataWriter(idPath, datasetPath, observerSet, eventSet, periodRanges,
                phases, voxelPositionSet);
        writeLog("Creating " + idPath + " " + datasetPath);
        System.err.println("Creating " + idPath + " " + datasetPath);

    }


    private void setBandPassFilter() throws IOException {
        System.err.println("Designing filter.");
        double omegaH = maxFreq * 2 * Math.PI / partialSamplingHz;
        double omegaL = minFreq * 2 * Math.PI / partialSamplingHz;
        filter = new BandPassFilter(omegaH, omegaL, filterNp);
        filter.setCausal(causal);
        writeLog(filter.toString());
        periodRanges = new double[][] { { 1 / maxFreq, 1 / minFreq } };
    }

    private void readPerturbationPoints() throws IOException {
        System.err.println("Reading perutbation points");
        voxelPositionSet = new VoxelInformationFile(voxelPath).fullPositionSet();

        if (timePartialPath != null) {
            if (observerSet.isEmpty() || eventSet.isEmpty())
                throw new RuntimeException("stationSet and idSet must be set before perturbationLocation");
            observerSet.forEach(observer -> voxelPositionSet.add(new FullPosition(observer.getPosition().getLatitude(),
                    observer.getPosition().getLongitude(), Earth.EARTH_RADIUS)));
            eventSet.forEach(id -> voxelPositionSet.add(id.getEventData().getCmtPosition()));
        }
        writeLog(voxelPositionSet.size() + " voxel points are found in " + voxelPath);
    }

    private void computeTimePartial(int N_THREADS) throws IOException {
        ExecutorService execs = Executors.newFixedThreadPool(N_THREADS);
        Set<EventFolder> timePartialEventDirs = DatasetAid.eventFolderSet(timePartialPath);
        for (EventFolder eventDir : timePartialEventDirs) {
            execs.execute(new WorkerTimePartial(eventDir));
            System.err.println("Working for time partials for " + eventDir);
        }
        execs.shutdown();
        while (!execs.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        partialDataWriter.flush();
        System.err.println();
    }


    private class WorkerTimePartial implements Runnable {

        private EventFolder eventDir;
        private GlobalCMTID id;

        @Override
        public void run() {
            try {
                writeLog("Running on " + id);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            Path timePartialFolder = eventDir.toPath();

            if (!Files.exists(timePartialFolder)) {
                throw new RuntimeException(timePartialFolder + " does not exist...");
            }

            Set<SACFileName> sacnameSet;
            try {
                sacnameSet = eventDir.sacFileSet()
                        .stream()
                        .filter(sacname -> sacname.isTemporalPartial())
                        .collect(Collectors.toSet());
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }

//          System.out.println(sacnameSet.size());
//          sacnameSet.forEach(name -> System.out.println(name));

            Set<TimewindowData> timewindowCurrentEvent = timewindowSet
                    .stream()
                    .filter(tw -> tw.getGlobalCMTID().equals(id))
                    .collect(Collectors.toSet());

            // すべてのsacファイルに対しての処理
            for (SACFileName sacname : sacnameSet) {
                try {
                    addTemporalPartial(sacname, timewindowCurrentEvent);
                } catch (ClassCastException e) {
                    // 出来上がったインスタンスがOneDPartialSpectrumじゃない可能性
                    System.err.println(sacname + "is not 1D partial.");
                    continue;
                } catch (Exception e) {
                    System.err.println(sacname + " is invalid.");
                    e.printStackTrace();
                    try {
                        writeLog(sacname + " is invalid.");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
            }
            System.err.print(".");
//
        }

        private void addTemporalPartial(SACFileName sacname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
            Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
                    .filter(info -> info.getObserver().toString().equals(sacname.getObserverID())) //TODO this may not get unique observer
                    .collect(Collectors.toSet());
            if (tmpTws.size() == 0) {
                return;
            }

            System.err.println(sacname + " (time partials)");

            SACFileAccess sacdata = sacname.read();
            Observer station = sacdata.getObserver();

            for (SACComponent component : components) {
                Set<TimewindowData> tw = tmpTws.stream()
                        .filter(info -> info.getObserver().equals(station))
                        .filter(info -> info.getGlobalCMTID().equals(id))
                        .filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

                if (tw.isEmpty()) {
                    tmpTws.forEach(window -> {
                        System.err.println(window);
                        System.err.println(window.getObserver().getPosition());
                    });
                    System.err.println(station.getPosition());
                    System.err.println("Ignoring empty timewindow " + sacname + " " + station);
                    continue;
                }

                for (TimewindowData t : tw) {
                    double[] filteredUt = sacdata.createTrace().getY();
                    cutAndWrite(station, filteredUt, t);
                }
            }
        }
        /**
         * @param u
         *            partial waveform
         * @param timewindowInformation
         *            cut information
         * @return u cut by considering sampling Hz
         */
        private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * finalSamplingHz);
            double[] sampleU = new double[outnpts];
            // cutting a waveform for outputting
            Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

            return sampleU;
        }

        private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t) {

            double[] cutU = sampleOutput(filteredUt, t);
            FullPosition stationLocation = new FullPosition(station.getPosition().getLatitude(), station.getPosition().getLongitude(), Earth.EARTH_RADIUS);

            if (userSourceTimeFunctionPath != null)
                System.err.println("Warning: check that the source time function used for the time partial is the same as the one used here.");

            PartialID PIDReceiverSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), 0, true, stationLocation, PartialType.TIME_RECEIVER,
                    cutU);
            PartialID PIDSourceSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), 0, true, id.getEventData().getCmtPosition(), PartialType.TIME_SOURCE,
                    cutU);

            try {
                if (partialTypes.contains(PartialType.TIME_RECEIVER))
                    partialDataWriter.addPartialID(PIDReceiverSide);
                if (partialTypes.contains(PartialType.TIME_SOURCE))
                    partialDataWriter.addPartialID(PIDSourceSide);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private WorkerTimePartial(EventFolder eventDir) {
            this.eventDir = eventDir;
            id = eventDir.getGlobalCMTID();
        };
    }

    /*
     * sort timewindows comparing stations
     */
    private List<TimewindowData> sortTimewindows() {
        List<TimewindowData> timewindows = timewindowSet.stream().collect(Collectors.toList());

        Comparator<TimewindowData> comparator = new Comparator<TimewindowData>() {
            @Override
            public int compare(TimewindowData o1, TimewindowData o2) {
                int res = o1.getObserver().compareTo(o2.getObserver());
                if (res != 0)
                    return res;
                else {
                    return o1.getGlobalCMTID().compareTo(o2.getGlobalCMTID());
                }
            }
        };
        timewindows.sort(comparator);

        return timewindows;
    }

    private List<Path[]> collectFP_jointCMT(Set<GlobalCMTID> idSet) {
        List<Path[]> paths = new ArrayList<>();

        for (GlobalCMTID id : idSet) {
            Path[] tmpPaths = new Path[6];
            for (int i = 0; i < 6; i++)
                tmpPaths[i] = fpPath.resolve(id + "_mt" + i + "/" + modelName);
            paths.add(tmpPaths);
        }

        return paths;
    }

    /**
     * Get observers correspond to a event by reading timewindow information file.
     * @param event
     * @return observerSet
     * @throws IOException
     */
    private Set<Observer> readObserver(GlobalCMTID event) throws IOException {
        Set<Observer> observerSet = timewindowSet.stream()
         .filter(info -> components.contains(info.getComponent()))
         .filter(info -> info.getGlobalCMTID().equals(event)).map(TimewindowData::getObserver)
         .collect(Collectors.toSet());

        if (observerSet.size() != observerSet.stream().map(Observer::toString).distinct().count()) {
            System.err.println("CAUTION!! Stations with same name and network but different positions detected!");
            Map<String, List<Observer>> nameToObserver = new HashMap<>();
            observerSet.forEach(obs -> {
                if (nameToObserver.containsKey(obs.toString())) {
                    List<Observer> tmp = nameToObserver.get(obs.toString());
                    tmp.add(obs);
                    nameToObserver.put(obs.toString(), tmp);
                }
                else {
                    List<Observer> tmp = new ArrayList<>();
                    tmp.add(obs);
                    nameToObserver.put(obs.toString(), tmp);
                }
            });
            nameToObserver.forEach((name, sta) -> {
                if (sta.size() > 1) {
                    sta.stream().forEach(s -> System.out.println(s));
                }
            });
        }

        writeLog(observerSet.size() + " stations are found in " + event + " .");
        return observerSet;

    }

    private void terminate() throws IOException {
        partialDataWriter.close();
        writeLog(partialDataWriter.getIDPath() + " " + partialDataWriter.getDataPath() + " were created");
    }

    private synchronized void writeLog(String line) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            pw.print(new Date() + " : ");
            pw.println(line);
        }
    }

}
