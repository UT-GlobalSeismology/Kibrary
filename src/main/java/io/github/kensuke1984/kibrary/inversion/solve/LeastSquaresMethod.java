package io.github.kensuke1984.kibrary.inversion.solve;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
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


    public static void main(String[] args) {
        double[][] x = new double[][]{{1, 0.000, 0}, {0.000, 1, 0}, {0.000, 0, 1}};
        double[] d = new double[]{1, 2, 3};
        RealMatrix X = new Array2DRowRealMatrix(x);
        RealMatrix XtX = X.transpose().multiply(X);
        RealVector y = new ArrayRealVector(d);
        RealVector Xty = X.transpose().operate(y);
        double lambda = 100.0;
        RealMatrix w = new Array2DRowRealMatrix(new double[][]{{1, 0, -1}, {0, 2, 1}});
        RealMatrix wtw = w.transpose().multiply(w);
        double[][] t = new double[][]{{1, 1, 0}, {0, 1, 0}, {0, 1, 1}};
        RealMatrix T = new Array2DRowRealMatrix(t);
        double[] eta = new double[]{20, 100, -2.3};
        RealVector ETA = new ArrayRealVector(eta);
        LeastSquaresMethod lsm = new LeastSquaresMethod(XtX, Xty, lambda, T, ETA);
        lsm.compute();
        System.out.println(lsm.ans);
    }

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
     * Find m which gives minimum |d-<b>A</b>m|<sup>2</sup> + &lambda;|m|<sup>2</sup>.
     *
     * @param ata (RealMatrix) A<sup>T</sup>A
     * @param atd (RealVector) A<sup>T</sup>d
     * @param lambda (double) &lambda;
     */
    public LeastSquaresMethod(RealMatrix ata, RealVector atd, double lambda) {
        this(ata, atd, lambda, MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension()), null);
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
        this.ata = ata;
        this.atd = atd;
        this.lambda = lambda;
        this.t = t;
        this.eta = eta;
    }


    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.LEAST_SQUARES_METHOD;
    }

    @Override
    public RealMatrix computeCovariance(double sigmaD, int j) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void compute() {
        RealMatrix j = ata;
        RealVector k = atd;
        if (0 < lambda) {
            RealMatrix tt = t.transpose();
            j = j.add(tt.multiply(t).scalarMultiply(lambda));
            if (eta != null) k = k.subtract(tt.operate(eta).mapMultiply(lambda));
        }
        ans = new Array2DRowRealMatrix(MatrixUtils.inverse(j).operate(k).toArray());
    }

    @Override
    public RealMatrix getBaseVectors() {
        throw new RuntimeException("No base vectors.");
    }

}
