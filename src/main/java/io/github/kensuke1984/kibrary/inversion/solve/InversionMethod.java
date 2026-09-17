package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Abstract parent class of various inversion methods to solve the problem A<sup>T</sup>A<b>m</b> = A<sup>T</sup><b>d</b>.
 * <p>
 * The (typical) regularized inverse problem can be written as
 *  (A<sup>T</sup>A + &lambda; I) <b>m</b> = A<sup>T</sup><b>d</b> . <br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |<b>m</b>|<sup>2</sup> is minimized.
 * <p>
 * By setting the matrix T, Tikhonov regularization can be applied. This makes T<b>m</b> get close to 0.<br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |T<b>m</b>|<sup>2</sup> is minimized.
 * <p>
 * By setting an additional vector <b>&eta;</b>, we can make T<b>m</b>-<b>&eta;</b> get close to 0. <br>
 * In this case,
 *  |<b>d</b>-A<b>m</b>|<sup>2</sup> + &lambda; |T<b>m</b>-<b>&eta;</b>|<sup>2</sup> is minimized.
 *
 * @since before 2016/1/25
 * @author Kensuke Konishi
 *
 * @version 2023/9/2 Renamed from inversion.InverseProblem to inversion.solve.InversionMethod.
 * @author otsuru
 */
public abstract class InversionMethod {

    RealMatrix answer;
    RealMatrix ata;
    RealVector atd;

    /**
     * Construct an inversion method after applying common regularization.
     *
     * @param inverseMethod ({@link InverseMethodEnum}) Inverse method.
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     * @param lambda_common (double) Common regularization parameter.
     * @param t_common (RealMatrix) Common regularization matrix. When null, identity matrix is used.
     * @param eta_common (RealVector) Common target vector. When null, it will not be used.
     * @param lambdas_LS (double[]) LS regularization parameter values.
     * @param t_LS (RealMatrix) LS regularization matrix. When null, identity matrix is used.
     * @param eta_LS (RealVector) LS target vector. When null, it will not be used.
     * @param m0_CG (RealVector) CG initial vector.
     * @return ({@link InversionMethod}) Constructed inversion method.
     */
    public static InversionMethod construct(InverseMethodEnum inverseMethod, RealMatrix ata, RealVector atd,
            double lambda_common, RealMatrix t_common, RealVector eta_common, double[] lambdas_LS,
            RealMatrix t_LS, RealVector eta_LS, RealVector m0_CG) {
        if (!ata.isSquare()) throw new IllegalArgumentException("AtA must be square.");
        if (ata.getRowDimension() != atd.getDimension()) throw new IllegalArgumentException("Dimension of AtA and Atd do not match.");
        if (t_common != null && t_common.getColumnDimension() != ata.getColumnDimension())
            throw new IllegalArgumentException("Dimension of common T is invalid.");
        if (eta_common != null && (t_common == null ? eta_common.getDimension() != ata.getColumnDimension()
                : eta_common.getDimension() != t_common.getRowDimension()))
            throw new IllegalArgumentException("Dimension of common eta and T do not match.");

        if (lambda_common != 0.) {
            RealMatrix t = (t_common != null) ? t_common : MatrixUtils.createRealIdentityMatrix(ata.getColumnDimension());
            RealMatrix tt = t.transpose();
            // At A + lambda Tt T
            ata = ata.add(tt.multiply(t).scalarMultiply(lambda_common));
            // At d + lambda Tt eta_LS
            if (eta_common != null) atd = atd.add(tt.operate(eta_common).mapMultiply(lambda_common));
        }

        RealVector conditioner = null;

        switch (inverseMethod) {
        case CONJUGATE_GRADIENT:
            return new ConjugateGradientMethod(ata, atd, m0_CG);
        case LEAST_SQUARES:
            return new LeastSquaresMethod(ata, atd, lambdas_LS, t_LS, eta_LS);
        case SINGULAR_VALUE_DECOMPOSITION:
            return new SingularValueDecomposition(ata, atd);

        //-----------------------
        case FAST_CONJUGATE_GRADIENT:
            return new FastConjugateGradientMethod(ata, atd, false); //TODO the name should be changed, but "ata" for FastConjugateGradientMethod is actually "a" (ata not needed for CG).
        case FAST_CONJUGATE_GRADIENT_DAMPED:
            if (conditioner == null) {
                conditioner = new ArrayRealVector(atd.getDimension(), 1.);
            }
            return new FastConjugateGradientMethod(ata, atd, true, conditioner); //TODO the name should be changed, but "ata" for FastConjugateGradientMethod is actually "a" (ata not needed for CG).
        case BICONJUGATE_GRADIENT_STABILIZED_METHOD:
            return new BiConjugateGradientStabilizedMethod(ata, atd);
        //-----------------------


        default:
            return null;
        }
    }
    @Deprecated
    InversionMethod getMethod(InverseMethodEnum inverseMethod, RealMatrix ata, RealMatrix a, RealVector u, RealVector s0) {
        switch (inverseMethod) {
        case NONLINEAR_CONJUGATE_GRADIENT:
            return new NonlinearConjugateGradientMethod(ata, a, s0, u);
        default:
            throw new RuntimeException("soteigai");
        }
    }
    @Deprecated
    InversionMethod getMethod(InverseMethodEnum inverseMethod, RealMatrix ata, RealVector atd, RealMatrix h) {
        switch (inverseMethod) {
        case CONSTRAINED_CONJUGATE_GRADIENT:
            return new ConstrainedConjugateGradientMethod(ata, atd, h);
        default:
            throw new RuntimeException("soteigai");
        }
    }

    /**
     * Output the answers inside a certain folder.
     * @param unknowns (List of {@link UnknownParameter}) Unknown parameters.
     * @param outPath (Path) Output folder.
     * @throws IOException
     */
    public void outputAnswers(List<UnknownParameter> unknowns, Path outPath) throws IOException {
        if (unknowns.size() != getNParameter()) throw new IllegalArgumentException("Number of unknowns and answer dimension differ.");

        Files.createDirectories(outPath);
        System.err.println("Outputting the answer files in " + outPath);
        for (int i = 0; i < getNAnswer(); i++) {
            Path outputPath = outPath.resolve(getEnum().simpleName() + (i + 1) + ".lst");
            double[] m = answer.getColumn(i);
            KnownParameterFile.write(unknowns, m, outputPath);
        }
    }

    public RealMatrix getAnswers() {
        return answer;
    }

    /**
     * @param i index (1, 2, ...)
     * @return i th answer
     */
    public RealVector getAnswerVector(int i) {
        if (i <= 0) throw new IllegalArgumentException("i must be a natural number.");
        return answer.getColumnVector(i - 1);
    }

    private int getNAnswer() {
        return answer.getColumnDimension();
    }

    /**
     * @return the number of unknown parameters
     */
    public int getNParameter() {
        if (ata != null) return ata.getColumnDimension();
        else return atd.getDimension();
    }

    public abstract void compute();

    /**
     * @param sigmaD (double) &sigma;<sub>d</sub>
     * @param j (int) index (1, 2, ...)
     * @return (int) The covariance matrix of the j-th answer Cov(<b>m</b><sub>j</sub>).
     */
    public abstract RealMatrix computeCovariance(double sigmaD, int j);

    /**
     * Output the basis vectors inside a certain folder.
     * @param outPath (Path) Output folder.
     * @throws IOException
     */
    public abstract void outputBasisVectors(Path outPath) throws IOException;

    /**
     * @return (RealMatrix) Matrix that has the i-th basis vector as the i-th column.
     */
    public abstract RealMatrix getBasisVectors();

    abstract InverseMethodEnum getEnum();

}
