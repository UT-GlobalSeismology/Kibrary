package io.github.kensuke1984.kibrary.elastic;

import io.github.kensuke1984.kibrary.util.spc.PartialType;

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
    Vp(1), Vs(2), Vb(3),
    LAMBDA(4), MU(5), LAMBDA2MU(6), KAPPA(7),
    // TI
    Vpv(8), Vph(9), Vsv(10), Vsh(11), ETA(12),
    A(13), C(14), F(15), L(16), N(17), XI(18),
    // Q
    Qmu(19), Qkappa(20),
    // aniso
    C11(21), C12(22), C13(23), C14(24), C15(25), C16(26), C22(27), C23(28), C24(29), C25(30), C26(31), C33(32), C34(33), C35(34), C36(35), C44(36), C45(37), C46(38), C55(39), C56(40), C66(41);

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

    public static boolean isAnisotropicModulus(VariableType type) {
        switch (type){
        case C11:
        case C12:
        case C13:
        case C14:
        case C15:
        case C16:
        case C22:
        case C23:
        case C24:
        case C25:
        case C26:
        case C33:
        case C34:
        case C35:
        case C36:
        case C44:
        case C45:
        case C46:
        case C55:
        case C56:
        case C66:
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
        case LAMBDA2MU: return LAMBDA2MU;
        case KAPPA: return KAPPA;
	case C11: return C11;
        case C12: return C12;
        case C13: return C13;
        case C14: return C14;
        case C15: return C15;
        case C16: return C16;
        case C22: return C22;
        case C23: return C23;
        case C24: return C24;
        case C25: return C25;
        case C26: return C26;
        case C33: return C33;
        case C34: return C34;
        case C35: return C35;
        case C36: return C36;
        case C44: return C44;
        case C45: return C45;
        case C46: return C46;
        case C55: return C55;
        case C56: return C56;
        case C66: return C66;

        default:
            throw new IllegalArgumentException("Illegal partial type");
        }
    }

}
