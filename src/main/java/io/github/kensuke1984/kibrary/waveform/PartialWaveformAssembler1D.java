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
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionHandler;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.voxel.ParameterType;

/**
 * Operation that assembles partial derivative waveforms for 1-D parameters
 * from SPC files created by sshshi and sshpsvi, or sshsh and sshpsv.
 * Output is written in the format of {@link PartialIDFile}.
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> the (event, observer, component)-pair is included in the input data entry file, if it is specified </li>
 * </ul>
 * <p>
 * SPC files must be inside (shPath,psvPath)/eventDir/modelName/.
 * It is possible to use only SH or only PSV, as well as to use both.
 * Input SPC file names should take the form:<br>
 * observerPositionCode.eventID.partialType...(SH or PSV).spc<br>
 * For information about observerPositionCode, see {@link HorizontalPosition#toCode}.
 * <p>
 * A set of partialTypes and perturbation radii to work for must be specified.
 * This class does NOT handle time partials.
 * <p>
 * Time length (tlen) and the number of steps in frequency domain (np) must be same as the values used when running DSM.
 * <p>
 * Source time functions and filters can be applied to the waveforms.
 * The sample rate of the resulting data is {@link #finalSamplingHz}.
 * <p>
 * Resulting entries can be specified by a (event, observer, component, partialType, perturbationRadius, timeframe)-pair.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2021/12/24 renamed from Partial1DDatasetMaker to PartialWaveformAssembler1D
 */
public class PartialWaveformAssembler1D extends Operation {

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
     * Components to use.
     */
    private Set<SACComponent> components;
    /**
     * Sampling frequency for intermediate computations [Hz].
     */
    private double partialSamplingHz;
    /**
     * Sampling frequency in output files [Hz].
     */
    private double finalSamplingHz;

    /**
     * Path of a timewindow file.
     */
    private Path timewindowPath;
    /**
     * Path of a data entry list file.
     */
    private Path dataEntryPath;
    /**
     * Variable types for compute for.
     */
    private Set<VariableType> variableTypes;
    /**
     * Radii of layers, when selecting to work for certain layers.
     */
    private double[] layerRadii;
    private Path shPath;
    private Path psvPath;
    /**
     * The SPC modes that shall be used, from {SH, PSV, BOTH}.
     */
    private SpcFileAid.UsableSPCMode usableSPCMode;
    /**
     * Name of folder containing SPC files (e.g. PREM).
     */
    private String modelName;

    /**
     * Source time function. {0: none, 1: boxcar, 2: triangle, 3: asymmetric triangle, 4: auto}
     */
    private SourceTimeFunctionType sourceTimeFunctionType;
    /**
     * Folder containing user-defined source time functions.
     */
    private Path userSourceTimeFunctionPath;
    /**
     * Catalog containing source time function durations.
     */
    private Path sourceTimeFunctionCatalogPath;

    /**
     * Time length (DSM parameter).
     */
    private double tlen;
    /**
     * Number of steps in frequency domain (DSM parameter).
     */
    private int np;
    /**
     * Lower frequency of bandpass [Hz].
     */
    private double lowFreq;
    /**
     * Upper frequency of bandpass [Hz].
     */
    private double highFreq;
    /**
     * see Saito, n
     */
    private int filterNp;
    /**
     * Whether to apply causal filter. {true: causal, false: zero-phase}
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
            pw.println("##SacComponents to be used. (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Sampling frequency for computation [Hz], must be (a power of 2)/tlen. (20)");
            pw.println("#partialSamplingHz ");
            pw.println("##(double) Sampling frequency in output files [Hz], must be a factor of partialSamplingHz. (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a timewindow data file, must be set.");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##VariableTypes to compute for at each voxel, listed using spaces. (MU)");
            pw.println("#variableTypes ");
            pw.println("##(double[]) Layer radii, listed using spaces, if you want to select layers to be worked for.");
            pw.println("#layerRadii ");
            pw.println("##Path of an SH folder. (.)");
            pw.println("#shPath ");
            pw.println("##Path of a PSV folder. (.)");
            pw.println("#psvPath ");
            pw.println("##The mode of spc files that have been computed, from {SH, PSV, BOTH}. (BOTH)");
            pw.println("#usableSPCMode ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used. (PREM)");
            pw.println("#modelName ");
            pw.println("##########Computation settings.");
            pw.println("##Path of folder containing source time functions. If not set, the following sourceTimeFunctionType will be used.");
            pw.println("#userSourceTimeFunctionPath ");
            pw.println("##Type of source time function, from {0:none, 1:boxcar, 2:triangle, 3:asymmetricTriangle, 4:auto}. (0)");
            pw.println("##  When 'auto' is selected, the function specified in the GCMT catalog will be used.");
            pw.println("#sourceTimeFunctionType ");
            pw.println("##Path of a catalog to set source time function durations. If unneeded, leave this unset.");
            pw.println("#sourceTimeFunctionCatalogPath ");
            pw.println("##Time length to be computed [s], set in spectrum files. (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, set in spectrum files. (512)");
            pw.println("#np ");
            pw.println("##Lower limit of the frequency band [Hz]. (0.005)");
            pw.println("#lowFreq ");
            pw.println("##Higher limit of the frequency band [Hz]. (0.08)");
            pw.println("#highFreq ");
            pw.println("##(int) The value of NP for the filter. (4)");
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
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialSamplingHz = property.parseDouble("partialSamplingHz", "20");
        finalSamplingHz = property.parseDouble("finalSamplingHz", "1");
        if (!MathAid.isInteger(partialSamplingHz / finalSamplingHz))
            throw new IllegalArgumentException("partialSamplingHz/finalSamplingHz must be integer.");

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        for (VariableType type : variableTypes)
            if (type.equals(VariableType.TIME)) throw new IllegalArgumentException("This class does not handle time partials.");
        if (property.containsKey("layerRadii")) {
            layerRadii = property.parseDoubleArray("layerRadii", null);
        }

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
        lowFreq = property.parseDouble("lowFreq", "0.005");
        highFreq = property.parseDouble("highFreq", "0.08");
        filterNp = property.parseInt("filterNp", "4");
        causal = property.parseBoolean("causal", "false");
    }

    @Override
    public void run() throws IOException {
        System.err.println("Using mode " + usableSPCMode);
        System.err.println("Model name is " + modelName);
        // information about output partial types
        System.err.println(variableTypes.stream().map(Object::toString).collect(Collectors.joining(" ", "Computing for ", "")));

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
        Path outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

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

        // output in partial folder
        PartialIDFile.write(partialIDs, outPath.resolve("partial"));
    }

    private ButterworthFilter designBandPassFilter() throws IOException {
        System.err.println("Designing filter. " + lowFreq + " - " + highFreq);
        double omegaH = highFreq * 2 * Math.PI / partialSamplingHz;
        double omegaL = lowFreq * 2 * Math.PI / partialSamplingHz;
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
                for (VariableType variableType : variableTypes) {
                    try {
                        convertSPCToPartials(observer, variableType);
                    } catch (IOException e) {
                        // this println() is for starting new line after writing "."s
                        System.err.println();
                        System.err.println("Failure for " + observer + " " + variableType);
                        e.printStackTrace();
                    }
                }
            }
            System.err.print(".");
        }

        private void convertSPCToPartials(Observer observer, VariableType variableType) throws IOException {
            // collect SPC files
            SPCFileAccess shSPCFile = (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) ? findSPCFile(observer, variableType, SPCMode.SH) : null;
            SPCFileAccess psvSPCFile = (usableSPCMode != SpcFileAid.UsableSPCMode.SH) ? findSPCFile(observer, variableType, SPCMode.PSV) : null;

            // collect corresponding timewindows
            Set<TimewindowData> correspondingTimewindows = timewindowSet.stream()
                    .filter(timewindow -> timewindow.getGlobalCMTID().equals(event) && timewindow.getObserver().equals(observer))
                    .collect(Collectors.toSet());

            for (TimewindowData timewindow : correspondingTimewindows) {
                if (usableSPCMode == SpcFileAid.UsableSPCMode.SH) {
                    buildPartialWaveform(shSPCFile, timewindow, variableType);
                } else if (usableSPCMode == SpcFileAid.UsableSPCMode.PSV) {
                    buildPartialWaveform(psvSPCFile, timewindow, variableType);
                } else {
                    buildPartialWaveform(shSPCFile, psvSPCFile, timewindow, variableType);
                }
            }
        }

        private SPCFileAccess findSPCFile(Observer observer, VariableType variableType, SPCMode mode) throws IOException {
            Path modelPath = (mode == SPCMode.SH) ? shModelPath : psvModelPath;
            Path spcPath = modelPath.resolve(observer.getPosition().toCode() + "." + event + "." + variableType.to1DSpcType() + "..." + mode + ".spc");
            if (!SPCFileName.isFormatted(spcPath)) {
                throw new IllegalStateException(spcPath + " has invalid SPC file name.");
            }
            SPCFileName spcName = new FormattedSPCFileName(spcPath);
            SPCFileAccess spcFile = spcName.read();
            if (spcFile.tlen() != tlen || spcFile.np() != np) {
                throw new IllegalStateException(spcFile + " has different tlen or np.");
            }
            process(spcFile);
            return spcFile;
        }

        private void buildPartialWaveform(SPCFileAccess spcFile, TimewindowData timewindow, VariableType variableType) {
            for (int k = 0; k < spcFile.nbody(); k++) {
                double currentBodyR = spcFile.getBodyR()[k];
                if (layerRadii != null) {
                    boolean exists = false;
                    for (double radius : layerRadii)
                        if (Precision.equals(radius, currentBodyR, FullPosition.RADIUS_EPSILON))
                            exists = true;
                    if (!exists)
                        continue;
                }
                double[] ut = spcFile.getSpcBodyList().get(k).getSpcElement(timewindow.getComponent()).getTimeseries();

                // apply filter
                double[] filteredUt = filter.applyFilter(ut);

                cutAndWrite(filteredUt, timewindow, currentBodyR, variableType);
            }
        }
        private void buildPartialWaveform(SPCFileAccess shSPCFile, SPCFileAccess psvSPCFile, TimewindowData timewindow, VariableType variableType) {
            for (int k = 0; k < shSPCFile.nbody(); k++) {
                if (!Precision.equals(shSPCFile.getBodyR()[k], psvSPCFile.getBodyR()[k], FullPosition.RADIUS_EPSILON)) {
                    throw new RuntimeException("SH and PSV bodyR differ " + shSPCFile.getBodyR()[k] + " " + psvSPCFile.getBodyR()[k]);
                }
                double currentBodyR = shSPCFile.getBodyR()[k];
                if (layerRadii != null) {
                    boolean exists = false;
                    for (double radius : layerRadii)
                        if (Precision.equals(radius, currentBodyR, FullPosition.RADIUS_EPSILON))
                            exists = true;
                    if (!exists)
                        continue;
                }
                double[] shUt = shSPCFile.getSpcBodyList().get(k).getSpcElement(timewindow.getComponent()).getTimeseries();
                double[] psvUt = psvSPCFile.getSpcBodyList().get(k).getSpcElement(timewindow.getComponent()).getTimeseries();

                if (shUt.length != psvUt.length)
                    throw new RuntimeException("sh and psv timeseries do not have the same length " + shUt.length + " " + psvUt.length);

                // apply filter
                double[] filteredSHUt = filter.applyFilter(shUt);
                double[] filteredPSVUt = filter.applyFilter(psvUt);
                double[] summedUt = new double[filteredSHUt.length];
                for (int it = 0; it < filteredSHUt.length; it++)
                    summedUt[it] = filteredSHUt[it] + filteredPSVUt[it];

                cutAndWrite(summedUt, timewindow, currentBodyR, variableType);
            }
        }

        private void process(SPCFileAccess spcFile) {
            for (SACComponent component : components) {
                spcFile.getSpcBodyList().stream().map(body -> body.getSpcElement(component))
                        .forEach(spcElement -> {
                            spcElement.applySourceTimeFunction(sourceTimeFunctions.get(event));
                            spcElement.toTimeDomain((int) MathAid.roundForPrecision(tlen * partialSamplingHz));
                            spcElement.applyGrowingExponential(spcFile.omegai(), partialSamplingHz);
                            spcElement.amplitudeCorrection(partialSamplingHz);
                        });
            }
        }

        private void cutAndWrite(double[] filteredUt, TimewindowData timewindow, double bodyR, VariableType variableType) {
            double[] xs = IntStream.range(0, filteredUt.length).mapToDouble(i -> i / partialSamplingHz).toArray();
            Trace filteredTrace = new Trace(xs, filteredUt);
            Trace resampledTrace = filteredTrace.resampleInWindow(timewindow, partialSamplingHz, finalSamplingHz);

            PartialID partialID = new PartialID(timewindow.getObserver(), event, timewindow.getComponent(), finalSamplingHz,
                    timewindow.getStartTime(), resampledTrace.getLength(), 1 / highFreq, 1 / lowFreq,
                    timewindow.getPhases(), sourceTimeFunctionType != SourceTimeFunctionType.NONE,
                    ParameterType.LAYER, variableType, new FullPosition(0, 0, bodyR), resampledTrace.getY());
            partialIDs.add(partialID);
        }

    }

}
