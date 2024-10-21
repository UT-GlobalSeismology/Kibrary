package io.github.kensuke1984.kibrary.perturbation;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Type of scalar values.
 *
 * @author otsuru
 * @since 2024/4/22
 */
public enum ScalarType {
    ABSOLUTE("Absolute"),
    DELTA("Delta"),
    PERCENT("Percent"),
    PERCENT_DIFFERENCE("PercentDifference"),
    PERCENT_RATIO("PercentRatio"),
    KERNEL_Z("KernelZ"),
    KERNEL_R("KernelR"),
    KERNEL_T("KernelT"),
    PARTIAL_Z("PartialZ"),
    PARTIAL_R("PartialR"),
    PARTIAL_T("PartialT"),
    ;

    private String naturalString;

    private ScalarType(String naturalString) {
        this.naturalString = naturalString;
    }

    public static ScalarType kernelOf(SACComponent component) {
        switch (component) {
        case Z : return KERNEL_Z;
        case R : return KERNEL_R;
        case T : return KERNEL_T;
        default: throw new IllegalArgumentException("Invalid component: " + component);
        }
    }

    public static ScalarType partialOf(SACComponent component) {
        switch (component) {
        case Z : return PARTIAL_Z;
        case R : return PARTIAL_R;
        case T : return PARTIAL_T;
        default: throw new IllegalArgumentException("Invalid component: " + component);
        }
    }

    /**
     * Get scalar type name in UpperCamelCase.
     * @return (String) Scalar type name in UpperCamelCase.
     */
    public String toNaturalString() {
        return naturalString;
    }

    /**
     * Create label of scale bar for GMT figures, formatted to write in GMT scripts.
     * @param variable ({@link VariableType}) Variable that the figure is for.
     * @param scalarType ({@link ScalarType}) Scalar type that the figure is for.
     * @return (String) Label of scale bar to write in GMT scripts.
     */
    public static String createScaleLabel(VariableType variable, ScalarType scalarType) {
        String paramName;
        switch (variable) {
        case RHO: paramName = "@~r@~"; break;
        case LAMBDA2MU: paramName = "(@~l@~+2@~m@~)"; break;
        case LAMBDA: paramName = "@~l@~"; break;
        case MU: paramName = "@~m@~"; break;
        case KAPPA: paramName = "@~k@~"; break;
        case ETA: paramName = "@~h@~"; break;
        case XI: paramName = "@~x@~"; break;
        case Qmu: paramName = "Q@-@~m@~@-"; break;
        case Qkappa: paramName = "Q@-@~k@~@-"; break;
        default: paramName = variable.toString();
        }

        String unit;
        switch (variable) {
        case RHO: unit = "g/cm@+3@+"; break;
        case Vp: case Vs: case Vb: case Vpv: case Vph: case Vsv: case Vsh: unit = "km/s"; break;
        case R: case ETA: case XI: unit = ""; break;
        default: unit = "GPa"; break;
        }

        switch (scalarType) {
        case ABSOLUTE: return paramName + (!unit.isEmpty() ? (" (" + unit + ")") : "");
        case DELTA: return "@~d@~" + paramName + (!unit.isEmpty() ? (" (" + unit + ")") : "");
        case PERCENT: return "@~d@~" + paramName + "/" + paramName + " (%)";
        case PERCENT_DIFFERENCE: return "@~d@~" + paramName + "/" + paramName + " Difference (%)";
        case PERCENT_RATIO: return "@~d@~" + paramName + "/" + paramName + " Ratio";
        case KERNEL_Z: case KERNEL_R: case KERNEL_T: return "Sensitivity (normalized)";
        case PARTIAL_Z: return "@%12%\\266@%%u@-Z@-/@%12%\\266@%%" + paramName + " (normalized)";
        case PARTIAL_R: return "@%12%\\266@%%u@-R@-/@%12%\\266@%%" + paramName + " (normalized)";
        case PARTIAL_T: return "@%12%\\266@%%u@-T@-/@%12%\\266@%%" + paramName + " (normalized)";
        default: throw new IllegalArgumentException("Unsupported scalar type.");
        }
    }

}
