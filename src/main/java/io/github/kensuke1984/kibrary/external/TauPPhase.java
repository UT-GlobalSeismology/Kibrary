/**
 * 
 */
package io.github.kensuke1984.kibrary.external;

import io.github.kensuke1984.anisotime.Phase;

/**
 * 
 * The output from taup_time.
 * 
 * This class is <b>immutable</b>
 * 
 * 
 * @author Kensuke Konishi
 * @version 0.0.2
 */
public class TauPPhase {

	TauPPhase(double distance, double depth, Phase phaseName, double travelTime, double rayParameter, double takeoff,
			double incident, double puristDistance, String puristName) {
		this.distance = distance;
		this.depth = depth;
		this.phaseName = phaseName;
		this.travelTime = travelTime;
		this.rayParameter = rayParameter;
		this.takeoff = takeoff;
		this.incident = incident;
		this.puristDistance = puristDistance;
		this.puristName = puristName;
	}

	/**
	 * epicentral distance (deg)
	 */
	private final double distance;

	/**
	 * source depth not radius (km)
	 */
	private final double depth;

	private final Phase phaseName;

	/**
	 * travel time (s)
	 */
	private final double travelTime;

	/**
	 * ray parameter (s/deg)
	 */
	private final double rayParameter;

	/**
	 * takeoff (deg)
	 */
	private final double takeoff;

	/**
	 * incident (deg)
	 */
	private final double incident;

	/**
	 * purist distance
	 */
	private final double puristDistance;

	/**
	 * purist name
	 */
	private final String puristName;

	public double getDistance() {
		return distance;
	}

	public double getDepth() {
		return depth;
	}

	public Phase getPhaseName() {
		return phaseName;
	}

	public double getTravelTime() {
		return travelTime;
	}

	public double getRayParameter() {
		return rayParameter;
	}

	public double getTakeoff() {
		return takeoff;
	}

	public double getIncident() {
		return incident;
	}

	public double getPuristDistance() {
		return puristDistance;
	}

	public String getPuristName() {
		return puristName;
	}

}
