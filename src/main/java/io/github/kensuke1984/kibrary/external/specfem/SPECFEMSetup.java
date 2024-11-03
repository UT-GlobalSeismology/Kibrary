package io.github.kensuke1984.kibrary.external.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Creates input files for SPECFEM.
 *
 * @author otsuru
 * @since 2022/3/14
 */
public class SPECFEMSetup {

    private Path workPath;
    private Path outPath;

    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: inPath workPath");
            return;
        }
        Path inPath = Paths.get(args[0]);
        Path workPath = Paths.get(args[1]);

        SPECFEMSetup setup = new SPECFEMSetup(inPath, workPath);
        setup.run();
    }

    public SPECFEMSetup(Path dataEntryPath, Path workPath) {
        this.dataEntryPath = dataEntryPath;
        this.workPath = workPath;
    }

    private void run() throws IOException {
        Map<GlobalCMTID, Set<DataEntry>> entryMap = DataEntryListFile.readAsMap(dataEntryPath);
        int nEvents = entryMap.size();
        if (!DatasetAid.checkNum(nEvents, "event", "events")) {
            return;
        } else if (nEvents >= 10000) {
            // only 4 digits can be used for the run**** folder names
            System.err.println("The number of events must be less than 10000. Aborting.");
            return;
        }

        outPath = workPath.resolve("specfem" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (nEvents == 1) {
            // this loop is only done once; loop is written here to extract event from Map<GlobalCMTID, Set<DataEntry>>
            for (GlobalCMTID event : entryMap.keySet()) {
                createRunDirectory(outPath, event, entryMap.get(event));
            }

            System.err.println();
            System.err.println("Instructions for running SPECFEM:");
            System.err.println("1. Copy the contents of " + outPath + "/DATA/ into specfem*/DATA/");
            System.err.println("2. Run the mesher");
            System.err.println("3. Run the solver");
            System.err.println();

        } else {
            int i = 1;
            for (GlobalCMTID event : entryMap.keySet()) {
                Path runPath = outPath.resolve("run" + MathAid.padToString(i++, 4, true));
                createRunDirectory(runPath, event, entryMap.get(event));
            }

            System.err.println();
            System.err.println("Instructions for running SPECFEM:");
            System.err.println();
            System.err.println("Pattern A");
            System.err.println("1. Run the mesher as usual");
            System.err.println("2. Copy the contents of " + outPath + " into specfem*/");
            System.err.println("3. Copy DATABASES_MPI/ (the one in the root folder, not in the run0001 to run**** folders),");
            System.err.println("    which has been filled with mesh informations from the run of the mesher in 1., into run0001/ .");
            System.err.println("    Folders run0002/DATABASES_MPI/ to run****/DATABASES_MPI/ can be left empty.");
            System.err.println("4. Copy the following files inside OUTPUT_FILES to run0001/OUTPUT_FILES :");
            System.err.println("    - addressing.txt");
            System.err.println("    - output_mesher.txt");
            System.err.println("    - values_from_mesher.h");
            System.err.println("5. In DATA/Par_file, set:");
            System.err.println("    NUMBER_OF_SIMULTANEOUS_RUNS = " + nEvents);
            System.err.println("    BROADCAST_SAME_MESH_AND_MODEL = .true.");
            System.err.println("    USE_FAILSAFE_MECHANISM = .true.");
            System.err.println("6. Run the solver, with " + nEvents + " times the original nuber of cores.");
            System.err.println();
            System.err.println("Pattern B");
            System.err.println("1. Copy the contents of " + outPath + " into specfem*/");
            System.err.println("2. In DATA/Par_file, set:");
            System.err.println("    NUMBER_OF_SIMULTANEOUS_RUNS = " + nEvents);
            System.err.println("    BROADCAST_SAME_MESH_AND_MODEL = .true.");
            System.err.println("    USE_FAILSAFE_MECHANISM = .true.");
            System.err.println("3. Run the mesher, with " + nEvents + " times the original nuber of cores.");
            System.err.println("4. Copy the following files inside OUTPUT_FILES to run0001/OUTPUT_FILES :");
            System.err.println("    - addressing.txt");
            System.err.println("    - output_mesher.txt");
            System.err.println("    - values_from_mesher.h");
            System.err.println("5. Run the solver, with " + nEvents + " times the original nuber of cores.");
            System.err.println();
        }
    }

    private static void createRunDirectory(Path runPath, GlobalCMTID event, Set<DataEntry> entrySet) throws IOException {
        Path dataPath = runPath.resolve("DATA");

        Files.createDirectories(dataPath);
        Files.createDirectories(runPath.resolve("DATABASES_MPI"));
        Files.createDirectories(runPath.resolve("OUTPUT_FILES"));

        generateCmtSolutionFile(event, dataPath);

        // collect observers in entry set (Note that duplication of observers is eliminated when converting to Set.)
        Set<Observer> observerSet = entrySet.stream().map(DataEntry::getObserver).collect(Collectors.toSet());
        generateStationFile(observerSet, dataPath);
    }

    private static void generateCmtSolutionFile(GlobalCMTID eventID, Path dataPath) throws IOException {
        Path cmtSolutionPath = dataPath.resolve("CMTSOLUTION");
        GlobalCMTAccess event = eventID.getEventData();
        double latitude = event.getCmtPosition().getLatitude();
        double longitude = event.getCmtPosition().getLongitude();
        double depth = event.getCmtPosition().getDepth();
        double[] mt = event.getCmt().toDSMStyle();

        // NOTE: The PDE solution is not used in SPECFEM but something must be written; so we just provide the CMT solution to avoid any confusion.
        String cmtLine = "PDE " + event.getCMTTime().format(DateTimeFormatter.ofPattern("yyyy MM dd HH mm ss.SSS"))
                + " " + latitude + " " + longitude + " " + depth
                + " " + event.getMb()+ " " + event.getMs() + " " + event.getGeographicalLocationName();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(cmtSolutionPath))) {
            pw.println(cmtLine);
            pw.println("event name: " + event.toString());
            // CAUTION: Since we provide the CMT time in the PDE information above, time shift should be 0.
            pw.println("time shift: 0.0000");
            // CAUTION: Half duration must be 0 to compute for a step STF (= delta-function moment rate function)!!
            pw.println("half duration: 0.0000");
            // NOTE: SPECFEM accepts latitudes in geographical coordinates.
            pw.println("latitude: " + latitude);
            pw.println("longitude: " + longitude);
            pw.println("depth: " + depth);
            pw.println("Mrr: " + String.format("%e", mt[0] * 1e25));
            pw.println("Mtt: " + String.format("%e", mt[3] * 1e25));
            pw.println("Mpp: " + String.format("%e", mt[5] * 1e25));
            pw.println("Mrt: " + String.format("%e", mt[1] * 1e25));
            pw.println("Mrp: " + String.format("%e", mt[2] * 1e25));
            pw.println("Mtp: " + String.format("%e", mt[4] * 1e25));
        }
    }
    private static void generateStationFile(Set<Observer> observerSet, Path dataPath) throws IOException {
        Path stationPath = dataPath.resolve("STATIONS");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(stationPath))) {
            observerSet.forEach(observer -> pw.println(observer.toPaddedInfoString() + "  0.0  0.0"));
        }
    }

}
