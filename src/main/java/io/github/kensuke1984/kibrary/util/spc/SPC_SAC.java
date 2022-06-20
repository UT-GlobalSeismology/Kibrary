package io.github.kensuke1984.kibrary.util.spc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.SourceTimeFunction;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;
    /**
     * components to be computed
     */
    private Set<SACComponent> components;

    private boolean usePSV;
    private Path psvPath;
    private boolean useSH;
    private Path shPath;

    /**
     * the name of a folder containing SPC files (e.g. PREM)（""）
     */
    private String modelName;
    /**
     * source time function. -1:Users, 0: none, 1: boxcar, 2: triangle
     */
    private int sourceTimeFunction;
    private Path sourceTimeFunctionPath;
    /**
     * sampling Hz [Hz] must be 20 now.
     */
    private double samplingHz;
    /**
     * If it computes temporal partial or not.
     */
    private boolean computeTimePartial;
    /**
     * If this is true, the SACExtension of computed files will be that of observed SAC files
     */
    private boolean computeAsObserved;

    private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;
    private Set<SPCFileName> psvSPCs;
    private Set<SPCFileName> shSPCs;

    private final List<String> stfcat =
            readSTFCatalogue("astf_cc_ampratio_ca.catalog"); //LSTF1 ASTF1 ASTF2 CATZ_STF.stfcat

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SACComponents to be exported, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(boolean) Whether to use PSV spectrums (true)");
            pw.println("#usePSV ");
            pw.println("##Path of a PSV folder (.)");
            pw.println("#psvPath ");
            pw.println("##(boolean) Whether to use SH spectrums (true)");
            pw.println("#useSH ");
            pw.println("##Path of an SH folder (.)");
            pw.println("#shPath ");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used.");
            pw.println("## If this is unset, then automatically set as the name of the folder in the eventDirs");
            pw.println("##  but the eventDirs can have only one folder inside and they must be the same.");
            pw.println("#modelName ");
            pw.println("##Type of source time function from {0:none, 1:boxcar, 2:triangle} (0)");
            pw.println("## or folder name containing *.stf if you want to use your own GLOBALCMTID.stf");
            pw.println("#sourceTimeFunction ");
            pw.println("##SamplingHz (20) !You can not change yet!");
            pw.println("#samplingHz ");
            pw.println("##(boolean) If this is true, temporal partial is computed (false)");
            pw.println("#computeTimePartial ");
            pw.println("##(boolean) If this is true, the SACExtension of computed files will be that of observed (false)");
            pw.println("## This is only valid when computeTimePartial is false.");
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
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        usePSV = property.parseBoolean("usePSV", "true");
        if (usePSV) psvPath = property.parsePath("psvPath", ".", true, workPath);
        useSH = property.parseBoolean("useSH", "true");
        if (useSH) shPath = property.parsePath("shPath", ".", true, workPath);

        if (property.containsKey("modelName")) {
            modelName = property.parseString("modelName", null);
        } else {
            modelName = searchModelName();
        }

        setSourceTimeFunction();
        samplingHz = 20; // TODO
        computeTimePartial = property.parseBoolean("computeTimePartial", "false");
        computeAsObserved = property.parseBoolean("computeAsObserved", "false");
    }

    private String searchModelName() throws IOException {
        // gather all names of model folders
        Set<EventFolder> eventFolders = new HashSet<>();
        if (usePSV) eventFolders.addAll(DatasetAid.eventFolderSet(psvPath));
        if (useSH) eventFolders.addAll(DatasetAid.eventFolderSet(shPath));
        Set<String> possibleNames =
                eventFolders.stream().flatMap(ef -> Arrays.stream(ef.listFiles(File::isDirectory))).map(File::getName)
                        .collect(Collectors.toSet());
        if (possibleNames.size() != 1) throw new RuntimeException(
                "There are no model folders in event folders or more than one folder. You must specify 'modelName' in this case.");

        // set model name
        String modelName = possibleNames.iterator().next();

        // check if all events contains the model folder
        if (eventFolders.stream().map(EventFolder::toPath).map(p -> p.resolve(modelName)).allMatch(Files::exists))
            return modelName;
        else throw new RuntimeException("There are some events without model folder " + modelName);
    }

    private void setSourceTimeFunction() throws IOException {
        String s = property.parseString("sourceTimeFunction", "0");
        if (s.length() == 1 && Character.isDigit(s.charAt(0)))
            sourceTimeFunction = Integer.parseInt(s);
        else {
            sourceTimeFunction = -1;
            sourceTimeFunctionPath = property.parsePath("sourceTimeFunction", null, true, workPath);
        }
        switch (sourceTimeFunction) {
            case -1:
            case 0:
            case 1:
            case 2:
            case 3:
            case 5:
                return;
            default:
                throw new RuntimeException("Integer for source time function is invalid.");
        }
    }

    @Override
    public void run() throws IOException {
        System.err.println("Model name is " + modelName);
        if (sourceTimeFunction == -1) readUserSourceTimeFunctions();

        if (usePSV == false && useSH == false) {
            System.err.println("Both usePSV and useSH are false; nothing to do.");
            return;
        }
        if (usePSV == true && (psvSPCs = collectSPCs(SPCMode.PSV, psvPath)).isEmpty()) {
            throw new FileNotFoundException("No PSV spectrum files are found.");
        }
        if (useSH == true && (shSPCs = collectSPCs(SPCMode.SH, shPath)).isEmpty()) {
            throw new FileNotFoundException("No SH spectrum files are found.");
        }
        if (usePSV == true && useSH == true && psvSPCs.size() != shSPCs.size()) {
            throw new IllegalStateException("Number of PSV files and SH files does not match.");
        }

        outPath = DatasetAid.createOutputFolder(workPath, "spcsac", tag, GadgetAid.getTemporaryString());

        ExecutorService es = ThreadAid.createFixedThreadPool();

        int nSAC = 0;
        // single
        if (usePSV == false || useSH == false) for (SPCFileName spc : (usePSV == true ? psvSPCs : shSPCs)) {
            SPCFile one = SPCFile.getInstance(spc);
            // create event folder under outPath
            Files.createDirectories(outPath.resolve(spc.getSourceID()));
            // operate method createSACMaker() -> instance of an anonymous inner class is returned
            // -> executes the run() of that class defined in createSACMaker()
            es.execute(createSACMaker(one, null));
            nSAC++;
            if (nSAC % 5 == 0) System.err.print("\rReading SPC files ... " + nSAC + " files");
        }
        // both
        else for (SPCFileName spc : psvSPCs) {
            SPCFile one = SPCFile.getInstance(spc);
            SPCFileName pair = pairFile(spc);
            if (pair == null || !pair.exists()) {
                throw new NoSuchFileException(pair + " does not exist");
            }
            SPCFile two = SPCFile.getInstance(pairFile(spc));
            // create event folder under outPath
            Files.createDirectories(outPath.resolve(spc.getSourceID()));
            // operate method createSACMaker() -> instance of an anonymous inner class is returned
            // -> executes the run() of that class defined in createSACMaker()
            es.execute(createSACMaker(one, two));
            nSAC++;
            if (nSAC % 5 == 0) System.err.print("\rReading SPC files ... " + nSAC + " pairs");
        }
        System.err.println("\rReading SPC files finished. " + nSAC + " total.");

        es.shutdown();
        while (!es.isTerminated()) {
            System.err.print("\rConverting " + Math.ceil(100.0 * numberOfCreatedSAC.get() / nSAC) + "%");
            ThreadAid.sleep(100);
        }
        System.err.println("\rConverting finished.");
    }

    private AtomicInteger numberOfCreatedSAC = new AtomicInteger();

    /**
     * creates {@link SACMaker} from two SPC files(sh, psv)
     *
     * @param primeSPC     spectrum file for SAC
     * @param secondarySPC null is ok
     * @return {@link SACMaker}
     */
    private SACMaker createSACMaker(SPCFile primeSPC, SPCFile secondarySPC) {
        SourceTimeFunction sourceTimeFunction = getSourceTimeFunction(primeSPC.np(), primeSPC.tlen(), samplingHz,
                new GlobalCMTID(primeSPC.getSourceID()));
        // create instance of an anonymous inner class extending SACMaker with the following run() function
        SACMaker sm = new SACMaker(primeSPC, secondarySPC, sourceTimeFunction) {
            @Override
            public void run() {
                // execute run() in SACMaker
                super.run();
                numberOfCreatedSAC.incrementAndGet();
            }
        };
        sm.setComponents(components);
        sm.setTemporalDifferentiation(computeTimePartial);
        sm.setAsObserved(computeAsObserved);
        sm.setOutPath(outPath.resolve(primeSPC.getSourceID()));
        return sm;
    }


    private void readUserSourceTimeFunctions() throws IOException {
        Set<GlobalCMTID> ids = DatasetAid.globalCMTIDSet(workPath);
        userSourceTimeFunctions = new HashMap<>(ids.size());
        for (GlobalCMTID id : ids)
            userSourceTimeFunctions
                    .put(id, SourceTimeFunction.readSourceTimeFunction(sourceTimeFunctionPath.resolve(id + ".stf")));
    }

    /**
     * @param np
     * @param tlen
     * @param samplingHz
     * @param id
     * @return
     * @author anselme add more options for source time function catalogs
     */
    private SourceTimeFunction getSourceTimeFunction(int np, double tlen, double samplingHz, GlobalCMTID id) {
        double halfDuration = id.getEventData().getHalfDuration();
        switch (sourceTimeFunction) {
            case -1:
                SourceTimeFunction tmp = userSourceTimeFunctions.get(id);
                if (tmp == null)
                    tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
                return tmp;
            case 0:
                return null;
            case 1:
                return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case 2:
                return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case 3:
                if (stfcat.contains("LSTF")) {
                    double halfDuration1 = id.getEventData().getHalfDuration();
                    double halfDuration2 = id.getEventData().getHalfDuration();
                    boolean found = false;
                      for (String str : stfcat) {
                          String[] stflist = str.split("\\s+");
                          GlobalCMTID eventID = new GlobalCMTID(stflist[0]);
                          if(id.equals(eventID)) {
                              if(Integer.valueOf(stflist[3]) >= 5.) {
                                  halfDuration1 = Double.valueOf(stflist[1]);
                                  halfDuration2 = Double.valueOf(stflist[2]);
                                  found = true;
                              }
                          }
                      }
                      SourceTimeFunction stf = null;
                      if (found) {
                          stf = SourceTimeFunction.asymmetrictriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration1, halfDuration2);
                      }
                      else
                          stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEventData().getHalfDuration());
                      return stf;
                }
                else {
                    boolean found = false;
                    double ampCorr = 1.;
                    for (String str : stfcat) {
                          String[] ss = str.split("\\s+");
                          GlobalCMTID eventID = new GlobalCMTID(ss[0]);
                          if (id.equals(eventID)) {
                              halfDuration = Double.parseDouble(ss[1]);
                              ampCorr = Double.parseDouble(ss[2]);
                              found = true;
                              break;
                          }
                      }
                    if (found)
                        return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration, ampCorr);
                    else
                        return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEventData().getHalfDuration());
                }
            case 4:
                throw new RuntimeException("Case 4 not implemented yet");
            case 5:
                halfDuration = 0.;
                double amplitudeCorrection = 1.;
                boolean found = false;
                  for (String str : stfcat) {
                      String[] stflist = str.split("\\s+");
                      GlobalCMTID eventID = new GlobalCMTID(stflist[0].trim());
                      if(id.equals(eventID)) {
                          halfDuration = Double.valueOf(stflist[1].trim());
                          amplitudeCorrection = Double.valueOf(stflist[2].trim());
                          found = true;
                      }
                  }
                  SourceTimeFunction stf = null;
                  if (found)
                      stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration, 1. / amplitudeCorrection);
                  else
                      stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEventData().getHalfDuration());
                  return stf;
            default:
                throw new RuntimeException("Integer for source time function is invalid.");
        }
    }

    private List<String> readSTFCatalogue(String STFcatalogue) throws IOException {
        System.err.println("STF catalogue: " +  STFcatalogue);
        return IOUtils.readLines(SPC_SAC.class.getClassLoader().getResourceAsStream(STFcatalogue)
                    , Charset.defaultCharset());
    }

    private Set<SPCFileName> collectSPCs(SPCMode mode, Path inPath) throws IOException {
        Set<SPCFileName> spcSet = new HashSet<>();
        Set<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(inPath);
        for (EventFolder eventFolder : eventFolderSet) {
            Path modelFolder = eventFolder.toPath().resolve(modelName);
            SpcFileAid.collectSpcFileName(modelFolder).stream()
                    .filter(f -> f.getMode() == mode).forEach(spcSet::add);
        }
        return spcSet;
    }

    private FormattedSPCFileName pairFile(SPCFileName psvFileName) {
        if (psvFileName.getMode() == SPCMode.SH) return null;
        return new FormattedSPCFileName(shPath.resolve(psvFileName.getSourceID()).resolve(modelName)
                .resolve(psvFileName.pairFileName()));
    }

}
