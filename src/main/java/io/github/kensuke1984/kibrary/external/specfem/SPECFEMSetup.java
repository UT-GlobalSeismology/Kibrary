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
        }

        outPath = workPath.resolve("specfem" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        if (eventDirs.size() == 1) {
            //TODO
        } else {
            int i = 1;
            for (EventFolder eventDir : eventDirs) {
                createRunDirectory(i++, eventDir);
            }
        }
    }

    private void createRunDirectory(int runNum, EventFolder eventDir) throws IOException {
        Path runPath = outPath.resolve("run" + MathAid.padToString(runNum, 4, "0"));
        Path dataPath = runPath.resolve("DATA");

        Files.createDirectories(dataPath);
        Files.createDirectories(runPath.resolve("DATABASES_MPI"));
        Files.createDirectories(runPath.resolve("OUTPUT_FILES"));

        generateCmtSolutionFile(eventDir.getGlobalCMTID(), dataPath);

        // collect all observers of SAC files under eventDir
        Set<Observer> observerSet = eventDir.sacFileSet().stream().map(name -> {
                    try {
                        return name.readHeader();
                    } catch (Exception e2) {
                        return null;
                    }
                }).filter(Objects::nonNull).map(Observer::of).collect(Collectors.toSet());
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
            pw.println("Mrr: " + mt[0] + "e+25");
            pw.println("Mtt: " + mt[3] + "e+25");
            pw.println("Mpp: " + mt[5] + "e+25");
            pw.println("Mrt: " + mt[1] + "e+25");
            pw.println("Mrp: " + mt[2] + "e+25");
            pw.println("Mtp: " + mt[4] + "e+25");
        }
    }
    private void generateStationFile(Set<Observer> observerSet, Path dataPath) throws IOException {
        Path stationPath = dataPath.resolve("STATION");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(stationPath))) {
            observerSet.forEach(observer -> pw.println(observer.getPaddedInfoString() + "  0.0  0.0"));
        }
    }

}
