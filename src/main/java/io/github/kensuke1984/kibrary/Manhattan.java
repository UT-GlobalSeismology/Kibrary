package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionForger;
import io.github.kensuke1984.kibrary.dsmsetup.OneDPartialDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.SyntheticDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.ThreeDPartialDSMSetup;
import io.github.kensuke1984.kibrary.entrance.DataLobby;
import io.github.kensuke1984.kibrary.entrance.DataRequestor;
import io.github.kensuke1984.kibrary.filter.FilterDivider;
import io.github.kensuke1984.kibrary.firsthandler.DataKitchen;
import io.github.kensuke1984.kibrary.inversion.LetMeInvert;
import io.github.kensuke1984.kibrary.inversion.setup.InversionArranger;
import io.github.kensuke1984.kibrary.inversion.solve.InversionSolver;
import io.github.kensuke1984.kibrary.multigrid.MultigridDesigner;
import io.github.kensuke1984.kibrary.multigrid.MultigridPartialsForger;
import io.github.kensuke1984.kibrary.perturbation.BlockModelMaker;
import io.github.kensuke1984.kibrary.perturbation.CheckerboardMaker;
import io.github.kensuke1984.kibrary.selection.DataSelection;
import io.github.kensuke1984.kibrary.selection.RaypathSelection;
import io.github.kensuke1984.kibrary.timewindow.TimewindowMaker;
import io.github.kensuke1984.kibrary.util.DatasetMerge;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructurePerturber;
import io.github.kensuke1984.kibrary.util.spc.SPC_SAC;
import io.github.kensuke1984.kibrary.visual.BasicRecordSectionCreator;
import io.github.kensuke1984.kibrary.visual.BasicWaveformPlotter;
import io.github.kensuke1984.kibrary.visual.CatalogueErrorCalculator;
import io.github.kensuke1984.kibrary.visual.DataFeatureHistogram;
import io.github.kensuke1984.kibrary.visual.ModelMapper;
import io.github.kensuke1984.kibrary.visual.ModelSetMapper;
import io.github.kensuke1984.kibrary.visual.PartialWaveformPlotter;
import io.github.kensuke1984.kibrary.visual.PerturbationMapper;
import io.github.kensuke1984.kibrary.visual.PolynomialStructurePlotter;
import io.github.kensuke1984.kibrary.visual.RaypathMapper;
import io.github.kensuke1984.kibrary.visual.SensitivityKernelMapper;
import io.github.kensuke1984.kibrary.voxel.VoxelLayoutDesigner;
import io.github.kensuke1984.kibrary.waveform.ActualWaveformCompiler;
import io.github.kensuke1984.kibrary.waveform.BasicIDMerge;
import io.github.kensuke1984.kibrary.waveform.BasicIDRebuilder;
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
    PolynomialStructurePerturber(2, PolynomialStructurePerturber.class),
    PolynomialStructurePlotter(3, PolynomialStructurePlotter.class),
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
    StaticCorrectionForger(38, StaticCorrectionForger.class),
    // Compiled 40
    ActualWaveformCompiler(40, ActualWaveformCompiler.class),
    BasicIDMerge(41, BasicIDMerge.class),
    BasicWaveformPlotter(42, BasicWaveformPlotter.class),
    BasicRecordSectionCreator(43, BasicRecordSectionCreator.class),
    DataFeatureHistogram(48, DataFeatureHistogram.class),
    // Voxel 50
    VoxelLayoutDesigner(50, VoxelLayoutDesigner.class),
    MultigridDesigner(53, MultigridDesigner.class),
    // Partial 60
    ThreeDPartialDSMSetup(60, ThreeDPartialDSMSetup.class),
    PartialWaveformAssembler3D(61, PartialWaveformAssembler3D.class),
    PartialIDMerge(62, PartialIDMerge.class),
    SensitivityKernelMapper(63, SensitivityKernelMapper.class),
    PartialWaveformPlotter(64, PartialWaveformPlotter.class),
    OneDPartialDSMSetup(65, OneDPartialDSMSetup.class), //
    MultigridPartialsForger(67, MultigridPartialsForger.class),
    CatalogueErrorCalculator(68, CatalogueErrorCalculator.class),
    // Inversion 70
    LetMeInvert(70, LetMeInvert.class),
    InversionArranger(71, InversionArranger.class),
    InversionSolver(72, InversionSolver.class),
    ModelSetMapper(74, ModelSetMapper.class),
    // Tests 80
    BlockModelMaker(80, BlockModelMaker.class),
    CheckerboardMaker(81, CheckerboardMaker.class),
    PerturbationMapper(82, PerturbationMapper.class),
    ModelMapper(83, ModelMapper.class),
    PseudoWaveformGenerator(85, PseudoWaveformGenerator.class),
    BasicIDRebuilder(86, BasicIDRebuilder.class),
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
