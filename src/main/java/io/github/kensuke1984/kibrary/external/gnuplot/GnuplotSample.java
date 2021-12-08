package io.github.kensuke1984.kibrary.external.gnuplot;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Sample of how to use {@link GnuplotFile}.
 *
 * @author otsuru
 * @since 2021/12/07
 */
public class GnuplotSample {

    public static void main(String[] args) throws IOException {

        GnuplotFile gnuplot = new GnuplotFile(Paths.get("sample.plt"));
        GnuplotLineAppearance appearance1 = new GnuplotLineAppearance();
        GnuplotLineAppearance appearance2 = new GnuplotLineAppearance(2, GnuplotColorNames.red, 3);

        gnuplot.setOutput("pdf", "sample.pdf", 21, 29.7, true);

        gnuplot.setXlabel("time");
        gnuplot.setYlabel("value");
        //gnuplot.setTitle("Test");

        gnuplot.setLabelOnField("AAA AA");
        gnuplot.addLine("sample.txt", 1, 2, appearance1);
        gnuplot.addLine("sample.txt", 1, 3, appearance2);
        gnuplot.nextField();

        gnuplot.addLine("sample.txt", 1, 4, appearance2);
        gnuplot.setLabelOnField("ABB AA 1234.5");
        gnuplot.nextPage();

        gnuplot.addLine("sample.txt", 1, 2, appearance1);
        gnuplot.setLabelOnField("This is a label.");
        gnuplot.addLine("sample.txt", 1, 4, appearance2);
        gnuplot.nextField();

        gnuplot.write();
        gnuplot.execute();

    }

}
