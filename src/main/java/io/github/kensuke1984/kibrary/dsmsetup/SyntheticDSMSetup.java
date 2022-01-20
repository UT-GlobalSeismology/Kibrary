package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import io.github.kensuke1984.kibrary.Operation_new;
import io.github.kensuke1984.kibrary.Property_new;
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
public class SyntheticDSMSetup extends Operation_new {

    private final Property_new property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The root folder containing event folders which have observed SAC files
     */
    private Path obsPath;

    /**
     * Number of steps in frequency domain.
     * It must be a power of 2.
     */
    private int np;
    /**
     * Time length [s].
     * It must be a power of 2 divided by 10.(2<sup>n</sup>/10)
     */
    private double tlen;
    /**
     * components to be used
     */
    private Set<SACComponent> components;
    /**
     * Information file name is header_[psv,sh].inf (default:PREM)
     */
    private String header;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    /**
     * Whether to use only events in a timewindow file
     */
    private boolean usewindow;
    private Path timewindowPath;

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
        else Operation_new.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property_new.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##(String) Header for names of output files (as in header_[psv, sh].inf) (PREM)");
            pw.println("#header");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath");
            pw.println("##Path of a structure file you want to use. If this is unset, PREM will be used.");
            pw.println("#structurePath");
            pw.println("##Time length to be calculated, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen");
            pw.println("##Number of points to be calculated in frequency domain, must be a power of 2 (512)");
            pw.println("#np");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM calculations (true)");
            pw.println("#mpi");
            pw.println("##(boolean) If a virtual set of observers is to be created (false)");
            pw.println("#syntheticDataset");
            pw.println("##Minimum epicentral distance of virtual observer, must be integer (1)");
            pw.println("#synMinDistance");
            pw.println("##Maximum epicentral distance of virtual observer, must be integer (170)");
            pw.println("#synMaxDistance");
            pw.println("##SPECFEM3D_GLOBE test dataset (false)");
            pw.println("#specfemDataset");
            pw.println("##To use only events in a timewindow file, set its path");
            pw.println("#timewindowPath NOT SUPPORTED YET");
        }
        System.err.println(outPath + " is created.");
    }

    public SyntheticDSMSetup(Property_new property) throws IOException {
        this.property = (Property_new) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", "", true, Paths.get(""));
        header = property.parseString("header", "PREM").split("\\s+")[0];
        components = Arrays.stream(property.parseString("components", "Z R T")
                .split("\\s+")).map(SACComponent::valueOf).collect(Collectors.toSet());

        obsPath = property.parsePath("obsPath", "", true, workPath);

        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structurePath = Paths.get("PREM");
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        mpi = property.parseBoolean("mpi", "true");

        syntheticDataset = property.parseBoolean("syntheticDataset", "false");
        synMinDistance = property.parseInt("synMinDistance", "1");
        synMaxDistance = property.parseInt("synMaxDistance", "170");
        specfemDataset = property.parseBoolean("specfemDataset", "false");

        if (usewindow = property.containsKey("timewindowPath")) {
            timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        }

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

        String modelName = structurePath.toString().trim().toUpperCase();
        PolynomialStructure ps = null;

        //use only events in timewindow file TODO
        if (usewindow) {
            Set<GlobalCMTID> idInWindow =
                    TimewindowDataFile.read(timewindowPath).stream()
                    .map(tw -> tw.getGlobalCMTID()).collect(Collectors.toSet());
        }

        // PREM_3600_RHO_3 : PREM is a 3% rho (density) discontinuity at radius 3600 km
        if (!modelName.contains("/") && modelName.contains("_")) {
            System.err.println("Using " + modelName + ". Adding perturbations");
            String[] ss = modelName.split("_");
            modelName = ss[0];
            String[] range = ss[1].split("-");
            double r1 = Double.parseDouble(range[0]);
            double r2 = Double.parseDouble(range[1]);
            Map<String, Double> quantityPercentMap = new HashMap<>();
            for (int i = 2; i < ss.length; i++) {
                String[] quantity_percent = ss[i].split("-");
                double percent = quantity_percent[1].startsWith("M") ? -1 * Double.parseDouble(quantity_percent[1].substring(1)) / 100.
                        : Double.parseDouble(quantity_percent[1]) / 100.;
                quantityPercentMap.put(quantity_percent[0], percent);
            }
            if (modelName.equals("MIASP91")) {
                ps = PolynomialStructure.MIASP91;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else if (modelName.equals("PREM")) {
                ps = PolynomialStructure.PREM;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else if (modelName.equals("AK135")) {
                ps = PolynomialStructure.AK135;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else
                throw new RuntimeException("Model not implemented yet");
        }
        else {
            switch (modelName) {
            case "PREM":
                System.err.println("Using PREM");
                ps = PolynomialStructure.PREM;
                break;
            case "AK135":
                System.err.println("Using AK135");
                ps = PolynomialStructure.AK135;
                break;
            case "AK135-ELASTIC":
                System.err.println("Using AK135 elastic");
                ps = PolynomialStructure.AK135_elastic;
                break;
            case "MIASP91":
                System.err.println("Using MIASP91");
                ps = PolynomialStructure.MIASP91;
                break;
            case "IPREM":
                System.err.println("Using IPREM");
                ps = PolynomialStructure.ISO_PREM;
                break;
            case "TNASNA":
                System.err.println("Using TNASNA");
                ps = PolynomialStructure.TNASNA;
                break;
            case "TBL50":
                System.err.println("Using TBL50");
                ps = PolynomialStructure.TBL50;
                break;
            case "MAK135":
                System.err.println("Using MAK135");
                ps = PolynomialStructure.MAK135;
                break;
            default:
                System.err.println("Using " + structurePath);
                ps = new PolynomialStructure(structurePath);
            }
        }

        Path outPath = workPath.resolve("synthetic" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (property != null)
            property.write(outPath.resolve("dsmifm.properties"));

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
                SyntheticDSMInputFile info = new SyntheticDSMInputFile(ps, id, specfemObserverSet, header, tlen, np);
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
                    SyntheticDSMInputFile info = new SyntheticDSMInputFile(ps, eventDir.getGlobalCMTID().getEvent(), observers, header, tlen, np);
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
        DSMShellscript shell = new DSMShellscript(outPath, mpi, eventDirs.size());
        shell.writePSV();
        shell.writeSH();
    }

}
