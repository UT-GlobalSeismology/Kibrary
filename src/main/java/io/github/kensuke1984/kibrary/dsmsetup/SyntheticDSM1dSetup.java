package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

/**
 * Operation that generates DSM1d input files and pertirbed model files to be used in tish1d and tipsv1d.
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
 * <p>
 * model files will be made in each event folder. In model files, number of zones and grides must be defined.
 * The absolute perturbation value of density and elastic constances must be listed in each grid.
 *
 * @since 2023/10/23
 * @auther rei
 */
public class SyntheticDSM1dSetup extends Operation {

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
     * Information file name is header_[sh,psv].inf (default:PREM)
     */
    private String header;
    /**
     * components to be used
     */
    private Set<SACComponent> components;

    /**
     * Path of a data entry list file
     */
    private Path dataEntryPath;
    /**
     * The root folder containing event folders which have observed SAC files
     */
    private Path obsPath;
    /**
     * Path of structure file to use instead of PREM
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
     * Number of zones to be perturbed
     */
    private int nzone;
    /**
     * Maximum radius of each zone
     */
    private double[] rmax;
    /**
     * Minimum radius of each zone
     */
    private double[] rmin;
    /**
     * Number of grids to list the value of perturbation
     */
    private int[] ngrid;
    /**
     * summation of all grids
     */
    private int sumGrid;
    /**
     * Perturbation of parameters in each grid
     */
    private double[] radius;
    private double[] perRho;
    private double[] perA;
    private double[] perC;
    private double[] perF;
    private double[] perL;
    private double[] perN;


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
            pw.println("##(String) Header for names of output files (as in header_[sh,psv].inf) (PREM)");
            pw.println("#header ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of an entry list file. If this is unset, the following obsPath will be used.");
            pw.println("#dataEntryPath dataEntry.lst");
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
            pw.println("##(boolean) If a virtual set of observers is to be created (false)");
            pw.println("#syntheticDataset ");
            pw.println("##Minimum epicentral distance of virtual observer, must be integer (1)");
            pw.println("#synMinDistance ");
            pw.println("##Maximum epicentral distance of virtual observer, must be integer (170)");
            pw.println("#synMaxDistance ");
            pw.println("##########Parameters for DSM1d model files");
            pw.println("##Number of zones to be perturbed, must be integer (1)");
            pw.println("#nzone");
            pw.println("##Maximum radius of each zone, listed NZONE values using spaces, must be set.");
            pw.println("#rmax");
            pw.println("##Minimum radius of each zone, listed NZONE values using spaces, must be set.");
            pw.println("#rmin");
            pw.println("##Number of grids to list the value of perturbation, must be integer, listed NZONE values using spaces, must be set.");
            pw.println("#ngrid");
            pw.println("##Radius of each grid, listed NZONE * NGRID values using spaces, must be set.");
            pw.println("#radius");
            pw.println("##Perturbation of rho in each grid (g/cm^3), listed NZONE * NGRID values using spaces. If this is unset, rho is NOT perturbed.");
            pw.println("#perRho");
            pw.println("##Perturbation of A in each grid (GPa), listed NZONE * NGRID values using spaces. If this is unset, A is NOT perturbed.");
            pw.println("#perA");
            pw.println("##Perturbation of C in each grid (GPa), listed NZONE * NGRID values using spaces. If this is unset, C is NOT perturbed.");
            pw.println("#perC");
            pw.println("##Perturbation of F in each grid (GPa), listed NZONE * NGRID values using spaces. If this is unset, F is NOT perturbed.");
            pw.println("#perF");
            pw.println("##Perturbation of L in each grid (GPa), listed NZONE * NGRID values using spaces. If this is unset, L is NOT perturbed.");
            pw.println("#perL");
            pw.println("##Perturbation of N in each grid (GPa), listed NZONE * NGRID values using spaces. If this is unset, N is NOT perturbed.");
            pw.println("#perN");
        }
        System.err.println(outPath + " is created.");
    }

    public SyntheticDSM1dSetup(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        header = property.parseStringSingle("header", "PREM");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
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

        syntheticDataset = property.parseBoolean("syntheticDataset", "false");
        synMinDistance = property.parseInt("synMinDistance", "1");
        synMaxDistance = property.parseInt("synMaxDistance", "170");
        if (synMinDistance < 0 || synMinDistance > synMaxDistance || 360 < synMaxDistance)
            throw new IllegalArgumentException("Distance range " + synMinDistance + " , " + synMaxDistance + " is invalid.");

        nzone = property.parseInt("nzone", "1");
        rmax = property.parseDoubleArray("rmax", null);
        rmin = property.parseDoubleArray("rmin", null);
        rmax = property.parseDoubleArray("rmax", null);
        ngrid = property.parseIntArray("ngrid", null);
        if (rmax.length != nzone || rmin.length != nzone || ngrid.length != nzone)
            throw new IllegalArgumentException("the Number of ramx, rmin, and ngrid must be " + nzone);
        sumGrid = 0;
        for (int n: ngrid)
            sumGrid += n;
        radius = property.parseDoubleArray("radius", null);
        if (property.containsKey("perRho"))
            perRho = property.parseDoubleArray("perRho", null);
        else
            perRho = zeroArray(sumGrid);
        if (property.containsKey("perA"))
            perA = property.parseDoubleArray("perA", null);
        else
            perA = zeroArray(sumGrid);
        if (property.containsKey("perC"))
            perC = property.parseDoubleArray("perC", null);
        else
            perC = zeroArray(sumGrid);
        if (property.containsKey("perF"))
            perF = property.parseDoubleArray("perF", null);
        else
            perF = zeroArray(sumGrid);
        if (property.containsKey("perL"))
            perL = property.parseDoubleArray("perL", null);
        else
            perL = zeroArray(sumGrid);
        if (property.containsKey("perN"))
            perN = property.parseDoubleArray("perN", null);
        else
            perN = zeroArray(sumGrid);
        if (radius.length != sumGrid || perRho.length != sumGrid || perA.length != sumGrid || perC.length != sumGrid
                || perF.length != sumGrid || perL.length != sumGrid || perN.length != sumGrid)
            throw new IllegalArgumentException("the Number of radius and pertirbations of parameters must be " + nzone);

        // write additional info
        property.setProperty("CMTcatalogue", GlobalCMTCatalog.getCatalogPath().toString());
    }

    @Override
    public void run() throws IOException {
        String dateStr = GadgetAid.getTemporaryString();

        // create set of events and observers to set up DSM for
        Map<GlobalCMTID, Set<Observer>> arcMap = DatasetAid.setupArcMapFromFileOrFolder(dataEntryPath, obsPath, components);
        if (!DatasetAid.checkNum(arcMap.size(), "event", "events")) return;

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        Path outPath = DatasetAid.createOutputFolder(workPath, "synthetic1d", folderTag, dateStr);
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
        // TreeSet is used here to sort sources in the sourceList file
        Set<String> sourceTreeSet = new TreeSet<>();
        for (GlobalCMTID event : arcMap.keySet()) {
            try {
                if (event.getEventData() == null) {
                    System.err.println(event + "is not in the catalog");
                    continue;
                }
                Set<Observer> observers = arcMap.get(event);
                if (syntheticDataset)
                    observers = synObserverSet;
                if (observers.isEmpty())
                    continue;

                // in the same event folder, observers with the same name should have same position
                int numberOfObserver = (int) observers.stream().map(Observer::toString).count();
                if (numberOfObserver != observers.size())
                    System.err.println("!Caution there are observers with the same name and different position for " + event);

                SyntheticDSMInputFile info = new SyntheticDSMInputFile(structure, event.getEventData(), observers, header, tlen, np, true);
                Path outEventPath = outPath.resolve(event.toString());
                Files.createDirectories(outEventPath.resolve(header));
                info.writeSH(outEventPath.resolve(header + "_SH.inf"));
                info.writePSV(outEventPath.resolve(header + "_PSV.inf"));
                writeModelSH(outEventPath.resolve("model_SH.inf"));
                writeModelPSV(outEventPath.resolve("model_PSV.inf"));
                sourceTreeSet.add(event.toString());
            } catch (IOException e) {
                // If there are any problems, move on to the next event.
                System.err.println("Error on " + event);
                e.printStackTrace();
            }
        }

        System.err.println("After this finishes, please enter " + outPath + " and run DSM1d");
    }

    private double[] zeroArray(int size) {
        double[] array = new double[size];
        for (int i = 0; i < size; i++) {
            array[i] = 0.0;
        }
        return array;
    }

    private void writeModelSH(Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("c nzone");
            pw.println(nzone);
            pw.println("c ngrid rmin rmax");
            for (int i = 0; i < nzone; i++) {
                pw.println(ngrid + " " + rmin + " " + rmax);
            }
            pw.println("c radius(km), rho(g/cm^3), L, N(GPa)");
            for (int i = 0; i < sumGrid; i++) {
                pw.println(radius[i] + " " + perRho[i] + " " + perL[i] + " " + perN[i]);
            }
            pw.println("end");
        }
    }

    private void writeModelPSV(Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("c nzone");
            pw.println(nzone);
            pw.println("c ngrid rmin rmax");
            for (int i = 0; i < nzone; i++) {
                pw.println(ngrid[i] + " " + rmin[i] + " " + rmax[i]);
            }
            pw.println("c radius(km), rho(g/cm^3),A,C,F,L,N(GPa)");
            for (int i = 0; i < sumGrid; i++) {
                pw.println(radius[i] + " " + perRho[i] + " " + perA[i] + " " + perC[i]
                        + " " + perF[i] + " " + perL[i] + " " + perN[i]);
            }
            pw.println("end");
        }
    }
}