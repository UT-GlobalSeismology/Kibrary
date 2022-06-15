package io.github.kensuke1984.kibrary.util.data;

import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Each entry of dataset, specified by event, observer, and component.
 *
 * @author otsuru
 * @since 2022/5/25
 */
public class DataEntry implements Comparable<DataEntry> {

    private final GlobalCMTID event;
    private final Observer observer;
    private final SACComponent component;

    public DataEntry(GlobalCMTID event, Observer observer, SACComponent component) {
        this.event = event;
        this.observer = observer;
        this.component = component;
    }

    /**
     * Sorting order is event &rarr; observer.
     */
    @Override
    public int compareTo(DataEntry o) {
        int evCompare = event.compareTo(o.event);
        if (evCompare != 0)
            return evCompare;
        int obCompare = observer.compareTo(o.observer);
        return obCompare != 0 ? obCompare : component.compareTo(o.component);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((component == null) ? 0 : component.hashCode());
        result = prime * result + ((event == null) ? 0 : event.hashCode());
        result = prime * result + ((observer == null) ? 0 : observer.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DataEntry other = (DataEntry) obj;

        if (component != other.component)
            return false;

        if (event == null) {
            if (other.event != null)
                return false;
        } else if (!event.equals(other.event))
            return false;

        if (observer == null) {
            if (other.observer != null)
                return false;
        } else if (!observer.equals(other.observer))
            return false;

        return true;
    }

    public GlobalCMTID getEvent() {
        return event;
    }

    public Observer getObserver() {
        return observer;
    }

    public SACComponent getComponent() {
        return component;
    }

    @Override
    public String toString() {
        return event + " " + observer.getPaddedInfoString() + " " + component;
    }

}
