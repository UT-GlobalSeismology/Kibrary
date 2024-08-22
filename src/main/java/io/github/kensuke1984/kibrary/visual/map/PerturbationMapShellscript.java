package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to generate shellscript files that are to be used to map horizontal slices of perturbations using GMT.
 *
 * @author otsuru
 * @since 2022/4/12
 * @version 2022/7/17 renamed from MapperShellscript to PerturbationMapShellscript
 */
public class PerturbationMapShellscript {

    /**
     * Width of each panel
     */
    private static final int PANEL_WIDTH = 21;
    /**
     * Height of each panel
     */
    private static final int PANEL_HEIGHT = 20;
    /**
     * The interval of deciding map size
     */
    private static final int MAP_SIZE_INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map
     */
    private static final int MAP_RIM = 5;
    /**
     * Number of nodes to divide an original node when smoothing
     */
    public static final int SMOOTHING_FACTOR = 10;

    private final VariableType variable;
    private final double[] radii;
    /**
     * The displayed value of each layer boundary. This may be radius, depth, or height from a certain discontinuity.
     */
    private final double[] boundaries;
    private final String mapRegion;
    /**
     * Interval of
     */
    private final double positionInterval;
    /**
     * Maximum of color scale
     */
    private final double scale;
    private final String modelFileNameRoot;
    /**
     * Number of panels to map in each row
     */
    private final int nPanelsPerRow;

    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;

    private String maskFileNameRoot;
    private double maskThreshold;

    public PerturbationMapShellscript(VariableType variable, double[] radii, double[] boundaries, String mapRegion, double positionInterval, double scale,
            String modelFileNameRoot, int nPanelsPerRow) {
        this.variable = variable;
        this.radii = radii;
        this.boundaries = boundaries;
        this.mapRegion = mapRegion;
        this.positionInterval = positionInterval;
        this.scale = scale;
        this.modelFileNameRoot = modelFileNameRoot;
        this.nPanelsPerRow = nPanelsPerRow;
        if (boundaries.length <= radii.length) {
            throw new IllegalArgumentException(boundaries.length + " boundaries is not enough for " + radii.length + " layers.");
        }

        // set this temporarily to display all layers (may be oveerwritten later)
        this.displayLayers = IntStream.range(0, radii.length).toArray();
    }

    public void setMask(String maskFileNameRoot, double maskThreshold) {
        this.maskFileNameRoot = maskFileNameRoot;
        this.maskThreshold = maskThreshold;
    }

    /**
     * Specify which of the layers to display.
     * @param displayLayers (int[]) Indices of layers to plot, listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    public void setDisplayLayers(int[] displayLayers) {
        this.displayLayers = displayLayers;
        for (int layerIndex : displayLayers) {
            if (layerIndex >= radii.length) {
                throw new IllegalArgumentException("Layer index " + layerIndex + " too large.");
            }
        }
    }

    /**
     * Write cp_master file, grid shellscript, and map shellscript.
     * @param outPath (Path) Directory where output files should be written.
     * @throws IOException
     */
    public void write(Path outPath) throws IOException {
        writeCpMaster(outPath.resolve("cp_master.cpt"));
        if (maskFileNameRoot != null) writeCpMask(outPath.resolve("cp_mask.cpt"), maskThreshold);
        writeGridMaker(outPath.resolve(modelFileNameRoot + "Grid.sh"));
        writeMakeMap(outPath.resolve(modelFileNameRoot + "Map.sh"));
    }

    static void writeCpMaster(Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("-3.5 129 14  30 -3.088235294117647 129 14  30");
            pw.println("-3.088235294117647 158 15  9 -2.6764705882352944 158 15  9");
            pw.println("-2.6764705882352944 218 20  7 -2.264705882352941 218 20  7");
            pw.println("-2.264705882352941 241 80  29 -1.8529411764705883 241 80  29");
            pw.println("-1.8529411764705883 244 129  17 -1.441176470588235 244 129  17");
            pw.println("-1.441176470588235 247 220  100 -1.0294117647058822 247 220  100");
            pw.println("-1.0294117647058822 247 238  159 -0.6176470588235294 247 238  159");
            pw.println("-0.6176470588235294 245 247  198 -0.20588235294117663 245 247  198");
            pw.println("-0.20588235294117663 253 253  253 0.20588235294117663 253 253  253");
            pw.println("0.20588235294117663 236 246  247 0.6176470588235299 236 246  247");
            pw.println("0.6176470588235299 212 237  239 1.0294117647058822 212 237  239");
            pw.println("1.0294117647058822 117 201  222 1.4411764705882355 117 201  222");
            pw.println("1.4411764705882355 64 172  197 1.8529411764705879 64 172  197");
            pw.println("1.8529411764705879 44 144  169 2.264705882352941 44 144  169");
            pw.println("2.264705882352941 59 80  129 2.6764705882352935 59 80  129");
            pw.println("2.6764705882352935 30 46  110 3.0882352941176467 30 46  110");
            pw.println("3.0882352941176467 17 46  85 3.5 17 46  85");
            pw.println("B       129 14  30");
            pw.println("F       17 46  85");
            pw.println("N       255 255 255");
        }
    }

    static void writeCpMask(Path outputPath, double maskThreshold) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("0 black " + maskThreshold + " black");
            pw.println("B black");
            pw.println("F white");
            pw.println("N 127.5");
        }
    }

    private void writeGridMaker(Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");

            // This will be done for all radii, even when the layers to display are specified.
            pw.print("for depth in");
            for (double radius : radii) {
                pw.print(" " + (int) radius + ".0");
            }
            pw.println("");

            pw.println("do");
            pw.println("    dep=${depth%.0}");

            // grid model
            pw.println("    grep \"$depth\" " + modelFileNameRoot + "XY.lst | \\");
            pw.println("    awk '{print $2,$1,$4}' | \\");
            pw.println("    gmt xyz2grd -G$dep\\model.grd -R" + mapRegion + " -I" + positionInterval + " -di0");

            // grid mask
            if (maskFileNameRoot != null) {
                pw.println("    grep \"$depth\" " + maskFileNameRoot + "XY.lst | \\");
                pw.println("    awk '{print $2,$1,$4}' | \\");
                pw.println("    gmt xyz2grd -G$dep\\mask.grd -R" + mapRegion + " -I" + positionInterval + " -di0");
            }

            pw.println("done");
        }
    }

    private void writeMakeMap(Path outputPath) throws IOException {
        String paramName = variable.toString();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 6000x6000");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 50");
            pw.println("gmt set FONT_LABEL 50p,Helvetica,black");
            pw.println("");
            pw.println("# map parameters");
            pw.println("R='-R" + mapRegion + "'");
            pw.println("J='-JQ15'");
            pw.println("B='-B30f10';");
            pw.println("");
            pw.println("outputps=" + modelFileNameRoot + "Map.eps");
            pw.println("MP=" + scale);
            pw.println("gmt makecpt -Ccp_master.cpt -T-$MP/$MP > cp.cpt");
            pw.println("");

            pw.println("#------- Panels");
            for (int iPanel = 0; iPanel < displayLayers.length; iPanel++) {
                // CAUTION: layers are referenced in reverse order because we will draw from the top
                int iPanelRev = displayLayers.length - 1 - iPanel;
                int layerIndex = displayLayers[iPanelRev];
                int radius = (int) radii[layerIndex];
                String upperBound = MathAid.simplestString(boundaries[layerIndex + 1]);
                String lowerBound = MathAid.simplestString(boundaries[layerIndex]);

                if (iPanel == 0) {
                    pw.println("gmt grdimage " + radius + "\\model.grd -BwESn+t\"" + upperBound + "-" + lowerBound
                            + " km\" $B -Ccp.cpt $J $R -K -Y180 > $outputps");
                } else if (iPanel % nPanelsPerRow == 0) {
                    pw.println("gmt grdimage " + radius + "\\model.grd -BwESn+t\"" + upperBound + "-" + lowerBound
                            + " km\" $B -Ccp.cpt $J $R -K -O -X-" + (PANEL_WIDTH * (nPanelsPerRow - 1)) + " -Y-" + PANEL_HEIGHT + " >> $outputps");
                } else {
                    pw.println("gmt grdimage " + radius + "\\model.grd -BwESn+t\"" + upperBound + "-" + lowerBound
                            + " km\" $B -Ccp.cpt $J $R -K -O -X" + PANEL_WIDTH + " >> $outputps");
                }

                if (maskFileNameRoot != null) {
                    pw.println("gmt grdimage " + radius + "\\mask.grd -Ccp_mask.cpt -G0/0/0 -t80 $J $R -K -O >> $outputps");
                }

                pw.println("gmt pscoast -Wthinner,black -A500 -J -R -K -O >> $outputps");
                pw.println("");
            }

            pw.println("#------- Scale");
            // compute the column number of the last panel (counting as 0, 1, 2, 3, ...)
            int nLastColumn = (displayLayers.length - 1) % nPanelsPerRow;
            pw.println("gmt psscale -Ccp.cpt -Dx2/-4+w12/0.8+h -B1.0+l\"@~d@~" + paramName + "/" + paramName + " (%)\" -K -O -X-"
                    + (PANEL_WIDTH * nLastColumn / 2) + " >> $outputps");
            pw.println("");

            pw.println("#------- Finalize");
            pw.println("gmt pstext -N -F+jLM+f30p,Helvetica,black $J $R -O << END >> $outputps");
            pw.println("END");
            pw.println("");
            pw.println("gmt psconvert $outputps -E100 -Tf -A -Qg4");
            pw.println("gmt psconvert $outputps -E100 -Tg -A -Qg4");
            pw.println("");
            pw.println("#-------- Clear");
            pw.println("rm -rf cp.cpt gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
    }

    /**
     * Decides the interval in which to sample the grid in a map.
     * This method sets the interval at roughly a tenth of the input position spacing.
     * @param positions (Set of {@link HorizontalPosition}) Input position set.
     * @return (double) Suggested value of grid spacing.
     */
    static double decideGridSampling(Set<? extends HorizontalPosition> positions) {
        double positionInterval = HorizontalPosition.findLatitudeInterval(positions);
        int power = (int) MathAid.floor(Math.log10(positionInterval));
        double coef = positionInterval / FastMath.pow(10, power);
        if (coef < 1) throw new IllegalStateException("Grid interval decision went wrong");
        else if (coef < 2) return 1.0 * FastMath.pow(10, power) / SMOOTHING_FACTOR;
        else if (coef < 5) return 2.0 * FastMath.pow(10, power) / SMOOTHING_FACTOR;
        else if (coef < 10) return 5.0 * FastMath.pow(10, power) / SMOOTHING_FACTOR;
        else throw new IllegalStateException("Grid interval decision went wrong");
    }

    /**
     * Decides a rectangular region of a map that is sufficient to map all given positions.
     * @param positions (Set of {@link HorizontalPosition}) Positions that need to be included in map region.
     * @return (String) Rectangular region in form "lonMin/lonMax/latMin/latMax".
     */
    static String decideMapRegion(Set<? extends HorizontalPosition> positions) {
        if (positions.size() == 0) throw new IllegalArgumentException("No positions are given");
        // whether to use [0:360) instead of [-180:180)
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        // map to latitude and longitude values
        double[] latitudes = positions.stream().mapToDouble(HorizontalPosition::getLatitude).toArray();
        double[] longitudes = positions.stream().mapToDouble(pos -> pos.getLongitude(crossDateLine)).toArray();
        // find min and max latitude and longitude
        double latMin = Arrays.stream(latitudes).min().getAsDouble();
        double latMax = Arrays.stream(latitudes).max().getAsDouble();
        double lonMin = Arrays.stream(longitudes).min().getAsDouble();
        double lonMax = Arrays.stream(longitudes).max().getAsDouble();
        // expand the region a bit more
        latMin = MathAid.floor(latMin / MAP_SIZE_INTERVAL) * MAP_SIZE_INTERVAL - MAP_RIM;
        latMax = MathAid.ceil(latMax / MAP_SIZE_INTERVAL) * MAP_SIZE_INTERVAL + MAP_RIM;
        lonMin = MathAid.floor(lonMin / MAP_SIZE_INTERVAL) * MAP_SIZE_INTERVAL - MAP_RIM;
        lonMax = MathAid.ceil(lonMax / MAP_SIZE_INTERVAL) * MAP_SIZE_INTERVAL + MAP_RIM;
        if (latMin < -90) latMin = -90;
        if (latMax > 90) latMax = 90;
        // return as String
        return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
    }

    /**
     * Decides the center point of the region when mapping all given positions.
     * @param positions (Set of {@link HorizontalPosition}) Positions that need to be included in map region.
     * @return (String) Center point in form "lon/lat".
     */
    static String decideMapCenter(Set<? extends HorizontalPosition> positions) {
        if (positions.size() == 0) throw new IllegalArgumentException("No positions are given");
        // whether to use [0:360) instead of [-180:180)
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        // map to latitude and longitude values
        double[] latitudes = positions.stream().mapToDouble(HorizontalPosition::getLatitude).toArray();
        double[] longitudes = positions.stream().mapToDouble(pos -> pos.getLongitude(crossDateLine)).toArray();
        // find min and max latitude and longitude
        double latMin = Arrays.stream(latitudes).min().getAsDouble();
        double latMax = Arrays.stream(latitudes).max().getAsDouble();
        double lonMin = Arrays.stream(longitudes).min().getAsDouble();
        double lonMax = Arrays.stream(longitudes).max().getAsDouble();
        // decide center point
        double latCenter = (latMin + latMax) / 2;
        double lonCenter = (lonMin + lonMax) / 2;
        // return as String
        return (int) lonCenter + "/" + (int) latCenter;
    }

}
