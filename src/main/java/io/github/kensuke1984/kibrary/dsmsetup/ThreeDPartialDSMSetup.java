package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation that generates DSM input files to be used in SHFP, SHBP, PSVFP, and PSVBP,
 * and prepares the environment to run these programs.
 * <p>
 * As input, the following 3 files are needed:
 * <ul>
 * <li> an event list file </li>
 * <li> an observer list file </li>
 * <li> a voxel information file </li>
 * </ul>
 * DSM input files for FP will be created to compute for all (event, perturbationPoint)-pairs.
 * DSM input files for BP will be created to compute for all (observerPosition, perturbationPoint)-pairs.
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
 * <p>
 * By reusing the output folder, computation for events and observers that have already been computed for can be skipped.
 * When doing so, all computation settings (besides events and observers) should be kept the same.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2021/12/24 renamed from InformationFileMaker to ThreeDPartialDSMSetup
 */
public class ThreeDPartialDSMSetup extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * Path of an output foler to reuse, if reusing any.
     */
    private Path reusePath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;
    /**
     * Path of the output folder.
     */
    private Path outPath;
    /**
     * Name root of input file for DSM (header_[SH,PSV].inf).
     */
    private String header;

    /**
     * Path of an event list file.
     */
    private Path eventPath;
    /**
     * Path of an osberver list file.
     */
    private Path observerPath;
    /**
     * Path of a voxel information file.
     */
    private Path voxelPath;
    /**
     * Path of structure file to use instead of PREM.
     */
    private Path structurePath;
    private String structureName;

    /**
     * Time length [s], must be (a power of 2)/samplingHz.
     */
    private double tlen;
    /**
     * Number of steps in frequency domain, should not exceed tlen*samplingHz/2.
     */
    private int np;
    /**
     * Whether to use MPI-version of DSM in shellscript file.
     */
    private boolean mpi;
    private boolean jointCMT;
    private boolean catalogMode;
    /**
     * Epicentral distances for catalog.
     */
    private double thetamin;
    private double thetamax;
    private double dtheta;

    /**
     * Horizontal positions of center points of voxels.
     */
    private HorizontalPosition[] voxelPositions;
    /**
     * Radii of center points of voxels.
     */
    private double[] voxelRadii;

    private String dateString;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##To reuse FP & BP pools that have already been created, set the folder containing them.");
            pw.println("#reusePath threeDPartial");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##(String) Header for names of output files (as in header_[SH,PSV].inf). (PREM)");
            pw.println("#header ");
            pw.println("##Path of an event list file, must be set.");
            pw.println("#eventPath event.lst");
            pw.println("##Path of an observer list file, must be set.");
            pw.println("#observerPath observer.lst");
            pw.println("##Path of a voxel information file for perturbation points, must be set.");
            pw.println("#voxelPath voxel.inf");
            pw.println("##Path of structure file to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of structure model to use. (PREM)");
            pw.println("#structureName ");
            pw.println("##Time length to compute [s], must be (a power of 2)/(desired sampling frequency). (3276.8)");
            pw.println("#tlen ");
            pw.println("##(int) Number of points to compute in frequency domain, should not exceed tlen*(desired sampling frequency)/2. (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to use MPI in the subsequent DSM computations. (true)");
            pw.println("#mpi false");
            pw.println("##(boolean) Whether to compute 6 green functions for the FP wavefield to use for joint structure-CMT inversion. (false)");
            pw.println("#jointCMT ");
            pw.println("##(boolean) Wavefield catalog mode. (false)");
            pw.println("#catalogMode ");
            pw.println("##Catalog distance range: thetamin thetamax dtheta.");
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
        if (property.containsKey("reusePath"))
            reusePath = property.parsePath("reusePath", null, true, workPath);
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
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

        catalogMode = property.parseBoolean("catalogMode", "false");
        if (catalogMode) {
            double[] tmpthetainfo = Stream.of(property.parseStringArray("thetaRange", null))
                    .mapToDouble(Double::parseDouble).toArray();
            thetamin = tmpthetainfo[0];
            thetamax = tmpthetainfo[1];
            dtheta = tmpthetainfo[2];
        }

        // check that settings match with reusePath
        if (reusePath != null) checkReusePath();
    }

    private void checkReusePath() throws IOException {
        Path lastPropertyPath = reusePath.resolve("_" + this.getClass().getSimpleName() + ".properties");
        Property lastProperty = new Property();
        lastProperty.readFrom(lastPropertyPath);

        if (!voxelPath.equals(lastProperty.parsePath("voxelPath", null, true, workPath)))
            throw new IllegalStateException("voxelPath does not match with reused folder.");
        if (structurePath != null) {
            if (!lastProperty.containsKey("structurePath") || !structurePath.equals(lastProperty.parsePath("structurePath", null, true, workPath)))
                throw new IllegalStateException("structurePath does not match with reused folder.");
        } else {
            if (!lastProperty.containsKey("structureName") || !structureName.equals(lastProperty.parseString("structureName", null)))
                throw new IllegalStateException("structureName does not match with reused folder.");
        }
        if (tlen != lastProperty.parseDouble("tlen", null)) {
            throw new IllegalStateException("tlen does not match with reused folder.");
        }
        if (np != lastProperty.parseInt("np", null)) {
            throw new IllegalStateException("np does not match with reused folder.");
        }
    }

    @Override
    public void run() throws IOException {
        dateString = GadgetAid.getTemporaryString();

        // read voxel information
        VoxelInformationFile vif = new VoxelInformationFile(voxelPath);
        voxelRadii = vif.getRadii();
        voxelPositions = vif.getHorizontalPositions().toArray(new HorizontalPosition[0]);

        // read event and observer information
        Set<GlobalCMTID> eventSet = EventListFile.read(eventPath);
        Set<Observer> observerSet = ObserverListFile.read(observerPath);
        if (eventSet.size() == 0 && observerSet.size() == 0) {
            return;
        }

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        // set outPath
        if (reusePath != null) {
            outPath = reusePath;
            System.err.println("Reusing " + reusePath);
        } else {
            outPath = DatasetAid.createOutputFolder(workPath, "threeDPartial", folderTag, appendFolderDate, dateString);
            property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
            FileUtils.copyFileToDirectory(voxelPath.toFile(), outPath.toFile(), false);
            createPointInformationFile();
        }

        setupFPs(eventSet, structure);
        setupBPs(observerSet, structure);
    }

    private void setupFPs(Set<GlobalCMTID> eventSet, PolynomialStructure structure) throws IOException {
        Path fpPoolPath = outPath.resolve("FPpool");
        Path fpCatPath = outPath.resolve("FPcat");
        Files.createDirectories(fpPoolPath);

        System.err.println("Making information files for the events (fp) ...");
        int nCreated = 0;
        int nSkipped = 0;
        // TreeSet is used here to sort the FP sources in the fpList file
        Set<String> fpSourceTreeSet = new TreeSet<>();
        for (GlobalCMTID event : eventSet) {
            GlobalCMTAccess eventData = event.getEventData();

            // joint CMT inversion
            if (jointCMT) {
                int mtExp = 25;
                double m0Coef = 1.;
                MomentTensor[] mts = new MomentTensor[6];
                mts[0] = new MomentTensor(m0Coef, 1., 0., 0., 0., 0., 0., mtExp);
                mts[1] = new MomentTensor(m0Coef, 0., 1., 0., 0., 0., 0., mtExp);
                mts[2] = new MomentTensor(m0Coef, 0., 0., 1., 0., 0., 0., mtExp);
                mts[3] = new MomentTensor(m0Coef, 0., 0., 0., 1., 0., 0., mtExp);
                mts[4] = new MomentTensor(m0Coef, 0., 0., 0., 0., 1., 0., mtExp);
                mts[5] = new MomentTensor(m0Coef, 0., 0., 0., 0., 0., 1., mtExp);

                for (int i = 0; i < 6; i++) {
                    // If computation for this event & mt already exists in the FP pool, skip
                    Path mtPoolPath = fpPoolPath.resolve(event.toString() + "_mt" + i);
                    if (Files.exists(mtPoolPath)) {
                        nSkipped++;
//                        System.err.println(" " + event.toString() + " : " + mtPoolPath + " already exists, skipping.");
                        continue;
                    }

                    GlobalCMTAccess virtualEventData = eventData.withCMT(mts[i]);
                    FPInputFile fp = new FPInputFile(virtualEventData, header, structure, tlen, np, voxelRadii, voxelPositions);
                    Files.createDirectories(mtPoolPath.resolve(header));
                    fp.writeSHFP(mtPoolPath.resolve(header + "_SH.inf"));
                    fp.writePSVFP(mtPoolPath.resolve(header + "_PSV.inf"));
                }

            } else {
                // If computation for this event already exists in the FP pool, skip
                Path eventPoolPath = fpPoolPath.resolve(event.toString());
                if (Files.exists(eventPoolPath)) {
                    nSkipped++;
//                    System.err.println(" " + event.toString() + " : " + eventPoolPath + " already exists, skipping.");
                    continue;
                }

                // Prepare files in FP pool
                FPInputFile fp = new FPInputFile(eventData, header, structure, tlen, np, voxelRadii, voxelPositions);
                Files.createDirectories(eventPoolPath.resolve(header));
                fp.writeSHFP(eventPoolPath.resolve(header + "_SH.inf"));
                fp.writePSVFP(eventPoolPath.resolve(header + "_PSV.inf"));

                if (catalogMode) {
                     Path catInfPath = fpCatPath.resolve(event.toString());
                     Files.createDirectories(catInfPath.resolve(header));
                     fp.writeSHFPCAT(catInfPath.resolve(header + "_SH.inf"), thetamin, thetamax, dtheta);
                     fp.writePSVFPCAT(catInfPath.resolve(header + "_PSV.inf"), thetamin, thetamax, dtheta);
                }
            }
            nCreated++;
            fpSourceTreeSet.add(event.toString());
        }

        if (nSkipped > 0)
            System.err.println(" " + MathAid.switchSingularPlural(nSkipped, "source was", "sources were")
                    + " skipped; directory already exists.");
        System.err.println(" " + MathAid.switchSingularPlural(nCreated, "source", "sources") + " created in " + fpPoolPath);
        if (nCreated == 0) return;

        // output list and shellscripts for execution of shfp and psvfp
        Path fpListPath = DatasetAid.generateOutputFilePath(outPath, "fpList", fileTag, appendFileDate, dateString, ".txt");
        Files.write(fpListPath, fpSourceTreeSet);
        Path outSHPath = DatasetAid.generateOutputFilePath(outPath, "runFP_SH", fileTag, appendFileDate, dateString, ".sh");
        Path outPSVPath = DatasetAid.generateOutputFilePath(outPath, "runFP_PSV", fileTag, appendFileDate, dateString, ".sh");
        DSMShellscript shellFP = new DSMShellscript(mpi, nCreated, header);
        shellFP.write(DSMShellscript.DSMType.FP, SPCMode.SH, fpListPath.getFileName().toString(), outSHPath);
        shellFP.write(DSMShellscript.DSMType.FP, SPCMode.PSV, fpListPath.getFileName().toString(), outPSVPath);
        System.err.println("After this finishes, please enter " + outPath + "/ and run "
                + outSHPath.getFileName() + " and " + outPSVPath.getFileName());
    }

    private void setupBPs(Set<Observer> observerSet, PolynomialStructure structure) throws IOException {
        Path bpPoolPath = outPath.resolve("BPpool");
        Path bpCatPath = outPath.resolve("BPcat");
        Files.createDirectories(bpPoolPath);

        System.err.println("Making information files for the observers (bp) ...");
        int nCreated = 0;
        int nSkipped = 0;
        // TreeSet is used here to sort the BP sources in the bpList file
        Set<String> bpSourceTreeSet = new TreeSet<>();
        for (Observer observer : observerSet) {
            String observerCode = observer.getPosition().toCode();

            // If computation for this observer already exists in the BP pool,
            //  or in case observers with same position but different name exist, skip
            Path observerPoolPath = bpPoolPath.resolve(observerCode);
            if (Files.exists(observerPoolPath)) {
                nSkipped++;
//                System.err.println(" " + observer.toString() + " : " + observerPoolPath + " already exists, skipping.");
                continue;
            }

            // Prepare files in BPqueue
            BPInputFile bp = new BPInputFile(observer.getPosition(), header, structure, tlen, np, voxelRadii, voxelPositions);
            Files.createDirectories(observerPoolPath.resolve(header));
            bp.writeSHBP(observerPoolPath.resolve(header + "_SH.inf"));
            bp.writePSVBP(observerPoolPath.resolve(header + "_PSV.inf"));
            nCreated++;
            bpSourceTreeSet.add(observerCode);
        }

        if (nSkipped > 0)
            System.err.println(" " + MathAid.switchSingularPlural(nSkipped, "source was", "sources were")
                    + " skipped; directory already exists.");
        System.err.println(" " + MathAid.switchSingularPlural(nCreated, "source", "sources") + " created in " + bpPoolPath);
        if (nCreated == 0) return;

        if (catalogMode) {
            BPInputFile bp = new BPInputFile(header, structure, tlen, np, voxelRadii, voxelPositions);
            Files.createDirectories(bpCatPath.resolve(header));
            bp.writeSHBPCat(bpCatPath.resolve(header + "_SH.inf"), thetamin, thetamax, dtheta);
            bp.writePSVBPCat(bpCatPath.resolve(header + "_PSV.inf"), thetamin, thetamax, dtheta);
        }

        // output list and shellscripts for execution of shbp and psvbp
        Path bpListPath = DatasetAid.generateOutputFilePath(outPath, "bpList", fileTag, appendFileDate, dateString, ".txt");
        Files.write(bpListPath, bpSourceTreeSet);
        Path outSHPath = DatasetAid.generateOutputFilePath(outPath, "runBP_SH", fileTag, appendFileDate, dateString, ".sh");
        Path outPSVPath = DatasetAid.generateOutputFilePath(outPath, "runBP_PSV", fileTag, appendFileDate, dateString, ".sh");
        DSMShellscript shellBP = new DSMShellscript(mpi, nCreated, header);
        shellBP.write(DSMShellscript.DSMType.BP, SPCMode.SH, bpListPath.getFileName().toString(), outSHPath);
        shellBP.write(DSMShellscript.DSMType.BP, SPCMode.PSV, bpListPath.getFileName().toString(), outPSVPath);
        System.err.println("After this finishes, please enter " + outPath + "/ and run "
                + outSHPath.getFileName() + " and " + outPSVPath.getFileName());
    }

    /**
     * Creates voxelPointCode.lst
     */
    private void createPointInformationFile() throws IOException {
        Path horizontalPointPath = outPath.resolve("voxelPointCode.txt");
        try (PrintWriter hpw = new PrintWriter(Files.newBufferedWriter(horizontalPointPath))) {
            int figure = String.valueOf(voxelPositions.length).length();
            for (int i = 0; i < voxelPositions.length; i++) {
                hpw.println("XY" + String.format("%0" + figure + "d", i + 1) + " " + voxelPositions[i]);
            }
        }
    }

}
