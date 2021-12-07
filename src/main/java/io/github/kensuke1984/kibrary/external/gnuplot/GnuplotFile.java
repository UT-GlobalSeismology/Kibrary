package io.github.kensuke1984.kibrary.external.gnuplot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.external.ExternalProcess;

/**
 * Plot file for gnuplot.
 *
 * @author Kensuke Konishi
 * @version 0.0.1
 */
public class GnuplotFile {

    private final Path filePath;

    private String terminal = "png";
    private String output = "output.png";

    /**
     * x軸のラベル
     */
    private String xlabel = null;
    /**
     * y軸のラベル
     */
    private String ylabel = null;
    /**
     * 図のタイトル
     */
    private String title = null;

    /**
     * 凡例をつけるか
     */
    private boolean key = false;

    // For the following variables, flags are used because primitive types cannot be null.

    /**
     * set size ratio ??? の　???
     */
    private double ratio;
    private boolean ratioFlag = false;
    private double xmin;
    private double xmax;
    private boolean xrangeFlag = false;
    private double ymin;
    private double ymax;
    private boolean yrangeFlag = false;
    private double xtics;
    private boolean xticsFlag = false;
    private double ytics;
    private boolean yticsFlag = false;

    private List<GnuplotLine> lines = new ArrayList<GnuplotLine>();

    public GnuplotFile(Path path) {
        filePath = path;
    }

    public boolean execute() throws IOException {
        if (!ExternalProcess.isInPath("gnuplot")) throw new NoSuchFileException("No gnuplot in PATH.");

        ExternalProcess xProcess = ExternalProcess.launch("gnuplot" + filePath, Paths.get(""));
        return xProcess.waitFor() == 0;
    }

    /**
     * Write into a gnuplot file
     */
    public void write() throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath))) {
            pw.println("set term " + terminal);
            pw.println("set out \"" + output + "\"");

            if (!key) pw.println("unset key");
            if (ratioFlag) pw.println("set size ratio " + ratio);
            if (xrangeFlag) pw.println("set xrange [" + xmin + ":" + xmax + "]");
            if (yrangeFlag) pw.println("set yrange [" + ymin + ":" + ymax + "]");
            if (xticsFlag) pw.println("set xtics " + xtics);
            if (yticsFlag) pw.println("set ytics " + ytics);

            if (!xlabel.isEmpty()) pw.println("set xlabel \"" + xlabel + "\"");
            if (!ylabel.isEmpty()) pw.println("set ylabel \"" + ylabel + "\"");
            if (!title.isEmpty()) pw.println("set title \"" + title + "\"");

            System.err.println(lines.size());
            System.err.println(lines.get(0).toString());

            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) {
                    pw.print("plot ");
                } else {
                    pw.print("    ");
                }

                pw.print(lines.get(i).toString());

                if (i == lines.size() - 1) {
                    pw.println();
                } else {
                    pw.println(",\\");
                }
            }

            pw.flush();
            pw.close();
        }
    }

    public void addLine(String fileName, String plotPart, GnuplotLineAppearance appearance) {
        lines.add(new GnuplotLine(fileName, plotPart, appearance));
    }

    public void addLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance) {
        lines.add(new GnuplotLine(fileName, columnX, columnY, appearance));
    }

    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

    public String getTerminal() {
        return terminal;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getOutput() {
        return output;
    }

    public void setKey(boolean key) {
        this.key = key;
    }

    public boolean getKey() {
        return key;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
        ratioFlag = true;
    }

    public double getRatio() {
        return ratio;
    }

    public void setXrange(double xmin, double xmax) {
        if (xmax <= xmin) throw new IllegalArgumentException("Input xmin xmax " + xmin + " " + xmax + " are invalid");
        this.xmin = xmin;
        this.xmax = xmax;
        xrangeFlag = true;
    }

    public void setYrange(double ymin, double ymax) {
        if (ymax <= ymin) throw new IllegalArgumentException("Input ymin ymax " + ymin + " " + ymax + " are invalid");
        this.ymin = ymin;
        this.ymax = ymax;
        yrangeFlag = true;
    }

    public double getXmin() {
        return xmin;
    }

    public double getXmax() {
        return xmax;
    }

    public double getYmin() {
        return ymin;
    }

    public double getYmax() {
        return ymax;
    }

    public void setXtics(double xtics) {
        this.xtics = xtics;
        xticsFlag = true;
    }

    public void setYtics(double ytics) {
        this.ytics = ytics;
        yticsFlag = true;
    }

    public double getXtics() {
        return xtics;
    }

    public double getYtics() {
        return ytics;
    }

    public void setXlabel(String xlabel) {
        this.xlabel = xlabel;
    }

    public void setYlabel(String ylabel) {
        this.ylabel = ylabel;
    }

    public String getXlabel() {
        return xlabel;
    }

    public String getYlabel() {
        return ylabel;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }


}
