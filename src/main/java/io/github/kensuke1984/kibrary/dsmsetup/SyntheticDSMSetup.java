package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalog;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Operation that makes DSM input files to be used in tish and tipsv,
 * and prepares the environment to run these programs.
 * DSM input files can be made either for existing observed dataset in event folders, for a data entry list file,
 * or for a virtual set of observers.
 *
 * <p>
 * If a dataset folder is assigned as input, SAC files that satisfy the following criteria will be chosen:
 * <ul>
 * <li> is observed waveform </li>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * Then, the DSM input files will be created for the (event, observer) pairs that have been chosen.
 * If there is no valid data in a certain input event folder, the corresponding output event folder will not be created.
 *
 * <p>
 * If a data entry list file is assigned as input, entries that satisfy the following criteria will be chosen:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * Then, the DSM input files will be created for the (event, observer) pairs that have been chosen.
 *
 * <p>
 * For virtual datasets, virtual observers will be made in 1-degree intervals.
 * They will have the network name specified in {@link Observer#SYN}.
 *
 * @since a long time ago
 * @version 2021/11/2 renamed from SyntheticDSMInformationFileMaker
 */
public class SyntheticDSMSetup extends Operation {

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
     * Information file name is header_[psv,sh].inf (default:PREM)
     */
    private String header;
    /**
     * components to be used
     */
    private Set<SACComponent> components;

    /**
     * The data entry list file
     */
    private Path entryPath;
    /**
     * The root folder containing event folders which have observed SAC files
     */
    private Path obsPath;
    /**
     * Structure file to use instead of PREM
     */
    private Path structurePath;
    /**
     * Structure to use
     */
    private String structureName;

    /**
     * Time length [s].
     * It must be a power of 2 divided by 10.(2<sup>n</sup>/10)
     */
    private double tlen;
    /**
     * Number of steps in frequency domain.
     * It must be a power of 2.
     */
    private int np;
    /**
     * Whether to use MPI-version of DSM in shellscript file
     */
    private boolean mpi;

    /**
     * whether a set of virtual observers are to be created
     */
    private boolean syntheticDataset;
    /**
     * minimum epicentral distance of virtual observers
     */
    private int synMinDistance;
    /**
     * maximum epicentral distance of virtual observers
     */
    private int synMaxDistance;

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
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(String) Header for names of output files (as in header_[psv, sh].inf) (PREM)");
            pw.println("#header ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of an entry list file. If this is unset, the following obsPath will be used.");
            pw.println("#entryPath ");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
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
            pw.println("##(boolean) If a virtual set of observers is to be created (false)");
            pw.println("#syntheticDataset ");
            pw.println("##Minimum epicentral distance of virtual observer, must be integer (1)");
            pw.println("#synMinDistance ");
            pw.println("##Maximum epicentral distance of virtual observer, must be integer (170)");
            pw.println("#synMaxDistance ");
        }
        System.err.println(outPath + " is created.");
    }

    public SyntheticDSMSetup(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        header = property.parseStringSingle("header", "PREM");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        if (property.containsKey("entryPath")) {
            entryPath = property.parsePath("entryPath", null, true, workPath);
        } else {
            obsPath = property.parsePath("obsPath", ".", true, workPath);
        }
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        mpi = property.parseBoolean("mpi", "true");

        syntheticDataset = property.parseBoolean("syntheticDataset", "false");
        synMinDistance = property.parseInt("synMinDistance", "1");
        synMaxDistance = property.parseInt("synMaxDistance", "170");
        if (synMinDistance < 0 || synMinDistance > synMaxDistance || 360 < synMaxDistance)
            throw new IllegalArgumentException("Distance range " + synMinDistance + " , " + synMaxDistance + " is invalid.");

        // write additional info
        property.setProperty("CMTcatalogue", GlobalCMTCatalog.getCatalogPath().toString());

    }

    /**
     * @author Kensuke Konishi
     * @author anselme add models, options for synthetic/specfem dataset, ...
     */
    @Override
    public void run() throws IOException {
        String dateStr = GadgetAid.getTemporaryString();

        // create set of events and observers to set up DSM for
        Map<GlobalCMTID, Set<Observer>> arcMap = DatasetAid.setupArcMapFromFileOrFolder(entryPath, obsPath, components);
        if (!DatasetAid.checkNum(arcMap.size(), "event", "events")) return;

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        Path outPath = DatasetAid.createOutputFolder(workPath, "synthetic", folderTag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // synthetic observer set
        Set<Observer> synObserverSet = new HashSet<>();
        if (syntheticDataset) {
            for (int i = synMinDistance; i <= synMaxDistance; i+=1) {
                double distance = i;
                String stationName = String.format("%03d", i);
                Observer observer = new Observer(stationName
                        , Observer.SYN, new HorizontalPosition(0, distance));
                synObserverSet.add(observer);
            }
        }

        // output information files in each event folder
        List<String> sourceList = new ArrayList<>();
        for (GlobalCMTID event : arcMap.keySet()) {
            try {
                Set<Observer> observers = arcMap.get(event);
                if (syntheticDataset)
                    observers = synObserverSet;
                if (observers.isEmpty())
                    continue;

                // in the same event folder, observers with the same name should have same position
                int numberOfObserver = (int) observers.stream().map(Observer::toString).count();
                if (numberOfObserver != observers.size())
                    System.err.println("!Caution there are observers with the same name and different position for " + event);

                Path eventOut = outPath.resolve(event.toString());

                if (event.getEventData() != null) {
                    SyntheticDSMInputFile info = new SyntheticDSMInputFile(structure, event.getEventData(), observers, header, tlen, np);
                    Files.createDirectories(eventOut.resolve(header));
                    info.writePSV(eventOut.resolve(header + "_PSV.inf"));
                    info.writeSH(eventOut.resolve(header + "_SH.inf"));
                    sourceList.add(event.toString());
                } else {
                    System.err.println(event + "is not in the catalog");
                }
            } catch (IOException e) {
                // If there are any problems, move on to the next event.
                System.err.println("Error on " + event);
                e.printStackTrace();
            }
        }

        // output shellscripts for execution of tipsv and tish
        String listFileName = "sourceList.txt";
        Files.write(outPath.resolve(listFileName), sourceList);
        DSMShellscript shell = new DSMShellscript(outPath, mpi, arcMap.size(), header);
        Path outPSVPath = outPath.resolve(DatasetAid.generateOutputFileName("runDSM_PSV", null, dateStr, ".sh"));
        Path outSHPath = outPath.resolve(DatasetAid.generateOutputFileName("runDSM_SH", null, dateStr, ".sh"));
        shell.write(SPCType.SYNTHETIC, SPCMode.PSV, listFileName, outPSVPath);
        shell.write(SPCType.SYNTHETIC, SPCMode.SH, listFileName, outSHPath);
        System.err.println("After this finishes, please run " + outPSVPath + " and " + outSHPath);
    }

}
