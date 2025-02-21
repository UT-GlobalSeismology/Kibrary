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

    public boolean isNonNegative() {
        switch (this) {
        case ABSOLUTE:
        case KERNEL_Z: case KERNEL_R: case KERNEL_T:
        return true;
        default: return false;
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
     * @param slashSize (int) Size of slash.
     * @return (String) Label of scale bar to write in GMT scripts.
     */
    public static String createScaleLabel(VariableType variable, ScalarType scalarType, int slashSize) {
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
        case Vp: paramName = "V@-P@-"; break;
        case Vs: paramName = "V@-S@-"; break;
        case Vb: paramName = "V@-B@-"; break;
        case Vpv: paramName = "V@-PV@-"; break;
        case Vph: paramName = "V@-PH@-"; break;
        case Vsv: paramName = "V@-SV@-"; break;
        case Vsh: paramName = "V@-SH@-"; break;
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
        case PERCENT: return "@~d@~" + paramName + "@- @:" + slashSize + ":/@::@-" + paramName + " (%)";
        case PERCENT_DIFFERENCE: return "@~d@~" + paramName + "@- @:" + slashSize + ":/@::@-" + paramName + " Difference (%)";
        case PERCENT_RATIO: return "@~d@~" + paramName + "@- @:" + slashSize + ":/@::@-" + paramName + " Ratio";
        case KERNEL_Z: case KERNEL_R: case KERNEL_T: return "Sensitivity (normalized)";
        case PARTIAL_Z: return "@%12%\\266@%%u@-Z@-@- @:" + slashSize + ":/@::@-@%12%\\266@%%" + paramName + " (normalized)";
        case PARTIAL_R: return "@%12%\\266@%%u@-R@-@- @:" + slashSize + ":/@::@-@%12%\\266@%%" + paramName + " (normalized)";
        case PARTIAL_T: return "@%12%\\266@%%u@-T@-@- @:" + slashSize + ":/@::@-@%12%\\266@%%" + paramName + " (normalized)";
        default: throw new IllegalArgumentException("Unsupported scalar type.");
        }
    }

    /**
     * Create label of scale bar for GMT figures using TeX, formatted to write in GMT scripts.
     * @param variable ({@link VariableType}) Variable that the figure is for.
     * @param scalarType ({@link ScalarType}) Scalar type that the figure is for.
     * @return (String) Label of scale bar to write in GMT scripts.
     */
    public static String createScaleLabel_TeX(VariableType variable, ScalarType scalarType) {
        String paramName;
        switch (variable) {
        case RHO: paramName = "\\rho"; break;
        case LAMBDA2MU: paramName = "(\\lambda + 2\\mu)"; break;
        case LAMBDA: paramName = "\\lambda"; break;
        case MU: paramName = "\\mu"; break;
        case KAPPA: paramName = "\\kappa"; break;
        case ETA: paramName = "\\eta"; break;
        case XI: paramName = "\\xi"; break;
        case Qmu: paramName = "Q_\\mu"; break;
        case Qkappa: paramName = "Q_\\kappa"; break;
        case Vp: paramName = "V_P"; break;
        case Vs: paramName = "V_S"; break;
        case Vb: paramName = "V_B"; break;
        case Vpv: paramName = "V_{PV}"; break;
        case Vph: paramName = "V_{PH}"; break;
        case Vsv: paramName = "V_{SV}"; break;
        case Vsh: paramName = "V_{SH}"; break;
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
        case ABSOLUTE: return "@[" + paramName + "@[" + (!unit.isEmpty() ? (" (" + unit + ")") : "");
        case DELTA: return "@[\\delta " + paramName + "@[" + (!unit.isEmpty() ? (" (" + unit + ")") : "");
        case PERCENT: return "@[\\delta " + paramName + " / " + paramName + " \\ \\mathrm{(\\%)}@[";
        case PERCENT_DIFFERENCE: return "@[\\delta " + paramName + " / " + paramName + " \\ \\mathrm{Difference (\\%)}@[";
        case PERCENT_RATIO: return "@[\\delta " + paramName + " / " + paramName + "@[ Ratio";
        case KERNEL_Z: case KERNEL_R: case KERNEL_T: return "Sensitivity (normalized)";
        case PARTIAL_Z: return "@[\\partial u_Z / \\partial " + paramName + "@[ (normalized)";
        case PARTIAL_R: return "@[\\partial u_R / \\partial " + paramName + "@[ (normalized)";
        case PARTIAL_T: return "@[\\partial u_T / \\partial " + paramName + "@[ (normalized)";
        default: throw new IllegalArgumentException("Unsupported scalar type.");
        }
    }

}
