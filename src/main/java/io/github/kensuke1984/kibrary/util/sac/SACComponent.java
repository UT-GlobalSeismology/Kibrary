package io.github.kensuke1984.kibrary.util.sac;


import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

/**
 * Components of SAC<br>
 * Z(1), R(2), T(3)
 *
 * CAUTION: values must be in -128~127 (range of byte)!! (The value is written into files as byte; see {@link WaveformDataWriter}.)
 *
 * @author Kensuke Konishi
 * @version 0.0.4
 */
public enum SACComponent {
    Z(1), R(2), T(3);

    private int value;

    SACComponent(int i) {
        value = i;
    }

    /**
     * @param n index 1, 2, 3
     * @return Z(1) R(2) T(3)
     * @throws IllegalArgumentException if the input n is not 1,2,3
     */
    public static SACComponent getComponent(int n) {
        switch (n) {
            case 1:
                return Z;
            case 2:
                return R;
            case 3:
                return T;
            default:
                throw new IllegalArgumentException("Invalid component! Components are Z(1) R(2) T(3)");
        }
    }

    /**
     * @param sacHeaderData must contain KCMPNM (vertical, radial or trnsvers)
     * @return SACComponent of the input sacHeaderData
     */
    public static SACComponent of(SACHeaderAccess sacHeaderData) {
        return sacHeaderData.getComponent();
    }

    /**
     * @return 1(Z), 2(R), 3(T)
     */
    public int valueOf() {
        return value;
    }

    /**
     * @param components string for components (Z, R, T)
     * @return set of the components in the string
     */
    public static Set<SACComponent> componentSetOf(String components) {
        if (components.matches(".*[^ZRT].*")) throw new IllegalArgumentException("Option -c can only have Z, R and T.");
        Set<SACComponent> set = new HashSet<>();
        if (components.contains("Z")) set.add(Z);
        if (components.contains("R")) set.add(R);
        if (components.contains("T")) set.add(T);
        return set;
    }

}
