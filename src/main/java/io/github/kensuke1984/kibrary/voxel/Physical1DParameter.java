package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Elastic parameter in a 1-D layer.
 * <p>
 * The size should be the thickness of the layer [km].
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since version 0.0.3
 */
public class Physical1DParameter implements UnknownParameter {
    private static final ParameterType PARAMETER_TYPE = ParameterType.LAYER;

    private final VariableType variableType;
    private final double layerRadius;
    private final double size;

    public static Physical1DParameter constructFromParts(String[] parts) {
        return new Physical1DParameter(VariableType.valueOf(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }

    @Deprecated
    public Physical1DParameter(PartialType partialType, double layerRadius, double size) {
        this.variableType = partialType.toVariableType();
        this.layerRadius = layerRadius;
        this.size = size;
    }

    public Physical1DParameter(VariableType variableType, double layerRadius, double size) {
        this.variableType = variableType;
        this.layerRadius = layerRadius;
        this.size = size;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(layerRadius);
        result = prime * result + (int) (temp ^ (temp >>> 32));
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
        Physical1DParameter other = (Physical1DParameter) obj;
        if (Double.doubleToLongBits(layerRadius) != Double.doubleToLongBits(other.layerRadius))
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
        return PartialType.of(PARAMETER_TYPE, variableType);
    }

    @Override
    public ParameterType getParameterType() {
        return PARAMETER_TYPE;
    }

    @Override
    public VariableType getVariableType() {
        return variableType;
    }

    @Override
    public FullPosition getPosition() {
        return new FullPosition(0., 0., layerRadius);
    }

    public double getRadius() {
        return layerRadius;
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return PARAMETER_TYPE + " " + variableType + " " + layerRadius + " " + size;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
