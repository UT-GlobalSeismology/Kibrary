package io.github.kensuke1984.kibrary.waveform;

import java.util.Arrays;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * <p>
 * ID for observed and synthetic waveform
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
 * <li> Horizontal position of station (latitude longitude) </li>
 * <li> Global CMT ID </li>
 * <li> Component (ZRT) </li>
 * <li> Period minimum and maximum </li>
 * <li> Start time </li>
 * <li> Number of points </li>
 * <li> Sampling Hz </li>
 * <li> If it is either convolved or observed, true </li>
 * <li> Position of a waveform for the ID </li>
 * </ul>
 *
 */
public class BasicID {

    protected final WaveformType TYPE;
    protected final double SAMPLINGHZ;
    protected final double START_TIME;
    protected final int NPTS;
    protected final Observer observer;
    protected final GlobalCMTID event;
    protected final SACComponent COMPONENT;
    protected final Phase[] PHASES;
    /**
     * [s] if the data has not been applied a filter, 0
     */
    protected final double MIN_PERIOD;
    /**
     * [s] if the data has not been applied a filter, {@link Double#POSITIVE_INFINITY}
     */
    protected final double MAX_PERIOD;
    /**
     * byte where this data starts
     */
    protected final long START_BYTE;
    protected final boolean CONVOLUTE;
    /**
     * waveform
     */
    private final double[] DATA;

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
     * @param convolute    If the data is convolute.
     * @param waveformData the waveform data for this ID.
     */
    public BasicID(WaveformType waveFormType, double samplingHz, double startTime, int npts, Observer observer,
            GlobalCMTID globalCMTID, SACComponent sacComponent, double minPeriod, double maxPeriod, Phase[] phases, long startByte,
            boolean convolute, double... waveformData) {
        this.TYPE = waveFormType;
        this.SAMPLINGHZ = Precision.round(samplingHz, 3);
        this.START_TIME = Precision.round(startTime, 3);
        this.NPTS = npts;
        this.observer = observer;
        this.event = globalCMTID;
        this.COMPONENT = sacComponent;
        this.PHASES = phases;
        this.MIN_PERIOD = Precision.round(minPeriod, 3);
        this.MAX_PERIOD = Precision.round(maxPeriod, 3);
        this.START_BYTE = startByte;
        this.CONVOLUTE = convolute;
        if (waveformData.length != 0 && waveformData.length != npts)
            throw new IllegalArgumentException("Input waveform data length is invalid");
        this.DATA = waveformData.clone();
    }

    public boolean containsData() {
        return DATA != null;
    }

    /**
     * @return Arrays of waveform data
     */
    public double[] getData() {
        return DATA.clone();
    }

     /**
     * A new BasicID with the input data will be returned.
     *
     * @param data Waveform data to be replaced
     * @return BasicID with the input data
     */
    public BasicID setData(double[] data) {
        return new BasicID(TYPE, SAMPLINGHZ, START_TIME, NPTS, observer, event, COMPONENT, MIN_PERIOD,
                MAX_PERIOD, PHASES, START_BYTE, CONVOLUTE, data);
    }

    @Override
    public String toString() {
        String basicString = observer.getPaddedInfoString() + " " + event.getPaddedString() + " "
                + COMPONENT + " " + TYPE + " " + START_TIME + " " + NPTS + " " + SAMPLINGHZ + " " + MIN_PERIOD
                + " " + MAX_PERIOD + " ";
        if (PHASES == null)
            basicString += "null" + " ";
        else if (PHASES.length == 1)
            basicString += PHASES[PHASES.length - 1] + " ";
        else if (PHASES.length > 1) {
            for (int i = 0; i < PHASES.length - 1; i++)
                basicString += PHASES[i] + ",";
            basicString += PHASES[PHASES.length - 1] + " ";
        }
        basicString += START_BYTE + " " + CONVOLUTE;
        return basicString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((event == null) ? 0 : event.hashCode());
        result = prime * result + (CONVOLUTE ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(MAX_PERIOD);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(MIN_PERIOD);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + NPTS;
        result = prime * result + ((COMPONENT == null) ? 0 : COMPONENT.hashCode());
        result = prime * result + ((TYPE == null) ? 0 : TYPE.hashCode());
        temp = Double.doubleToLongBits(SAMPLINGHZ);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(START_TIME);
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
        if (CONVOLUTE != other.CONVOLUTE) return false;
        if (Double.doubleToLongBits(MAX_PERIOD) != Double.doubleToLongBits(other.MAX_PERIOD)) return false;
        if (Double.doubleToLongBits(MIN_PERIOD) != Double.doubleToLongBits(other.MIN_PERIOD)) return false;
        if (NPTS != other.NPTS) return false;
        if (COMPONENT != other.COMPONENT) return false;
        if (TYPE != other.TYPE) return false;
        if (Double.doubleToLongBits(SAMPLINGHZ) != Double.doubleToLongBits(other.SAMPLINGHZ)) return false;
        if (Double.doubleToLongBits(START_TIME) != Double.doubleToLongBits(other.START_TIME)) return false;
        if (observer == null) {
            if (other.observer != null) return false;
        } else if (!observer.equals(other.observer)) return false;
        return true;
    }


    public WaveformType getWaveformType() {
        return TYPE;
    }

    /**
     * @return Sampling Hz [hz]
     */
    public double getSamplingHz() {
        return SAMPLINGHZ;
    }

    /**
     * @return [s]
     */
    public double getStartTime() {
        return START_TIME;
    }

    /**
     * @return Number of data points
     */
    public int getNpts() {
        return NPTS;
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getGlobalCMTID() {
        return event;
    }

    public SACComponent getSacComponent() {
        return COMPONENT;
    }

    public double getMinPeriod() {
        return MIN_PERIOD;
    }

    public double getMaxPeriod() {
        return MAX_PERIOD;
    }

    public Phase[] getPhases() {
        return PHASES;
    }

    /**
     * If this is 100, then the data for this ID starts from 100th byte in the file.
     * @return [byte]
     */
    public long getStartByte() {
        return START_BYTE;
    }

    /**
     * @return If this ID is convolute.
     */
    public boolean isConvolute() {
        return CONVOLUTE;
    }

    /**
     * @return Trace of the waveform for this ID.
     */
    public Trace getTrace() {
        double[] x = new double[DATA.length];
        Arrays.setAll(x, i -> START_TIME + i / SAMPLINGHZ);
        return new Trace(x, DATA);
    }

}
