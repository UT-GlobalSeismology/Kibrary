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
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.complex.Complex;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAid;

/**
 * Source time function. <br>
 * <p>
 * You have to multiply<br>
 * Source time function: stf[0], .. stf[NP] <br>
 * on <br>
 * Waveform in frequency domain: U[0].. U[NP], respectively. See {@link #convolve(Complex[])}
 *
 * @since before 2016/1/25
 * @author Kensuke Konishi
 */
public class SourceTimeFunction {

    /**
     * Number of steps in frequency domain (counting only positive frequency part).
     */
    private final int np;
    /**
     * Time length of whole STF waveform [s]. Its reciprocal will be the size of frequency steps.
     */
    private final double tlen;
    /**
     * Source time function in frequency domain. Length is np + 1.
     */
    private Complex[] sourceTimeFunction;

    /**
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of whole STF waveform [s]. Its reciprocal will be the size of frequency steps.
     */
    SourceTimeFunction(int np, double tlen) {
        this.np = np;
        this.tlen = tlen;
    }

    /**
     * @param sourceTimeFunction (Complex[]) Source time function in frequency domain. Length is np + 1.
     * @param tlen (double) Time length of whole STF waveform [s]. Its reciprocal will be the size of frequency steps.
     */
    SourceTimeFunction(Complex[] sourceTimeFunction, double tlen) {
        this.np = sourceTimeFunction.length - 1;
        this.tlen = tlen;
        this.sourceTimeFunction = sourceTimeFunction;
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
    public static final SourceTimeFunction gaussianSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2 * Math.PI * deltaF * halfDuration;
        //sourceTimeFunction.sourceTimeFunction[0] = Complex.ONE;
        for (int i = 0; i < np + 1; i++) {
            // TODO check the correctness
            double omegaTau = i * constant;
            double coef1 = 0.5 * Math.exp(-1.0 * Math.pow(omegaTau + Math.PI, 2.0) / 72.0);
            double coef2 = 0.5 * Math.exp(-1.0 * Math.pow(omegaTau - Math.PI, 2.0) / 72.0);
            sourceTimeFunction.sourceTimeFunction[i] =
                    new Complex(coef1 * Math.sin(0.5 * (omegaTau + Math.PI)) - coef2 * Math.sin(0.5 * (omegaTau - Math.PI)),
                            coef1 * Math.cos(0.5 * (omegaTau + Math.PI)) - coef2 * Math.cos(0.5 * (omegaTau - Math.PI)));
        }
        return sourceTimeFunction;
    }

    /**
     * ASYMMETRIC triangle source time function.
     * <p>
     * The width is determined by the left-side half duration &tau;<sub>1</sub> and right-side half duration &tau;<sub>2</sub>. <br>
     * f(t) = 2(t + &tau;<sub>1</sub>) / &tau;<sub>1</sub>(&tau;<sub>1</sub> + &tau;<sub>2</sub>) (-&tau;<sub>1</sub> &le; t &le; 0), <br>
     * &emsp;&emsp;  2(&tau;<sub>2</sub> - t) / &tau;<sub>2</sub>(&tau;<sub>1</sub> + &tau;<sub>2</sub>) (0 &le; t &le; &tau;<sub>2</sub>), <br>
     * &emsp;&emsp;  0 (t &lt; -&tau;<sub>1</sub>, &tau;<sub>2</sub> &lt; t) <br>
     * Source time function F(&omega;) = 2 / (&tau;<sub>1</sub> + &tau;<sub>2</sub>)&omega;<sup>2</sup>
     * * ( (1 - cos&omega;&tau;<sub>1</sub>) / &tau;<sub>1</sub> + (1 - cos&omega;&tau;<sub>2</sub>) / &tau;<sub>2</sub>
     * - i sin&omega;&tau;<sub>1</sub> / &tau;<sub>1</sub> + i sin&omega;&tau;<sub>2</sub> / &tau;<sub>2</sub> )
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration1 (double) Left-side half duration [s] of the source.
     * @param halfDuration2 (double) Right-side half duration [s] of the source.
     * @return ({@link SourceTimeFunction}) Created STF.
     *
     * @author lina
     */
    static SourceTimeFunction asymmetricTriangleSourceTimeFunction(int np, double tlen, double halfDuration1, double halfDuration2) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        double deltaF = 1.0 / tlen;
        double h = 2.0 / (halfDuration1 + halfDuration2);
        sourceTimeFunction.sourceTimeFunction[0] = Complex.ONE;
        for (int i = 1; i < np + 1; i++) {
            double omega = i * 2.0 * Math.PI * deltaF;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(
                    h / omega / omega * (1.0 / halfDuration1 + 1.0 / halfDuration2 - Math.cos(omega * halfDuration1) / halfDuration1 - Math.cos(omega * halfDuration2) / halfDuration2),
                    -h / omega / omega * (Math.sin(omega * halfDuration1) / halfDuration1 - Math.sin(omega * halfDuration2) / halfDuration2));
        }
        return sourceTimeFunction;
    }

    /**
     * Triangle source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/&tau;<sup>2</sup> t + 1/&tau; (-&tau; &le; t &le; 0), <br>
     * &emsp;&emsp;  -1/&tau;<sup>2</sup> t + 1/&tau; (0 &le; t &le; &tau;), <br>
     * &emsp;&emsp;  0 (t &lt; -&tau;, &tau; &lt; t) <br>
     * Source time function F(f) = (2 - 2 cos(2 &pi; &tau; f)) / (2 &pi; &tau; f)<sup>2</sup>
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link SourceTimeFunction}) Created STF.
     */
    static final SourceTimeFunction triangleSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2.0 * Math.PI * deltaF * halfDuration;
        sourceTimeFunction.sourceTimeFunction[0] = Complex.ONE;
        for (int i = 1; i < np + 1; i++) {
            double omegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex((2.0 - 2.0 * Math.cos(omegaTau)) / omegaTau / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Boxcar source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = 1/(2&tau;) (-&tau; &le; t &le; &tau;), <br>
     * &emsp;&emsp;  0 (t &lt; -&tau;, &tau; &lt; t) <br>
     * Source time function F(f) = sin(2 &pi; &tau; f) / (2 &pi; &tau; f)
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link SourceTimeFunction}) Created STF.
     */
    public static final SourceTimeFunction boxcarSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2.0 * Math.PI * deltaF * halfDuration;
        sourceTimeFunction.sourceTimeFunction[0] = Complex.ONE;
        for (int i = 1; i < np + 1; i++) {
            double omegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(Math.sin(omegaTau) / omegaTau);
        }
        return sourceTimeFunction;
    }

    /**
     * Smoothed ramp source time function.
     * <p>
     * The width is determined by the half duration &tau;. <br>
     * f(t) = (1-tanh<sup>2</sup>(2t/&tau;))/&tau; (-&tau; &le; t &le; &tau;), <br>
     * &emsp;&emsp;  0 (t &lt; -&tau;, &tau; &lt; t) <br>
     * Source time function F(&omega;) = (&pi;<sup>2</sup> &tau; f / 2) / sinh(&pi;<sup>2</sup> &tau; f / 2)<br>
     *
     * @param np (int) Number of steps in frequency domain (only positive frequency part).
     * @param tlen (double) Time length of STF [s]. Its reciprocal will be the size of frequency steps.
     * @param halfDuration (double) Half duration [s] of the source.
     * @return ({@link SourceTimeFunction}) Created STF.
     */
    static final SourceTimeFunction smoothedRampSourceTimeFunction(int np, double tlen, double halfDuration) {
        SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(np, tlen);
        sourceTimeFunction.sourceTimeFunction = new Complex[np + 1];
        final double deltaF = 1.0 / tlen;
        final double constant = 2.0 * Math.PI * deltaF * halfDuration * Math.PI / 4.0;
        sourceTimeFunction.sourceTimeFunction[0] = Complex.ONE;
        for (int i = 1; i < np + 1; i++) {
            double quarterPiOmegaTau = i * constant;
            sourceTimeFunction.sourceTimeFunction[i] = new Complex(quarterPiOmegaTau / Math.sinh(quarterPiOmegaTau));
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
            pw.println("#real complex");
            for (int i = 0; i < sourceTimeFunction.length; i++)
                pw.println(sourceTimeFunction[i].getReal() + " " + sourceTimeFunction[i].getImaginary());
        }
    }

    static SourceTimeFunction read(Path sourcePath) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath);
        String[] parts = lines.get(1).split("\\s+");
        int np = Integer.parseInt(parts[0]);
        double tlen = Double.parseDouble(parts[1]);
        Complex[] function = IntStream.range(0, np + 1).mapToObj(i -> toComplex(lines.get(i + 3))).toArray(Complex[]::new);

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
     * Get source time function in time domain.
     * x axis: time [s], y axis: amplitude
     * Converted after considering that conjugate F[i] = F[N-i].
     * @return ({@link Trace}) Source time function in time domain.
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Create a source time function file under the working folder.
     * @param args Options.
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
        Options options = Summon.defaultOptions();

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("i").longOpt("id").hasArg().argName("eventID")
                .desc("Global CMT ID.").build());
        inputOption.addOption(Option.builder("h").longOpt("halfDuration").hasArg().argName("halfDuration")
                .desc("Half duration [s] of the source.").build());
        inputOption.setRequired(true);
        options.addOptionGroup(inputOption);

        options.addOption(Option.builder("f").longOpt("function").hasArg().argName("functionType")
                .desc("Type of source time function, from {1:boxcar, 2:triangle, 4:auto, 5: gaussian}. (4)").build());
        options.addOption(Option.builder("n").longOpt("np").hasArg().argName("np")
                .desc("Number of steps in frequency domain (counting only positive frequency part). (512)").build());
        options.addOption(Option.builder("t").longOpt("tlen").hasArg().argName("tlen")
                .desc("Time length of whole STF waveform [s]. Its reciprocal will be the size of frequency steps. (3276.8)").build());
        options.addOption(Option.builder("s").longOpt("samplingHz").hasArgs().argName("samplingHz")
                .desc("Sampling Hz of STF waveform. If this option is set, the STF waveform is output in the time domain.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");

        SourceTimeFunctionType type = cmdLine.hasOption("f")
                ? SourceTimeFunctionType.ofNumber(Integer.parseInt(cmdLine.getOptionValue("f")))
                : SourceTimeFunctionType.AUTO;
        int np = cmdLine.hasOption("n") ? Integer.parseInt(cmdLine.getOptionValue("n")) : 512;
        double tlen = cmdLine.hasOption("t") ? Double.parseDouble(cmdLine.getOptionValue("t")) : 3276.8;

        if (cmdLine.hasOption("i")) {
            GlobalCMTID event = new GlobalCMTID(cmdLine.getOptionValue("i"));
            SourceTimeFunctionHandler stfHandler = new SourceTimeFunctionHandler(type, null, null);
            Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), event.toString(), fileTag, false, null, ".stf");
            stfHandler.createSourceTimeFunction(np, tlen, event).write(outputPath);

        } else if (cmdLine.hasOption("h")) {
            double halfDuration = Double.parseDouble(cmdLine.getOptionValue("h"));
            Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "testSTF", fileTag, appendFileDate, null, ".stf");
            SourceTimeFunction stf;
            switch (type) {
            case BOXCAR:
                stf = boxcarSourceTimeFunction(np, tlen, halfDuration);
                break;
            case TRIANGLE:
                stf = triangleSourceTimeFunction(np, tlen, halfDuration);
                break;
            case GAUSSIAN:
                stf = gaussianSourceTimeFunction(np, tlen, halfDuration);
                break;
            default:
                throw new IllegalArgumentException("STF type " + type + " not allowed.");
            }
            if (cmdLine.hasOption("s")) {
                double samplingHz = Double.parseDouble(cmdLine.getOptionValue("s"));
                stf.getSourceTimeFunctionInTimeDomain(samplingHz).write(outputPath);
            } else {
                stf.write(outputPath);
            }
        }
    }
}
