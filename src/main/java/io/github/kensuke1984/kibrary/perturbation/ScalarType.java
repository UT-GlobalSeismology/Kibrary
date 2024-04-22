package io.github.kensuke1984.kibrary.perturbation;

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
    KERNEL_Z("KernelZ"),
    KERNEL_R("KernelR"),
    KERNEL_T("KernelT"),
    PERCENT_DIFFERENCE("PercentDifference"),
    PERCENT_RATIO("PercentRatio"),
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

    public String toNaturalString() {
        return naturalString;
    }

}
