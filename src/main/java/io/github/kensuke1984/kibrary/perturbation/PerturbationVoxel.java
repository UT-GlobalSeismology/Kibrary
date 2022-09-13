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
    private final ElasticMedium initialMedium;
    private final ElasticMedium perturbedMedium;

    public PerturbationVoxel(FullPosition position, double volume, PolynomialStructure oneDStructure) {
        this.position = position;
        this.volume = volume;
        this.initialMedium = oneDStructure.mediumAt(position.getR());
        this.perturbedMedium = new ElasticMedium();
    }


    private PerturbationVoxel(FullPosition position, double volume, ElasticMedium initialMedium,
            ElasticMedium perturbedMedium) {
        this.position = position;
        this.volume = volume;
        this.initialMedium = initialMedium.clone();
        this.perturbedMedium = perturbedMedium.clone();
    }

    public void setDelta(VariableType type, double perturbation) {
        double absolute = initialMedium.get(type) + perturbation;
        perturbedMedium.set(type, absolute);
    }

    public void setPercent(VariableType type, double percent) {
        double absolute = initialMedium.get(type) * (1. + percent / 100);
        perturbedMedium.set(type, absolute);
    }

    public void setDefaultIfUndefined(VariableType type) {
        if (!perturbedMedium.isDefined(type)) {
            double def = initialMedium.get(type);
            perturbedMedium.set(type, def);
        }
    }

    /**
     * Create a new perturbation voxel with the same absolute parameter values but with a different initial medium.
     * @param oneDStructure
     * @return
     */
    public PerturbationVoxel withInitialStructureAs(PolynomialStructure oneDStructure) {
        ElasticMedium newInitialMedium = oneDStructure.mediumAt(position.getR());
        return new PerturbationVoxel(position, volume, newInitialMedium, perturbedMedium);
    }

    public double getDelta(VariableType type) {
        return perturbedMedium.get(type) - initialMedium.get(type);
    }

    public double getAbsolute(VariableType type) {
        return perturbedMedium.get(type);
    }

    public double getPercent(VariableType type) {
        return (perturbedMedium.get(type) / initialMedium.get(type) - 1.) * 100;
    }

    public FullPosition getPosition() {
        return position;
    }

    public double getVolume() {
        return volume;
    }
}
