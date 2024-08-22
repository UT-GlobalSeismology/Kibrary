package io.github.kensuke1984.kibrary.inversion.solve;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Inversion based on singular value decomposition (SVD).
 * (Note: In the actual program, the eigenvalue decomposition of A<sup>T</sup>A is used instead of the SVD of A.)
 * <p>
 * SVD is a factorization of the form A = U &Sigma; V<sup>T</sup>,
 *  where U and V are orthogonal, and &Sigma; is an m &times; n diagonal matrix. <br>
 * Then, the problem A<sup>T</sup>A<b>m</b> = A<sup>T</sup><b>d</b> becomes <br>
 *  <b>m</b> = V (&Sigma;<sup>T</sup>&Sigma;)<sup>-1</sup> V<sup>T</sup> A<sup>T</sup> <b>d</b> = V <b>p</b>
 *   = &Sigma;<sub>j=1</sub><sup>M</sup> p<sub>j</sub> <b>v</b><sub>j</sub> , <br>
 *  where we define <b>p</b> = (&Sigma;<sup>T</sup>&Sigma;)<sup>-1</sup> V<sup>T</sup> A<sup>T</sup><b>d</b>.
 * <p>
 * Now, A<sup>T</sup>A = V &Sigma;<sup>T</sup> U<sup>T</sup> U &Sigma; V<sup>T</sup> = V &Sigma;<sup>T</sup>&Sigma; V<sup>T</sup>.
 * This is the eigenvalue decomposition of A<sup>T</sup>A, with the diagonal matrix as D = &Sigma;<sup>T</sup>&Sigma;. <br>
 * Thus, V and &Sigma;<sup>T</sup>&Sigma; can be computed via the eigenvalue decomposition of A<sup>T</sup>A.
 * (Note that A<sup>T</sup>A is a real symmetric matrix, so V is orthogonal.)
 * <p>
 * To find the solution, we sum over the first n eigenvectors of the expansion as
 *  <b>m</b><sub>n</sub> = &Sigma;<sub>j=1</sub><sup>n</sup> p<sub>j</sub> <b>v</b><sub>j</sub> .
 * We can compute p<sub>j</sub> as
 *  p<sub>j</sub> = (1 / &sigma;<sub>j</sub><sup>2</sup>) (V<sup>T</sup> A<sup>T</sup><b>d</b>)<sub>j</sub>.
 * <p>
 * See Fuji et al. (2010) for further explanations.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=https://ja.wikipedia.org/wiki/%E7%89%B9%E7%95%B0%E5%80%A4%E5%88%86%E8%A7%A3>Japanese wiki</a>,
 * <a href=https://en.wikipedia.org/wiki/Singular_value_decomposition>English wiki</a>
 */
public class SingularValueDecomposition extends InversionMethod {

    private EigenDecomposition eigenDecomposition;

    /**
     * Set up method based on SVD to find m.
     * @param ata (RealMatrix) A<sup>T</sup>A.
     * @param atd (RealVector) A<sup>T</sup>d.
     */
    public SingularValueDecomposition(RealMatrix ata, RealVector atd) {
        this.ata = ata;
        this.atd = atd;
        // set up answer matrix
        int dimension = ata.getColumnDimension();
        answer = MatrixUtils.createRealMatrix(dimension, dimension);
    }

    @Override
    public void compute() {
        System.err.println("Solving by SVD (singular value decomposition).");

        System.err.print(" Decomposing AtA ...");
        eigenDecomposition = new EigenDecomposition(ata);
        System.err.println("  done");
        // V^t
        RealMatrix vt = eigenDecomposition.getVT();
        // Sigma^t Sigma
        RealMatrix sigma2 = eigenDecomposition.getD();
        // size of matrices
        int nParameter = ata.getRowDimension();

        // compute V^t A^t d
        RealVector vtatd = vt.operate(atd);

        // vector to store the answer m_j as accumulation of p_k v_k
        RealVector mj = new ArrayRealVector(nParameter);
        for (int j = 0; j < nParameter; j++) {
            // p_j = (1 / sigma_j^2) (V^t A^t d)_j
            double pj = vtatd.getEntry(j) / sigma2.getEntry(j, j);
            // m_j = sum_{k=1}^j p_k v_k
            mj = mj.add(vt.getRowVector(j).mapMultiply(pj));
            answer.setColumnVector(j, mj);
        }
    }

    @Override
    public void outputAnswers(List<UnknownParameter> unknowns, Path outPath) throws IOException {
        super.outputAnswers(unknowns, outPath);

        // output eigenvalues of AtA
        Path outputPath = outPath.resolve("eigenvaluesOfAta.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (double sigma2 : eigenDecomposition.getRealEigenvalues()) pw.println(sigma2);
        }
    }

    /**
     * Cov(<b>m</b><sub>j</sub>) = &sigma;<sub>D</sub><sup>2</sup> &Sigma;<sub>i=1</sub><sup>j</sup>
     *  (1 / &sigma;<sub>i</sub><sup>2</sup>) <b>v</b><sub>i</sub> <b>v</b><sub>i</sub><sup>T</sup> . <br>
     * See Fuji et al. (2010) for explanations.
     */
    @Override
    public RealMatrix computeCovariance(double sigmaD, int j) {
        RealMatrix covarianceMatrix = MatrixUtils.createRealMatrix(getNParameter(), getNParameter());
        // array of sigma^2
        double[] sigma2 = eigenDecomposition.getRealEigenvalues();
        for (int i = 0; i < j; i++) {
            double coeff = sigmaD * sigmaD / sigma2[i];
            // get v_i as a 1-column matrix
            RealMatrix vi = eigenDecomposition.getV().getColumnMatrix(i);
            covarianceMatrix = covarianceMatrix.add(vi.multiply(vi.transpose()).scalarMultiply(coeff));
        }
        return covarianceMatrix;
    }

    @Override
    public RealMatrix getBaseVectors() {
        return eigenDecomposition.getV();
    }

    @Override
    InverseMethodEnum getEnum() {
        return InverseMethodEnum.SINGULAR_VALUE_DECOMPOSITION;
    }

}
