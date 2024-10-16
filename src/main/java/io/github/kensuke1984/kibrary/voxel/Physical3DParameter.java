package io.github.kensuke1984.kibrary.voxel;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Elastic parameter in a 3-D voxel.
 * <p>
 * The size should be the volume of the voxel [km^3].
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class Physical3DParameter implements UnknownParameter {
    public static final int oneUnknownByte = 42;

    private static final ParameterType PARAMETER_TYPE = ParameterType.VOXEL;

    private final VariableType variableType;
    /**
     * Position of voxel to perturb.
     */
    private final FullPosition voxelPosition;
    private final double size;

    public static Physical3DParameter constructFromParts(String[] parts) {
        return new Physical3DParameter(VariableType.valueOf(parts[1]),
                new FullPosition(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4])),
                Double.parseDouble(parts[5]));
    }

    public Physical3DParameter(VariableType variableType, FullPosition voxelPosition, double size) {
        this.variableType = variableType;
        this.voxelPosition = voxelPosition;
        this.size = size;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(size);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((variableType == null) ? 0 : variableType.hashCode());
        result = prime * result + ((voxelPosition == null) ? 0 : voxelPosition.hashCode());
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
        Physical3DParameter other = (Physical3DParameter) obj;
        if (Double.doubleToLongBits(size) != Double.doubleToLongBits(other.size))
            return false;
        if (variableType != other.variableType)
            return false;
        if (voxelPosition == null) {
            if (other.voxelPosition != null)
                return false;
        } else if (!voxelPosition.equals(other.voxelPosition))
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

    @Override
    public FullPosition getPosition() {
        return voxelPosition;
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return PARAMETER_TYPE + " " + variableType + " " + voxelPosition + " " + size;
    }

    public byte[] getBytes() {
        byte[] part1 = StringUtils.rightPad(variableType.name(), 10).getBytes();
        byte[] loc1 = new byte[8];
        byte[] loc2 = new byte[8];
        byte[] loc3 = new byte[8];
        ByteBuffer.wrap(loc1).putDouble(voxelPosition.getLatitude());
        ByteBuffer.wrap(loc2).putDouble(voxelPosition.getLongitude());
        ByteBuffer.wrap(loc3).putDouble(voxelPosition.getR());
        byte[] sizeByte = new byte[8];
        ByteBuffer.wrap(sizeByte).putDouble(size);
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
            bytes[i + 34] = sizeByte[i];

        return bytes;
    }

    public static UnknownParameter create(byte[] bytes) {
        byte[] part1 = Arrays.copyOfRange(bytes, 0, 10);
        byte[] loc1 = Arrays.copyOfRange(bytes, 10, 18);
        byte[] loc2 = Arrays.copyOfRange(bytes, 18, 26);
        byte[] loc3 = Arrays.copyOfRange(bytes, 26, 34);
        byte[] sizeByte = Arrays.copyOfRange(bytes, 34, 42);

        VariableType variableType = VariableType.valueOf(new String(part1).trim());
        double latitude = ByteBuffer.wrap(loc1).getDouble();
        double longitude = ByteBuffer.wrap(loc2).getDouble();
        double r = ByteBuffer.wrap(loc3).getDouble();
        double size = ByteBuffer.wrap(sizeByte).getDouble();

        return new Physical3DParameter(variableType, new FullPosition(latitude, longitude, r), size);
    }

}
