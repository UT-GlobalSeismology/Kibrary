package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Elastic parameter in a 1-D layer.
 * <p>
 * The weighting should be the thickness of the layer [km].
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since version 0.0.3
 */
public class Physical1DParameter implements UnknownParameter {

    private final PartialType partialType;
    private final double layerRadius;
    private final double weighting;

    public Physical1DParameter(PartialType partialType, double layerRadius, double weighting) {
        this.partialType = partialType;
        this.layerRadius = layerRadius;
        this.weighting = weighting;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
        long temp;
        temp = Double.doubleToLongBits(layerRadius);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(weighting);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Physical1DParameter other = (Physical1DParameter) obj;
        if (partialType != other.partialType) return false;
        return Double.doubleToLongBits(layerRadius) == Double.doubleToLongBits(other.layerRadius) &&
                Double.doubleToLongBits(weighting) == Double.doubleToLongBits(other.weighting);
    }

    @Override
    public PartialType getPartialType() {
        return partialType;
    }

    @Override
    public FullPosition getPosition() {
        return new FullPosition(0., 0., layerRadius);
    }

    public double getRadius() {
        return layerRadius;
    }

    @Override
    public double getWeighting() {
        return weighting;
    }

    @Override
    public String toString() {
        return partialType + " " + layerRadius + " " + weighting;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
        // TODO
    }
}
