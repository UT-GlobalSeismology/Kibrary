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
     * Whether the output graph file format is pdf
     */
    private boolean isPdf = false;
    private String terminal = "pngcairo enhanced";
    private double sizeX = 640;
    private double sizeY = 480;
    /**
     * Whether the size is set in cm (true: cm ; false: pixels)
     */
    private boolean cm = false;

    private String fontName = "Arial";
    private int fontSizeTitle = 18;
    private int fontSizeLabel = 12;
    private int fontSizeTics = 12;
    private int fontSizeKey = 12;
    private int fontSizeDefault = 12;

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
    private double xminLimit;
    private double xmaxLimit;
    private boolean xrangeLimitFlag = false;
    private double ymin;
    private double ymax;
    private boolean yrangeFlag = false;
    private double yminLimit;
    private double ymaxLimit;
    private boolean yrangeLimitFlag = false;

    private double xtics;
    private boolean xticsFlag = false;
    private double ytics;
    private boolean yticsFlag = false;
    private int lmargin;
    private int rmargin;
    private boolean marginHFlag = false;
    private int tmargin;
    private int bmargin;
    private boolean marginVFlag = false;

    /**
     * Whether any drawing has started, because then, output file cannot be changed.
     */
    private boolean drawStarted = false;
    private List<GnuplotPage> pages = new ArrayList<GnuplotPage>();

    public GnuplotFile(Path filePath) {
        this.filePath = filePath;
        // make first page
        pages.add(new GnuplotPage());
    }

    public boolean execute() throws IOException {
        if (!ExternalProcess.isInPath("gnuplot")) throw new NoSuchFileException("No gnuplot in PATH.");

        ExternalProcess xProcess = ExternalProcess.launch("gnuplot " + filePath.getFileName(),
                (filePath.getParent() != null ? filePath.getParent() : Paths.get("")));
        return xProcess.waitFor() == 0;
    }

    /**
     * Write into a gnuplot file
     */
    public void write() throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath))) {
            String terminalSize;
            if (cm) {
                terminalSize =  "size " + sizeX + "cm," + sizeY + "cm";
            } else {
                terminalSize = "size " + sizeX + "," + sizeY;
            }
            pw.println("set term " + terminal + " " + terminalSize + " font \"" + fontName + "," + fontSizeDefault + "\"");
            pw.println("set out \"" + output + "\"");

            pw.println("set title font \"" + fontName + "," + fontSizeTitle + "\"");
            pw.println("set xlabel font \"" + fontName + "," + fontSizeLabel + "\"");
            pw.println("set ylabel font \"" + fontName + "," + fontSizeLabel + "\"");
            pw.println("set tics font \"" + fontName + "," + fontSizeTics + "\"");
            pw.println("set key font \"" + fontName + "," + fontSizeKey + "\"");

            if (!keySettings.isEmpty()) {
                pw.println("set key " + keySettings);
            } else {
                pw.println("unset key");
            }
            if(marginHFlag) {
                pw.println("set lmargin " + lmargin);
                pw.println("set rmargin " + rmargin);
            }
            if(marginVFlag) {
                pw.println("set tmargin " + tmargin);
                pw.println("set bmargin " + bmargin);
            }
            if (ratioFlag) pw.println("set size ratio " + ratio);

            if (xrangeFlag) {
                pw.println("set xrange [" + xmin + ":" + xmax + "]");
            } else if (xrangeLimitFlag) {
                pw.println("set xrange [" + xminLimit + "<*:*<" + xmaxLimit + "]");
            }
            if (yrangeFlag) {
                pw.println("set yrange [" + ymin + ":" + ymax + "]");
            } else if (yrangeLimitFlag) {
                pw.println("set yrange [" + yminLimit + "<*:*<" + ymaxLimit + "]");
            }

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
                    GnuplotField field = pages.get(k).field(j);

                    if (field.numLine() == 0) continue;

                    // plot range
                    if (field.hasXrange()) {
                        pw.println("set xrange " + field.getXrange());
                    }
                    if (field.hasYrange()) {
                        pw.println("set yrange [" + field.getYrange());
                    }

                    // each label
                    if (field.numLabel() == 0) {
                        pw.println(" unset label");
                    } else for (int label = 0; label < field.numLabel(); label++) {
                        pw.println(" set label " + (label + 1) + " " + field.label(label).toString());
                    }

                    // each arrow
                    for (int arrow = 0; arrow < field.numArrow(); arrow++) {
                        pw.println(" set arrow " + (arrow + 1) + " " + field.arrow(arrow).toString());
                    }

                    // each line
                    for (int i = 0; i < field.numLine(); i++) {
                        if (i == 0) {
                            pw.print(" plot ");
                        } else {
                            pw.print("      ");
                        }

                        pw.print(field.line(i).toString());

                        if (i == field.numLine() - 1) {
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
     * @param function (String)
     * @param plotPart (String) The content of the "using" part.
     * @param appearance ({@link GnuplotAppearance})
     * @param title (String) Name to display in key. If you want to set "notitle", set this as "".
     */
    public void addLine(String function, GnuplotLineAppearance appearance, String title) {
        drawStarted = true;
        // add line to current page
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLine(new GnuplotLine(function, appearance, title));
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
     * @param title (String) Name to display in key (if it is set on). If you want to set "notitle", set this as "".
     */
    public void addLine(String fileName, int columnX, int columnY, GnuplotLineAppearance appearance, String title) {
        drawStarted = true;
        // add line to current page
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLine(new GnuplotLine(fileName, columnX, columnY, appearance, title));
    }

    /**
     * @param posX (double)
     * @param appearance ({@link GnuplotAppearance})
     */
    public void addVerticalLine(double posX, GnuplotLineAppearance appearance) {
        drawStarted = true;
        // add line to current page
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addArrow(new GnuplotArrow(posX, appearance));
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
    /**
     * @param label (String)
     * @param coordinateX (String) The coordinate system used to specify posX, from "first", "second", "graph", "screen", or "character".
     * @param posX (double)
     * @param coordinateY (String) The coordinate system used to specify posY, from "first", "second", "graph", "screen", or "character".
     * @param posY (double)
     */
    public void addLabel(String label, String coordinateX, double posX, String coordinateY, double posY) {
        drawStarted = true;
        // add label to current field
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLabel(new GnuplotLabel(label, coordinateX, posX, coordinateY, posY));
    }

    /**
     * @param label (String)
     * @param coordinate (String) The coordinate system used to specify position, from "first", "second", "graph", "screen", or "character".
     * @param posX (double)
     * @param posY (double)
     * @param color (GnuplotColorName)
     */
    public void addLabel(String label, String coordinate, double posX, double posY, GnuplotColorName color) {
        drawStarted = true;
        // add label to current field
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLabel(new GnuplotLabel(label, coordinate, posX, posY, color));
    }
    /**
     * @param label (String)
     * @param coordinateX (String) The coordinate system used to specify posX, from "first", "second", "graph", "screen", or "character".
     * @param posX (double)
     * @param coordinateY (String) The coordinate system used to specify posY, from "first", "second", "graph", "screen", or "character".
     * @param posY (double)
     * @param color (GnuplotColorName)
     */
    public void addLabel(String label, String coordinateX, double posX, String coordinateY, double posY, GnuplotColorName color) {
        drawStarted = true;
        // add label to current field
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).addLabel(new GnuplotLabel(label, coordinateX, posX, coordinateY, posY, color));
    }


    /**
     * Sets xrange to the current field.
     * @param xmin
     * @param xmax
     */
    public void setXrange(double xmin, double xmax) {
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).setXrange(xmin, xmax);
    }

    /**
     * Sets a limit to the range of autoscaling for the x-axis of the current field.
     * @param xminLimit
     * @param xmaxLimit
     */
    public void setXrangeLimit(double xminLimit, double xmaxLimit) {
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).setXrangeLimit(xminLimit, xmaxLimit);
    }

    /**
     * Sets yrange to the current field.
     * @param ymin
     * @param ymax
     */
    public void setYrange(double ymin, double ymax) {
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).setYrange(ymin, ymax);
    }

    /**
     * Sets a limit to the range of autoscaling for the y-axis of the current field.
     * @param yminLimit
     * @param ymaxLimit
     */
    public void setYrangeLimit(double yminLimit, double ymaxLimit) {
        GnuplotPage page = pages.get(pages.size() - 1);
        page.field(page.numField() - 1).setYrangeLimit(yminLimit, ymaxLimit);
    }


    public void nextField() {
        drawStarted = true;
        // add field to current page
        pages.get(pages.size() - 1).nextField();
    }

    /**
     * Switches to the next page.
     * This can only be done for "pdf" format. Otherwise, an IllegalArgumentException will be thrown.
     */
    public void nextPage() {
        if (!isPdf) throw new IllegalArgumentException("New page cannot be added for output file formats other than pdf.");
        drawStarted = true;
        pages.add(new GnuplotPage());
    }

    /**
     * Sets the format of output graph file, its name, and size.
     * This must be set before lines, fields, or pages are added.
     * If this function is called after drawing has started, an IllegalStateException will be thrown.
     * @param format (String) Choose from "pdf", "png", and "eps". Otherwise, IlleganArgumentException will be thrown.
     * @param output (String) Name of output file
     * @param sizeX (double) Width of output file
     * @param sizeY (double) Height of output file
     * @param cm (boolean) true if the size is given in cm ; if false, pixels (must be true for pdf and eps)
     */
    public void setOutput(String format, String output, double sizeX, double sizeY, boolean cm) {
        if (drawStarted) throw new IllegalStateException("Output cannot be changed after drawing has started.");

        switch(format) {
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
            throw new IllegalArgumentException("Unrecognizable file format " + format + ".");
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
     * @param fontName (String) Font name to use for all letters in the graph file
     * @param title (int) Font size for the title
     * @param label (int) Font size for xlabel and ylabel
     * @param tics (int) Font size for xtics and ytics
     * @param key (int) Font size for the key
     * @param fileDefault (int) Font size for anything else (ex. added labels)
     */
    public void setFont(String fontName, int title, int label, int tics, int key, int fileDefault) {
        this.fontName = fontName;
        this.fontSizeTitle = title;
        this.fontSizeLabel = label;
        this.fontSizeTics = tics;
        this.fontSizeKey = key;
        this.fontSizeDefault = fileDefault;
    }

    public void setMarginH(int lmargin, int rmargin) {
        this.lmargin = lmargin;
        this.rmargin = rmargin;
        marginHFlag = true;
    }

    public void setMarginV(int tmargin, int bmargin) {
        this.tmargin = tmargin;
        this.bmargin = bmargin;
        marginVFlag = true;
    }

    /**
     * Sets the legend of the graph. To turn it off, use {@link #unsetKey()}.
     * @param box (boolean) Whether to surround the key with box
     * @param position (String) Position and additional options (if unneeded, set this "")
     */
    public void setKey(boolean box, String position) {
        if (!box){
            this.keySettings = "nobox "+ position;
        } else {
            this.keySettings = "box " + position;
        }
    }

    /**
     * Unsets the legend of the graph. To turn it on, use {@link #setKey()}.
     */
    public void unsetKey() {
        this.keySettings = "";
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

    public void setCommonXrange(double xmin, double xmax) {
        if (xmax <= xmin) throw new IllegalArgumentException("Input xmin xmax " + xmin + " " + xmax + " are invalid");
        this.xmin = xmin;
        this.xmax = xmax;
        xrangeFlag = true;
    }

    /**
     * Sets a limit to the range of autoscaling for the x-axis.
     * If {@link #setCommonXrange(double, double)} is used, this will be ignored.
     * @param xminLimit
     * @param xmaxLimit
     */
    public void setCommonXrangeLimit(double xminLimit, double xmaxLimit) {
        if (xmaxLimit <= xminLimit)
            throw new IllegalArgumentException("Input xminLimit xmaxLimit " + xminLimit + " " + xmaxLimit + " are invalid");
        this.xminLimit = xminLimit;
        this.xmaxLimit = xmaxLimit;
        xrangeLimitFlag = true;
    }

    public void setCommonYrange(double ymin, double ymax) {
        if (ymax <= ymin) throw new IllegalArgumentException("Input ymin ymax " + ymin + " " + ymax + " are invalid");
        this.ymin = ymin;
        this.ymax = ymax;
        yrangeFlag = true;
    }

    /**
     * Sets a limit to the range of autoscaling for the y-axis.
     * If {@link #setCommonYrange(double, double)} is used, this will be ignored.
     * @param yminLimit
     * @param ymaxLimit
     */
    public void setCommonYrangeLimit(double yminLimit, double ymaxLimit) {
        if (ymaxLimit <= yminLimit)
            throw new IllegalArgumentException("Input yminLimit ymaxLimit " + yminLimit + " " + ymaxLimit + " are invalid");
        this.yminLimit = yminLimit;
        this.ymaxLimit = ymaxLimit;
        yrangeLimitFlag = true;
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
