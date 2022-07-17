package io.github.kensuke1984.kibrary.model;

import io.github.kensuke1984.kibrary.elasticparameter.ElasticMedium;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * A voxel of which its parameters are perturbed.
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

    public void setDelta(ParameterType type, double perturbation) {
        double absolute = initialMedium.get(type) + perturbation;
        perturbedMedium.set(type, absolute);
    }

    public void setPercent(ParameterType type, double percent) {
        double absolute = initialMedium.get(type) * (1. + percent);
        perturbedMedium.set(type, absolute);
    }

    public void setDefaultIfUndefined(ParameterType type) {
        if (!perturbedMedium.isDefined(type)) {
            double def = initialMedium.get(type);
            perturbedMedium.set(type, def);
        }
    }

    public double getDelta(ParameterType type) {
        return perturbedMedium.get(type) - initialMedium.get(type);
    }

    public double getAbsolute(ParameterType type) {
        return perturbedMedium.get(type);
    }

    public double getPercent(ParameterType type) {
        return (perturbedMedium.get(type) / initialMedium.get(type) - 1.) * 100;
    }

    public FullPosition getPosition() {
        return position;
    }

    public double getVolume() {
        return volume;
    }
}
