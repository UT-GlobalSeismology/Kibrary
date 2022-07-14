package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.dsmsetup.SyntheticDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.ThreeDPartialDSMSetup;
import io.github.kensuke1984.kibrary.entrance.DataLobby;
import io.github.kensuke1984.kibrary.entrance.DataRequestor;
import io.github.kensuke1984.kibrary.filter.FilterDivider;
import io.github.kensuke1984.kibrary.firsthandler.DataKitchen;
import io.github.kensuke1984.kibrary.inv_new.setup.InversionArranger;
import io.github.kensuke1984.kibrary.inv_new.solve.InversionSolver;
import io.github.kensuke1984.kibrary.inversion.LetMeInvert_old;
import io.github.kensuke1984.kibrary.model.VelocityModelMapper;
import io.github.kensuke1984.kibrary.selection.DataSelection;
import io.github.kensuke1984.kibrary.selection.RaypathSelection;
import io.github.kensuke1984.kibrary.timewindow.TimewindowMaker;
import io.github.kensuke1984.kibrary.util.DatasetMerge;
import io.github.kensuke1984.kibrary.util.spc.SPC_SAC;
import io.github.kensuke1984.kibrary.visual.RaypathMapper;
import io.github.kensuke1984.kibrary.visual.RecordSectionCreater;
import io.github.kensuke1984.kibrary.visual.SensitivityKernelMapper;
import io.github.kensuke1984.kibrary.visual.WaveformPlotCreater;
import io.github.kensuke1984.kibrary.waveform.ActualWaveformCompiler;
import io.github.kensuke1984.kibrary.waveform.BasicIDMerge;
import io.github.kensuke1984.kibrary.waveform.BasicIDSelect;
import io.github.kensuke1984.kibrary.waveform.PartialIDMerge;
import io.github.kensuke1984.kibrary.waveform.PartialWaveformAssembler3D;
import io.github.kensuke1984.kibrary.waveform.PseudoWaveformGenerator;

/**
 * An enum where all {@link Operation}s in Kibrary should be assigned to.
 * <p>
 * The value name set to this enum must be the same as its corresponding class name.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/1/7 Recreated based on the original Manhattan.
 */
enum Manhattan {
    // Environment & General 00
    DatasetMerge(1, DatasetMerge.class),
    RaypathMapper(6, RaypathMapper.class),
    // Data download 10
    DataRequestor(13, DataRequestor.class),
    DataLobby(14, DataLobby.class),
    DataKitchen(15, DataKitchen.class),
    // Synthetic  20
    SyntheticDSMSetup(20, SyntheticDSMSetup.class),
    SPC_SAC(21, SPC_SAC.class),
    // Filtered 30
    FilterDivider(30, FilterDivider.class),
    TimewindowMaker(31, TimewindowMaker.class),
    FujiStaticCorrection(32, FujiStaticCorrection.class),
    DataSelection(33, DataSelection.class),
    RaypathSelection(34, RaypathSelection.class),
    // Compiled 40
    ActualWaveformCompiler(40, ActualWaveformCompiler.class),
    BasicIDMerge(41, BasicIDMerge.class),
    WaveformPlotCreater(42, WaveformPlotCreater.class),
    RecordSectionCreater(43, RecordSectionCreater.class),
    BasicIDSelect(45, BasicIDSelect.class),
    // Partial 50
    ThreeDPartialDSMSetup(50, ThreeDPartialDSMSetup.class),
    PartialWaveformAssembler3D(51, PartialWaveformAssembler3D.class),
    PartialIDMerge(52, PartialIDMerge.class),
    SensitivityKernelMapper(53, SensitivityKernelMapper.class),
    // Inversion 60
    LetMeInvert(60, LetMeInvert_old.class),
    InversionArranger(61, InversionArranger.class),
    InversionSolver(62, InversionSolver.class),
    VelocityModelMapper(64, VelocityModelMapper.class),
    PseudoWaveformGenerator(65, PseudoWaveformGenerator.class),
    // Temporal 100
    ;

    private Class<? extends Operation> c;
    private int value;

    Manhattan(int n, Class<? extends Operation> c) {
        value = n;
        this.c = c;
    }

    static void printList() {
        Arrays.stream(values()).sorted().forEach(m -> System.out.println(m.value + "-" + m.c.getSimpleName()));
    }

    static String numRange() {
        Manhattan[] all = values();
        int min = Arrays.stream(all).mapToInt(m -> m.value).min().getAsInt();
        int max = Arrays.stream(all).mapToInt(m -> m.value).max().getAsInt();
        return min + "-" + max;
    }

    /**
     * Returns a Manhattan given its corresponding number.
     * Note that {@link #valueOf(String)}, which returns a Manhattan given a String of its name,
     * is already defined automatically.
     *
     * @param n (int)
     * @return
     */
    static Manhattan valueOf(int n) {
        return Arrays.stream(values()).filter(m -> m.value == n).findAny().get();
    }

    Class<? extends Operation> getOperation() {
        return c;
    }

    void writeDefaultPropertiesFile() throws ReflectiveOperationException {
        c.getMethod("writeDefaultPropertiesFile", (Class<?>[]) null).invoke(null, (Object[]) null);
    }

}
