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
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
     * Maps pixel points of positions included in voxel information file.
     *
     * @param args
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
                .desc("Path of voxel information file").build());
        // settings
        options.addOption(Option.builder("j").longOpt("mapProjection").hasArg().argName("mapProjection")
                .desc("Mode of map projection {Q: Cylindrical Equidistant, Elon0/lat0/: Azimuthal Equidistant with map center (lon0, lat0)} (Q)").build());
        options.addOption(Option.builder("r").longOpt("region").hasArg().argName("region")
                .desc("Map region in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]").build());

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

        // decide map projection
        String mapProjection;
        if (cmdLine.hasOption("j")) mapProjection = cmdLine.getOptionValue("j");
        else mapProjection = "Q";

        // decide map region
        String mapRegion;
        if (cmdLine.hasOption("r")) mapRegion = cmdLine.getOptionValue("r");
        else mapRegion = PerturbationMapShellscript.decideMapRegion(voxelPositions.stream().collect(Collectors.toSet()));

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "voxelMap", null, GadgetAid.getTemporaryString());

        // output pixels
        List<String> pixelLines = voxelPositions.stream().map(HorizontalPosition::toString).collect(Collectors.toList());
        Files.write(outPath.resolve("pixel.lst"), pixelLines);
        // NOTE: HorizontalPosition.crossesDateLine() is not needed here, as psxy can plot points on longitude+360

        // output GMT script
        String gmtFileName = "voxelMap.sh";
        Path gmtPath = outPath.resolve(gmtFileName);
        outputGMT(gmtPath, mapProjection, mapRegion) ;

        System.err.println("After this finishes, please run " + gmtPath);
    }

    private static void outputGMT(Path gmtPath, String mapProjection, String mapRegion) throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(gmtPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("outputps=\"voxelMap.eps\"");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 1100x1100");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 25");
            pw.println("");
            pw.println("# map parameters");
            pw.println("R='-R" + mapRegion + "'");
            pw.println("J='-J" + mapProjection + "20'");
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
