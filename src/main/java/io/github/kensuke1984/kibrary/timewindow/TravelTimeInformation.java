package io.github.kensuke1984.kibrary.timewindow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.sc.seis.TauP.Arrival;
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
    private final Map<Phase, Double> usePhaseTimes;
    private final Map<Phase, Double> avoidPhaseTimes;

    public TravelTimeInformation(GlobalCMTID event, Observer observer, Set<TauPPhase> usePhases, Set<TauPPhase> avoidPhases) {
        this.event = event;
        this.observer = observer;
        usePhaseTimes = new HashMap<>();
        usePhases.forEach(phase -> {
            // if there are phases with same name, keep the faster one (This is done because Map overwrites value of same key) TODO should try to keep all
            if (usePhaseTimes.containsKey(phase.getPhaseName()) && usePhaseTimes.get(phase.getPhaseName()) < phase.getTravelTime()) return;
            usePhaseTimes.put(phase.getPhaseName(), phase.getTravelTime());
        });
        avoidPhaseTimes = new HashMap<>();
        avoidPhases.forEach(phase -> {
            // if there are phases with same name, keep the faster one (This is done because Map overwrites value of same key) TODO should try to keep all
            if (avoidPhaseTimes.containsKey(phase.getPhaseName()) && avoidPhaseTimes.get(phase.getPhaseName()) < phase.getTravelTime()) return;
            avoidPhaseTimes.put(phase.getPhaseName(), phase.getTravelTime());
        });
    }
    public TravelTimeInformation(GlobalCMTID event, Observer observer, List<Arrival> useArrivals, List<Arrival> avoidArrivals) {
        this.event = event;
        this.observer = observer;
        usePhaseTimes = new HashMap<>();
        useArrivals.forEach(arrival -> {
            // if there are phases with same name, keep the faster one (This is done because Map overwrites value of same key) TODO should try to keep all
            Phase phase = Phase.create(arrival.getPhase().getName());
            if (usePhaseTimes.containsKey(phase) && usePhaseTimes.get(phase) < arrival.getTime()) return;
            usePhaseTimes.put(phase, arrival.getTime());
        });
        avoidPhaseTimes = new HashMap<>();
        avoidArrivals.forEach(arrival -> {
            // if there are phases with same name, keep the faster one (This is done because Map overwrites value of same key) TODO should try to keep all
            Phase phase = Phase.create(arrival.getPhase().getName());
            if (avoidPhaseTimes.containsKey(phase) && avoidPhaseTimes.get(phase) < arrival.getTime()) return;
            avoidPhaseTimes.put(phase, arrival.getTime());
        });
    }

    public TravelTimeInformation(GlobalCMTID event, Observer observer, Map<Phase, Double> usePhaseTimes, Map<Phase, Double> avoidPhaseTimes) {
        this.event = event;
        this.observer = observer;
        this.usePhaseTimes = usePhaseTimes;
        this.avoidPhaseTimes = avoidPhaseTimes;
    }

    /**
     * Returns travel time of a specified phase.
     * If data for that phase does not exist, null is returned.
     * @param phaseToFind
     * @return (Double) Travel time of a phase. null if it does not exist.
     */
    public Double timeOf(Phase phaseToFind) {
        for (Phase phase : usePhaseTimes.keySet()) {
            if (phase.equals(phaseToFind)) {
                return usePhaseTimes.get(phase);
            }
        }
        for (Phase phase : avoidPhaseTimes.keySet()) {
            if (phase.equals(phaseToFind)) {
                return avoidPhaseTimes.get(phase);
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

    public Map<Phase, Double> getUsePhases() {
        return new HashMap<>(usePhaseTimes);
    }

    public Map<Phase, Double> getAvoidPhases() {
        return new HashMap<>(avoidPhaseTimes);
    }


}
