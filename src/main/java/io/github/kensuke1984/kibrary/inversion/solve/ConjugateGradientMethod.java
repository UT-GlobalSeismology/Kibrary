package io.github.kensuke1984.kibrary.inversion.solve;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * Conjugate gradient method.
 *
 * @author Kensuke Konishi
 * @since version 0.0.3.2
 * @see <a href=https://ja.wikipedia.org/wiki/%E5%85%B1%E5%BD%B9%E5%8B%BE%E9%85%8D%E6%B3%95>Japanese wiki</a>,
 * <a href=https://en.wikipedia.org/wiki/Conjugate_gradient_method>English wiki</a>
 */
public class ConjugateGradientMethod extends InverseProblem {

    /**
     * Alpha = (alpha1, alpha2, ...)
     */
    private final RealVector alpha;
    /**
     * P = (p1, p2, ....)
     */
    private final RealMatrix p;
    /**
     * m_0
     */
    private final RealVector m0;

    /**
     * AtAδm= AtD を解く
     *
     * @param ata AtA
     * @param atd AtD
     */
    public ConjugateGradientMethod(RealMatrix ata, RealVector atd) {
        this(ata, atd, null);
    }

    /**
     * Set up CG method to find m.
     * @param ata (RealMatrix) A<sup>T</sup>A
     * @param atd (RealVector) A<sup>T</sup>d
     * @param m0 (RealVector) Initial vector m<sub>0</sub>
     */
    public ConjugateGradientMethod(RealMatrix ata, RealVector atd, RealVector m0) {
        this.ata = ata;
        this.atd = atd;
        int column = ata.getColumnDimension();
        // when initial vector is not set, set it as zero-vector
        this.m0 = (m0 != null) ? m0 : new ArrayRealVector(column);
        // set up matrices
        p = MatrixUtils.createRealMatrix(column, column);
        ans = MatrixUtils.createRealMatrix(column, column);
        alpha = new ArrayRealVector(column);
    }

    /**
     * Compute using CG method.
     * The i-th answer is stored in the (i-1)th column of {@link InverseProblem#ans} (0:CG1 , 1:CG2 , ...).
     */
    @Override
    public void compute() {
        System.err.println("Solving by CG (conjugate gradient) method.");

        // r_0 = Atd - AtA m_0 (A35)
        RealVector r = atd.subtract(ata.operate(m0));
        // p_0 = r_0
        p.setColumnVector(0, r);

        // remember AtA p
        RealVector atap = ata.operate(p.getColumnVector(0));
        // alpha = r p / p AtA p
        alpha.setEntry(0, p.getColumnVector(0).dotProduct(r) / p.getColumnVector(0).dotProduct(atap));
        // m_1 = m_0 + alpha p
        ans.setColumnVector(0, p.getColumnVector(0).mapMultiply(alpha.getEntry(0)).add(m0));

        for (int i = 1; i < ata.getColumnDimension(); i++) {
            // r_{k+1} = r_k - alpha AtA p
            r = r.subtract(atap.mapMultiply(alpha.getEntry(i - 1)));
            // beta = - r AtA p / p AtA p
            double b = - r.dotProduct(atap) / p.getColumnVector(i - 1).dotProduct(atap);
            // p_{k+1} = r + beta p
            p.setColumnVector(i, r.add(p.getColumnVector(i - 1).mapMultiply(b)));

            // remember new AtA p
            atap = ata.operate(p.getColumnVector(i));
            // alpha = r p / p AtA p
            alpha.setEntry(i, p.getColumnVector(i).dotProduct(r) / p.getColumnVector(i).dotProduct(atap));
            // m_{k+1} = m_k + alpha p
            ans.setColumnVector(i, p.getColumnVector(i).mapMultiply(alpha.getEntry(i)).add(ans.getColumnVector(i - 1)));
        }
    }

    @Override
    public RealMatrix computeCovariance(double sigmaD, int j) {
        RealMatrix covariance = MatrixUtils.createRealMatrix(getNParameter(), getNParameter());
        double sigmaD2 = sigmaD * sigmaD;
        for (int i = 0; i < j ; i++) {
            double paap = p.getColumnVector(i).dotProduct(ata.operate(p.getColumnVector(i)));
            RealMatrix p = this.p.getColumnMatrix(i);
            double sigmaD2paap = sigmaD2 / paap;
            covariance = covariance.add(p.multiply(p.transpose()).scalarMultiply(sigmaD2paap));
        }
        return covariance;
    }

    /**
     * L<sub>i, j</sub> = p<sup>T</sup><sub>i</sub> A<sup>T</sup>A p<sub>i</sub>
     * i=j 0 i≠j
     *
     * @return L<sub>i, j</sub>
     */
    public RealMatrix getL() {
        RealMatrix l = MatrixUtils.createRealMatrix(getNParameter(), getNParameter());
        for (int i = 0; i < getNParameter(); i++) {
            RealVector p = this.p.getColumnVector(i);
            double val = p.dotProduct(ata.operate(p));
            l.setEntry(i, i, val);
        }
        return l;
    }

    @Deprecated
    public RealMatrix computeCovariance() {
        // RealMatrix ata = this.ata;
        RealMatrix covariance = MatrixUtils.createRealMatrix(getNParameter(), getNParameter());

        // new LUDecomposition(getL()).getSolver().getInverse();
        // covariance = p.multiply(getL().inverse()).multiply(p.transpose());
        // //TODO
        covariance = p.multiply(new LUDecomposition(getL()).getSolver().getInverse()).multiply(p.transpose());

        return covariance;
    }

    @Override
    public RealMatrix getBaseVectors() {
        return p;
    }

    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.CONJUGATE_GRADIENT;
    }
}
