package io.github.kensuke1984.kibrary.voxel;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Elastic parameter in a 3-D voxel.
 * <p>
 * The weighting should be the volume of the voxel [km^3].
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since version 0.0.3.1
 */
public class Physical3DParameter implements UnknownParameter {

    public static final int oneUnknownByte = 42;

    private final PartialType partialType;
    /**
     * Position of voxel to perturb.
     */
    private final FullPosition voxelPosition;
    private final double weighting;

    public Physical3DParameter(PartialType partialType, FullPosition voxelPosition, double weighting) {
        this.partialType = partialType;
        this.voxelPosition = voxelPosition;
        this.weighting = weighting;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
        result = prime * result + ((voxelPosition == null) ? 0 : voxelPosition.hashCode());
        long temp;
        temp = Double.doubleToLongBits(weighting);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Physical3DParameter other = (Physical3DParameter) obj;
        if (partialType != other.partialType) return false;
        if (voxelPosition == null) {
            if (other.voxelPosition != null) return false;
        } else if (!voxelPosition.equals(other.voxelPosition)) return false;
        return Double.doubleToLongBits(weighting) == Double.doubleToLongBits(other.weighting);
    }

    @Override
    public PartialType getPartialType() {
        return partialType;
    }

    @Override
    public FullPosition getPosition() {
        return voxelPosition;
    }

    @Override
    public double getWeighting() {
        return weighting;
    }

    @Override
    public String toString() {
        return partialType + " " + voxelPosition + " " + weighting;
    }

    public byte[] getBytes() {
        byte[] part1 = StringUtils.rightPad(partialType.name(), 10).getBytes();
        byte[] loc1 = new byte[8];
        byte[] loc2 = new byte[8];
        byte[] loc3 = new byte[8];
        ByteBuffer.wrap(loc1).putDouble(voxelPosition.getLatitude());
        ByteBuffer.wrap(loc2).putDouble(voxelPosition.getLongitude());
        ByteBuffer.wrap(loc3).putDouble(voxelPosition.getR());
        byte[] weightByte = new byte[8];
        ByteBuffer.wrap(weightByte).putDouble(weighting);
        byte[] bytes = new byte[oneUnknownByte];

        for (int i = 0; i < 10; i++)
            bytes[i] = part1[i];
        for (int i = 0; i < 8; i++)
            bytes[i + 10] = loc1[i];
        for (int i = 0; i < 8; i++)
            bytes[i + 18] = loc2[i];
        for (int i = 0; i < 8; i++)
            bytes[i + 26] = loc3[i];
        for (int i = 0; i < 8; i++)
            bytes[i + 34] = weightByte[i];

        return bytes;
    }

    public static UnknownParameter create(byte[] bytes) {
        byte[] part1 = Arrays.copyOfRange(bytes, 0, 10);
        byte[] loc1 = Arrays.copyOfRange(bytes, 10, 18);
        byte[] loc2 = Arrays.copyOfRange(bytes, 18, 26);
        byte[] loc3 = Arrays.copyOfRange(bytes, 26, 34);
        byte[] weightByte = Arrays.copyOfRange(bytes, 34, 42);

        PartialType partialType = PartialType.valueOf(new String(part1).trim());
        double latitude = ByteBuffer.wrap(loc1).getDouble();
        double longitude = ByteBuffer.wrap(loc2).getDouble();
        double r = ByteBuffer.wrap(loc3).getDouble();
        double weight = ByteBuffer.wrap(weightByte).getDouble();

        return new Physical3DParameter(partialType, new FullPosition(latitude, longitude, r), weight);
    }

}
