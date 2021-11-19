package io.github.kensuke1984.kibrary.dsmsetup;

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
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.Utilities;
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
public class SyntheticDSMSetup implements Operation {

    private final Properties property;
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
    private Path timewindowPath;
    private boolean usewindow;

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

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths
                .get(SyntheticDSMSetup.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan SyntheticDSMInformationFileMaker");
            pw.println("##SacComponents that observers must have to be used (Z R T)");
            pw.println("#components");
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath");
            pw.println("##Header for names of information files, header_[psv, sh].inf, (PREM)");
            pw.println("#header");
            pw.println("##Path of a structure file you want to use. ()");
            pw.println("#structureFile");
            pw.println("##Time length to be calculated, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen");
            pw.println("##Number of points to be calculated in frequency domain, must be a power of 2 (512)");
            pw.println("#np");
            pw.println("##(boolean) If a virtual set of observers is to be created (false)");
            pw.println("#syntheticDataset");
            pw.println("##Minimum epicentral distance of virtual observer, must be integer (1)");
            pw.println("#synMinDistance");
            pw.println("##Maximum epicentral distance of virtual observer, must be integer (170)");
            pw.println("#synMaxDistance");
            pw.println("##SPECFEM3D_GLOBE test dataset (false)");
            pw.println("#specfemDataset");
            pw.println("#timewindowPath");
        }
        System.err.println(outPath + " is created.");
    }

    private SyntheticDSMSetup(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("obsPath")) property.setProperty("obsPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("tlen")) property.setProperty("tlen", "3276.8");
        if (!property.containsKey("np")) property.setProperty("np", "512");
        if (!property.containsKey("header")) property.setProperty("header", "PREM");
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
        np = Integer.parseInt(property.getProperty("np").split("\\s+")[0]);
        tlen = Double.parseDouble(property.getProperty("tlen").split("\\s+")[0]);
        header = property.getProperty("header").split("\\s+")[0];
        if (property.containsKey("structureFile"))
            structurePath = Paths.get(property.getProperty("structureFile").split("\\s+")[0]);
        else
            structurePath = Paths.get("PREM");

        syntheticDataset = Boolean.parseBoolean(property.getProperty("syntheticDataset"));
        synMinDistance = Integer.parseInt(property.getProperty("synMinDistance"));
        synMaxDistance = Integer.parseInt(property.getProperty("synMaxDistance"));
        specfemDataset = Boolean.parseBoolean(property.getProperty("specfemDataset"));

        usewindow = property.getProperty("timewindowPath") != "";
        timewindowPath = Paths.get(property.getProperty("timewindowPath"));
    }

    /**
     * @param args [parameter file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        SyntheticDSMSetup sdsms = new SyntheticDSMSetup(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(SyntheticDSMSetup.class.getName() + " is operating.");
        sdsms.run();
        System.err.println(SyntheticDSMSetup.class.getName() + " finished in " +
                Utilities.toTimeString(System.nanoTime() - startTime));
    }

    /**
     * @author Kensuke Konishi
     * @author anselme add models, options for synthetic/specfem dataset, ...
     */
    @Override
    public void run() throws IOException {
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(obsPath);
        String modelName = structurePath.toString().trim().toUpperCase();
        PolynomialStructure ps = null;

        //use only events in timewindow file
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

        Path outPath = workPath.resolve("synthetic" + Utilities.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (property != null)
            writeProperties(outPath.resolve("dsmifm.properties"));

        //synthetic station set
        Set<Observer> synObserverSet = new HashSet<>();
        if (syntheticDataset) {
            for (int i = synMinDistance; i <= synMaxDistance; i+=1) {
                double distance = i;
                String stationName = String.format("%03d", i);
                Observer observer = new Observer(stationName
                        , new HorizontalPosition(0, distance), Observer.SYN);
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
