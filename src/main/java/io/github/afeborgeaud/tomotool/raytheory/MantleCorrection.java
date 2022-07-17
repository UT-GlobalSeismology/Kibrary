package io.github.afeborgeaud.tomotool.raytheory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.afeborgeaud.tomotool.topoModel.LLNLG3DJPS;
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

public class MantleCorrection {
    private Path inputPath;
    private String threeDmodel;
    private String phaseName;

    private Set<TimewindowData> timewindows;
    private Seismic3Dmodel seismic3Dmodel;
    private String phaseNames;
    private Phase[] phase;
    private String modelName;

    private Path outPath;
    private Path bouncepointPath;

    private Set<StaticCorrectionData> corrections = new HashSet<>();
    private Set<String> bounces = new HashSet<>();

    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            System.err.println("USAGE:");
            System.err.println("timewindow file path, 3Dmodel, phase");
            System.err.println("Choose 3Dmodel form semucb, llnlg3d, or s20rts"); //TODO 一覧表示できるもの
            System.err.println("Chosse pahse form ScS, PcP");
        }
        else {
            long startTime = System.nanoTime();
            System.err.println(MantleCorrection.class.getName() + " is going..");

            MantleCorrection mc = new MantleCorrection(Paths.get(args[0]), args[1].trim(), args[2].trim());

            mc.run();

            System.err.println(MantleCorrection.class.getName() + " finished in " + GadgetAid.toTimeString(System.nanoTime() - startTime));
        }
    }

    public MantleCorrection(Path inputPath, String threedModel, String phase) throws IOException {
        this.inputPath = inputPath;
        this.threeDmodel = threedModel;
        this.phaseName = phase;

        set();
    }

    private void set() throws IOException {
        timewindows = TimewindowDataFile.read(inputPath);

        //set 3D seismic model
        switch (threeDmodel) {
        case "semucb":
            seismic3Dmodel = new SEMUCBWM1();
            break;
        case "llnlg3d":
            seismic3Dmodel = new LLNLG3DJPS();
            break;
        case "s20rts":
            seismic3Dmodel = new S20RTS();
            break;
        default:
            throw new RuntimeException("Error: 3D model " + threeDmodel + " not implemented yet");
        }
        seismic3Dmodel.setTruncationRange(3881., 6371.);
        //seismic3Dmodel.setTruncationRange(3971., 6371.);
        //seismic3Dmodel.setTruncationRange(3481., 6371.);

        switch (phaseName) {
        case "ScS":
            phaseNames = "S, ScS";
            phase = new Phase[] {Phase.ScS};
            break;
        case "PcP":
            phaseNames = "P, PcP";
            phase = new Phase[] {Phase.PcP};
            break;
        default:
            throw new RuntimeException("Error: phase " + phaseName + " not implemented yet");
        }

        modelName = "prem";
    }

    private void run() throws IOException {
        setOutput();

        if (timewindows == null || timewindows.isEmpty()) {
            throw new RuntimeException("Error: timewindow file is null or empty");
        } else {
            for (TimewindowData window : timewindows) {
                Compute(window);
            }
        }

        writeResult();

        //else if (mc.phase.equals("test")) {
         //   System.err.println("test");
         //   try {
         //       mc.test(mc.raypathInformations);
          //  } catch (TauModelException e) {
          //      e.printStackTrace();
          //  }
        //}
    }

    private void setOutput() {
        switch (phaseName) {
        case "ScS":
            bouncepointPath = Paths.get(DatasetAid.generateOutputFileName("bouncepoint_ScS", threeDmodel, GadgetAid.getTemporaryString(), ".lst"));
            outPath = Paths.get(DatasetAid.generateOutputFileName("mantleCorrection_S-ScS", threeDmodel, GadgetAid.getTemporaryString(), ".dat"));
            break;
        case "PcP":
            bouncepointPath = Paths.get(DatasetAid.generateOutputFileName("bouncepoint_PcP", threeDmodel, GadgetAid.getTemporaryString(), ".lst"));
            outPath = Paths.get(DatasetAid.generateOutputFileName("mantleCorrection_P-PcP", threeDmodel, GadgetAid.getTemporaryString(), ".dat"));
            break;
        default:
            throw new RuntimeException("Error: phase " + phaseName + " not implemented yet");
        }
    }

    public void Compute(TimewindowData window) throws IOException {
        List<RaypathInformation> raypathInformations = new ArrayList<>();
        raypathInformations.add(new RaypathInformation(window.getObserver(), window.getGlobalCMTID()));

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseNames);

        traveltimetool.setIgnoreMantle(false);
        traveltimetool.setIgnoreCMBElevation(true);
        traveltimetool.run();

        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_reflect = new ArrayList<>();
        List<TraveltimeData> ttData_direct = new ArrayList<>();

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (phaseName.equals("PcP") && !(phases.contains("PcP") && phases.contains("P"))) continue;
            if (phaseName.equals("ScS") && !(phases.contains("ScS") && phases.contains("S"))) continue;
            TraveltimeData ttdDir = null;
            TraveltimeData ttdRef = null;
            for (TraveltimeData ttd : record) {
                if (phaseName.equals("PcP")) {
                     if (ttd.getPhaseName().equals("P")) {
                         ttdDir = ttd;
                         ttData_direct.add(ttd);
                     } else if (ttd.getPhaseName().equals("PcP")) {
                         ttdRef = ttd;
                         ttData_reflect.add(ttd);
                     }
                }
                if (phaseName.equals("ScS")) {
                    if (ttd.getPhaseName().equals("S")) {
                        ttdDir = ttd;
                        ttData_direct.add(ttd);
                    } else if (ttd.getPhaseName().equals("ScS")) {
                        ttdRef = ttd;
                        ttData_reflect.add(ttd);
                    }
                }
            }
            double shift = -(ttdRef.getTraveltimePerturbationToPREM() - ttdDir.getTraveltimePerturbationToPREM());

            bounces.add(ttdRef.getObserver().getPaddedInfoString() + " " + ttdRef.getGlobalCMTID().toString() + " " + ttdRef.getScatterPointList().get(0) + " " + shift);

            StaticCorrectionData correction = new StaticCorrectionData(ttdDir.getObserver(), ttdDir.getGlobalCMTID()
                    , window.getComponent(), 0., shift, 1., phase);
            corrections.add(correction);
        }
    }

    private void writeResult() throws IOException {
        PrintWriter pw = new PrintWriter(bouncepointPath.toFile());
        for (String bounce : bounces) {
             pw.println(bounce);
        }
        pw.close();
        StaticCorrectionDataFile.write(corrections, outPath);
    }

//    public static void test(List<RaypathInformation> raypathInformations) throws IOException, TauModelException {
//        String modelName = "prem";
//
//        Seismic3Dmodel seismic3Dmodel = new SEMUCBWM1();
//
//        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, "P, PcP");
//
//        traveltimetool.setIgnoreMantle(false);
//        traveltimetool.setIgnoreCMBElevation(true);
//
//        traveltimetool.run();
//        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();
//
//        List<TraveltimeData> ttData_PcP = new ArrayList<>();
//        List<TraveltimeData> ttData_P = new ArrayList<>();
//
////		TauP_Time tauptime = new TauP_Time("/usr/local/share/TauP-2.4.5/StdModels/PREM_1000.taup");
//        TauP_Time tauptime = new TauP_Time("prem");
//        tauptime.parsePhaseList("P, PcP");
//
//        for (List<TraveltimeData> record : ttData) {
//            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
//            if (!(phases.contains("PcP") && phases.contains("P"))) {
//                System.err.println(record);
//                continue;
//            }
//            TraveltimeData ttdP = null;
//            TraveltimeData ttdPcP = null;
//            for (TraveltimeData ttd : record) {
//                if (ttd.getPhaseName().equals("P")) {
//                    ttdP = ttd;
//                    ttData_P.add(ttd);
//                }
//                else if (ttd.getPhaseName().equals("PcP")) {
//                    ttdPcP = ttd;
//                    ttData_PcP.add(ttd);
//                }
//            }
//
//            tauptime.setSourceDepth(6371. - ttdP.getGlobalCMTID().getEvent().getCmtLocation().getR());
//            tauptime.calculate(ttdP.getEpicentralDistance());
//            double taup_P = tauptime.getArrival(0).getTime();
//            double taup_PcP = tauptime.getArrival(1).getTime();
//
//            System.err.println(ttdPcP.getAbsoluteTraveltimeRef() + " " + ttdPcP.getAbsoluteTraveltimePREM() + " " + taup_PcP
//                    + " " + ttdPcP.getTraveltimePerturbation() + " " + ttdPcP.getTraveltimePerturbationToPREM());
//        }
//    }

}
