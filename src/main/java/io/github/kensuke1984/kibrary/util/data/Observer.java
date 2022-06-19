package io.github.kensuke1984.kibrary.util.data;


import java.nio.ByteBuffer;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * <p>
 * Information of observer,
 * consisting of station code, network code, and {@link HorizontalPosition}. <br>
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * </p>
 * <p>
 * Station code and network code must be 8 or less letters.
 * (This is set at 8 letters probably because alphanumeric fields in SAC data format are 8 letters.
 * The actual maximum number of letters are 5 and 2;
 * see <a href=https://ds.iris.edu/ds/nodes/dmc/data/formats/seed/>SEED reference</a>.
 * However, network may be set 'DSM' as stated below, so the maximum length should be thought of as 3.)
 * <p>
 * Observers are considered equal if and only if
 * [network code is equal && station code is equal && position is {@link #equal(HorizontalPosition, HorizontalPosition)}].
 * If the network code is 'DSM', comparison of networks between instances is not done;
 * station code and horizontal position is considered.
 *
 * @author Kensuke Konishi
 */
public final class Observer implements Comparable<Observer> {

    /**
     * network code for stations in synthetic datasets
     */
    public static final String SYN = "DSM";
    /**
     * maximum number of letters of station
     */
    private static final int STA_LENGTH = 5;
    /**
     * maximum number of letters of network
     * (length may be 3 in case of 'DSM', but rightPad() won't cut it so it is OK.)
     */
    private static final int NET_LENGTH = 2;

    /**
     * station code
     */
    private final String station;
    /**
     * network code
     */
    private final String network;
    /**
     * the {@link HorizontalPosition} of the station
     */
    private final HorizontalPosition position;

    /**
     * @param station    Name of the station (must be 8 or less letters)
     * @param network    Name of the network of the station (must be 8 or less letters)
     * @param position   Horizontal POSITION of the station
     */
    public Observer(String station, String network, HorizontalPosition position) {
        if (8 < station.length() || 8 < network.length())
            throw new IllegalArgumentException("Both station and network name must be 8 or less letters.");
        this.station = station;
        this.network = network;
        this.position = position;
    }

    public Observer(String observerID, HorizontalPosition position) {
        this(observerID.split("_")[0], observerID.split("_")[1], position);
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
    public static Observer of(SACHeaderAccess sacHeaderData) {
        return sacHeaderData.getSACString(SACHeaderEnum.KNETWK) == "-12345"
                ? new Observer(sacHeaderData.getSACString(SACHeaderEnum.KSTNM).trim(),
                        SYN,
                        new HorizontalPosition(sacHeaderData.getValue(SACHeaderEnum.STLA),
                                sacHeaderData.getValue(SACHeaderEnum.STLO)))
                : new Observer(sacHeaderData.getSACString(SACHeaderEnum.KSTNM).trim(),
                        sacHeaderData.getSACString(SACHeaderEnum.KNETWK).trim(),
                        new HorizontalPosition(sacHeaderData.getValue(SACHeaderEnum.STLA),
                                sacHeaderData.getValue(SACHeaderEnum.STLO)));
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
        return new Observer(name, network, new HorizontalPosition(bb.getDouble(), bb.getDouble()));
    }

    public static Observer createObserver(String stationLine) {
        String[] ss = stationLine.trim().split("\\s+");
        String stationName = ss[0];
        String network = ss[1];
        double latitude = Double.parseDouble(ss[2]);
        double longitude = Double.parseDouble(ss[3]);
        HorizontalPosition position = new HorizontalPosition(latitude, longitude);
        return new Observer(stationName, network, position);
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
     * && position is {@link HorizontalPosition#equals(Object)}].
     * If the network code is 'DSM', comparison of networks between instances is not done;
     * only station code and horizontal position are considered.
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
        } else if (!position.equals(other.position))
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
     * Sorting order is station &rarr; network &rarr; position.
     */
    @Override
    public int compareTo(Observer o) {
        int name = station.compareTo(o.station);
        if (name != 0)
            return name;
        int net = network.compareTo(o.network);
        return net != 0 ? net : position.compareTo(o.getPosition());
    }

    /**
     * @return the name of the station
     */
    public String getStation() {
        return station;
    }

    /**
     * @return the name of the network
     */
    public String getNetwork() {
        return network;
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

    public String toPaddedString() {
        return StringUtils.rightPad(station, STA_LENGTH) + " " + StringUtils.rightPad(network, NET_LENGTH);
    }

    /**
     * @return
     *
     * @deprecated use toString().
     */
    public String getStringID() { // TODO erase
        return station + "_" + network;
    }

    /**
     * @return (String) station network latitude longitude
     */
    public String toPaddedInfoString() {
        return StringUtils.rightPad(station, STA_LENGTH) + " " + StringUtils.rightPad(network, NET_LENGTH)
                + " " + position.toString();
    }
}
