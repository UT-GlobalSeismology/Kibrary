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
 * By setting an additional vector <b>&eta;</b>, we can make T<b>m</b>-<b>&eta;</b> get close to 0. <br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |T<b>m</b>-<b>&eta;</b>|<sup>2</sup> is minimized.<br>
 * The answer is
 *  <b>m</b> = (A<sup>T</sup>A + &lambda; T<sup>T</sup>T)<sup>-1</sup>
 *   (A<sup>T</sup><b>d</b> + &lambda; T<sup>T</sup><b>&eta;</b>)
 *
 * @since 2016/4/16
 * @author Kensuke Konishi
 */
public class LeastSquaresMethod extends InversionMethod {

    /**
     * Values of &lambda; : reguralization parameter.
     */
    private final double[] lambdas_LS;
    /**
     * <b>T</b> : matrix that allows for more complex regularization patterns in Tikhonov regularization.
     */
    private final RealMatrix t_LS;
    /**
     * &eta; : vector value that <b>T</b>m should approach.
     */
    private final RealVector eta_LS;


    /**
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd) {
        // Note: by default, new arrays contain the value 0, so here, lambdas_LS={0.0}.
        this(ata, atd, new double[1], null, null);
    }

    /**
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup> + &lambda;|<b>T</b>m-&eta;|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     * @param lambdas_LS (double[]) LS regularization parameter values.
     * @param t_LS (RealMatrix) LS regularization matrix. When null, identity matrix is used.
     * @param eta_LS (RealVector) LS target vector. When null, it will not be used.
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd, double[] lambdas_LS, RealMatrix t_LS, RealVector eta_LS) {
        if (t_LS != null && t_LS.getColumnDimension() != ata.getColumnDimension())
            throw new IllegalArgumentException("Dimension of T is invalid.");
        if (eta_LS != null && t_LS != null && eta_LS.getDimension() != t_LS.getRowDimension())
            throw new IllegalArgumentException("Dimension of eta and T for LS do not match.");
        this.ata = ata;
        this.atd = atd;
        this.lambdas_LS = lambdas_LS;
        // when T is not set, set it as identity
        this.t_LS = (t_LS != null) ? t_LS : MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension());
        this.eta_LS = eta_LS;

        // set up answer matrix
        int dimension = ata.getColumnDimension();
        answer = MatrixUtils.createRealMatrix(dimension, lambdas_LS.length);
    }

    @Override
    public void compute() {
        System.err.println("Solving by LS (least squares) method.");

        for (int i = 0; i < lambdas_LS.length; i++) {
            double lambda_LS = lambdas_LS[i];
            RealMatrix j = ata.copy();
            RealVector k = atd.copy();
            if (0 < lambda_LS) {
                RealMatrix tt = t_LS.transpose();
                // At A + lambda Tt T
                j = j.add(tt.multiply(t_LS).scalarMultiply(lambda_LS));
                // At d + lambda Tt eta_LS
                if (eta_LS != null) k = k.add(tt.operate(eta_LS).mapMultiply(lambda_LS));
            }
            answer.setColumnVector(i, MatrixUtils.inverse(j).operate(k));
        }
    }

    @Override
    public void outputAnswers(List<UnknownParameter> unknowns, Path outPath) throws IOException {
        if (unknowns.size() != getNParameter()) throw new IllegalArgumentException("Number of unknowns and answer dimension differ.");

        Files.createDirectories(outPath);
        System.err.println("Outputting the answer files in " + outPath);
        for (int i = 0; i < lambdas_LS.length; i++) {
            Path outputPath = outPath.resolve(getEnum().simpleName() + MathAid.simplestString(lambdas_LS[i]) + ".lst");
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
    public void outputBasisVectors(Path outPath) throws IOException {}

    @Override
    public RealMatrix getBasisVectors() {
        throw new RuntimeException("No base vectors.");
    }

    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.LEAST_SQUARES;
    }

}
