package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * File of layer information.
 * <p>
 * The file format is as below: <br>
 * h1 h2 h3..... hn (Layer thicknesses, from the ones closer to the center of planet)<br>
 * r1 r2 r3..... rn (Radii, cannot have duplicate values, must be sorted)<br>
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2023/4/12
 */
public class LayerInformationFile {

    /**
     * thickness of each layer
     */
    private final double[] layerThicknesses;
    /**
     * Radii of voxel center points, sorted, no duplication
     */
    private final double[] layerRadii;

    /**
     * Writes a layer information file given arrays of radii.
     * @param layerThicknesses (double[]) Must be in the same order as voxelRadii.
     * @param layerRadii (double[])  The radii should be sorted, and there should be no duplication.
     * @param outputPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(double[] layerThicknesses, double[] layerRadii, Path outputPath, OpenOption... options) throws IOException {
        if (layerThicknesses.length != layerRadii.length)
            throw new IllegalArgumentException("The number of thicknesses and radii does not match.");

        System.err.println("Outputting "
                + MathAid.switchSingularPlural(layerRadii.length, "layer", "layers")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# thicknesses of each layer [km]");
            for (double thickness : layerThicknesses) {
                pw.print(thickness + " ");
            }
            pw.println("");

            pw.println("# radii of center points of each layer [km]");
            for (double radius : layerRadii) {
                pw.print(radius + " ");
            }
            pw.println("");
        }
    }

    /**
     * Reads in a layer information file.
     * @param filePath (Path)
     * @throws IOException
     */
    public LayerInformationFile(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath, true);

        layerThicknesses = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        layerRadii = Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        if (layerThicknesses.length != layerRadii.length)
            throw new IllegalArgumentException("The number of thicknesses and radii does not match.");

        DatasetAid.checkNum(layerRadii.length, "layer", "layers");
    }

    /**
     * Create information for layer file from array of border radii.
     * @param borderRadii (double[]) Radii of layer borders [km]; [0:).
     */
    public LayerInformationFile(double[] borderRadii) {
        layerThicknesses = new double[borderRadii.length - 1];
        layerRadii = new double[borderRadii.length - 1];
        for (int i = 0; i < borderRadii.length - 1; i++) {
            layerThicknesses[i] = borderRadii[i + 1] - borderRadii[i];
            layerRadii[i] = (borderRadii[i] + borderRadii[i + 1]) / 2;
        }
    }

    /**
     * Create information for layer file from lower & upper border radus and dRadius.
     * @param lowerRadius (double) Lower limit of radius [km]; [0:upperRadius).
     * @param upperRadius (double) Upper limit of radius [km]; (lowerRadius:).
     * @param dRadius (double) Radius spacing [km]; (0:).
     */
    public LayerInformationFile(double lowerRadius, double upperRadius, double dRadius) {
        int nRadius = (int) Math.floor((upperRadius - lowerRadius) / dRadius);
        layerThicknesses = new double[nRadius];
        layerRadii = new double[nRadius];
        for (int i = 0; i < nRadius; i++) {
            layerThicknesses[i] = dRadius;
            layerRadii[i] = lowerRadius + (i + 0.5) * dRadius;
        }
    }

    public void write(Path outputPath, OpenOption... options) throws IOException {
        write(layerThicknesses, layerRadii, outputPath, options);
    }

    /**
     * Get information of layer thicknesses.
     * @return (double[])
     */
    public double[] getThicknesses() {
        return layerThicknesses.clone();
    }

    /**
     * Get radii information. The radii should be sorted, and there should be no duplication.
     * @return (double[])
     */
    public double[] getRadii() {
        return layerRadii.clone();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Create layer information file.
     * @param args Options.
     * @throws IOException
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

        // settings
        options.addOption(Option.builder("r").longOpt("radii").hasArg().argName("borderRadii")
                .desc("(double[]) Radii of layer borders, listed using commas [km]; [0:).").build());
        options.addOption(Option.builder("l").longOpt("lower").hasArg().argName("lowerRadius")
                .desc("(double) Lower limit of radius [km]; [0:upperRadius).").build());
        options.addOption(Option.builder("u").longOpt("upper").hasArg().argName("upperRadius")
                .desc("(double) Upper limit of radius [km]; (lowerRadius:).").build());
        options.addOption(Option.builder("d").longOpt("dRadius").hasArg().argName("dRadius")
                .desc("(double) Radius spacing [km]; (0:).").build());

        // output
        options.addOption(Option.builder("t").longOpt("tag").hasArg().argName("tag")
                .desc("A tag to include in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        LayerInformationFile layerFile;

        if (cmdLine.hasOption("r")) {
            double[] borderRadii = Arrays.stream(cmdLine.getOptionValue("r").split(",")).mapToDouble(Double::parseDouble)
                    .sorted().toArray();
            if (borderRadii.length < 2) throw new IllegalArgumentException("There must be at least 2 values for borderRadii");

            layerFile = new LayerInformationFile(borderRadii);

        } else if (cmdLine.hasOption("l") && cmdLine.hasOption("u") && cmdLine.hasOption("d")) {
            double lowerRadius = Double.parseDouble(cmdLine.getOptionValue("l"));
            double upperRadius = Double.parseDouble(cmdLine.getOptionValue("u"));
            double dRadius = Double.parseDouble(cmdLine.getOptionValue("d"));

            layerFile = new LayerInformationFile(lowerRadius, upperRadius, dRadius);

        } else {
            throw new IllegalArgumentException("Either '-r' or '-l & -u & -d' must be set.");
        }

        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;

        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("layer", tag, GadgetAid.getTemporaryString(), ".inf"));
        System.err.println("Outputting in "+ outputPath);
        layerFile.write(outputPath);
    }

}
