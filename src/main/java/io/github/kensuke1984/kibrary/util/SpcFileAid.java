package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;

/**
 * Utilities for collecting SPC files.
 *
 * @author otsuru
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class SpcFileAid {
    private SpcFileAid() {}

    /**
     * @param path ({@link Path}) Folder in which to look for {@link FormattedSPCFileName}s.
     * @return (Set of {@link SPCFileName}) SPC files in the folder.
     * @throws IOException
     */
    public static Set<SPCFileName> collectSpcFileName(Path path) throws IOException {
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toSet());
        }
    }

    public static List<SPCFileName> collectOrderedSpcFileNamePFPB(Path path, SPCMode spcMode) throws IOException {
        List<SPCFileName> fileNameList;
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            fileNameList = stream.filter(p -> (p.getFileName().toString().endsWith("PF..." + spcMode + ".spc")
                            || p.getFileName().toString().endsWith("PB..." + spcMode + ".spc")))
                    .sorted(Comparator.comparing(filePath -> filePath.getFileName().toString()))
                    .filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toList());
        }
        if (fileNameList.get(fileNameList.size() - 1).getReceiverID().equals("XY" + fileNameList.size()) == false) {
            throw new IllegalStateException("Error when collecting SPC files in " + path);
        }
        return fileNameList;
    }

    public static List<SPCFileName> collectOrderedSpcFileNameUFUB(Path path, SPCMode spcMode) throws IOException {
        List<SPCFileName> fileNameList;
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            fileNameList = stream.filter(p -> (p.getFileName().toString().endsWith("UF..." + spcMode + ".spc")
                            || p.getFileName().toString().endsWith("UB..." + spcMode + ".spc")))
                    .sorted(Comparator.comparing(filePath -> filePath.getFileName().toString()))
                    .filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toList());
        }
        if (fileNameList.get(fileNameList.size() - 1).getReceiverID().equals("XY" + fileNameList.size()) == false) {
            throw new IllegalStateException("Error when collecting SPC files in " + path);
        }
        return fileNameList;
    }

    /**
     * Compute npts = tlen * samplingHz, ensuring it is a power of 2.
     * @param tlen (double) Time length [s].
     * @param samplingHz (double) Sampling frequency [Hz].
     * @return (int) Number of data points in time domain.
     */
    public static int findNpts(double tlen, double samplingHz) {
        // npts = tlen * samplingHz must be a power of 2.
        if (!MathAid.isInteger(tlen * samplingHz)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        int npts = (int) MathAid.roundForPrecision(tlen * samplingHz);
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        return npts;
    }

    /**
     * Convert the data in frequency domain to time domain.
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
     * @param uFreq (Complex[]) Waveform in frequency domain.
     * @param np (int) Number of steps in frequency domain.
     * @param npts (int) Number of data points in time domain. Must be a power of 2.
     * @param samplingHz (double) Sampling frequency [Hz].
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @return (Complex[]) Waveform in time domain.
     */
    public static Complex[] convertToTimeDomain(Complex[] uFreq, int np, int npts, double samplingHz, double omegaI) {
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");

        //~conduct inverse Fourier transform
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        // pack to temporary Complex array
        Complex[] data = new Complex[npts];
        System.arraycopy(uFreq, 0, data, 0, np + 1);
        // set blank due to difference in np and npts
        int nnp = npts / 2;
        Arrays.fill(data, np + 1, nnp + 1, Complex.ZERO);
        // set values for imaginary frequency: F[i] = F[N-i]
        for (int i = 0; i < nnp - 1; i++)
            data[nnp + i + 1] = data[nnp - i - 1].conjugate();
        // fast fourier transformation
        Complex[] uTime = fft.transform(data, TransformType.INVERSE);

        //~apply growing exponential
        double constant = omegaI / samplingHz;
        for (int i = 0; i < npts; i++)
            uTime[i] = uTime[i].multiply(FastMath.exp(constant * i));

        //~correct amplitude
        double coef = 1000 * samplingHz;
        for (int i = 0; i < npts; i++)
            uTime[i] = uTime[i].multiply(coef);

        return uTime;
    }

    public static enum UsableSPCMode {
        SH,
        PSV,
        BOTH
    }

}
