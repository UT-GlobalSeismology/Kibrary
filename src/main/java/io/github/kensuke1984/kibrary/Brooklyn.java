package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import io.github.kensuke1984.kibrary.abandon.LobbyCleanup;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.entrance.DataAligner;
import io.github.kensuke1984.kibrary.entrance.DataTransfer;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.timewindow.TimewindowSubtract;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalogUpdate;
import io.github.kensuke1984.kibrary.visual.AzimuthHistogram;
import io.github.kensuke1984.kibrary.visual.ColorBinInformationFile;
import io.github.kensuke1984.kibrary.visual.DistanceHistogram;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterSetter;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;
import io.github.kensuke1984.kibrary.waveform.VarianceComputer;

/**
 * An enum where all {@link Summon}able classes in Kibrary should be assigned to.
 * They are classes that can be run with just a few arguments.
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
    PolynomialStructureFile(3, PolynomialStructureFile.class),
    DataEntryListFile(4, DataEntryListFile.class),
    EventListFile(5, EventListFile.class),
    ObserverListFile(6, ObserverListFile.class),
    ColorBinInformationFile(9, ColorBinInformationFile.class),
    // Data download 10
    DataTransfer(12, DataTransfer.class),
    DataAligner(13, DataAligner.class),
    LobbyCleanup(19, LobbyCleanup.class),
    // Synthetic  20
    // Filtered 30
    TimewindowDataFile(31, TimewindowDataFile.class),
    StaticCorrectionDataFile(32, StaticCorrectionDataFile.class),
    TimewindowSubtract(35, TimewindowSubtract.class),
    // Compiled 40
    BasicIDFile(40, BasicIDFile.class),
    VarianceComputer(45, VarianceComputer.class),
    DistanceHistogram(48, DistanceHistogram.class),
    AzimuthHistogram(49, AzimuthHistogram.class),
    // Voxel 50
    UnknownParameterSetter(51, UnknownParameterSetter.class),
    // Partial 50
    PartialIDFile(55, PartialIDFile.class),
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
