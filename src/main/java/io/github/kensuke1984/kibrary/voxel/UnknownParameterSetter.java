package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Create unknown parameter file.
 * @author ???
 * @since a long time ago
 * @version 2022/2/12 moved & renamed from inversion.addons.MakeUnknownParameterFile
 */
public class UnknownParameterSetter {

    /**
     * Create unknown parameter file.
     * @param args [option]
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

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("l").longOpt("layer").hasArg().argName("layerFile")
                .desc("Path of input layer file.").build());
        inputOption.addOption(Option.builder("v").longOpt("voxel").hasArg().argName("voxelFile")
                .desc("Path of input voxel file.").build());
        options.addOptionGroup(inputOption);

        // settings
        options.addOption(Option.builder("p").longOpt("partials").hasArg().argName("partialTypes").required()
                .desc("Partial types to make unknown parameters for, listed using commas.").build());

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

        // partial types
        System.err.println("Working for:");
        PartialType[] types = Stream.of(cmdLine.getOptionValue("p").split(",")).map(PartialType::valueOf).toArray(PartialType[]::new);
        for (int i = 0; i < types.length; i++) {
            System.err.println(" " + types[i]);
        }

        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;

        if (cmdLine.hasOption("l")) {
            // work for layer file
            Path layerPath = Paths.get(cmdLine.getOptionValue("l"));
            outputFor1D(layerPath, types, tag);

        } else if (cmdLine.hasOption("v")) {
            // work for voxel file
            Path voxelPath = Paths.get(cmdLine.getOptionValue("v"));
            outputFor3D(voxelPath, types, tag);

        } else {
            throw new IllegalArgumentException("Either a voxel information file or a list of radii must be specified.");
        }

    }

    private static void outputFor1D(Path layerPath, PartialType[] types, String tag) throws IOException {
        // read voxel information
        LayerInformationFile file = new LayerInformationFile(layerPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();

        // create unknown parameters
        List<UnknownParameter> parameterList = new ArrayList<>();
        int numLayer = radii.length;

        // loop for each layer
        for (int i = 0; i < radii.length; i++) {
            for (PartialType type : types) {
                Physical1DParameter parameter = new Physical1DParameter(type, radii[i], layerThicknesses[i]);
                parameterList.add(parameter);
            }
        }
        System.err.println("Finished working for all " + numLayer + " layers.");

        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("unknowns", tag, GadgetAid.getTemporaryString(), ".lst"));
        System.err.println("Outputting in "+ outputPath);
        UnknownParameterFile.write(parameterList, outputPath);
    }

    private static void outputFor3D(Path voxelPath, PartialType[] types, String tag) throws IOException {
        // read voxel information
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        List<HorizontalPixel> horizontalPixels = file.getHorizontalPixels();

        // create unknown parameters
        List<UnknownParameter> parameterList = new ArrayList<>();
        int numFinished = 0;
        int numVoxel = horizontalPixels.size() * radii.length;
        for (HorizontalPixel pixel : horizontalPixels) {
            // extract information of horizontal pixel
            HorizontalPosition horizontalPosition = pixel.getPosition();
            double dLatitude = pixel.getDLatitude();
            double dLongitude = pixel.getDLongitude();
            // loop for each layer
            for (int i = 0; i < radii.length; i++) {
                FullPosition voxelPosition = horizontalPosition.toFullPosition(radii[i]);
                double volume = Earth.computeVolume(voxelPosition, layerThicknesses[i], dLatitude, dLongitude);
                for (PartialType type : types) {
                    Physical3DParameter parameter = new Physical3DParameter(type, voxelPosition, volume);
                    parameterList.add(parameter);
                }
                numFinished++;
            }
            System.err.print("\rFinished " + numFinished + " of " + numVoxel + " voxels");
        }
        System.err.println("\rFinished working for all " + numVoxel + " voxels.");

        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("unknowns", tag, GadgetAid.getTemporaryString(), ".lst"));
        System.err.println("Outputting in "+ outputPath);
        UnknownParameterFile.write(parameterList, outputPath);
    }

}
