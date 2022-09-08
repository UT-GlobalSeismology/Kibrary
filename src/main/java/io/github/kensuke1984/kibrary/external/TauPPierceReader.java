package io.github.kensuke1984.kibrary.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Pierce;
import edu.sc.seis.TauP.TimeDist;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.Raypath;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Utility class to handle with {@link TauP_Pierce} in TauP package.
 * <p>
 * A set of dataEntries shall be given as input all at once to optimize computation time.
 * For each dataEntry, multiple raypaths may be created.
 *
 * @see <a href=http://www.seis.sc.edu/taup/>TauP</a>
 *
 * @author Kensuke Konishi
 * @since version 0.3.2.1
 * @version 2022/9/8
 */
public final class TauPPierceReader {

    private TauP_Pierce timeTool;
    private Map<DataEntry, List<Raypath>> entryMap;

    public TauPPierceReader(String structureName, String phaseName) throws TauModelException {
        this(structureName, phaseName, null);
    }

    public TauPPierceReader(String structureName, String[] phaseNames) throws TauModelException {
        this(structureName, phaseNames, null);
    }

    public TauPPierceReader(String structureName, String phaseName, double[] pierceRadii) throws TauModelException {
        timeTool = new TauP_Pierce(structureName);

        String[] phaseNames = {phaseName};
        timeTool.setPhaseNames(phaseNames);

        if (pierceRadii != null) {
            String[] pierceDepthStrings = DoubleStream.of(pierceRadii).mapToObj(r -> String.valueOf(Earth.EARTH_RADIUS - r)).toArray(String[]::new);
            timeTool.setAddDepths(String.join(",", pierceDepthStrings));
        }
    }

    public TauPPierceReader(String structureName, String[] phaseNames, double[] pierceRadii) throws TauModelException {
        timeTool = new TauP_Pierce(structureName);

        timeTool.setPhaseNames(phaseNames);

        if (pierceRadii != null) {
            String[] pierceDepthStrings = DoubleStream.of(pierceRadii).mapToObj(r -> String.valueOf(Earth.EARTH_RADIUS - r)).toArray(String[]::new);
            timeTool.setAddDepths(String.join(",", pierceDepthStrings));
        }
    }

    /**
     * Compute pierce points for all given entries.
     * @param entrySet (Set of DataEntry) Entries to compute pierce points for
     * @throws TauModelException
     */
    public void compute(Set<DataEntry> entrySet) throws TauModelException {
        System.err.println("Computing pierce points using TauP");

        // compute for each event
        // computation for each event is gathered together because changing the source depth is time consuming
        Set<GlobalCMTID> events = entrySet.stream().map(entry -> entry.getEvent()).collect(Collectors.toSet());
        for (GlobalCMTID event : events) {

            // set event depth
            FullPosition eventPosition = event.getEventData().getCmtLocation();
            timeTool.setSourceDepth(eventPosition.getDepth());

            // collect entries for this event, and run computation for each
            Set<DataEntry> eventEntries = entrySet.stream().filter(entry -> entry.getEvent().equals(event)).collect(Collectors.toSet());
            for (DataEntry entry : eventEntries) {
                HorizontalPosition observerPosition = entry.getObserver().getPosition();
                double distance = eventPosition.computeEpicentralDistance(observerPosition);
                timeTool.calculate(distance);
                List<Arrival> arrivals = timeTool.getArrivals();

                // convert Arrival to Raypath
                List<Raypath> raypaths = new ArrayList<>();
                for (Arrival arrival : arrivals) {
                    raypaths.add(convertToRaypath(eventPosition, observerPosition, arrival));
                }

                // add results to Map
                entryMap.put(entry, raypaths);
            }

        }

    }

    private static Raypath convertToRaypath(FullPosition eventPosition, HorizontalPosition observerPosition, Arrival arrival) {
        double azimuthDeg = eventPosition.computeAzimuthDeg(observerPosition);
        String phaseName = arrival.getName();
        TimeDist[] pierces = arrival.getPierce();
        int nPierce = pierces.length;

        double[] distancesDeg = new double[nPierce];
        List<FullPosition> positions = new ArrayList<>();
        for (int i = 0; i < nPierce; i++) {
            distancesDeg[i] = pierces[i].getDistDeg();
            double radius = Earth.EARTH_RADIUS - pierces[i].getDepth();
            HorizontalPosition position = eventPosition.pointAlongAzimuth(azimuthDeg, distancesDeg[i]);
            positions.add(position.toFullPosition(radius));
        }

        Raypath raypath = new Raypath(phaseName, distancesDeg, positions);
        return raypath;
    }

    /**
     * Whether raypaths for a specified entry exist.
     * @param entry (DataEntry) DataEntry to check.
     * @return (boolean)
     */
    public boolean hasRaypaths(DataEntry entry) {
        List<Raypath> raypathList = entryMap.get(entry);
        return (raypathList.size() > 0);
    }

    /**
     * Get all raypaths of all dataEntries (one dataEntry may have multiple raypaths).
     * @return (List of Raypath)
     */
    public List<Raypath> getAll() {
        return entryMap.values().stream().flatMap(list -> list.stream()).collect(Collectors.toList());
    }

    /**
     * Get all raypaths of a specific dataEntry.
     * @param entry (DataEntry) DataEntry to get raypaths for.
     * @return (List of Raypath)
     */
    public List<Raypath> get(DataEntry entry) {
        return entryMap.get(entry);
    }

    /**
     * Get specific raypath of a specific dataEntry.
     * @param entry (DataEntry) DataEntry to get raypath for.
     * @param index (int) Which raypath to get (0:first, 1:second, ...)
     * @return (Raypath)
     */
    public Raypath get(DataEntry entry, int index) {
        return entryMap.get(entry).get(index);
    }

}
