package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about a line to be plotted in gnuplot.
 *
 * @author otsuru
 * @since 2021/12/06
 */
class GnuplotLine {

    private String content;

    private GnuplotLineAppearance appearance;

    private String title;

    /**
     * @param function
     * @param appearance
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    GnuplotLine(String function, GnuplotLineAppearance appearance, String title) {
        content = function;
        this.appearance = appearance;
        this.title = title;
    }

    /**
     * @param fileName (String) Name of file that contains data to be plotted.
     * @param plotPart (String) The part after "using" (ex. 1:3, 1:($3+$1) )
     * @param appearance
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    GnuplotLine(String fileName, String plotPart, GnuplotLineAppearance appearance, String title) {
        content = "\"" + fileName + "\"" + " u " + plotPart;
        this.appearance = appearance;
        this.title = title;
    }

    /**
     * @param fileName (String) Name of file that contains data to be plotted.
     * @param columnX
     * @param columnY
     * @param appearance
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    GnuplotLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance, String title) {
        content = "\"" + fileName + "\"" + " u " + columnX + ":" + columnY;
        this.appearance = appearance;
        this.title = title;
    }

    @Override
    public String toString() {
        if (title.isEmpty()) {
            return content + " w lines " + appearance.toString() + " notitle";
        } else {
            return content + " w lines " + appearance.toString() + " title \"" + title + "\"";
        }
    }
}
