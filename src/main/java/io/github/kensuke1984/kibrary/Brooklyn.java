package io.github.kensuke1984.kibrary;

import java.util.Arrays;
import java.util.List;

import io.github.kensuke1984.kibrary.entrance.DataAligner;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalogUpdate;

/**
 * An enum where all {@link Summon}able classes in Kibrary should be assigned to.
 * They are classes that can be run with only a few arguments.
 * <p>
 * Classes assigned here must contain methods usage() and run().
 * The value name set to this enum must be the same as its corresponding class name.
 *
 * @author otsuru
 * @since 2022/4/4
 */
enum Brooklyn {
    // Environment 00
    GlobalCMTCatalogUpdate(1, GlobalCMTCatalogUpdate.class),
    // Data download 10
    DataAligner(13, DataAligner.class), //
    ;

    private Class<?> c;
    private int value;

    Brooklyn(int n, Class<?> c) {
        value = n;
        this.c = c;
    }

    static void printList() {
        Arrays.stream(values()).sorted().forEach(m -> System.out.println(m.value + " " + m.c.getSimpleName()));
    }

    /**
     * Returns a Brooklyn given its corresponding number.
     * Note that {@link #valueOf(String)}, which returns a Brooklyn given a String of its name,
     * is already defined automatically.
     *
     * @param n (int)
     * @return
     */
    static Brooklyn valueOf(int n) {
        return Arrays.stream(values()).filter(m -> m.value == n).findAny().get();
    }

    String getClassName() {
        return c.getName();
    }

    void displayUsage() throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<String> usageList = (List<String>) c.getMethod("usage", (Class<?>[]) null).invoke(null, (Object[]) null);
        usageList.forEach(System.out::println);
    }

    void summon(String[] args) throws ReflectiveOperationException {
        c.getMethod("run", String[].class).invoke(null, (Object) args);
    }

}
