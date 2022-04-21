package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.entrance.DataAligner;
import io.github.kensuke1984.kibrary.entrance.DataTransfer;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.data.EventInformationFile;
import io.github.kensuke1984.kibrary.util.data.ObserverInformationFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalogUpdate;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * An enum where all {@link Summon}able classes in Kibrary should be assigned to.
 * They are classes that can be run with only a few arguments.
 * <p>
 * Classes assigned here must contain methods usage() and run().
 * If no arguments are needed, usage() should return null.
 * <p>
 * The value name set to this enum must be the same as its corresponding class name.
 *
 * @author otsuru
 * @since 2022/4/4
 */
enum Brooklyn {
    // Environment & General 00
    About(0, About.class),
    Environment(1, Environment.class),
    GlobalCMTCatalogUpdate(2, GlobalCMTCatalogUpdate.class),
    EventInformationFile(5, EventInformationFile.class),
    ObserverInformationFile(6, ObserverInformationFile.class),
    // Data download 10
    DataTransfer(12, DataTransfer.class),
    DataAligner(13, DataAligner.class),
    // Synthetic  20
    // Filtered 30
    TimewindowDataFile(31, TimewindowDataFile.class),
    StaticCorrectionDataFile(32, StaticCorrectionDataFile.class),
    // Compiled 40
    BasicIDFile(40, BasicIDFile.class),
    // Partial 50
    // Inversion 60
    // Temporal 100
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

    static String numRange() {
        Brooklyn[] all = values();
        int min = Arrays.stream(all).mapToInt(m -> m.value).min().getAsInt();
        int max = Arrays.stream(all).mapToInt(m -> m.value).max().getAsInt();
        return min + "-" + max;
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

    Options getOptions() throws ReflectiveOperationException {
        return (Options) c.getMethod("defineOptions", (Class<?>[]) null).invoke(null, (Object[]) null);
    }

    void summon(CommandLine cmdLine) throws ReflectiveOperationException {
        c.getMethod("run", CommandLine.class).invoke(null, (Object) cmdLine);
    }

}
