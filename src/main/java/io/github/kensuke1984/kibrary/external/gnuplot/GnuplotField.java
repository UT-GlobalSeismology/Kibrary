package io.github.kensuke1984.kibrary.external.gnuplot;

import java.util.ArrayList;
import java.util.List;

class GnuplotField {

    private List<GnuplotLine> lines = new ArrayList<GnuplotLine>();
    private List<GnuplotLabel> labels = new ArrayList<GnuplotLabel>();

    GnuplotField(){
    }

    void addLine(GnuplotLine line) {
        lines.add(line);
    }

    int numLine() {
        return lines.size();
    }

    GnuplotLine line(int num) {
        return lines.get(num);
    }

    void addLabel(GnuplotLabel label) {
        this.labels.add(label);
    }

    int numLabel() {
        return labels.size();
    }

    GnuplotLabel label(int index) {
        return labels.get(index);
    }
}
