package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.FastMath;

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
     * Number of data points in time domain.
     */
    private int nptsInTimeDomain;
    /**
     * Data in time domain [m/s]. u[i], where i=[0,nptsInTimedomain-1].
     */
    private Complex[] uTime;

    SPCElement(int np) {
        this.np = np;
        uFreq = new Complex[np + 1];
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
    public SPCElement copy() {
        SPCElement s = new SPCElement(np);
        s.nptsInTimeDomain = nptsInTimeDomain;
        System.arraycopy(uFreq, 0, s.uFreq, 0, uFreq.length);
        if (uTime != null) s.uTime = uTime.clone();
        return s;
    }

    /**
     * Add the spectrum values in the frequency domain of another {@link SPCElement}.
     * @param anotherElement ({@link SPCElement}) The instance to add to this instance.
     */
    public void addElement(SPCElement anotherElement) {
        if (np != anotherElement.getNp()) throw new IllegalStateException("np is not equal.");

        Complex[] another = anotherElement.getValueInFrequencyDomain();
        for (int i = 0; i < np + 1; i++)
            uFreq[i] = uFreq[i].add(another[i]);
    }

    /**
     * Apply ramped source time function.
     * <p>
     * To be conducted before {@link #toTimeDomain(int)}.
     * @param sourceTimeFunction ({@link SourceTimeFunction}) Source time function to be applied.
     */
    public void applySourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        uFreq = sourceTimeFunction.convolve(uFreq);
    }

    /**
     * Convert the data in frequency domain to time domain using FFT.
     * Note that amplitude corrections are not done here, thus the resulting time series still has the unit [km].
     * @param npts (int) Number of data points in time domain.
     */
    public void toTimeDomain(int npts) {
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");
        nptsInTimeDomain = npts;

        int nnp = nptsInTimeDomain / 2;

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // pack to temporary Complex array
        Complex[] data = new Complex[nptsInTimeDomain];
        System.arraycopy(uFreq, 0, data, 0, np + 1);

        // set blank due to lsmooth
        Arrays.fill(data, np + 1, nnp + 1, Complex.ZERO);

        // set values for imaginary frequency  F[i] = F[N-i]
        for (int i = 0; i < nnp - 1; i++)
            data[nnp + 1 + i] = data[nnp - 1 - i].conjugate();

        // fast fourier transformation
        uTime = fft.transform(data, TransformType.INVERSE);
    }

    /**
     * Multiply exp(&omega;<sub>I</sub>t) to account for the artificial damping
     * introduced in DSM as &omega; = &omega;<sub>R</sub> - i&omega;<sub>I</sub>.
     * See section 5.1 of Geller & Ohminato (1994).
     * <p>
     * t = tlen * i / nptsInTimeDomain = i / samplingHz.
     * <p>
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    public void applyGrowingExponential(double omegaI, double samplingHz) {
        double constant = omegaI / samplingHz;
        for (int i = 0; i < nptsInTimeDomain; i++)
            uTime[i] = uTime[i].multiply(FastMath.exp(constant * i));
    }

    /**
     * Correct the amplitude of time series.
     * Here, the following is done:
     * <ul>
     * <li> multiply by sampling frequency [Hz] so that DFT matches with the Fourier transform used in DSM.
     * <li> multiply by 1000 to convert from [km] to [m].
     * </ul>
     * Through this method, the unit is changed from [km] to [m/s].
     * <p>
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    public void amplitudeCorrection(double samplingHz) {
        double coef = 1000 * samplingHz;
        for (int i = 0; i < nptsInTimeDomain; i++)
            uTime[i] = uTime[i].multiply(coef);
    }

    /**
     * Differentiate the data (in frequency domain) by time.
     * <p>
     * -ufreq[i] * 2 i &pi; (ip / tlen).
     * <p>
     * To be conducted before {@link #toTimeDomain(int)}.
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
    public void mapMultiply(double factor) {
        for (int i = 0; i < np + 1; i++)
            uFreq[i] = uFreq[i].multiply(factor);
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
        return Arrays.stream(uTime).mapToDouble(Complex::getReal).toArray();
    }

}
