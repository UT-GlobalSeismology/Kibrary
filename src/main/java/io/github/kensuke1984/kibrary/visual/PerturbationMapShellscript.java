package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * @author otsuru
 * @since 2022/4/12
 * @version 2022/7/17 renamed from MapperShellscript to PerturbationMapShellscript
 */
public class PerturbationMapShellscript {

    /**
     * Number of panels to map in each row
     */
    private static final int PANEL_PER_ROW = 4;
    /**
     * The interval of deciding map size
     */
    private static final int INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map
     */
    private static final int MAP_RIM = 5;

    private final VariableType variable;
    private final double[] radii;
    private final String mapRegion;
    private final double scale;
    private final String modelFileNameRoot;
    private String maskFileNameRoot;
    private double maskThreshold;

    public PerturbationMapShellscript(VariableType variable, double[] radii, String mapRegion, double scale,
            String modelFileNameRoot) {
        this.variable = variable;
        this.radii = radii;
        this.mapRegion = mapRegion;
        this.scale = scale;
        this.modelFileNameRoot = modelFileNameRoot;
    }

    public PerturbationMapShellscript(VariableType variable, double[] radii, String mapRegion, double scale,
            String modelFileNameRoot, String maskFileNameRoot, double maskThreshold) {
        this.variable = variable;
        this.radii = radii;
        this.mapRegion = mapRegion;
        this.scale = scale;
        this.modelFileNameRoot = modelFileNameRoot;
        this.maskFileNameRoot = maskFileNameRoot;
        this.maskThreshold = maskThreshold;
    }

    /**
     * Write cp_master file, grid shellscript, and map shellscript.
     * @param outPath (Path) Directory where output files should be written.
     * @throws IOException
     */
    public void write(Path outPath) throws IOException {
        writeCpMaster(outPath.resolve("cp_master.cpt"));
        if (maskFileNameRoot != null) writeCpMask(outPath.resolve("cp_mask.cpt"));
        writeGridMaker(outPath.resolve(modelFileNameRoot + "Grid.sh"));
        writeMakeMap(outPath.resolve(modelFileNameRoot + "Map.sh"));
    }

    private void writeCpMaster(Path outputPath) throws IOException {
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

    private void writeCpMask(Path outputPath) throws IOException {
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

            pw.print("for depth in");
            for (double radius : radii) {
                pw.print(" " + (int) radius + ".0");
            }
            pw.println("");

            pw.println("do");
            pw.println("    dep=${depth%.0}");
            pw.println("    grep \"$depth\" " + modelFileNameRoot + ".lst | \\");
            pw.println("    awk '{print $2,$1,$4}' | \\");
            pw.println("    gmt xyz2grd -G$dep.grd -R" + mapRegion + " -I5 -di0");//TODO parameterize interval (-I)
            pw.println("    gmt grdsample $dep.grd -G$dep\\smooth.grd -I0.5");//TODO parameterize interval (-I)
            if (maskFileNameRoot != null) {
                pw.println("    grep \"$depth\" " + maskFileNameRoot + ".lst | \\");
                pw.println("    awk '{print $2,$1,$4}' | \\");
                pw.println("    gmt xyz2grd -G$dep\\mask.grd -R" + mapRegion + " -I5 -di0");//TODO parameterize interval (-I)
                pw.println("    gmt grdsample $dep\\mask.grd -G$dep\\maskSmooth.grd -I0.5");//TODO parameterize interval (-I)
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
            pw.println("gmt set PS_MEDIA 3300x3300");
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
            // CAUTION: i is in reverse order because we will draw from the top
            for (int i = radii.length - 1; i >= 0; i--) {
                int radius = (int) radii[i];

                if (i == radii.length - 1) {
                    pw.println("gmt grdimage " + radius + "\\smooth.grd -BwESn+t\"" + (radius - 3455) + "-" + (radius - 3505)//TODO parameterize
                            + " km\" $B -Ccp.cpt $J $R -K -Y80 > $outputps");
                } else if (i % PANEL_PER_ROW == (PANEL_PER_ROW - 1)) {
                    pw.println("gmt grdimage " + radius + "\\smooth.grd -BwESn+t\"" + (radius - 3455) + "-" + (radius - 3505)//TODO parameterize
                            + " km\" $B -Ccp.cpt $J $R -K -O -X-63 -Y-20 >> $outputps");
                } else {
                    pw.println("gmt grdimage " + radius + "\\smooth.grd -BwESn+t\"" + (radius - 3455) + "-" + (radius - 3505)//TODO parameterize
                            + " km\" $B -Ccp.cpt $J $R -K -O -X21 >> $outputps");
                }

                if (maskFileNameRoot != null) {
                    pw.println("gmt grdimage " + radius + "\\maskSmooth.grd -Ccp_mask.cpt -G0/0/0 -t80 $J $R -K -O >> $outputps");
                }

                pw.println("gmt pscoast -Wthinner,black -A500 -J -R -K -O >> $outputps");
                pw.println("");
            }

            pw.println("#------- Scale");
            pw.println("gmt psscale -Ccp.cpt -Dx7/-1+w12/0.8+h -B1.0+l\"@~d@~" + paramName + "/" + paramName + " \\(\\%\\)\" -K -O -Y-3 -X-36 >> $outputps");
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
     * Decides a rectangular region of a map that is sufficient to plot all parameter points.
     * @param positions (Set of FullPosition) Positions that need to be included in map region
     * @return (String) "lonMin/lonMax/latMin/latMax"
     * @throws IOException
     */
    static String decideMapRegion(Set<FullPosition> positions) throws IOException {
        double latMin = Double.MAX_VALUE;
        double latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE;
        double lonMax = -Double.MAX_VALUE;
        // search all unknowns
        for (FullPosition pos : positions) {
            if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
            if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
            if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
            if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
        }
        // expand the region a bit more
        latMin = Math.floor(latMin / INTERVAL) * INTERVAL - MAP_RIM;
        latMax = Math.ceil(latMax / INTERVAL) * INTERVAL + MAP_RIM;
        lonMin = Math.floor(lonMin / INTERVAL) * INTERVAL - MAP_RIM;
        lonMax = Math.ceil(lonMax / INTERVAL) * INTERVAL + MAP_RIM;
        // return as String
        return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
    }
}
