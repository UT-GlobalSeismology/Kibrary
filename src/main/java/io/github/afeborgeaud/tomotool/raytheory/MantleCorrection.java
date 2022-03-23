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

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.afeborgeaud.tomotool.topoModel.LLNLG3DJPS;
import io.github.afeborgeaud.tomotool.topoModel.S20RTS;
import io.github.afeborgeaud.tomotool.topoModel.SEMUCBWM1;
import io.github.afeborgeaud.tomotool.topoModel.Seismic3Dmodel;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

public class MantleCorrection {

    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            System.err.println("USAGE:");
            System.err.println("information file path, 3Dmodel, phase");
            System.err.println("information file should be timewiondow file or should contaion (GCMTID, station name, network name, latitude of the station, longitude of the station)");
            System.err.println("Choose 3Dmodel form"); //TODO なんか一覧表示できるもの
            System.err.println("Chosse pahse form ScS, PcP");
        }
        else {
                // Read parmeter
                Path inputPath = Paths.get(args[0]);
                String threeDmodel = args[1].trim();
                String phase = args[2].trim();

                // Set raypath Informations
                List<RaypathInformation> raypathInformations;
                try {
                    raypathInformations = RaypathInformation.readRaypathFromTimewindows(inputPath);
                } catch (Exception e) {
                    raypathInformations = RaypathInformation.readRaypathInformation(inputPath);
                }

                // Set 3Dmodel
                Seismic3Dmodel seismic3Dmodel = null;
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
//        		seismic3Dmodel.setTruncationRange(3971., 6371.);
//        		seismic3Dmodel.setTruncationRange(3481., 6371.);

                if (phase.equals("PcP")) {
                    System.err.println("Compute PcP");
                    Compute_PcP(raypathInformations, seismic3Dmodel);
                }
                else if (phase.equals("ScS")) {
                    System.err.println("Compute ScS");
                    Compute_ScS(raypathInformations, seismic3Dmodel);
                }
                else if (phase.equals("test")) {
                    System.err.println("test");
                    try {
                        test(raypathInformations);
                    } catch (TauModelException e) {
                        e.printStackTrace();
                    }
                }
        }


    }

    public static void Compute_PcP(List<RaypathInformation> raypathInformations, Seismic3Dmodel seismic3Dmodel) throws IOException {
        String modelName = "prem";

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, "P, PcP");

        traveltimetool.setIgnoreMantle(false);
        traveltimetool.setIgnoreCMBElevation(true);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_PcP = new ArrayList<>();
        List<TraveltimeData> ttData_P = new ArrayList<>();
        Set<StaticCorrectionData> corrections = new HashSet<>();


        String[] pwString = new String[] {""};

        for (List<TraveltimeData> record : ttData) {
//		ttData.stream().parallel().forEach(record -> {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains("PcP") && phases.contains("P"))) continue;
            TraveltimeData ttdP = null;
            TraveltimeData ttdPcP = null;
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals("P")) {
                    ttdP = ttd;
                    ttData_P.add(ttd);
                }
                else if (ttd.getPhaseName().equals("PcP")) {
                    ttdPcP = ttd;
                    ttData_PcP.add(ttd);
                }
            }

//			double shift = -(mPcP.getTraveltimePerturbation() - mP.getTraveltimePerturbation());
            double shift = -(ttdPcP.getTraveltimePerturbationToPREM() - ttdP.getTraveltimePerturbationToPREM());

            pwString[0] += ttdPcP.getScatterPointList().get(0) + " " + shift + "\n";

            StaticCorrectionData correction = new StaticCorrectionData(ttdP.getObserver(), ttdP.getGlobalCMTID()
                    , SACComponent.Z, 0., shift, 1., new Phase[] {Phase.PcP});
            corrections.add(correction);
//		});
        }

        Path bouncepointPath = Paths.get("bouncepointPcP.dat");
        PrintWriter pw = new PrintWriter(bouncepointPath.toFile());
        pw.print(pwString[0]);
        pw.close();

        Path outpath = Paths.get("mantleCorrection_P-PcP.dat");
        StaticCorrectionDataFile.write(corrections, outpath);
    }

    public static void Compute_ScS(List<RaypathInformation> raypathInformations, Seismic3Dmodel seismic3Dmodel) throws IOException {
        String modelName = "prem";

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, "S, ScS");

        traveltimetool.setIgnoreMantle(false);
        traveltimetool.setIgnoreCMBElevation(true);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_ScS = new ArrayList<>();
        List<TraveltimeData> ttData_S = new ArrayList<>();
        Set<StaticCorrectionData> corrections = new HashSet<>();

        Path bouncepointPath = Paths.get("bouncepointScS.dat");
        PrintWriter pw = new PrintWriter(bouncepointPath.toFile());

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains("ScS") && phases.contains("S"))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttdS = null;
            TraveltimeData ttdScS = null;
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals("ScS")) {
                    ttdScS = ttd;
                    ttData_ScS.add(ttd);
                }
                else if (ttd.getPhaseName().equals("S")) {
                    ttdS = ttd;
                    ttData_S.add(ttd);
                }
            }

//			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
            double shift = -(ttdScS.getTraveltimePerturbationToPREM() - ttdS.getTraveltimePerturbationToPREM());

            pw.println(ttdScS.getScatterPointList().get(0) + " " + shift);

            StaticCorrectionData correction = new StaticCorrectionData(ttdS.getObserver(), ttdS.getGlobalCMTID()
                    , SACComponent.T, 0., shift, 1., new Phase[] {Phase.ScS});
            corrections.add(correction);
        }
        pw.close();

        Path outpath = Paths.get("mantleCorrection_S-ScS.dat");
        StaticCorrectionDataFile.write(corrections, outpath);
    }

    public static void test(List<RaypathInformation> raypathInformations) throws IOException, TauModelException {
        String modelName = "prem";

        Seismic3Dmodel seismic3Dmodel = new SEMUCBWM1();

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, "P, PcP");

        traveltimetool.setIgnoreMantle(false);
        traveltimetool.setIgnoreCMBElevation(true);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_PcP = new ArrayList<>();
        List<TraveltimeData> ttData_P = new ArrayList<>();

//		TauP_Time tauptime = new TauP_Time("/usr/local/share/TauP-2.4.5/StdModels/PREM_1000.taup");
        TauP_Time tauptime = new TauP_Time("prem");
        tauptime.parsePhaseList("P, PcP");

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains("PcP") && phases.contains("P"))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttdP = null;
            TraveltimeData ttdPcP = null;
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals("P")) {
                    ttdP = ttd;
                    ttData_P.add(ttd);
                }
                else if (ttd.getPhaseName().equals("PcP")) {
                    ttdPcP = ttd;
                    ttData_PcP.add(ttd);
                }
            }

            tauptime.setSourceDepth(6371. - ttdP.getGlobalCMTID().getEvent().getCmtLocation().getR());
            tauptime.calculate(ttdP.getEpicentralDistance());
            double taup_P = tauptime.getArrival(0).getTime();
            double taup_PcP = tauptime.getArrival(1).getTime();

            System.err.println(ttdPcP.getAbsoluteTraveltimeRef() + " " + ttdPcP.getAbsoluteTraveltimePREM() + " " + taup_PcP
                    + " " + ttdPcP.getTraveltimePerturbation() + " " + ttdPcP.getTraveltimePerturbationToPREM());
        }
    }

}
