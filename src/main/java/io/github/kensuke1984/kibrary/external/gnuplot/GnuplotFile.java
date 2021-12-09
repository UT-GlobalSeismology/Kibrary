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
 * <p>
 * Gnuplot version 5.0 or above must be in your PATH.
 *
 * @author Kensuke Konishi
 * @version 0.0.1
 */
public class GnuplotFile {

    /**
     * Plot file path
     */
    private final Path filePath;

    /**
     * Output graph file name
     */
    private String output = "output.png";
    /**
     * Whether the output file extension is pdf
     */
    private boolean isPdf = false;
    private String terminal = "pngcairo enhanced";
    private double sizeX = 640;
    private double sizeY = 480;
    /**
     * Whether the size is set in cm (true: cm ; false: pixels)
     */
    private boolean cm = false;

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
     * Settings for the key. If "", no key.
     */
    private String keySettings = "";

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
    private List<GnuplotPage> pages = new ArrayList<GnuplotPage>();

    public GnuplotFile(Path path) {
        filePath = path;
        // make first page
        pages.add(new GnuplotPage());
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
            if (cm) {
                pw.println("set term " + terminal + " size " + sizeX + "cm," + sizeY + "cm");
            } else {
                pw.println("set term " + terminal + " size " + sizeX + "," + sizeY);
            }
            pw.println("set out \"" + output + "\"");

            if (!keySettings.isEmpty()) {
                pw.println("set key " + keySettings);
            } else {
                pw.println("unset key");
            }
            if (ratioFlag) pw.println("set size ratio " + ratio);
            if (xrangeFlag) pw.println("set xrange [" + xmin + ":" + xmax + "]");
            if (yrangeFlag) pw.println("set yrange [" + ymin + ":" + ymax + "]");
            if (xticsFlag) pw.println("set xtics " + xtics);
            if (yticsFlag) pw.println("set ytics " + ytics);

            if (!xlabel.isEmpty()) pw.println("set xlabel \"" + xlabel + "\"");
            if (!ylabel.isEmpty()) pw.println("set ylabel \"" + ylabel + "\"");
            if (!title.isEmpty()) pw.println("set title \"" + title + "\"");

            // each page
            for (int k = 0; k < pages.size(); k++) {
                pw.println("set multiplot layout " + pages.get(k).numField() + ",1");

                // each field
                for (int j = 0; j < pages.get(k).numField(); j++) {

                    // each label
                    if (pages.get(k).field(j).numLabel() == 0) {
                        pw.println("unset label");
                    } else for (int label = 0; label < pages.get(k).field(j).numLabel(); label++) {
                        pw.println("set label " + (label + 1) + " " + pages.get(k).field(j).label(label).toString());
                    }

                    // each line
                    for (int i = 0; i < pages.get(k).field(j).numLine(); i++) {
                        if (i == 0) {
                            pw.print("plot ");
                        } else {
                            pw.print("    ");
                        }

                        pw.print(pages.get(k).field(j).line(i).toString());

                        if (i == pages.get(k).field(j).numLine() - 1) {
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

    /**
     * @param fileName (String)
     * @param plotPart (String) The content of the "using" part.
     * @param appearance ({@link GnuplotAppearance})
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    public void addLine(String fileName, String plotPart, GnuplotLineAppearance appearance, String title) {
        drawStarted = true;
        // add line to current page
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLine(new GnuplotLine(fileName, plotPart, appearance, title));
    }

    /**
     * @param fileName (String)
     * @param columnX (double) The column number of input file to use for the x-axis
     * @param columnY (double) The column number of input file to use for the y-axis
     * @param appearance ({@link GnuplotAppearance})
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    public void addLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance, String title) {
        drawStarted = true;
        // add line to current page
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLine(new GnuplotLine(fileName, columnX, columnY, appearance, title));
    }

    /**
     * @param label (String)
     * @param coordinate (String) The coordinate system used to specify position, from "first", "second", "graph", "screen", or "character".
     * @param posX (double)
     * @param posY (double)
     */
    public void addLabel(String label, String coordinate, double posX, double posY) {
        drawStarted = true;
        // add label to current field
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLabel(new GnuplotLabel(label, coordinate, posX, posY));
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
        pages.add(new GnuplotPage());
    }

    /**
     * Sets the type of output graph file, its name, and size.
     * This must be set before lines, fields, or pages are added.
     * If this function is called after drawing has started, an IllegalStateException will be thrown.
     * @param type (String) Choose from "pdf", "png", and "eps". Otherwise, IlleganArgumentException will be thrown.
     * @param output (String) Name of output file
     * @param sizeX (double) Width of output file
     * @param sizeY (double) Height of output file
     * @param cm (boolean) true if the size is given in cm ; if false, pixels (must be true for pdf and eps)
     */
    public void setOutput(String type, String output, double sizeX, double sizeY, boolean cm) {
        if (drawStarted) throw new IllegalStateException("Output cannot be changed after drawing has started.");

        switch(type) {
        case "pdf":
            if (!cm) throw new IllegalArgumentException("For pdf, size cannot be set in pixels.");
            this.terminal = "pdfcairo enhanced";
            this.isPdf = true;
            break;
        case "png":
            this.terminal = "pngcairo enhanced";
            break;
        case "eps":
            if (!cm) throw new IllegalArgumentException("For eps, size cannot be set in pixels.");
            this.terminal = "epscairo enhanced";
            break;
        default:
            throw new IllegalArgumentException("Unrecognizable file type.");
        }

        this.output = output;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.cm = cm;
    }

    public String getTerminal() {
        return terminal;
    }

    public String getOutput() {
        return output;
    }

    /**
     * @param set (boolean) Whether to display key
     * @param box (boolean) Whether to surround the key with box
     * @param position (String) Position and additional options (if unneeded, set this "")
     */
    public void setKey(boolean set, boolean box, String position) {
        if(!set) {
            this.keySettings = "";
        } else if (!box){
            this.keySettings = "nobox "+ position;
        } else {
            this.keySettings = "box " + position;
        }
    }

    public String getKeySettings() {
        return keySettings;
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
