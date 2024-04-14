package io.github.kensuke1984.kibrary.util.spc;

/**
 * Types of {@link SPCFile}.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public enum SPCType {
    RHO1D, LAMBDA1D, MU1D, A1D, C1D, F1D, L1D, N1D, VS1D, Q1D,
    PF, PFSHCAT, PFPSVCAT, PB, PBSHCAT, PBPSVCAT, UF, UB,
    SYNTHETIC;

    static SPCType ofNumber(int number) {
        switch(number) {
        case 3: // Normal synthetic
            return SYNTHETIC;
        case 4: // Forward propagation dislocation field. 4 is an identifier. Holds 3 components. (3 non-zero components).
            return SPCType.UF;
        case 5: // Back propagation dislocation filed. 5 is an identifier. Holds 9 components (9 non-zero components).
            return SPCType.UB;
        case 7: // Back propagation PSV catalog. 7 is an identifier. Holds 27 components (27 non-zero components).
            return PBPSVCAT;
        case 8: // Back propagation SH catalog. 8 is an identifier. Holds 27 components (18 non-zero components).
            return PBSHCAT;
        case 9: // Forward propagation strain field.
            return PF;
        case 10: // Forward propagation SH catalog. 10 is an identifier. Holds 9 components.
            return PFSHCAT;
        case 12: // Forward propagation SH catalog. 12 is an identifier. Holds 9 components.
            return PFPSVCAT;
        case 27: // Back propagation strain field.
            return PB;
        default:
            throw new RuntimeException("SPC type can be only 3(synthetic), 4(uf), 5(ub), 7(bppsvcat), 8(bpshcat), "
                    + "9(fp), 10(fpshcat), 12(fppsvcat), or 27(bp) right now");
        }
    }

    int getNElement() {
        switch(this) {
        case PB:
        case PBSHCAT:
        case PBPSVCAT:
            return 27;
        case PF:
        case PFSHCAT:
        case PFPSVCAT:
        case UB:
            return 9;
        default:
            return 3;
        }
    }
}
