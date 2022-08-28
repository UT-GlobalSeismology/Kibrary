package io.github.kensuke1984.kibrary.selection;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;

/**
 * Information of values that represent the difference between observed and synthetic waveforms,
 * such as normalized variance, amplitude ratio, and correlation coefficient.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/8/27 renamed from selection.DataSelectionInformation to selection.DataFeature
 */
public class DataFeature {

    private TimewindowData timewindow;

    private double variance;

    private double correlation;

    private double maxRatio;

    private double minRatio;

    private double absRatio;

    private double snRatio;

    private boolean selected;


    public DataFeature(TimewindowData timewindow, double variance, double correlation,
            double maxRatio, double minRatio, double absRatio, double snRatio, boolean selected) {
        this.timewindow = timewindow;
        this.variance = variance;
        this.correlation = correlation;
        this.maxRatio = maxRatio;
        this.minRatio = minRatio;
        this.absRatio = absRatio;
        this.snRatio = snRatio;
        this.selected = selected;
    }

    public TimewindowData getTimewindow() {
        return timewindow;
    }

    public double getVariance() {
        return variance;
    }

    public double getCorrelation() {
        return correlation;
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

    public double getSNRatio() {
        return snRatio;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public String toString() {
        return timewindow.toString() + " " + maxRatio + " " + minRatio + " " + absRatio + " " +
                variance + " " + correlation + " " + snRatio + " " + selected;
    }
}
