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
    private boolean isPdf = false;
    private String output = "output.png";

    /**
     * x軸のラベル
     */
    private String xlabel = "";
    /**
     * y軸のラベル
     */
    private String ylabel = "";
    /**
     * 図のタイトル
     */
    private String title = "";

    /**
     * 凡例をつけるか
     */
    private boolean key = false;

    // For the following variables, flags are used because primitive types cannot be empty.

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

    private boolean drawStarted = false;
    private List<GnuplotMultiplot> pages = new ArrayList<GnuplotMultiplot>();

    public GnuplotFile(Path path) {
        filePath = path;
        pages.add(new GnuplotMultiplot());
    }

    public boolean execute() throws IOException {
        if (!ExternalProcess.isInPath("gnuplot")) throw new NoSuchFileException("No gnuplot in PATH.");

        ExternalProcess xProcess = ExternalProcess.launch("gnuplot " + filePath, Paths.get("."));
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

            for (int k = 0; k < pages.size(); k++) {
                pw.println("set multiplot layout " + pages.get(k).size() + ",1");

                for (int j = 0; j < pages.get(k).size(); j++) {
                    for (int i = 0; i < pages.get(k).field(j).size(); i++) {
                        if (i == 0) {
                            pw.print("plot ");
                        } else {
                            pw.print("    ");
                        }

                        pw.print(pages.get(k).field(j).line(i).toString());

                        if (i == pages.get(k).field(j).size() - 1) {
                            pw.println();
                        } else {
                            pw.println(",\\");
                        }
                    }
                }

                pw.println("unset multiplot");
            }

            pw.flush();
            pw.close();
        }
    }

    public void addLine(String fileName, String plotPart, GnuplotLineAppearance appearance) {
        drawStarted = true;
        // add line to current page
        pages.get(pages.size() - 1).addLine(new GnuplotLine(fileName, plotPart, appearance));
    }

    public void addLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance) {
        drawStarted = true;
        // add line to current page
        pages.get(pages.size() - 1).addLine(new GnuplotLine(fileName, columnX, columnY, appearance));
    }

    public void nextField() {
        drawStarted = true;
        // add field to current page
        pages.get(pages.size() - 1).nextField();
    }

    /**
     * Switches to the next page.
     * This can only be done for "pdf" style. Otherwise, an IllegalArgumentException will be thrown.
     */
    public void nextPage() {
        if (!isPdf) throw new IllegalArgumentException("New page cannot be added for output types other than pdf.");
        drawStarted = true;
        pages.add(new GnuplotMultiplot());
    }

    /**
     * Sets the type of output graph file and its name.
     * This must be set before lines, fields, or pages are added.
     * If this function is called after drawing has started, and IllegalStateException will be thrown.
     * @param type (String) Choose from "pdf", "png", and "eps". Otherwise, IlleganArgumentException will be thrown.
     * @param output (String) Name of output file
     */
    public void setOutput(String type, String output) {
        if (drawStarted) throw new IllegalStateException("Output cannot be changed after drawing has started.");

        switch(type) {
        case "pdf":
            this.terminal = "pdfcairo enhanced";
            this.isPdf = true;
            break;
        case "png":
            this.terminal = "pngcairo enhanced";
            break;
        case "eps":
            this.terminal = "epscairo enhanced";
            break;
        default:
            throw new IllegalArgumentException("Unrecognizable file type.");
        }

        this.output = output;
    }

    public String getTerminal() {
        return terminal;
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
