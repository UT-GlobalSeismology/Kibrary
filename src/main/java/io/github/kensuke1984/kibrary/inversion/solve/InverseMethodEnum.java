package io.github.kensuke1984.kibrary.inversion.solve;

/**
 * Names of methods for inversion, such as conjugate gradient method, singular value decomposition, etc.
 *
 * @author Kensuke Konishi
 * @since version 0.0.3
 */
public enum InverseMethodEnum {
    SINGULAR_VALUE_DECOMPOSITION, CONJUGATE_GRADIENT, LEAST_SQUARES_METHOD,
    NON_NEGATIVE_LEAST_SQUARES_METHOD, BICONJUGATE_GRADIENT_STABILIZED_METHOD,
    FAST_CONJUGATE_GRADIENT, FAST_CONJUGATE_GRADIENT_DAMPED, NONLINEAR_CONJUGATE_GRADIENT,
    CONSTRAINED_CONJUGATE_GRADIENT;

    public static InverseMethodEnum of(String simple) {
        switch (simple.toUpperCase()) {
        case "SVD":
            return SINGULAR_VALUE_DECOMPOSITION;
        case "CG":
            return CONJUGATE_GRADIENT;
        case "LSM":
            return LEAST_SQUARES_METHOD;
        case "NNLS":
            return NON_NEGATIVE_LEAST_SQUARES_METHOD;
        case "BCGS":
            return BICONJUGATE_GRADIENT_STABILIZED_METHOD;
        case "FCG":
            return FAST_CONJUGATE_GRADIENT;
        case "FCGD":
            return FAST_CONJUGATE_GRADIENT_DAMPED;
        case "NCG":
            return NONLINEAR_CONJUGATE_GRADIENT;
        case "CCG":
            return CONSTRAINED_CONJUGATE_GRADIENT;
        default:
            throw new IllegalArgumentException("Invalid name for InverseMethod.");
        }
    }

    public String simpleName() {
        switch (this) {
        case SINGULAR_VALUE_DECOMPOSITION:
            return "SVD";
        case CONJUGATE_GRADIENT:
            return "CG";
        case LEAST_SQUARES_METHOD:
            return "LSM";
        case NON_NEGATIVE_LEAST_SQUARES_METHOD:
            return "NNLS";
        case BICONJUGATE_GRADIENT_STABILIZED_METHOD:
            return "BCGS";
        // TODO: implements FCG FCGD NLCG and CCG to work with the workflow. Now works only if CG
        case FAST_CONJUGATE_GRADIENT:
            return "FCG";
        case FAST_CONJUGATE_GRADIENT_DAMPED:
            return "FCGD";
        case NONLINEAR_CONJUGATE_GRADIENT:
            return "NLCG";
        case CONSTRAINED_CONJUGATE_GRADIENT:
            return "CCG";
        default:
            throw new UnsupportedOperationException("Unsupported inversion method.");
        }
    }

}
