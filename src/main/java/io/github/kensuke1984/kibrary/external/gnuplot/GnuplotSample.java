package io.github.kensuke1984.kibrary.external.gnuplot;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Sample of how to use {@link GnuplotFile}.
 *
 * @author otsuru
 * @since 2021/12/07
 */
final class GnuplotSample {

    public static void main(String[] args) throws IOException {

        GnuplotFile gnuplot = new GnuplotFile(Paths.get("sample.plt"));
        GnuplotLineAppearance appearance1 = new GnuplotLineAppearance();
        GnuplotLineAppearance appearance2 = new GnuplotLineAppearance(2, GnuplotColorName.red, 3);

        gnuplot.setOutput("pdf", "sample.pdf", 21, 29.7, true);
        gnuplot.setKey(true, true, "top right");

        gnuplot.setXlabel("time");
        gnuplot.setYlabel("value");
        //gnuplot.setTitle("Test");

        gnuplot.addLabel("AAA AA", "graph", 0, 0.95);
        gnuplot.addLine("sample.txt", 1, 2, appearance1, "hello");
        gnuplot.addLine("sample.txt", 1, 3, appearance2, "world");
        gnuplot.nextField();

        gnuplot.addLine("sample.txt", 1, 4, appearance2, "");
        gnuplot.nextPage();

        gnuplot.addLine("sample.txt", 1, 2, appearance1, "");
        gnuplot.addLabel("ABB AA 1234.5", "graph", 0, 0.95);
        gnuplot.addLabel("This is a label.", "graph", 0, 0.85);
        gnuplot.addLine("sample.txt", 1, 4, appearance2, "test");
        gnuplot.nextField();

        gnuplot.write();
        if (!gnuplot.execute()) System.err.println("gnuplot failed!!");

    }

}
