package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * firstHandler 0.22.8 内の seedsac.c の移植
 * <p>
 * RESPを考慮した波形を作る
 * <p>
 * <p>
 * とりあえずtaperはsine taperで
 * <p>
 * Information about instrument response can be found <a href=http://docs.fdsn.org/projects/stationxml/en/latest/response.html>here</a>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
class SacDeconvolution {

    private final Path sourcePath;
    private final Path spectraPath;
    private final Path outputPath;

    private final double minFreq;
    private final double maxFreq;

    /**
     * true: sretraFile contain NaN, false: spectraFile in NOT contain NaN
     */
    private boolean isNaN = false;

    /**
     * 0: taper なし, 1: sine taper, 2: cosine taper TODO
     */
    private static int taperType = 1;

    /**
     * timewindowの端のテーピング領域（％） （フーリエ変換を行うため）
     */
    private static int taperAreaRatio = 5;

    /**
     * ナイキスト周波数
     */
    private static double nyquistFreq = 10;
    /**
     * フーリエ変換
     */
    private static FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

    /**
     * @param sourcePath 元になるSacFile
     * @param spectraPath   evalrespにより作成したスペクトルファイル
     * @param outputPath 装置関数を外したSacFile
     * @param minFreq       minimum frequency
     * @param maxFreq       maximum frequency
     */
    SacDeconvolution(Path sourceSacPath, Path spectraPath, Path outputSacPath, double minFreq, double maxFreq) {
        sourcePath = sourceSacPath;
        this.spectraPath = spectraPath;
        outputPath = outputSacPath;
        this.minFreq = minFreq;
        this.maxFreq = maxFreq;
    }

    void compute()
            throws IOException {
        Map<SACHeaderEnum, String> sacHeader = SACUtil.readHeader(sourcePath);
        double[] wavedata = SACUtil.readSACData(sourcePath);

        int npts = Integer.parseInt(sacHeader.get(SACHeaderEnum.NPTS));

        // 読み込んだwavedataにテーパーをかける
        if (taperAreaRatio != 0) taperInTimeDomain(wavedata);

        Complex[] complexWave = Arrays.stream(wavedata).mapToObj(Complex::new).toArray(Complex[]::new);
        // フーリエ変換 波形を周波数空間へ
        complexWave = fft.transform(complexWave, TransformType.FORWARD);

        Complex[] resp = new Complex[npts];
        double[] freq = new double[npts];
        readResponseFile(spectraPath, freq, resp);

        if (isNaN) {
            return;
        }

        // cut frequencyセット
        double cutfreq = 0.01;
        for (int i = 0; i < npts; i++)
            if (0.005 <= freq[i]) break;
            else if (0 < resp[i].getReal()) cutfreq = 1 / 360.0;

        // taperセット
        taperInFrequencyDomain(freq, cutfreq, minFreq, complexWave);

        // 装置関数を外す
        deconvolve(complexWave, resp);

        // 時間領域に戻す
        Complex[] finalComplexWave = fft.transform(complexWave, TransformType.INVERSE);

        Arrays.parallelSetAll(wavedata, i -> finalComplexWave[i].getReal());

        SACUtil.writeSAC(outputPath, sacHeader, wavedata);

    }

    /**
     * 波形データにテーピングをかける 時間窓の両端taperAreaRatio(%)にかける
     *
     * @param wavedata waveform data
     */
    private static void taperInTimeDomain(double[] wavedata) {
        int npts = wavedata.length;
        int taperArea = npts / 100 * taperAreaRatio;
        double angle = Math.PI / taperArea / 2;

        double[] taper = taperType == 1 ?
                IntStream.range(0, taperArea + 1).mapToDouble(i -> i * angle).map(Math::sin).toArray() :
                IntStream.range(0, taperArea + 1).mapToDouble(i -> i * angle)
                        .map(theta -> Math.sin(theta) * Math.sin(theta)).toArray();
        IntStream.range(0, taperArea + 1).forEach(i -> {
            wavedata[i] *= taper[i];
            wavedata[(npts - 1) - i] *= taper[i];
        });
    }

    /**
     * 装置関数を外す
     */
    private static void deconvolve(Complex[] complexData, Complex[] resp) {
        int npts = complexData.length;
        for (int i = 1; i < npts / 2; i++) {
            complexData[i] = complexData[i].divide(resp[i - 1]);
            complexData[npts - i] = complexData[npts - i].divide(resp[i - 1]);
        }
    }

    /**
     * 周波数領域でテーパーをかける
     *
     * @param freq        frequency data
     * @param cutfreq     cut frequency
     * @param minFreq     minimum frequency
     * @param complexData complex data
     */
    private static void taperInFrequencyDomain(double[] freq, double cutfreq, double minFreq, Complex[] complexData) {
        int npts = complexData.length;
        complexData[0] = Complex.ZERO;
        for (int i = 1; i <= npts / 2; i++) {
            double taper = 0;
            if (freq[i - 1] < cutfreq) if (freq[i - 1] < minFreq) taper = 0;
            else taper = 0.5 * (1 - Math.cos(Math.PI * freq[i - 1] / 2 / (cutfreq - minFreq)));
            else if (freq[i - 1] <= 0.9 * nyquistFreq) taper = 1;
            else if (freq[i] < nyquistFreq)
                taper = 0.5 * (1 + Math.cos(Math.PI * freq[i - 1] / 2.0 / (nyquistFreq * 0.1)));
            else taper = 0;
            complexData[i] = complexData[i].multiply(taper);
            complexData[npts - i] = complexData[npts - i].multiply(taper);
        }
    }

    /**
     * スペクトルファイルを読み込む
     *
     * @param spectorPath path for the file
     * @param freq        frequency data
     * @param resp        response data
     * @param isNaN       file contains NaN or file is empty
     */
    private void readResponseFile(Path spectorPath, double[] freq, Complex[] resp) throws IOException {
        List<String> lines = Files.readAllLines(spectorPath);

        if (lines.size() <= 0) {
            isNaN = true;
        }

        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\s+");

            try {
                freq[i] = Double.parseDouble(parts[0]);
                resp[i] = new Complex(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            } catch (NumberFormatException e) {
                isNaN = true;
                break;
            }

            if (resp[i] == null || resp[i].isNaN()) {
                isNaN = true;
                break;
            }
        }
    }

    /*
     * @return (boolean) spectra file contain NaN
     */
    boolean isNaN() {
        return isNaN;
    }

}
