package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAid;

/**
 * Source time function. <br>
 * <p>
 * You have to multiply<br>
 * Source time function: stf[0], .. stf[NP] <br>
 * on <br>
 * Waveform in frequency domain: U[0].. U[NP], respectively. See {@link #convolve(Complex[])}
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class SourceTimeFunction {

    /**
     * Number of steps in frequency domain (counting only positive frequency part).
     */
    private final int np;
    /**
     * Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     */
    private final double tlen;
    /**
     * Source time function in frequency domain. Length is np + 1.
     */
    private Complex[] sourceTimeFunction;

    /**
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     */
    SourceTimeFunction(int np, double tlen) {
        this.np = np;
        this.tlen = tlen;
    }

    /**
     * @param sourceTimeFunction (Complex[]) Source time function in frequency domain. Length is np + 1.
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     */
    SourceTimeFunction(Complex[] sourceTimeFunction, double tlen) {
        this.np = sourceTimeFunction.length - 1;
        this.tlen = tlen;
        this.sourceTimeFunction = sourceTimeFunction;
    }

    /**
     * ASYMMETRIC triangle source time function.
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration1 (double) Half duration [s] of the source.
     * @param halfDuration2 (double) Half duration [s] of the source.
     * @return ({@link} SourceTimeFunction) Created STF.
     *
     * @author lina
     */
    static SourceTimeFunction asymmetricTriangleSourceTimeFunction(int np, double tlen, double halfDuration1, double halfDuration2) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        double deltaF = 1.0 / tlen;
        double h = 2. /(halfDuration1 + halfDuration2);
        for (int i = 0; i < np + 1; i++) {
             double omega = i * 2. * Math.PI * deltaF;
             sourceTimeFunction.sourceTimeFunction[i]
                     = new Complex(1.*h/omega/omega*(1./halfDuration1 + 1./halfDuration2 - Math.cos(omega*halfDuration1)/halfDuration1 - Math.cos(omega*halfDuration2)/halfDuration2),
                             -1.*h/omega/omega*(Math.sin(omega*halfDuration1)/halfDuration1 - Math.sin(omega*halfDuration2)/halfDuration2));
        }
        return sourceTimeFunction;
    }

    /**
     * Triangle source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/&tau;<sup>2</sup> t + 1/&tau; (-&tau; &le; t &le; 0), -1/&tau;
     * <sup>2</sup> t + 1/&tau; (0 &le; t &le; &tau;), 0 (t &lt; -&tau;, &tau;
     * &lt; t) <br>
     * Source time function F(&omega;) = (2-2cos(2&pi;&omega;&tau;))
     * /(2&pi;&omega;&tau;)<sup>2</sup>
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link} SourceTimeFunction) Created STF.
     */
    static final SourceTimeFunction triangleSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np + 1; i++) {
            double omegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex((2 - 2 * Math.cos(omegaTau)) / omegaTau / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Boxcar source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/(2&times;&tau;) (-&tau; &le; t &le; &tau;), 0 (t &lt; -&tau;,
     * &tau; &lt; t) <br>
     * Source time function F(&omega;) = sin(2&pi;&omega;&tau;)/(2&pi;&omega;&tau;);
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link} SourceTimeFunction) Created STF.
     */
    public static final SourceTimeFunction boxcarSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np + 1; i++) {
            double omegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(Math.sin(omegaTau) / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Smoothed ramp source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = (1-tanh<sup>2</sup>(2t/&tau;))/&tau; (-&tau; &le; t &le; &tau;), 0
     * (t &lt; -&tau;, &tau; &lt; t) <br>
     * Source time function F(&omega;) = (&pi;<sup>2</sup>
     * &omega;&tau;/2)/sinh(&pi;<sup>2</sup>&omega;&tau;/2)<br>
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link} SourceTimeFunction) Created STF.
     */
    static final SourceTimeFunction smoothedRampSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration / 4 * Math.PI;
        for (int i = 0; i < np + 1; i++) {
            double omegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(omegaTau / Math.sinh(omegaTau));
        }
        return sourceTimeFunction;
    }

    /**
     * @param outPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException If the source time function is not computed, an error occurs.
     */
    void write(Path outPath, OpenOption... options) throws IOException {
        Objects.requireNonNull(sourceTimeFunction, "Source time function is not computed yet.");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("#np tlen");
            pw.println(np + " " + tlen);
            for (int i = 0; i < sourceTimeFunction.length; i++)
                pw.println(sourceTimeFunction[i].getReal() + " " + sourceTimeFunction[i].getImaginary());
        }
    }

    static SourceTimeFunction read(Path sourcePath) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath);
        String[] parts = lines.get(1).split("\\s+");
        int np = Integer.parseInt(parts[0]);
        double tlen = Double.parseDouble(parts[1]);
        Complex[] function = IntStream.range(0, np + 1).mapToObj(i -> toComplex(lines.get(i + 2))).toArray(Complex[]::new);

        SourceTimeFunction stf = new SourceTimeFunction(np, tlen);
        stf.sourceTimeFunction = function;
        return stf;
    }

    private static Complex toComplex(String line) {
        String[] parts = line.split("\\s+");
        double real = Double.parseDouble(parts[0]);
        double imag = Double.parseDouble(parts[1]);
        return new Complex(real, imag);
    }

    /**
     * Operates convolution for data in <b>frequency</b> domain.
     * @param data (Complex[]) Data to be convolved in <b>frequency</b> domain (non-negative frequency part).
     *                           Length must be {@link #np} + 1.
     * @param parallel (boolean) Whether to conduct parallel computations.
     * @return (Complex[]) Convolved data in <b>frequency</b> domain.
     */
    public final Complex[] convolve(Complex[] data, boolean parallel) {
        if (data.length != np + 1)
            throw new IllegalArgumentException("Input data length is invalid: " + data.length + " " + (np + 1));
        if (parallel) {
            return IntStream.range(0, np + 1).parallel()
                    .mapToObj(i -> data[i].multiply(sourceTimeFunction[i])).toArray(Complex[]::new);
        } else {
            return IntStream.range(0, np + 1)
                    .mapToObj(i -> data[i].multiply(sourceTimeFunction[i])).toArray(Complex[]::new);
        }
    }

    /**
     * x axis: time [s], y axis: amplitude
     * After considering that conjugate F[i] = F[N-i],
     *
     * @return trace of Source time function in time domain
     */
    Trace getSourceTimeFunctionInTimeDomain(double samplingHz) {
        Objects.requireNonNull(sourceTimeFunction, "Source time function is not set yet.");

        int npts = SPCFileAid.findNpts(tlen, samplingHz);
        double[] time = new double[npts];
        Arrays.setAll(time, i -> i / samplingHz);

        Complex[] stf = new Complex[np + 1];
        for (int i = 0; i < np + 1; i++) stf[i] = sourceTimeFunction[i];
        double[] stfInTime = Arrays.stream(FourierTransform.convertToTimeDomain(stf, np, npts))
                .mapToDouble(Complex::getReal).map(d -> d * samplingHz).toArray();
        return new Trace(time, stfInTime);
    }

    public int getNp() {
        return np;
    }

    public double getTlen() {
        return tlen;
    }

    public Complex[] getSourceTimeFunction() {
        return sourceTimeFunction;
    }

    void setSourceTimeFunction(Complex[] sourceTimeFunction) {
        this.sourceTimeFunction = sourceTimeFunction;
    }

}
