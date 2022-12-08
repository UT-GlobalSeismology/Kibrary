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

    public static int PRECISION = 3;

    private TimewindowData timewindow;

    private double variance;

    private double correlation;

    private double posSideRatio;

    private double negSideRatio;

    private double absRatio;

    private double snRatio;

    private boolean selected;


    public DataFeature(TimewindowData timewindow, double variance, double correlation,
            double posSideRatio, double negSideRatio, double absRatio, double snRatio, boolean selected) {
        if (variance < 0) throw new IllegalArgumentException("variance must be positive: " + timewindow);
        if (absRatio < 0) throw new IllegalArgumentException("absRatio must be positive: " + timewindow);
        if (snRatio < 0) throw new IllegalArgumentException("snRatio must be positive: " + timewindow);
        this.timewindow = timewindow;
        this.variance = variance;
        this.correlation = correlation;
        this.posSideRatio = posSideRatio;
        this.negSideRatio = negSideRatio;
        this.absRatio = absRatio;
        this.snRatio = snRatio;
        this.selected = selected;
    }

    public static DataFeature create(TimewindowData timewindow, RealVector obsU, RealVector synU, double snRatio, boolean selected) {
        if (obsU.getDimension() < synU.getDimension())
            synU = synU.getSubVector(0, obsU.getDimension() - 1);
        else if (synU.getDimension() < obsU.getDimension())
            obsU = obsU.getSubVector(0, synU.getDimension() - 1);

        // variance
        RealVector resid = obsU.subtract(synU);
        double var = resid.dotProduct(resid) / obsU.dotProduct(obsU);
        // "Math.abs()" is to exclude -Infinity.
        double variance = Math.abs(Precision.round(var, PRECISION));

        // ratio
        double posSideRatio = Precision.round(synU.getMaxValue() / obsU.getMaxValue(), PRECISION);
        double negSideRatio = Precision.round(synU.getMinValue() / obsU.getMinValue(), PRECISION);
        // "Math.abs()" is to exclude -Infinity.
        double absRatio = Math.abs(Precision.round(synU.getLInfNorm() / obsU.getLInfNorm(), PRECISION));

        // correlation
        double cor = obsU.dotProduct(synU) / (synU.getNorm() * obsU.getNorm());
        double correlation = Precision.round(cor, PRECISION);

        return new DataFeature(timewindow, variance, correlation, posSideRatio, negSideRatio, absRatio, snRatio, selected);
    }

    public TimewindowData getTimewindow() {
        return timewindow;
    }

    /**
     * @return(double) variance of residual waveform. MAY BE INFINITY or NaN!!
     */
    public double getVariance() {
        return variance;
    }

    /**
     * @return(double) correlation of observed and synthetic waveforms. MAY BE INFINITY or NaN!!
     */
    public double getCorrelation() {
        return correlation;
    }

    /**
     * @return (double) syn/obs ratio of maximum value in waveforms. MAY BE NEGATIVE, INFINITY, or NaN!!
     */
    public double getPosSideRatio() {
        return posSideRatio;
    }

    /**
     * @return (double) syn/obs ratio of minimum value in waveforms. MAY BE NEGATIVE, INFINITY, or NaN!!
     */
    public double getNegSideRatio() {
        return negSideRatio;
    }

    /**
     * @return (double) syn/obs ratio of maximum absolute value in waveforms. MAY BE INFINITY or NaN!!
     */
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
        return timewindow.toString() + " " + posSideRatio + " " + negSideRatio + " " + absRatio + " " +
                variance + " " + correlation + " " + snRatio + " " + selected;
    }
}
