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
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;

import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAid;

/**
 * Source time function. <br>
 * <p>
 * You have to multiply<br>
 * Source time function: stf[0], .. stf[NP-1] <br>
 * on <br>
 * Waveform in frequency domain: U[1].. U[NP], respectively. See
 * {@link #convolve(Complex[])}
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class SourceTimeFunction {

    static final FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

    /**
     * Number of steps in frequency domain.
     */
    private final int np;
    /**
     * Number of data points in time domain.
     */
    private final int npts;
    /**
     * Sampling frequency [Hz].
     */
    private final double samplingHz;
    /**
     * Source time function in frequency domain. Length is np.
     */
    private Complex[] sourceTimeFunction;

    /**
     * @param np (int) Number of steps in frequency domain. Should not exceed npts/2; points above that will be ignored.
     * @param npts (int) Number of data points in time domain. Must be a power of 2.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    protected SourceTimeFunction(int np, int npts, double samplingHz) {
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");
        int nnp = npts / 2;
        if (np > nnp) System.err.println("!CAUTION: np=" + np + " is larger than npts/2=" + nnp + ", using only points up to " + nnp + ".");
        this.np = np;
        this.npts = npts;
        this.samplingHz = samplingHz;
    }

    /**
     * ASYMMETRIC Triangle source time function
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration [s] of the source
     * @return SourceTimeFunction
     *
     * @author lina
     */
    public static SourceTimeFunction asymmetricTriangleSourceTimeFunction(int np, double tlen, double samplingHz,
            double halfDuration1, double halfDuration2) {
        int npts = SPCFileAid.findNpts(tlen, samplingHz);
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, npts, samplingHz);
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        double deltaF = 1.0 / tlen;
        double h = 2. /(halfDuration1 + halfDuration2);
        for (int i = 0; i < np; i++) {
             double omega = (i + 1) * 2. * Math.PI * deltaF;
             sourceTimeFunction.sourceTimeFunction[i]
                     =new Complex(1.*h/omega/omega*(1./halfDuration1 + 1./halfDuration2 - Math.cos(omega*halfDuration1)/halfDuration1 - Math.cos(omega*halfDuration2)/halfDuration2),
                             -1.*h/omega/omega*(Math.sin(omega*halfDuration1)/halfDuration1 - Math.sin(omega*halfDuration2)/halfDuration2));
        }
        return sourceTimeFunction;
    }

    /**
     * Triangle source time function
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/&tau;<sup>2</sup> t + 1/&tau; (-&tau; &le; t &le; 0), -1/&tau;
     * <sup>2</sup> t + 1/&tau; (0 &le; t &le; &tau;), 0 (t &lt; -&tau;, &tau;
     * &lt; t) <br>
     * Source time function F(&omega;) = (2-2cos(2&pi;&omega;&tau;))
     * /(2&pi;&omega;&tau;)<sup>2</sup>
     *
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration [s] of the source
     * @return SourceTimeFunction
     */
    public static final SourceTimeFunction triangleSourceTimeFunction(int np, double tlen, double samplingHz, double halfDuration) {
        int npts = SPCFileAid.findNpts(tlen, samplingHz);
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, npts, samplingHz);
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np; i++) {
            double omegaTau = (i + 1) * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex((2 - 2 * Math.cos(omegaTau)) / omegaTau / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Boxcar source time function
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/(2&times;&tau;) (-&tau; &le; t &le; &tau;), 0 (t &lt; -&tau;,
     * &tau; &lt; t) <br>
     * Source time function F(&omega;) = sin(2&pi;&omega;&tau;)/(2&pi;&omega;&tau;);
     *
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration [s] of the source
     * @return SourceTimeFunction
     */
    public static final SourceTimeFunction boxcarSourceTimeFunction(int np, double tlen, double samplingHz, double halfDuration) {
        int npts = SPCFileAid.findNpts(tlen, samplingHz);
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, npts, samplingHz);
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np; i++) {
            double omegaTau = (i + 1) * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(Math.sin(omegaTau) / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Smoothed ramp source time function
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = (1-tanh<sup>2</sup>(2t/&tau;))/&tau; (-&tau; &le; t &le; &tau;), 0
     * (t &lt; -&tau;, &tau; &lt; t) <br>
     * Source time function F(&omega;) = (&pi;<sup>2</sup>
     * &omega;&tau;/2)/sinh(&pi;<sup>2</sup>&omega;&tau;/2)<br>
     *
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration [s] of the source
     * @return SourceTimeFunction
     */
    public static final SourceTimeFunction smoothedRampSourceTimeFunction(int np, double tlen, double samplingHz, double halfDuration) {
        int npts = SPCFileAid.findNpts(tlen, samplingHz);
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, npts, samplingHz);
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen; // omega
        final double constant = 2 * Math.PI * deltaF * halfDuration / 4 * Math.PI;
        for (int i = 0; i < np; i++) {
            double omegaTau = (i + 1) * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(omegaTau / Math.sinh(omegaTau));
        }
        return sourceTimeFunction;
    }

    /**
     * @param outPath Path for a file.
     * @param options for writing the file
     * @throws IOException if the source time function is not computed, then an error
     *                     occurs
     */
    public void writeSourceTimeFunction(Path outPath, OpenOption... options) throws IOException {
        Objects.requireNonNull(sourceTimeFunction, "Source time function is not computed yet.");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("#np npts samplingHz");
            pw.println(np + " " + npts + " " + samplingHz);
            for (int i = 0; i < sourceTimeFunction.length; i++)
                pw.println(sourceTimeFunction[i].getReal() + " " + sourceTimeFunction[i].getImaginary());
        }
    }

    public static SourceTimeFunction readSourceTimeFunction(Path sourcePath) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath);
        String[] parts = lines.get(1).split("\\s+");
        int np = Integer.parseInt(parts[0]);
        int npts = Integer.parseInt(parts[1]);
        double samplingHz = Double.parseDouble(parts[2]);
        Complex[] function = IntStream.range(0, np).mapToObj(i -> toComplex(lines.get(i + 2))).toArray(Complex[]::new);

        SourceTimeFunction stf = new SourceTimeFunction(np, npts, samplingHz);
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
     * @param data (Complex[]) Data to be convolved in <b>frequency</b> domain. Length must be {@link #np} + 1.
     * @param parallel (boolean) Whether to conduct parallel computations.
     * @return (Complex[]) Convolved data in <b>frequency</b> domain.
     */
    public final Complex[] convolve(Complex[] data, boolean parallel) {
        if (data.length != np + 1)
            throw new IllegalArgumentException("Input data length is invalid: " + data.length + " " + (np + 1));
        if (parallel) {
            return IntStream.range(0, np + 1).parallel()
                    .mapToObj(i -> i == 0 ? data[i] : data[i].multiply(sourceTimeFunction[i - 1])).toArray(Complex[]::new);
        } else {
            return IntStream.range(0, np + 1)
                    .mapToObj(i -> i == 0 ? data[i] : data[i].multiply(sourceTimeFunction[i - 1])).toArray(Complex[]::new);
        }
    }

    /**
     * x axis: time [s], y axis: amplitude
     * After considering that conjugate F[i] = F[N-i],
     *
     * @return trace of Source time function in time domain
     */
    public Trace getSourceTimeFunctionInTimeDomain() {
        Objects.requireNonNull(sourceTimeFunction, "Source time function is not set yet.");

        double[] time = new double[npts];
        Arrays.setAll(time, i -> i / samplingHz);

        Complex[] stf = new Complex[np + 1];
        stf[0] = Complex.ZERO;
        for (int i = 0; i < np; i++) stf[i+1] = sourceTimeFunction[i];
        double[] stfInTime = Arrays.stream(SPCFileAid.convertToTimeDomain(stf, np, npts, samplingHz, 0.0))
                .mapToDouble(Complex::getReal).map(d -> d * samplingHz).toArray();
        return new Trace(time, stfInTime);
    }

    public void setSourceTimeFunction(Complex[] function) {
        this.sourceTimeFunction = function;
    }

    public static void main(String[] args) {
        int np = 32768;
        double tlen = 3276.8;
        double samplingHz = 20.;
        double halfDuration = 3.;

        SourceTimeFunction boxcar = SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
        SourceTimeFunction triangle = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
        SourceTimeFunction triangleA = SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration, halfDuration);

        Trace trace1 = boxcar.getSourceTimeFunctionInTimeDomain();
        Trace trace2 = triangle.getSourceTimeFunctionInTimeDomain();
        Trace trace3 = triangleA.getSourceTimeFunctionInTimeDomain();
        for (int i = 0; i < trace1.getLength(); i++)
            if (trace1.getXAt(i) < 30)
                System.out.println(trace1.getXAt(i) + " " + trace1.getYAt(i) + " " + trace2.getYAt(i) + " " + trace3.getYAt(i));
    }

}
