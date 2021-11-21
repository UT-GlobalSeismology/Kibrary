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
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.aid.ThreadAid;
import io.github.kensuke1984.kibrary.correction.SourceTimeFunction;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;

/**
 * Operation that converts from {@link SPCFile} to {@link SACFileAccess} file.
 * Source time function can be convolved during this process.
 * <p>
 * It converts all the SPC files in eventFolders/model under the specified PSVFolder and SHFolder.
 * If you leave 'model name' blank and each event folder has only one folder,
 * the model name will be set automatically as the name of that folder.
 * <p>
 * The waveform in time domain will be sampled in {@link #samplingHz},
 * so the number of data points will become [time length x samplingHz].
 *
 * @author Kensuke Konishi
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public final class SPC_SAC implements Operation {

    private final Properties property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * Path of the output folder
     */
    private Path outPath;

    private Path sourceTimeFunctionPath;
    /**
     * components to be computed
     */
    private Set<SACComponent> components;
    /**
     * the name of a folder containing SPC files (e.g. PREM)（""）
     */
    private String modelName;
    /**
     * sampling Hz [Hz] must be 20 now.
     */
    private double samplingHz;
    /**
     * source time function. -1:Users, 0: none, 1: boxcar, 2: triangle
     */
    private int sourceTimeFunction;
    private Path psvPath;
    private Path shPath;
    /**
     * If it computes temporal partial or not.
     */
    private boolean computesPartial;
    private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;
    private Set<SPCFileName> psvSPCs;
    private Set<SPCFileName> shSPCs;

    private final List<String> stfcat =
            readSTFCatalogue("astf_cc_ampratio_ca.catalog"); //LSTF1 ASTF1 ASTF2 CATZ_STF.stfcat

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(SPC_SAC.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan SPC_SAC");
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##SACComponents for write (Z R T)");
            pw.println("#components");
            pw.println("##In the following, if you do NOT want to use PSV or SH, set the one 'null'.");
            pw.println("##Path of a PSV folder (.)");
            pw.println("#psvPath");
            pw.println("##Path of an SH folder (.)");
            pw.println("#shPath");
            pw.println("##The model name used; e.g. if it is PREM, spectrum files in 'eventDir/PREM' are used.");
            pw.println("##If it is unset, then automatically set as the name of the folder in the eventDirs");
            pw.println("## but the eventDirs can have only one folder inside and they must be the same.");
            pw.println("#modelName");
            pw.println("##Type of source time function; 0:none, 1:boxcar, 2:triangle. (0)");
            pw.println("## or folder name containing *.stf if you want to your own GLOBALCMTID.stf ");
            pw.println("#sourceTimeFunction");
            pw.println("##SamplingHz (20) !You can not change yet!");
            pw.println("#samplingHz");
            pw.println("##(boolean) If it is true, then temporal partial is computed. (false)");
            pw.println("#timePartial");
        }
        System.err.println(outPath + " is created.");
    }

    public SPC_SAC(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("psvPath")) property.setProperty("psvPath", "");
        if (!property.containsKey("shPath")) property.setProperty("shPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("sourceTimeFunction")) property.setProperty("sourceTimeFunction", "0");
        if (!property.containsKey("timePartial")) property.setProperty("timePartial", "false");
        if (!property.containsKey("modelName")) property.setProperty("modelName", "");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        if (!property.getProperty("psvPath").equals("null")) psvPath = getPath("psvPath");
        if (psvPath != null && !Files.exists(psvPath))
            throw new NoSuchFileException("The psvPath " + psvPath + " does not exist");

        if (!property.getProperty("shPath").equals("null")) shPath = getPath("shPath");
        if (shPath != null && !Files.exists(shPath))
            throw new NoSuchFileException("The shPath " + shPath + " does not exist");

        modelName = property.getProperty("modelName");
        if (modelName.isEmpty()) setModelName();

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        setSourceTimeFunction();
        computesPartial = Boolean.parseBoolean(property.getProperty("timePartial"));
        samplingHz = 20; // TODO
    }

    /**
     * @param args [parameter file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        SPC_SAC ss = new SPC_SAC(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(SPC_SAC.class.getName() + " is operating.");
        ss.run();
        System.err.println(SPC_SAC.class.getName() + " finished in " +
                Utilities.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws IOException {
        System.err.println("Model name is " + modelName);
        if (sourceTimeFunction == -1) readUserSourceTimeFunctions();

        if (psvPath != null && (psvSPCs = collectPSVSPCs()).isEmpty())
            throw new FileNotFoundException("No PSV spector files are found.");

        if (shPath != null && (shSPCs = collectSHSPCs()).isEmpty())
            throw new FileNotFoundException("No SH spector files are found.");

        outPath = workPath.resolve("spcsac" + Utilities.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        ExecutorService es = ThreadAid.createFixedThreadPool();

        int nSAC = 0;
        // single
        if (psvPath == null || shPath == null) for (SPCFileName spc : psvSPCs != null ? psvSPCs : shSPCs) {
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
                System.err.println(pair + " does not exist");
                continue;
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
        sm.setTemporalDifferentiation(computesPartial);
        sm.setOutPath(outPath.resolve(primeSPC.getSourceID()));
        return sm;
    }


    private void setSourceTimeFunction() {
        String s = property.getProperty("sourceTimeFunction");
        if (s.length() == 1 && Character.isDigit(s.charAt(0)))
            sourceTimeFunction = Integer.parseInt(property.getProperty("sourceTimeFunction"));
        else {
            sourceTimeFunction = -1;
            sourceTimeFunctionPath = getPath("sourceTimeFunction");
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

    private void readUserSourceTimeFunctions() throws IOException {
        Set<GlobalCMTID> ids = Utilities.globalCMTIDSet(workPath);
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
        double halfDuration = id.getEvent().getHalfDuration();
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
                    double halfDuration1 = id.getEvent().getHalfDuration();
                    double halfDuration2 = id.getEvent().getHalfDuration();
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
                          stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEvent().getHalfDuration());
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
                        return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEvent().getHalfDuration());
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
                      stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, id.getEvent().getHalfDuration());
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

    private void setModelName() throws IOException {
        Set<EventFolder> eventFolders = new HashSet<>();
        if (psvPath != null) eventFolders.addAll(Utilities.eventFolderSet(psvPath));
        if (shPath != null) eventFolders.addAll(Utilities.eventFolderSet(shPath));
        Set<String> possibleNames =
                eventFolders.stream().flatMap(ef -> Arrays.stream(ef.listFiles(File::isDirectory))).map(File::getName)
                        .collect(Collectors.toSet());
        if (possibleNames.size() != 1) throw new RuntimeException(
                "There are no model folder in event folders or more than one folder. You must specify 'modelName' in the case.");

        String modelName = possibleNames.iterator().next();
        if (eventFolders.stream().map(EventFolder::toPath).map(p -> p.resolve(modelName)).allMatch(Files::exists))
            this.modelName = modelName;
        else throw new RuntimeException("There are some events without model folder " + modelName);
    }

    private Set<SPCFileName> collectSHSPCs() throws IOException {
        Set<SPCFileName> shSet = new HashSet<>();
        Set<EventFolder> eventFolderSet = Utilities.eventFolderSet(shPath);
        for (EventFolder eventFolder : eventFolderSet) {
            Path modelFolder = eventFolder.toPath().resolve(modelName);
            Utilities.collectSpcFileName(modelFolder).stream()
                    .filter(f -> !f.getName().contains("par") && f.getName().endsWith("SH.spc")).forEach(shSet::add);
        }
        return shSet;
    }

    private Set<SPCFileName> collectPSVSPCs() throws IOException {
        Set<SPCFileName> psvSet = new HashSet<>();
        Set<EventFolder> eventFolderSet = Utilities.eventFolderSet(psvPath);
        for (EventFolder eventFolder : eventFolderSet) {
            Path modelFolder = eventFolder.toPath().resolve(modelName);
            Utilities.collectSpcFileName(modelFolder).stream()
                    .filter(f -> !f.getName().contains("par") && f.getName().endsWith("PSV.spc")).forEach(psvSet::add);
        }
        return psvSet;
    }

    private FormattedSPCFileName pairFile(SPCFileName psvFileName) {
        if (psvFileName.getMode() == SPCMode.SH) return null;
        return new FormattedSPCFileName(shPath.resolve(psvFileName.getSourceID() + "/" + modelName + "/" +
                psvFileName.getName().replace("PSV.spc", "SH.spc")));
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

    @Override
    public Path getWorkPath() {
        return workPath;
    }

}
