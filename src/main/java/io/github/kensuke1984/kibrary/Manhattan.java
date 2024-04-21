package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionForger;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionMerge;
import io.github.kensuke1984.kibrary.dsmsetup.OneDPartialDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.SyntheticDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.ThreeDPartialDSMSetup;
import io.github.kensuke1984.kibrary.entrance.DataLobby;
import io.github.kensuke1984.kibrary.entrance.DataRequestor;
import io.github.kensuke1984.kibrary.entrance.NetworkLookup;
import io.github.kensuke1984.kibrary.filter.FilterDivider;
import io.github.kensuke1984.kibrary.firsthandler.DataKitchen;
import io.github.kensuke1984.kibrary.fusion.AdaptiveGridDesigner;
import io.github.kensuke1984.kibrary.fusion.CoarseGridDesigner;
import io.github.kensuke1984.kibrary.fusion.PartialsFuser;
import io.github.kensuke1984.kibrary.inversion.LetMeInvert;
import io.github.kensuke1984.kibrary.inversion.setup.InversionArranger;
import io.github.kensuke1984.kibrary.inversion.solve.InversionSolver;
import io.github.kensuke1984.kibrary.inversion.solve.InversionSumSolver;
import io.github.kensuke1984.kibrary.perturbation.BlockModelMaker;
import io.github.kensuke1984.kibrary.perturbation.CheckerboardMaker;
import io.github.kensuke1984.kibrary.perturbation.ModelSmoothener;
import io.github.kensuke1984.kibrary.selection.DataSelection;
import io.github.kensuke1984.kibrary.selection.RaypathSelection;
import io.github.kensuke1984.kibrary.timewindow.TimewindowMaker;
import io.github.kensuke1984.kibrary.timewindow.TimewindowMerge;
import io.github.kensuke1984.kibrary.util.DatasetMerge;
import io.github.kensuke1984.kibrary.util.data.VirtualDatasetMaker;
import io.github.kensuke1984.kibrary.util.earth.ModelStructureConverter;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructurePerturber;
import io.github.kensuke1984.kibrary.util.globalcmt.VirtualEventRegistration;
import io.github.kensuke1984.kibrary.util.spc.SPC_SAC;
import io.github.kensuke1984.kibrary.visual.map.CrossSectionCreator;
import io.github.kensuke1984.kibrary.visual.map.GreatArcMapper;
import io.github.kensuke1984.kibrary.visual.map.ModelMapper;
import io.github.kensuke1984.kibrary.visual.map.ModelSetMapper;
import io.github.kensuke1984.kibrary.visual.map.PartialsMovieMaker;
import io.github.kensuke1984.kibrary.visual.map.PerturbationMapper;
import io.github.kensuke1984.kibrary.visual.map.RaypathMapper;
import io.github.kensuke1984.kibrary.visual.map.SensitivityKernelMapper;
import io.github.kensuke1984.kibrary.visual.plot.BasicBinnedStackCreator;
import io.github.kensuke1984.kibrary.visual.plot.BasicRecordSectionCreator;
import io.github.kensuke1984.kibrary.visual.plot.BasicWaveformPlotter;
import io.github.kensuke1984.kibrary.visual.plot.CatalogueErrorCalculator;
import io.github.kensuke1984.kibrary.visual.plot.DataFeatureHistogram;
import io.github.kensuke1984.kibrary.visual.plot.ModelStructurePlotter;
import io.github.kensuke1984.kibrary.visual.plot.PartialsPlotter;
import io.github.kensuke1984.kibrary.visual.plot.PolynomialStructurePlotter;
import io.github.kensuke1984.kibrary.voxel.VoxelAutoDesigner;
import io.github.kensuke1984.kibrary.voxel.VoxelManualDesigner;
import io.github.kensuke1984.kibrary.waveform.ActualWaveformCompiler;
import io.github.kensuke1984.kibrary.waveform.BasicIDMerge;
import io.github.kensuke1984.kibrary.waveform.BasicIDRebuilder;
import io.github.kensuke1984.kibrary.waveform.OrthogonalityTest;
import io.github.kensuke1984.kibrary.waveform.PartialIDMerge;
import io.github.kensuke1984.kibrary.waveform.PartialsAssembler3D;
import io.github.kensuke1984.kibrary.waveform.PartialsBuilder1D;
import io.github.kensuke1984.kibrary.waveform.PseudoWaveformGenerator;

/**
 * An enum where all {@link Operation}s in Kibrary should be assigned to.
 * <p>
 * The value name set to this enum must be the same as its corresponding class name.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2022/1/7 Recreated based on the original Manhattan.
 */
enum Manhattan {
    // Environment & General 00
    VirtualEventRegistration(0, VirtualEventRegistration.class),
    DatasetMerge(1, DatasetMerge.class),
    PolynomialStructurePerturber(2, PolynomialStructurePerturber.class),
    PolynomialStructurePlotter(3, PolynomialStructurePlotter.class),
    GreatArcMapper(5, GreatArcMapper.class),
    RaypathMapper(6, RaypathMapper.class),
    RaypathSelection(7, RaypathSelection.class),
    // Data download 10
    DataRequestor(13, DataRequestor.class),
    DataLobby(14, DataLobby.class),
    DataKitchen(15, DataKitchen.class),
    NetworkLookup(18, NetworkLookup.class),
    // Synthetic  20
    SyntheticDSMSetup(20, SyntheticDSMSetup.class),
    SPC_SAC(21, SPC_SAC.class),
    VirtualDatasetMaker(25, VirtualDatasetMaker.class),
    // Filtered 30
    FilterDivider(30, FilterDivider.class),
    TimewindowMaker(31, TimewindowMaker.class),
    FujiStaticCorrection(32, FujiStaticCorrection.class),
    DataSelection(33, DataSelection.class),
    TimewindowMerge(37, TimewindowMerge.class),
    StaticCorrectionMerge(38, StaticCorrectionMerge.class),
    StaticCorrectionForger(39, StaticCorrectionForger.class),
    // Compiled 40
    ActualWaveformCompiler(40, ActualWaveformCompiler.class),
    BasicIDMerge(41, BasicIDMerge.class),
    BasicWaveformPlotter(42, BasicWaveformPlotter.class),
    BasicRecordSectionCreator(43, BasicRecordSectionCreator.class),
    BasicBinnedStackCreator(44, BasicBinnedStackCreator.class),
    DataFeatureHistogram(48, DataFeatureHistogram.class),
    // Voxel 50
    VoxelAutoDesigner(50, VoxelAutoDesigner.class),
    VoxelManualDesigner(51,VoxelManualDesigner.class),
    CoarseGridDesigner(52, CoarseGridDesigner.class),
    AdaptiveGridDesigner(53, AdaptiveGridDesigner.class),
    // Partial 60
    OneDPartialDSMSetup(60, OneDPartialDSMSetup.class),
    ThreeDPartialDSMSetup(61, ThreeDPartialDSMSetup.class),
    PartialsBuilder1D(62, PartialsBuilder1D.class),
    PartialsAssembler3D(63, PartialsAssembler3D.class),
    PartialIDMerge(64, PartialIDMerge.class),
    PartialsPlotter(65, PartialsPlotter.class),
    SensitivityKernelMapper(66, SensitivityKernelMapper.class),
    PartialsMovieMaker(67, PartialsMovieMaker.class),
    CatalogueErrorCalculator(68, CatalogueErrorCalculator.class),
    PartialsFuser(69, PartialsFuser.class),
    // Inversion 70
    LetMeInvert(70, LetMeInvert.class),
    InversionArranger(71, InversionArranger.class),
    InversionSolver(72, InversionSolver.class),
    InversionSumSolver(73, InversionSumSolver.class),
    ModelStructurePlotter(74, ModelStructurePlotter.class),
    ModelSetMapper(75, ModelSetMapper.class),
    ModelMapper(76, ModelMapper.class),
    PerturbationMapper(77, PerturbationMapper.class),
    CrossSectionCreator(78, CrossSectionCreator.class),
    ModelStructureConverter(79, ModelStructureConverter.class),
    // Tests 80
    BlockModelMaker(80, BlockModelMaker.class),
    CheckerboardMaker(81, CheckerboardMaker.class),
    ModelSmoothener(84, ModelSmoothener.class),
    PseudoWaveformGenerator(85, PseudoWaveformGenerator.class),
    BasicIDRebuilder(86, BasicIDRebuilder.class),
    OrthogonalityTest(87, OrthogonalityTest.class),
    // Temporal 100
    ;

    private final Class<? extends Operation> operation;
    private final int number;

    private Manhattan(int number, Class<? extends Operation> operation) {
        this.number = number;
        this.operation = operation;
    }

    static void printList() {
        Arrays.stream(values()).sorted().forEach(m -> System.out.println(m.number + "-" + m.operation.getSimpleName()));
    }

    static String numRange() {
        Manhattan[] all = values();
        int min = Arrays.stream(all).mapToInt(m -> m.number).min().getAsInt();
        int max = Arrays.stream(all).mapToInt(m -> m.number).max().getAsInt();
        return min + "-" + max;
    }

    /**
     * Returns a {@link Manhattan} given its corresponding number.
     * Note that {@link #valueOf(String)}, which returns a {@link Manhattan} given a String of its name,
     * is already defined automatically.
     *
     * @param number (int) The value to get a {@link Manhattan} for.
     * @return ({@link Manhattan}) The {@link Manhattan} corresponding to the value.
     */
    static Manhattan ofNumber(int number) {
        return Arrays.stream(values()).filter(m -> m.number == number).findAny().get();
    }

    Class<? extends Operation> getOperation() {
        return operation;
    }

    void writeDefaultPropertiesFile() throws ReflectiveOperationException {
        operation.getMethod("writeDefaultPropertiesFile", (Class<?>[]) null).invoke(null, (Object[]) null);
    }

}
