package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {

        Path outPath1 = Paths.get("output1fsp.txt");
        Path outPath2 = Paths.get("output2fsp.txt");
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        double amplitude = 1000;
        double samplingHz = 20;
        double tlen = 3276.8;
        int np = 1024;
        int nnp = 2 * np * findLsmooth(samplingHz, tlen, np);
        double[] noiseKensuke = new double[nnp];
        double[] noiseUni = new double[nnp];
        double delta = 1.0 / samplingHz;
        double maxFreq = 0.1;
        double minFreq = 0.005;
        ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * delta * maxFreq, 2 * Math.PI * delta * minFreq, 6);

        // noise of Kensuke
        Complex[] kensukeU = createRandomComplex(amplitude, samplingHz, tlen, np);
        Complex[] kensukeTimeU = fft.transform(kensukeU, TransformType.INVERSE);
        for (int i = 0; i < kensukeTimeU.length; i++)
            noiseKensuke[i] = kensukeTimeU[i].getReal();
        noiseKensuke[0] = 0;
        noiseKensuke[nnp-1] = 0;
        noiseKensuke = bpf.applyFilter(noiseKensuke);
        Complex[] specKensuke = fft.transform(noiseKensuke, TransformType.FORWARD);

        // uniform nosie
        Complex[] timeU = new Complex[nnp];
        timeU[0] = new Complex(0., 0.);
        timeU[nnp-1] = new Complex(0., 0.);
        for (int i = 1; i < nnp -1; i++) {
            double argument = 2 * Math.PI * Math.random();
            timeU[i] = new Complex(Math.cos(argument), Math.sin(argument));
//            double real = 2. * Math.random() - 1.;
//            if (real > 1.0) real = 1.0;
//            if (real < -1.0) real = -1.0;
//            timeU[i] = new Complex(real, 0.);
        }
//        Complex[] specU = fft.transform(timeU, TransformType.FORWARD);
        for (int i = 0; i < timeU.length; i++)
            noiseUni[i] = timeU[i].getReal();
        noiseUni = bpf.applyFilter(noiseUni);
        noiseUni[0] = 0.0;
        noiseUni[nnp-1] = 0.0;
        Complex[] specUni = fft.transform(noiseUni, TransformType.FORWARD);

        // output
        Complex[] out1 = specKensuke;
        Complex[] out2 = specUni;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath1))) {
            for (int i = 0; i < out1.length; i++) {
                //pw.println(out1[i].getReal());
                pw.println(out1[i].abs());
            }
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath2))) {
            for (int i = 0; i < out2.length; i++) {
                //pw.println(out2[i].getReal());
                pw.println(out2[i].abs());
            }
        }
//        double[] out1 = noiseKensuke;
//        double[] out2 = noiseUni;
//        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath1))) {
//            for (int i = 0; i < out1.length; i++) {
//                pw.println(out1[i]);
//            }
//        }
//        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath2))) {
//            for (int i = 0; i < out2.length; i++) {
//                pw.println(out2[i]);
//            }
//        }


/*        // read knowns
        List<KnownParameter> knowns = KnownParameterFile.read(Paths.get(args[0]));

        // build model
        PolynomialStructure initialStructure = DefaultStructure.PREM;
        PerturbationModel model = new PerturbationModel(knowns, initialStructure);

        // output discrete perturbation file
        Map<FullPosition, Double> discreteMap = model.getPercentForType(VariableType.Vs);
        Path outputDiscretePath = Paths.get("").resolve("vsPercent.lst");
        PerturbationListFile.write(discreteMap, outputDiscretePath);
*/

/*        HorizontalPosition posE = new HorizontalPosition(-14, -69);
        HorizontalPosition posS = new HorizontalPosition(-16, 28);

        System.err.println(posE.computeEpicentralDistanceDeg(posS));

        double baz = posS.computeAzimuthDeg(posE);
        System.err.println(baz);
        System.err.println(posS.pointAlongAzimuth(baz, 24));
        System.err.println(posS.pointAlongAzimuth(baz, 60));

        System.err.println(posS.pointAlongAzimuth(0, 10));
*/
/*
        Set<DataFeature> featureSet = DataFeatureListFile.read(Paths.get(args[0]));

        Path acceptedPath = Paths.get("acceptedEntry.lst");
        Set<DataEntry> acceptedSet = featureSet.stream().filter(feature -> 0.2 < feature.getAbsRatio() && feature.getAbsRatio() < 5)
                .map(feature -> feature.getTimewindow().toDataEntry()).collect(Collectors.toSet());

        DataEntryListFile.writeFromSet(acceptedSet, acceptedPath);
 */
    }

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

        // set values for imaginary frequency
        for (int i = 0; i < nnp - 1; i++) {
            int ii = nnp + 1 + i;
            int jj = nnp - 1 - i;
            spectorU[ii] = spectorU[jj].conjugate();
        }
        return spectorU;
    }

    private static int findLsmooth(double samplingHz, double tlen, int np) {

        int lsmooth = (int) (0.5 * tlen * samplingHz / np);
        int i = Integer.highestOneBit(lsmooth);
        if (i < lsmooth) i *= 2;
        return i;
    }
}
