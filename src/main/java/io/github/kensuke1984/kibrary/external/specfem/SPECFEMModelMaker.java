package io.github.kensuke1984.kibrary.external.specfem;

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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
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
 * @since a long time ago
 * @version 2024/11/3 Renamed from specfem.Make3DModel to external.specfem.SPECFEMModelMaker
 */
public class SPECFEMModelMaker {

    /**
     * Create model file for SPECFEM.
     * @param args Options.
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // input
        options.addOption(Option.builder("s").longOpt("scalarFile").hasArg().argName("scalarFile").required()
                .desc("Path of scalar list file.").build());

        // settings
        OptionGroup inputOption = new OptionGroup();
        inputOption.setRequired(true);
        inputOption.addOption(Option.builder("i").longOpt("interval").hasArg().argName("interval")
                .desc("Extract perturbation values at the speficied latitude/longitude interval.").build());
        inputOption.addOption(Option.builder("a").longOpt("asIs")
                .desc("Use all input perturbations as is. Note that they must be equally spaced.").build());
        options.addOptionGroup(inputOption);

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path scalarPath = Paths.get(cmdLine.getOptionValue("s"));
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "model", fileTag, appendFileDate, null, ".txt");

        // read interval information
        double interval;
        if (cmdLine.hasOption("i")) {
            interval = Double.parseDouble(cmdLine.getOptionValue("i"));
        } else if (cmdLine.hasOption("a")) {
            interval = 0.0;
        } else {
            throw new IllegalArgumentException("Either -i or -a must be specified.");
        }

        // create model
        List<Perturbation> perturbations = createFromScalarFile(scalarPath, interval);
        writeModel(perturbations, outputPath);

/*
        List<Perturbation> oneLayer = onePerturbationLayer(3580, 3680, 0);
        Path oneLayerPath = Paths.get("oneLayer.txt");
        writeModel(oneLayer, oneLayerPath);

        double[] radii = {3480, 3580, 3680, 3780, 3880};
        List<Perturbation> checkerboard = checkerboard(radii, 10, 10, 2, false);
        Path checkerboardPath = Paths.get("checkerboard.txt");
        writeModel(checkerboard, checkerboardPath);
*/
    }

    private static List<Perturbation> createFromScalarFile(Path scalarPath, double interval) throws IOException {
        Map<FullPosition, Double> scalarMap = ScalarListFile.read(scalarPath);

        // gather positions
        Set<FullPosition> positions = scalarMap.keySet();
        Set<HorizontalPosition> horizontalPositions = positions.stream().map(FullPosition::toHorizontalPosition).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(FullPosition::getR).distinct().sorted().toArray();

        // find latitude geometry information
        double[] latitudes = horizontalPositions.stream().mapToDouble(HorizontalPosition::getLatitude).distinct().sorted().toArray();
        double minLatitude = latitudes[0];
        double maxLatitude = latitudes[latitudes.length - 1];
        // find longitude geometry information
        // I'm not sure if SPECFEM can handle a region crossing the date line, so we will not consider the date line here;
        //   then, longitudes around the globe will be exported when the region crosses the date line. This should be fine for SPECFEM.
        //boolean crossDateLine = HorizontalPosition.crossesDateLine(horizontalPositions);
        double[] longitudes = horizontalPositions.stream().mapToDouble(pos -> pos.getLongitude()).distinct().sorted().toArray();
        double minLongitude = longitudes[0];
        double maxLongitude = longitudes[longitudes.length - 1];

        // decide lat/lon interval
        double dLatitude;
        double dLongitude;
        if (interval > 0.0) {
            // When extracting perturbations at a certain interval, set min/max lat/lon to a multiple of the interval.
            dLatitude = interval;
            dLongitude = interval;
            minLatitude = MathAid.ceil(minLatitude / interval) * interval;
            maxLatitude = MathAid.floor(maxLatitude / interval) * interval;
            minLongitude = MathAid.ceil(minLongitude / interval) * interval;
            maxLongitude = MathAid.floor(maxLongitude / interval) * interval;
        } else {
            // When using the interval of input file, compute its value.
            dLatitude = latitudes[1] - latitudes[0];
            dLongitude = longitudes[1] - longitudes[0];
        }
        int numLatitude = (int) Math.round((maxLatitude - minLatitude) / dLatitude) + 1;
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

    private static List<Perturbation> onePerturbationLayer(double rmin, double rmax, double percentVs) {
        double dR = 5.0;
        List<Perturbation> perturbations = new ArrayList<>();

        int nr = (int) ((rmax - rmin) / dR);
        for (int k = 0; k < nr; k++) {
            double radius = rmax - (k + 0.5) * dR;
            double premVs = DefaultStructure.PREM.mediumAt(radius).get(VariableType.Vs);

            for (int lon = -180; lon <= 175; lon += 5) {
                for (int lat = -85; lat <= 85; lat += 5) {
                    FullPosition position = new FullPosition(lat, lon, radius);
                    Perturbation perturbation = new Perturbation(position, percentVs, premVs);
                    perturbations.add(perturbation);
                }
            }
        }
        return perturbations;
    }

    private static List<Perturbation> checkerboard(double[] borderRadii, double dLatitude, double dLongitude, double percentVs, boolean flipSign) {
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

    private static void writeModel(List<Perturbation> perturbations, Path ppmPath) throws IOException {
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
