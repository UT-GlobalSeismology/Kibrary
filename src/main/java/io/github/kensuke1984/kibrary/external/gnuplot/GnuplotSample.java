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
        GnuplotLineAppearance appearance = new GnuplotLineAppearance();
        System.err.println(appearance);

        gnuplot.setTerminal("png");
        gnuplot.setOutput("sample.png");


        gnuplot.addLine("sample.txt", 1, 2, appearance);
        gnuplot.write();
        gnuplot.execute();

    }

}
