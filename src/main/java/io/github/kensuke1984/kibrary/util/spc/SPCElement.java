package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.source.SourceTimeFunction;

/**
 * Data for one element in one {@link SPCBody} in a {@link SPCFile}.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class SPCElement {

    /**
     * Number of steps in frequency domain.
     */
    private final int np;
    /**
     * Data in frequency domain [km]. u[i], where i=[0, np]. The length is np+1.
     */
    private Complex[] uFreq;
    /**
     * Data in time domain [m/s]. u[i], where i=[0,nptsInTimedomain-1].
     */
    private Complex[] uTime;

    SPCElement(int np) {
        this.np = np;
        uFreq = new Complex[np + 1];
        // Set value for ip=0, since it may not exist in SPC file.
        uFreq[0] = Complex.ZERO;
    }

    /**
     * Set spectrum value for a single &omega; value.
     * @param ip (int) Step number in frequency domain.
     * @param spec ({@link Complex}) Spectrum value [km].
     */
    void setValue(int ip, Complex spec) {
        if (spec.isNaN()) throw new IllegalStateException("NaN in spectrum.");
        uFreq[ip] = spec;
    }

    /**
     * @return DEEP copy of this
     */
    SPCElement copy() {
        SPCElement s = new SPCElement(np);
        System.arraycopy(uFreq, 0, s.uFreq, 0, uFreq.length);
        if (uTime != null) s.uTime = uTime.clone();
        return s;
    }

    /**
     * Add the spectrum values in the frequency domain of another {@link SPCElement}.
     * @param anotherElement ({@link SPCElement}) The instance to add to this instance.
     */
    void addElement(SPCElement anotherElement) {
        if (np != anotherElement.getNp()) throw new IllegalStateException("np is not equal.");

        Complex[] another = anotherElement.getValueInFrequencyDomain();
        for (int i = 0; i < np + 1; i++)
            uFreq[i] = uFreq[i].add(another[i]);
    }

    /**
     * Apply ramped source time function.
     * <p>
     * To be conducted before {@link #convertToTimeDomain(int, double, double)}.
     * @param sourceTimeFunction ({@link SourceTimeFunction}) Source time function to be applied.
     */
    public void applySourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        uFreq = sourceTimeFunction.convolve(uFreq);
    }

    /**
     * Differentiate the data (in frequency domain) by time.
     * <p>
     * -ufreq[i] * 2 i &pi; (ip / tlen).
     * <p>
     * To be conducted before {@link #convertToTimeDomain(int, double, double)}.
     * @param tlen (double) Time length [s].
     */
    void differentiate(double tlen) {
        double constant = 2 * Math.PI / tlen;
        for (int i = 1; i <= np; i++) {
            double c = constant * i;
            uFreq[i] = new Complex(uFreq[i].getImaginary() * c, -uFreq[i].getReal() * c);
        }
    }

    /**
     * Multiply the spectrum by a specified factor.
     * @param factor (double) Value to multiply.
     * @author anselme
     */
    void mapMultiply(double factor) {
        for (int i = 0; i < np + 1; i++)
            uFreq[i] = uFreq[i].multiply(factor);
    }

    /**
     * Convert the data in frequency domain to time domain.
     * <p>
     * This method does the following:
     * <ul>
     * <li> conduct the inverse fast Fourier transform (FFT).
     * <li> multiply exp(&omega;<sub>I</sub>t) to account for artificial damping.
     * <li> correct amplitude to match FFT with the Fourier transform used in DSM and convert from [km] to [m].
     * </ul>
     * @param npts (int) Number of data points in time domain.
     * @param samplingHz (double) Sampling frequency [Hz].
     * @param omegaI (double) &omega;<sub>i</sub>.
     */
    public void convertToTimeDomain(int npts, double samplingHz, double omegaI) {
        uTime = SPCFileAid.convertToTimeDomain(uFreq, np, npts, samplingHz, omegaI);
    }

    private int getNp() {
        return np;
    }

    /**
     * Get the displacement velociy spectrum.
     * @return (Complex[]) Data in frequency domain [km].
     */
    public Complex[] getValueInFrequencyDomain() {
        return uFreq;
    }

    /**
     * Get the displacement velociy time series.
     * @return (double[]) Data in time domain [m/s].
     */
    public double[] getTimeseries() {
        if (uTime == null) throw new IllegalStateException("Conversion to time series is not done yet!");
        return Arrays.stream(uTime).mapToDouble(Complex::getReal).toArray();
    }

}
