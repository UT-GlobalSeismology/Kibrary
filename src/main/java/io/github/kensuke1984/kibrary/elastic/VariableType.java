package io.github.kensuke1984.kibrary.elastic;

/**
 * Parameters of elastic medium.
 *
 * @author otsuru
 * @since 2022/4/11
 * @version 2022/7/17 renamed & moved from util.earth.ParameterType to elastic.VariableType
 */

public enum VariableType {
    RHO,
    // iso
    Vp, Vs, Vb, R,
    LAMBDA, MU, LAMBDA2MU, KAPPA,
    // TI
    Vpv, Vph, Vsv, Vsh, ETA,
    A, C, F, L, N, XI,
    // Q
    Qmu, Qkappa,
    // others
    TIME;

    public boolean isDensity() {
        if (this == RHO) return true;
        else return false;
    }

    public boolean isIsotropicVelocity() {
        switch (this) {
        case Vp:
        case Vs:
        case Vb:
            return true;
        default:
            return false;
        }
    }

    public boolean isIsotropicModulus() {
        switch (this) {
        case LAMBDA2MU:
        case LAMBDA:
        case MU:
        case KAPPA:
            return true;
        default:
            return false;
        }
    }

    public boolean isTIVelocity() {
        switch (this) {
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

    public boolean isTIModulus() {
        switch (this) {
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

    /**
     * 変微分係数波形を計算するときのCijklの重み A C F L N Mu lambda
     *
     * @return ({@link WeightingFactor}) Weighting for this variable when computing partials.
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

}
