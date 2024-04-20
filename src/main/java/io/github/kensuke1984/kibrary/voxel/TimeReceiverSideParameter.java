package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * 時間シフトに対するパラメタ情報　du/dtに対するm (Am=d)における
 * 観測点名
 * <p>
 * sideに観測点名を入れる
 *
 * @author ?
 * @since a long time ago
 */
public class TimeReceiverSideParameter implements UnknownParameter {
    private static final ParameterType PARAMETER_TYPE = ParameterType.RECEIVER;

    private final VariableType variableType = VariableType.TIME;
    private final Observer observer;
    private final int bouncingOrder;
    private final double size = 1.;

    public static TimeReceiverSideParameter constructFromParts(String[] parts) {
        return new TimeReceiverSideParameter(new Observer(parts[2], parts[3],
                new HorizontalPosition(Double.parseDouble(parts[4]), Double.parseDouble(parts[5]))),
                Integer.parseInt(parts[6]));
    }

    public TimeReceiverSideParameter(Observer observer, int bouncingOrder) {
        this.observer = observer;
        this.bouncingOrder = bouncingOrder;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + bouncingOrder;
        result = prime * result + ((observer == null) ? 0 : observer.hashCode());
        long temp;
        temp = Double.doubleToLongBits(size);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((variableType == null) ? 0 : variableType.hashCode());
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
        if (bouncingOrder != other.bouncingOrder)
            return false;
        if (observer == null) {
            if (other.observer != null)
                return false;
        } else if (!observer.equals(other.observer))
            return false;
        if (Double.doubleToLongBits(size) != Double.doubleToLongBits(other.size))
            return false;
        if (variableType != other.variableType)
            return false;
        return true;
    }

    @Override
    public ParameterType getParameterType() {
        return PARAMETER_TYPE;
    }

    @Override
    public VariableType getVariableType() {
        return variableType;
    }

    public Observer getObserver() {
        return observer;
    }

    public int getBouncingOrder() {
        return bouncingOrder;
    }

    @Override
    public FullPosition getPosition() {
        return observer.getPosition().toFullPosition(0);
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return PARAMETER_TYPE + " " + variableType + " " + observer.toPaddedInfoString() + " " + bouncingOrder + " " + size;
    }
    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
