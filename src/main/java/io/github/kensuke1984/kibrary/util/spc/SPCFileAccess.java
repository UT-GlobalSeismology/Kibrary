/**
 *
 */
package io.github.kensuke1984.kibrary.util.spc;

import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Data of DSM write
 *
 * @author Kensuke Konishi
 * @version 0.0.1
 * @author anselme add network
 */
public interface SPCFileAccess {

    /**
     * @return number of bodies
     */
    int nbody();

    /**
     * @return list of spc bodies
     */
    List<SPCBody> getSpcBodyList();

    void setSpcBody(int i, SPCBody body);

    /**
     * @return array of body Rs
     */
    double[] getBodyR();

    /**
     * @return (FullPosition) Position of a seismic source.
     */
    FullPosition getSourcePosition();

    /**
     * @return ID of a source
     */
    String getSourceID();

    /**
     * @return ID of observer (station_network)
     */
    String getReceiverID();

    /**
     * @return HorizontalPosition of an observer.
     */
    HorizontalPosition getReceiverPosition();

    /**
     * @return length of time
     */
    double tlen();

    /**
     * @return number of steps in frequency domain.
     */
    int np();

    /**
     * @return OMEGAI
     */
    double omegai();

    /**
     * @return SPCType of this
     */
    SPCType getSpcFileType();


    SPCFileName getSpcFileName();
}
