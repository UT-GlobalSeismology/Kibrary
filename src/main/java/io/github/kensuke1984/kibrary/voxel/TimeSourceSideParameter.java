package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * 時間シフトに対するパラメタ情報　du/dtに対するm (Am=d)における
 * イベント
 * <p>
 * sideにイベントを入れる
 *
 */
public class TimeSourceSideParameter implements UnknownParameter {

    private final PartialType partialType = PartialType.TIME_SOURCE;
    private final GlobalCMTID id;
    /**
     * location of the perturbation
     */
    private final FullPosition position;
    private final double size = 1.;

    public TimeSourceSideParameter(GlobalCMTID id) {
        this.id = id;
        this.position = id.getEventData().getCmtPosition();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        TimeSourceSideParameter other = (TimeSourceSideParameter) obj;
        if (partialType != other.partialType)
            return false;
        if (position == null) {
            if (other.position != null)
                return false;
        } else if (!position.equals(other.position))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (Double.doubleToLongBits(size) != Double.doubleToLongBits(other.size))
            return false;
        return true;
    }

    @Override
    public PartialType getPartialType() {
        return partialType;
    }

    public GlobalCMTID getGlobalCMTID() {
        return id;
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
        return partialType + " " + id + " " + size;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
