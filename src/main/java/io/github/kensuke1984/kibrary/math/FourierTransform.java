package io.github.kensuke1984.kibrary.math;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class FourierTransform {

    public static final FastFourierTransformer FFT = new FastFourierTransformer(DftNormalization.STANDARD);

    private static final double TAPER_LENGTH_PERCENT = 5.0;

    /**
     * Convert the data in frequency domain to time domain using the inverse fast Fourier transform (FFT).
     * Artificial damping and amplitude are not corrected.
     *
     * @param uFreq (Complex[]) Waveform in frequency domain (non-negative frequency part). Length must be np+1.
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @param npts (int) Number of data points in time domain. Must be a power of 2.
     * @return (Complex[]) Waveform in time domain.
     */
    public static Complex[] convertToTimeDomain(Complex[] uFreq, int np, int npts) {
        if (uFreq.length != np + 1) throw new IllegalArgumentException("Length of waveform in frequency domain must be np+1=" + (np + 1));
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");
        int nnp = npts / 2;
        if (np > nnp) System.err.println("!CAUTION: np=" + np + " is larger than npts/2=" + nnp + ", using only points up to " + nnp + ".");

        // pack to temporary Complex array
        Complex[] data = new Complex[npts];
        System.arraycopy(uFreq, 0, data, 0, np + 1);
        // set blank due to difference in np and npts
        Arrays.fill(data, np + 1, nnp + 1, Complex.ZERO);
        // set values for imaginary frequency: F[i] = F[N-i]
        for (int i = 0; i < nnp - 1; i++)
            data[nnp + i + 1] = data[nnp - i - 1].conjugate();
        // fast fourier transformation
        Complex[] uTime = FFT.transform(data, TransformType.INVERSE);

        return uTime;
    }

    /**
     * Convert the data in frequency domain to time domain, fixing artificial damping. Amplitude is not corrected.
     * <p>
     * First, the inverse fast Fourier transform (FFT) is conducted.
     * <p>
     * After the inverse FFT, exp(&omega;<sub>I</sub>t) is multiplied
     * to account for the artificial damping introduced in DSM as &omega; = &omega;<sub>R</sub> - i&omega;<sub>I</sub>
     * (see section 5.1 of Geller & Ohminato 1994).
     * Here, t = tlen * i / nptsInTimeDomain = i / samplingHz.
     *
     * @param uFreq (Complex[]) Waveform in frequency domain (non-negative frequency part). Length must be np+1.
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @param npts (int) Number of data points in time domain. Must be a power of 2.
     * @param samplingHz (double) Sampling frequency [Hz].
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @return (Complex[]) Waveform in time domain.
     */
    public static Complex[] convertToTimeDomain(Complex[] uFreq, int np, int npts, double samplingHz, double omegaI) {
        Complex[] uTime = convertToTimeDomain(uFreq, np, npts);

        //~apply growing exponential
        double constant = omegaI / samplingHz;
        for (int i = 0; i < npts; i++)
            uTime[i] = uTime[i].multiply(Math.exp(constant * i));

        return uTime;
    }

    /**
     * Convert the data in frequency domain to time domain, fixing artificial damping and amplitude from DSM.
     * <p>
     * First, the inverse fast Fourier transform (FFT) is conducted.
     * <p>
     * After the inverse FFT, exp(&omega;<sub>I</sub>t) is multiplied
     * to account for the artificial damping introduced in DSM as &omega; = &omega;<sub>R</sub> - i&omega;<sub>I</sub>
     * (see section 5.1 of Geller & Ohminato 1994).
     * Here, t = tlen * i / nptsInTimeDomain = i / samplingHz.
     * <p>
     * Also, the amplitude of time series is corrected.
     * Here, the following is done:
     * <ul>
     * <li> multiply by sampling frequency [Hz] so that the FFT matches with the Fourier transform used in DSM.
     * <li> multiply by 1000 to convert from [km] to [m].
     * </ul>
     *
     * @param uFreq (Complex[]) Waveform in frequency domain (non-negative frequency part). Length must be np+1.
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @param npts (int) Number of data points in time domain. Must be a power of 2.
     * @param samplingHz (double) Sampling frequency [Hz].
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @return (Complex[]) Waveform in time domain.
     */
    public static Complex[] convertToTimeDomainWithFix(Complex[] uFreq, int np, int npts, double samplingHz, double omegaI) {
        Complex[] uTime = convertToTimeDomain(uFreq, np, npts, samplingHz, omegaI);

        //~correct amplitude
        double coef = 1000 * samplingHz;
        for (int i = 0; i < npts; i++)
            uTime[i] = uTime[i].multiply(coef);

        return uTime;
    }

    /**
     * Convert the data in time domain to frequency domain using the fast Fourier transform (FFT).
     * Artificial damping and amplitude are not corrected.
     * Input waveform is tapered before converting.
     *
     * @param uTime (double[]) Waveform in time domain.
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @return (Complex[]) Waveform in frequency domain (non-negative frequency part). Length is np+1.
     */
    public static Complex[] convertToFrequencyDomain(double[] uTime, int np) {
        int npts = uTime.length;
        int nnp = npts / 2;
        if (np > nnp) System.err.println("!CAUTION: np=" + np + " is larger than npts/2=" + nnp + ", using only points up to " + nnp + ".");

        // apply taper
        double[] realArray = taper(uTime, TAPER_LENGTH_PERCENT, false);

        // switch to Complex
        Complex[] complexArray = Arrays.stream(realArray).mapToObj(Complex::new).toArray(Complex[]::new);

        // FFT to frequency domain
        complexArray = FFT.transform(complexArray, TransformType.FORWARD);
        // extract non-negative frequency part
        Complex[] uFreq = Arrays.copyOfRange(complexArray, 0, np + 1);

        return uFreq;
    }

    /**
     * Convert the data in time domain to frequency domain, applying artificial damping. Amplitude is not modified.
     * Input waveform is tapered before converting. The fast Fourier transform (FFT) is used.
     *
     * @param uTime (double[]) Waveform in time domain.
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @param samplingHz (double) Sampling frequency [Hz].
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @return (Complex[]) Waveform in frequency domain (non-negative frequency part). Length is np+1.
     */
    public static Complex[] convertToFrequencyDomain(double[] uTime, int np, double samplingHz, double omegaI) {
        int npts = uTime.length;
        int nnp = npts / 2;
        if (np > nnp) System.err.println("!CAUTION: np=" + np + " is larger than npts/2=" + nnp + ", using only points up to " + nnp + ".");

        // remove growing exponential
        double[] realArray = new double[npts];
        double constant = omegaI / samplingHz;
        for (int i = 0; i < npts; i++)
            realArray[i] = uTime[i] / Math.exp(constant * i);

        return convertToFrequencyDomain(realArray, np);
    }

    /**
     * Taper both ends of the input waveform using sine taper.
     * @param waveArray (double[]) Input waveform in time domain.
     * @param taperLengthPercent (double) Ratio of length to taper at each end [%].
     * @param cosine (boolean) Whether to use cosine taper. Otherwise, sine.
     * @return (double[]) Tapered waveform.
     */
    public static double[] taper(double[] waveArray, double taperLengthPercent, boolean cosine) {
        int npts = waveArray.length;

        // create shape of taper
        int taperLength = (int) Math.round(npts * taperLengthPercent / 100);
        double dAngle = Math.PI / 2 / taperLength;
        double[] taper = cosine ?
                IntStream.range(0, taperLength + 1).mapToDouble(i -> i * dAngle).map(i -> Math.sin(i) * Math.sin(i)).toArray() :
                IntStream.range(0, taperLength + 1).mapToDouble(i -> i * dAngle).map(Math::sin).toArray();

        // apply taper
        double[] taperedArray = waveArray.clone();
        for (int i = 0; i < taperLength + 1; i++) {
            taperedArray[i] *= taper[i];
            taperedArray[(npts - 1) - i] *= taper[i];
        }
        return taperedArray;
    }


    double[] y;
    Complex[] Fy;
    double[] amp;
    double[] phase;
    double[] reFy;
    double[] imFy;
    double df_point;

    public static void main(String[] args) {
        int n = 1024;
        double[] y = new double[n];
//		for (int i = 0; i < n; i++)
//			y[i] = Math.sin(2 * Math.PI / 50. * i / 10.) * Math.sin(2 * Math.PI / 5 * i / 10.);
        for (int i = 0; i < n; i++)
            y[i] = Math.sin(2 * Math.PI / 50. * i) + 2 * Math.sin(2 * Math.PI / 20. * i);

        FourierTransform transform = new FourierTransform(y);

        Complex[] Fy = transform.getFy();

        double a[] = transform.getAOfOmega();
        double phase[] = transform.getPhaseOfOmega();

        double df = transform.getFreqIncrement(1.);

        System.out.println(2*Math.PI / 50.);
        System.out.println(2*Math.PI / 20.);
        for (int i = 0; i < n; i++)
            System.out.println(i + " " + i*df + " " + y[i] + " " + a[i] +  " " + phase[i] / 90.);
    }

    public FourierTransform(double[] y) {
        this.y = y.clone();
        amp = new double[y.length];
        phase = new double[y.length];
        reFy = new double[y.length];
        imFy = new double[y.length];

        int npowOf2 = Integer.highestOneBit(y.length) * 2;
        double[] ytaped = ApplyTaper(y);
        double[] ypadded = Arrays.copyOf(ytaped, npowOf2);

        Complex[] Fypadded = FFT.transform(ypadded, TransformType.FORWARD);

        df_point = 1. / npowOf2;

//		double[] RealFy = new double[npowOf2];
//		double[] ImagFy = new double[npowOf2];
//		for (int i = 0; i < npowOf2; i++) {
//			RealFy[i] = Fy[i].getReal();
//			ImagFy[i] = Fy[i].getImaginary();
//		}
//		RealFy = ApplySineTaper(RealFy);
//		ImagFy = ApplySineTaper(ImagFy);
//		Complex[] Fytapped = new Complex[npowOf2];
//		for (int i = 0; i < npowOf2; i++)
//			Fytapped[i] = new Complex(RealFy[i], ImagFy[i]);

        this.Fy = Arrays.copyOf(Fypadded, y.length);

        for (int i = 0; i < y.length; i++) {
            amp[i] = Fy[i].abs();
            reFy[i] = Fy[i].getReal();
            imFy[i] = Fy[i].getImaginary();
        }
    }

    public FourierTransform(double[] y, int reSamplingHz) {
        amp = new double[y.length * reSamplingHz];
        phase = new double[y.length * reSamplingHz];
        reFy = new double[y.length * reSamplingHz];
        imFy = new double[y.length * reSamplingHz];

        int npowOf2 = Integer.highestOneBit(y.length) * 2;
        double[] ytaped = ApplyTaper(y);
        double[] ypadded = Arrays.copyOf(ytaped, npowOf2);

        ypadded = resample(ypadded, reSamplingHz);
        npowOf2 *= reSamplingHz;

        Complex[] Fypadded = FFT.transform(ypadded, TransformType.FORWARD);

        df_point = 1. / npowOf2;

        this.Fy = Arrays.copyOf(Fypadded, y.length);

        for (int i = 0; i < y.length; i++) {
            amp[i] = Fy[i].abs();
            reFy[i] = Fy[i].getReal();
            imFy[i] = Fy[i].getImaginary();
        }
    }

    private static double[] resample(double[] y, int reSamplingHz) {
        return Arrays.copyOf(y, y.length * reSamplingHz);
    }

    private static double[] removeMean(double[] y) {
        double mean = new ArrayRealVector(y).dotProduct(new ArrayRealVector(y.length).mapAdd(1.)) / y.length;
        return new ArrayRealVector(y).mapAdd(-mean).toArray();
    }

    public double getFreqIncrement(double samplingHz) {
        return df_point * samplingHz;
    }

    private static final int SACSamplingHz = 20;

    private double[] ApplyTaper(double y[]) {
        double[] taped = y.clone();
//		int width = 4;
//		int width = (int) (y.length / 10.);
//		if (width < 4)
//			width = 4;
        int width = SACSamplingHz * 1;
        for (int i = 0; i < y.length; i++) {
            double f = 1;
            if (i < width)
                f = (double) i / width;
            else if (i > y.length - width - 1)
                f = (double) (y.length - i - 1) / width;
            taped[i] *= f ;
        }
        return taped;
    }

    public double[] getAOfOmega() {
        return amp;
    }

    public double[] getLogA() {
        return new ArrayRealVector(amp).map(Math::log).toArray();
    }

    public double[] getRealFy() {
        return reFy;
    }

    public double[] getImFy() {
        return imFy;
    }

    public double[] getPhaseOfOmega() {
        return phase;
    }

    public Complex[] getFy() {
        return Fy;
    }

}