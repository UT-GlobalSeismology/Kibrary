package io.github.kensuke1984.kibrary.external.gnuplot;

import java.util.ArrayList;
import java.util.List;

class GnuplotField {

    private String label;
    private List<GnuplotLine> lines = new ArrayList<GnuplotLine>();

    GnuplotField(){
    }

    void addLine(GnuplotLine line) {
        lines.add(line);
    }

    int size() {
        return lines.size();
    }

    GnuplotLine line(int num) {
        return lines.get(num);
    }

    void setLabel(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }
}
