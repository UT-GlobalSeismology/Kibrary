package io.github.kensuke1984.kibrary.waveform;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;

/**
 * Create noise for given waveform.
 *
 * @author Kensuke Konishi
 * @since version 0.1.0 ランダム波形作成を並列化
 * TODO adopt hite Gaussian noise
 */
public final class RandomNoiseMaker {
    private RandomNoiseMaker() {}

    /**
     * @param amplitude  of noize
     * @param samplingHz [Hz] of noize
     * @param tlen       [s] time length of noize
     * @param np         the number of step in frequency domain. (must be a power of 2)
     * @return Trace of time and noize
     * @deprecated
     */
    public static Trace create(double amplitude, double samplingHz, double tlen, int np) {
        Complex[] spectorU = createRandomComplex(amplitude, samplingHz, tlen, np);
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] timeU = fft.transform(spectorU, TransformType.INVERSE);
        int npts = 2 * np * findLsmooth(samplingHz, tlen, np);
        double[] noise = new double[npts];
        double[] time = new double[npts];
        for (int i = 0; i < npts; i++)
            noise[i] = timeU[i].getReal();
        Arrays.setAll(time, j -> j / samplingHz);
        return new Trace(time, noise);
    }
    /**
     * Create the trace of noise for given waveform.
     *
     * @param snRatio
     * @param noiseAmp
     * @param waveform {@link RealVector}
     * @param startTime [s] start time ofwaveform
     * @param maxPeriod [s] maximum period of the applied filter if none.
     * @param minPeriod [s] minimum period of the applied filter if none.
     * @param sacSamplingHz [Hz] Sampling Hz of sac.
     * @param finalSamplingHz [Hz] Sampling Hz of waveform.
     * @param noiseType noise type {gaussian, white}
     * @return Trace of time and noize
     * @author rei
     */
    public static Trace create(double snRatio, double noiseAmp, RealVector waveform, double startTime,
            double maxPeriod, double minPeriod, double sacSamplingHz, double finalSamplingHz, String noiseType) {
        // make time array and ready to noise array
        int npts = waveform.getDimension();
        double[] noise = new double[npts];
        double[] time = new double[npts];
        Arrays.setAll(time, j -> startTime + j / finalSamplingHz);
        // generate noise
        switch(noiseType) {
        case "white":
            noise = createWhiteNoise(snRatio, noiseAmp, waveform, startTime, maxPeriod, minPeriod, sacSamplingHz, finalSamplingHz);
            break;
        case "gaussian":
            noise = createGaussianNoise(snRatio, noiseAmp, waveform);
            break;
        default:
            throw new IllegalArgumentException("noiseType must be set to white or gaussian");
        }
        // return noise trace
        return new Trace(time, noise);
    }

    /**
     * Create white noise.
     * <p> Note that it is not necessarily Gaussian noise.
     *
     * @param snRatio
     * @param noiseAmp
     * @param waveform {@link RealVector}
     * @return Array of gaussian noise vector
     * @author rei
     */
    private static double[] createWhiteNoise(double snRatio, double noiseAmp, RealVector waveform,
            double startTime, double maxPeriod, double minPeriod, double sacSamplingHz, double finalSamplingHz) {
        // set up parameters
        int npts = waveform.getDimension();
        double endTime = startTime + npts / finalSamplingHz;
        double[] noise = new double[npts];
        double tlen = findTlen(endTime);
        int np = findNp(tlen, minPeriod);
        // create uniform random numbers in frequency domain
        Complex[] spectorU = createRandomComplex(1.0, sacSamplingHz, tlen, np);
        // transform to time domain
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] timeU = fft.transform(spectorU, TransformType.INVERSE);
        // resample and filtering
        noise = filterAndResample(timeU, minPeriod, maxPeriod, sacSamplingHz, finalSamplingHz, startTime, npts);
        // adjust noise to given S/N ratio
        RealVector noiseV = new ArrayRealVector(noise, false);
        double coeff = (Double.isNaN(snRatio)) ? adjustToAmplitude(noiseAmp, noiseV) : adjustToSnRatio(snRatio, waveform, noiseV);
        noise = noiseV.mapMultiply(coeff).toArray();
        return noise;
    }

    /**
     * Create Gaussian noise.
     * <p> Note that it is not necessarily white noise.
     *
     * @param snRatio
     * @param noiseAmp
     * @param waveform {@link RealVector}
     * @return Array of gaussian noise vector
     * @author rei
     */
    private static double[] createGaussianNoise(double snRatio, double noiseAmp, RealVector waveform) {
        int npts = waveform.getDimension();
        double[] noise = new double[npts];
        // initialize random generator with a new seed for each set of waveforms
        Random random = new Random();
        for (int i = 0; i < npts; i++) {
            noise[i] = random.nextGaussian();
        }
        // adjust noise to given S/N ratio
        RealVector noiseV = new ArrayRealVector(noise, false);
        double coeff = (Double.isNaN(snRatio)) ? adjustToAmplitude(noiseAmp, noiseV) : adjustToSnRatio(snRatio, waveform, noiseV);
        noise = noiseV.mapMultiply(coeff).toArray();
        return noise;
    }

    /**
     * Returns the coefficient of noise, so that S/N ratio matches the given value.
     * <p> Note that S/N ratio is defined as the ratio between waveform+noise and noise.
     *
     * @param snRatio
     * @param waveform {@link RealVector}
     * @param noiseV {@link RealVector}
     * @return coeff of noise
     * @author rei
     */
    private static double adjustToSnRatio(double snRatio, RealVector waveform, RealVector noiseV) {
        double coeff;
        double w2 = waveform.getNorm() * waveform.getNorm();
        double n2 = noiseV.getNorm() * noiseV.getNorm();
        double wn = waveform.dotProduct(noiseV);
        double a = snRatio * snRatio - 1.0;
        if(snRatio == 1.0)
            coeff = - w2 / 2 / wn;
        else if (snRatio > 1.0)
            coeff = (wn + Math.sqrt(wn * wn + a * w2 * n2)) / a / n2;
        else
            throw new IllegalArgumentException("S/N ratio must be above 1");
        return coeff;
    }

    /**
     * Returns the coefficient of noise, so that amplitude matches the given value.
     *
     * @param noiseAmp
     * @param noiseV {@link RealVector}
     * @return coeff of noise
     * @author rei
     */
    private static double adjustToAmplitude(double noiseAmp, RealVector noiseV) {
        return noiseAmp / noiseV.getLInfNorm();
    }

    private static double findTlen(double endTime) {
        double tlen;
        int index = (int) Math.ceil(Math.log(20 * endTime) / Math.log(2.0));
        if (index <= 15)
            tlen = 3276.8;
        else
            tlen = 0.1 * Math.pow(2.0, index);
        return tlen;
    }

    private static int findNp(double tlen, double minPeriod) {
        int np;
        int index;
        if (minPeriod != 0)
            index = (int) Math.ceil(Math.log(2.0 * tlen / minPeriod) / Math.log(2.0));
        else
            index = (int) Math.ceil(Math.log(2.0 * tlen / 20) / Math.log(2.0));
        if (index <= 10)
            np = 1024;
        else
            np = (int) Math.pow(2.0, index);
        return np;
    }

    /**
     * Create uniform random numbers in frequency domain.
     * The spectrum is the trigonometric function which amplitude is contant and angle is uniformly random.
     */
    private static Complex[] createRandomComplex(double amplitude, double samplingHz, double tlen, int np) {
        int nnp = np * findLsmooth(samplingHz, tlen, np);
        Complex[] spectorU = new Complex[nnp * 2];
        // pack to temporary Complex array
        for (int i = 0; i <= np; i++) {
            double argument = 2 * Math.PI * Math.random();
            spectorU[i] = new Complex(amplitude * Math.cos(argument), amplitude * Math.sin(argument));
        }
        // set blank due to lsmooth
        Arrays.fill(spectorU, np + 1, nnp + 1, Complex.ZERO);
        // set values for imaginary frequency to transform time domain as Real
        for (int i = 0; i < nnp - 1; i++) {
            int ii = nnp + 1 + i;
            int jj = nnp - 1 - i;
            spectorU[ii] = spectorU[jj].conjugate();
        }
        return spectorU;
    }

    private static double[] filterAndResample(Complex[] timeU, double minPeriod, double maxPeriod,
            double sacSamplingHz, double finalSamplingHz, double startTime, int npts) {
        double[] raw = new double[timeU.length];
        for (int i = 0; i < timeU.length; i++)
            raw[i] = timeU[i].getReal();
        // filtering
        if (minPeriod != 0 && maxPeriod != 0) {
            ButterworthFilter bpf = new BandPassFilter(2 * Math.PI / minPeriod / sacSamplingHz, 2 * Math.PI / maxPeriod / sacSamplingHz, 4);
            raw = bpf.applyFilter(raw);
        }
        else
            System.err.println("Skkiping applying filter");
        // resampling
        double[] resampled = new double[npts];
        int startT = (int) (startTime * sacSamplingHz);
        int step = (int) (sacSamplingHz / finalSamplingHz);
        for (int j = 0; j < npts; j++)
            resampled[j] = raw[j * step + startT];
        return resampled;
    }

    /**
     * Find the smoothing value to transform from frequency domain to time domain at sacSamplingHz.
     */
    private static int findLsmooth(double samplingHz, double tlen, int np) {
        int lsmooth = (int) (0.5 * tlen * samplingHz / np);
        int i = Integer.highestOneBit(lsmooth);
        if (i < lsmooth) i *= 2;
        return i;
    }
}
