package io.github.kensuke1984.kibrary.timewindow;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Timewindow data for an (event, observer, component) data pair.
 * <p>
 * Contains information of {@link GlobalCMTID}, {@link Observer}, {@link SACComponent}, and {@link Phase}s,
 * in addition to the start and end times specified in {@link Timewindow}.
 *
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since version 0.1.3
 */
public class TimewindowData extends Timewindow {

    /**
     * observer
     */
    private final Observer observer;
    /**
     * event ID
     */
    private final GlobalCMTID eventID;
    /**
     * component
     */
    private final SACComponent component;
    /**
     * seismic phases included in the timewindow (e.g. S, ScS)
     */
    private final Phase[] phases;

    public TimewindowData(double startTime, double endTime, Observer observer, GlobalCMTID eventID,
            SACComponent component, Phase[] phases) {
        super(startTime, endTime);
        this.eventID = eventID;
        this.component = component;
        this.observer = observer;
        this.phases = phases;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((component == null) ? 0 : component.hashCode());
        result = prime * result + ((eventID == null) ? 0 : eventID.hashCode());
        result = prime * result + ((observer == null) ? 0 : observer.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        TimewindowData other = (TimewindowData) obj;
        if (component != other.component) return false;
        if (eventID == null) {
            if (other.eventID != null) return false;
        } else if (!eventID.equals(other.eventID)) return false;
        if (observer == null) {
            if (other.observer != null) return false;
        } else if (!observer.equals(other.observer)) return false;
        return true;
    }

    @Override
    public int compareTo(Timewindow o) {
        if (!(o instanceof TimewindowData)) return super.compareTo(o);
        TimewindowData ot = (TimewindowData) o;
        int sta = getObserver().compareTo(ot.getObserver());
        if (sta != 0) return sta;
        int id = getGlobalCMTID().compareTo(ot.getGlobalCMTID());
        if (id != 0) return id;
        int comp = getComponent().compareTo(ot.getComponent());
        if (comp != 0) return comp;
        return super.compareTo(o);
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
     * @return (Phase array) Phases included in this timewindow
     * @author anselme
     */
    public Phase[] getPhases() {
        return phases;
    }

    public DataEntry toDataEntry() {
        return new DataEntry(eventID, observer, component);
    }

    @Override
    public String toString() {
        return observer.toPaddedInfoString() + " " + eventID.toPaddedString() + " " + component + " "
                + MathAid.padToString(startTime, TYPICAL_MAX_INTEGER_DIGITS, PRECISION, false) + " "
                + MathAid.padToString(endTime, TYPICAL_MAX_INTEGER_DIGITS, PRECISION, false) + " " + phasesAsString(phases);
    }

    /**
     * Change array of phases into a String for outputting in files. TODO There may be somewhere else to put this method.
     * @param phases (Phase array)
     * @return (String) Phase names connected with ",", or "null" when there are no phases.
     */
    public static String phasesAsString(Phase[] phases) {
        if (phases == null || phases.length == 0) {
            return "null";
        } else {
            List<String> phaseStrings = Stream.of(phases).filter(phase -> phase != null).map(Phase::toString).collect(Collectors.toList());
            return String.join(",", phaseStrings);
        }
    }

    /**TODO erase
     * @return
     * @author anselme
     */
    public double getAzimuthDegree() {
        return Math.toDegrees(eventID.getEventData().getCmtPosition().computeAzimuthRad(observer.getPosition()));
    }

    /**TODO erase
     * @return
     * @author anselme
     */
    public double getDistanceDegree() {
        return Math.toDegrees(eventID.getEventData().getCmtPosition().computeEpicentralDistanceRad(observer.getPosition()));
    }

}
