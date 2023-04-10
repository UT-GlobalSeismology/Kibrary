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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

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
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.DefaultStructure;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.FujiConversion;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.util.spc.SACMaker;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCType;
import io.github.kensuke1984.kibrary.util.spc.VSConversion;

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
 * <b>Assume there are no station with the same name but different networks in
 * same events</b> TODO
 *
 *
 * @author Kensuke Konishi
 * @since version 0.2.0.3
 * @version 2021/12/24 renamed from Partial1DDatasetMaker to PartialWaveformAssembler1D
 */
public class PartialWaveformAssembler1D extends Operation {

    private final double eps = 1e-6;

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
     * Timewindows to work for.
     */
    private Set<TimewindowData> timewindowSet;
    private Map<GlobalCMTID, SourceTimeFunction> sourceTimeFunctions;
    private ButterworthFilter filter;
    /**
     * sacdataを何ポイントおきに取り出すか
     */
    private int step;

    /**
     * Created {@link PartialID}s.
     */
    private List<PartialID> partialIDs = Collections.synchronizedList(new ArrayList<>());






    private Path logPath;

    private FujiConversion fujiConversion;

    private VSConversion vsConversion;

    private Path timePartialPath;

    private String structurePath;

    private int lsmooth;


    private boolean par00; //TODO what is PAR00 ?


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

    public static void writeDefaultPropertiesFile_old() throws IOException {
        Path outPath = Paths
                .get(PartialWaveformAssembler1D.class.getName() + GadgetAid.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("##path of the time partials directory, must be set if PartialType containes TIME_SOURCE or TIME_RECEIVER");
            pw.println("#timePartialPath");
            pw.println("##Polynomial structure file (leave blank if PREM)");
            pw.println("#ps");
        }
        System.err.println(outPath + " is created.");
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




        String dateString = GadgetAid.getTemporaryString();

        logPath = workPath.resolve("partial1D" + dateString + ".log");

        // pdm.createStreams();
        int N_THREADS = Runtime.getRuntime().availableProcessors();
        // N_THREADS = 2;
        writeLog("going with " + N_THREADS + " threads");

        PolynomialStructure structure = null;
        switch (structurePath) {
        case "PREM":
        case "prem":
            structure = DefaultStructure.PREM;
            break;
        case "AK135":
        case "ak135":
            structure = DefaultStructure.AK135;
            break;
        default:
            try {
                structure = PolynomialStructureFile.read(Paths.get(structurePath));
            } catch (IOException e) {
                e.printStackTrace();
            }
            break;
        }

        if (partialTypes.contains(PartialType.PARQ))
            fujiConversion = new FujiConversion(structure);

        if (partialTypes.contains(PartialType.PARVS))
            vsConversion = new VSConversion(structure);

        System.err.println("going with the structure " + structurePath);
        writeLog("going with the structure " + structurePath);

        setLsmooth();
        writeLog("Set lsmooth " + lsmooth);



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

        // sacdataを何ポイントおきに取り出すか
        step = (int) (partialSamplingHz / finalSamplingHz);

        // create output folder
        outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(event -> new EventFolder(shPath.resolve(event.toString()))).map(Worker::new).forEach(es::execute);
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


    private class Worker2 implements Runnable {

        private final GlobalCMTID event;
        private final Path shModelPath;
        private final Path psvModelPath;

        private Worker2(GlobalCMTID event) {
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
                        addPartialSpectrum(observer, partialType);
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

        private void addPartialSpectrum(Observer observer, PartialType partialType) throws IOException {
            Path shSPCPath = shModelPath.resolve(observer + "." + event + "." + partialType + "...SH.spc");
            if (!SPCFileName.isFormatted(shSPCPath)) {
                System.err.println(shSPCPath + " has invalid SPC file name.");
                return;
            }
            SPCFileName shSPCName = new FormattedSPCFileName(shSPCPath);
            SPCFileAccess shSPCFile = shSPCName.read();
            if (shSPCFile.tlen() != tlen || shSPCFile.np() != np) {
                System.err.println(shSPCFile + " has different np or tlen.");
                return;
            }
            process(shSPCFile);

            Path psvSPCPath = psvModelPath.resolve(observer + "." + event + "." + partialType + "...PSV.spc");
            if (!SPCFileName.isFormatted(psvSPCPath)) {
                System.err.println(psvSPCPath + " has invalid SPC file name.");
                return;
            }
            SPCFileName psvSPCName = new FormattedSPCFileName(psvSPCPath);
            SPCFileAccess psvSPCFile = psvSPCName.read();
            if (psvSPCFile.tlen() != tlen || psvSPCFile.np() != np) {
                System.err.println(psvSPCFile + " has different np or tlen.");
                return;
            }
            process(psvSPCFile);

            Set<TimewindowData> correspondingTimewindows = timewindowSet.stream()
                    .filter(timewindow -> timewindow.getGlobalCMTID().equals(event) && timewindow.getObserver().equals(observer))
                    .collect(Collectors.toSet());

            for (TimewindowData t : correspondingTimewindows) {
                for (int k = 0; k < shSPCFile.nbody(); k++) { //TODO loop for r:bodyR, so that error can be thrown when data for a radius doesn't exist
                    double currentBodyR = shSPCFile.getBodyR()[k];
                    boolean exists = false;
                    for (double r : bodyR)
                        if (Precision.equals(r, currentBodyR, eps))
                            exists = true;
                    if (!exists)
                        continue;
                    double[] ut = shSPCFile.getSpcBodyList().get(k).getSpcComponent(t.getComponent()).getTimeseries();

                    // applying the filter
                    double[] filteredUt = filter.applyFilter(ut);
                    cutAndWrite(observer, filteredUt, t, currentBodyR, partialType);
                }
            }
        }

        private void process(SPCFileAccess spcFile) {
            for (SACComponent component : components)
                spcFile.getSpcBodyList().stream().map(body -> body.getSpcComponent(component))
                        .forEach(spcComponent -> {
                            spcComponent.applySourceTimeFunction(sourceTimeFunctions.get(event));
                            spcComponent.toTimeDomain(lsmooth);
                            spcComponent.applyGrowingExponential(spcFile.omegai(), tlen);
                            spcComponent.amplitudeCorrection(tlen);
                        });
        }

        private void cutAndWrite(Observer observer, double[] filteredUt, TimewindowData t, double bodyR,
                PartialType partialType) {

            double[] cutU = sampleOutput(filteredUt, t);

            PartialID partialID = new PartialID(observer, event, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), sourceTimeFunctionType != SourceTimeFunctionType.NONE,
                    new FullPosition(0, 0, bodyR), partialType, cutU);
            partialIDs.add(partialID);
        }

        /**
         * @param u
         *            partial waveform
         * @param timewindowInformation
         *            cut information
         * @return u cut by considering sampling Hz
         */
        private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {//TODO check
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * finalSamplingHz);
            double[] sampleU = new double[outnpts];
            // cutting a waveform for outputting
            Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

            return sampleU;
        }

    }

    private class Worker implements Runnable {

        private GlobalCMTID event;
        private EventFolder eventDir;

        private Worker(EventFolder eventDir) {
            this.eventDir = eventDir;
            event = eventDir.getGlobalCMTID();
        }

        @Override
        public void run() {
            //OK
            try {
                writeLog("Running on " + event);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            Path spcFolder = eventDir.toPath().resolve(modelName); // SPCの入っているフォルダ

            //OK
            if (!Files.exists(spcFolder)) {
                System.err.println(spcFolder + " does not exist...");
                return;
            }

            //OK
            Set<SPCFileName> spcFileNames;
            try {
                if (usableSPCMode == SpcFileAid.UsableSPCMode.SH) {
                    spcFileNames = collectSHSPCs(spcFolder);
                    System.out.println("Collecting SH spc");
                }
                else if (usableSPCMode == SpcFileAid.UsableSPCMode.PSV) {
                    spcFileNames = collectPSVSPCs(spcFolder);
                    System.out.println("Collecting PSV spc");
                }
                else {
                    spcFileNames = collectPSVSPCs(spcFolder);
                    System.out.println("Collecting PSV and SH spc");
                }
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }

            //OK
            Set<TimewindowData> timewindowCurrentEvent = timewindowSet.stream()
                .filter(tw -> tw.getGlobalCMTID().equals(event))
                .collect(Collectors.toSet());

            // すべてのspcファイルに対しての処理
            for (SPCFileName spcFileName : spcFileNames) {

                //OK
                // 理論波形（非偏微分係数波形）ならスキップ
                if (spcFileName.isSynthetic())
                    continue;

                //OK
                if (!spcFileName.getSourceID().equals(event.toString())) {
                    try {
                        writeLog(spcFileName + " has an invalid global CMT ID.");
                        continue;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                //OK
                SPCType spcFileType = spcFileName.getFileType();

                //OK
                // 3次元用のスペクトルなら省く
                if (spcFileType == SPCType.PF || spcFileType == SPCType.PB)
                    continue;

                //OK
                // check if the partialtype is included in computing list.
                PartialType partialType = PartialType.valueOf(spcFileType.toString());

                //OK
                if (!(partialTypes.contains(partialType)
                        || (partialTypes.contains(PartialType.PARQ) && spcFileType == SPCType.PAR2)))
                    continue;

                //OK
                SPCFileName shspcname = null;
                if (usableSPCMode == SpcFileAid.UsableSPCMode.BOTH) {
                    if (spcFileType.equals(SPCType.PARN)
                    || spcFileType.equals(SPCType.PARL)
                    || spcFileType.equals(SPCType.PAR0)
                    || spcFileType.equals(SPCType.PAR2)) {
                        String shname = shPath.resolve(spcFileName.getSourceID() + "/" + modelName + "/"
                            + spcFileName.getName().replace("PSV.spc", "SH.spc")).toFile().toString();
                        shspcname = new FormattedSPCFileName(shname);
//						shspcname = new SpcFileName(spcFileName.getPath().replace("PSV.spc", "SH.spc"));
                    }
                }

                try {
                    if (shspcname == null)
                        addPartialSpectrum(spcFileName, timewindowCurrentEvent);
                    else {
                        if(shspcname.exists())
                            addPartialSpectrum(spcFileName, shspcname, timewindowCurrentEvent);
                        else {
                            System.out.println("SH spc file not found " + shspcname);
                            addPartialSpectrum(spcFileName, timewindowCurrentEvent);
                        }
                    }
                } catch (ClassCastException e) {
                    // 出来上がったインスタンスがOneDPartialSpectrumじゃない可能性
                    System.err.println(spcFileName + "is not 1D partial.");
                    continue;
                } catch (Exception e) {
                    System.err.println(spcFileName + " is invalid.");
                    e.printStackTrace();
                    try {
                        writeLog(spcFileName + " is invalid.");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
            }
            System.out.print(".");

        }

        private void process(SPCFileAccess spectrum) {
            for (SACComponent component : components)
                spectrum.getSpcBodyList().stream().map(body -> body.getSpcComponent(component))
                        .forEach(spcComponent -> {
                            spcComponent.applySourceTimeFunction(sourceTimeFunctions.get(event));
                            spcComponent.toTimeDomain(lsmooth);
                            spcComponent.applyGrowingExponential(spectrum.omegai(), tlen);
                            spcComponent.amplitudeCorrection(tlen);
                        });
        }

        private void addPartialSpectrum(SPCFileName spcname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
            //OK
            Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
                    .filter(info -> info.getObserver().getStation().equals(spcname.getStationCode())
                            && info.getObserver().getNetwork().equals(spcname.getNetworkCode()))
                    .collect(Collectors.toSet());
            if (tmpTws.size() == 0) {
//				System.err.println(spcname + " " + "No timewindow found");
//				writeLog(spcname + " " + "No timewindow found");
                return;
            }

            //OK
            System.out.println(spcname);
            SPCFileAccess spectrum = spcname.read();
            if (spectrum.tlen() != tlen || spectrum.np() != np) {
                System.err.println(spcname + " has different np or tlen.");
                writeLog(spcname + " has different np or tlen.");
                return;
            }

            //OK
            String stationName = spcname.getStationCode();
            String network = spcname.getNetworkCode();
            Observer station = new Observer(stationName, network, spectrum.getObserverPosition());
            PartialType partialType = PartialType.valueOf(spcname.getFileType().toString());

            //OK
            SPCFileAccess qSpectrum = null;
            if (spcname.getFileType() == SPCType.PAR2 && partialTypes.contains(PartialType.PARQ)) {
                qSpectrum = fujiConversion.convert(spectrum);
                process(qSpectrum);
            }
            process(spectrum);

            for (SACComponent component : components) {

                //OK
                Set<TimewindowData> tw = tmpTws.stream()
                        .filter(info -> info.getObserver().equals(station))
                        .filter(info -> info.getGlobalCMTID().equals(event))
                        .filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

                //OK
                if (tw.isEmpty()) {
                    tmpTws.forEach(window -> {
                        System.out.println(window);
                        System.out.println(window.getObserver().getPosition());
                    });
                    System.err.println(station.getPosition());
                    System.err.println("Ignoring empty timewindow " + spcname + " " + station);
                    continue;
                }

                //OK
                for (int k = 0; k < spectrum.nbody(); k++) {
                    double bodyR = spectrum.getBodyR()[k];
                    boolean exists = false;
                    for (double r : PartialWaveformAssembler1D.this.bodyR)
                        if (Precision.equals(r, bodyR, eps))
                            exists = true;
                    if (!exists)
                        continue;
                    double[] ut = spectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();

                    // applying the filter
                    double[] filteredUt = filter.applyFilter(ut);
                    for (TimewindowData t : tw)
                        cutAndWrite(station, filteredUt, t, bodyR, partialType);
                }
                if (qSpectrum != null)
                    for (int k = 0; k < spectrum.nbody(); k++) {
                        double bodyR = spectrum.getBodyR()[k];
                        boolean exists = false;
                        for (double r : PartialWaveformAssembler1D.this.bodyR)
                            if (Precision.equals(r, bodyR, eps))
                                exists = true;
                        if (!exists)
                            continue;
                        double[] ut = qSpectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();

                        // applying the filter
                        double[] filteredUt = filter.applyFilter(ut);
                        for (TimewindowData t : tw)
                            cutAndWrite(station, filteredUt, t, bodyR, PartialType.PARQ);
                    }
            }
        }

        private void addPartialSpectrum(SPCFileName spcname, SPCFileName shspcname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
            //OK
            Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
                    .filter(info -> info.getObserver().getStation().equals(spcname.getStationCode())
                            && info.getObserver().getNetwork().equals(spcname.getNetworkCode()))
                    .collect(Collectors.toSet());
            if (tmpTws.size() == 0) {
//				System.out.println("No timewindow found");
                return;
            }

            //OK
            System.out.println(spcname);
            SPCFileAccess spectrum = spcname.read();
            if (spectrum.tlen() != tlen || spectrum.np() != np) {
                System.err.println(spcname + " has different np or tlen.");
                writeLog(spcname + " has different np or tlen.");
                return;
            }

            //OK
            System.out.println(shspcname);
            SPCFileAccess shspectrum = shspcname.read();
            if (shspectrum.tlen() != tlen || shspectrum.np() != np) {
                System.err.println(shspcname + " has different np or tlen.");
                writeLog(shspcname + " has different np or tlen.");
                return;
            }

            // TODO before: check2
            if (!SACMaker.check(spectrum, shspectrum)) {
                System.err.println("SH and PSV spc files are not a pair " + spectrum + " " + shspectrum);
                return;
            }

            //OK
            String stationName = spcname.getStationCode();
            String network = spcname.getNetworkCode();
            Observer station = new Observer(stationName, network, spectrum.getObserverPosition());
            PartialType partialType = PartialType.valueOf(spcname.getFileType().toString());

            SPCFileAccess qSpectrum = null;
            if (spcname.getFileType() == SPCType.PAR2 && partialTypes.contains(PartialType.PARQ)) {
                qSpectrum = fujiConversion.convert(spectrum);
                process(qSpectrum);
            }

            //OK
            process(spectrum);
            process(shspectrum);

            for (SACComponent component : components) {

                //OK
                Set<TimewindowData> tw = tmpTws.stream()
                        .filter(info -> info.getObserver().equals(station))
                        .filter(info -> info.getGlobalCMTID().equals(event))
                        .filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

                //OK
                if (tw.isEmpty()) {
                    tmpTws.forEach(window -> {
                        System.out.println(window);
                        System.out.println(window.getObserver().getPosition());
                    });
                    System.err.println(station.getPosition());
                    System.err.println("Ignoring empty timewindow " + spcname + " " + station);
                    continue;
                }

//				for (int k = 0; k < spectrum.nbody(); k++) {
//					double bodyR = spectrum.getBodyR()[k];
//					if (shspectrum.getBodyR()[k] != bodyR)
//						throw new RuntimeException("sh and psv bodyR differ " + shspectrum.getBodyR()[k] + " " + bodyR);
//					boolean exists = false;
//					for (double r : Partial1DDatasetMaker_v2.this.bodyR)
//						if (Utilities.equalWithinEpsilon(r, bodyR, eps))
//							exists = true;
//					if (!exists)
//						continue;
                for (int k = 0; k < bodyR.length; k++) {
                    if (shspectrum.getBodyR()[k] != bodyR[k] || shspectrum.getBodyR()[k] != spectrum.getBodyR()[k])
                        throw new RuntimeException("sh and psv bodyR differ " + shspectrum.getBodyR()[k] + " " + bodyR);
                    boolean exists = false;
                    for (double r : bodyR)
                        if (Precision.equals(r, shspectrum.getBodyR()[k], eps))
                            exists = true;
                    if (!exists)
                        continue;
                    double[] ut = spectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
                    double[] shut = shspectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();

                    if (ut.length != shut.length)
                        throw new RuntimeException("sh and psv timeseries do not have the same length " + shut.length + " " + ut.length);

                    // applying the filter
                    double[] filteredUt = filter.applyFilter(ut);
                    double[] filteredSHUt = filter.applyFilter(shut);
                    for (int it = 0; it < filteredUt.length; it++)
                        filteredUt[it] += filteredSHUt[it];
                    for (TimewindowData t : tw)
                        cutAndWrite(station, filteredUt, t, bodyR[k], partialType);
                }

                if (qSpectrum != null) {
                    System.err.println("Q sh and psv spc not implemented yet");
                    for (int k = 0; k < bodyR.length; k++) {
                        boolean exists = false;
                        for (double r : bodyR)
                            if (Precision.equals(r, spectrum.getBodyR()[k], eps))
                                exists = true;
                        if (!exists)
                            continue;
                        double[] ut = qSpectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
                        // applying the filter

                        double[] filteredUt = filter.applyFilter(ut);
                        for (TimewindowData t : tw)
                            cutAndWrite(station, filteredUt, t, bodyR[k], PartialType.PARQ);
                    }
                }
            }
        }

        private void cutAndWrite(Observer observer, double[] filteredUt, TimewindowData t, double bodyR,
                PartialType partialType) {

            double[] cutU = sampleOutput(filteredUt, t);

            PartialID partialID = new PartialID(observer, event, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), sourceTimeFunctionType != SourceTimeFunctionType.NONE,
                    new FullPosition(0, 0, bodyR), partialType, cutU);
            partialIDs.add(partialID);
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

    }

    private void writeLog(String line) throws IOException {
        Date now = new Date();
        synchronized (this) {
            try (PrintWriter pw = new PrintWriter(
                    Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                pw.println(now + " : " + line);
            }
        }
    }

    private Set<SPCFileName> collectSHSPCs(Path spcFolder) throws IOException {
        Set<SPCFileName> shSet = new HashSet<>();
        SpcFileAid.collectSpcFileName(spcFolder).stream()
                .filter(f -> f.getName().contains("PAR") && f.getName().endsWith("SH.spc")).forEach(shSet::add);
        return shSet;
    }

    private Set<SPCFileName> collectPSVSPCs(Path spcFolder) throws IOException {
        Set<SPCFileName> psvSet = new HashSet<>();
        SpcFileAid.collectSpcFileName(spcFolder).stream()
                .filter(f -> f.getName().contains("PAR") && f.getName().endsWith("PSV.spc")).forEach(psvSet::add);
        return psvSet;
    }
}
