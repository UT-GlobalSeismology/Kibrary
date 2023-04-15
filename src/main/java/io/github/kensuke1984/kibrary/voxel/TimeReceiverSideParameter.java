package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * 時間シフトに対するパラメタ情報　du/dtに対するm (Am=d)における
 * 観測点名
 * <p>
 * sideに観測点名を入れる
 *
 */
public class TimeReceiverSideParameter implements UnknownParameter {

    private final PartialType partialType = PartialType.TIME_RECEIVER;
    private final Observer observer;
    /**
     * location of the perturbation
     */
    private final FullPosition position;
    private final int bouncingOrder;
    private final double size = 1.;

    public TimeReceiverSideParameter(Observer station, int bouncingOrder) {
        this.observer = station;
        this.position = new FullPosition(station.getPosition().getLatitude(),
                station.getPosition().getLongitude(), 0.);
        this.bouncingOrder = bouncingOrder;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
        result = prime * result + ((observer == null) ? 0 : observer.hashCode());
        result = prime * result + bouncingOrder;
        long temp;
        temp = Double.doubleToLongBits(size);
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
        if (position == null) {
            if (other.position != null)
                return false;
        } else if (!position.equals(other.position))
            return false;
        if (observer == null) {
            if (other.observer != null)
                return false;
        } else if (!observer.equals(other.observer))
            return false;
        if (bouncingOrder != other.bouncingOrder)
            return false;
        if (Double.doubleToLongBits(size) != Double.doubleToLongBits(other.size))
            return false;
        return true;
    }

    @Override
    public PartialType getPartialType() {
        return partialType;
    }

    public Observer getObserver() {
        return observer;
    }

    public int getBouncingOrder() {
        return bouncingOrder;
    }

    @Override
    public FullPosition getPosition() {
        return position;
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return partialType + " " + observer.toPaddedInfoString() + " " + bouncingOrder + " " + size;
    }
    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
