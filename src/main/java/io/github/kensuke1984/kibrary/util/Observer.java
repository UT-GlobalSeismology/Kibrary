package io.github.kensuke1984.kibrary.util;


import java.nio.ByteBuffer;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.firsthandler.DataKitchen;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderData;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * <p>
 * Information of observer,
 * consisting of station code, network code, and {@link HorizontalPosition}. <br>
 * <p>
 * This class is <b>IMMUTABLE</b>
 * </p>
 * <p>
 * Station code and network code must be 8 or less letters.
 * (This is set at 8 letters probably because alphanumeric fields in SAC data format are 8 letters.)
 * <p>
 * Observers are considered equal if and only if
 * [network code is equal && station code is equal && position is {@link #equal(HorizontalPosition, HorizontalPosition)}].
 * If the network code is 'DSM', comparison of networks between instances is not done;
 * station code and horizontal position is considered.
 *
 */
public class Observer implements Comparable<Observer> {

    /**
     * network code for stations in synthetic datasets
     */
    public static final String SYN = "DSM";
    /**
     * The number of decimal places to round off coordinates
     * when judging whether observers (with same network and station) are in the same position.
     * It is OK if the value is different from {@link DataKitchen#coordinateGrid}.
     */
    public static final int COORDINATE_SCALE = 2;

    /**
     * network code
     */
    private final String network;
    /**
     * station code
     */
    private final String station;
    /**
     * the {@link HorizontalPosition} of the station
     */
    private final HorizontalPosition position;

    /**
     * @param stationName Name of the station (must be 8 or less letters)
     * @param network     Name of the network of the station (must be 8 or less letters)
     * @param position    Horizontal POSITION of the station
     */
    public Observer(String stationName, HorizontalPosition position, String network) {
        if (8 < stationName.length() || 8 < network.length())
            throw new IllegalArgumentException("Both station and network name must be 8 or less letters.");
        this.station = stationName;
        this.network = network;
        this.position = position;
    }

    public Observer(String observerID, HorizontalPosition position) {
        this(observerID.split("_")[0], position, observerID.split("_")[1]);
    }

    public Observer(Observer station) {
        this.station = station.station;
        this.network = station.network;
        this.position = station.position;
    }

    /**
     * @param sacHeaderData header data
     * @return Station of the input sacHeaderData
     */
    public static Observer of(SACHeaderData sacHeaderData) {
        return sacHeaderData.getSACString(SACHeaderEnum.KNETWK) == "-12345"
                ? new Observer(sacHeaderData.getSACString(SACHeaderEnum.KSTNM).trim(),
                        new HorizontalPosition(sacHeaderData.getValue(SACHeaderEnum.STLA),
                                sacHeaderData.getValue(SACHeaderEnum.STLO)),
                        SYN)
                : new Observer(sacHeaderData.getSACString(SACHeaderEnum.KSTNM).trim(),
                        new HorizontalPosition(sacHeaderData.getValue(SACHeaderEnum.STLA),
                                sacHeaderData.getValue(SACHeaderEnum.STLO)),
                        sacHeaderData.getSACString(SACHeaderEnum.KNETWK).trim());
    }

    /**
     * Creates station from the input bytes.
     * <p>
     * The bytes must contain Name(8), NETWORK(8), latitude(4), longitude(4)
     * <p>
     * The bytes are written in header parts of BasicIDFile PartialIDFile
     * TimewindowInformationFile.
     *
     * @param bytes for one station
     * @return Station created from the input bytes
     */
    public static Observer createObserver(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        byte[] str = new byte[8];
        bb.get(str);
        String name = new String(str).trim();
        bb.get(str);
        String network = new String(str).trim();
        return new Observer(name, new HorizontalPosition(bb.getDouble(), bb.getDouble()), network);
    }

    public static Observer createObserver(String stationLine) {
        String[] ss = stationLine.trim().split("\\s+");
        String stationName = ss[0];
        String network = ss[1];
        double latitude = Double.parseDouble(ss[2]);
        double longitude = Double.parseDouble(ss[3]);
        HorizontalPosition position = new HorizontalPosition(latitude, longitude);
        return new Observer(stationName, position, network);
    }

    @Override
    public int compareTo(Observer o) {
        int name = station.compareTo(o.station);
        if (name != 0)
            return name;
        int net = network.compareTo(o.network);
//		int pos = comparePosition(o) == true ? 0 : 1;
        return net != 0 ? net : position.compareTo(o.getPosition());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
//		result = prime * result + ((position == null) ? 0 : position.hashCode());
//		result = prime * result + ((stationName == null) ? 0 : stationName.hashCode());
        result = 314159 * prime * station.hashCode() * network.hashCode();
        return result;
    }

    /**
     * Observers are considered equal if and only if
     * [network code is equal && station code is equal
     * && position is {@link #equal(HorizontalPosition, HorizontalPosition)}].
     * If the network code is 'DSM', comparison of networks between instances is not done;
     * station code and horizontal position is considered.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Observer other = (Observer) obj;

        if (position == null) {
            if (other.position != null)
                return false;
        } else if (!equal(position, other.position))
            return false;

        if (station == null) {
            if (other.station != null)
                return false;
        } else if (!station.equals(other.station))
            return false;

        if (network == null)
            return other.network == null || other.network.equals(SYN);
        else if (network.equals(SYN))
            return true;
        else if (other.network != null && !other.network.equals(SYN) && !network.equals(other.network))
            return false;

        return true;
    }

    /**
     * Judges whether 2 observers are at the same position.
     * @param pos1
     * @param pos2
     * @return
     */
    private boolean equal(HorizontalPosition pos1, HorizontalPosition pos2) {
        if (Precision.round(pos1.getLatitude(), COORDINATE_SCALE)
                != Precision.round(pos2.getLatitude(), COORDINATE_SCALE))
            return false;
        else if (Precision.round(pos1.getLongitude(), COORDINATE_SCALE)
                != Precision.round(pos2.getLongitude(), COORDINATE_SCALE))
            return false;
        else
            return true;
        // this way, it is transitive (i.e. if (x==y && y==z) then x==z)

/*      //this is not transitive:
        if (!Utilities.equalWithinEpsilon(pos1.getLatitude(), pos2.getLatitude(), COORDINATE_GRID))
            return false;
        else if (!Utilities.equalWithinEpsilon(pos1.getLongitude(), pos2.getLongitude(), COORDINATE_GRID))
            return false;
        else
            return true;
*/
    }

    /**
     * @return the name of the network
     */
    public String getNetwork() {
        return network;
    }

    /**
     * @return the name of the station
     */
    public String getStation() {
        return station;
    }

    /**
     * @return the position of the observer
     */
    public HorizontalPosition getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return station + "_" + network;
    }

    public String getStringID() {
        return station + "_" + network;
    }

}
