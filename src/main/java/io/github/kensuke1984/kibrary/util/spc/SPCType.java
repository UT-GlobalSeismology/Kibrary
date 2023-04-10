package io.github.kensuke1984.kibrary.util.spc;

/**
 * SpcFileの中身 par? (?:0 - 5, A, C, F, L, N, Q(Q<sup>-1</sup>)), PB, PF, SYNTHETIC
 *
 * @author Kensuke Konishi
 * @version 0.0.3.0.1
 * @author anselme add several partial types
 * @author rei add several partial types
 */
public enum SPCType {
    PAR0, PAR1, PAR2, PARA, PARC, PARF, PARL, PARN, PARQ, PARVS,
    PF, PFSHCAT, PFPSVCAT, PB, PBSHCAT, PBPSVCAT, UF, UB,
    SYNTHETIC;
}
