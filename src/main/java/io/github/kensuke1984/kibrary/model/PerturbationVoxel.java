package io.github.kensuke1984.kibrary.model;

import java.util.HashMap;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * @author otsuru
 * @since 2022/4/9
 */
class PerturbationVoxel {

    private final FullPosition position;
    private final PolynomialStructure oneDStructure;
    private Map<PartialType, Double> perturbationMap = new HashMap<>();

    PerturbationVoxel(FullPosition position, PolynomialStructure oneDStructure) {
        this.position = position;
        this.oneDStructure = oneDStructure;
    }

    void setDelta(PartialType type, double value) {
        perturbationMap.put(type, value);
        calculateWhenAdding(type);
    }

    double getDelta(PartialType type) {
        if (!perturbationMap.containsKey(type)) throw new IllegalArgumentException(type + " is not defined.");
        return perturbationMap.get(type);
    }

    double getAbsolute(PartialType type) {
        if (!perturbationMap.containsKey(type)) throw new IllegalArgumentException(type + " is not defined.");

        double radius = position.getR();
        return oneDStructure.getVshAt(radius) + perturbationMap.get(type);//TODO
    }

    double getPercent(PartialType type) {
        if (!perturbationMap.containsKey(type)) throw new IllegalArgumentException(type + " is not defined.");

        double radius = position.getR();
        return perturbationMap.get(type) / oneDStructure.getVshAt(radius) * 100;//TODO
    }

    FullPosition getPosition() {
        return position;
    }

    private void calculateWhenAdding(PartialType type) {
        double radius = position.getR();

        if (type == PartialType.MU) {
            double deltaMu = perturbationMap.get(PartialType.MU);
            double vsDash = Math.sqrt((oneDStructure.computeMu(radius) + deltaMu) / oneDStructure.getRhoAt(radius));
            double deltaVs = vsDash - oneDStructure.getVshAt(radius);

            perturbationMap.put(PartialType.Vs, deltaVs);


        }
    }
}
