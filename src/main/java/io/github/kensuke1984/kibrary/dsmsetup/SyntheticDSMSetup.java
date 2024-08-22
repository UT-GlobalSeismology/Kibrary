package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;

/**
 * Operation that generates DSM input files to be used in tish and tipsv,
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
 * @author ?
 * @since a long time ago
 * @version 2021/11/2 renamed from SyntheticDSMInformationFileMaker
 */
public class SyntheticDSMSetup extends Operation {

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
     * Name root of input file for DSM (header_[SH,PSV].inf).
     */
    private String header;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of a data entry list file.
     */
    private Path dataEntryPath;
    /**
     * The root folder containing event folders which have observed SAC files.
     */
    private Path obsPath;
    /**
     * Path of structure file to use instead of PREM.
     */
    private Path structurePath;
    /**
     * Structure to use.
     */
    private String structureName;

    /**
     * Time length [s], must be a power of 2 divided by 10. (2<sup>n</sup>/10)
     */
    private double tlen;
    /**
     * Number of steps in frequency domain, must be a power of 2.
     */
    private int np;
    /**
     * Whether to use MPI-version of DSM in shellscript file.
     */
    private boolean mpi;

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
            pw.println("##(String) Header for names of output files (as in header_[SH,PSV].inf). (PREM)");
            pw.println("#header ");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of an entry list file. If this is unset, the following obsPath will be used.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use. (PREM)");
            pw.println("#structureName ");
            pw.println("##Time length to be computed, must be a power of 2 over 10. (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, must be a power of 2. (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM computations. (true)");
            pw.println("#mpi false");
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
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
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
        mpi = property.parseBoolean("mpi", "true");
    }

    @Override
    public void run() throws IOException {
        String dateString = GadgetAid.getTemporaryString();

        // create set of events and observers to set up DSM for
        Map<GlobalCMTID, Set<Observer>> arcMap = DatasetAid.setupArcMapFromFileOrFolder(dataEntryPath, obsPath, components);
        if (!DatasetAid.checkNum(arcMap.size(), "event", "events")) return;

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        Path outPath = DatasetAid.createOutputFolder(workPath, "synthetic", folderTag, appendFolderDate, dateString);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

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
                if (observers.isEmpty())
                    continue;

                // in the same event folder, observers with the same name should have same position
                int numberOfObserver = (int) observers.stream().map(Observer::toString).distinct().count();
                if (numberOfObserver != observers.size())
                    System.err.println("! Caution: There are observers with the same name and different position for " + event);

                SyntheticDSMInputFile info = new SyntheticDSMInputFile(structure, event.getEventData(), observers, header, tlen, np);
                Path outEventPath = outPath.resolve(event.toString());
                Files.createDirectories(outEventPath.resolve(header));
                info.writeSH(outEventPath.resolve(header + "_SH.inf"));
                info.writePSV(outEventPath.resolve(header + "_PSV.inf"));
                sourceTreeSet.add(event.toString());
            } catch (IOException e) {
                // If there are any problems, move on to the next event.
                System.err.println("Error on " + event);
                e.printStackTrace();
            }
        }

        // output shellscripts for execution of tipsv and tish
        String listFileName = "sourceList.txt";
        Files.write(outPath.resolve(listFileName), sourceTreeSet);
        DSMShellscript shell = new DSMShellscript(mpi, arcMap.size(), header);
        Path outSHPath = DatasetAid.generateOutputFilePath(outPath, "runDSM_SH", null, false, dateString, ".sh");
        Path outPSVPath = DatasetAid.generateOutputFilePath(outPath, "runDSM_PSV", null, false, dateString, ".sh");
        shell.write(DSMShellscript.DSMType.SYNTHETIC, SPCMode.SH, listFileName, outSHPath);
        shell.write(DSMShellscript.DSMType.SYNTHETIC, SPCMode.PSV, listFileName, outPSVPath);
        System.err.println("After this finishes, please enter " + outPath + "/ and run "
                + outSHPath.getFileName() + " and " + outPSVPath.getFileName());
    }

}
