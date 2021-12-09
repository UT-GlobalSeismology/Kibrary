package io.github.kensuke1984.kibrary.external.gnuplot;

/**
 * Information about a label to be displayed in gnuplot.
 *
 * @author otsuru
 * @since 2021/12/09
 */
class GnuplotLabel {

    private String label;
    /**
     * The coordinate system used to specify position, from "first", "second", "graph", "screen", or "character".
     */
    private String coordinate;
    private double posX;
    private double posY;

    GnuplotLabel(String label, String coordinate, double posX, double posY) {
        this.label = label;
        this.coordinate = coordinate;
        this.posX = posX;
        this.posY = posY;
    }

    @Override
    public String toString() {
        return "at " + coordinate + " " + posX + "," + posY + " \"" + label + "\"";
    }

}
