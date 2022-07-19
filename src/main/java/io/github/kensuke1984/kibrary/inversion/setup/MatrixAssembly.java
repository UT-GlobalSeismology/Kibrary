package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.math.Matrix;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.PartialID;

/**
 * Class for assembling A<sup>T</sup>A and A<sup>T</sup>d.
 * <p>
 * The size of A matrix will be decided by the input {@DVectorBuilder} and the input List of {@UnknownParameter}s.
 * The input {@PartialID} array can have extra IDs, but all needed IDs must be included.
 *
 * @author otsuru
 * @since 2022/7/4
 */
public class MatrixAssembly {

    private final DVectorBuilder dVectorBuilder;
    private final Matrix a;
    private final RealVector d;
    private RealMatrix ata;
    private RealVector atd;

    /**
     * Compute A<sup>T</sup>A and A<sup>T</sup>d.
     * <p>
     * Note that A<sup>T</sup>d can be calculated as follows: <br>
     * A<sup>T</sup>d = v <br>
     * then <br>
     * v<sup>T</sup> = (A<sup>T</sup>d)<sup>T</sup>= d<sup>T</sup>A
     *
     * @param basicIDs
     * @param partialIDs
     * @param parameterList
     * @param weightingType
     */
    public MatrixAssembly(BasicID[] basicIDs, PartialID[] partialIDs, List<UnknownParameter> parameterList,
            WeightingType weightingType) {

        // set DVector
        System.err.println("Setting data for d vector");
        dVectorBuilder = new DVectorBuilder(basicIDs);

        // set weighting
        System.err.println("Setting weighting of type " + weightingType);
        Weighting weighting = new Weighting(dVectorBuilder, weightingType, null);

        // set AMatrix
        System.err.println("Setting data for A matrix");
        AMatrixBuilder aMatrix = new AMatrixBuilder(partialIDs, parameterList, dVectorBuilder);

        // assemble A and d
        System.err.println("Assembling A matrix");
        a = aMatrix.buildWithWeight(weighting);
        System.err.println("Assembling d vector");
        d = dVectorBuilder.buildWithWeight(weighting);

    }

    public DVectorBuilder getDVectorBuilder() {
        return dVectorBuilder;
    }

    public Matrix getA() {
        return a;
    }

    public RealVector getAtd() {
        if (atd == null) {
            System.err.println("Assembling Atd");
            atd = a.preMultiply(d);
        }
        return atd;
    }

    public RealMatrix getAta() {
        if (ata == null) {
            System.err.println("Assembling AtA");
            ata = a.computeAtA();
        }
        return ata;
    }

}
