package io.github.kensuke1984.kibrary.inversion.solve;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * Least squares method.
 * <p>
 * The (typical) regularized least squares problem can be written as
 *  (<b>A</b><sup>T</sup><b>A</b> + &lambda;<b>I</b>) m = <b>A</b><sup>T</sup>d . <br>
 * In this case,
 *  |d-<b>A</b>m|<sup>2</sup> + &lambda;|m|<sup>2</sup> is minimized.<br>
 * The answer is
 *  m = (<b>A</b><sup>T</sup><b>A</b> + &lambda;<b>I</b>)<sup>-1</sup> <b>A</b><sup>T</sup>d .
 * <p>
 * By setting the matrix <b>T</b>, Tikhonov regularization can be applied. This makes <b>T</b>m get close to 0.<br>
 * In this case,
 *  |d-<b>A</b>m|<sup>2</sup> + &lambda;|<b>T</b>m|<sup>2</sup> is minimized.<br>
 * The answer is
 *  m = (<b>A</b><sup>T</sup><b>A</b> + &lambda;<b>T</b><sup>T</sup><b>T</b>)<sup>-1</sup> <b>A</b><sup>T</sup>d
 * <p>
 * By setting an additional vector &eta;, we can make <b>T</b>m+&eta; get close to 0. <br>
 * In this case,
 *  |d-<b>A</b>m|<sup>2</sup> + &lambda;|<b>T</b>m+&eta;|<sup>2</sup> is minimized.<br>
 * The answer is
 *  m = (<b>A</b><sup>T</sup><b>A</b> + &lambda;<b>T</b><sup>T</sup><b>T</b>)<sup>-1</sup>
 *   (<b>A</b><sup>T</sup>d - &lambda;<b>T</b><sup>T</sup>&eta;)
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
    private RealMatrix t;

    /**
     * &eta; : vector value that <b>T</b>m should approach.
     */
    private RealVector eta;


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
     * @param t (RealMatrix) T
     * @param eta (RealVector) &eta;
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd, double lambda, RealMatrix t, RealVector eta) {
        if (t != null && t.getColumnDimension() != ata.getColumnDimension())
            throw new IllegalArgumentException("Dimension of T is invalid.");
        if (eta != null && t != null && eta.getDimension() != t.getRowDimension())
            throw new IllegalArgumentException("Dimension of eta and T do not match.");
        this.ata = ata;
        this.atd = atd;
        this.lambda = lambda;
        this.t = t;
        this.eta = eta;
    }

    @Override
    public void compute() {
        RealMatrix j = ata;
        RealVector k = atd;
        if (0 < lambda) {
            // when T is not set, set it as identity
            if (t == null) t = MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension());
            RealMatrix tt = t.transpose();
            // At A + lambda Tt T
            j = j.add(tt.multiply(t).scalarMultiply(lambda));
            // At d - lambda Tt eta
            if (eta != null) k = k.subtract(tt.operate(eta).mapMultiply(lambda));
        }
        ans = new Array2DRowRealMatrix(MatrixUtils.inverse(j).operate(k).toArray());
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
        return InverseMethodEnum.LEAST_SQUARES_METHOD;
    }

}
