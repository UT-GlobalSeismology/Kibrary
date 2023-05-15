package io.github.kensuke1984.kibrary.elastic;

import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Parameters of elastic medium.
 *
 * @author otsuru
 * @since 2022/4/11
 * @version 2022/7/17 renamed & moved from util.earth.ParameterType to elastic.VariableType
 */

public enum VariableType {
    RHO(0),
    // iso
    Vp(1), Vs(2), Vb(3), R(22),
    LAMBDA(4), MU(5), LAMBDA2MU(6), KAPPA(7),
    // TI
    Vpv(8), Vph(9), Vsv(10), Vsh(11), ETA(12),
    A(13), C(14), F(15), L(16), N(17), XI(18),
    // Q
    Qmu(19), Qkappa(20),
    // others
    TIME(21);

    private int value;

    VariableType(int n) {
        value = n;
    }

    public int valueOf() {
        return value;
    }

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
        case LAMBDA2MU:
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

    // TODO hmm...
    public SPCType to1DSpcType() {
        switch (this) {
        case RHO:
            return SPCType.RHO1D;
        case LAMBDA:
            return SPCType.LAMBDA1D;
        case MU:
            return SPCType.MU1D;
        case A:
            return SPCType.A1D;
        case C:
            return SPCType.C1D;
        case F:
            return SPCType.F1D;
        case L:
            return SPCType.L1D;
        case N:
            return SPCType.N1D;
        default:
            throw new RuntimeException("unexpected");
        }

    }

}
