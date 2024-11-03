package io.github.kensuke1984.kibrary.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.util.earth.DefaultStructure;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;


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

        List<Perturbation> oneLayer = onePerturbationLayer(3480, 3530, 2);
        Path oneLayerPath = Paths.get("oneLayer.txt");

        double[] radii = {3480, 3580, 3680, 3780, 3880};
        List<Perturbation> checkerboard = checkerboard(radii, 10, 10, 2, false);
        Path checkerboardPath = Paths.get("checkerboard.txt");

        List<Perturbation> custom = createFromScalarFile(Paths.get(args[0]));
        Path customPath = Paths.get("custom.txt");

        writeModel(oneLayer, oneLayerPath);
        writeModel(checkerboard, checkerboardPath);
        writeModel(custom, customPath);
    }

    public static List<Perturbation> createFromScalarFile(Path scalarPath) throws IOException {
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
        // I'm not sure if SPECFEM can handle a region crossing the date line, so we will not consider the date line here;
        //   then, longitudes around the globe will be exported when the region crosses the date line. This should be fine for SPECFEM.
        //boolean crossDateLine = HorizontalPosition.crossesDateLine(horizontalPositions);
        double[] longitudes = horizontalPositions.stream().mapToDouble(pos -> pos.getLongitude()).distinct().sorted().toArray();
        double minLongitude = longitudes[0];
        double maxLongitude = longitudes[longitudes.length - 1];
        double dLongitude = longitudes[1] - longitudes[0];
        int numLongitude = (int) Math.round((maxLongitude - minLongitude) / dLongitude) + 1;

        List<Perturbation> perturbations = new ArrayList<>();

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
                        Perturbation perturbation = new Perturbation(position, percentVs, premVs);
                        perturbations.add(perturbation);
                    } else {
                        Perturbation perturbation = new Perturbation(position, 0.0, premVs);
                        perturbations.add(perturbation);
                    }
                }
            }
        }

        return perturbations;
    }

    public static List<Perturbation> onePerturbationLayer(double rmin, double rmax, double percentVs) {
        double dR = 5.0;
        List<Perturbation> perturbations = new ArrayList<>();

        int nr = (int) ((rmax - rmin) / dR);
        for (int k = 0; k < nr; k++) {
            double radius = rmax - (k + 0.5) * dR;
            double premVs = DefaultStructure.PREM.mediumAt(radius).get(VariableType.Vs);

            for (int lon = -180; lon <= 179; lon++) {
                for (int lat = -89; lat <= 89; lat++) {
                    FullPosition position = new FullPosition(lat, lon, radius);
                    Perturbation perturbation = new Perturbation(position, percentVs, premVs);
                    perturbations.add(perturbation);
                }
            }
        }
        return perturbations;
    }

    public static List<Perturbation> checkerboard(double[] borderRadii, double dLatitude, double dLongitude, double percentVs, boolean flipSign) {
        List<Perturbation> perturbations = new ArrayList<>();

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

            for (int j = 0; j < numLongitude; j++) {
                double longitude = dLongitude * (j + 0.5) - 180;
                for (int k = 0; k < numLatitude; k++) {
                    double latitude = dLatitude * (k + 0.5) - 90;

                    int numDiff = i + j + k;
                    double value = percentVs * (((numDiff % 2 == 1) ^ flipSign) ? 1 : -1); // ^ is XOR
                    FullPosition location = new FullPosition(latitude, longitude, radius);
                    Perturbation perturbation = new Perturbation(location, value, premVs);
                    perturbations.add(perturbation);
                }
            }
        }
        return perturbations;
    }

    public static void writeModel(List<Perturbation> perturbations, Path ppmPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ppmPath))) {
            pw.println("#lon(deg), lat(deg), depth(km), Vs-perturbation_wrt_PREM(%), Vs-PREM (km/s)");
            for (Perturbation perturbation : perturbations) {
                pw.println(perturbation.toString());
            }
        }
    }

    private static class Perturbation {
        FullPosition position;
        double percentVs;
        double premVs;

        public Perturbation(FullPosition position, double percentVs, double premVs) {
            this.position = position;
            this.percentVs = percentVs;
            this.premVs = premVs;
        }
        @Override
        public String toString() {
            return position.getLongitude() + " " + position.getLatitude() + " " + position.getDepth() + " " + percentVs + " " + premVs;
        }

    }

}
