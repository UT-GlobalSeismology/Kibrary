package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import io.github.kensuke1984.kibrary.math.MatrixFile;

/**
 * Conjugate gradient method.
 * <p>
 * See Appendix 3 of Kawai et al. (2014) for further information.
 *
 * @see <a href=https://ja.wikipedia.org/wiki/%E5%85%B1%E5%BD%B9%E5%8B%BE%E9%85%8D%E6%B3%95>Japanese wiki</a>,
 * <a href=https://en.wikipedia.org/wiki/Conjugate_gradient_method>English wiki</a>
 *
 * @since 2016/1/25
 * @author Kensuke Konishi
 */
public class ConjugateGradientMethod extends InversionMethod {

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
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     * @param m0 (RealVector) Initial vector m<sub>0</sub>.
     */
    public ConjugateGradientMethod(RealMatrix ata, RealVector atd, RealVector m0) {
        this.ata = ata;
        this.atd = atd;
        int dimension = ata.getColumnDimension();
        // when initial vector is not set, set it as zero-vector
        this.m0 = (m0 != null) ? m0 : new ArrayRealVector(dimension);
        // set up matrices
        p = MatrixUtils.createRealMatrix(dimension, dimension);
        answer = MatrixUtils.createRealMatrix(dimension, dimension);
        alpha = new ArrayRealVector(dimension);
    }

    /**
     * Compute using CG method.
     * The i-th answer is stored in the (i-1)th column of {@link InversionMethod#answer} (0:CG1 , 1:CG2 , ...).
     */
    @Override
    public void compute() {
        System.err.println("Solving by CG (conjugate gradient) method.");

        // r_0 = Atd - AtA m_0
        RealVector r = atd.subtract(ata.operate(m0));
        double rrNew = r.dotProduct(r);
        double rrOld = rrNew;
        // p_0 = r_0
        RealVector p_i = r;
        p.setColumnVector(0, p_i);

        // remember AtA p
        RealVector atap = ata.operate(p_i);
        // alpha = r r / p AtA p
        alpha.setEntry(0, rrNew / p_i.dotProduct(atap));
        // m_1 = m_0 + alpha p
        answer.setColumnVector(0, p_i.mapMultiply(alpha.getEntry(0)).add(m0));

        for (int i = 1; i < ata.getColumnDimension(); i++) {
            // r_{k+1} = r_k - alpha AtA p
            r = r.subtract(atap.mapMultiply(alpha.getEntry(i - 1)));
            // beta = r_{k+1} r_{k+1} / r_k r_k
            rrNew = r.dotProduct(r);
            double b = rrNew / rrOld;
            rrOld = rrNew;
            // p_{k+1} = r + beta p
            p_i = r.add(p.getColumnVector(i - 1).mapMultiply(b));
            p.setColumnVector(i, p_i);

            // remember new AtA p
            atap = ata.operate(p_i);
            // alpha = r r / p AtA p
            alpha.setEntry(i, rrNew / p_i.dotProduct(atap));
            // m_{k+1} = m_k + alpha p
            answer.setColumnVector(i, p_i.mapMultiply(alpha.getEntry(i)).add(answer.getColumnVector(i - 1)));
        }
    }

    /**
     * Cov(<b>m</b><sub>j</sub>) = &sigma;<sub>D</sub><sup>2</sup> &Sigma;<sub>i=1</sub><sup>j</sup>
     *  (<b>p</b><sub>i</sub> <b>p</b><sub>i</sub><sup>T</sup>)
     *  / (<b>p</b><sub>i</sub><sup>T</sup> A<sup>T</sup>A <b>p</b><sub>i</sub>) . <br>
     *  (Truncation of &sigma;<sub>d</sub><sup>2</sup> P L<sup>-1</sup> P<sup>T</sup> .)
     *
     * <p>
     * See eq. (A33) of Kawai et al. (2014).
     */
    @Override
    public RealMatrix computeCovariance(double sigmaD, int j) {
        RealMatrix covariance = MatrixUtils.createRealMatrix(getNParameter(), getNParameter());
        for (int i = 0; i < j; i++) {
            // p_i^T A^T A p_i
            double paap = p.getColumnVector(i).dotProduct(ata.operate(p.getColumnVector(i)));
            double coeff = sigmaD * sigmaD / paap;
            // get p_i as a 1-column matrix
            RealMatrix pi = p.getColumnMatrix(i);
            covariance = covariance.add(pi.multiply(pi.transpose()).scalarMultiply(coeff));
        }
        return covariance;
    }

    public static void computeResolutionMatrix(Path inversionPath, Path resultPath, int nBasis, Path outputPath) throws IOException {
        RealMatrix ata = MatrixFile.read(inversionPath.resolve("ata.lst"));
        RealMatrix p = MatrixFile.read(resultPath.resolve("pMatrix.lst"));

        int dimension = ata.getColumnDimension();
        if (p.getColumnDimension() != dimension) throw new IllegalStateException("Size of AtA and P do not match.");

        // compute PLP
        RealMatrix plp = MatrixUtils.createRealMatrix(dimension, dimension);
        for (int j = 0; j < nBasis; j++) {
            RealVector pjVec = p.getColumnVector(j);
            double paap = pjVec.dotProduct(ata.operate(pjVec));
            RealMatrix pjMat = p.getColumnMatrix(j);
            plp = plp.add(pjMat.multiply(pjMat.transpose()).scalarMultiply(1 / paap));
        }

        // compute resolution matrix and output
        RealMatrix resMatrix = plp.multiply(ata);
        MatrixFile.write(resMatrix, outputPath);
    }

    @Override
    public void outputBasisVectors(Path outPath) throws IOException {
        Files.createDirectories(outPath);
        System.err.println("Outputting base vectors in " + outPath);
        MatrixFile.write(p, outPath.resolve("pMatrix.lst"));
    }

    @Override
    public RealMatrix getBasisVectors() {
        return p;
    }

    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.CONJUGATE_GRADIENT;
    }
}
