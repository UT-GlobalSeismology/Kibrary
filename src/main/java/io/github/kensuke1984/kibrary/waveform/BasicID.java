package io.github.kensuke1984.kibrary.waveform;

import java.util.Arrays;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * <p>
 * ID and waveform data for a pair of event and observer of observed and synthetic waveforms.
 * </p>
 * This class is <b>IMMUTABLE</b> <br>
 * <p>
 * Double values will be rounded off to 3rd decimal places. <br>
 * (Those are stored as Float in the file)<br>
 * <p>
 * Contents of information for one ID:
 * <ul>
 * <li> Whether it is observed(true) or synthetic(false) </li>
 * <li> Name of station </li>
 * <li> Name of network </li>
 * <li> Horizontal position of observer (latitude longitude) </li>
 * <li> Global CMT ID </li>
 * <li> Component (ZRT) </li>
 * <li> Period minimum and maximum </li>
 * <li> Start time </li>
 * <li> Number of points </li>
 * <li> Sampling Hz </li>
 * <li> If it is either convolved or observed, true </li>
 * <li> Position of a waveform for the ID </li>
 * </ul>
 * <p>
 * Caution: A BasicID instance may or may not hold waveform data, dependeing on whether it has already been set.
 *
 * @since a long time ago
 * @author Kensuke Konishi
 */
public class BasicID {

    /**
     * Margin to decide whether two IDs have the same minPeriod or maxPeriod.
     * Period value should be around 5~200, so a value around 0.1 for epsilon should be enough.
     */
    public static final double PERIOD_EPSILON = 0.1;

    protected final WaveformType type;
    protected final double samplingHz;
    protected final double startTime;
    protected final int npts;
    protected final Observer observer;
    protected final GlobalCMTID event;
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
    /**
     * byte where this data starts
     */
    protected final long startByte;
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
     * @param globalCMTID  Event ID for the data.
     * @param sacComponent Component of the data.
     * @param minPeriod    [s] minimum period of the applied filter if none, 0
     * @param maxPeriod    [s] minimum period of the applied filter if none, {@link Double#POSITIVE_INFINITY}
     * @param phases	   Array of phases
     * @param startByte    [byte] where the waveform data for this ID starts in the file
     * @param convolved    If the data is convolved.
     * @param waveformData the waveform data for this ID.
     */
    public BasicID(WaveformType waveFormType, double samplingHz, double startTime, int npts, Observer observer,
            GlobalCMTID globalCMTID, SACComponent sacComponent, double minPeriod, double maxPeriod, Phase[] phases, long startByte,
            boolean convolved, double... waveformData) {
        this.type = waveFormType;
        this.samplingHz = Precision.round(samplingHz, 3);
        this.startTime = Precision.round(startTime, 3);
        this.npts = npts;
        this.observer = observer;
        this.event = globalCMTID;
        this.component = sacComponent;
        this.phases = phases;
        this.minPeriod = Precision.round(minPeriod, 3);
        this.maxPeriod = Precision.round(maxPeriod, 3);
        this.startByte = startByte;
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
        return new BasicID(type, samplingHz, startTime, npts, observer, event, component, minPeriod,
                maxPeriod, phases, startByte, convolved, data);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((event == null) ? 0 : event.hashCode());
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
     * The startPoint is ignored.
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BasicID other = (BasicID) obj;
        if (event == null) {
            if (other.event != null) return false;
        } else if (!event.equals(other.event)) return false;
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

    /**
     * Decides whether two IDs (BasicID and/or PartialID) are pairs. (Note that {@link PartialID} extends {@link BasicID}.)
     * They are regarded as same if observer, globalCMTID, component, npts, sampling Hz, start time, max & min period are same.
     * This method ignores whether the input IDs are observed or synthetic. It also ignores the Phases.
     *
     * @param id0 {@link BasicID}
     * @param id1 {@link BasicID}
     * @return if the IDs are same
     */
    public static boolean isPair(BasicID id0, BasicID id1) {
        boolean res = id0.getObserver().equals(id1.getObserver()) && id0.getGlobalCMTID().equals(id1.getGlobalCMTID())
                && id0.getSacComponent() == id1.getSacComponent() && id0.getNpts() == id1.getNpts()
                && id0.getSamplingHz() == id1.getSamplingHz()
                && Precision.equals(id0.getStartTime(), id1.getStartTime(), TimewindowData.TIME_SHIFT_MAX)
                && Precision.equals(id0.getMaxPeriod(), id1.getMaxPeriod(), PERIOD_EPSILON)
                && Precision.equals(id0.getMinPeriod(), id1.getMinPeriod(), PERIOD_EPSILON);
        return res;
    }


    public WaveformType getWaveformType() {
        return type;
    }

    /**
     * @return Sampling Hz [hz]
     */
    public double getSamplingHz() {
        return samplingHz;
    }

    /**
     * @return [s]
     */
    public double getStartTime() {
        return startTime;
    }

    /**
     * @return Number of data points
     */
    public int getNpts() {
        return npts;
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getGlobalCMTID() {
        return event;
    }

    public SACComponent getSacComponent() {
        return component;
    }

    public double getMinPeriod() {
        return minPeriod;
    }

    public double getMaxPeriod() {
        return maxPeriod;
    }

    public Phase[] getPhases() {
        return phases;
    }

    /**
     * If this is 100, then the data for this ID starts from 100th byte in the file.
     * @return [byte]
     */
    public long getStartByte() {
        return startByte;
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

    @Override
    public String toString() {
        String basicString = observer.toPaddedInfoString() + " " + event.toPaddedString() + " " + component + " "
                + type + " " + startTime + " " + npts + " " + samplingHz + " " + minPeriod + " " + maxPeriod + " "
                + TimewindowData.phasesAsString(phases) + " " + startByte + " " + convolved;
        return basicString;
    }

}
