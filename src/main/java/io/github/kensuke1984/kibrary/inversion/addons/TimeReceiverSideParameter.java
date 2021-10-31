package io.github.kensuke1984.kibrary.inversion.addons;

import io.github.kensuke1984.kibrary.inversion.UnknownParameter;
import io.github.kensuke1984.kibrary.util.Location;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

public class TimeReceiverSideParameter implements UnknownParameter {
	public Location getPointLocation() {
		return pointLocation;
	}

	@Override
	public String toString() {
		return partialType + " " + station.getStation() + " " + station.getNetwork() + " " + station.getPosition() + " " + bouncingOrder + " " + weighting;
	}

	private final PartialType partialType = PartialType.TIME_RECEIVER;
	private final double weighting = 1.;

	public TimeReceiverSideParameter(Observer station, int bouncingOrder) {
		this.station = station;
		this.pointLocation = new Location(station.getPosition().getLatitude(), 
				station.getPosition().getLongitude(), 0.);
		this.bouncingOrder = bouncingOrder;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
		result = prime * result + ((station == null) ? 0 : station.hashCode());
		result = prime * result + bouncingOrder;
		long temp;
		temp = Double.doubleToLongBits(weighting);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		TimeReceiverSideParameter other = (TimeReceiverSideParameter) obj;
		if (partialType != other.partialType)
			return false;
		if (pointLocation == null) {
			if (other.pointLocation != null)
				return false;
		} else if (!pointLocation.equals(other.pointLocation))
			return false;
		if (station == null) {
			if (other.station != null)
				return false;
		} else if (!station.equals(other.station))
			return false;
		if (bouncingOrder != other.bouncingOrder)
			return false;
		if (Double.doubleToLongBits(weighting) != Double.doubleToLongBits(other.weighting))
			return false;
		return true;
	}

	/**
	 * location of the perturbation
	 */
	private final Location pointLocation;
	
	private final Observer station;
	
	private final int bouncingOrder;
	
	public Observer getStation() {
		return station;
	}
	
	public int getBouncingOrder() {
		return bouncingOrder;
	}

	@Override
	public double getWeighting() {
		return weighting;
	}

	@Override
	public PartialType getPartialType() {
		return partialType;
	}
	
	@Override
	public Location getLocation() {
		return pointLocation;
	}
	
	@Override
	public byte[] getBytes() {
		return new byte[0];
		// TODO
	}
}
