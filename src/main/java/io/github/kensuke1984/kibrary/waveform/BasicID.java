package io.github.kensuke1984.kibrary.waveform;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * <p>
 * ID and waveform data of observed and synthetic waveforms for a pair of event and observer.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * <p>
 * Contents of information for one ID:
 * <ul>
 * <li> waveform type (observed or synthetic) </li>
 * <li> observer </li>
 * <li> global CMT ID </li>
 * <li> component (Z, R, or T) </li>
 * <li> minimum and maximum period </li>
 * <li> start time </li>
 * <li> number of points </li>
 * <li> sampling Hz </li>
 * <li> whether it is either convolved or observed </li>
 * <li> phases contained in timewindow </li>
 * </ul>
 * <p>
 * Caution: A BasicID instance may or may not hold waveform data, dependeing on whether it has already been set.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class BasicID {

    /**
     * Margin to decide whether two IDs have the same minPeriod or maxPeriod.
     * Period value should be around 5~200, so a value around 0.1 for epsilon should be enough.
     */
    public static final double PERIOD_EPSILON = 0.1;
    /**
     * The number of decimal places to round off the values.
     */
    private static final int DECIMALS = 3;

    protected final WaveformType type;
    protected final double samplingHz;
    protected final double startTime;
    protected final int npts;
    protected final Observer observer;
    protected final GlobalCMTID eventID;
    protected final SACComponent component;
    protected final Phase[] phases;
    /**
     * [s] if the data has not been applied a filter, 0
     */
    protected final double minPeriod;
    /**
     * [s] if the data has not been applied a filter, {@link Double#POSITIVE_INFINITY}
     */
    protected final double maxPeriod;
    protected final boolean convolved;
    /**
     * waveform
     */
    private final double[] data;

    /**
     * @param waveFormType Type of waveform data.
     * @param samplingHz   [Hz] Sampling Hz.
     * @param startTime    [s] start time of the time window.
     * @param npts         Number of data points
     * @param observer      Information of observer.
     * @param eventID  Event ID for the data.
     * @param sacComponent Component of the data.
     * @param minPeriod    [s] minimum period of the applied filter if none, 0
     * @param maxPeriod    [s] minimum period of the applied filter if none, {@link Double#POSITIVE_INFINITY}
     * @param phases       Array of phases
     * @param convolved    If the data is convolved.
     * @param waveformData the waveform data for this ID.
     */
    public BasicID(WaveformType waveFormType, double samplingHz, double startTime, int npts, Observer observer,
            GlobalCMTID eventID, SACComponent sacComponent, double minPeriod, double maxPeriod, Phase[] phases,
            boolean convolved, double... waveformData) {
        this.type = waveFormType;
        this.samplingHz = Precision.round(samplingHz, DECIMALS);
        this.startTime = Precision.round(startTime, Timewindow.DECIMALS);
        this.npts = npts;
        this.observer = observer;
        this.eventID = eventID;
        this.component = sacComponent;
        this.phases = phases;
        this.minPeriod = Precision.round(minPeriod, DECIMALS);
        this.maxPeriod = Precision.round(maxPeriod, DECIMALS);
        this.convolved = convolved;
        if (waveformData.length != 0 && waveformData.length != npts)
            throw new IllegalArgumentException("Input waveform data length is invalid");
        this.data = waveformData.clone();
    }

     /**
     * A new BasicID with the input data will be returned.
     *
     * @param data Waveform data to be replaced
     * @return BasicID with the input data
     */
    public BasicID withData(double[] data) {
        return new BasicID(type, samplingHz, startTime, npts, observer, eventID, component, minPeriod,
                maxPeriod, phases, convolved, data);
    }

    /**
     * Extract all timewindows from a set of input timewindows
     * that have the same (event, observer, component) and overlap with the timewindow of this basicID.
     * @param timewindowSet (Set of {@link TimewindowData}) Input timewindow set to search from
     * @return (Set of {@link TimewindowData}) All timewindows that overlap with this
     */
    public Set<TimewindowData> findAllOverlappingWindows(Set<TimewindowData> timewindowSet) {
        Set<TimewindowData> overlappingWindows = timewindowSet.stream()
                .filter(window -> window.getGlobalCMTID().equals(eventID)
                        && window.getObserver().equals(observer)
                        && window.getComponent().equals(component)
                        // there must be some overlap between the windows
                        && window.getStartTime() < computeEndTime()
                        && startTime < window.getEndTime())
                .collect(Collectors.toSet());
        return overlappingWindows;
    }

    /**
     * Decides whether two IDs (BasicID and/or PartialID) are pairs. (Note that {@link PartialID} extends {@link BasicID}.)
     * They are regarded as same if eventID, observer, component, npts, samplingHz, max & min period are same
     * and startTime difference is within the maximum time shift.
     * This method ignores whether the input IDs are observed or synthetic. It also ignores the Phases.
     *
     * @param id0 {@link BasicID}
     * @param id1 {@link BasicID}
     * @return if the IDs are same
     */
    public static boolean isPair(BasicID id0, BasicID id1) {
        boolean res = id0.getGlobalCMTID().equals(id1.getGlobalCMTID()) && id0.getObserver().equals(id1.getObserver())
                && id0.getSacComponent() == id1.getSacComponent() && id0.getNpts() == id1.getNpts()
                && Precision.equals(id0.getStartTime(), id1.getStartTime(), TimewindowData.TIME_SHIFT_MAX)
                && id0.getSamplingHz() == id1.getSamplingHz()
                && Precision.equals(id0.getMaxPeriod(), id1.getMaxPeriod(), PERIOD_EPSILON)
                && Precision.equals(id0.getMinPeriod(), id1.getMinPeriod(), PERIOD_EPSILON);
        return res;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((eventID == null) ? 0 : eventID.hashCode());
        result = prime * result + (convolved ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(maxPeriod);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minPeriod);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + npts;
        result = prime * result + ((component == null) ? 0 : component.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        temp = Double.doubleToLongBits(samplingHz);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(startTime);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((observer == null) ? 0 : observer.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BasicID other = (BasicID) obj;
        if (eventID == null) {
            if (other.eventID != null) return false;
        } else if (!eventID.equals(other.eventID)) return false;
        if (convolved != other.convolved) return false;
        if (Double.doubleToLongBits(maxPeriod) != Double.doubleToLongBits(other.maxPeriod)) return false;
        if (Double.doubleToLongBits(minPeriod) != Double.doubleToLongBits(other.minPeriod)) return false;
        if (npts != other.npts) return false;
        if (component != other.component) return false;
        if (type != other.type) return false;
        if (Double.doubleToLongBits(samplingHz) != Double.doubleToLongBits(other.samplingHz)) return false;
        if (Double.doubleToLongBits(startTime) != Double.doubleToLongBits(other.startTime)) return false;
        if (observer == null) {
            if (other.observer != null) return false;
        } else if (!observer.equals(other.observer)) return false;
        return true;
    }

    public WaveformType getWaveformType() {
        return type;
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getGlobalCMTID() {
        return eventID;
    }

    public SACComponent getSacComponent() {
        return component;
    }

    public Phase[] getPhases() {
        return phases;
    }

    /**
     * @return [s]
     */
    public double getStartTime() {
        return startTime;
    }

    /**
     * @return [s]
     */
    public double computeEndTime() {
        return startTime + (npts - 1) / samplingHz;
    }

    /**
     * @return Number of data points
     */
    public int getNpts() {
        return npts;
    }

    /**
     * @return Sampling Hz [hz]
     */
    public double getSamplingHz() {
        return samplingHz;
    }

    public double getMinPeriod() {
        return minPeriod;
    }

    public double getMaxPeriod() {
        return maxPeriod;
    }

    /**
     * @return If this ID is convolved
     */
    public boolean isConvolved() {
        return convolved;
    }

    public boolean containsData() {
        return data != null;
    }

    /**
     * @return Arrays of waveform data
     */
    public double[] getData() {
        return data.clone();
    }

    /**
     * @return Trace of the waveform for this ID.
     */
    public Trace toTrace() {
        double[] x = new double[data.length];
        Arrays.setAll(x, i -> startTime + i / samplingHz);
        return new Trace(x, data);
    }

    /**
     * @return
     * @since 2022/12/13
     * @author otsuru
     */
    public DataEntry toDataEntry() {
        return new DataEntry(eventID, observer, component);
    }

    @Override
    public String toString() {
        String basicString = observer.toPaddedInfoString() + " " + eventID.toPaddedString() + " " + component + " " + type + " "
                + MathAid.padToString(startTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.DECIMALS, false) + " "
                + npts + " " + samplingHz + " " + minPeriod + " " + maxPeriod + " "
                + TimewindowData.phasesAsString(phases) + " " + convolved;
        return basicString;
    }

}
