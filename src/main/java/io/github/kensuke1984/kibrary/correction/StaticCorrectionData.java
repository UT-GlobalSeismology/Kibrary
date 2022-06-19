package io.github.kensuke1984.kibrary.correction;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
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
 * Amplitude correction value (AMPLITUDE) is observed /
 * synthetic.
 * <p>
 * Time shift is rounded off to the second decimal place.
 * <p>
 * To identify which time window for a waveform, SYNTHETIC_TIME is also used.
 *
 * @author Kensuke Konishi
 * @version 0.1.1.2
 * @author anselme add phase information
 */
public class StaticCorrectionData implements Comparable<StaticCorrectionData> {

    private final Observer observer;
    private final GlobalCMTID eventID;
    private final SACComponent component;
    /**
     * time shift [s]<br>
     * Synthetic [t1, t2], Observed [t1 - TIME, t2 - TIME]
     */
    private final double timeShift;
    /**
     * amplitude correction: obs / syn<br>
     * Observed should be divided by this value.
     */
    private final double amplitude;
    /**
     * start time of synthetic waveform
     */
    private final double synStartTime;

    /**
     * phases in windows
     */
    private final Phase[] phases;

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
        this.synStartTime = Precision.round(synStartTime, 2);
        this.timeShift = Precision.round(timeShift, 2);
        this.amplitude = Precision.round(amplitudeRatio, 2);
        this.phases = phases;
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
        return Double.compare(amplitude, o.amplitude);
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
     * @return value of time shift (syn-obs)
     */
    public double getTimeshift() {
        return timeShift;
    }

    /**
     * @return value of ratio (obs / syn)
     */
    public double getAmplitudeRatio() {
        return amplitude;
    }

    /**
     * @return value of synthetic start time for the identification when you use multiple time windows.
     */
    public double getSynStartTime() {
        return synStartTime;
    }

    public Phase[] getPhases() {
        return phases;
    }

    @Override
    public String toString() {
        List<String> phaseStrings =
                Stream.of(phases).filter(phase -> phase != null).map(Phase::toString).collect(Collectors.toList());
        return observer.toPaddedInfoString() + " " + eventID.toPaddedString() + " " + component + " "
                + synStartTime + " " + timeShift + " " + amplitude + " " + String.join(",", phaseStrings);
    }

}
