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
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to generate shellscript files that are to be used to map horizontal slices of perturbations using GMT.
 *
 * @author otsuru
 * @since 2022/4/12
 * @version 2022/7/17 renamed from MapperShellscript to PerturbationMapShellscript
 */
public class ScalarMapShellscript {

    /**
     * The interval of deciding map size.
     */
    private static final int MAP_SIZE_INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map.
     */
    private static final int MAP_RIM = 5;
    /**
     * Number of nodes to divide an original node when smoothing.
     */
    public static final int SMOOTHING_FACTOR = 5;

    private final VariableType variable;
    private final ScalarType scalarType;
    private final String tag;

    private final double[] radii;
    /**
     * The displayed value of each layer boundary. This may be radius, depth, or height from a certain discontinuity.
     */
    private final double[] boundaries;
    private final String mapRegion;
    /**
     * Interval of interpolation when mapping [deg].
     */
    private final double positionInterval;
    /**
     * Maximum of color scale.
     */
    private final double scale;
    /**
     * Number of panels to map in each row.
     */
    private final int nPanelsPerRow;
    /**
     * File name root of output files.
     */
    private final String plotFileNameRoot;

    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private final String scalarFileName;

    private boolean maskExists = false;
    private double maskThreshold;
    private String maskFileName;

    /**
     * Color palette.
     * 0: red-yellow-white-skyblue-turquoise
     * 1: orange-yellow-white-cyan-skyblue
     * 2: red-orange-white-skyblue-purple
     */
    private int cpStyle = 1;
    private boolean forSlides = false;

    ScalarMapShellscript(VariableType variable, ScalarType scalarType, double[] radii, double[] boundaries,
            String mapRegion, double positionInterval, double scale, int nPanelsPerRow) {
        this(variable, scalarType, null, radii, boundaries, mapRegion, positionInterval, scale, nPanelsPerRow);
    }

    ScalarMapShellscript(VariableType variable, ScalarType scalarType, String tag, double[] radii, double[] boundaries,
            String mapRegion, double positionInterval, double scale, int nPanelsPerRow) {
        this.variable = variable;
        this.scalarType = scalarType;
        this.tag = tag;
        this.radii = radii;
        this.boundaries = boundaries;
        this.mapRegion = mapRegion;
        this.positionInterval = positionInterval;
        this.scale = scale;
        this.nPanelsPerRow = nPanelsPerRow;
        if (boundaries.length <= radii.length) {
            throw new IllegalArgumentException(boundaries.length + " boundaries is not enough for " + radii.length + " layers.");
        }

        // set file name root of output files
        this.plotFileNameRoot = (variable != null ? variable.toString().toLowerCase() : "scalar")
                + scalarType.toNaturalString() + ((tag != null) ? ("_" + tag + "_") : "");
        // set this temporarily to display all layers (may be oveerwritten later)
        this.displayLayers = IntStream.range(0, radii.length).toArray();
        // set scalar file name
        String tag1 = (tag != null) ? (tag + "_XY") : "XY";
        this.scalarFileName = ScalarListFile.generateFileName(variable, scalarType, tag1);
    }

    void setMask(VariableType maskVariable, ScalarType maskScalarType, double maskThreshold) {
        this.maskExists = true;
        this.maskThreshold = maskThreshold;
        // set scalar file name
        String tag2 = (tag != null) ? (tag + "_forMaskXY") : "forMaskXY";
        this.maskFileName = ScalarListFile.generateFileName(maskVariable, maskScalarType, tag2);
    }

    /**
     * Specify which of the layers to display.
     * @param displayLayers (int[]) Indices of layers to plot, listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    void setDisplayLayers(int[] displayLayers) {
        this.displayLayers = displayLayers;
        for (int layerIndex : displayLayers) {
            if (layerIndex >= radii.length) {
                throw new IllegalArgumentException("Layer index " + layerIndex + " too large.");
            }
        }
    }

    void setCpStyle(int cpStyle, VariableType variable) {
        this.cpStyle = cpStyle;
    }

    void setForSlides(boolean forSlides) {
        this.forSlides = forSlides;
    }

    /**
     * Write cp_master file, grid shellscript, and map shellscript.
     * @param outPath (Path) Directory where output files should be written.
     * @throws IOException
     */
    void write(Path outPath) throws IOException {
        writeCpMaster(outPath.resolve("cp_master.cpt"), cpStyle, forSlides);
        if (maskExists) writeCpMask(outPath.resolve("cp_mask.cpt"), maskThreshold);
        writeGridMaker(outPath.resolve(plotFileNameRoot + "Grid.sh"));
        writeMakeMap(outPath.resolve(plotFileNameRoot + "Map.sh"));
    }

    static void writeCpMaster(Path outputPath, int cpStyle, boolean forSlides) throws IOException {
        int styleCode = (forSlides ? cpStyle * 10 + 1 : cpStyle * 10);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            switch(styleCode) {
            case 0:
            case 1:
                // red-yellow-white-skyblue-turquoise
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
                break;
            case 10:
                // orange-yellow-white-cyan-skyblue
                pw.println("-4.00 127  25   5  -3.75 127  25   5");
                pw.println("-3.75 161  55  14  -3.25 161  55  14");
                pw.println("-3.25 199  91  28  -2.75 199  91  28");
                pw.println("-2.75 217 127  53  -2.25 217 127  53");
                pw.println("-2.25 230 164  83  -1.75 230 164  83");
                pw.println("-1.75 240 201 115  -1.25 240 201 115");
                pw.println("-1.25 245 227 159  -0.75 245 227 159");
                pw.println("-0.75 250 244 200  -0.25 250 244 200");
                pw.println("-0.25 253 253 253  0.25 253 253 253");
                pw.println("0.25 234 248 248   0.75 234 248 248");
                pw.println("0.75 200 242 243   1.25 200 242 243");
                pw.println("1.25 158 221 232   1.75 158 221 232");
                pw.println("1.75 124 195 225   2.25 124 195 225");
                pw.println("2.25  83 159 199   2.75  83 159 199");
                pw.println("2.75  49 121 168   3.25  49 121 168");
                pw.println("3.25  37  91 153   3.75  37  91 153");
                pw.println("3.75  27  65 140   4.00  27  65 140");
                pw.println("B       127  25   5");
                pw.println("F        27  65 140");
                pw.println("N       255 255 255");
                break;
            case 11:
                // orange-yellow-white-cyan-skyblue
                pw.println("-4.00 127  25   5  -3.75 127  25   5");
                pw.println("-3.75 161  55  14  -3.25 161  55  14");
                pw.println("-3.25 199  91  28  -2.75 199  91  28");
                pw.println("-2.75 217 127  53  -2.25 217 127  53");
                pw.println("-2.25 230 163  80  -1.75 230 163  80");
                pw.println("-1.75 240 198 108  -1.25 240 198 108");
                pw.println("-1.25 245 223 142  -0.75 245 223 142");
                pw.println("-0.75 250 240 175  -0.25 250 240 175");
                pw.println("-0.25 253 253 253  0.25 253 253 253");
                pw.println("0.25 221 248 248   0.75 221 248 248");
                pw.println("0.75 189 241 243   1.25 189 241 243");
                pw.println("1.25 153 220 232   1.75 153 220 232");
                pw.println("1.75 121 194 225   2.25 121 194 225");
                pw.println("2.25  83 159 199   2.75  83 159 199");
                pw.println("2.75  49 121 168   3.25  49 121 168");
                pw.println("3.25  37  91 153   3.75  37  91 153");
                pw.println("3.75  27  65 140   4.00  27  65 140");
                pw.println("B       127  25   5");
                pw.println("F        27  65 140");
                pw.println("N       255 255 255");
                break;
            case 20:
            case 21:
                // red-orange-white-skyblue-purple
                pw.println("-4.00 102   5   6  -3.75 102   5   6");
                pw.println("-3.75 145  12   7  -3.25 145  12   7");
                pw.println("-3.25 204  32  16  -2.75 204  32  16");
                pw.println("-2.75 230  85  50  -2.25 230  85  50");
                pw.println("-2.25 240 136  84  -1.75 240 136  84");
                pw.println("-1.75 243 180 114  -1.25 243 180 114");
                pw.println("-1.25 248 212 128  -0.75 248 212 128");
                pw.println("-0.75 250 235 154  -0.25 250 235 154");
                pw.println("-0.25 253 253 253  0.25 253 253 253");
                pw.println("0.25 198 232 248   0.75 198 232 248");
                pw.println("0.75 169 202 235   1.25 169 202 235");
                pw.println("1.25 147 172 220   1.75 147 172 220");
                pw.println("1.75 129 142 215   2.25 129 142 215");
                pw.println("2.25 104 104 209   2.75 104 104 209");
                pw.println("2.75  90  59 199   3.25  90  59 199");
                pw.println("3.25  69   6 158   3.75  69   6 158");
                pw.println("3.75  53   5 102   4.00  53   5 102");
                pw.println("B       102   5   6");
                pw.println("F        53   5 102");
                pw.println("N       255 255 255");
                break;
            }
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
            pw.println("    grep \"$depth\" " + scalarFileName + " | \\");
            pw.println("    awk '{print $2,$1,$4}' | \\");
            pw.println("    gmt xyz2grd -G$dep\\model.grd -R" + mapRegion + " -I" + positionInterval + " -di0");

            // grid mask
            if (maskExists) {
                pw.println("    grep \"$depth\" " + maskFileName + " | \\");
                pw.println("    awk '{print $2,$1,$4}' | \\");
                pw.println("    gmt xyz2grd -G$dep\\mask.grd -R" + mapRegion + " -I" + positionInterval + " -di0");
            }

            pw.println("done");
        }
    }

    private void writeMakeMap(Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("#------- GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 6000x6000");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 0p");
            if (forSlides) pw.println("gmt set FORMAT_GEO_MAP D");
            pw.println("gmt set FONT " + (forSlides ? "40" : "30"));
            pw.println("gmt set FONT_TITLE " + (forSlides ? "50" : "40"));
            pw.println("gmt set FONT_ANNOT " + (forSlides ? "40" : "30"));
            pw.println("gmt set FONT_LABEL " + (forSlides ? "50" : "40") + "p,Helvetica,black");
            pw.println("");
            pw.println("#------- Map parameters");
            pw.println("R='-R" + mapRegion + "'");
            pw.println("J='-JQ15'");
            pw.println("B='-B" + decideTickSpacing(mapRegion) + " -BwESn'");
            pw.println("");
            pw.println("#------- Color palette");
            pw.println("MP=" + scale);
            pw.println("gmt makecpt -Ccp_master.cpt -T-$MP/$MP > cp.cpt");
            pw.println("");

            pw.println("#------- Begin main plot");
            pw.println("gmt begin " + plotFileNameRoot + "Map eps,pdf,png");
            pw.println("");
            pw.println("#------- Panels");
            int nPanelsPerColumn = MathAid.divideUp(displayLayers.length, nPanelsPerRow);
            pw.println("gmt subplot begin " + nPanelsPerColumn + "x" + nPanelsPerRow + " -Fs15/0 -SCb+t -SRr -M1/1.5 -Y10 $B $J $R");
            pw.println("");

            for (int iPanel = 0; iPanel < displayLayers.length; iPanel++) {
                // CAUTION: layers are referenced in reverse order because we will draw from the top
                int iPanelRev = displayLayers.length - 1 - iPanel;
                int layerIndex = displayLayers[iPanelRev];
                int radius = (int) radii[layerIndex];
                String upperBound = MathAid.simplestString(boundaries[layerIndex + 1]);
                String lowerBound = MathAid.simplestString(boundaries[layerIndex]);

                pw.println("gmt subplot set");
                pw.println("gmt grdimage " + radius + "\\model.grd -B+t\"" + upperBound + "-" + lowerBound + " km\" -Ccp.cpt");

                if (maskExists) {
                    pw.println("gmt grdimage " + radius + "\\mask.grd -Ccp_mask.cpt -G0/0/0 -t80");
                }

                pw.println("gmt pscoast -Wthinner,black -A500");
                pw.println("");
            }

            pw.println("gmt subplot end");
            pw.println("");
            pw.println("#------- Scale");
            int scaleWidth = (forSlides ? 12 : 10);
            int slashSize = (forSlides ? 75 : 60);
            pw.println("gmt psscale -Ccp.cpt -DJCB+w" + scaleWidth + "/0.8+h -Y-2 -B$MP+l\""
                        + ScalarType.createScaleLabel(variable, scalarType, slashSize) + "\"");
            pw.println("#gmt psscale -Ccp.cpt -DJCB+w" + scaleWidth + "/0.8+h -Y-2 -B$MP+l\""
                        + ScalarType.createScaleLabel_TeX(variable, scalarType) + "\"");
            pw.println("");
            pw.println("#------- Finalize");
            pw.println("gmt end");
            pw.println("");

            pw.println("#-------- Clear");
            pw.println("rm -rf cp.cpt gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
    }

    /**
     * Decides the interval in which to sample the grid in a map.
     * This method sets the interval at roughly a {@value #SMOOTHING_FACTOR}-th of the input position spacing.
     * @param positions (Set of {@link HorizontalPosition}) Input position set.
     * @return (double) Suggested value of grid spacing.
     */
    static double decideGridSampling(Set<? extends HorizontalPosition> positions) {
        double positionInterval = HorizontalPosition.findLatitudeInterval(positions) / SMOOTHING_FACTOR;
        // the exponent in scientific notation
        int power = (int) Math.floor(Math.log10(positionInterval));
        // the coefficient in scientific notation
        double coef = positionInterval / FastMath.pow(10, power);
        // round down the coefficient to either 1, 2, or 5
        if (coef < 1) throw new IllegalStateException("Grid interval decision went wrong.");
        else if (coef < 2) return 1.0 * FastMath.pow(10, power);
        else if (coef < 5) return 2.0 * FastMath.pow(10, power);
        else if (coef < 10) return 5.0 * FastMath.pow(10, power);
        else throw new IllegalStateException("Grid interval decision went wrong.");
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

    static String decideTickSpacing(double length, boolean setGrid) {
        double aNum, fNum;
        if (length > 150.0) {
            aNum = 30; fNum = 30;
        } else if (length > 70.0) {
            aNum = 30; fNum = 10;
        } else if (length > 55.0) {
            aNum = 20; fNum = 10;
        } else if (length > 40.0) {
            aNum = 15; fNum = 5;
        } else if (length > 22.0) {
            aNum = 10; fNum = 5;
        } else if (length > 12.0) {
            aNum = 5; fNum = 2.5;
        } else if (length > 9.0) {
            aNum = 4; fNum = 2;
        } else if (length > 4.0) {
            aNum = 2; fNum = 1;
        } else {
            aNum = 1; fNum = 0.5;
        }
        return "a" + MathAid.simplestString(aNum) + (setGrid ? "g" + MathAid.simplestString(aNum) : "f" + MathAid.simplestString(fNum));
    }

    static String decideTickSpacing(String mapRegion) {
        String[] parts = mapRegion.split("/");
        int lonMin = Integer.parseInt(parts[0]);
        int lonMax = Integer.parseInt(parts[1]);
        int latMin = Integer.parseInt(parts[2]);
        int latMax = Integer.parseInt(parts[3]);
        // get average of longitude length and latitude length
        double length = ((lonMax - lonMin) + (latMax - latMin)) / 2.0;
        return decideTickSpacing(length, false);
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

    String getPlotFileNameRoot() {
        return plotFileNameRoot;
    }

}
