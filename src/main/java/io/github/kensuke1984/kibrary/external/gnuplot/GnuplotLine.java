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

    private String title;

    /**
     * @param fileName
     * @param plotPart
     * @param appearance
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    GnuplotLine(String fileName, String plotPart, GnuplotLineAppearance appearance, String title) {
        this.fileName = fileName;
        this.plotPart = plotPart;
        this.appearance = appearance;
        this.title = title;
    }

    /**
     * @param fileName
     * @param columnX
     * @param columnY
     * @param appearance
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    GnuplotLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance, String title) {
        this.fileName = fileName;
        this.plotPart = columnX + ":" + columnY;
        this.appearance = appearance;
        this.title = title;
    }

    @Override
    public String toString() {
        if (title.isEmpty()) {
            return "\"" + fileName + "\"" + " u " + plotPart + " " + appearance.toString() + " notitle";
        } else {
            return "\"" + fileName + "\"" + " u " + plotPart + " " + appearance.toString() + " title \"" + title + "\"";
        }
    }


}
