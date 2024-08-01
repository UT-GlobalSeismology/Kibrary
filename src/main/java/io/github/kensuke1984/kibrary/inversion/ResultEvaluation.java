package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.MathAid;

public class ResultEvaluation {

    private final RealMatrix ata;
    private final RealVector atd;
    private final double numIndependent;
    private final double dNorm;
    private final double obsNorm;

    public ResultEvaluation(RealMatrix ata, RealVector atd, double numIndependent, double dNorm, double obsNorm) {
        this.ata = ata;
        this.atd = atd;
        this.numIndependent = numIndependent;
        this.dNorm = dNorm;
        this.obsNorm = obsNorm;
    }

    public void evaluate(RealMatrix ans, int maxNum, double[] alphas, Path outPath) throws IOException {
        System.err.println("Computing variance and AIC ...");

        // compute normalized variance up to basis vector maxNum
        double[] variances = new double[maxNum + 1];
        variances[0] = dNorm * dNorm / (obsNorm * obsNorm);
        for (int i = 0; i < maxNum; i++) {
            variances[i + 1] = varianceOf(ans.getColumnVector(i));
        }
        writeVariance(variances, outPath.resolve("variance.txt"));

        for (int k = 0; k < alphas.length; k++) {
            double[] aics = computeAIC(variances, alphas[k]);
            writeAIC(aics, outPath.resolve("aic_" + alphas[k] + ".txt"));
        }

        createScript(outPath, alphas);
    }

    private static void writeVariance(double[] dat, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < dat.length; i++) {
                // #CGVector normalizedVariance normalizedVariancePercent
                pw.println(i + " " + dat[i] + " " + (dat[i] * 100));
            }
        }
    }
    private static void writeAIC(double[] dat, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < dat.length; i++) {
                // #CGVector AIC normalizedAIC
                pw.println(i + " " + dat[i] + " " + (dat[i] / dat[0]));
            }
        }
    }

    /**
     * A&delta;m = &delta;d 求めたいのは (&delta;d - A&delta;m)<sup>T</sup>(&delta;d - A&delta;m) / |obs|<sup>2</sup>
     * <p>
     * (&delta;d<sup>T</sup> - &delta;m<sup>T</sup>A<sup>T</sup>)(&delta;d - A&delta;m) = &delta;d<sup>T</sup>&delta;d - &delta;d<sup>T
     * </sup>A&delta;m - &delta;m<sup>T</sup>A<sup>T</sup>&delta;d + &delta;m<sup>T</sup>A<sup>T</sup>A&delta;m = &delta;d<sup>T
     * </sup>&delta;d - 2*(A<sup>T</sup>&delta;d)&delta;m<sup>T</sup> + &delta;m<sup>T</sup>(A<sup>T</sup>A)&delta;m
     *
     * @param m &delta;m
     * @return |A&delta;m - &delta;d|<sup>2</sup>/|obs|<sup>2</sup>
     */
    private double varianceOf(RealVector m) {
        Objects.requireNonNull(m);

        double variance = dNorm * dNorm - 2  * atd.dotProduct(m) + m.dotProduct(ata.operate(m));
        return variance / (obsNorm * obsNorm);
    }

    /**
     * 自由度iに対してAICを計算する 独立データは n / alpha 各々のAIC群
     *
     * @param variance varianceの列
     * @param alpha    alpha redundancy
     * @return array of aic
     */
    private double[] computeAIC(double[] variance, double alpha) {
        double[] aic = new double[variance.length];
        int independentN = (int) (numIndependent / alpha);
        for (int i = 0; i < aic.length; i++)
            aic[i] = MathAid.computeAIC(variance[i], independentN, i);
        return aic;
    }

    private static void createScript(Path outPath, double[] alpha) throws IOException {
        Path scriptPath = outPath.resolve("plot.plt");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced font 'Helvetica,14'");
            pw.println("set xlabel 'i'");
            pw.println("set ylabel 'Normalized AIC'");
            pw.println("set y2label 'Normalized variance (%)'");
            pw.println("set ytics nomirror");
            pw.println("set y2tics nomirror");
            pw.println("set output 'plot.png'");

            pw.print("plot");
            for (int k = 0; k < alpha.length; k++) {
                String aicFileName = "aic_" + alpha[k] + ".txt";
                pw.println(" '" + aicFileName + "' u 1:3 axis x1y1 w l title \"{/Symbol a} = " + alpha[k] + "\", \\");
            }
            pw.println(" 'variance.txt' u 1:3 axis x1y2 w l title \"Normalized variance\"");
        }

        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
    }
}
