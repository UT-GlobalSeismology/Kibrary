package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.selection.DataSelectionInformation;

/**
 * Weighting to be applied to A matrix and d vector in Am=d.
 * To be applied as the W matrix in WAm=Wd.
 * It is assumed to be a diagonal matrix, so the diagonal components are stored as a vector.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * Caution: {@link RealVector} is not immutable, so don't hand it over without deep-copying!
 *
 * @author otsuru
 * @since 2022/7/6 created based on part of inversion.Dvector
 */
public final class Weighting {

    private final RealVector[] weightingVecs;

    private final DVectorBuilder dVector;
    private final WeightingType weightingType;
    private final List<DataSelectionInformation> selectionInfo; // TODO apply

    public Weighting(DVectorBuilder dVector, WeightingType weightingType, List<DataSelectionInformation> selectionInfo) {
        this.dVector = dVector;
        this.weightingType = weightingType;
        this.selectionInfo = selectionInfo;

        this.weightingVecs = setWeights();

    }

    private RealVector[] setWeights() {
        ToDoubleBiFunction<RealVector, RealVector> WEIGHTING_FUNCTION;
        RealVector[] weightingVectors = new ArrayRealVector[dVector.getNTimeWindow()];

        switch (weightingType) {
        case RECIPROCAL:
            WEIGHTING_FUNCTION = (obs, syn) -> {
                return 1. / obs.getLInfNorm();
            };
            break;
        case IDENTITY:
            WEIGHTING_FUNCTION = (obs, syn) -> 1.;
            break;
        default:
            throw new UnsupportedOperationException("Weighting type " + weightingType + " not supported yet.");
        }

        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            double weighting;
            weighting = WEIGHTING_FUNCTION.applyAsDouble(dVector.getObsVec(i), dVector.getSynVec(i));

            double[] ws = new double[dVector.getObsVec(i).getDimension()];
            for (int j = 0; j < ws.length; j++) {
                ws[j] = weighting;
            }
            weightingVectors[i] = new ArrayRealVector(ws);

        }
        return weightingVectors;
    }

    public RealVector get(int i) {
        return weightingVecs[i].copy();
    }

}
