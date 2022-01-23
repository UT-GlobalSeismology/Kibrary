package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.axiSEM.Result;
import io.github.kensuke1984.kibrary.correction.SourceTimeFunctionByGridSearch;
import io.github.kensuke1984.kibrary.correction.TakeuchiStaticCorrection;
import io.github.kensuke1984.kibrary.dsmsetup.OneDPartialDSMSetup;
import io.github.kensuke1984.kibrary.dsmsetup.ThreeDPartialDSMSetup;
import io.github.kensuke1984.kibrary.external.gmt.RaypathDistribution;
import io.github.kensuke1984.kibrary.inversion.CheckerBoardTest;
import io.github.kensuke1984.kibrary.inversion.LetMeInvert;
import io.github.kensuke1984.kibrary.inversion.LetMeInvert_fromAtA;
import io.github.kensuke1984.kibrary.selection.DataSelection;
import io.github.kensuke1984.kibrary.selection.PhaseEnvelope;
import io.github.kensuke1984.kibrary.selection.RaypathSelection;
import io.github.kensuke1984.kibrary.selection.SecondHandler;
import io.github.kensuke1984.kibrary.waveform.ActualWaveformCompiler;
import io.github.kensuke1984.kibrary.waveform.PartialWaveformAssembler1D;
import io.github.kensuke1984.kibrary.waveform.PartialWaveformAssembler3D;
import io.github.kensuke1984.kibrary.waveform.addons.AtAMaker;
import io.github.kensuke1984.kibrary.waveform.addons.ObservedSyntheticDatasetMaker_SpcTest;
import io.github.kensuke1984.kibrary.waveform.addons.Partial1DEnvelopeMaker;
import io.github.kensuke1984.kibrary.waveform.addons.Partial1DSpcMaker;

/**
 * An enum where all {@link Operation}s in Kibrary should be assigned to.
 *
 * @author Kensuke Konishi
 * @version 0.0.5.3
 */
public enum Manhattan {
    CheckerBoardTest(1, CheckerBoardTest.class), //
    DataSelection(3, DataSelection.class), //
    ThreeDPartialDSMSetup(7, ThreeDPartialDSMSetup.class), //
    LetMeInvert(8, LetMeInvert.class), //
    ActualWaveformCompiler(9, ActualWaveformCompiler.class), //
    PartialWaveformAssembler1D(10, PartialWaveformAssembler1D.class), //
    PartialWaveformAssembler3D(11, PartialWaveformAssembler3D.class), //
    PhaseEnvelope(12, PhaseEnvelope.class), //
    RaypathDistribution(13, RaypathDistribution.class), //
    Result(14, Result.class), //
    SecondHandler(15, SecondHandler.class), //
    SourceTimeFunctionByGridSearch(16, SourceTimeFunctionByGridSearch.class), //
    OneDPartialDSMSetup(18, OneDPartialDSMSetup.class), //
    TakeuchiStaticCorrection(20, TakeuchiStaticCorrection.class), //
    AtAMaker(22, AtAMaker.class),//
    LetMeInvert_fromAtA(23, LetMeInvert_fromAtA.class),//
    Partial1DEnvelopeMaker(24, Partial1DEnvelopeMaker.class),//
    Partial1DSpcMaker(25, Partial1DSpcMaker.class),//
    ObservedSyntheticDatasetMaker_SpcTest(26, ObservedSyntheticDatasetMaker_SpcTest.class), //
    RaypathSelection(31, RaypathSelection.class), //
    ;

    private Class<? extends Operation> c;
    private int value;

    Manhattan(int n, Class<? extends Operation> c) {
        value = n;
        this.c = c;
    }

    public static void printList() {
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
    static Manhattan valueOf(int n) {
        return Arrays.stream(values()).filter(m -> m.value == n).findAny().get();
    }

    /**
     * @return Class class of this Manhattan.
     * @author otsuru
     * @since 2022/1/7
     */
    public Class<? extends Operation> getOperation() {
        return c;
    }

    /**
     * invoke main of this with the args
     *
     * @param args to input main
     * @throws Exception if any
     */
    public void invokeMain(String[] args) throws Exception {
        c.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    public void writeDefaultPropertiesFile() throws Exception {
        c.getMethod("writeDefaultPropertiesFile", (Class<?>[]) null).invoke(null, (Object[]) null);
    }

}
