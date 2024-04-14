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
 * @author anselme add methods for interpolation for BP/FP catalog
 */
public class SPCElement {

    /**
     * Number of steps in frequency domain.
     */
    private final int np;
    /**
     * Number of data points in time domain.
     */
    private int nptsInTimeDomain;
    /**
     * Data in frequency domain. u[i], i=[0, np]. The length is np+1.
     */
    private Complex[] uFreq;
    /**
     * Data in time domain. u[i], i=[0,nptsInTimedomain-1].
     */
    private Complex[] uTime;

    SPCElement(int np) {
        this.np = np;
        uFreq = new Complex[np + 1];
    }

    /**
     * Set spectrum value for a single &omega; value.
     * @param ip (int) Step number in frequency domain.
     * @param spec ({@link Complex}) Spectrum value.
     */
    void setValue(int ip, Complex spec) {
        if (spec.isNaN()) throw new IllegalStateException("NaN in spectrum");
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
        if (np != anotherElement.getNp()) throw new RuntimeException("Error: Size of body is not equal!");

        Complex[] another = anotherElement.getValueInFrequencyDomain();
        for (int i = 0; i < np + 1; i++)
            uFreq[i] = uFreq[i].add(another[i]);
    }

    /**
     * Apply ramped source time function. To be conducted before {@link #toTimeDomain(int)}.
     * @param sourceTimeFunction to be applied
     */
    public void applySourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        uFreq = sourceTimeFunction.convolve(uFreq);
    }

    public void toTimeDomain(int lsmooth) {
        nptsInTimeDomain = getNPTS(lsmooth);

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
        data = fft.transform(data, TransformType.INVERSE);

        // put values in time domain into collections
        uTime = data;
    }

    private int getNPTS(int lsmooth) {
        int npts = np * lsmooth * 2;
        int pow2 = Integer.highestOneBit(npts);
        return pow2 < npts ? pow2 * 2 : npts;
    }

    /**
     * TLEN * (double) (i) / (double) nptsInTimeDomain;
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param omegai &omega;<sub>i</sub>
     * @param tlen   time length
     */
    public void applyGrowingExponential(double omegai, double tlen) {
        double constant = omegai * tlen / nptsInTimeDomain;
        for (int i = 0; i < nptsInTimeDomain; i++)
            uTime[i] = uTime[i].multiply(FastMath.exp(constant * i));
    }

    /**
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param tlen time length
     */
    public void amplitudeCorrection(double tlen) {
        double tmp = nptsInTimeDomain * 1e3 / tlen;
        for (int i = 0; i < nptsInTimeDomain; i++)
            uTime[i] = uTime[i].multiply(tmp);

    }

    /**
     * before toTime
     * <p>
     * -ufreq[i] * 2 i &pi; (ip /TLEN)
     * <p>
     *
     * @param tlen time length
     */
    void differentiate(double tlen) {
        double constant = 2 * Math.PI / tlen;
        for (int i = 1; i <= np; i++) {
            double c = constant * i;
            uFreq[i] = new Complex(uFreq[i].getImaginary() * c, -uFreq[i].getReal() * c);
        }
    }

    /**
     * Multiply self by double
     * @param factor
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
     * @return 周波数領域のデータ
     */
    public Complex[] getValueInFrequencyDomain() {
        return uFreq;
    }

    /**
     * @return the data in time_domain
     */
    public double[] getTimeseries() {
        return Arrays.stream(uTime).mapToDouble(Complex::getReal).toArray();
    }

}
