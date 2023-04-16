package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.elastic.VariableType;
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
    private static final ParameterType PARAMETER_TYPE = ParameterType.SOURCE;

    private final VariableType variableType = VariableType.TIME;
    private final GlobalCMTID id;
    private final double size = 1.;

    public static TimeSourceSideParameter constructFromParts(String[] parts) {
        return new TimeSourceSideParameter(new GlobalCMTID(parts[2]));
    }

    public TimeSourceSideParameter(GlobalCMTID id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        TimeSourceSideParameter other = (TimeSourceSideParameter) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (Double.doubleToLongBits(size) != Double.doubleToLongBits(other.size))
            return false;
        if (variableType != other.variableType)
            return false;
        return true;
    }

    @Override
    @Deprecated
    public PartialType getPartialType() {
        return variableType.toPartialType(PARAMETER_TYPE);
    }

    @Override
    public ParameterType getParameterType() {
        return PARAMETER_TYPE;
    }

    @Override
    public VariableType getVariableType() {
        return variableType;
    }

    public GlobalCMTID getGlobalCMTID() {
        return id;
    }

    @Override
    public FullPosition getPosition() {
        return id.getEventData().getCmtPosition();
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return PARAMETER_TYPE + " " + variableType + " " + id + " " + size;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
