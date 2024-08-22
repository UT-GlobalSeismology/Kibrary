package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Operation that plots a great arc on a map.
 *
 * @author otsuru
 * @since 2023/3/31
 */
public class GreatArcMapper extends Operation {

    /**
     * The interval of deciding map size.
     */
    private static final int INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map.
     */
    private static final int MAP_RIM = 5;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    private double pos0Latitude;
    private double pos0Longitude;
    private double pos1Latitude;
    private double pos1Longitude;
    /**
     * Distance of the starting point along arc before position 0.
     */
    private double beforePos0Deg;
    /**
     * Distance of the ending point along arc after either position 0 or position 1.
     */
    private double afterPosDeg;
    /**
     * Whether the ending point should be decided with respect to position 0 or position 1.
     */
    private boolean useAfterPos1;

    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##########Settings of great circle arc to display in the cross section.");
            pw.println("##(double) Latitude of position 0, must be set.");
            pw.println("#pos0Latitude ");
            pw.println("##(double) Longitude of position 0, must be set.");
            pw.println("#pos0Longitude ");
            pw.println("##(double) Latitude of position 1, must be set.");
            pw.println("#pos1Latitude ");
            pw.println("##(double) Longitude of position 1, must be set.");
            pw.println("#pos1Longitude ");
            pw.println("##(double) Distance along arc before position 0. (0)");
            pw.println("#beforePos0Deg ");
            pw.println("##(double) Distance along arc after position 0. If not set, the following afterPos1Deg will be used.");
            pw.println("#afterPos0Deg ");
            pw.println("##(double) Distance along arc after position 1. (0)");
            pw.println("#afterPos1Deg ");
            pw.println("##########Settings for mapping");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax.");
            pw.println("#mapRegion -180/180/-90/90");
        }
        System.err.println(outPath + " is created.");
    }

    public GreatArcMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        pos0Latitude = property.parseDouble("pos0Latitude", null);
        pos0Longitude = property.parseDouble("pos0Longitude", null);
        pos1Latitude = property.parseDouble("pos1Latitude", null);
        pos1Longitude = property.parseDouble("pos1Longitude", null);
        beforePos0Deg = property.parseDouble("beforePos0Deg", "0");
        if (property.containsKey("afterPos0Deg")) {
            afterPosDeg = property.parseDouble("afterPos0Deg", null);
            useAfterPos1 = false;
        } else {
            afterPosDeg = property.parseDouble("afterPos1Deg", "0");
            useAfterPos1 = true;
        }

        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
    }

    @Override
    public void run() throws IOException {
        //~decide start and end positions of cross section
        HorizontalPosition pos0 = new HorizontalPosition(pos0Latitude, pos0Longitude);
        HorizontalPosition pos1 = new HorizontalPosition(pos1Latitude, pos1Longitude);
        HorizontalPosition startPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), -beforePos0Deg);
        HorizontalPosition endPosition;
        if (useAfterPos1) {
            endPosition = pos1.pointAlongAzimuth(pos1.computeAzimuthDeg(pos0), -afterPosDeg);
        } else {
            endPosition = pos0.pointAlongAzimuth(pos0.computeAzimuthDeg(pos1), afterPosDeg);
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "greatArc", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        outputGMT(startPosition, endPosition, outPath.resolve("arcMap.sh"));

        System.err.println("After this finishes, please enter " + outPath + "/ and run arcMap.sh");
    }

    private void outputGMT(HorizontalPosition startPosition, HorizontalPosition endPosition, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("outputps=\"arcMap.eps\"");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 1500x1500");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 10p");
            pw.println("");
            pw.println("# map parameters");
            pw.println("R='-R" + decideMapRegion(startPosition, endPosition) + "'");
            pw.println("J='-Jq1:120000000'");
            pw.println("B='-Ba30 -BWeSn'");
            pw.println("");
            pw.println("gmt pscoast -Ggray -Wthinnest,gray20 $B $J $R -P -K > $outputps");
            pw.println("");
            pw.println("#------- Great arc");
            pw.println("gmt psxy -: $J $R -O -K -Wfat,magenta << END >> $outputps");
            pw.println(startPosition.toString());
            pw.println(endPosition.toString());
            pw.println("END");
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

    private String decideMapRegion(HorizontalPosition startPosition, HorizontalPosition endPosition) throws IOException {
        if (mapRegion != null) {
            return mapRegion;
        } else {
            double distance = startPosition.computeEpicentralDistanceDeg(endPosition);
            double azimuth = startPosition.computeAzimuthDeg(endPosition);
            // create set of points at 10 deg intervals
            Set<HorizontalPosition> positions = new HashSet<>();
            positions.add(startPosition);
            positions.add(endPosition);
            for (int i = 10; i < distance; i += 10) {
                positions.add(startPosition.pointAlongAzimuth(azimuth, i));
            }
            return ScalarMapShellscript.decideMapRegion(positions);
        }
    }
}
