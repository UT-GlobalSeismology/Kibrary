package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;

/**
 * Partial types
 * <p>
 * 3D: A, C, F, L, N, LAMBDA, MU, Q<br>
 * 1D: PAR* (PAR1:LAMBDA PAR2:MU)<br>
 * TIME
 *
 * @author Kensuke Konishi
 * @version 0.0.3.1
 * @author anselme add several partial types
 */
public enum PartialType {

    A(0), C(1), F(2), L(3), N(4), MU(5), LAMBDA(6), Q(7), TIME_SOURCE(8), TIME_RECEIVER(9), PAR1(10), PAR2(11), PARA(12), PARC(13), PARF(14),
    PARL(15), PARN(16), PARQ(17), G1(18), G2(19), G3(20), G4(21), G5(22), G6(23), PAR0(24), PAR00(25), KAPPA(26), LAMBDA2MU(27), R(28), Vs(29),
    PARVS(30), PARVSIM(31), PARVP(32), PARM(33), PARG(34), RHO(35),
    C11(36), C12(37), C13(38), C14(39), C15(40), C16(41), C22(42), C23(43), C24(44), C25(45), C26(46), C33(47), C34(48), C35(49), C36(50), C44(51), C45(52), C46(53), C55(54), C56(55), C66(56);

    private int value;

    PartialType(int n) {
        value = n;
    }

    public static PartialType getType(int n) {
        return Arrays.stream(PartialType.values()).filter(type -> type.value == n).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input n " + n + " is invalid."));
    }

    public boolean is3D() {
        return 8 < value || value >= 26;
    }

    public boolean isTimePartial() {
        return value == 8 || value == 9;
    }

    public boolean isDensity() {
        return value == 35;
    }

    public int getValue() {
        return value;
    }

    /**
     * 変微分係数波形を計算するときのCijklの重み A C F L N Mu lambda
     *
     * @return weighting for {@link PartialType} to compute partials
     */
    public WeightingFactor getWeightingFactor() {
        switch (this) {
        case A:
            return WeightingFactor.A;
        case C:
            return WeightingFactor.C;
        case F:
            return WeightingFactor.F;
        case L:
            return WeightingFactor.L;
        case N:
            return WeightingFactor.N;
        case MU:
            return WeightingFactor.MU;
        case LAMBDA:
            return WeightingFactor.LAMBDA;
        case KAPPA:
            return WeightingFactor.KAPPA;
        case LAMBDA2MU:
            return WeightingFactor.LAMBDA2MU;
        case C11:
            return WeightingFactor.C11;
        case C12:
            return WeightingFactor.C12;
        case C13:
            return WeightingFactor.C13;
        case C14:
            return WeightingFactor.C14;
        case C15:
            return WeightingFactor.C15;
        case C16:
            return WeightingFactor.C16;
        case C22:
            return WeightingFactor.C22;
        case C23:
            return WeightingFactor.C23;
        case C24:
            return WeightingFactor.C24;
        case C25:
            return WeightingFactor.C25;
        case C26:
            return WeightingFactor.C26;
        case C33:
            return WeightingFactor.C33;
        case C34:
            return WeightingFactor.C34;
        case C35:
            return WeightingFactor.C35;
        case C36:
            return WeightingFactor.C36;
        case C44:
            return WeightingFactor.C44;
        case C45:
            return WeightingFactor.C45;
        case C46:
            return WeightingFactor.C46;
        case C55:
            return WeightingFactor.C55;
        case C56:
            return WeightingFactor.C56;
        case C66:
            return WeightingFactor.C66;
        default:
            throw new RuntimeException("Unexpected happens");
        }
    }

    // TODO hmm...
    public SPCType toSpcFileType() {
        switch (this) {
        case A:
        case PARA:
            return SPCType.PARA;
        case LAMBDA:
        case PAR1:
            return SPCType.PAR1;
        case C:
        case PARC:
            return SPCType.PARC;
        case MU:
        case PAR2:
            return SPCType.PAR2;
        case F:
            return SPCType.PARF;
        case PARF:
            return SPCType.PAR3;
        case L:
        case PARL:
            return SPCType.PAR4;
        case N:
        case PARN:
            return SPCType.PAR5;
        case PARQ:
        case Q:
            return SPCType.PARQ;
        case G1:
            return SPCType.G1;
        case G2:
            return SPCType.G2;
        case G3:
            return SPCType.G3;
        case G4:
            return SPCType.G4;
        case G5:
            return SPCType.G5;
        case G6:
            return SPCType.G6;
        case PAR0:
            return SPCType.PAR0;
        case PARVS:
            return SPCType.PARVS;
        case PAR00:
        case PARM:
        case PARVP:
        case PARG:
            throw new RuntimeException("Not SpcFileType");
        default:
            throw new RuntimeException("unexpected");
        }

    }

}
