package io.github.kensuke1984.kibrary.correction;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Static correction data for a raypath.<br>
 * <p>
 * This class is <b>IMMUTABlE</b>
 * <p>
 * The time shift value <i>t</i> indicates how much time the observed waveform should be shifted in the positive direction,
 * which means how much time the observed time window should be shifted in the negative direction.
 * So, use synthetic time window [t1 : t2] and observed time window [t1-t : t2-t].
 * <p>
 * In other words, the time shift value is the relative pick time in synthetic - the one in observed.
 * <p>
 * Amplitude correction value (AMPLITUDE) is observed / synthetic.
 * <p>
 * Time shift is rounded off to the second decimal place.
 * <p>
 * To identify which time window of a waveform this corresponds to, {@link #synStartTime} is also used.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 *
 * TODO shouldn't this hold TimewindowData as a field, instead of obs/ev/comp/phases/start ? (2022/12/14 otsuru)
 */
public class StaticCorrectionData implements Comparable<StaticCorrectionData> {

    private static final int AMPLITUDE_DECIMALS = 2;

    private final Observer observer;
    private final GlobalCMTID eventID;
    private final SACComponent component;
    /**
     * Start time of window for synthetic waveform.
     */
    private final double synStartTime;
    /**
     * Seismic phases included in the time window (e.g. S, ScS).
     */
    private final Phase[] phases;

    /**
     * Time shift [s].
     * Use synthetic window [t1 : t2] and observed window [t1 - timeShift : t2 - timeShift].
     */
    private final double timeShift;
    /**
     * Amplitude correction: obs / syn.
     * Observed should be divided by this value.
     */
    private final double amplitudeRatio;

    /**
     * When a time window for a synthetic is [start : end],
     * then use a window of [start-timeshift : end-timeshift] in the corresponding observed one.
     * For example, if you want to align a phase which arrives at Ts in synthetic and at To in observed, the time shift will be Ts-To.
     * <p>
     * Amplitude ratio is observed / synthetic. Observed should be divided by this value.
     * <p>
     * synStartTime is used only for identification when your dataset contains multiple time windows in one waveform.
     *
     * @param observer        for shift
     * @param eventID        for shift
     * @param component      for shift
     * @param synStartTime   for identification
     * @param timeShift      value Synthetic [t1, t2], Observed [t1-timeShift,
     *                       t2-timeShift]
     * @param amplitudeRatio Observed / Synthetic, an observed waveform will be divided by this value.
     */
    public StaticCorrectionData(Observer observer, GlobalCMTID eventID, SACComponent component, double synStartTime,
            double timeShift, double amplitudeRatio, Phase[] phases) {
        this.observer = observer;
        this.eventID = eventID;
        this.component = component;
        this.synStartTime = Precision.round(synStartTime, Timewindow.DECIMALS);
        this.timeShift = Precision.round(timeShift, Timewindow.DECIMALS);
        this.amplitudeRatio = Precision.round(amplitudeRatio, AMPLITUDE_DECIMALS);
        this.phases = phases;
    }

    /**
     * Judges whether this static correction is for the given timewindow.
     * @param t (TimewindowData) Timewindow to judge.
     * @return (boolean) Whether the timewindow is the correct one.
     */
    public boolean isForTimewindow(TimewindowData t) {
        return (t.getObserver().equals(observer) && t.getGlobalCMTID().equals(eventID) && t.getComponent() == component
                && Math.abs(t.getStartTime() - synStartTime) < TimewindowData.TIME_EPSILON);
    }

    @Override
    public int compareTo(StaticCorrectionData o) {
        int obs = observer.compareTo(o.observer);
        if (obs != 0) return obs;
        int id = eventID.compareTo(o.eventID);
        if (id != 0) return id;
        int comp = component.compareTo(o.component);
        if (comp != 0) return comp;
        int start = Double.compare(synStartTime, o.synStartTime);
        if (start != 0) return start;
        int shift = Double.compare(timeShift, o.timeShift);
        if (shift != 0) return shift;
        return Double.compare(amplitudeRatio, o.amplitudeRatio);
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getGlobalCMTID() {
        return eventID;
    }

    public SACComponent getComponent() {
        return component;
    }

    /**
     * @return (double) Value of synthetic start time for identification when you use multiple time windows.
     */
    public double getSynStartTime() {
        return synStartTime;
    }

    public Phase[] getPhases() {
        return phases;
    }

    /**
     * @return (double) Value of time shift [s] (syn-obs).
     */
    public double getTimeshift() {
        return timeShift;
    }

    /**
     * @return (double) Value of amplitude ratio (obs / syn).
     */
    public double getAmplitudeRatio() {
        return amplitudeRatio;
    }

    @Override
    public String toString() {
        return observer.toPaddedInfoString() + " " + eventID.toPaddedString() + " " + component + " "
                + MathAid.padToString(synStartTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.DECIMALS, false) + " "
                + TimewindowData.phasesAsString(phases) + " " + timeShift + " " + amplitudeRatio;
    }

}
