/**
 *
 */
package io.github.kensuke1984.kibrary.util.spc;

import java.util.List;

import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.Location;

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
     * @return Location of a seismic source.
     */
    Location getSourceLocation();

    /**
     * @return ID of a source
     */
    String getSourceID();

    /**
     * @return ID of observer (station_network)
     */
    String getObserverID();

    /**
     * @return STATION code of the observer
     */
    String getStationCode();

    /**
     * @return NETWORK code of the observer
     * @author anselme
     */
    String getNetworkCode();

    /**
     * @return HorizontalPosition of an observer.
     */
    HorizontalPosition getObserverPosition();

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
