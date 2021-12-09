package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about line appearance in gnuplot.
 *
 * @author otsuru
 * @since 2021/12/06
 */
public class GnuplotLineAppearance {

    /**
     * dash type of line (1 to 10, may depend on environment)
     */
    private int dashtype = 1;

    /**
     * line color
     */
    private GnuplotColorName linecolor = GnuplotColorName.black;

    /**
     * line width
     */
    private int linewidth = 1;


    /**
     * Constructor using default settings.
     * Default:
     *  dashtype 1,
     *  linecolor black,
     *  linewidth 1
     */
    public GnuplotLineAppearance() {
    }

    public GnuplotLineAppearance(int dashtype, GnuplotColorName linecolor, int linewidth) {
        this.dashtype = dashtype;
        this.linecolor = linecolor;
        this.linewidth = linewidth;
    }


    public int getDashtype() {
        return dashtype;
    }

    public void setDashtype(int dashtype) {
        this.dashtype = dashtype;
    }

    public GnuplotColorName getLinecolor() {
        return linecolor;
    }

    public void setLinecolor(GnuplotColorName linecolor) {
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
        return "w lines dt " + dashtype + " lc rgb \"" + linecolor.nameColorName() + "\" lw " + linewidth;
    }
}
