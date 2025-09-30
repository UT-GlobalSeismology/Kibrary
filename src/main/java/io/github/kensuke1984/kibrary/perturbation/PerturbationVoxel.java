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
    private final double size;
    private final ElasticMedium referenceMedium;
    private final ElasticMedium perturbedMedium;

    public PerturbationVoxel(FullPosition position, double size, PolynomialStructure oneDStructure) {
        this.position = position;
        this.size = size;
        this.referenceMedium = oneDStructure.mediumAt(position.getR());
        this.perturbedMedium = new ElasticMedium();
    }


    private PerturbationVoxel(FullPosition position, double size, ElasticMedium referenceMedium, ElasticMedium perturbedMedium) {
        this.position = position;
        this.size = size;
        this.referenceMedium = referenceMedium.clone();
        this.perturbedMedium = perturbedMedium.clone();
    }

    public void setPerturbation(VariableType type, double value, String valueFormat) {
        switch (valueFormat) {
        case "difference":
            setDelta(type, value);
            break;
        case "percent":
            setPercent(type, value);
            break;
        case "absolute":
            setAbsolute(type, value);
            break;
        default:
            throw new IllegalArgumentException("valueFormat must be selected from difference, percent, & absolute");
        }
    }

    public void setDelta(VariableType type, double perturbation) {
        double absolute = referenceMedium.get(type) + perturbation;
        perturbedMedium.set(type, absolute);
    }

    public void setPercent(VariableType type, double percent) {
        double absolute = referenceMedium.get(type) * (1. + percent / 100);
        perturbedMedium.set(type, absolute);
    }

    public void setAbsolute(VariableType type, double absolute) {
        perturbedMedium.set(type, absolute);
    }

    public void setDefaultIfUndefined(VariableType type) {
        if (!perturbedMedium.isDefined(type)) {
            double def = referenceMedium.get(type);
            perturbedMedium.set(type, def);
        }
    }

    /**
     * Create a new perturbation voxel with the same absolute parameter values but with a different initial medium.
     * @param oneDStructure
     * @return
     */
    public PerturbationVoxel withReferenceStructureAs(PolynomialStructure oneDStructure) {
        ElasticMedium newInitialMedium = oneDStructure.mediumAt(position.getR());
        return new PerturbationVoxel(position, size, newInitialMedium, perturbedMedium);
    }

    public double getDelta(VariableType type) {
        return perturbedMedium.get(type) - referenceMedium.get(type);
    }

    public double getAbsolute(VariableType type) {
        return perturbedMedium.get(type);
    }

    public double getPercent(VariableType type) {
        return (perturbedMedium.get(type) / referenceMedium.get(type) - 1.) * 100;
    }

    public FullPosition getPosition() {
        return position;
    }

    public double getSize() {
        return size;
    }
}
