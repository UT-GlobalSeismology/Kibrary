package io.github.kensuke1984.kibrary.external.gnuplot;

import java.util.ArrayList;
import java.util.List;

class GnuplotField {

    private String xrange;
    private String yrange;

    private List<GnuplotLine> lines = new ArrayList<>();
    private List<GnuplotLabel> labels = new ArrayList<>();
    private List<GnuplotArrow> arrows = new ArrayList<>();

    GnuplotField(){
    }

    // ----------line----------

    void addLine(GnuplotLine line) {
        lines.add(line);
    }

    int numLine() {
        return lines.size();
    }

    GnuplotLine line(int num) {
        return lines.get(num);
    }

    // ----------label----------

    void addLabel(GnuplotLabel label) {
        this.labels.add(label);
    }

    int numLabel() {
        return labels.size();
    }

    GnuplotLabel label(int index) {
        return labels.get(index);
    }

    // ----------arrow----------

    void addArrow(GnuplotArrow arrow) {
        this.arrows.add(arrow);
    }

    int numArrow() {
        return arrows.size();
    }

    GnuplotArrow arrow(int index) {
        return arrows.get(index);
    }

    // ----------plot range----------

    public void setXrange(double xmin, double xmax) {
        if (xmax <= xmin) throw new IllegalArgumentException("Input xmin xmax " + xmin + " " + xmax + " are invalid");
        xrange = "[" + xmin + ":" + xmax + "]";
    }

    /**
     * Sets a limit to the range of autoscaling for the x-axis.
     * @param xminLimit
     * @param xmaxLimit
     */
    public void setXrangeLimit(double xminLimit, double xmaxLimit) {
        if (xmaxLimit <= xminLimit)
            throw new IllegalArgumentException("Input xminLimit xmaxLimit " + xminLimit + " " + xmaxLimit + " are invalid");
        xrange = "[" + xminLimit + "<*:*<" + xmaxLimit + "]";
    }

    public void setYrange(double ymin, double ymax) {
        if (ymax <= ymin) throw new IllegalArgumentException("Input ymin ymax " + ymin + " " + ymax + " are invalid");
        yrange = "[" + ymin + ":" + ymax + "]";
    }

    /**
     * Sets a limit to the range of autoscaling for the y-axis.
     * @param yminLimit
     * @param ymaxLimit
     */
    public void setYrangeLimit(double yminLimit, double ymaxLimit) {
        if (ymaxLimit <= yminLimit)
            throw new IllegalArgumentException("Input yminLimit ymaxLimit " + yminLimit + " " + ymaxLimit + " are invalid");
        yrange = "[" + yminLimit + "<*:*<" + ymaxLimit + "]";
    }

    public boolean hasXrange() {
        return xrange != null;
    }

    public String getXrange() {
        return xrange;
    }

    public boolean hasYrange() {
        return yrange != null;
    }

    public String getYrange() {
        return yrange;
    }

}
