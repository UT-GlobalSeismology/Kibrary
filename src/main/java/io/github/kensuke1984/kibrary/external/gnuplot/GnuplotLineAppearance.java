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
    private double linewidth = 1;


    /**
     * Constructor using default settings.
     * Default:
     *  dashtype 1,
     *  linecolor black,
     *  linewidth 1
     */
    public GnuplotLineAppearance() {
    }

    public GnuplotLineAppearance(int dashtype, GnuplotColorName linecolor, double linewidth) {
        this.dashtype = dashtype;
        this.linecolor = linecolor;
        this.linewidth = linewidth;
    }

    public int getDashtype() {
        return dashtype;
    }

    public GnuplotLineAppearance withDashtype(int dashtype) {
        return new GnuplotLineAppearance(dashtype, linecolor, linewidth);
    }

    public GnuplotColorName getLinecolor() {
        return linecolor;
    }

    public GnuplotLineAppearance withLinecolor(GnuplotColorName linecolor) {
        return new GnuplotLineAppearance(dashtype, linecolor, linewidth);
    }

    public double getLinewidth() {
        return linewidth;
    }

    public GnuplotLineAppearance withLinewidth(double linewidth) {
        return new GnuplotLineAppearance(dashtype, linecolor, linewidth);
    }

    @Override
    public String toString() {
        return "dt " + dashtype + " lc rgb \"" + linecolor.nameColorName() + "\" lw " + linewidth;
    }

}
