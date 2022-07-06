package io.github.kensuke1984.kibrary.inv_new.setup;

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
 *
 * @author otsuru
 * @since 2022/7/4
 */
public class MatrixAssembly {

    private RealVector atd;
    private RealMatrix ata;

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
        DVectorBuilder dVector = new DVectorBuilder(basicIDs);

        // set weighting
        System.err.println("Setting weighting of type " + weightingType);
        Weighting weighting = new Weighting(dVector, weightingType, null);

        // set AMatrix
        System.err.println("Setting data for A matrix");
        AMatrixBuilder aMatrix = new AMatrixBuilder(partialIDs, parameterList, dVector);

        // assemble AtA and Atd
        System.err.println("Assembling A matrix");
        Matrix a = aMatrix.buildWithWeight(weighting);
        System.err.println("Assembling d vector");
        RealVector d = dVector.buildWithWeight(weighting);
        System.err.println("Assembling AtA");
        ata = a.computeAtA();
        System.err.println("Assembling Atd");
        atd = a.preMultiply(d);

    }

    public RealVector getAtd() {
        return atd;
    }

    public RealMatrix getAta() {
        return ata;
    }

}
