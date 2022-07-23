package io.github.kensuke1984.kibrary.external.gnuplot;

import java.util.ArrayList;
import java.util.List;

class GnuplotField {

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

}
