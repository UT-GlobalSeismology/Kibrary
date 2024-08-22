package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Abstract parent class of various inversion methods to solve the problem A<sup>T</sup>A<b>m</b> = A<sup>T</sup><b>d</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public abstract class InversionMethod {

    RealMatrix answer;
    RealMatrix ata;
    RealVector atd;

    public static InversionMethod construct(InverseMethodEnum inverseMethod, RealMatrix ata, RealVector atd,
            double[] lambdas_LS, RealMatrix t_LS, RealVector eta_LS, RealVector m0_CG) {
        if (!ata.isSquare()) throw new IllegalArgumentException("AtA must be square.");
        if (ata.getRowDimension() != atd.getDimension()) throw new IllegalArgumentException("Dimension of AtA and Atd do not match.");

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


//    public void setANS(int i, RealVector v) {
//        ans.setColumnVector(i - 1, v);
//    }

    private int getNAnswer() {
        return answer.getColumnDimension();
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
            double[] m = answer.getColumn(i);
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
