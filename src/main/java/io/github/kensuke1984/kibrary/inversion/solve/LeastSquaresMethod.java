package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Least squares method.
 * <p>
 * The (typical) regularized least squares problem can be written as
 *  (A<sup>T</sup>A + &lambda; I) <b>m</b> = A<sup>T</sup><b>d</b> . <br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |<b>m</b>|<sup>2</sup> is minimized.<br>
 * The answer is
 *  <b>m</b> = (A<sup>T</sup>A + &lambda; I)<sup>-1</sup> A<sup>T</sup><b>d</b> .
 * <p>
 * By setting the matrix T, Tikhonov regularization can be applied. This makes T<b>m</b> get close to 0.<br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |T<b>m</b>|<sup>2</sup> is minimized.<br>
 * The answer is
 *  <b>m</b> = (A<sup>T</sup>A + &lambda; T<sup>T</sup>T)<sup>-1</sup> A<sup>T</sup><b>d</b> .
 * <p>
 * By setting an additional vector <b>&eta;</b>, we can make T<b>m</b>+<b>&eta;</b> get close to 0. <br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |T<b>m</b>+<b>&eta;</b>|<sup>2</sup> is minimized.<br>
 * The answer is
 *  <b>m</b> = (A<sup>T</sup>A + &lambda; T<sup>T</sup>T)<sup>-1</sup>
 *   (A<sup>T</sup><b>d</b> - &lambda; T<sup>T</sup><b>&eta;</b>)
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class LeastSquaresMethod extends InversionMethod {

    /**
     * Values of &lambda; : reguralization parameter.
     */
    private final double[] lambdas;
    /**
     * <b>T</b> : matrix that allows for more complex regularization patterns in Tikhonov regularization.
     */
    private final RealMatrix t;
    /**
     * &eta; : vector value that <b>T</b>m should approach.
     */
    private final RealVector eta;


    /**
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd) {
        // Note: by default, new arrays contain the value 0, so here, lambdas={0.0}.
        this(ata, atd, new double[1], null, null);
    }

    /**
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup> + &lambda;|<b>T</b>m+&eta;|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     * @param lambdas (double[]) Values of &lambda; to compute for.
     * @param t (RealMatrix) T. When null, identity matrix is used.
     * @param eta (RealVector) &eta;. When null, it will not be used.
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd, double[] lambdas, RealMatrix t, RealVector eta) {
        if (t != null && t.getColumnDimension() != ata.getColumnDimension())
            throw new IllegalArgumentException("Dimension of T is invalid.");
        if (eta != null && t != null && eta.getDimension() != t.getRowDimension())
            throw new IllegalArgumentException("Dimension of eta and T do not match.");
        this.ata = ata;
        this.atd = atd;
        this.lambdas = lambdas;
        // when T is not set, set it as identity
        this.t = (t != null) ? t : MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension());
        this.eta = eta;

        // set up answer matrix
        int dimension = ata.getColumnDimension();
        answer = MatrixUtils.createRealMatrix(dimension, lambdas.length);
    }

    @Override
    public void compute() {
        System.err.println("Solving by LS (least squares) method.");

        RealMatrix j = ata;
        RealVector k = atd;
        for (int i = 0; i < lambdas.length; i++) {
            double lambda = lambdas[i];
            if (0 < lambda) {
                RealMatrix tt = t.transpose();
                // At A + lambda Tt T
                j = j.add(tt.multiply(t).scalarMultiply(lambda));
                // At d - lambda Tt eta
                if (eta != null) k = k.subtract(tt.operate(eta).mapMultiply(lambda));
            }
            answer.setColumnVector(i, MatrixUtils.inverse(j).operate(k));
        }
    }

    @Override
    public void outputAnswers(List<UnknownParameter> unknowns, Path outPath) throws IOException {
        if (unknowns.size() != getNParameter()) throw new IllegalArgumentException("Number of unknowns and answer dimension differ.");

        Files.createDirectories(outPath);
        System.err.println("Outputting the answer files in " + outPath);
        for (int i = 0; i < lambdas.length; i++) {
            Path outputPath = outPath.resolve(getEnum().simpleName() + MathAid.simplestString(lambdas[i]) + ".lst");
            double[] m = answer.getColumn(i);
            KnownParameterFile.write(unknowns, m, outputPath);
        }
    }

    @Override
    public RealMatrix computeCovariance(double sigmaD, int j) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RealMatrix getBaseVectors() {
        throw new RuntimeException("No base vectors.");
    }

    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.LEAST_SQUARES;
    }

}
