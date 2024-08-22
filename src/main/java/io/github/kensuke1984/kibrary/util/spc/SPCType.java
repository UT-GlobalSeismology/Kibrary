package io.github.kensuke1984.kibrary.util.spc;

/**
 * Types of {@link SPCFile}.
 *
 * @author Kensuke Konishi
 * @since version 0.0.3.0.1
 */
public enum SPCType {
    RHO1D, LAMBDA1D, MU1D, A1D, C1D, F1D, L1D, N1D, VS1D, Q1D,
    PF, PFSHCAT, PFPSVCAT, PB, PBSHCAT, PBPSVCAT, UF, UB,
    SYNTHETIC;

    static SPCType ofNumber(int number) {
        switch(number) {
        case 3: // normal synthetic
            return SYNTHETIC;
        case 4:// forward propagation dislocation field. 4 is an identifier. The actual number of component is 3 (3 non-zero component).
            return SPCType.UF;
        case 5:// back propagation dialocation filed. 5 is an identifier. The actual number of component is 9 (9 non-zero component).
            return SPCType.UB;
        case 7: // back propagation PSV catalog. 7 is an identifier. The actual number of component is 27 (27 non-zero component).
            return PBPSVCAT;
        case 8: // back propagation SH catalog. 8 is an identifier. The actual number of component is 27 (18 non-zero component).
            return PBSHCAT;
        case 9: // forward propagation (strain field)
            return PF;
        case 10: // forward propagation SH catalog. 10 is an identifier. The actual number of component is 9.
            return PFSHCAT;
        case 12: // forward propagation SH catalog. 10 is an identifier. The actual number of component is 9.
            return PFPSVCAT;
        case 27: // back propagation (strain field)
            return PB;
        default:
            throw new RuntimeException("component can be only 3(synthetic), 4(uf), 5(ub), 7(bppsvcat), 8(bpshcat), "
                    + "9(fp), 10(fpshcat), 12(fppsvcat), or 27(bp) right now");
        }
    }

    int getNComponent() {
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
