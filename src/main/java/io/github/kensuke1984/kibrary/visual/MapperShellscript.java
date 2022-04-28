package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class MapperShellscript {

    private double[] radii;
    private String modelFileName;

    public MapperShellscript(double[] radii, String modelFileName) {
        this.radii = radii;
        this.modelFileName = modelFileName;
    }

    public void write(Path outPath) throws IOException {
        writeCpMaster(outPath.resolve("cp_master.cpt"));
        writeGridMaker(outPath.resolve("gridmaker.sh"));
        writeMakeMap(outPath.resolve("makemap.sh"));
    }

    private void writeCpMaster(Path outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
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

    private void writeGridMaker(Path outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            pw.println("#!/bin/sh");
            pw.println("");

            pw.print("for depth in");
            for (double radius : radii) {
                pw.print(" " + (int) radius + ".0");
            }
            pw.println("");

            pw.println("do");
            pw.println("    dep=${depth%.0}");
            pw.println("    grep \"$depth\" " + modelFileName + ".lst | \\");
            pw.println("    awk '{print $2,$1,$4}' | \\");
            pw.println("    gmt xyz2grd -G$dep.grd -R-60/45/-55/35 -I5 -di0");//TODO parameterize position range (-R) and interval (-I)
            pw.println("    gmt grdsample $dep.grd -G$dep\\comp.grd -I0.5");//TODO parameterize interval (-I)
            pw.println("done");
        }
    }
    private void writeMakeMap(Path outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
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
            pw.println("# parameters for gmt pscoast");
            pw.println("R='-R-60/45/-55/35'");//TODO parameterize
            pw.println("J='-JQ15'");
            pw.println("G='-G255/255/255';");
            pw.println("B='-B30f10';");
            pw.println("O='-W1';");
            pw.println("");
            pw.println("outputps=" + modelFileName + "Map.eps");
            pw.println("MP=3");//TODO parameterize
            pw.println("gmt makecpt -Ccp_master.cpt -T-$MP/$MP > cp.cpt");
            pw.println("");

            for (int i = radii.length - 1; i >= 0; i--) {
                int radius = (int) radii[i];

                if (i == radii.length - 1) {
                    pw.println("gmt grdimage " + radius + "\\comp.grd -BwESn+t\"" + (radius - 3505) + "-" + (radius - 3455)//TODO parameterize
                            + "km\" $B $J $R -Ccp.cpt -K -Y80 > $outputps");
                } else if (i == radii.length / 2 - 1) {
                    pw.println("gmt grdimage " + radius + "\\comp.grd -BwESn+t\"" + (radius - 3505) + "-" + (radius - 3455)//TODO parameterize
                            + "km\" $B $J $R -Ccp.cpt -K -O -X-63 -Y-20 >> $outputps");
                } else {
                    pw.println("gmt grdimage " + radius + "\\comp.grd -BwESn+t\"" + (radius - 3505) + "-" + (radius - 3455)//TODO parameterize
                            + "km\" $B $J $R -Ccp.cpt -K -O -X21 >> $outputps");
                }

                if (i == 0) {
                    pw.println("gmt psscale -Ccp.cpt -D7/-1/12/0.8h -B1.0:@~d@~Vs/Vs\\(\\%\\): -K -O -Y-3 -X-32 >> $outputps");
                    pw.println("gmt pscoast -R -J $O -X32 -Y3 -O -Wthinner,black -A500 >> $outputps");
                } else {
                    pw.println("gmt pscoast -R -J $O -K -O  -Wthinner,black -A500 >> $outputps");
                }

                pw.println("");
            }

            pw.println("gmt psconvert $outputps -E100 -Tf -A -Qg4");
            pw.println("gmt psconvert $outputps -E100 -Tg -A -Qg4");
//            pw.println("gmt ps2raster $outputps -A -Tgf -Qg4 -E150");
            pw.println("echo \"Done!\"");
        }
    }

}
