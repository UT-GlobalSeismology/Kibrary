package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Am = d の中の mのある成分
 * <p>
 * location (radius), type of parameter, weighting (width or volume or...)
 * <p>
 * The class implementing this must be <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @version 0.0.2.1.1
 * @author anselme add methods
 */
public interface UnknownParameter {

    /**
     * @return ({@link PartialType}) Type of parameter.
     */
    public PartialType getPartialType();

    public FullPosition getPosition();

    /**
     * Weighting for this parameter. It may be the thickness of a layer, the volume of voxel, and so on...
     *
     * @return (double) Weighting for this parameter.
     */
    public double getWeighting();

    public byte[] getBytes();

}
