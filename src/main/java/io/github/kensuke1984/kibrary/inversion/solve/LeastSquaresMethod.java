package io.github.kensuke1984.kibrary.inversion.solve;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

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
 * @since version 0.1.0
 */
public class LeastSquaresMethod extends InverseProblem {

    /**
     * &lambda; : reguralization parameter.
     */
    private final double lambda;

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
     * @param ata (RealMatrix) A<sup>T</sup>A
     * @param atd (RealVector) A<sup>T</sup>d
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd) {
        this(ata, atd, 0, null, null);
    }

    /**
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup> + &lambda;|<b>T</b>m+&eta;|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A
     * @param atd (RealVector) A<sup>T</sup>d
     * @param lambda (double) &lambda;
     * @param t (RealMatrix) T. When null, identity matrix is used.
     * @param eta (RealVector) &eta;. When null, it will not be used.
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd, double lambda, RealMatrix t, RealVector eta) {
        if (t != null && t.getColumnDimension() != ata.getColumnDimension())
            throw new IllegalArgumentException("Dimension of T is invalid.");
        if (eta != null && t != null && eta.getDimension() != t.getRowDimension())
            throw new IllegalArgumentException("Dimension of eta and T do not match.");
        this.ata = ata;
        this.atd = atd;
        this.lambda = lambda;
        // when T is not set, set it as identity
        this.t = (t != null) ? t : MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension());
        this.eta = eta;
    }

    @Override
    public void compute() {
        System.err.println("Solving by LS (least squares) method.");

        RealMatrix j = ata;
        RealVector k = atd;
        if (0 < lambda) {
            RealMatrix tt = t.transpose();
            // At A + lambda Tt T
            j = j.add(tt.multiply(t).scalarMultiply(lambda));
            // At d - lambda Tt eta
            if (eta != null) k = k.subtract(tt.operate(eta).mapMultiply(lambda));
        }
        ans = MatrixUtils.createColumnRealMatrix(MatrixUtils.inverse(j).operate(k).toArray());
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
