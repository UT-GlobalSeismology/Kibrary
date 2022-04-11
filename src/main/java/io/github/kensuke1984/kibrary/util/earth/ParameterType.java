package io.github.kensuke1984.kibrary.util.earth;

import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Parameters of elastic medium.
 *
 * @author otsuru
 * @since 2022//4/11
 */
public enum ParameterType {
    RHO,
    // iso
    Vp, Vs, Vb,
    LAMBDA, MU, LAMBDAplus2MU, KAPPA,
    // TI
    Vpv, Vph, Vsv, Vsh, ETA,
    A, C, F, L, N, XI,
    // Q
    Qmu, Qkappa;

    public static boolean isIsotropicVelocity(ParameterType type) {
        switch (type) {
        case Vp:
        case Vs:
        case Vb:
            return true;
        default:
            return false;
        }
    }

    public static boolean isIsotropicModulus(ParameterType type) {
        switch (type) {
        case LAMBDAplus2MU:
        case LAMBDA:
        case MU:
        case KAPPA:
            return true;
        default:
            return false;
        }
    }

    public static ParameterType of(PartialType type) {
        switch (type) {
        default:
            throw new IllegalArgumentException("Illegal partial type");
        }
    }

}
