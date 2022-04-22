package io.github.kensuke1984.kibrary.external.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
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
     * Path of dataset folder including event folders with SAC files inside
     */
    private Path inPath;

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

    public SPECFEMSetup(Path inPath, Path workPath) {
        this.inPath = inPath;
        this.workPath = workPath;
    }

    private void run() throws IOException {
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(inPath);
        if (!DatasetAid.checkEventNum(eventDirs.size())) {
            return;
        } else if (eventDirs.size() >= 10000) {
            // only 4 digits can be used for the run**** folder names
            System.err.println("The number of events must be less than 10000. Aborting.");
            return;
        }

        outPath = workPath.resolve("specfem" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (eventDirs.size() == 1) {
            // this loop is only done once; loop is written here to extract eventDir from Set<EventFolder>
            for (EventFolder eventDir : eventDirs) {
                createRunDirectory(outPath, eventDir);
            }

            System.err.println();
            System.err.println("Instructions for running SPECFEM:");
            System.err.println("1. Copy the contents of " + outPath + "/DATA/ into specfem*/DATA/");
            System.err.println("2. Run the mesher");
            System.err.println("3. Run the solver");
            System.err.println();

        } else {
            int i = 1;
            for (EventFolder eventDir : eventDirs) {
                Path runPath = outPath.resolve("run" + MathAid.padToString(i++, 4, "0"));
                createRunDirectory(runPath, eventDir);
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
            System.err.println("    NUMBER_OF_SIMULTANEOUS_RUNS = " + eventDirs.size());
            System.err.println("    BROADCAST_SAME_MESH_AND_MODEL = .true.");
            System.err.println("    USE_FAILSAFE_MECHANISM = .true.");
            System.err.println("6. Run the solver, with " + eventDirs.size() + " times the original nuber of cores.");
            System.err.println();
            System.err.println("Pattern B");
            System.err.println("1. Copy the contents of " + outPath + " into specfem*/");
            System.err.println("2. In DATA/Par_file, set:");
            System.err.println("    NUMBER_OF_SIMULTANEOUS_RUNS = " + eventDirs.size());
            System.err.println("    BROADCAST_SAME_MESH_AND_MODEL = .true.");
            System.err.println("    USE_FAILSAFE_MECHANISM = .true.");
            System.err.println("3. Run the mesher, with " + eventDirs.size() + " times the original nuber of cores.");
            System.err.println("4. Copy the following files inside OUTPUT_FILES to run0001/OUTPUT_FILES :");
            System.err.println("    - addressing.txt");
            System.err.println("    - output_mesher.txt");
            System.err.println("    - values_from_mesher.h");
            System.err.println("5. Run the solver, with " + eventDirs.size() + " times the original nuber of cores.");
            System.err.println();
        }
    }

    private void createRunDirectory(Path runPath, EventFolder eventDir) throws IOException {
        Path dataPath = runPath.resolve("DATA");

        Files.createDirectories(dataPath);
        Files.createDirectories(runPath.resolve("DATABASES_MPI"));
        Files.createDirectories(runPath.resolve("OUTPUT_FILES"));

        generateCmtSolutionFile(eventDir.getGlobalCMTID(), dataPath);

        // collect all observers of SAC files under eventDir
        Set<Observer> observerSet = eventDir.sacFileSet().stream()
                .map(name -> name.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                .map(Observer::of).collect(Collectors.toSet());
        generateStationFile(observerSet, dataPath);
    }

    private void generateCmtSolutionFile(GlobalCMTID eventID, Path dataPath) throws IOException {
        Path cmtSolutionPath = dataPath.resolve("CMTSOLUTION");
        GlobalCMTAccess event = eventID.getEvent();
        double latitude = event.getCmtLocation().getLatitude();
        double longitude = event.getCmtLocation().getLongitude();
        double depth = event.getCmtLocation().getDepth();
        double[] mt = event.getCmt().getDSMmt();

        String cmtLine = "PDE " + event.getCMTTime().format(DateTimeFormatter.ofPattern("yyyy MM dd HH mm ss.SSS"))
                + " " + latitude + " " + longitude + " " + depth
                + " " + event.getMb()+ " " + event.getMs() + " " + event.getGeographicalLocationName();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(cmtSolutionPath))) {
            pw.println(cmtLine);
            pw.println("event name: " + event.toString());
            pw.println("time shift: 0.0000");
            pw.println("half duration: 0.0000");
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
    private void generateStationFile(Set<Observer> observerSet, Path dataPath) throws IOException {
        Path stationPath = dataPath.resolve("STATIONS");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(stationPath))) {
            observerSet.forEach(observer -> pw.println(observer.getPaddedInfoString() + "  0.0  0.0"));
        }
    }

}
