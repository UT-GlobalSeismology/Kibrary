package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Class to map voxel positions.
 *
 * @author otsuru
 * @since 2023/3/13
 */
public class VoxelMapper {

    /**
     * Maps pixel points of positions included in a {@link VoxelInformationFile}.
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
        options.addOption(Option.builder("v").longOpt("voxelFile").hasArg().argName("voxelFile").required()
                .desc("Path of voxel information file.").build());
        // settings
        options.addOption(Option.builder("l").longOpt("lambert")
                .desc("Use Lambert azimuthal projection. Otherwise, equidistant cylindrical projection").build());
        options.addOption(Option.builder("r").longOpt("region").hasArg().argName("region")
                .desc("Map region in the form 'lonMin/lonMax/latMin/latMax', for equidistant cylindrical projection.").build());
        options.addOption(Option.builder("c").longOpt("center").hasArg().argName("centerPoint")
                .desc("Center point in the form 'lon/lat', for Lambert azimuthal projection.").build());
        options.addOption(Option.builder("h").longOpt("horizon").hasArg().argName("horizon")
                .desc("(int) Horizon of map, for Lambert azimuthal projection. (90)").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("folderTag")
                .desc("A tag to include in output folder name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output folder name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        // read input voxels
        Path voxelPath = Paths.get(cmdLine.getOptionValue("v"));
        List<HorizontalPosition> voxelPositions = new VoxelInformationFile(voxelPath).getHorizontalPositions();

        // decide map region
        String regionString;
        String projectionString;
        if (cmdLine.hasOption("l")) {
            // Lambert azimuthal projection
            String mapCenter;
            int horizon = cmdLine.hasOption("h") ? Integer.parseInt(cmdLine.getOptionValue("h")) : 90;
            if (cmdLine.hasOption("c")) mapCenter = cmdLine.getOptionValue("c");
            else mapCenter = ScalarMapShellscript.decideMapCenter(voxelPositions.stream().collect(Collectors.toSet()));
            regionString = "-Rg";
            projectionString = "-Ja" + mapCenter + "/" + horizon + "/1:120000000";
        } else {
            // equidistant cylindrical projection
            String mapRegion;
            if (cmdLine.hasOption("r")) mapRegion = cmdLine.getOptionValue("r");
            else mapRegion = ScalarMapShellscript.decideMapRegion(voxelPositions.stream().collect(Collectors.toSet()));
            regionString = "-R" + mapRegion;
            projectionString = "-Jq1:120000000";
        }

        // create output folder
        String folderTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFolderDate = !cmdLine.hasOption("O");
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "voxelMap", folderTag, appendFolderDate, null);

        // output pixels
        List<String> pixelLines = voxelPositions.stream().map(HorizontalPosition::toString).collect(Collectors.toList());
        Files.write(outPath.resolve("pixel.lst"), pixelLines);
        // NOTE: HorizontalPosition.crossesDateLine() is not needed here, as psxy can plot points on longitude+360

        // output GMT script
        String gmtFileName = "voxelMap.sh";
        Path gmtPath = outPath.resolve(gmtFileName);
        outputGMT(gmtPath, regionString, projectionString) ;

        System.err.println("After this finishes, please run " + gmtPath);
    }

    private static void outputGMT(Path gmtPath, String regionString, String projectionString) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(gmtPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("outputps=\"voxelMap.eps\"");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 1500x1500");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 25");
            pw.println("");
            pw.println("# map parameters");
            pw.println("R='" + regionString + "'");
            pw.println("J='" + projectionString + "'");
            pw.println("B='-Ba30 -BWeSn'");
            pw.println("");
            pw.println("gmt pscoast -Ggray -Wthinnest,gray20 $B $J $R -P -K > $outputps");
            pw.println("");
            pw.println("#------- Pixels");
            pw.println("gmt psxy pixel.lst -: -Sc0.3 -G0/255/0 -Wthinnest -J -R -P -O -K >> $outputps");
            pw.println("");
            pw.println("#------- Finalize");
            pw.println("gmt pstext -N -F+jLM+f30p,Helvetica,black -J -R -O << END >> $outputps");
            pw.println("END");
            pw.println("");
            pw.println("gmt psconvert $outputps -A -Tf -Qg4 -E100");
            pw.println("gmt psconvert $outputps -A -Tg -Qg4 -E500");
            pw.println("");
            pw.println("#-------- Clear");
            pw.println("rm -rf cp.cpt gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
    }
}
