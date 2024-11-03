package io.github.kensuke1984.kibrary.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.util.addons.EventCluster;
import io.github.kensuke1984.kibrary.util.earth.DefaultStructure;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;


/**
 * Class to create 3-D model for SPECFEM specified by Vs perturbation w.r.t. PREM at each position (lon, lat, depth).
 *
 * <p>
 * For the method in model_ppm.f90 to correctly retrieve the Vs perturbations,
 * the 3-D model file must be written in the following order:
 * <ul>
 * <li> depths (min to max) </li>
 * <li> longitudes (min to max) </li>
 * <li> latitudes (min to max) </li>
 * </ul>
 *
 * @author Anselme
 */
public class Make3DModel {

    public static void main(String[] args) throws IOException {

        List<PerturbationPoint> oneLayer = onePerturbationLayer(3480, 3530, 2);
        Path oneLayerPath = Paths.get("oneLayer.txt");

        double[] radii = {3480, 3580, 3680, 3780, 3880};
        List<PerturbationPoint> checkerboard = checkerboard(radii, 10, 10, 2, false);
        Path checkerboardPath = Paths.get("checkerboard.txt");

        List<PerturbationPoint> custom = readScalarFile(Paths.get(args[0]));
        Path customPath = Paths.get("custom.txt");

        writeModel(oneLayer, oneLayerPath);
        writeModel(checkerboard, checkerboardPath);
        writeModel(custom, customPath);
    }

    public static List<PerturbationPoint> readScalarFile(Path scalarPath) throws IOException {
        Map<FullPosition, Double> scalarMap = ScalarListFile.read(scalarPath);

        // gather positions
        Set<FullPosition> positions = scalarMap.keySet();
        Set<HorizontalPosition> horizontalPositions = positions.stream().map(FullPosition::toHorizontalPosition).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(FullPosition::getR).distinct().sorted().toArray();

        // find latitude geometry information
        double[] latitudes = horizontalPositions.stream().mapToDouble(HorizontalPosition::getLatitude).distinct().sorted().toArray();
        double minLatitude = latitudes[0];
        double maxLatitude = latitudes[latitudes.length - 1];
        double dLatitude = latitudes[1] - latitudes[0];
        int numLatitude = (int) Math.round((maxLatitude - minLatitude) / dLatitude) + 1;
        // find longitude geometry information
        boolean crossDateLine = HorizontalPosition.crossesDateLine(horizontalPositions);
        double[] longitudes = horizontalPositions.stream().mapToDouble(pos -> pos.getLongitude(crossDateLine)).distinct().sorted().toArray();
        double minLongitude = longitudes[0];
        double maxLongitude = longitudes[longitudes.length - 1];
        double dLongitude = longitudes[1] - longitudes[0];
        int numLongitude = (int) Math.round((maxLongitude - minLongitude) / dLongitude) + 1;

        List<PerturbationPoint> perturbations = new ArrayList<>();

        // radii are called in reverse order so that depths will be in order
        for (int i = radii.length - 1; i >= 0; i--) {
            double radius = radii[i];
            double premVs = DefaultStructure.PREM.mediumAt(radii[i]).get(VariableType.Vs);

            // write values for all positions within the rectangle that includes the input positions
            for (int j = 0; j < numLongitude; j++) {
                double longitude = minLongitude + j * dLongitude;
                for (int k = 0; k < numLatitude; k++) {
                    double latitude = minLatitude + k * dLatitude;
                    FullPosition position = new FullPosition(latitude, longitude, radius);

                    // if input file contains this position, write its value; else, write 0
                    if (positions.contains(position)) {
                        double percentVs = scalarMap.get(position);
                        PerturbationPoint perturbation = new PerturbationPoint(position, percentVs, premVs);
                        perturbations.add(perturbation);
                    } else {
                        PerturbationPoint perturbation = new PerturbationPoint(position, 0.0, premVs);
                        perturbations.add(perturbation);
                    }
                }
            }
        }

        return perturbations;
    }

    public static List<PerturbationPoint> onePerturbationLayer(double rmin, double rmax, double percentVs) {
        double dR = 5.0;
        List<PerturbationPoint> perturbations = new ArrayList<>();

        int nr = (int) ((rmax - rmin) / dR);
        for (int k = 0; k < nr; k++) {
            double radius = rmax - (k + 0.5) * dR;
            double premVs = DefaultStructure.PREM.mediumAt(radius).get(VariableType.Vs);

            for (int lon = -180; lon <= 179; lon++) {
                for (int lat = -89; lat <= 89; lat++) {
                    FullPosition position = new FullPosition(lat, lon, radius);
                    PerturbationPoint perturbation = new PerturbationPoint(position, percentVs, premVs);
                    perturbations.add(perturbation);
                }
            }
        }
        return perturbations;
    }

    public static List<PerturbationPoint> checkerboard(double[] borderRadii, double dLatitude, double dLongitude, double percentVs, boolean flipSign) {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        double[] centerRadii = new double[borderRadii.length - 1];
        for (int i = 0; i < centerRadii.length; i++) {
            centerRadii[i] = (borderRadii[i] + borderRadii[i + 1]) / 2;
        }

        double divLatitude = 180 / dLatitude;
        if (!Precision.equals(divLatitude, Math.round(divLatitude), 0.01)) {
            throw new IllegalArgumentException("dLatitude must divide 180");
        }
        int numLatitude = (int) Math.round(divLatitude);

        double divLongitude = 360 / dLongitude;
        if (!Precision.equals(divLongitude, Math.round(divLongitude), 0.01)) {
            throw new IllegalArgumentException("dLongitude must divide 360");
        }
        int numLongitude = (int) Math.round(divLongitude);

        // radii are called in reverse order so that depths will be in order
        for (int i = centerRadii.length - 1; i >= 0; i--) {
            double radius = centerRadii[i];
            double premVs = DefaultStructure.PREM.mediumAt(radius).get(VariableType.Vs);

/*                for (int j = 0; j < numLongitude; j++) {
            double longitude = dLongitude * (j + 0.5) - 180;
            for (int k = 0; k < numLatitude; k++) {
                double latitude = dLatitude * (k + 0.5) - 90;
*/
            for (int j = 0; j < numLongitude; j++) {
                double longitude = dLongitude * (j + 0.25) - 180;
                for (int k = 0; k < numLatitude - 1; k++) {
                    double latitude = dLatitude * (k + 0.25) - 90;

                    int numDiff = i + j + k;
                    double value = percentVs * (((numDiff % 2 == 1) ^ flipSign) ? 1 : -1); // ^ is XOR
                    FullPosition location = new FullPosition(latitude, longitude, radius);
                    PerturbationPoint perturbation = new PerturbationPoint(location, value, premVs);
                    perturbations.add(perturbation);
                }
            }
        }
        return perturbations;
    }


    public static void writeModel(List<PerturbationPoint> perturbations, Path ppmPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ppmPath))) {
            pw.println("#lon(deg), lat(deg), depth(km), Vs-perturbation_wrt_PREM(%), Vs-PREM (km/s)");
            for (PerturbationPoint perturbation : perturbations) {
                pw.println(perturbation.toString());
            }
        }
    }

    private static class PerturbationPoint {
        FullPosition position;
        double percentVs;
        double premVs;

        public PerturbationPoint(FullPosition position, double dvs) {
            this.position = position;
            this.percentVs = dvs;
            this.premVs = 0;
        }
        public PerturbationPoint(FullPosition position, double percentVs, double premVs) {
            this.position = position;
            this.percentVs = percentVs;
            this.premVs = premVs;
        }
        @Override
        public String toString() {
            return position.getLongitude() + " " + position.getLatitude() + " " + position.getDepth() + " " + percentVs + " " + premVs;
        }
    }






    public static List<PerturbationPoint> from1DmodelsCl4() {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        Path eventClusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
        Path root = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster4/oneDPartial_sw_it1/inversion/each_6deg/8s/SPECTRUM/NEW/");
        Path root2 = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/oneDPartialPREM_Q165/inversion/40km/8s/each_3deg/NEW/");
        Path cl4az0Path = root.resolve("lmi_7279_AB_vs_cl4_az0_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az1Path = root.resolve("lmi_7279_AB_vs_cl4_az1_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az2Path = root.resolve("lmi_7279_AB_vs_cl4_az2_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az3Path = root2.resolve("lmi_7079_AB_vs_cl4_az3_l02_g02_lq06_gq08_semucb_noAmpCorr");
        Path cl4az4Path = root.resolve("lmi_7279_AB_vs_cl4_az4_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az5Path = root.resolve("lmi_7279_AB_vs_cl4_az5_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");

        List<EventCluster> clusters = null;
        try {
            clusters = EventCluster.readClusterFile(eventClusterPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<Double, Double> vel0 = readVel(cl4az0Path);
        Map<Double, Double> vel1 = readVel(cl4az1Path);
        Map<Double, Double> vel2 = readVel(cl4az2Path);
        Map<Double, Double> vel3 = readVel(cl4az3Path);
        Map<Double, Double> vel4 = readVel(cl4az4Path);
        Map<Double, Double> vel5 = readVel(cl4az5Path);

        double lonmin = -120;
        double lonmax = -50;
        double latmin = -20;
        double latmax = 40;
        double dl = 0.25;
        int nlat = (int) ((latmax - latmin) / dl) + 1;
        int nlon = (int) ((lonmax - lonmin) / dl) + 1;

        double h = 40;

        double[] depths = new double[] {2371,2411,2451,2491,2531,2571,2611,2651,2691,2731,2771,2811,2851,2891};

        EventCluster cluster = clusters.stream().filter(c -> c.getIndex() == 4).findFirst().get();

        for (double depth : depths) {
            for (int ilon = 0; ilon <nlon; ilon++) {
                double lon = lonmin + ilon * dl;
                for (int ilat = 0; ilat < nlat; ilat++) {
                    double lat = latmin + ilat * dl;
                    FullPosition loc = new FullPosition(lat, lon, 6371 - depth);
                    double dv = 0;
                    if (depth == 2371)
                        dv = 0.;
                    else {
                        double azimuth = Math.toDegrees(cluster.getCenterPosition().computeAzimuthRad(loc));
                        if (azimuth < 180) azimuth += 360;

                        int iaz = -1;
                        for (int i = 0; i < 6; i++) {
                            double azmin = cluster.getAzimuthBound(i)[0];
                            double azmax = cluster.getAzimuthBound(i)[1];

    //						if (i == 0) azmin = 0;
                            if (i == 5) azmax = 720;
                            if (azimuth >= azmin && azimuth < azmax) iaz = i;
                        }

                        double tmpdepth = depth == 2891 ? depth : depth + h;

                        switch (iaz) {
                        case 0:
                            dv = vel0.get(tmpdepth);
                            break;
                        case 1:
                            dv = vel1.get(tmpdepth);
                            break;
                        case 2:
                            dv = vel2.get(tmpdepth);
                            break;
                        case 3:
                            dv = vel3.get(tmpdepth);
                            break;
                        case 4:
                            dv = vel4.get(tmpdepth);
                            break;
                        case 5:
                            dv = vel5.get(tmpdepth);
                            break;
                        default:
                            break;
                        }
                    }

                    perturbations.add(new PerturbationPoint(loc, dv));
                }
            }
        }

        return perturbations;
    }

    public static List<PerturbationPoint> from1DmodelsCl4_simple() {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        Path eventClusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
        Path root = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster4/oneDPartial_sw_it1/inversion/each_6deg/8s/SPECTRUM/NEW/");
        Path root2 = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/oneDPartialPREM_Q165/inversion/40km/8s/each_3deg/NEW/");
        Path cl4az0Path = root.resolve("lmi_7279_AB_vs_cl4_az0_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az1Path = root.resolve("lmi_7279_AB_vs_cl4_az1_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az2Path = root.resolve("lmi_7279_AB_vs_cl4_az2_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az3Path = root2.resolve("lmi_7079_AB_vs_cl4_az3_l02_g02_lq06_gq08_semucb_noAmpCorr");
        Path cl4az4Path = root.resolve("lmi_7279_AB_vs_cl4_az4_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");
        Path cl4az5Path = root.resolve("lmi_7279_AB_vs_cl4_az5_l04_g02_lq12_gq06_semucb_ef1.5_noAmpCorr");

        List<EventCluster> clusters = null;
        try {
            clusters = EventCluster.readClusterFile(eventClusterPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<Double, Double> vel0 = readVel(cl4az0Path);
        Map<Double, Double> vel1 = readVel(cl4az1Path);
        Map<Double, Double> vel2 = readVel(cl4az2Path);
        Map<Double, Double> vel3 = readVel(cl4az3Path);
        Map<Double, Double> vel4 = readVel(cl4az4Path);
        Map<Double, Double> vel5 = readVel(cl4az5Path);

//		double dv0 = vel0.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv0 /= vel0.values().size();
//		double dv1 = vel1.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv1 /= vel1.values().size();
//		double dv2 = vel2.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv2 /= vel2.values().size();
//		double dv3 = vel3.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv3 /= vel3.values().size();
//		double dv4 = vel4.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv4 /= vel4.values().size();
//		double dv5 = vel5.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv5 /= vel5.values().size();
//
//		double dv_high_cmb = (dv0 + dv1 + dv2 + dv4 + dv5) / 5. * (2891 - 2400) / 150. * 2;
//		double dv_low_cmb = dv3 * (2891 - 2400) / 150. * 2;
//
//		System.out.println(dv_high_cmb + " " + dv_low_cmb);

        Map<Double, Double> vel_high = new HashMap<>();
        Map<Double, Double> vel_low = vel3;

        PolynomialStructure model = null;
        try {
            model = PolynomialStructureFile.read(Paths.get("/work/anselme/POLY/sw_it1.poly"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (Double depth : vel0.keySet()) {
            double dv = 0;
            if (depth > 2600)
                dv = (vel0.get(depth) + vel1.get(depth) + vel2.get(depth) + vel4.get(depth) + vel5.get(depth)) / 5.;
            else {
                double r = 6371 - depth;
                dv = (model.getAtRadius(VariableType.Vsh, r) - DefaultStructure.PREM.getAtRadius(VariableType.Vsh, r)) / DefaultStructure.PREM.getAtRadius(VariableType.Vsh, r) * 100;
            }
            vel_high.put(depth, dv);
        }

        Path outpath_low = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/SPECFEM_MODELS/CL4/model_low.txt");
        Path outpath_high = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/SPECFEM_MODELS/CL4/model_high.txt");
        try {
            PrintWriter pw_low = new PrintWriter(outpath_low.toFile());
            PrintWriter pw_high = new PrintWriter(outpath_high.toFile());

            List<Double> sortedDepths = vel0.keySet().stream().collect(Collectors.toList());
            Collections.sort(sortedDepths);

            for (Double depth : sortedDepths) {
                double vlow = DefaultStructure.PREM.getAtRadius(VariableType.Vsh, 6371 - depth) * (1 + vel_low.get(depth) / 100.);
                double vhigh = PolynomialStructureFile.read(Paths.get("/work/anselme/POLY/sw_it1.poly")).getAtRadius(VariableType.Vsh, 6371. - depth)
                        * (1 + vel_high.get(depth) / 100.);
                pw_low.println(depth + " " + vlow + " " + vlow + " " + vlow);
                pw_high.println(depth + " " + vhigh + " " + vhigh + " " + vhigh);
            }

            pw_low.close();
            pw_high.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        double lonmin = -120;
        double lonmax = -50;
        double latmin = -20;
        double latmax = 40;
        double dl = 0.25;
        int nlat = (int) ((latmax - latmin) / dl) + 1;
        int nlon = (int) ((lonmax - lonmin) / dl) + 1;

        double h = 40;

        double[] depths = new double[] {2371,2411,2451,2491,2531,2571,2611,2651,2691,2731,2771,2811,2851,2891};

        EventCluster cluster = clusters.stream().filter(c -> c.getIndex() == 4).findFirst().get();

        for (double depth : depths) {
            for (int ilon = 0; ilon <nlon; ilon++) {
                double lon = lonmin + ilon * dl;
                for (int ilat = 0; ilat < nlat; ilat++) {
                    double lat = latmin + ilat * dl;
                    FullPosition loc = new FullPosition(lat, lon, 6371 - depth);
                    double dv = 0;
                    if (depth == 2371)
                        dv = 0.;
                    else {
                        double azimuth = Math.toDegrees(cluster.getCenterPosition().computeAzimuthRad(loc));
                        if (azimuth < 180) azimuth += 360;

                        int iaz = -1;
                        for (int i = 0; i < 6; i++) {
                            double azmin = cluster.getAzimuthBound(i)[0];
                            double azmax = cluster.getAzimuthBound(i)[1];

    //						if (i == 0) azmin = 0;
                            if (i == 5) azmax = 720;
                            if (azimuth >= azmin && azimuth < azmax) iaz = i;
                        }

                        double tmpdepth = depth == 2891 ? depth : depth + h;

                        switch (iaz) {
                        case 0:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 1:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 2:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 3:
                            dv = vel_low.get(tmpdepth);
                            break;
                        case 4:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 5:
                            dv = vel_high.get(tmpdepth);
                            break;
                        default:
                            break;
                        }
                    }

                    perturbations.add(new PerturbationPoint(loc, dv));
                }
            }
        }

        return perturbations;
    }



    public static List<PerturbationPoint> from1DmodelsCl3() {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        Path eventClusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
        Path root = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster3/oneDPartial_cl3s0_it2/inversion/40km/8s/NEW");
        Path root2 = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/oneDPartialPREM_Q165/inversion/40km/8s/each_3deg/NEW");
        Path cl3az0Path = root.resolve("lmi_7079_AB_vs_cl3_az0_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az1Path = root.resolve("lmi_7079_AB_vs_cl3_az1_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az2Path = root.resolve("lmi_7079_AB_vs_cl3_az2_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az3Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az4Path = root2.resolve("lmi_7079_4AB_vs_cl3_az4_cl5_az1_l04_g02_lq06_gq12_semucb_noAmpCorr");
        Path cl3az5Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");

        List<EventCluster> clusters = null;
        try {
            clusters = EventCluster.readClusterFile(eventClusterPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<Double, Double> vel0 = readVel(cl3az0Path);
        Map<Double, Double> vel1 = readVel(cl3az1Path);
        Map<Double, Double> vel2 = readVel(cl3az2Path);
        Map<Double, Double> vel3 = readVel(cl3az3Path);
        Map<Double, Double> vel4 = readVel(cl3az4Path);
        Map<Double, Double> vel5 = readVel(cl3az5Path);

        double lonmin = -120;
        double lonmax = -50;
        double latmin = -20;
        double latmax = 40;
        double dl = 0.25;
        int nlat = (int) ((latmax - latmin) / dl) + 1;
        int nlon = (int) ((lonmax - lonmin) / dl) + 1;

        double h = 40;

        double[] depths = new double[] {2371,2411,2451,2491,2531,2571,2611,2651,2691,2731,2771,2811,2851,2891};

        EventCluster cluster = clusters.stream().filter(c -> c.getIndex() == 3).findFirst().get();

        for (double depth : depths) {
            for (int ilon = 0; ilon <nlon; ilon++) {
                double lon = lonmin + ilon * dl;
                for (int ilat = 0; ilat < nlat; ilat++) {
                    double lat = latmin + ilat * dl;
                    FullPosition loc = new FullPosition(lat, lon, 6371 - depth);
                    double dv = 0;
                    if (depth == 2371)
                        dv = 0.;
                    else {
                        double azimuth = Math.toDegrees(cluster.getCenterPosition().computeAzimuthRad(loc));
                        if (azimuth < 180) azimuth += 360;

                        int iaz = -1;
                        for (int i = 0; i < 6; i++) {
                            double azmin = cluster.getAzimuthBound(i)[0];
                            double azmax = cluster.getAzimuthBound(i)[1];

    //						if (i == 0) azmin = 0;
                            if (i == 5) azmax = 720;
                            if (azimuth >= azmin && azimuth < azmax) iaz = i;
                        }

                        double tmpdepth = depth == 2891 ? depth : depth + h;

                        switch (iaz) {
                        case 0:
                            dv = vel0.get(tmpdepth);
                            break;
                        case 1:
                            dv = vel1.get(tmpdepth);
                            break;
                        case 2:
                            dv = vel2.get(tmpdepth);
                            break;
                        case 3:
                            dv = vel3.get(tmpdepth);
                            break;
                        case 4:
                            dv = vel4.get(tmpdepth);
                            break;
                        case 5:
                            dv = vel5.get(tmpdepth);
                            break;
                        default:
                            break;
                        }
                    }

                    perturbations.add(new PerturbationPoint(loc, dv));
                }
            }
        }

        return perturbations;
    }

    public static List<PerturbationPoint> from1DmodelsCl3_simple() {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        Path eventClusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
        Path root = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster3/oneDPartial_cl3s0_it2/inversion/40km/8s/NEW");
        Path root2 = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/oneDPartialPREM_Q165/inversion/40km/8s/each_3deg/NEW");
        Path cl3az0Path = root.resolve("lmi_7079_AB_vs_cl3_az0_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az1Path = root.resolve("lmi_7079_AB_vs_cl3_az1_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az2Path = root.resolve("lmi_7079_AB_vs_cl3_az2_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az3Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az4Path = root2.resolve("lmi_7079_4AB_vs_cl3_az4_cl5_az1_l04_g02_lq06_gq12_semucb_noAmpCorr");
        Path cl3az5Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");

        List<EventCluster> clusters = null;
        try {
            clusters = EventCluster.readClusterFile(eventClusterPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<Double, Double> vel0 = readVel(cl3az0Path);
        Map<Double, Double> vel1 = readVel(cl3az1Path);
        Map<Double, Double> vel2 = readVel(cl3az2Path);
        Map<Double, Double> vel3 = readVel(cl3az3Path);
        Map<Double, Double> vel4 = readVel(cl3az4Path);
        Map<Double, Double> vel5 = readVel(cl3az5Path);

//		double dv0 = vel0.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv0 /= vel0.values().size();
//		double dv1 = vel1.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv1 /= vel1.values().size();
//		double dv2 = vel2.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv2 /= vel2.values().size();
//		double dv3 = vel3.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv3 /= vel3.values().size();
//		double dv4 = vel4.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv4 /= vel4.values().size();
//		double dv5 = vel5.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv5 /= vel5.values().size();
//
//		double dv_high_cmb = (dv0 + dv1 + dv2 + dv3 + dv5) / 5. * (2891 - 2400) / 150. * 2;
//		double dv_low_cmb = dv4 * (2891 - 2400) / 150. * 2;

//		System.out.println(dv_high_cmb + " " + dv_low_cmb);

        Map<Double, Double> vel_high = new HashMap<>();
        Map<Double, Double> vel_low = vel4;

        for (Double depth : vel0.keySet()) {
            double dv = 0;
            if (depth > 2600)
                dv = (vel0.get(depth) + vel1.get(depth) + vel2.get(depth) + vel3.get(depth)) / 4.;
            vel_high.put(depth, dv);
        }

        Path outpath_low = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/SPECFEM_MODELS/CL3/model_low.txt");
        Path outpath_high = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/SPECFEM_MODELS/CL3/model_high.txt");
        try {
            PrintWriter pw_low = new PrintWriter(outpath_low.toFile());
            PrintWriter pw_high = new PrintWriter(outpath_high.toFile());

            List<Double> sortedDepths = vel0.keySet().stream().collect(Collectors.toList());
            Collections.sort(sortedDepths);

            for (Double depth : sortedDepths) {
                double vlow = DefaultStructure.PREM.getAtRadius(VariableType.Vsh, 6371 - depth) * (1 + vel_low.get(depth) / 100.);
                double vhigh = PolynomialStructureFile.read(Paths.get("/work/anselme/POLY/cl3az0_it2.poly")).getAtRadius(VariableType.Vsh, 6371. - depth)
                        * (1 + vel_high.get(depth) / 100.);
                pw_low.println(depth + " " + vlow + " " + vlow + " " + vlow);
                pw_high.println(depth + " " + vhigh + " " + vhigh + " " + vhigh);
            }

            pw_low.close();
            pw_high.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        double lonmin = -120;
        double lonmax = -50;
        double latmin = -20;
        double latmax = 40;
        double dl = 0.25;
        int nlat = (int) ((latmax - latmin) / dl) + 1;
        int nlon = (int) ((lonmax - lonmin) / dl) + 1;

        double h = 40;

        double[] depths = new double[] {2371,2411,2451,2491,2531,2571,2611,2651,2691,2731,2771,2811,2851,2891};

        EventCluster cluster = clusters.stream().filter(c -> c.getIndex() == 3).findFirst().get();

        for (double depth : depths) {
            for (int ilon = 0; ilon <nlon; ilon++) {
                double lon = lonmin + ilon * dl;
                for (int ilat = 0; ilat < nlat; ilat++) {
                    double lat = latmin + ilat * dl;
                    FullPosition loc = new FullPosition(lat, lon, 6371 - depth);
                    double dv = 0;
                    if (depth == 2371)
                        dv = 0.;
                    else {
                        double azimuth = Math.toDegrees(cluster.getCenterPosition().computeAzimuthRad(loc));
                        if (azimuth < 180) azimuth += 360;

                        int iaz = -1;
                        for (int i = 0; i < 6; i++) {
                            double azmin = cluster.getAzimuthBound(i)[0];
                            double azmax = cluster.getAzimuthBound(i)[1];

    //						if (i == 0) azmin = 0;
                            if (i == 5) azmax = 720;
                            if (azimuth >= azmin && azimuth < azmax) iaz = i;
                        }

                        double tmpdepth = depth == 2891 ? depth : depth + h;

                        switch (iaz) {
                        case 0:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 1:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 2:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 3:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 4:
                            dv = vel_low.get(tmpdepth);
                            break;
                        case 5:
                            dv = vel_high.get(tmpdepth);
                            break;
                        default:
                            break;
                        }
                    }

                    perturbations.add(new PerturbationPoint(loc, dv));
                }
            }
        }

        return perturbations;
    }

    public static List<PerturbationPoint> from1DmodelsCl5_simple() {
        List<PerturbationPoint> perturbations = new ArrayList<>();

        Path eventClusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
        Path root = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster3/oneDPartial_cl3s0_it2/inversion/40km/8s/NEW");
        Path root2 = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/oneDPartialPREM_Q165/inversion/40km/8s/each_3deg/NEW");
        Path cl3az0Path = root.resolve("lmi_7079_AB_vs_cl3_az0_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az1Path = root.resolve("lmi_7079_AB_vs_cl3_az1_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az2Path = root.resolve("lmi_7079_AB_vs_cl3_az2_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az3Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");
        Path cl3az4Path = root2.resolve("lmi_7079_4AB_vs_cl3_az4_cl5_az1_l04_g02_lq06_gq12_semucb_noAmpCorr");
        Path cl3az5Path = root.resolve("lmi_7079_AB_vs_cl3_az3_l08_g04_lq12_gq12_semucb_noAmpCorr");

        List<EventCluster> clusters = null;
        try {
            clusters = EventCluster.readClusterFile(eventClusterPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<Double, Double> vel0 = readVel(cl3az0Path);
        Map<Double, Double> vel1 = readVel(cl3az1Path);
        Map<Double, Double> vel2 = readVel(cl3az2Path);
        Map<Double, Double> vel3 = readVel(cl3az3Path);
        Map<Double, Double> vel4 = readVel(cl3az4Path);
        Map<Double, Double> vel5 = readVel(cl3az5Path);

//		double dv0 = vel0.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv0 /= vel0.values().size();
//		double dv1 = vel1.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv1 /= vel1.values().size();
//		double dv2 = vel2.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv2 /= vel2.values().size();
//		double dv3 = vel3.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv3 /= vel3.values().size();
//		double dv4 = vel4.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv4 /= vel4.values().size();
//		double dv5 = vel5.values().stream().reduce((v1, v2) -> v1 + v2).get();
//		dv5 /= vel5.values().size();
//
//		double dv_high_cmb = (dv0 + dv1 + dv2 + dv3 + dv5) / 5. * (2891 - 2400) / 150. * 2;
//		double dv_low_cmb = dv4 * (2891 - 2400) / 150. * 2;

//		System.out.println(dv_high_cmb + " " + dv_low_cmb);

        Map<Double, Double> vel_high = new HashMap<>();
        Map<Double, Double> vel_low = vel4;

        for (Double depth : vel0.keySet()) {
            double dv = 0;
            if (depth > 2600)
                dv = (vel0.get(depth) + vel1.get(depth) + vel2.get(depth) + vel3.get(depth)) / 4.;
            vel_high.put(depth, dv);
        }

        double lonmin = -120;
        double lonmax = -50;
        double latmin = -20;
        double latmax = 40;
        double dl = 0.25;
        int nlat = (int) ((latmax - latmin) / dl) + 1;
        int nlon = (int) ((lonmax - lonmin) / dl) + 1;

        double h = 40;

        double[] depths = new double[] {2371,2411,2451,2491,2531,2571,2611,2651,2691,2731,2771,2811,2851,2891};

        EventCluster cluster = clusters.stream().filter(c -> c.getIndex() == 5).findFirst().get();

        for (double depth : depths) {
            for (int ilon = 0; ilon <nlon; ilon++) {
                double lon = lonmin + ilon * dl;
                for (int ilat = 0; ilat < nlat; ilat++) {
                    double lat = latmin + ilat * dl;
                    FullPosition loc = new FullPosition(lat, lon, 6371 - depth);
                    double dv = 0;
                    if (depth == 2371)
                        dv = 0.;
                    else {
                        double azimuth = Math.toDegrees(cluster.getCenterPosition().computeAzimuthRad(loc));
                        if (azimuth < 180) azimuth += 360;

                        int iaz = -1;
                        for (int i = 0; i < 3; i++) {
                            double azmin = cluster.getAzimuthBound(i)[0];
                            double azmax = cluster.getAzimuthBound(i)[1];

    //						if (i == 0) azmin = 0;
                            if (i == 2) azmax = 720;
                            if (azimuth >= azmin && azimuth < azmax) iaz = i;
                        }

                        double tmpdepth = depth == 2891 ? depth : depth + h;

                        switch (iaz) {
                        case 0:
                            dv = vel_high.get(tmpdepth);
                            break;
                        case 1:
                            dv = vel_low.get(tmpdepth);
                            break;
                        case 2:
                            dv = vel_high.get(tmpdepth);
                            break;
                        default:
                            break;
                        }
                    }

                    perturbations.add(new PerturbationPoint(loc, dv));
                }
            }
        }

        return perturbations;
    }

    private static Map<Double, Double> readVel(Path inversionRootPath) {
        PolynomialStructure model = DefaultStructure.PREM;
        Map<Double, Double> velMap = new HashMap<Double, Double>();
        List<String> lines = null;
        try {
            lines = Files.readAllLines(inversionRootPath.resolve("CG/velocityCG12.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] ss = lines.get(1).split("\\s+");
        double r = Double.valueOf(ss[0]);;
        double depth = 6371. - r;
        double dv = (Double.valueOf(ss[1]) - model.getAtRadius(VariableType.Vsh, r)) / Double.valueOf(ss[2]) * 100;
        velMap.put(depth, dv);
        for (int i = 3; i < lines.size() - 1; i += 2) {
            ss = lines.get(i).split("\\s+");
            r = Double.valueOf(ss[0]);
            depth = 6371. - r;
            dv = (Double.valueOf(ss[1]) - model.getAtRadius(VariableType.Vsh, r)) / Double.valueOf(ss[2]) * 100;
            velMap.put(depth, dv);
        }
        ss = lines.get(lines.size() - 1).split("\\s+");
        depth = 6371. - Double.valueOf(ss[0]);
        dv = 0.;
        velMap.put(depth, dv);
        return velMap;
    }

}


