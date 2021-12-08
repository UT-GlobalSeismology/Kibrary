package io.github.kensuke1984.kibrary.external.gnuplot;

import java.util.ArrayList;
import java.util.List;

class GnuplotMultiplot {

    private List<GnuplotField> fields = new ArrayList<GnuplotField>();

    GnuplotMultiplot(){
        // make first field
        fields.add(new GnuplotField());
    }

    void nextField() {
        fields.add(new GnuplotField());
    }

    void addLine(GnuplotLine line) {
        // add line to the current field
        fields.get(fields.size() - 1).addLine(line);
    }

    int size() {
        return fields.size();
    }

    GnuplotField field(int num) {
        return fields.get(num);
    }
}
