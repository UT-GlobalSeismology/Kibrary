package io.github.afeborgeaud.tomotool.raytheory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;

import edu.sc.seis.TauP.SphericalCoords;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.afeborgeaud.tomotool.topoModel.ExternalModel;
import io.github.afeborgeaud.tomotool.topoModel.GaussianPointPerturbation;
import io.github.afeborgeaud.tomotool.topoModel.LLNLG3DJPS;
import io.github.afeborgeaud.tomotool.topoModel.S20RTS;
import io.github.afeborgeaud.tomotool.topoModel.SEMUCBWM1;
import io.github.afeborgeaud.tomotool.topoModel.Seismic3Dmodel;
import io.github.afeborgeaud.tomotool.topoModel.TK10;
import io.github.afeborgeaud.tomotool.utilities.ReadUtils;
import io.github.afeborgeaud.tomotool.utilities.Utils;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

public class Compute {

    public static void main(String[] args) throws IOException {
        Options options = new Options();

        Option raypathInfoFileOpt = new Option("ri", "raypath-info", true, "path to a raypath information file");
        raypathInfoFileOpt.setRequired(true);
        options.addOption(raypathInfoFileOpt);

        Option modelNameOpt = new Option("m", "model", true, "name of the mantle/topo model, or path to a custom model file");
        modelNameOpt.setRequired(true);
        options.addOption(modelNameOpt);

        Option phaseNameOpt = new Option("p", "phase", true, "seismic phase");
        phaseNameOpt.setRequired(true);
        options.addOption(phaseNameOpt);

        Option customModelNameOpt = new Option("n", "model-name", true, "name of the mantle/topo model (for custom models)");
        customModelNameOpt.setRequired(false);
        options.addOption(customModelNameOpt);

        Option help = new Option("h", "help", false, "print this message");
        options.addOption(help);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Compute", options);

            System.exit(1);
        }

        Path raypathInformationPath = Paths.get(cmd.getOptionValue("raypath-info"));
        String threeDmodel = cmd.getOptionValue("model").trim();
        String phaseName = cmd.getOptionValue("phase").trim();
        String modelName = null;

        if (cmd.hasOption("model-name")) modelName = cmd.getOptionValue("model-name").trim();

        List<RaypathInformation> raypathInformations = RaypathInformation.readRaypathInformation(raypathInformationPath);
        Seismic3Dmodel seismic3Dmodel = null;
        String refModelName = "prem";

        if (modelName != null) seismic3Dmodel = Utils.parse3DModel(threeDmodel, modelName);
        else seismic3Dmodel = Utils.parse3DModel(threeDmodel);

        List<TraveltimeData> ttData_mantle = compute_phase_from_raypathinfo(raypathInformations,
                seismic3Dmodel, refModelName, phaseName, true, false);
        List<TraveltimeData> ttData_topo = compute_phase_from_raypathinfo(raypathInformations,
                seismic3Dmodel, refModelName, phaseName, false, true);

        Path outpath_mantle = Paths.get("dt_" + seismic3Dmodel.getName() + "_" + "mantle" + "_" + phaseName + ".txt");
        Path outpath_topo = Paths.get("dt_" + seismic3Dmodel.getName() + "_" + "topo" + "_" + phaseName + ".txt");
        writeMeasurements(outpath_mantle, ttData_mantle, phaseName);
        writeMeasurements(outpath_topo, ttData_topo, phaseName);
    }

    private static void writeMeasurements(Path outpath, List<TraveltimeData> ttData, String phaseName) {
        try(PrintWriter pw = new PrintWriter(outpath.toFile())) {
            for (TraveltimeData ttd : ttData) {
    //			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
                double shift = ttd.getTraveltimePerturbation();

                for (ScatterPoint p : ttd.getScatterPointList()) {
                    String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() + " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType();
                    pw.println(tmpStr);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void Compute_SmKS() throws IOException {
//		Path workdir = Paths.get("/work/anselme/TOPO");
//		Path resourcePath =  new File("resources").getAbsoluteFile().toPath();
//		Path eventFile = resourcePath.resolve("events_java.inf"); //events.inf
//		Path stationFile = resourcePath.resolve("station_usarray.inf"); // station_usarray.inf station_fnet.inf

        //test Wen-che
//		eventFile = resourcePath.resolve("events_wenche_1.inf");// events_wenche_1.inf events_wenche_small.inf
//		stationFile = resourcePath.resolve("stations_test_wenche.inf");
//		Set<Station> stationSet = ReadUtils.readStationFile(stationFile);
//		Set<GlobalCMTID> eventSet = ReadUtils.readEventFile(eventFile);
//
//		workdir = Paths.get("/Users/navy/Dropbox/Frederic/project/topography_CMB/calculations");

        // ---
        Path workdir = Paths.get("/work/anselme/TOPO/ETH/dataset");
        Path eventFile = workdir.resolve("evt_dataset_1.inf");
//		Path eventFile = resourcePath.resolve("evt_dataset_tonga.inf");
        Path stationFile = workdir.resolve("sta_dataset_1.inf");
        Set<Observer> observerSet = ReadUtils.readStationFile(stationFile);
        Set<GlobalCMTID> eventSet = ReadUtils.readSimpleEventFile(eventFile);

        double minDistance = 90;
        double maxDistance = 140;

        //Select raypaths
        List<RaypathInformation> raypathInformations = new ArrayList<>();
        for (GlobalCMTID event : eventSet) {
            for (Observer observer : observerSet) {
                RaypathInformation raypathInformation = new RaypathInformation(observer, event);
                double distance = raypathInformation.getDistanceDegree();
                boolean userecord = true;
                if (distance < minDistance || distance > maxDistance)
                    userecord = false;
                if (userecord) {
                    raypathInformations.add(raypathInformation);
                }
            }
        }

        String modelName = "prem";

//		Seismic3Dmodel seismic3Dmodel = new SEMUCBWM1();
        Seismic3Dmodel seismic3Dmodel = new TK10();

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, "SKS, SKKS, SKKKS");

        traveltimetool.setIgnoreMantle(true);
        traveltimetool.setIgnoreCMBElevation(false);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_SKS = new ArrayList<>();
        List<TraveltimeData> ttData_SKKS = new ArrayList<>();
        List<TraveltimeData> ttData_S3KS = new ArrayList<>();
        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains("SKS") && phases.contains("SKKS") && phases.contains("SKKKS"))) {
                System.err.println(record);
                continue;
            }
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals("SKS"))
                    ttData_SKS.add(ttd);
                else if (ttd.getPhaseName().equals("SKKS"))
                    ttData_SKKS.add(ttd);
                else if (ttd.getPhaseName().equals("SKKKS"))
                    ttData_S3KS.add(ttd);
            }
        }

//		Path modelOutPath = workdir.resolve("sh18cex.dat");
//		traveltimetool.getSeismic3Dmodel().writeCMBElevationMap(modelOutPath);

//		Path eventPath = workdir.resolve("events.dat");
//		traveltimetool.writeEventInformation(eventPath);

//		Path stationPath = workdir.resolve("stations.dat");
//		traveltimetool.writeStationInformation(stationPath);

        Path pierceSKSPath = workdir.resolve("piercepoints_SKS.dat");
        Path undersideSKKSPath = workdir.resolve("undersidepoints_SKKS.dat");
        Path pierceSKKSPath = workdir.resolve("piercepoints_SKKS.dat");
//		Path timeSKKS = workdir.resolve("traveltime_SKKS-SKS_mantleOnly_dataset1.dat");
//		Path timeS3KS = workdir.resolve("traveltime_S3KS-SKKS_mantleOnly_dataset1.dat");
        Path timeSKKS = workdir.resolve("traveltime_SKKS-SKS_dataset1.dat");
        Path timeS3KS = workdir.resolve("traveltime_S3KS-SKKS_dataset1.dat");
        PrintWriter pwSKS = new PrintWriter(pierceSKSPath.toFile());
        PrintWriter pwSKKS = new PrintWriter(undersideSKKSPath.toFile());
        PrintWriter pwPierceSKKS = new PrintWriter(pierceSKKSPath.toFile());
        PrintWriter pwTimeSKKS = new PrintWriter(timeSKKS.toFile());
        PrintWriter pwTimeS3KS = new PrintWriter(timeS3KS.toFile());
        for (int i = 0; i < ttData_SKS.size(); i++) {
            TraveltimeData mSKS = ttData_SKS.get(i);
            TraveltimeData mSKKS = ttData_SKKS.get(i);
            TraveltimeData mS3KS = ttData_S3KS.get(i);
            mSKS.getScatterPointList().forEach(p -> pwSKS.println(p.getPosition().getLongitude() + " " + p.getPosition().getLatitude()));
            mSKKS.getScatterPointList().stream().filter(p -> p.getType().equals(ScatterType.reflection_under)).forEach(p -> pwSKKS.println(p.getPosition().getLongitude() + " " + p.getPosition().getLatitude()));
            mSKKS.getScatterPointList().stream().filter(p -> p.getType().equals(ScatterType.transmission)).forEach(p -> pwPierceSKKS.println(p.getPosition().getLongitude() + " " + p.getPosition().getLatitude()));
            HorizontalPosition reflectionPointSKKS = mSKKS.getScatterPointList().stream().filter(p -> p.getType().equals(ScatterType.reflection_under)).map(p ->p.getPosition()).findAny().get();
            pwTimeSKKS.println(reflectionPointSKKS + " " + mSKKS.getEpicentralDistance() + " " + (mSKKS.getTraveltimePerturbation() - mSKS.getTraveltimePerturbation()));
            pwTimeS3KS.println(reflectionPointSKKS + " " + mS3KS.getEpicentralDistance() + " " + (mS3KS.getTraveltimePerturbation() - mSKKS.getTraveltimePerturbation()));
        }
        pwSKS.close();
        pwSKKS.close();
        pwTimeSKKS.close();
        pwPierceSKKS.close();
        pwTimeS3KS.close();
    }

    public static void compute_ScS_differential() {
        Path rootpath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS");
        Path rayinfoPath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS/rayinfo_ScS.inf");
        String threeDmodel = "s20rts";
        String phaseRef = "S";
        String phase = "ScS";

        if (!threeDmodel.equals("s20rts"))
            return;

        try {
            List<RaypathInformation> rayinfo = RaypathInformation.readRaypathInformation(rayinfoPath);

            List<RaypathInformation> rayinfo_CA = rayinfo.parallelStream().filter(ray -> {
                double latitude = SphericalCoords.latFor(ray.getCmtLocation().getLatitude(),
                        ray.getCmtLocation().getLongitude(), ray.getDistanceDegree() / 2., ray.getAzimuthDegree());
                double longitude = SphericalCoords.lonFor(ray.getCmtLocation().getLatitude(),
                        ray.getCmtLocation().getLongitude(), ray.getDistanceDegree() / 2., ray.getAzimuthDegree());
                if (latitude >= -10 && latitude <= 30
                    && longitude >= -110 && longitude <= -60)
                    return true;
                else
                    return false;
            }).collect(Collectors.toList());
            System.out.println(rayinfo_CA.size());

            System.out.println("Running long wavelength model");
            int nlmax = 4;
            S20RTS seismic3dmodel = new S20RTS(2.5);
            seismic3dmodel.filter(nlmax);
            compute_phase_differential_raypathinfo(rayinfo, seismic3dmodel, phaseRef, phase, false, true, rootpath);
            compute_phase_differential_raypathinfo(rayinfo, seismic3dmodel, phaseRef, phase, true, false, rootpath);

            System.out.println("Running short wavelength model");
            seismic3dmodel = new S20RTS(-8, 2.5);
            compute_phase_differential_raypathinfo(rayinfo_CA, seismic3dmodel, phaseRef, phase, false, true, rootpath);
            compute_phase_differential_raypathinfo(rayinfo_CA, seismic3dmodel, phaseRef, phase, true, false, rootpath);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public static void test(Path timewindowPath) throws IOException, TauModelException {
        Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowPath);
        timewindows = timewindows.stream().limit(10).collect(Collectors.toSet());

        //Select raypaths
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

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
            for (TraveltimeData m : record) {
                if (m.getPhaseName().equals("P")) {
                    ttdP = m;
                    ttData_P.add(m);
                }
                else if (m.getPhaseName().equals("PcP")) {
                    ttdPcP = m;
                    ttData_PcP.add(m);
                }
            }

            tauptime.setSourceDepth(6371. - ttdP.getGlobalCMTID().getEventData().getCmtPosition().getR());
            tauptime.calculate(ttdP.getEpicentralDistance());
            double taup_P = tauptime.getArrival(0).getTime();
            double taup_PcP = tauptime.getArrival(1).getTime();

            System.out.println(ttdPcP.getAbsoluteTraveltimeRef() + " " + ttdPcP.getAbsoluteTraveltimePREM() + " " + taup_PcP
                    + " " + ttdPcP.getTraveltimePerturbation() + " " + ttdPcP.getTraveltimePerturbationToPREM());
        }
    }

    public static void compute_phase_differential(Path timewindowPath, String threeDmodel, String phaseRefName, String phaseName) throws IOException {
        Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowPath);

        //Select raypaths
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

        String modelName = "prem";

        Seismic3Dmodel seismic3Dmodel = null;
        switch (threeDmodel) {
        case "semucb":
            seismic3Dmodel = new SEMUCBWM1();
            break;
        case "llnlg3d":
            seismic3Dmodel = new LLNLG3DJPS();
            break;
        default:
            throw new RuntimeException("Error: 3D model " + threeDmodel + " not implemented yet");
        }

        String phaseListString = String.format("%s, %s", phaseRefName, phaseName);
        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseListString);

        traveltimetool.setIgnoreMantle(true);
        traveltimetool.setIgnoreCMBElevation(false);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_ScS = new ArrayList<>();
        List<TraveltimeData> ttData_S = new ArrayList<>();
        Set<StaticCorrectionData> corrections = new HashSet<>();

        Path outpath = Paths.get("dt_diff_" + threeDmodel + "_" + phaseRefName + "_" + phaseName + ".dat");
        PrintWriter pw = new PrintWriter(outpath.toFile());

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains(phaseRefName) && phases.contains(phaseName))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttdRef = null;
            TraveltimeData ttd = null;
            for (TraveltimeData ttdTmp : record) {
                if (ttdTmp.getPhaseName().equals(phaseRefName)) {
                    ttdRef = ttdTmp;
                    ttData_ScS.add(ttdRef);
                }
                else if (ttdTmp.getPhaseName().equals(phaseName)) {
                    ttd = ttdTmp;
                    ttData_S.add(ttd);
                }
            }

//			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
            double shift = -(ttd.getTraveltimePerturbationToPREM() - ttdRef.getTraveltimePerturbationToPREM());

            pw.println();
        }
        pw.close();

    }

    public static void compute_phase_differential(Path timewindowPath, String threeDmodel, String phaseRefName,
            String phaseName, boolean switchMantle, boolean switchTopo) throws IOException {
        Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowPath);

        //Select raypaths
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

        String modelName = "prem";

        Seismic3Dmodel seismic3Dmodel = null;
        switch (threeDmodel) {
        case "semucb":
            seismic3Dmodel = new SEMUCBWM1();
            break;
        case "llnlg3d":
            seismic3Dmodel = new LLNLG3DJPS();
            break;
        case "S20RTS":
            seismic3Dmodel = new S20RTS();
            break;
        default:
            throw new RuntimeException("Error: 3D model " + threeDmodel + " not implemented yet");
        }

        String phaseListString = String.format("%s, %s", phaseRefName, phaseName);
        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseListString);

        traveltimetool.setIgnoreMantle(!switchMantle);
        traveltimetool.setIgnoreCMBElevation(!switchTopo);

        String switchString = "";
        if (switchMantle)
            switchString += "3d";
        if (switchTopo)
            switchString += "topo";

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_ScS = new ArrayList<>();
        List<TraveltimeData> ttData_S = new ArrayList<>();
        Set<StaticCorrectionData> corrections = new HashSet<>();

        Path outpath = Paths.get("dt_diff_" + threeDmodel + "_" + switchString + "_" + phaseRefName + "_" + phaseName + ".dat");
        PrintWriter pw = new PrintWriter(outpath.toFile());

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains(phaseRefName) && phases.contains(phaseName))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttdRef = null;
            TraveltimeData ttd = null;
            for (TraveltimeData ttdTmp : record) {
                if (ttdTmp.getPhaseName().equals(phaseRefName)) {
                    ttdRef = ttdTmp;
                    ttData_ScS.add(ttdRef);
                }
                else if (ttdTmp.getPhaseName().equals(phaseName)) {
                    ttd = ttdTmp;
                    ttData_S.add(ttd);
                }
            }

//			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
            double shift = -(ttd.getTraveltimePerturbationToPREM() - ttdRef.getTraveltimePerturbationToPREM());

            pw.println();
        }
        pw.close();

    }

    public static void compute_phase_differential(List<TimewindowData> timewindows, Seismic3Dmodel seismic3Dmodel
            , String phaseRefName, String phaseName, boolean switchMantle, boolean switchTopo) throws IOException {

        //Select raypaths
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

        String modelName = "prem";

        String phaseListString = String.format("%s, %s", phaseRefName, phaseName);
        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseListString);

        traveltimetool.setIgnoreMantle(!switchMantle);
        traveltimetool.setIgnoreCMBElevation(!switchTopo);

        String switchString = "";
        if (switchMantle)
            switchString += "3d";
        if (switchTopo)
            switchString += "topo";

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData_ScS = new ArrayList<>();
        List<TraveltimeData> ttData_S = new ArrayList<>();

        Path outpath = Paths.get("dt_diff_" + seismic3Dmodel.getName() + "_" + switchString + "_" + phaseRefName + "_" + phaseName + ".dat");
        PrintWriter pw = new PrintWriter(outpath.toFile());

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains(phaseRefName) && phases.contains(phaseName))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttdRef = null;
            TraveltimeData ttd = null;
            for (TraveltimeData ttdTmp : record) {
                if (ttdTmp.getPhaseName().equals(phaseRefName)) {
                    ttdRef = ttdTmp;
                    ttData_ScS.add(ttdRef);
                }
                else if (ttdTmp.getPhaseName().equals(phaseName)) {
                    ttd = ttdTmp;
                    ttData_S.add(ttd);
                }
            }

//			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
            double shift = -(ttd.getTraveltimePerturbationToPREM() - ttdRef.getTraveltimePerturbationToPREM());
            ScatterPoint p = ttd.getScatterPointList().get(0);
            String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() + " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType();
            pw.println(tmpStr);
        }
        pw.close();

    }

    private static List<int[]> create_batches(int maxNinBatch, int n_raypaths) {
        List<int[]> batches = new ArrayList<>();
        if (maxNinBatch > n_raypaths)
            batches.add(new int[] {0, n_raypaths});
        else {
            int n_batches = n_raypaths / maxNinBatch;
            if (n_batches % maxNinBatch != 0) n_batches += 1;
            for (int i = 0; i < n_batches - 1; i++)
                batches.add(new int[] {i * maxNinBatch, (i+1) * maxNinBatch});
            batches.add(new int[] {(n_batches-1) * maxNinBatch, n_raypaths});
        }
        return batches;
    }

    public static void compute_phase_differential_raypathinfo(List<RaypathInformation> raypathInformations,
            Seismic3Dmodel seismic3Dmodel, String phaseRefName, String phaseName, boolean switchMantle,
            boolean switchTopo, Path rootpath, String path_suffix) throws IOException {
        String modelName = "prem";

        String phaseListString = String.format("%s, %s", phaseRefName, phaseName);

        String switchString = "";
        if (switchMantle)
            switchString += "3d";
        if (switchTopo)
            switchString += "topo";

        Path outpath = null;
        if (path_suffix != null)
            outpath = rootpath.resolve("dt_diff_" + seismic3Dmodel.getName() + "_" + switchString +
                    "_" + phaseRefName + "_" + phaseName + "_" + path_suffix + ".dat");
        else
            outpath = rootpath.resolve("dt_diff_" + seismic3Dmodel.getName() + "_" +
                    switchString + "_" + phaseRefName + "_" + phaseName + ".dat");

        List<String> outputStrs = new ArrayList<>();

        List<int[]> batches = create_batches((int) (1e4), raypathInformations.size());

//		for (int[] batch : batches) {
        batches.parallelStream().forEach(batch -> {
            Traveltime traveltimetool = new Traveltime(raypathInformations.subList(batch[0], batch[1]),
                    modelName, seismic3Dmodel, phaseListString);

            traveltimetool.setIgnoreMantle(!switchMantle);
            traveltimetool.setIgnoreCMBElevation(!switchTopo);

            traveltimetool.run();
            List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

            List<TraveltimeData> ttData_ScS = new ArrayList<>();
            List<TraveltimeData> ttData_S = new ArrayList<>();

            for (List<TraveltimeData> record : ttData) {
                Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
                if (!(phases.contains(phaseRefName) && phases.contains(phaseName))) {
                    System.err.println(record);
                    continue;
                }
                TraveltimeData ttdRef = null;
                TraveltimeData ttd = null;
                for (TraveltimeData ttdTmp : record) {
                    if (ttdTmp.getPhaseName().equals(phaseRefName)) {
                        ttdRef = ttdTmp;
                        ttData_ScS.add(ttdRef);
                    }
                    else if (ttdTmp.getPhaseName().equals(phaseName)) {
                        ttd = ttdTmp;
                        ttData_S.add(ttd);
                    }
                }

                double shift = (ttd.getTraveltimePerturbationToPREM() - ttdRef.getTraveltimePerturbationToPREM());

                List<String> tmpStrs = new ArrayList<String>();
                if (ttd.getPhaseName().equals("ScS")) {
                    ScatterPoint p = ttd.getScatterPointList().get(0);
                    String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() +
                        " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType() +
                        " " + ttd.getPhaseName();
                    tmpStrs.add(tmpStr);
                }
                else if (ttd.getPhaseName().equals("SKKS") || ttd.getPhaseName().equals("SKKSm")) {
                    ScatterPoint p = ttd.getScatterPointList().get(1);
                    String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() +
                        " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType() +
                        " " + ttd.getPhaseName();
                    tmpStrs.add(tmpStr);
                }

                for (String tmpStr : tmpStrs)
                    outputStrs.add(tmpStr);
            }
//		}
        });

        PrintWriter pw = new PrintWriter(outpath.toFile());
        for (String line : outputStrs)
            pw.println(line);
        pw.close();

    }

    public static void compute_phase_differential_raypathinfo(List<RaypathInformation> raypathInformations,
            Seismic3Dmodel seismic3Dmodel, String phaseRefName, String phaseName, boolean switchMantle,
            boolean switchTopo, Path rootpath) throws IOException {
        compute_phase_differential_raypathinfo(raypathInformations, seismic3Dmodel,
                phaseRefName, phaseName, switchMantle, switchTopo, rootpath, null);
    }

    public static void compute_phase(Path timewindowPath, String threeDmodel, String phaseName, boolean switchMantle, boolean switchTopo) throws IOException {
        Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowPath);

        //Select raypaths
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

        String modelName = "prem";

        Seismic3Dmodel seismic3Dmodel = null;
        switch (threeDmodel) {
        case "semucb":
            seismic3Dmodel = new SEMUCBWM1();
            break;
        case "llnlg3d":
            seismic3Dmodel = new LLNLG3DJPS();
            break;
        case "tanaka10":
            seismic3Dmodel = new TK10();
            break;
        case "gauss":
            seismic3Dmodel = new GaussianPointPerturbation();
            break;
        default:
            throw new RuntimeException("Error: 3D model " + threeDmodel + " not implemented yet");
        }

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseName);

        traveltimetool.setIgnoreMantle(!switchMantle);
        traveltimetool.setIgnoreCMBElevation(!switchTopo);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData = traveltimetool.getMeasurements();

        String switchString = "";
        if (switchMantle)
            switchString += "3d";
        if (switchTopo)
            switchString += "topo";

        Path outpath = Paths.get("dt_" + threeDmodel + "_" + switchString + "_" + phaseName + ".dat");
        PrintWriter pw = new PrintWriter(outpath.toFile());

        for (List<TraveltimeData> record : ttData) {
            Set<String> phases = record.stream().map(p -> p.getPhaseName()).collect(Collectors.toSet());
            if (!(phases.contains(phaseName))) {
                System.err.println(record);
                continue;
            }
            TraveltimeData ttd = null;
            for (TraveltimeData ttdTmp : record) {
                if (ttdTmp.getPhaseName().equals(phaseName)) {
                    ttd = ttdTmp;
                }
            }

//			double shift = -(mScS.getTraveltimePerturbation() - mS.getTraveltimePerturbation());
            double shift = ttd.getTraveltimePerturbation();

            for (ScatterPoint p : ttd.getScatterPointList()) {
                String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() + " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType();
                pw.println(tmpStr);
            }
        }
        pw.close();

    }

    public static List<TraveltimeData> compute_phase(List<TimewindowData> timewindows, Seismic3Dmodel seismic3Dmodel,
            String phaseName) throws IOException {
        List<RaypathInformation> raypathInformations = timewindows.stream()
                .map(tw -> new RaypathInformation(tw.getObserver(), tw.getGlobalCMTID()))
                .collect(Collectors.toList());

        String modelName = "prem";

        Traveltime traveltimetool = new Traveltime(raypathInformations, modelName, seismic3Dmodel, phaseName);

        traveltimetool.setIgnoreMantle(true);
        traveltimetool.setIgnoreCMBElevation(false);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData_tmp = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData = new ArrayList<>();

        System.out.println(timewindows.size() + " " + ttData_tmp.size());

        for (int i = 0; i < timewindows.size(); i++) {
            List<TraveltimeData> record = ttData_tmp.get(i);
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals(phaseName) || ttd.getPhaseName().equals(phaseName + "m")) {
//					Measurement mm = new data.Measurement(timewindows.get(i), m.getTraveltimePerturbation(), 1., 1.);
                    ttData.add(ttd);
                }
                else
                    System.err.println("Warning " + phaseName + " " + ttd.getPhaseName());
            }
        }

        return ttData;
    }


    /**
     * Compute travel-time perturbations due to 3D mantle and CMB topography
     * @param raypathInformations list of raypaths information
     * @param seismic3Dmodel the 3D mantle + topo model to use for computation of travel time computations
     * @param refModelName the reference model for computation of raypaths ("prem" or "ak135")
     * @param phaseName the name of the seismic phase you want to compute (e.g. "ScS")
     * @param switchMantle if true, add the travel-time perturbations due to the 3D mantle
     * @param switchTopo if true, add the travel-time perturbations due to the CMB topography
     * @return ttData list of ttData objects that holds the travel-time perturbations
     */
    public static List<TraveltimeData> compute_phase_from_raypathinfo(
            List<RaypathInformation> raypathInformations, Seismic3Dmodel seismic3Dmodel,
            String refModelName, String phaseName,
            boolean switchMantle, boolean switchTopo) {
        Traveltime traveltimetool = new Traveltime(raypathInformations, refModelName, seismic3Dmodel, phaseName);

        traveltimetool.setIgnoreMantle(!switchMantle);
        traveltimetool.setIgnoreCMBElevation(!switchTopo);

        traveltimetool.run();
        List<List<TraveltimeData>> ttData_tmp = traveltimetool.getMeasurements();

        List<TraveltimeData> ttData = new ArrayList<>();

        System.out.println(raypathInformations.size() + " " + ttData_tmp.size());

        for (int i = 0; i < raypathInformations.size(); i++) {
            List<TraveltimeData> record = ttData_tmp.get(i);
            for (TraveltimeData ttd : record) {
                if (ttd.getPhaseName().equals(phaseName) || ttd.getPhaseName().equals(phaseName + "m")) {
//				if (m.getPhaseName().equals(phaseName)) {
//					Measurement mm = new data.Measurement(timewindows.get(i), m.getTraveltimePerturbation(), 1., 1.);
                    ttData.add(ttd);
                }
                else
                    System.err.println("Warning " + phaseName + " " + ttd.getPhaseName());
            }
        }

        return ttData;
    }

    public static void compute_geodynamics_models(
            boolean switchMantle, boolean switchTopo) {
        Path model_root = Paths.get("/home/anselme/Dropbox/topo_eth_local/models/geodynamics");
        Path out_root = Paths.get("/home/anselme/Dropbox/topo_eth_local/raytheory/geodynamics");
        Path rayinfoPath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS/rayinfo_ScS.inf");
//		Path rayinfoPath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS/rayinfo_dist_90_140.inf");

        String[] phaseNames = new String[] {"ScS"};
//		String[] phaseNames = new String[] {"SKKS"};
//		String[] phaseNames = new String[] {"SKS"};

        String switchString = "";
        if (switchMantle)
            switchString += "3d";
        if (switchTopo)
            switchString += "topo";


        try {
            List<RaypathInformation> rayinfo = RaypathInformation.readRaypathInformation(rayinfoPath);

            Map<GlobalCMTID, Long> eventCounts = rayinfo.stream().collect(
                    Collectors.groupingByConcurrent(RaypathInformation::getEventData, Collectors.counting()));

            System.out.println("Num. raypaths: " + rayinfo.size());

            int lmax = 20;

            double min_depth = 100.;
            double min_record_per_event = 50;
            rayinfo = rayinfo.parallelStream()
                    .filter(rinfo -> {
                        double depth = 6371. - rinfo.getEventData().getEventData().getCmtPosition().getR();
                        int count = eventCounts.get(rinfo.getEventData()).intValue();
                        return depth >= min_depth && count >= min_record_per_event;
                    }).collect(Collectors.toList());

            System.out.println("Num. raypaths after filtering: " + rayinfo.size());
            List<Path> models_path = Files.list(model_root).filter(s -> s.toString().endsWith("_rot_l20.ylm")).collect(Collectors.toList());

            for (Path model_path : models_path) {
                System.out.println(model_path.getFileName());

                String model_name = model_path.getFileName().toString().replace(".ylm", "");
                ExternalModel seismic3Dmodel = new ExternalModel(model_path.toString(), model_name, "s20rts");
                seismic3Dmodel.initVelocityGrid();

                seismic3Dmodel.filter(lmax);

                ArrayRealVector topos = new ArrayRealVector(180*360/4);
                int i = 0;
                for (int lon = -180; lon < 180; lon +=2) {
                    for (int lat = -90; lat < 90; lat += 2) {
                        double topo = seismic3Dmodel.getCMBElevation(new HorizontalPosition(lat, lon));
                        topos.setEntry(i, topo);
                        i++;
                    }
                }
                System.out.println("Max topo: " + topos.getLInfNorm());

                for (String phaseName : phaseNames) {
                    List<TraveltimeData> ttData = compute_phase_from_raypathinfo(rayinfo, seismic3Dmodel, "prem",
                            phaseName, switchMantle, switchTopo);

                    Path outpath = out_root.resolve("dt_" + model_name + "_l" + lmax + "_depth" + min_depth + "_" + switchString + phaseName + ".dat");
                    PrintWriter pw = new PrintWriter(outpath.toFile());

                    for (TraveltimeData ttd : ttData) {
                        double shift = ttd.getTraveltimePerturbationToPREM();
                        List<String> tmpStrs = new ArrayList<String>();
                        if (ttd.getPhaseName().equals("ScS")) {
                            ScatterPoint p = ttd.getScatterPointList().get(0);
                            String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() +
                                " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType() +
                                " " + ttd.getPhaseName();
                            tmpStrs.add(tmpStr);
                        }
                        else if (ttd.getPhaseName().equals("SKKS") || ttd.getPhaseName().equals("SKKSm")) {
                            ScatterPoint p = ttd.getScatterPointList().get(1);
                            String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() +
                                " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType() +
                                " " + ttd.getPhaseName();
                            tmpStrs.add(tmpStr);
                        }
                        else if (ttd.getPhaseName().equals("SKS") || ttd.getPhaseName().equals("SKSm")) {
                            for (ScatterPoint p : ttd.getScatterPointList()) {
                                String tmpStr = String.format("%.5f", shift) + " " + p.getPosition() +
                                    " " + ttd.getEpicentralDistance() + " " + ttd.getAzimuth() + " " + p.getType() +
                                    " " + ttd.getPhaseName();
                                tmpStrs.add(tmpStr);
                            }
                        }

                        for (String s : tmpStrs)
                            pw.println(s);
                    }
                    pw.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void compute_geodynamics_models_diff(boolean switchMantle, boolean switchTopo,
            String phaseString, int lmax) {
        Path model_root = Paths.get("/home/anselme/Dropbox/topo_eth_local/models/geodynamics");
        Path out_root = Paths.get("/home/anselme/Dropbox/topo_eth_local/raytheory/geodynamics");

        Path rayinfoPath = null;
        String phaseRefName = null;
        String[] phaseNames = null;

        if (phaseString.equals("ScS")) {
            rayinfoPath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS/rayinfo_ScS.inf");
            phaseRefName = "S";
            phaseNames = new String[] {"ScS"};
        }
        else if (phaseString.equals("SKKS")) {
            rayinfoPath = Paths.get("/home/anselme/Dropbox/topo_eth_local/eventsmetadata/DATALESS/rayinfo_dist_90_140_test.inf");
            phaseRefName = "SKS";
            phaseNames = new String[] {"SKKS"};
        }
        else {
            return;
        }

        try {
            List<RaypathInformation> rayinfo = RaypathInformation.readRaypathInformation(rayinfoPath);

            Map<GlobalCMTID, Long> eventCounts = rayinfo.stream().collect(
                    Collectors.groupingByConcurrent(RaypathInformation::getEventData, Collectors.counting()));

            System.out.println("Num. raypaths: " + rayinfo.size());

            double min_depth = 100.;
            double min_record_per_event = 50;
            rayinfo = rayinfo.parallelStream()
                    .filter(rinfo -> {
                        double depth = 6371. - rinfo.getEventData().getEventData().getCmtPosition().getR();
                        int count = eventCounts.get(rinfo.getEventData()).intValue();
                        return depth >= min_depth && count >= min_record_per_event;
                    }).collect(Collectors.toList());

            System.out.println("Num. raypaths after filtering: " + rayinfo.size());
            List<Path> models_path = Files.list(model_root).filter(s -> s.toString().endsWith("_rot_l20.ylm")).collect(Collectors.toList());

            for (Path model_path : models_path) {
                System.out.println(model_path.getFileName());

                String model_name = model_path.getFileName().toString().replace(".ylm", "");
                ExternalModel seismic3Dmodel = new ExternalModel(model_path.toString(), model_name, "s20rts");
                seismic3Dmodel.initVelocityGrid();

                seismic3Dmodel.filter(lmax);

                ArrayRealVector topos = new ArrayRealVector(180*360/4);
                int i = 0;
                for (int lon = -180; lon < 180; lon +=2) {
                    for (int lat = -90; lat < 90; lat += 2) {
                        double topo = seismic3Dmodel.getCMBElevation(new HorizontalPosition(lat, lon));
                        topos.setEntry(i, topo);
                        i++;
                    }
                }
                System.out.println("Max topo: " + topos.getLInfNorm());

                for (String phaseName : phaseNames) {
                    String suffix = String.format("l%d_depth%.0f", lmax, min_depth);
                    compute_phase_differential_raypathinfo(rayinfo, seismic3Dmodel,
                            phaseRefName, phaseName, switchMantle, switchTopo, out_root, suffix);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
