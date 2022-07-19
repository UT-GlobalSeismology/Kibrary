package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.MomentTensor;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalog;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.SPCType;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation that makes DSM input files to be used in SHFP, SHBP, PSVFP, and PSVBP,
 * and prepares the environment to run these programs.
 * <p>
 * As input, the following 3 files are needed:
 * <ul>
 * <li> an event list file </li>
 * <li> an observer list file </li>
 * <li> a voxel information file </li>
 * </ul>
 * DSM input files for FP will be created to calculate for all (event, perturbationPoint)-pairs.
 * DSM input files for BP will be created to calculate for all (observerPosition, perturbationPoint)-pairs.
 * <p>
 * The resulting folders in output BP folder will be named by the position code of observers (see {@link HorizontalPosition#toCode}).
 * Observer position codes are used here instead of observer names to dodge the problem caused by
 * "observers with same name but different position".
 * (At a single time moment, only one observer with the same network and station code exists.
 * However, at different times, observers with the same name but different positions can exist.
 * In BP folders created here, observers are collected from different time periods, so their names can conflict.
 * Therefore, observer positions are used instead to distinguish an observer.
 * "Observers with different names but same position" can also exist, but they do not cause problems here
 * because the same BP waveform can be used for both of them.)
 *
 * @author Kensuke Konishi
 * @since version 0.2.2.1
 * @author anselme add content for catalog
 * @version 2021/12/24 renamed from InformationFileMaker
 */
public class ThreeDPartialDSMSetup extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;
    /**
     * Information file name is header_[psv,sh].inf
     */
    private String header;

    /**
     * information file of events.
     */
    private Path eventPath;
    /**
     * information file of observers.
     */
    private Path observerPath;
    /**
     * information file of locations of pertubation points.
     */
    private Path voxelPath;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
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
    private boolean jointCMT;
    private boolean catalogue;
    /**
     * epicentral distances for catalog
     */
    private double thetamin;
    private double thetamax;
    private double dtheta;

    /**
     * locations of perturbation points
     *
     */
    private HorizontalPosition[] perturbationPositions;
    /**
     * Radii of perturbation points default values are double[]{3505, 3555,
     * 3605, 3655, 3705, 3755, 3805, 3855} Sorted. No duplication.
     */
    private double[] perturbationRadii;


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
            pw.println("##(String) Header for names of output files (as in header_[psv, sh].inf) (PREM)");
            pw.println("#header ");
            pw.println("##Path of an event list file, must be set");
            pw.println("#eventPath event.lst");
            pw.println("##Path of an observer list file, must be set");
            pw.println("#observerPath observer.lst");
            pw.println("##Path of a voxel information file for perturbation points, must be set");
            pw.println("#voxelPath voxel.inf");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
            pw.println("##Time length to be calculated, must be a power of 2 over 10 (3276.8)");
            pw.println("#tlen ");
            pw.println("##Number of points to be calculated in frequency domain, must be a power of 2 (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM calculations (true)");
            pw.println("#mpi ");
            pw.println("##(boolean) Whether to compute 6 green functions for the FP wavefield to use for joint structure-CMT inversion (false)");
            pw.println("#jointCMT ");
            pw.println("##(boolean) Wavefield catalogue mode (false)");
            pw.println("#catalogue ");
            pw.println("##Catalogue distance range: thetamin thetamax dtheta");
            pw.println("#thetaRange ");
        }
        System.err.println(outPath + " is created.");
    }

    public ThreeDPartialDSMSetup(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        header = property.parseStringSingle("header", "PREM");

        eventPath = property.parsePath("eventPath", null, true, workPath);
        observerPath = property.parsePath("observerPath", null, true, workPath);
        voxelPath = property.parsePath("voxelPath", null, true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");
        mpi = property.parseBoolean("mpi", "true");

        jointCMT = property.parseBoolean("jointCMT", "false");

        catalogue = property.parseBoolean("catalogue", "false");
        if (catalogue) {
            double[] tmpthetainfo = Stream.of(property.parseStringArray("thetaRange", null))
                    .mapToDouble(Double::parseDouble).toArray();
            thetamin = tmpthetainfo[0];
            thetamax = tmpthetainfo[1];
            dtheta = tmpthetainfo[2];
        }

        // additional info
        property.setProperty("CMTcatalogue", GlobalCMTCatalog.getCatalogPath().toString());
    }

    @Override
    public void run() throws IOException {
        // read voxel information
        VoxelInformationFile vif = new VoxelInformationFile(voxelPath);
        perturbationRadii = vif.getRadii();
        perturbationPositions = vif.getHorizontalPositions();

        // read event information
        Set<GlobalCMTID> eventSet = EventListFile.read(eventPath);
        System.err.println("Number of events read in: " + eventSet.size());

        // read observer information
        Set<Observer> observerSet = ObserverListFile.read(observerPath);
        System.err.println("Number of observers read in: " + observerSet.size());

        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = PolynomialStructureFile.read(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        outPath = DatasetAid.createOutputFolder(workPath, "threeDPartial", tag, GadgetAid.getTemporaryString());

        if (property != null)
            property.write(outPath.resolve("threeddsm.properties"));

        Path bpPath = outPath.resolve("BPinfo");
        Path fpPath = outPath.resolve("FPinfo");
        Path fpCatPath = outPath.resolve("FPcat");
        Path bpCatPath = outPath.resolve("BPcat");
        createPointInformationFile();


        // FP
        System.err.println("Making information files for the events (fp) ...");
        int n = 0;
        for (GlobalCMTID eventID : eventSet) {
            GlobalCMTAccess event;
            try {
                event = eventID.getEventData();

                // joint CMT inversion
                if (jointCMT) {
                    int mtEXP = 25;
                    double mw = 1.;
                    MomentTensor[] mts = new MomentTensor[6];
                    mts[0] = new MomentTensor(1., 0., 0., 0., 0., 0., mtEXP, mw);
                    mts[1] = new MomentTensor(0., 1., 0., 0., 0., 0., mtEXP, mw);
                    mts[2] = new MomentTensor(0., 0., 1., 0., 0., 0., mtEXP, mw);
                    mts[3] = new MomentTensor(0., 0., 0., 1., 0., 0., mtEXP, mw);
                    mts[4] = new MomentTensor(0., 0., 0., 0., 1., 0., mtEXP, mw);
                    mts[5] = new MomentTensor(0., 0., 0., 0., 0., 1., mtEXP, mw);

                    for (int i = 0; i < 6; i++) {
                        event.setCMT(mts[i]);
                        FPInputFile fp = new FPInputFile(event, header, structure, tlen, np, perturbationRadii, perturbationPositions);
                        Path infPath = fpPath.resolve(event.toString() + "_mt" + i);
                        Files.createDirectories(infPath.resolve(header));
                        fp.writeSHFP(infPath.resolve(header + "_SH.inf"));
                        fp.writePSVFP(infPath.resolve(header + "_PSV.inf"));
                    }
                }
                else {
                    FPInputFile fp = new FPInputFile(event, header, structure, tlen, np, perturbationRadii, perturbationPositions);
                    Path infPath = fpPath.resolve(event.toString());
                    Files.createDirectories(infPath.resolve(header));
                    fp.writeSHFP(infPath.resolve(header + "_SH.inf"));
                    fp.writePSVFP(infPath.resolve(header + "_PSV.inf"));

                    if (catalogue) {
                         Path catInfPath = fpCatPath.resolve(event.toString());
                         Files.createDirectories(catInfPath.resolve(header));
                         fp.writeSHFPCAT(catInfPath.resolve(header + "_SH.inf"), thetamin, thetamax, dtheta);
                         fp.writePSVFPCAT(catInfPath.resolve(header + "_PSV.inf"), thetamin, thetamax, dtheta);
                    }
                }
                n++;
            } catch (RuntimeException e) {  // TODO: is this needed?
                System.err.println(e.getMessage());
            }
        }
        System.err.println(n + " sources created in " + fpPath);
        // output shellscripts for execution of psvfp and shfp
        DSMShellscript shellFP = new DSMShellscript(outPath, mpi, eventSet.size(), header);
        shellFP.write(SPCType.PF, SPCMode.PSV);
        shellFP.write(SPCType.PF, SPCMode.SH);
        System.err.println("After this finishes, please run " + outPath + "/runFP_PSV.sh and " + outPath + "/runFP_SH.sh");

        // BP
        System.err.println("Making information files for the observers (bp) ...");
        n = 0;
        for (Observer observer : observerSet) {
            // System.out.println(str);
            BPInputFile bp = new BPInputFile(observer.getPosition(), header, structure, tlen, np, perturbationRadii, perturbationPositions);
            Path infPath = bpPath.resolve(observer.getPosition().toCode());

            // In case observers with same position but different name exist
            if (Files.exists(infPath)) {
                System.err.println(" " + observer.toString() + " : " + infPath + " already exists, skipping.");
                continue;
            }

            Files.createDirectories(infPath.resolve(header));
            bp.writeSHBP(infPath.resolve(header + "_SH.inf"));
            bp.writePSVBP(infPath.resolve(header + "_PSV.inf"));
            n++;
        }
        System.err.println(n + " sources created in " + bpPath);
        if (catalogue) {
            BPInputFile bp = new BPInputFile(header, structure, tlen, np, perturbationRadii, perturbationPositions);
            Path catInfPath = bpCatPath;
            Files.createDirectories(catInfPath.resolve(header));
            bp.writeSHBPCat(catInfPath.resolve(header + "_SH.inf"), thetamin, thetamax, dtheta);
            bp.writePSVBPCat(catInfPath.resolve(header + "_PSV.inf"), thetamin, thetamax, dtheta);
        }
        // output shellscripts for execution of psvbp and shbp
        DSMShellscript shellBP = new DSMShellscript(outPath, mpi, observerSet.size(), header);
        shellBP.write(SPCType.PB, SPCMode.PSV);
        shellBP.write(SPCType.PB, SPCMode.SH);
        System.err.println("After this finishes, please run " + outPath + "/runBP_PSV.sh and " + outPath + "/runBP_SH.sh");

        // TODO
        if (fpPath.toFile().delete() && bpPath.toFile().delete()) {
            Files.delete(outPath.resolve("horizontalPoint.inf"));
            Files.delete(outPath.resolve("perturbationPoint.inf"));
            Files.delete(outPath);
        } else {
            // FileUtils.moveFileToDirectory(getParameterPath().toFile(),
            // outputPath.toFile(), false);
            FileUtils.copyFileToDirectory(voxelPath.toFile(), outPath.toFile(), false);
        }

    }

    /**
     * Creates files, horizontalPoint.inf and information perturbationPoint
     */
    private void createPointInformationFile() throws IOException {
        Path horizontalPointPath = outPath.resolve("voxelPointCode.inf");
        Path perturbationPointPath = outPath.resolve("perturbationPoint.inf"); //TODO unneeded?
        try (PrintWriter hpw = new PrintWriter(Files.newBufferedWriter(horizontalPointPath));
                PrintWriter ppw = new PrintWriter(Files.newBufferedWriter(perturbationPointPath))) {
            int figure = String.valueOf(perturbationPositions.length).length();
            for (int i = 0; i < perturbationPositions.length; i++) {
                hpw.println("XY" + String.format("%0" + figure + "d", i + 1) + " " + perturbationPositions[i]);
                for (double aPerturbationR : perturbationRadii)
                    ppw.println(perturbationPositions[i] + " " + aPerturbationR);
            }
        }
    }

}
