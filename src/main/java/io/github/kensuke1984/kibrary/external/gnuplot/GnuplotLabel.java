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
    private String coordinateX;
    private String coordinateY;
    private double posX;
    private double posY;

    GnuplotLabel(String label, String coordinate, double posX, double posY) {
        this.label = label;
        this.coordinateX = coordinate;
        this.coordinateY = coordinate;
        this.posX = posX;
        this.posY = posY;
    }

    GnuplotLabel(String label, String coordinateX, double posX, String coordinateY, double posY) {
        this.label = label;
        this.coordinateX = coordinateX;
        this.coordinateY = coordinateY;
        this.posX = posX;
        this.posY = posY;
    }

    @Override
    public String toString() {
        if (coordinateX.equals(coordinateY)) {
            return "at " + coordinateX + " " + posX + "," + posY + " \"" + label + "\"";
        } else {
            return "at " + coordinateX + " " + posX + "," + coordinateY + " " + posY + " \"" + label + "\"";
        }
    }

}
