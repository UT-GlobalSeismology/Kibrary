package io.github.kensuke1984.kibrary.inversion;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * @author Kensuke Konishi
 * @version 0.0.3.2
 */
public abstract class InverseProblem {

    protected RealMatrix ans;
    protected RealMatrix ata;
    protected RealVector atd;

    private static void writeDat(Path out, double[] dat) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            Arrays.stream(dat).forEach(pw::println);
        }
    }

    public RealMatrix getANS() {
        return ans;
    }

    abstract InverseMethodEnum getEnum();

    /**
     * @param i index (1, 2, ...)
     * @return i th answer
     */
    public RealVector getAns(int i) {
        return ans.getColumnVector(i - 1);
    }

    /**
     * @return the number of unknown parameters
     */
    public int getParN() {
        return ata.getColumnDimension();
    }

    /**
     * @param sigmaD &sigma;<sub>d</sub>
     * @param j      index (1, 2, ...)
     * @return j番目の解の共分散行列 &sigma;<sub>d</sub> <sup>2</sup> V (&Lambda;
     * <sup>T</sup>&Lambda;) <sup>-1</sup> V<sup>T</sup>
     */
    public abstract RealMatrix computeCovariance(double sigmaD, int j);

    /**
     * 解のアウトプット
     *
     * @param outPath {@link File} for output of solutions
     * @throws IOException if an I/O error occurs
     */
    public void outputAns(Path outPath) throws IOException {
        Files.createDirectories(outPath);
        System.err.println("outputting the answer files in " + outPath);
        for (int i = 0; i < getParN(); i++) {
            Path out = outPath.resolve(getEnum().simple() + (i + 1) + ".txt");
            double[] m = ans.getColumn(i);
            writeDat(out, m);
        }
    }

    public abstract void compute();

    /**
     * @return 基底ベクトルを並べた行列 i番目のcolumnに i番目の基底ベクトル
     */
    public abstract RealMatrix getBaseVectors();

}
