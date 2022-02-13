package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalog;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Operation that makes DSM input files to be used in tish and tipsv,
 * and prepares the environment to run these programs.
 * Input files can be made either for existing observed dataset in event folders, or for a virtual set of observers.
 * <p>
 * Input event folders must be inside one certain input directory, which may or may not be the workpath.
 * Only observers that have files of the specified components will be considered.
 * If there is no valid data in a certain input event directory, the corresponding output event directory will not be made.
 * <p>
 * For virtual datasets, virtual observers will be made in 1-degree intervals.
 * They will have the network name specified in {@link Observer#SYN}.
 */
public class SyntheticDSMSetup extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * Information file name is header_[psv,sh].inf (default:PREM)
     */
    private String header;
    /**
     * components to be used
     */
    private Set<SACComponent> components;

    /**
     * The root folder containing event folders which have observed SAC files
     */
    private Path obsPath;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;
    /**
     * Whether to use only events in a timewindow file
     */
    private boolean usewindow;
    private Path timewindowPath;

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
    private boolean specfemDataset;

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
            pw.println("##(String) Header for names of output files (as in header_[psv, sh].inf) (PREM)");
            pw.println("#header ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##To use only events in a timewindow file, set its path");
            pw.println("#timewindowPath NOT SUPPORTED YET");
            pw.println("##Time length to be calculated, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be calculated in frequency domain, must be a power of 2 (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM calculations (true)");
            pw.println("#mpi ");
            pw.println("##(boolean) If a virtual set of observers is to be created (false)");
            pw.println("#syntheticDataset ");
            pw.println("##Minimum epicentral distance of virtual observer, must be integer (1)");
            pw.println("#synMinDistance ");
            pw.println("##Maximum epicentral distance of virtual observer, must be integer (170)");
            pw.println("#synMaxDistance ");
            pw.println("##SPECFEM3D_GLOBE test dataset (false)");
            pw.println("#specfemDataset ");
        }
        System.err.println(outPath + " is created.");
    }

    public SyntheticDSMSetup(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        header = property.parseString("header", "PREM").split("\\s+")[0];
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        obsPath = property.parsePath("obsPath", ".", true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }
        if (usewindow = property.containsKey("timewindowPath")) {  // This "=" is not a mistake, calm down.
            timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        mpi = property.parseBoolean("mpi", "true");

        syntheticDataset = property.parseBoolean("syntheticDataset", "false");
        synMinDistance = property.parseInt("synMinDistance", "1");
        synMaxDistance = property.parseInt("synMaxDistance", "170");
        if (synMinDistance < 0 || synMinDistance > synMaxDistance || 360 < synMaxDistance)
            throw new IllegalArgumentException("Distance range " + synMinDistance + " , " + synMaxDistance + " is invalid.");

        specfemDataset = property.parseBoolean("specfemDataset", "false");

        // write additional info
        property.setProperty("CMTcatalogue", GlobalCMTCatalog.getCatalogPath().toString());

    }
/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("obsPath")) property.setProperty("obsPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("header")) property.setProperty("header", "PREM");
        if (!property.containsKey("tlen")) property.setProperty("tlen", "3276.8");
        if (!property.containsKey("np")) property.setProperty("np", "512");
        if (!property.containsKey("mpi")) property.setProperty("mpi", "true");
        if (!property.containsKey("syntheticDataset")) property.setProperty("syntheticDataset", "false");
        if (!property.containsKey("synMinDistance")) property.setProperty("synMinDistance", "1");
        if (!property.containsKey("synMaxDistance")) property.setProperty("synMaxDistance", "170");
        if (!property.containsKey("specfemDataset")) property.setProperty("specfemDataset", "false");
        if (!property.containsKey("timewindowPath")) property.setProperty("timewindowPath", "");
        // write additional info
        property.setProperty("CMTcatalogue", GlobalCMTCatalog.getCatalogPath().toString());
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        obsPath = getPath("obsPath");
        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        header = property.getProperty("header").split("\\s+")[0];
        if (property.containsKey("structureFile"))
            structurePath = Paths.get(property.getProperty("structureFile").split("\\s+")[0]);
        else
            structurePath = Paths.get("PREM");
        tlen = Double.parseDouble(property.getProperty("tlen").split("\\s+")[0]);
        np = Integer.parseInt(property.getProperty("np").split("\\s+")[0]);
        mpi = Boolean.parseBoolean(property.getProperty("mpi"));

        syntheticDataset = Boolean.parseBoolean(property.getProperty("syntheticDataset"));
        synMinDistance = Integer.parseInt(property.getProperty("synMinDistance"));
        synMaxDistance = Integer.parseInt(property.getProperty("synMaxDistance"));
        specfemDataset = Boolean.parseBoolean(property.getProperty("specfemDataset"));

        usewindow = property.getProperty("timewindowPath") != "";
        timewindowPath = Paths.get(property.getProperty("timewindowPath"));
    }
*/

    /**
     * @author Kensuke Konishi
     * @author anselme add models, options for synthetic/specfem dataset, ...
     */
    @Override
    public void run() throws IOException {
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(obsPath);
        if (!DatasetAid.checkEventNum(eventDirs.size())) {
            return;
        }

        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = new PolynomialStructure(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        //use only events in timewindow file TODO
        if (usewindow) {
            Set<GlobalCMTID> idInWindow =
                    TimewindowDataFile.read(timewindowPath).stream()
                    .map(tw -> tw.getGlobalCMTID()).collect(Collectors.toSet());
        }


        Path outPath = workPath.resolve("synthetic" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (property != null)
            property.write(outPath.resolve("syndsm.properties"));

        //synthetic station set
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

        //specfem test dataset
        if (specfemDataset) {
            Set<Observer> specfemObserverSet = IOUtils.readLines(SyntheticDSMSetup.class.getClassLoader()
                    .getResourceAsStream("specfem_stations.inf"), Charset.defaultCharset())
                .stream().map(s -> Observer.createObserver(s)).collect(Collectors.toSet());
            try {
                GlobalCMTAccess id = new GlobalCMTID("060994A").getEvent();
                Path eventOut = outPath.resolve(id.toString());
                SyntheticDSMInputFile info = new SyntheticDSMInputFile(structure, id, specfemObserverSet, header, tlen, np);
                Files.createDirectories(eventOut.resolve(header));
                info.writePSV(eventOut.resolve(header + "_PSV.inf"));
                info.writeSH(eventOut.resolve(header + "_SH.inf"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // output information files in each event directory
        for (EventFolder eventDir : eventDirs) {
            try {
                Set<Observer> observers = eventDir.sacFileSet().stream()
                        .filter(name -> name.isOBS() && components.contains(name.getComponent())).map(name -> {
                            try {
                                return name.readHeader();
                            } catch (Exception e2) {
                                return null;
                            }
                        }).filter(Objects::nonNull).map(Observer::of).collect(Collectors.toSet());
                if (syntheticDataset)
                    observers = synObserverSet;
                if (observers.isEmpty())
                    continue;

                // in the same event folder, observers with the same name should have same position
                int numberOfObserver = (int) observers.stream().map(Observer::toString).count();
                if (numberOfObserver != observers.size())
                    System.err.println("!Caution there are observers with the same name and different positions in "
                            + eventDir);

                Path eventOut = outPath.resolve(eventDir.toString());

                if (eventDir.getGlobalCMTID().getEvent() != null) {
                    SyntheticDSMInputFile info = new SyntheticDSMInputFile(structure, eventDir.getGlobalCMTID().getEvent(), observers, header, tlen, np);
                    Files.createDirectories(eventOut.resolve(header));
                    info.writePSV(eventOut.resolve(header + "_PSV.inf"));
                    info.writeSH(eventOut.resolve(header + "_SH.inf"));
                }
                else {
                    System.err.println(eventDir.getGlobalCMTID() + "is not in the catalog");
                }
            } catch (IOException e) {
                // If there are any problems, move on to the next event.
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            }
        }

        // output shellscripts for execution of tipsv and tish
        DSMShellscript shell = new DSMShellscript(outPath, mpi, eventDirs.size(), header);
        shell.write(SPCType.SYNTHETIC, SPCMode.PSV);
        shell.write(SPCType.SYNTHETIC, SPCMode.SH);
    }

}
