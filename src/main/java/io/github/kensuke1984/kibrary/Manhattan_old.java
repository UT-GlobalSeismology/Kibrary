package io.github.kensuke1984.kibrary;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.axiSEM.Result;
import io.github.kensuke1984.kibrary.correction.SourceTimeFunctionByGridSearch;
import io.github.kensuke1984.kibrary.correction.TakeuchiStaticCorrection;
import io.github.kensuke1984.kibrary.selection.PhaseEnvelope;
import io.github.kensuke1984.kibrary.selection.SecondHandler;
import io.github.kensuke1984.kibrary.waveform.PartialWaveformAssembler1D;
import io.github.kensuke1984.kibrary.waveform.addons.AtAMaker;
import io.github.kensuke1984.kibrary.waveform.addons.ObservedSyntheticDatasetMaker_SpcTest;
import io.github.kensuke1984.kibrary.waveform.addons.Partial1DEnvelopeMaker;
import io.github.kensuke1984.kibrary.waveform.addons.Partial1DSpcMaker;

/**
 * An enum where all {@link Operation_old}s in Kibrary should be assigned to.
 *
 * @author Kensuke Konishi
 * @version 0.0.5.3
 */
public enum Manhattan_old {
    PartialWaveformAssembler1D(10, PartialWaveformAssembler1D.class), //
    PhaseEnvelope(12, PhaseEnvelope.class), //
    Result(14, Result.class), //
    SecondHandler(15, SecondHandler.class), //
    SourceTimeFunctionByGridSearch(16, SourceTimeFunctionByGridSearch.class), //
    TakeuchiStaticCorrection(20, TakeuchiStaticCorrection.class), //
    AtAMaker(22, AtAMaker.class),//
    Partial1DEnvelopeMaker(24, Partial1DEnvelopeMaker.class),//
    Partial1DSpcMaker(25, Partial1DSpcMaker.class),//
    ObservedSyntheticDatasetMaker_SpcTest(26, ObservedSyntheticDatasetMaker_SpcTest.class), //
    ;

    private Class<? extends Operation_old> c;
    private int value;

    Manhattan_old(int n, Class<? extends Operation_old> c) {
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
    static Manhattan_old valueOf(int n) {
        return Arrays.stream(values()).filter(m -> m.value == n).findAny().get();
    }

    /**
     * @return Class class of this Manhattan.
     * @author otsuru
     * @since 2022/1/7
     */
    public Class<? extends Operation_old> getOperation() {
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
