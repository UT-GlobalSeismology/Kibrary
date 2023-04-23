package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Information file for SSHSH
 *
 * @author Kensuke Konishi
 * @since version 0.1.3
 * @version 2021/12/24 renamed from SshDSMInformationFileMaker
 */
public class OneDPartialDSMSetup extends Operation {

    private final Property property;
    /**
     * work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Information file name is header_[psv,sh].inf (default:PREM)
     */
    private String header;
    /**
     * components to create an information file for
     */
    private Set<SACComponent> components;
    /**
     * The root folder containing event folders which have observed SAC files
     */
    private Path obsPath;
    /**
     * perturbation radii
     */
    private double[] perturbationR;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;
    /**
     * number of steps in frequency domain, must be a power of 2 (2<sup>n</sup>)
     */
    private int np;
    /**
     * [s] time length of data must be a power of 2 divided by 10 (2<sup>n</sup>/10)
     */
    private double tlen;
    /**
     * Whether to use MPI-version of DSM in shellscript file
     */
    private boolean mpi;
    /**
     * Path of a time window file to selecte the set of events and observers
     */
    private Path timewindowPath;

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##(String) Header for names of information files, header_[psv, sh].inf, (PREM)");
            pw.println("#header");
            pw.println("##SacComponents to be used (Z R T)");
            pw.println("#components");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Depths for computations, listed using spaces, must be set");
            pw.println("#perturbationR 3500 3600");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##Time length to be computed, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, must be a power of 2 (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM computations (true)");
            pw.println("#mpi ");
            pw.println("##Path of a time window file to selecte the set of events and observers");
            pw.println("#timewindowPath");
        }
        System.err.println(outPath + " is created.");
    }

    public OneDPartialDSMSetup(Property property) throws IOException {
        this.property = (Property) property.clone();
        //set();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        header = property.parseStringSingle("header", "PREM");

        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        obsPath = property.parsePath("obsPath", ".", true, workPath);

        perturbationR = property.parseDoubleArray("perturbationR", null);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }
        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        mpi = property.parseBoolean("mpi", "true");

        if (property.containsKey("timewindowInformationPath"))
            timewindowPath = Paths.get(property.getProperty("timewindowPath"));
        else
            timewindowPath = null;
    }

    @Override
    public void run() throws IOException {

        String dataStr = GadgetAid.getTemporaryString();

        //set structure
        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = PolynomialStructureFile.read(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        //set output
        Path outPath = DatasetAid.createOutputFolder(workPath, "oneDPartial", tag, dataStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        Path isoOutPath = outPath.resolve("ISO");
        Path tiOutPath = outPath.resolve("TI");

        //read timewindows
        final Set<TimewindowData> timewindows;
        if (timewindowPath != null)
            timewindows = TimewindowDataFile.read(timewindowPath);
        else timewindows = null;

        //get set of observers and events
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(obsPath);
        if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
            return;
        }

        List<String> sourceList = new ArrayList<>();
        for (EventFolder eventDir : eventDirs) {
            Set<Observer> observers = eventDir.sacFileSet().stream()
                    .filter(name -> name.isOBS() && components.contains(name.getComponent()))
                    .map(name -> name.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                    .map(Observer::of).collect(Collectors.toSet());

            //select stations in timewindows
            if (timewindowPath != null)
                observers.removeIf(obs -> timewindows.stream().filter(tw -> tw.getObserver().equals(obs)
                        && tw.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                        && components.contains(tw.getComponent())).count() == 0);

            //check observers
            if (observers.isEmpty())
                continue;
            int numberOfStation = (int) observers.stream().map(Observer::getStation).count();
            if (numberOfStation != observers.size())
                System.err.println("!Caution there are stations with the same name and different positions in "
                        + eventDir.getGlobalCMTID());

            Path isoOutEvent = isoOutPath.resolve(eventDir.toString());
            Path isoModelPath = isoOutEvent.resolve(header);
            Path tiOutEvent = tiOutPath.resolve(eventDir.toString());
            Path tiModelPath = tiOutEvent.resolve(header);
            Files.createDirectories(isoOutEvent);
            Files.createDirectories(isoModelPath);
            Files.createDirectories(tiOutEvent);
            Files.createDirectories(tiModelPath);

            sourceList.add(eventDir.getGlobalCMTID().toString());

            OneDPartialDSMInputFile info = new OneDPartialDSMInputFile(structure, eventDir.getGlobalCMTID().getEventData(), observers, header, perturbationR,
                    tlen, np);
            info.writeTIPSV(tiOutEvent.resolve(header + "_PSV.inf"));
            info.writeTISH(tiOutEvent.resolve(header + "_SH.inf"));
            info.writeISOPSV(isoOutEvent.resolve(header + "_PSV.inf"));
            info.writeISOSH(isoOutEvent.resolve(header + "_SH.inf"));
        }
        // output shellscripts for execution of tipsv and tish
        //TODO PAR2の場合とPAR5の場合で場合分け
        String listFileName = "sourceList.txt";
        Files.write(outPath.resolve(listFileName), sourceList);
        DSMShellscript shell = new DSMShellscript(outPath, mpi, eventDirs.size(), header);
        String dateStr = GadgetAid.getTemporaryString();
        Path outISOPSVPath = outPath.resolve(DatasetAid.generateOutputFileName("runSSHi_PSV", null, dateStr, ".sh"));
        Path outISOSHPath = outPath.resolve(DatasetAid.generateOutputFileName("runSSHi_SH", null, dateStr, ".sh"));
        Path outTIPSVPath = outPath.resolve(DatasetAid.generateOutputFileName("runSSH_PSV", null, dateStr, ".sh"));
        Path outTISHPath = outPath.resolve(DatasetAid.generateOutputFileName("runSSH_SH", null, dateStr, ".sh"));
        shell.write(SPCType.PAR2, SPCMode.PSV, listFileName, outISOPSVPath);
        shell.write(SPCType.PAR2, SPCMode.SH, listFileName, outISOSHPath);
        shell.write(SPCType.PAR5, SPCMode.PSV, listFileName, outTIPSVPath);
        shell.write(SPCType.PAR5, SPCMode.SH, listFileName, outTISHPath);
        System.err.println("After this finishes, please run " + outPath + "/runDSM_PSV.sh and " + outPath + "/runDSM_SH.sh");
    }
}
