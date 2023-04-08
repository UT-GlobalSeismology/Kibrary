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
import java.util.Map;
import java.util.Set;
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
import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Information file for SSHSH and SSHPSV
 *
 * @author Kensuke Konishi
 * @since version 0.1.3
 * @version 2021/12/24 renamed from SshDSMInformationFileMaker to OneDPartialDSMSetup
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
    private String folderTag;
    /**
     * Information file name is header_[sh,psv].inf (default:PREM)
     */
    private String header;
    /**
     * components to create an information file for
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
     * perturbation radii
     */
    private double[] perturbationR;
    private boolean forTIParameters;
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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(String) Header for names of output files (as in header_[sh,psv].inf) (PREM)");
            pw.println("#header ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of an entry list file. If this is unset, the following obsPath will be used.");
            pw.println("#entryPath dataEntry.lst");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##(double[]) Radii of perturbation points, listed using spaces, must be set");
            pw.println("#perturbationR 3500 3600");
            pw.println("##(boolean) Whether to compute partial derivatives for TI parameters. Otherwise, isotropic. (false)");
            pw.println("#forTIParameters true");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##Time length to be computed, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be computed in frequency domain, must be a power of 2 (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM computations (true)");
            pw.println("#mpi false");
        }
        System.err.println(outPath + " is created.");
    }

    public OneDPartialDSMSetup(Property property) throws IOException {
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
        perturbationR = property.parseDoubleArray("perturbationR", null);
        forTIParameters = property.parseBoolean("forTIParameters", "false");
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
        String dateStr = GadgetAid.getTemporaryString();

        // create set of events and observers to set up DSM for
        Map<GlobalCMTID, Set<Observer>> arcMap = DatasetAid.setupArcMapFromFileOrFolder(entryPath, obsPath, components);
        if (!DatasetAid.checkNum(arcMap.size(), "event", "events")) return;

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "oneDPartial", folderTag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output information files in each event folder
        List<String> sourceList = new ArrayList<>();
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
                int numberOfObserver = (int) observers.stream().map(Observer::toString).count();
                if (numberOfObserver != observers.size())
                    System.err.println("!Caution there are observers with the same name and different position for " + event);

                OneDPartialDSMInputFile info = new OneDPartialDSMInputFile(structure, event.getEventData(), observers, header,
                        perturbationR, tlen, np);
                Path outEventPath = outPath.resolve(event.toString());
                Files.createDirectories(outEventPath.resolve(header));
                if (forTIParameters) {
                    info.writeTISH(outEventPath.resolve(header + "_SH.inf"));
                    info.writeTIPSV(outEventPath.resolve(header + "_PSV.inf"));
                } else {
                    info.writeISOSH(outEventPath.resolve(header + "_SH.inf"));
                    info.writeISOPSV(outEventPath.resolve(header + "_PSV.inf"));
                }
                sourceList.add(event.toString());
            } catch (IOException e) {
                // If there are any problems, move on to the next event.
                System.err.println("Error on " + event);
                e.printStackTrace();
            }
        }
        // output shellscripts for execution of sshsh and sshpsv
        String listFileName = "sourceList.txt";
        Files.write(outPath.resolve(listFileName), sourceList);
        DSMShellscript shell = new DSMShellscript(outPath, mpi, arcMap.size(), header);
        Path outSHPath;
        Path outPSVPath;
        if (forTIParameters) {
            outSHPath = outPath.resolve(DatasetAid.generateOutputFileName("run1dparTI_SH", null, dateStr, ".sh"));
            outPSVPath = outPath.resolve(DatasetAid.generateOutputFileName("run1dparTI_PSV", null, dateStr, ".sh"));
            shell.write(SPCType.PARN, SPCMode.SH, listFileName, outSHPath);  // TODO using PARN here is not a clean program
            shell.write(SPCType.PARN, SPCMode.PSV, listFileName, outPSVPath);
        } else {
            outSHPath = outPath.resolve(DatasetAid.generateOutputFileName("run1dparI_SH", null, dateStr, ".sh"));
            outPSVPath = outPath.resolve(DatasetAid.generateOutputFileName("run1dparI_PSV", null, dateStr, ".sh"));
            shell.write(SPCType.PAR2, SPCMode.SH, listFileName, outSHPath);  // TODO using PAR2 here is not a clean program
            shell.write(SPCType.PAR2, SPCMode.PSV, listFileName, outPSVPath);
        }
        System.err.println("After this finishes, please run " + outSHPath + " and " + outPSVPath);
    }
}
