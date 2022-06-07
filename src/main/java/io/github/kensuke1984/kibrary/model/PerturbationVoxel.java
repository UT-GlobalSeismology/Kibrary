package io.github.kensuke1984.kibrary.model;

import io.github.kensuke1984.kibrary.elasticparameter.ElasticMedium;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * @author otsuru
 * @since 2022/4/9
 */
class PerturbationVoxel {

    private final FullPosition position;
    private final ElasticMedium initialMedium;
    private final ElasticMedium perturbedMedium;

    PerturbationVoxel(FullPosition position, PolynomialStructure oneDStructure) {
        this.position = position;
        this.initialMedium = oneDStructure.getMediumAt(position.getR());
        this.perturbedMedium = new ElasticMedium();
    }

    void setDelta(ParameterType type, double perturbation) {
        double absolute = initialMedium.get(type) + perturbation;
        perturbedMedium.set(type, absolute);
    }

    void setDefaultIfUndefined(ParameterType type) {
        if (!perturbedMedium.isDefined(type)) {
            double def = initialMedium.get(type);
            perturbedMedium.set(type, def);
        }
    }

    double getDelta(ParameterType type) {
        return perturbedMedium.get(type) - initialMedium.get(type);
    }

    double getAbsolute(ParameterType type) {
        return perturbedMedium.get(type);
    }

    double getPercent(ParameterType type) {
        return (perturbedMedium.get(type) / initialMedium.get(type) - 1.) * 100;
    }

    FullPosition getPosition() {
        return position;
    }
}
