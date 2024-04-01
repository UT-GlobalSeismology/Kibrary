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
 * When a time window for a synthetic is [t1, t2], then <br>
 * use a window of [t1-timeshift, t2-timeshift] in a observed one.<br>
 * and amplitude observed dataset is divided by the AMPLITUDE.
 * <p>
 * In short, time correction value is relative pick time in synthetic - the one
 * in observed.
 * <p>
 * Amplitude correction value (AMPLITUDE) is observed / synthetic.
 * <p>
 * Time shift is rounded off to the second decimal place.
 * <p>
 * To identify which time window for a waveform, SYNTHETIC_TIME is also used.
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
     * start time of timewindow for synthetic waveform
     */
    private final double synStartTime;
    /**
     * seismic phases included in the timewindow (e.g. S, ScS)
     */
    private final Phase[] phases;

    /**
     * time shift [s]<br>
     * Synthetic [t1, t2], Observed [t1 - TIME, t2 - TIME]
     */
    private final double timeShift;
    /**
     * amplitude correction: obs / syn<br>
     * Observed should be divided by this value.
     */
    private final double amplitudeRatio;

    /**
     * When a time window for a synthetic is [start, end], then
     * use a window of [start-timeshift, end-timeshift] in the corresponding
     * observed one.<br>
     * Example, if you want to align a phase which arrives Ts in synthetic and
     * To in observed, the timeshift will be Ts-To.<br>
     * Amplitude ratio shall be observed / synthetic. Observed will be divided by this value.
     * <p>
     * synStartTime may be used only for identification when your dataset contain multiple time windows in one waveform.
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
     * @param t (TimewindowData) Timewindow to judge
     * @return (boolean) true if the timewindow is the correct one
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
     * @return value of synthetic start time for identification when you use multiple time windows.
     */
    public double getSynStartTime() {
        return synStartTime;
    }

    public Phase[] getPhases() {
        return phases;
    }

    /**
     * @return value of time shift [s] (syn-obs)
     */
    public double getTimeshift() {
        return timeShift;
    }

    /**
     * @return value of amplitude ratio (obs / syn)
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
