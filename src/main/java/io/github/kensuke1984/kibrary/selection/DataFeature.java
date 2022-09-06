package io.github.kensuke1984.kibrary.selection;

import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Precision;

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

    public static DataFeature create(TimewindowData timewindow, RealVector obsU, RealVector synU, double snRatio, boolean selected) {
        if (obsU.getDimension() < synU.getDimension())
            synU = synU.getSubVector(0, obsU.getDimension() - 1);
        else if (synU.getDimension() < obsU.getDimension())
            obsU = obsU.getSubVector(0, synU.getDimension() - 1);

        double synMax = synU.getMaxValue();
        double synMin = synU.getMinValue();
        double obsMax = obsU.getMaxValue();
        double obsMin = obsU.getMinValue();
        double obs2 = obsU.dotProduct(obsU);
        double syn2 = synU.dotProduct(synU);
        double cor = obsU.dotProduct(synU);
        cor /= Math.sqrt(obs2 * syn2);
        double var = obs2 + syn2 - 2 * obsU.dotProduct(synU);
        var /= obs2;

        double maxRatio = Precision.round(synMax / obsMax, 2);
        double minRatio = Precision.round(synMin / obsMin, 2);
        double absRatio = Precision.round((-synMin < synMax ? synMax : -synMin) / (-obsMin < obsMax ? obsMax : -obsMin), 2);
        double variance = Precision.round(var, 2);
        double correlation = Precision.round(cor, 2);

        return new DataFeature(timewindow, variance, correlation, maxRatio, minRatio, absRatio, snRatio, selected);
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

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return timewindow.toString() + " " + maxRatio + " " + minRatio + " " + absRatio + " " +
                variance + " " + correlation + " " + snRatio + " " + selected;
    }
}
