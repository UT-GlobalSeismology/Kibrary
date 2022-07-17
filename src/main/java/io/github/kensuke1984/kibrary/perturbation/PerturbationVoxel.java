package io.github.kensuke1984.kibrary.perturbation;

import io.github.kensuke1984.kibrary.elastic.ElasticMedium;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
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
