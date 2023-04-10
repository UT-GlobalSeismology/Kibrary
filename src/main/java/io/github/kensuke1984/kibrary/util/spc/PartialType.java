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

    A(0), C(1), F(2), L(3), N(4), MU(5), LAMBDA(6), Q(7), TIME_SOURCE(8), TIME_RECEIVER(9),
    PAR1(10), PAR2(11), PARA(12), PARC(13), PARF(14), PARL(15), PARN(16), PARQ(17), PAR0(24),
    KAPPA(26), LAMBDA2MU(27), R(28), Vs(29),
    PARVS(30), PARVP(32), RHO(35);

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
        default:
            throw new RuntimeException("Unexpected happens");
        }
    }

    // TODO hmm...
    public SPCType toSpcFileType() {
        switch (this) {
        case PAR0:
            return SPCType.PAR0;
        case LAMBDA:
        case PAR1:
            return SPCType.PAR1;
        case MU:
        case PAR2:
            return SPCType.PAR2;
        case A:
        case PARA:
            return SPCType.PARA;
        case C:
        case PARC:
            return SPCType.PARC;
        case F:
        case PARF:
            return SPCType.PARF;
        case L:
        case PARL:
            return SPCType.PARL;
        case N:
        case PARN:
            return SPCType.PARN;
        case PARQ:
        case Q:
            return SPCType.PARQ;
        case PARVS:
            return SPCType.PARVS;
        case PARVP:
            throw new RuntimeException("Not SpcFileType");
        default:
            throw new RuntimeException("unexpected");
        }

    }

}
