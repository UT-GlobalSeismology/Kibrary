package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about line appearance in gnuplot.
 *
 * @author otsuru
 * @since 2021/12/06
 */
public class GnuplotLineAppearance {

    /**
     * line type (-1 to 10, may depend on environment)
     */
    private int linetype = 1;

    /**
     * line color
     */
    private GnuplotColorNames linecolor = GnuplotColorNames.black;

    /**
     * line width
     */
    private int linewidth = 1;


    /**
     * Constructor using default settings.
     * Default:
     *  linetype 1,
     *  linecolor black,
     *  linewidth 1
     */
    public GnuplotLineAppearance() {
    }

    public GnuplotLineAppearance(int linetype, GnuplotColorNames linecolor, int linewidth) {
        this.linetype = linetype;
        this.linecolor = linecolor;
        this.linewidth = linewidth;
    }


    public int getLinetype() {
        return linetype;
    }

    public void setLinetype(int linetype) {
        this.linetype = linetype;
    }

    public GnuplotColorNames getLinecolor() {
        return linecolor;
    }

    public void setLinecolor(GnuplotColorNames linecolor) {
        this.linecolor = linecolor;
    }

    public int getLinewidth() {
        return linewidth;
    }

    public void setLinewidth(int linewidth) {
        this.linewidth = linewidth;
    }

    @Override
    public String toString() {
        return "w lines lt " + linetype + " lc rgb \"" + linecolor.nameColorName() + "\" lw " + linewidth;
    }
}
