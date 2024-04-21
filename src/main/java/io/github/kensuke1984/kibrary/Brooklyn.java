package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import io.github.kensuke1984.kibrary.abandon.LobbyCleanup;
import io.github.kensuke1984.kibrary.abandon.ThreeDPartialCleanup;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.entrance.DataAligner;
import io.github.kensuke1984.kibrary.entrance.DataTransfer;
import io.github.kensuke1984.kibrary.inversion.DiagATAExtract;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.math.MatrixFile;
import io.github.kensuke1984.kibrary.math.VectorFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationComparison;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.timewindow.TimewindowSubtract;
import io.github.kensuke1984.kibrary.util.data.DataEntryIntersection;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalogUpdate;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTListup;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.visual.map.ColorBinInformationFile;
import io.github.kensuke1984.kibrary.visual.map.VoxelMapper;
import io.github.kensuke1984.kibrary.visual.plot.AzimuthHistogram;
import io.github.kensuke1984.kibrary.visual.plot.DistanceHistogram;
import io.github.kensuke1984.kibrary.voxel.LayerInformationFile;
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
    EventListFile(4, EventListFile.class),
    ObserverListFile(5, ObserverListFile.class),
    DataEntryListFile(6, DataEntryListFile.class),
    DataEntryIntersection(7, DataEntryIntersection.class),
    ColorBinInformationFile(8, ColorBinInformationFile.class),
    // Data download 10
    GlobalCMTID(10, GlobalCMTID.class),
    GlobalCMTListup(11, GlobalCMTListup.class),
    DataTransfer(12, DataTransfer.class),
    DataAligner(13, DataAligner.class),
    LobbyCleanup(19, LobbyCleanup.class),
    // Synthetic  20
    SPCFileAccess(21, SPCFileAccess.class),
    SACFileAccess(22, SACFileAccess.class),
    // Filtered 30
    TimewindowDataFile(31, TimewindowDataFile.class),
    StaticCorrectionDataFile(32, StaticCorrectionDataFile.class),
    TimewindowSubtract(37, TimewindowSubtract.class),
    // Compiled 40
    BasicIDFile(40, BasicIDFile.class),
    VarianceComputer(41, VarianceComputer.class),
    DistanceHistogram(42, DistanceHistogram.class),
    AzimuthHistogram(43, AzimuthHistogram.class),
    // Voxel 50
    LayerInformationFile(50, LayerInformationFile.class),
    VoxelMapper(51, VoxelMapper.class),
    UnknownParameterSetter(52, UnknownParameterSetter.class),
    // Partial 60
    PartialIDFile(60, PartialIDFile.class),
    ThreeDPartialCleanup(69, ThreeDPartialCleanup.class),
    // Inversion 70
    WeightingHandler(70, WeightingHandler.class),
    VectorFile(71, VectorFile.class),
    MatrixFile(72, MatrixFile.class),
    PerturbationComparison(73, PerturbationComparison.class),
    DiagATAExtract(74, DiagATAExtract.class);
    // Temporal 100
    ;

    private final Class<?> c;
    private final int number;

    private Brooklyn(int number, Class<?> c) {
        this.number = number;
        this.c = c;
    }

    static void printList() {
        Arrays.stream(values()).sorted().forEach(m -> System.out.println(m.number + " " + m.c.getSimpleName()));
    }

    static String numRange() {
        Brooklyn[] all = values();
        int min = Arrays.stream(all).mapToInt(m -> m.number).min().getAsInt();
        int max = Arrays.stream(all).mapToInt(m -> m.number).max().getAsInt();
        return min + "-" + max;
    }

    /**
     * Returns a {@link Brooklyn} given its corresponding number.
     * Note that {@link #valueOf(String)}, which returns a {@link Brooklyn} given a String of its name,
     * is already defined automatically.
     *
     * @param number (int) The value to get a {@link Brooklyn} for.
     * @return ({@link Brooklyn}) The {@link Brooklyn} corresponding to the value.
     */
    static Brooklyn ofNumber(int number) {
        return Arrays.stream(values()).filter(m -> m.number == number).findAny().get();
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
