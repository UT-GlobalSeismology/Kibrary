package io.github.kensuke1984.kibrary.external.gnuplot;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author otsuru
 * @since 2021/12/07
 */
public class GnuplotSample {

    public static void main(String[] args) throws IOException {

        GnuplotFile gnuplot = new GnuplotFile(Paths.get("sample.plt"));
        GnuplotLineAppearance appearance1 = new GnuplotLineAppearance();
        GnuplotLineAppearance appearance2 = new GnuplotLineAppearance(2, GnuplotColorNames.red, 3);

        gnuplot.setTerminal("png");
        gnuplot.setOutput("sample.png");


        gnuplot.addLine("sample.txt", 1, 2, appearance1);
        gnuplot.addLine("sample.txt", 1, 3, appearance2);
        gnuplot.addLine("sample.txt", 1, 4, appearance2);
        gnuplot.write();
        gnuplot.execute();

    }

}
