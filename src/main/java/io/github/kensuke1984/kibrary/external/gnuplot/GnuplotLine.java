package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about a line to be plotted in gnuplot.
 *
 * @author otsuru
 * @since 2021/12/06
 */
class GnuplotLine {

    /**
     * Name of file that contains data to be plotted.
     */
    private String fileName;

    /**
     * データをどう使うか　using ????? の　???の分
     * （例　1:3, 1:($3+$1)）
     */
    private String plotPart;

    private GnuplotLineAppearance appearance;

    GnuplotLine(String fileName, String plotPart, GnuplotLineAppearance appearance) {
        this.fileName = fileName;
        this.plotPart = plotPart;
        this.appearance = appearance;
    }

    GnuplotLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance) {
        this.fileName = fileName;
        this.plotPart = columnX + ":" + columnY;
        this.appearance = appearance;
    }

    @Override
    public String toString() {
        return "\"" + fileName + "\"" + " u " + plotPart + " " + appearance.toString();
    }


}
