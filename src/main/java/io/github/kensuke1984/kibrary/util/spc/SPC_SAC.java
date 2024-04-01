package io.github.kensuke1984.kibrary.util.spc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionHandler;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;

/**
 * Operation that converts from {@link SPCFile} to {@link SACFileAccess} file.
 * Source time function can be convolved during this process.
 * <p>
 * It converts all the SPC files in eventFolders/modelName under the specified PSV folder and SH folder.
 * If either {@link #usePSV} or {@link #useSH} is false, only one of the PSV or SH files will be used.
 * If both {@link #usePSV} and {@link #useSH} is true and a pair for an SPC file cannot be found, an exception will be thrown.
 * <p>
 * If you set a 'model name', only SPC files under that model folder will be used.
 * In this case, there may be event folders that do not contain this model folder.
 * <p>
 * If you leave 'model name' blank and all event folders have exactly one folder, all with a common name,
 * the model name will be set automatically as the name of that folder.
 * In this case, if several model names exist, or if event folders without the common-name model folder exist,
 * an exception will be thrown.
 * <p>
 * If {@link #components} is set, only the SAC files for the components included here will be exported.
 * <p>
 * The waveform in time domain will be sampled in {@link #samplingHz},
 * so the number of data points will become [time length x samplingHz].
 *
 * @author Kensuke Konishi
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public final class SPC_SAC extends Operation {

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
     * Path of the output folder.
     */
    private Path outPath;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    private Path shPath;
    private Path psvPath;
    /**
     * The SPC modes that shall be used: SH, PSV, or BOTH.
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
     * Sampling Hz [Hz]. must be 20 now.
     */
    private double samplingHz;
    /**
     * If it computes temporal partial or not.
     */
    private boolean computeTimePartial;
    /**
     * If this is true, the SACExtension of computed files will be that of observed SAC files.
     */
    private boolean computeAsObserved;

    private Set<SPCFileName> shSPCs;
    private Set<SPCFileName> psvSPCs;
    private SourceTimeFunctionHandler stfHandler;
    /**
     * Number of sac files that are done creating.
     */
    private AtomicInteger numberOfCreatedSAC = new AtomicInteger();

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
            pw.println("##SACComponents to be exported, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of an SH folder. (.)");
            pw.println("#shPath ");
            pw.println("##Path of a PSV folder. (.)");
            pw.println("#psvPath ");
            pw.println("##The mode of spc files that have been computed, from {SH, PSV, BOTH}. (BOTH)");
            pw.println("#usableSPCMode ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used.");
            pw.println("##  If this is unset, then automatically set as the name of the folder in the eventDirs");
            pw.println("##    but the eventDirs can have only one folder inside and they must be the same.");
            pw.println("#modelName ");
            pw.println("##Path of folder containing source time functions. If not set, the following sourceTimeFunctionType will be used.");
            pw.println("#userSourceTimeFunctionPath ");
            pw.println("##Type of source time function, from {0:none, 1:boxcar, 2:triangle, 3:asymmetricTriangle, 4:auto}. (0)");
            pw.println("##  When 'auto' is selected, the function specified in the GCMT catalog will be used.");
            pw.println("#sourceTimeFunctionType ");
            pw.println("##Path of a catalog to set source time function durations. If unneeded, leave this unset.");
            pw.println("#sourceTimeFunctionCatalogPath ");
            pw.println("##Sampling frequency [Hz]. (20) !You can not change yet!");
            pw.println("#samplingHz ");
            pw.println("##(boolean) If this is true, temporal partial is computed. (false)");
            pw.println("#computeTimePartial ");
            pw.println("##(boolean) If this is true, the SACExtension of computed files will be that of observed. (false)");
            pw.println("##  This is only valid when computeTimePartial is false.");
            pw.println("#computeAsObserved ");
        }
        System.err.println(outPath + " is created.");
    }

    public SPC_SAC(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        shPath = property.parsePath("shPath", ".", true, workPath);
        psvPath = property.parsePath("psvPath", ".", true, workPath);
        usableSPCMode = SpcFileAid.UsableSPCMode.valueOf(property.parseString("usableSPCMode", "BOTH").toUpperCase());
        if (property.containsKey("modelName")) {
            modelName = property.parseString("modelName", null);
        } else {
            modelName = searchModelName();
        }

        if (property.containsKey("userSourceTimeFunctionPath")) {
            userSourceTimeFunctionPath = property.parsePath("userSourceTimeFunctionPath", null, true, workPath);
        } else {
            sourceTimeFunctionType = SourceTimeFunctionType.valueOf(property.parseInt("sourceTimeFunctionType", "0"));
        }
        if (property.containsKey("sourceTimeFunctionCatalogPath")) {
            sourceTimeFunctionCatalogPath = property.parsePath("sourceTimeFunctionCatalogPath", null, true, workPath);
        }

        samplingHz = 20; // TODO
        computeTimePartial = property.parseBoolean("computeTimePartial", "false");
        computeAsObserved = property.parseBoolean("computeAsObserved", "false");
    }

    private String searchModelName() throws IOException {
        // gather all names of model folders
        Set<EventFolder> eventFolders = new HashSet<>();
        if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV) eventFolders.addAll(DatasetAid.eventFolderSet(shPath));
        if (usableSPCMode != SpcFileAid.UsableSPCMode.SH) eventFolders.addAll(DatasetAid.eventFolderSet(psvPath));
        Set<String> possibleNames =
                eventFolders.stream().flatMap(ef -> Arrays.stream(ef.listFiles(File::isDirectory))).map(File::getName)
                        .collect(Collectors.toSet());
        if (possibleNames.size() != 1) throw new RuntimeException(
                "There are no model folders in event folders or more than one folder. You must specify 'modelName' in this case.");

        // set model name
        String modelName = possibleNames.iterator().next();

        // check if all events contain the model folder
        if (eventFolders.stream().map(EventFolder::toPath).map(p -> p.resolve(modelName)).allMatch(Files::exists))
            return modelName;
        else throw new RuntimeException("There are some events without model folder " + modelName);
    }

    @Override
    public void run() throws IOException {
        System.err.println("Using mode " + usableSPCMode);
        System.err.println("Model name is " + modelName);

        stfHandler = new SourceTimeFunctionHandler(sourceTimeFunctionType,
                sourceTimeFunctionCatalogPath, userSourceTimeFunctionPath, DatasetAid.globalCMTIDSet(workPath));

        if (usableSPCMode != SpcFileAid.UsableSPCMode.PSV && (shSPCs = collectSPCsFromAllEvents(SPCMode.SH, shPath)).isEmpty()) {
            throw new FileNotFoundException("No SH spectrum files are found.");
        }
        if (usableSPCMode != SpcFileAid.UsableSPCMode.SH && (psvSPCs = collectSPCsFromAllEvents(SPCMode.PSV, psvPath)).isEmpty()) {
            throw new FileNotFoundException("No PSV spectrum files are found.");
        }
        if (usableSPCMode == SpcFileAid.UsableSPCMode.BOTH && psvSPCs.size() != shSPCs.size()) {
            throw new IllegalStateException("Number of PSV files and SH files does not match.");
        }

        outPath = DatasetAid.createOutputFolder(workPath, "spcsac", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();

        int nSAC = 0;
        // single
        if (usableSPCMode != SpcFileAid.UsableSPCMode.BOTH) {
            for (SPCFileName spc : (usableSPCMode == SpcFileAid.UsableSPCMode.SH ? shSPCs : psvSPCs)) {
                SPCFile spcFile = SPCFile.getInstance(spc);
                // create event folder under outPath
                Files.createDirectories(outPath.resolve(spc.getSourceID()));
                // operate method createSACMaker() -> instance of an anonymous inner class is returned
                // -> executes the run() of that class defined in createSACMaker()
                es.execute(createSACMaker(spcFile, null));
                nSAC++;
                if (nSAC % 5 == 0) System.err.print("\rReading SPC files ... " + nSAC + " files");
            }
        }
        // both
        else {
            for (SPCFileName shSPC : shSPCs) {
                SPCFileName psvSPC = pairPSVFile(shSPC);
                if (psvSPC == null || !psvSPC.exists()) {
                    throw new NoSuchFileException(psvSPC + " does not exist");
                }
                SPCFile shFile = SPCFile.getInstance(shSPC);
                SPCFile psvFile = SPCFile.getInstance(psvSPC);
                // create event folder under outPath
                Files.createDirectories(outPath.resolve(shSPC.getSourceID()));
                // operate method createSACMaker() -> instance of an anonymous inner class is returned
                // -> executes the run() of that class defined in createSACMaker()
                es.execute(createSACMaker(shFile, psvFile));
                nSAC++;
                if (nSAC % 5 == 0) System.err.print("\rReading SPC files ... " + nSAC + " pairs");
            }
        }
        System.err.println("\rReading SPC files finished. " + nSAC + " total.");

        es.shutdown();
        while (!es.isTerminated()) {
            System.err.print("\rConverting " + MathAid.ceil(100.0 * numberOfCreatedSAC.get() / nSAC) + "%");
            ThreadAid.sleep(100);
        }
        System.err.println("\rConverting finished.");
    }

    /**
     * Creates {@link SACMaker} from two SPC files (sh, psv).
     *
     * @param primarySPC ({@link SPCFile}) First spectrum file for SAC.
     * @param secondarySPC ({@link SPCFile}) Second spectrum file for SAC. null is OK.
     * @return ({@link SACMaker})
     */
    private SACMaker createSACMaker(SPCFile primarySPC, SPCFile secondarySPC) {
        SourceTimeFunction sourceTimeFunction = stfHandler.createSourceTimeFunction(primarySPC.np(), primarySPC.tlen(), samplingHz,
                new GlobalCMTID(primarySPC.getSourceID()));
        // create instance of an anonymous inner class extending SACMaker with the following run() function
        SACMaker sm = new SACMaker(primarySPC, secondarySPC, sourceTimeFunction) {
            @Override
            public void run() {
                // execute run() in SACMaker
                try {
                    super.run();
                    numberOfCreatedSAC.incrementAndGet();
                } catch (Exception e) {
                    System.err.println();
                    System.err.println("!! " + primarySPC.getSpcFileName().toString() + " failed:");
                    throw e;
                }
            }
        };
        sm.setComponents(components);
        sm.setTemporalDifferentiation(computeTimePartial);
        sm.setAsObserved(computeAsObserved);
        sm.setOutPath(outPath.resolve(primarySPC.getSourceID()));
        return sm;
    }

    private Set<SPCFileName> collectSPCsFromAllEvents(SPCMode mode, Path inPath) throws IOException {
        Set<SPCFileName> spcSet = new HashSet<>();
        Set<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(inPath);
        for (EventFolder eventFolder : eventFolderSet) {
            Path modelFolder = eventFolder.toPath().resolve(modelName);
            SpcFileAid.collectSpcFileName(modelFolder).stream()
                    .filter(f -> f.getMode() == mode).forEach(spcSet::add);
        }
        return spcSet;
    }

    private FormattedSPCFileName pairPSVFile(SPCFileName shFileName) {
        if (shFileName.getMode() != SPCMode.SH) return null;
        return new FormattedSPCFileName(psvPath.resolve(shFileName.getSourceID()).resolve(modelName)
                .resolve(shFileName.pairFileName()));
    }

}
