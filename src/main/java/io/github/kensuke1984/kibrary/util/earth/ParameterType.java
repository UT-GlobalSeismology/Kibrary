package io.github.kensuke1984.kibrary.util.earth;

import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Parameters of elastic medium.
 *
 * @author otsuru
 * @since 2022//4/11
 */
public enum ParameterType {
    RHO(0),
    // iso
    Vp(1), Vs(2), Vb(3),
    LAMBDA(4), MU(5), LAMBDA2MU(6), KAPPA(7),
    // TI
    Vpv(8), Vph(9), Vsv(10), Vsh(11), ETA(12),
    A(13), C(14), F(15), L(16), N(17), XI(18),
    // Q
    Qmu(19), Qkappa(20);

    private int value;

    ParameterType(int n) {
        value = n;
    }

    public int valueOf() {
        return value;
    }

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
        case LAMBDA2MU:
        case LAMBDA:
        case MU:
        case KAPPA:
            return true;
        default:
            return false;
        }
    }

    public static boolean isTIVelocity(ParameterType type) {
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

    public static boolean isTIModulus(ParameterType type) {
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

    public static ParameterType of(PartialType type) {
        switch (type) {
        case RHO: return RHO;
        case A: return A;
        case C: return C;
        case F: return F;
        case L: return L;
        case N: return N;
        case LAMBDA: return LAMBDA;
        case MU: return MU;
        case LAMBDA2MU: return LAMBDA2MU;
        case KAPPA: return KAPPA;

        default:
            throw new IllegalArgumentException("Illegal partial type");
        }
    }

}
