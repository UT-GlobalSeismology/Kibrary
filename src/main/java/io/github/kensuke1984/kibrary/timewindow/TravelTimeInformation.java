package io.github.kensuke1984.kibrary.timewindow;

import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.external.TauPPhase;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Travel times of a set of phases for a certain event-observer pair.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * {@link TauPPhase} is immutable, so its Set can be cloned using the copy constructor of HashSet.
 *
 * @author otsuru
 * @since 2022/7/23
 */
public final class TravelTimeInformation {

    private final GlobalCMTID event;
    private final Observer observer;
    private final Set<TauPPhase> usePhases;
    private final Set<TauPPhase> avoidPhases;

    public TravelTimeInformation(GlobalCMTID event, Observer observer, Set<TauPPhase> usePhases, Set<TauPPhase> avoidPhases) {
        this.event = event;
        this.observer = observer;
        this.usePhases = new HashSet<>(usePhases);
        this.avoidPhases = new HashSet<>(avoidPhases);
   }

    /**
     * Returns data for a specified phase.
     * If data for that phase does not exist, null is returned.
     * @param phaseToFind
     * @return (TauPPhase) Information for a phase. null if it does not exist.
     */
    public TauPPhase dataFor(Phase phaseToFind) {
        for (TauPPhase phase : usePhases) {
            if (phase.getPhaseName().equals(phaseToFind)) {
                return phase;
            }
        }
        for (TauPPhase phase : avoidPhases) {
            if (phase.getPhaseName().equals(phaseToFind)) {
                return phase;
            }
        }
        return null;
    }

    public GlobalCMTID getEvent() {
        return event;
    }

    public Observer getObserver() {
        return observer;
    }

    public Set<TauPPhase> getUsePhases() {
        return new HashSet<>(usePhases);
    }

    public Set<TauPPhase> getAvoidPhases() {
        return new HashSet<>(avoidPhases);
    }


}
