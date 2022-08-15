package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
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
     *      [voxelPath  partialTypes...]
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

        options.addOption(Option.builder("v").longOpt("voxel").hasArg().argName("voxelFile").required()
                .desc("Path of input voxel file").build());
        options.addOption(Option.builder("p").longOpt("partials").hasArg().argName("partialTypes").required()
                .desc("Partial types to make unknown parameters for, listed using commas").build());
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

        Path voxelPath = Paths.get(cmdLine.getOptionValue("v"));

        // partial types
        System.err.println("Working for:");
        PartialType[] types = Stream.of(cmdLine.getOptionValue("p").split(",")).map(PartialType::valueOf).toArray(PartialType[]::new);
        for (int i = 0; i < types.length; i++) {
            System.err.println(" " + types[i]);
        }

        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;

        output(voxelPath, types, tag);

    }

    private static void output(Path voxelPath, PartialType[] types, String tag) throws IOException {
        // read voxel information
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        double dLatitude = file.getSpacingLatitude();
        double dLongitude = file.getSpacingLongitude();
        HorizontalPosition[] positions = file.getHorizontalPositions();

        List<UnknownParameter> parameterList = new ArrayList<>();
        int numFinished = 0;
        int numTotal = positions.length * radii.length;
        for (HorizontalPosition position : positions) {
            for (int i = 0; i < radii.length; i++) {
                FullPosition pointPosition = position.toFullPosition(radii[i]);
                double volume = Earth.computeVolume(pointPosition, layerThicknesses[i], dLatitude, dLongitude);
                for (PartialType type : types) {
                    Physical3DParameter parameter = new Physical3DParameter(type, pointPosition, volume);
                    parameterList.add(parameter);
                }
                numFinished++;
            }
            System.err.print("\rFinished " + numFinished + " of " + numTotal + " voxels");
        }
        System.err.println("\rFinished working for all " + numTotal + " voxels.");

        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("unknowns", tag, GadgetAid.getTemporaryString(), ".lst"));
        System.err.println("Outputting in "+ outputPath);
        UnknownParameterFile.write(parameterList, outputPath);
    }

}
