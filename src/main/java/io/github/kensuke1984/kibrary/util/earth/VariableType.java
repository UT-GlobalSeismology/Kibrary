package io.github.kensuke1984.kibrary.util.earth;

import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Parameters of elastic medium.
 *
 * @author otsuru
 * @since 2022/4/11
 */
public enum VariableType {
    RHO,
    // iso
    Vp, Vs, Vb,
    LAMBDA, MU, LAMBDAplus2MU, KAPPA,
    // TI
    Vpv, Vph, Vsv, Vsh, ETA,
    A, C, F, L, N, XI,
    // Q
    Qmu, Qkappa;

    public static boolean isIsotropicVelocity(VariableType type) {
        switch (type) {
        case Vp:
        case Vs:
        case Vb:
            return true;
        default:
            return false;
        }
    }

    public static boolean isIsotropicModulus(VariableType type) {
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

    public static boolean isTIVelocity(VariableType type) {
        switch (type) {
        case Vpv:
        case Vph:
        case Vsv:
        case Vsh:
        case ETA:
            return true;
        default:
            return false;
        }
    }

    public static boolean isTIModulus(VariableType type) {
        switch (type) {
        case A:
        case C:
        case F:
        case L:
        case N:
        case XI:
            return true;
        default:
            return false;
        }
    }

    public static VariableType of(PartialType type) {
        switch (type) {
        case RHO: return RHO;
        case A: return A;
        case C: return C;
        case F: return F;
        case L: return L;
        case N: return N;
        case LAMBDA: return LAMBDA;
        case MU: return MU;

        default:
            throw new IllegalArgumentException("Illegal partial type");
        }
    }

}
