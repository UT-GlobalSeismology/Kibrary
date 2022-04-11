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
    private final PolynomialStructure oneDStructure;
    private final ElasticMedium medium;

    PerturbationVoxel(FullPosition position, PolynomialStructure oneDStructure) {
        this.position = position;
        this.oneDStructure = oneDStructure;
        this.medium = new ElasticMedium();
    }

    void setDelta(ParameterType type, double perturbation) {
        double radius = position.getR();
        double absolute = oneDStructure.getAtRadius(type, radius) + perturbation;
        medium.set(type, absolute);
    }

    void setDefaultIfUndefined(ParameterType type) {
        if (!medium.isDefined(type)) {
            double radius = position.getR();
            double def = oneDStructure.getAtRadius(type, radius);
            medium.set(type, def);
        }
    }

    double getDelta(ParameterType type) {
        double radius = position.getR();
        return medium.get(type) - oneDStructure.getAtRadius(type, radius);
    }

    double getAbsolute(ParameterType type) {
        return medium.get(type);
    }

    double getPercent(ParameterType type) {
        double radius = position.getR();
        return (medium.get(type) / oneDStructure.getAtRadius(type, radius) - 1.) * 100;
    }

    FullPosition getPosition() {
        return position;
    }
}
