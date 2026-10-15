package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.math.geometry.CoordinateConverter;
import io.github.kensuke1984.kibrary.math.geometry.IntegerXY;
import io.github.kensuke1984.kibrary.util.DatasetAid;

/**
 * Create new {@link VoxelInformationFile} that has twice a fine mesh in both horizontal directions
 * as the input {@link VoxelInformationFile}.
 *
 * @since 2026/8/2
 * @author otsuru
 */
public class VoxelOctopus {

    /**
     * Create new {@link VoxelInformationFile} that has twice a fine mesh in both horizontal directions
     * as the input {@link VoxelInformationFile}.
     * @param args (String[]) Options.
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
     * @return (Options) Options that can be specified by user.
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder("v").longOpt("voxel").hasArg().argName("voxelFile").required()
                .desc("Path of input voxel information file.").build());
        options.addOption(Option.builder("c").longOpt("converter").hasArg().argName("converterFile").required()
                .desc("Path of coordinate converter file.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine (CommandLine) Options specified by user.
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "voxel", fileTag, appendFileDate, null, ".inf");

        // read input voxel information file
        VoxelInformationFile vif = new VoxelInformationFile(Paths.get(cmdLine.getOptionValue("v")));
        double[] layerRadii = vif.getRadii();
        double[] layerThicknesses = vif.getThicknesses();
        List<IntegerXY> voxelIntegerXYs = vif.getIntegerXYs();

        // read coordinate converver file
        CoordinateConverter converter = new CoordinateConverter(Paths.get(cmdLine.getOptionValue("c")));
        CoordinateConverter octopusConverter = converter.withDeltas(converter.getDLatitude() / 2.0, converter.getDLongitudeVal() / 2.0);

        // set coordinates of new grid
        Set<IntegerXY> octopusIntegerXYs = new HashSet<>();
        for (IntegerXY integerXY : voxelIntegerXYs) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    IntegerXY octopusIntegerXY = new IntegerXY(integerXY.x() * 2 + i, integerXY.y() * 2 + j);
                    octopusIntegerXYs.add(octopusIntegerXY);
                }
            }
        }

        // convert coordinates to positions
        List<HorizontalPixel> horizontalPixels = octopusIntegerXYs.stream()
                .sorted(Comparator.comparing(IntegerXY::y).thenComparing(IntegerXY::x))
                .map(xy -> octopusConverter.createHorizontalPixel(xy)).collect(Collectors.toList());

        // write
        VoxelInformationFile.write(layerThicknesses, layerRadii, horizontalPixels, outputPath);
    }

}
