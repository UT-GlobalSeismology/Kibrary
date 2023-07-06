package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.ArithmeticUtils;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;

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
 * @version 0.0.7
 * @author Lina add asymmetric triangle source time function
 */
public class SourceTimeFunction {

    static final FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

    /**
     * The number of steps in frequency domain. It must be a power of 2.
     */
    protected final int np;

    /**
     * timeLength [s]. It must be a tenth of a power of 2
     */
    protected final double tlen;
    protected final double samplingHz;
    /**
     * The length is NP
     */
    protected Complex[] sourceTimeFunction;
    private int nptsInTimeDomain;

    /**
     * @param np         must be a power of 2
     * @param tlen       [s] must be a tenth of powers of 2
     * @param samplingHz 20 preferred (now must)
     */
    protected SourceTimeFunction(int np, double tlen, double samplingHz) {
        if (!checkValues(np, tlen, samplingHz))
            throw new IllegalArgumentException("np: " + np + ", tlen: " + tlen + ", samplingHz: " + samplingHz);
        this.np = np;
        this.tlen = tlen;
        this.samplingHz = samplingHz;
        nptsInTimeDomain = np * 2 * computeLsmooth(np, tlen, samplingHz);
    }

    /**
     * This main method is for debug.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        // setting options
        Options options = new Options();
        options.addOption(Option.builder("h").longOpt("halfDuration").hasArgs().argName("halfDuration")
                .desc("Half duration for source time functions (3)").build());
        options.addOption(Option.builder("u").longOpt("upperTime").hasArgs().argName("upper time")
                .desc("Upper time to show the source time functions (30)").build());
        options.addOption(Option.builder("o").longOpt("output").hasArgs().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
       // set parameter
        int np = 32768;
        double tlen = 3276.8;
        double samplingHz = 20.;
        double halfDuration = cmdLine.hasOption("h") ? Double.parseDouble(cmdLine.getOptionValue("h")) : 3.;
        double upperTime = cmdLine.hasOption("u") ? Double.parseDouble(cmdLine.getOptionValue("u")) : 30.;

        // set output
        Path outputPath;
        if (cmdLine.hasOption("o")) {
            outputPath = Paths.get(cmdLine.getOptionValue("o"));
        } else {
            outputPath = Paths.get("stf" + GadgetAid.getTemporaryString() +  ".txt");
        }

        // Inverse Fourier transform the source time functions in periodic domain
        SourceTimeFunction boxcar = SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
        SourceTimeFunction triangle = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
        SourceTimeFunction triangleA = SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration, halfDuration);
        SourceTimeFunction gaussian = SourceTimeFunction.gaussianSourceTimeFunction(np, tlen, samplingHz, halfDuration);

        Complex[] c1 = boxcar.getSourceTimeFunctionInFrequencyDomain();
        Complex[] c2 = triangle.getSourceTimeFunctionInFrequencyDomain();

        Trace trace1 = boxcar.getSourceTimeFunctionInTimeDomain();
        Trace trace2 = triangle.getSourceTimeFunctionInTimeDomain();
        Trace trace3 = triangleA.getSourceTimeFunctionInTimeDomain();
        Trace trace4 = gaussian.getSourceTimeFunctionInTimeDomain();

        // output
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < trace1.getLength(); i++)
                if (trace1.getXAt(i) <= upperTime)
                    pw.println(trace1.getXAt(i) + " " + trace1.getYAt(i) + " " + trace2.getYAt(i) +
                            " " + trace3.getYAt(i) + " " + trace4.getYAt(i));
        }
        System.err.println("Output the boxcar function, tirangle function, asymmetric triangle function, and "
                + "gaussian function in " + outputPath);
    }

    /**
     * Gaussian source time function. Note that this is NOT strictly gaussian function (see Borgeaud et al. 2016)
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = (18/&pi;&tau;<sup>2</sup>)<sup>1/2</sup> exp(-18/&tau;<sup>2</sup>(t - &tau;/2)<sup>2</sup>) sin(&pi;t/&tau;)<br>
     * Source time function is as follows;<br>
     * Re[F(&omega;)] = C<sub>1</sub>sin((&omega;&tau; + &pi;)/2) - C<sub>2</sub>sin((&omega;&tau; - &pi;)/2)<br>
     * Im[F(&omega;)] = C<sub>1</sub>cos((&omega;&tau; + &pi;)/2) - C<sub>2</sub>cos((&omega;&tau; - &pi;)/2)<br>
     * where C<sub>1</sub> = exp(-(&omega;&tau; + &pi;)<sup>2</sup>/72) and C<sub>2</sub> = exp(-(&omega;&tau; - &pi;)<sup>2</sup>/72)
     *
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration [s] of the source
     * @return SourceTimeFunction
     */
    //TODO
    public static final SourceTimeFunction gaussianSourceTimeFunction(int np, double tlen, double samplingHz, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np; i++) {
            // TODO check the correctness
            double omegaTau = (i + 1) * constant;
            double coef1 = 0.5 * Math.exp( -1.0 * Math.pow(omegaTau + Math.PI, 2.0) / 72.0);
            double coef2 = 0.5 * Math.exp( -1.0 * Math.pow(omegaTau - Math.PI, 2.0) / 72.0);
            sourceTimeFunction.sourceTimeFunction[i] =
                    new Complex(coef1 * Math.sin(0.5 * (omegaTau + Math.PI)) - coef2 * Math.sin(0.5 * (omegaTau - Math.PI)),
                            coef1 * Math.cos(0.5 * (omegaTau + Math.PI)) - coef2 * Math.cos(0.5 * (omegaTau - Math.PI)));
        }
        return sourceTimeFunction;
    }

    /**
     * ASYMMETRIC Triangle source time function
     * <p>
     * The width is determined by the half duration &tau;1 & &tau;2. <br>
     * f(t) = (h/&tau;1) t + h (-&tau;1 &le; t &le; 0), -(h/&tau;2)
     * t + h (0 &le; t &le; &tau;2), 0 (t &lt; -&tau;1, &tau;2
     * &lt; t) <br>
     * where h = 2 / (&tau;1+&tau;2) <br><br>
     * Source time function is as follows; <br>
     * Re[F(&omega;)] = h/&omega;<sup>2</sup> [1/&tau;1 + 1/&tau;2
     * - cos(&omega;&tau;1)/&tau;1 - cos(&omega;&tau;2)/&tau;2)] <br>
     * Im[F(&omega;)] = h/&omega;<sup>2</sup> [sin(&omega;&tau;1)/&tau;1 + sin(&omega;&tau;2)/&tau;2)]
     *
     * @param np           the number of steps in frequency domain
     * @param tlen         [s] time length
     * @param samplingHz   [Hz]
     * @param halfDuration1 [s] of the source
     * @param halfDuration2 [s] of the source
     * @return SourceTimeFunction
     * @author lina
     */
    public static SourceTimeFunction asymmetricTriangleSourceTimeFunction(int np, double tlen, double samplingHz,
                                                                double halfDuration1, double halfDuration2) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
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
    public static final SourceTimeFunction triangleSourceTimeFunction(int np, double tlen, double samplingHz,
                                                                      double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
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
     * @param np
     * @param tlen
     * @param samplingHz
     * @param halfDuration
     * @param amplitudeCorrection
     * @author anselme
     * @return
     */
    public static final SourceTimeFunction triangleSourceTimeFunction(int np, double tlen, double samplingHz,
            double halfDuration, double amplitudeCorrection) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        for (int i = 0; i < np; i++) {
            double omegaTau = (i + 1) * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex((2 - 2 * Math.cos(omegaTau)) / omegaTau / omegaTau)
                .multiply(amplitudeCorrection);
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
    public static final SourceTimeFunction boxcarSourceTimeFunction(int np, double tlen, double samplingHz,
                                                                    double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen; // omega
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
    public static final SourceTimeFunction smoothedRampSourceTimeFunction(int np, double tlen, double samplingHz,
                                                                          double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return sourceTimeFunction;
            }
        };
        sourceTimeFunction.sourceTimeFunction = new Complex[np];
        final double deltaF = 1.0 / tlen; // omega
        final double constant = 2 * Math.PI * deltaF * halfDuration / 4 * Math.PI;
        for (int i = 0; i < np; i++) {
            double omegaTau = (i + 1) * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(omegaTau / Math.sinh(omegaTau));
        }
        return sourceTimeFunction;
    }

    protected static boolean checkValues(int np, double tlen, double samplingHz) {
        boolean bool = true;
        if (samplingHz != 20) {
            System.err.println("Only samplingHz 20 is acceptable now.");
            bool = false;
        }
        if (!ArithmeticUtils.isPowerOfTwo(np)) {
            System.err.println("np must be a power of 2");
            bool = false;
        }
        long tlen10 = Math.round(10 * tlen);
        if (!ArithmeticUtils.isPowerOfTwo(tlen10)) {
            System.err.println("tlen must be a tenth of a power of 2");
            bool = false;
        }

        return bool;
    }

    private static Complex toComplex(String line) {
        String[] parts = line.split("\\s+");
        double real = Double.parseDouble(parts[0]);
        double imag = Double.parseDouble(parts[1]);
        return new Complex(real, imag);
    }

    public static SourceTimeFunction readSourceTimeFunction(Path sourcePath) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath);
        String[] parts = lines.get(1).split("\\s+");
        int np = Integer.parseInt(parts[0]);
        double tlen = Double.parseDouble(parts[1]);
        double samplingHz = Double.parseDouble(parts[2]);
        Complex[] function = IntStream.range(0, np).mapToObj(i -> toComplex(lines.get(i + 2))).toArray(Complex[]::new);

        SourceTimeFunction stf = new SourceTimeFunction(np, tlen, samplingHz) {
            @Override
            public Complex[] getSourceTimeFunctionInFrequencyDomain() {
                return function;
            }
        };
        stf.sourceTimeFunction = function;
        return stf;
    }

    private static int computeLsmooth(int np, double tlen, double samplingHz) {
        int lsmooth = (int) (0.5 * tlen * samplingHz / np);
        int i = Integer.highestOneBit(lsmooth);
        if (i < lsmooth) i *= 2;
        return lsmooth;
    }

    /**
     * Source time function is computed simply by division.
     *
     * @param obs        waveform of observed
     * @param syn        waveform of syn
     * @param np         steps of frequency [should be same as synthetics]
     * @param tlen       [s] length of waveform [should be same as synthetics]
     * @param samplingHz [Hz]
     * @return Source time function F(obs)/F(syn) in <b>frequency domain</b>
     */
    public static SourceTimeFunction computeSourceTimeFunction(int np, double tlen, double samplingHz, double[] obs,
                                                               double[] syn) {
        int inputLength = obs.length;
        if (inputLength != syn.length)
            throw new IllegalArgumentException("Input obs and syn waveform must have same lengths");
        int nptsInTimeDomain = computeLsmooth(np, tlen, samplingHz) * np * 2;
        double[] realObs = new double[nptsInTimeDomain];
        double[] realSyn = new double[nptsInTimeDomain];
        for (int i = 0; i < inputLength; i++) {
            realObs[i] = obs[i];
            realSyn[i] = syn[i];
        }
        Complex[] obsInFrequencyDomain = fft.transform(realObs, TransformType.FORWARD);
        Complex[] synInFrequencyDomain = fft.transform(realSyn, TransformType.FORWARD);
        Complex[] sourceTimeFunction = new Complex[np];
        for (int i = 0; i < np; i++)
            sourceTimeFunction[i] = obsInFrequencyDomain[i + 1].divide(synInFrequencyDomain[i + 1]);
        SourceTimeFunction stf = new SourceTimeFunction(np, tlen, samplingHz);
        stf.sourceTimeFunction = sourceTimeFunction;
        return stf;
    }

    public int getNp() {
        return np;
    }

    public double getTlen() {
        return tlen;
    }

    public double getSamplingHz() {
        return samplingHz;
    }

    /**
     * @return source time function in frequency domain. the length is
     * {@link #np}
     */
    public Complex[] getSourceTimeFunctionInFrequencyDomain() {
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
            pw.println("#np tlen samplingHz");
            pw.println(np + " " + tlen + " " + samplingHz);
            for (int i = 0; i < sourceTimeFunction.length; i++)
                pw.println(sourceTimeFunction[i].getReal() + " " + sourceTimeFunction[i].getImaginary());
        }

    }

    /**
     * TODO
     *
     * @param dataInFrequency
     * @return
     */
    private double[] inverseFourierTransform(Complex[] dataInFrequency) {
        // pack to temporary Complex array
        Complex[] data = new Complex[nptsInTimeDomain];
        System.arraycopy(dataInFrequency, 0, data, 0, np + 1);

        // set blank due to lsmooth
        Arrays.fill(data, np + 1, nptsInTimeDomain / 2 + 1, Complex.ZERO);

        // set values for imaginary frequency
        for (int i = 0, nnp = nptsInTimeDomain / 2; i < nnp - 1; i++)
            data[nnp + 1 + i] = data[nnp - 1 - i].conjugate();

        // fast fourier transformation
        data = fft.transform(data, TransformType.INVERSE);

        return Arrays.stream(data).mapToDouble(Complex::getReal).toArray();
    }

    /**
     * Operates convolution for data in <b>time</b> domain.
     *
     * @param data to be convolved in <b>time</b> domain. The data is convolved
     *             after FFTed.
     * @return convolute data in <b>time</b> domain
     */
    public final double[] convolve(double[] data) {
        if (data.length != nptsInTimeDomain)
            throw new IllegalArgumentException("Input data is invalid (length): " + data.length + " " + nptsInTimeDomain);
        Complex[] dataInFrequencyDomain = fft.transform(data, TransformType.FORWARD);
        dataInFrequencyDomain = Arrays.copyOfRange(dataInFrequencyDomain, 0, np + 1);
        Complex[] convolvedDataInFrequencyDomain = convolve(dataInFrequencyDomain);
        return inverseFourierTransform(convolvedDataInFrequencyDomain);
    }

    /**
     * Operates convolution for data in <b>frequency</b> domain.
     *
     * @param data to be convolved in <b>frequency</b> domain. The length must be
     *             {@link #np} + 1
     * @return convolute data in <b>frequency</b> domain
     */
    public final Complex[] convolve(Complex[] data) {
        if (data.length != np + 1)
            throw new IllegalArgumentException("Input data length is invalid: " + data.length + " " + (np+1));
        return IntStream.range(0, np + 1).parallel()
                .mapToObj(i -> i == 0 ? data[i] : data[i].multiply(sourceTimeFunction[i - 1])).toArray(Complex[]::new);
    }

    public final Complex[] convolveSerial(Complex[] data) {
        if (data.length != np + 1)
            throw new IllegalArgumentException("Input data length is invalid: " + data.length + " " + (np+1));
        return IntStream.range(0, np + 1)
                .mapToObj(i -> i == 0 ? data[i] : data[i].multiply(sourceTimeFunction[i - 1])).toArray(Complex[]::new);
    }

    /**
     * @param sacData to convolute with this.
     * @return convoluted SACData
     */
    public final SACFileAccess convolve(SACFileAccess sacData) {
        double[] data = sacData.getData();
        double[] convolute = convolve(data);
        return sacData.setSACData(convolute);
    }

    /**
     * x axis: time [s], y axis: amplitude
     * After considering that conjugate F[i] = F[N-i],
     *
     * @return trace of Source time function in time domain
     */
    public Trace getSourceTimeFunctionInTimeDomain() {
        Objects.requireNonNull(sourceTimeFunction, "Source time function is not computed yet.");
        double[] time = new double[nptsInTimeDomain];
        Arrays.setAll(time, i -> i / samplingHz);

        Complex[] stf = new Complex[nptsInTimeDomain];
        Arrays.fill(stf, Complex.ZERO);
        for (int i = 0 ; i < np; i++) {
            stf[i] = sourceTimeFunction[i];
            stf[nptsInTimeDomain - 1 - i] = stf[i+1].conjugate();
        }
        double[] stfInTime = Arrays.stream(inverseFourierTransform(stf)).map(d -> d * samplingHz).toArray();
        return new Trace(time, stfInTime);
    }

}
