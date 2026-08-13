package io.github.kensuke1984.kibrary.archive;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

public class UpdateVoxel {

    /**
     * Update voxel file to new style.
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

        // input
        options.addOption(Option.builder("v").longOpt("voxel").hasArg().argName("voxelFile").required()
                .desc("Path of input voxel file.").build());
        options.addOption(Option.builder("l").longOpt("longitude").hasArg().argName("baseLongitude").required()
                .desc("Value of base longitude [deg].").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("folderTag")
                .desc("A tag to include in output folder name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output folder name.").build());

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

        Path inputPath = Paths.get(cmdLine.getOptionValue("v"));
        InformationFileReader reader = new InformationFileReader(inputPath, true);

        double baseLongitude = Double.parseDouble(cmdLine.getOptionValue("l"));

        List<HorizontalPosition> horizontalPositions = new ArrayList<>();
        String line;
        reader.next();  // radii line
        while ((line = reader.next()) != null) {
            String[] parts = line.split("\\s+");
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            horizontalPositions.add(position);
        }

        double[] latitudes = horizontalPositions.stream().mapToDouble(HorizontalPosition::getLatitude).distinct().sorted().toArray();

        List<HorizontalPixel> horizontalPixels = new ArrayList<>();
        int m = latitudes.length;
        for (int i = 0; i < m; i++) {
            double latitude = latitudes[i];
            List<HorizontalPosition> positionsForLat = horizontalPositions.stream()
                    .filter(pos -> Precision.equals(pos.getLatitude(), latitude)).collect(Collectors.toList());

            double[] longitudes = positionsForLat.stream()
                    .mapToDouble(HorizontalPosition::getLongitude).distinct().sorted().toArray();
            int n = longitudes.length;
            double dLongitude = (longitudes[n - 1] - longitudes[0]) / (n - 1);

            for (HorizontalPosition position : positionsForLat) {
                double longitude = position.getLongitude();
                int j = (int) Math.round((longitude - baseLongitude) / dLongitude);

                HorizontalPosition shiftedPosition = new HorizontalPosition(latitude, longitude + 5.0);
                HorizontalPixel pixel = new HorizontalPixel(shiftedPosition, 3.0, dLongitude, m - i, j);
                horizontalPixels.add(pixel);
            }

        }

        // output
        double[] layerThicknesses = {100, 100, 100, 100};
        double[] layerRadii = {3530, 3630, 3730, 3830};
        VoxelInformationFile.write(layerThicknesses, layerRadii, horizontalPixels, outputPath);

        // write coordinate converter file
//        CoordinateConverter converter = new CoordinateConverter(dLatitudeKm, dLatitudeDeg, setLatitudeByKm, 90.0 + latitudeOffset,
//                dLongitudeKm, dLongitudeDeg, setLongitudeByKm, baseLongitude, centerRadius, crossDateLine);
//        Path converterPath = DatasetAid.generateOutputFilePath(workPath, "converter", fileTag, appendFileDate, null, ".inf");
//        converter.writeToFile(converterPath);
    }

}
