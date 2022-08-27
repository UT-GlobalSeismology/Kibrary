package io.github.kensuke1984.kibrary.selection;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;

/**
 * Information of values that represent the difference between observed and synthetic waveforms,
 * such as normalized variance, amplitude ratio, and cross correlation.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/8/27 renamed from selection.DataSelectionInformation to selection.DataFeature
 */
public class DataFeature {

    private TimewindowData timewindow;

    private double variance;

    private double cc;

    private double maxRatio;

    private double minRatio;

    private double absRatio;

    private double SNratio;

    private boolean selected;


    public DataFeature(TimewindowData timewindow, double variance, double cc,
            double maxRatio, double minRatio, double absRatio, double SNratio, boolean selected) {
        this.timewindow = timewindow;
        this.variance = variance;
        this.cc = cc;
        this.maxRatio = maxRatio;
        this.minRatio = minRatio;
        this.absRatio = absRatio;
        this.SNratio = SNratio;
        this.selected = selected;
    }

    public TimewindowData getTimewindow() {
        return timewindow;
    }

    public double getVariance() {
        return variance;
    }

    public double getCC() {
        return cc;
    }

    public double getMaxRatio() {
        return maxRatio;
    }

    public double getMinRatio() {
        return minRatio;
    }

    public double getAbsRatio() {
        return absRatio;
    }

    public double getSNratio() {
        return SNratio;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public String toString() {
        return timewindow.toString() + " " + maxRatio + " " + minRatio + " " + absRatio + " " +
                variance + " " + cc + " " + SNratio + " " + selected;
    }
}
