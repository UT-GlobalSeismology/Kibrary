package io.github.kensuke1984.kibrary.util.data;

import java.util.Arrays;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Each entry of record, specified by event, observer, component, and phases.
 *
 * @author rei
 * @since 2025/10/16
 */
public class RecordEntry extends DataEntry {

    private final Phase[] phases;

    public RecordEntry(GlobalCMTID event, Observer observer, SACComponent component, Phase[] phases) {
        super(event, observer, component);
        this.phases = phases;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((phases == null) ? 0 : Arrays.hashCode(phases));
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RecordEntry other = (RecordEntry) obj;

        if (!Arrays.equals(phases, other.phases))
            return false;

        return true;
    }

    @Override
    public int compareTo(DataEntry o) {
        int deCompare = super.compareTo(o);
        if (!(o instanceof RecordEntry)) return deCompare;
        if (deCompare != 0)
            return deCompare;
        RecordEntry ot = (RecordEntry) o;
        int phCompare = TimewindowData.phasesAsString(phases).compareTo(TimewindowData.phasesAsString(ot.phases));
        return phCompare;
    }

    public Phase[] getPhases() {
        return phases;
    }

    @Override
    public String toString() {
        return super.toString() + " " + TimewindowData.phasesAsString(phases);
    }

}