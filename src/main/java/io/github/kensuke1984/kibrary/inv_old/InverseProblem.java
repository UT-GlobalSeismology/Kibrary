package io.github.kensuke1984.kibrary.inv_old;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * @author Kensuke Konishi
 * @since version 0.0.4
 */
public abstract class InverseProblem {

    RealMatrix ans;
    RealMatrix ata;
    RealVector atd;

    public void setANS(int i, RealVector v) {
        ans.setColumnVector(i - 1, v);
    }

    public RealMatrix getANS() {
        return ans;
    }

    /**
     * @param i index (1, 2, ...)
     * @return i th answer
     */
    public RealVector getAnsVec(int i) {
        if (i <= 0) throw new IllegalArgumentException("i must be a natural number.");
        return ans.getColumnVector(i - 1);
    }

    /**
     * @return the number of unknown parameters
     */
    public int getNParameter() {
        if (ata != null)
            return ata.getColumnDimension();
        else
            return atd.getDimension();
    }

    /**
     * Output the answers inside a certain folder.
     * @param unknowns (List)
     * @param outPath (Path) Output folder
     * @throws IOException
     */
    public void outputAnswers(List<UnknownParameter> unknowns, Path outPath) throws IOException {
        if (unknowns.size() != getNParameter()) throw new IllegalArgumentException("Number of unknowns and answer dimension differ.");

        Files.createDirectories(outPath);
        System.err.println("Outputting the answer files in " + outPath);
        for (int i = 0; i < getNParameter(); i++) {
            Path outputPath = outPath.resolve(getEnum().simpleName() + (i+1) + ".lst");
            double[] m = ans.getColumn(i);
            KnownParameterFile.write(unknowns, m, outputPath);
        }
    }

    /**
     * output the answer
     * @param outPath {@link File} for write of solutions
     * @throws IOException if an I/O error occurs
     * @deprecated
     */
    public void outputAns(Path outPath) throws IOException {
        Files.createDirectories(outPath);
        System.err.println("outputting the answer files in " + outPath);
        for (int i = 0; i < getNParameter(); i++) {
            Path out = outPath.resolve(getEnum().simpleName() + (i+1) + ".txt");
            double[] m = ans.getColumn(i);
            writeDat(out, m);
        }
    }

    /**
     * output the answer
     * @param outPath {@link File} for write of solutions
     * @param parameterWeights
     * @throws IOException if an I/O error occurs
     * @deprecated
     */
    public void outputAns(Path outPath, double[] parameterWeights) throws IOException {
        Files.createDirectories(outPath);
        System.err.println("outputting the answer files in " + outPath);
        for (int i = 0; i < getNParameter(); i++) {
            Path out = outPath.resolve(getEnum().simpleName() + (i+1) + ".txt");
            double[] m = ans.getColumn(i);
            for (int j = 0; j < m.length; j++)
                m[j] *= parameterWeights[j];
            writeDat(out, m);
        }
    }

    /**
     * @param outPath
     * @throws IOException
     * @author anselme
     * @deprecated
     */
    public void outputAnsX(Path outPath) throws IOException {
        Files.createDirectories(outPath);
        System.err.println("outputting the answer files in " + outPath);
        for (int i = 0; i < getNParameter(); i++) {
            Path out = outPath.resolve(getEnum().simpleName() + "_x" + (i+1) + ".txt");
            double[] m = ans.getColumn(i);
            writeDat(out, m);
        }
    }

    @Deprecated
    private static void writeDat(Path out, double[] dat) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            Arrays.stream(dat).forEach(pw::println);
        }
    }

    public abstract void compute();

    /**
     * @param sigmaD (double) &sigma;<sub>d</sub>
     * @param j      (int) index (1, 2, ...)
     * @return j番目の解の共分散行列 &sigma;<sub>d</sub> <sup>2</sup> V (&Lambda;
     * <sup>T</sup>&Lambda;) <sup>-1</sup> V<sup>T</sup>
     */
    public abstract RealMatrix computeCovariance(double sigmaD, int j);

    /**
     * @return (RealMatrix) Matrix that has the i-th basis vector as the i-th column.
     */
    public abstract RealMatrix getBaseVectors();

    abstract InverseMethodEnum getEnum();

}
