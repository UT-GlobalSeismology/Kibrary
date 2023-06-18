package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to create an {@link UnknownParameterFile}.
 * @author ???
 * @since a long time ago
 * @version 2022/2/12 moved & renamed from inversion.addons.MakeUnknownParameterFile to voxel.UnknownParameterSetter
 */
public class UnknownParameterSetter {

    /**
     * Create an {@link UnknownParameterFile}.
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
        options.addOption(Option.builder("p").longOpt("params").hasArg().argName("variableTypes").required()
                .desc("Variable types to make unknown parameters for, listed using commas.").build());

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
        VariableType[] types = Stream.of(cmdLine.getOptionValue("p").split(",")).map(VariableType::valueOf).toArray(VariableType[]::new);
        for (int i = 0; i < types.length; i++) {
            System.err.println(" " + types[i]);
        }

        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;

        List<UnknownParameter> parameterList;
        if (cmdLine.hasOption("l")) {
            // work for layer file
            Path layerPath = Paths.get(cmdLine.getOptionValue("l"));
            parameterList = createParametersFor1D(layerPath, types);

        } else if (cmdLine.hasOption("v")) {
            // work for voxel file
            Path voxelPath = Paths.get(cmdLine.getOptionValue("v"));
            parameterList = createParametersFor3D(voxelPath, types);

        } else {
            throw new IllegalArgumentException("Either a layer information file or a voxel information file must be specified.");
        }

        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("unknowns", tag, GadgetAid.getTemporaryString(), ".lst"));
        System.err.println("Outputting in "+ outputPath);
        UnknownParameterFile.write(parameterList, outputPath);
    }

    private static List<UnknownParameter> createParametersFor1D(Path layerPath, VariableType[] types) throws IOException {
        // read voxel information
        LayerInformationFile file = new LayerInformationFile(layerPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();

        //~create unknown parameters
        List<UnknownParameter> parameterList = new ArrayList<>();
        // loop for each layer
        for (int i = 0; i < radii.length; i++) {
            for (VariableType type : types) {
                Physical1DParameter parameter = new Physical1DParameter(type, radii[i], layerThicknesses[i]);
                parameterList.add(parameter);
            }
        }
        System.err.println("Finished working for all " + radii.length + " layers.");

        return parameterList;
    }

    private static List<UnknownParameter> createParametersFor3D(Path voxelPath, VariableType[] types) throws IOException {
        // read voxel information
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        List<HorizontalPixel> horizontalPixels = file.getHorizontalPixels();

        //~create unknown parameters
        Set<UnknownParameter> parameterSet = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger numFinished = new AtomicInteger();
        int numVoxel = horizontalPixels.size() * radii.length;
        horizontalPixels.parallelStream().forEach(pixel -> {
            // extract information of horizontal pixel
            HorizontalPosition horizontalPosition = pixel.getPosition();
            double dLatitude = pixel.getDLatitude();
            double dLongitude = pixel.getDLongitude();
            // loop for each layer
            for (int i = 0; i < radii.length; i++) {
                FullPosition voxelPosition = horizontalPosition.toFullPosition(radii[i]);
                double volume = Earth.computeVolume(voxelPosition, layerThicknesses[i], dLatitude, dLongitude);
                for (VariableType type : types) {
                    Physical3DParameter parameter = new Physical3DParameter(type, voxelPosition, volume);
                    parameterSet.add(parameter);
                }
                if (numFinished.incrementAndGet() % 100 == 0)
                    System.err.print("\rFinished " + numFinished + " of " + numVoxel + " voxels");
            }
        });
        System.err.println("\rFinished working for all " + numVoxel + " voxels.");

        return parameterSet.stream().sorted(Comparator.comparing(UnknownParameter::getPosition)).collect(Collectors.toList());
    }

}
