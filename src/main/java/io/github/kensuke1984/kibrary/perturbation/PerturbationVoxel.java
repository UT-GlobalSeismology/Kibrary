package io.github.kensuke1984.kibrary.perturbation;

import io.github.kensuke1984.kibrary.elastic.ElasticMedium;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * A voxel of which its parameters are perturbed.
 * Values of a reference medium and the perturbed medium are stored.
 *
 * <p>
 * CAUTION, member fields in this class are <b>NOT IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationVoxel {

    private final FullPosition position;
    private final double volume;
    private final ElasticMedium referenceMedium;
    private final ElasticMedium perturbedMedium;

    public PerturbationVoxel(FullPosition position, double volume, PolynomialStructure oneDStructure) {
        this.position = position;
        this.volume = volume;
        this.referenceMedium = oneDStructure.mediumAt(position.getR());
        this.perturbedMedium = new ElasticMedium();
    }

    private PerturbationVoxel(FullPosition position, double volume, ElasticMedium referenceMedium, ElasticMedium perturbedMedium) {
        this.position = position;
        this.volume = volume;
        this.referenceMedium = referenceMedium.clone();
        this.perturbedMedium = perturbedMedium.clone();
    }

    /**
     * Set the value of a certain variable in the specified scalar type.
     * @param variable ({@link VariableType}) Variable to set value.
     * @param scalarType ({@link ScalarType}) Scalar type to set value in.
     * @param value (double) Value to set.
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public void setValue(VariableType variable, ScalarType scalarType, double value) {
        double absolute;
        switch (scalarType) {
        case ABSOLUTE: absolute = value; break;
        case DELTA: absolute = referenceMedium.get(variable) + value; break;
        case PERCENT: absolute = referenceMedium.get(variable) * (1.0 + value / 100.0);
        default: throw new IllegalArgumentException("Unsupported scalar type: " + scalarType);
        }
        perturbedMedium.set(variable, absolute);
    }

    /**
     * Set a certain variable to its value in the reference medium if it is not defined yet.
     * @param variable ({@link VariableType}) Variable to set.
     */
    public void setDefaultIfUndefined(VariableType variable) {
        if (!perturbedMedium.isDefined(variable)) {
            double def = referenceMedium.get(variable);
            perturbedMedium.set(variable, def);
        }
    }

    /**
     * Create a new perturbation voxel with the same absolute parameter values but with a different reference medium.
     * @param oneDStructure ({@link PolynomialStructure}) New reference structure.
     * @return ({@link PerturbationVoxel}) New perturbation voxel with the given reference structure.
     */
    public PerturbationVoxel withReferenceStructureAs(PolynomialStructure oneDStructure) {
        ElasticMedium newInitialMedium = oneDStructure.mediumAt(position.getR());
        return new PerturbationVoxel(position, volume, newInitialMedium, perturbedMedium);
    }

    /**
     * Get value of a certain variable in the specified scalar type.
     * @param variable ({@link VariableType}) Variable to get value for.
     * @param scalarType ({@link ScalarType}) Scalar type to get value in.
     * @return (double) Value.
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public double getValue(VariableType variable, ScalarType scalarType) {
        switch (scalarType) {
        case ABSOLUTE: return perturbedMedium.get(variable);
        case DELTA: return perturbedMedium.get(variable) - referenceMedium.get(variable);
        case PERCENT: return (perturbedMedium.get(variable) / referenceMedium.get(variable) - 1.0) * 100.0;
        default: throw new IllegalArgumentException("Unsupported scalar type: " + scalarType);
        }
    }

    public FullPosition getPosition() {
        return position;
    }

    public double getVolume() {
        return volume;
    }

}
