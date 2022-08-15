package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about arrow to be displayed in gnuplot.
 *
 * @author otsuru
 * @since 2022/7/23
 */
public class GnuplotArrow {

    private final String content;

    private final GnuplotLineAppearance appearance;

    GnuplotArrow(double posX, GnuplotLineAppearance appearance) {
        content = "from first " + posX + ", graph 0 rto graph 0,1";
        this.appearance = appearance;
    }

    @Override
    public String toString() {
        return content + " nohead " + appearance.toString();
    }
}
