package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static InverseProblem create(InverseMethodEnum inverseMethod, RealMatrix ata, RealVector atd,
            double lambda_LS, RealMatrix t_LS, RealVector eta_LS) {
        switch (inverseMethod) {
        case CONJUGATE_GRADIENT:
            return new ConjugateGradientMethod(ata, atd);
        case LEAST_SQUARES_METHOD:
            return new LeastSquaresMethod(ata, atd, lambda_LS, t_LS, eta_LS);
        default:
            return null;
        }
    }

    public void setANS(int i, RealVector v) {
        ans.setColumnVector(i - 1, v);
    }

    private int getNAnswer() {
        return ans.getColumnDimension();
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
        for (int i = 0; i < getNAnswer(); i++) {
            Path outputPath = outPath.resolve(getEnum().simpleName() + (i+1) + ".lst");
            double[] m = ans.getColumn(i);
            KnownParameterFile.write(unknowns, m, outputPath);
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
