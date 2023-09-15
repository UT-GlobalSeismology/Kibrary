package io.github.afeborgeaud.tomotool.raytheory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.afeborgeaud.tomotool.topoModel.S20RTS;
import io.github.afeborgeaud.tomotool.topoModel.SEMUCBWM1;
import io.github.afeborgeaud.tomotool.topoModel.Seismic3Dmodel;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Compute mantle correction value at each raypath.
 * Different from MantleCorrection, this class uses single phase to compute correction value.
 * Raypath informations are referred from {@link TimewindowData}.
 *
 * The format of output file is same as {@link StaticCorrectionDataFile}.
 * bouncepoint.lst file includes following: obsever, event, bounce point position, and mantle correction value.
 *
 * @author ryoichi
 */

public class SingleRayCorrection{
    /**
     * Path to input timewindow file
     */
    private Path inputPath;
    /**
     * Seismic 3D model name
     */
    private String threeDmodel;
    /**
     * phase name (S or P)
     */
    private String phaseName;
    /**
     * Timewindow data
     */
    private Set<TimewindowData> timewindows;
    /**
     * Seismic 3D model
     * Sould be selected from {@link SEMUCBWM1}, {@link S20RTS}
     */
    private Seismic3Dmodel seismic3Dmodel;
    /**
     * phase name ("S" or "P")
     */
    private String phaseNames;
    /**
     * Phase
     */
    private Phase[] phase;
    /**
     * Model nae to compute travel time
     */
    private String modelName;
    /**
     * Path to output file
     */
    private Path outPath;
    /**
     * Path to bouncepoint.lst
     */
    private Path bouncepointPath;
    /**
     * Mantle correction value;
     */
    private Set<StaticCorrectionData> corrections = new HashSet<>();
    /**
     * Information of bounce points
     */
    private Set<String> bounces = new HashSet<>();

    public static void main(String[] args) throws IOException {

        if(args.length != 3) {
            System.err.println("USAGE:");
            System.err.println("timewindow file path, 3Dmodel, phase");
            System.err.println("Choose 3Dmodel from semucb, s40rts");
            System.err.println("Choose phase from S, P");
        } else {
            long startTime = System.nanoTime();
            System.err.println(SingleRayCorrection.class.getName() + " is going...");

            SingleRayCorrection src = new SingleRayCorrection(Paths.get(args[0]), args[1].trim(), args[2].trim());

            src.run();

            System.err.println(SingleRayCorrection.class.getName() + " finished in " + GadgetAid.toTimeString(System.nanoTime() - startTime));
        }
    }

    public SingleRayCorrection(Path inputPath, String threedModel, String phase) throws IOException {
        this.inputPath = inputPath;
        this.threeDmodel = threedModel;
        this.phaseName = phase;

        set();
    }

    private void set() throws IOException {
        timewindows = TimewindowDataFile.read(inputPath);

        // set 3D seismic model
        switch (threeDmodel) {
        case "semucb" :
            seismic3Dmodel = new SEMUCBWM1();
            break;
        case "s20rts" :
            seismic3Dmodel = new S20RTS();
            break;
        default:
            throw new RuntimeException("Error: 3D model " + threeDmodel + " not implemented yet");
        }
        seismic3Dmodel.setTruncationRange(6011., 6371.); // TODO

        switch (phaseName) {
        case "S" :
            phaseNames = "S";
            phase = new Phase[] {Phase.S};
            break;
        case "P" :
            phaseNames = "P";
            phase = new Phase[] {Phase.P};
            break;
        default :
            throw new RuntimeException("Error: phase " + phaseName + " not implemented yet");
        }

        modelName = "prem";
    }

    private void run() throws IOException {
        setOutput();

        if(timewindows == null || timewindows.isEmpty()) {
            throw new RuntimeException("Error: timewindow file is null or empty");
        } else {
            for(TimewindowData window : timewindows) {
                Compute(window);
            }
        }

        writeResult();
    }

    /**
     * Set path to output file and bounce point file
     */
    private void setOutput() {
        switch (phaseName) {
        case "S" :
            bouncepointPath = Paths.get(DatasetAid.generateOutputFileName("bouncepoint_S", threeDmodel, GadgetAid.getTemporaryString(), ".lst"));
            outPath = Paths.get(DatasetAid.generateOutputFileName("singleRayCorrection_S", threeDmodel, GadgetAid.getTemporaryString(), ".dat"));
            break;
        case "P" :
            bouncepointPath = Paths.get(DatasetAid.generateOutputFileName("bouncepoint_P", threeDmodel, GadgetAid.getTemporaryString(), ".lst"));
            outPath = Paths.get(DatasetAid.generateOutputFileName("singleRayCorrection_P", threeDmodel, GadgetAid.getTemporaryString(), ".dat"));
            break;
        default :
            throw new RuntimeException("Error: phase " + phaseName + " not implemented yet");
        }
    }

    /**
     * Compute mantle correction value. Computation is excuted by using {@link Traveltime}.
     * @param window
     * @throws IOException
     */
    public void Compute(TimewindowData window) throws IOException {
        List<RaypathInformation> raypathInformations = new ArrayList<>();
        raypathInformations.add(new RaypathInformation(window.getObserver(), window.getGlobalCMTID()));

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseNames);

        traveltimetool.setIgnoreMantle(false);
        traveltimetool.setIgnoreCMBElevation(true);
        traveltimetool.run();

        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

//        List<TraveltimeData> ttData_direct = new ArrayList<>();

        for(List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if(phaseName.equals("P") && !(phases.contains("P"))) continue;
            if(phaseName.equals("S") && !(phases.contains("S"))) continue;
//            TraveltimeData ttdDir = null;
            TraveltimeData ttdDir = null;
            TraveltimeData ttd = null;
            for (int i = 0; i < record.size(); i++) {
                if(ttd.getPhaseName().equals(phaseName)) {
                    ttd = record.get(i);
                    if (i == 0) ttdDir = ttd;
                    else if(ttd.getAbsoluteTraveltimePREM() < ttdDir.getAbsoluteTraveltimePREM()) ttdDir = ttd;
//                      ttData_direct.add(ttd);
                }
            }
            double shift = ttdDir.getTraveltimePerturbationToPREM();

            StaticCorrectionData correction = new StaticCorrectionData(ttdDir.getObserver(), ttdDir.getGlobalCMTID()
                    , window.getComponent(), 0., shift, 1., phase);
            corrections.add(correction);
        }
    }

    /**
     * Write mantle correcction to output file.
     * @throws IOException
     */
    private void writeResult() throws IOException {
        StaticCorrectionDataFile.write(corrections, outPath);
    }
}