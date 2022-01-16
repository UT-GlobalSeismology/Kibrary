package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.util.spc.SPC_SAC;

/**
 * An enum where all {@link Operation_new}s in Kibrary should be assigned to.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/1/7 Recreated based on the original Manhattan.
 */
enum Manhattan_new {
    // Environment 00
    // Data download 10
    // Synthetic  20
    SPC_SAC(17, SPC_SAC.class), //
    // Filtered 30
    FujiStaticCorrection(6, FujiStaticCorrection.class), //
    // Compiled 40
    // Partial 50
    // Inversion 60
    // Temporal 100
    ;

    private Class<? extends Operation_new> c;
    private int value;

    Manhattan_new(int n, Class<? extends Operation_new> c) {
        value = n;
        this.c = c;
    }

    static void printList() {
        Arrays.stream(values()).sorted().forEach(m -> System.err.println(m.value + " " + m.c.getSimpleName()));
    }

    /**
     * Returns a Manhattan given its corresponding number.
     * Note that {@link #valueOf(String)}, which returns a Manhattan given a String of its name,
     * is already defined automatically.
     *
     * @param n (int)
     * @return
     */
    static Manhattan_new valueOf(int n) {
        return Arrays.stream(values()).filter(m -> m.value == n).findAny().get();
    }

    Class<? extends Operation_new> getOperation() {
        return c;
    }

    void writeDefaultPropertiesFile() throws ReflectiveOperationException {
        c.getMethod("writeDefaultPropertiesFile", (Class<?>[]) null).invoke(null, (Object[]) null);
    }

}
