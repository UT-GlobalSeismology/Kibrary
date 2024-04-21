package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Am = d の中の mのある成分
 * <p>
 * position (radius), type of parameter, size (thickness or volume or...)
 * <p>
 * The class implementing this must be <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public interface UnknownParameter {

    /**
     * @return ({@link ParameterType}) Type of parameter.
     */
    public ParameterType getParameterType();

    /**
     * @return ({@link VariableType}) Type of variable of parameter.
     */
    public VariableType getVariableType();

    public FullPosition getPosition();

    /**
     * Spatial size for this parameter. It may be the thickness of layer, the volume of voxel, and so on...
     *
     * @return (double) Spatial size for this parameter.
     */
    public double getSize();

    public byte[] getBytes();

}
