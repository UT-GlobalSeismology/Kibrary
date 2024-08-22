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
 * However, virtual observers with longer station or network names (up to 8 letters) may be created.)
 * <p>
 * Observers are considered equal if and only if
 * [network code is equal && station code is equal && position is {@link #equal(HorizontalPosition, HorizontalPosition)}].
 * <p>
 * At a single time moment, only one observer with the same network and station code exists.
 * However, at different times, observers with the same name but different positions can exist.
 * Therefore, the latitude and longitude are needed to specify a certain observer.
 * On the other hand, network and station codes are useful for humans to recognize an observer.
 * Hence, these 4 keys (network, station, latitude, longitude) shall be used to specify observers.
 * Only inside folders of a single event can files be named using just network and station code
 * (because a single event means a single time moment).
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public final class Observer implements Comparable<Observer> {

    /**
     * maximum length to allow for an observer ID
     */
    public static final int MAX_LENGTH = 16;
    /**
     * typical maximum number of letters of station
     */
    private static final int STA_LENGTH = 5;
    /**
     * typical maximum number of letters of network
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
        if (observerID.split("_").length == 1) {
            this.station = observerID;
            this.network = null;
            this.position = position;
        } else {
            this.station = observerID.split("_")[0];
            this.network = observerID.split("_")[1];
            this.position = position;
        }
    }

    public Observer(Observer observer) {
        this.station = observer.station;
        this.network = observer.network;
        this.position = observer.position;
    }

    /**
     * @param sacHeaderData header data
     * @return Station of the input sacHeaderData
     */
    public static Observer of(SACHeaderAccess sacHeaderData) {
        return new Observer(sacHeaderData.getSACString(SACHeaderEnum.KSTNM).trim(), sacHeaderData.getSACString(SACHeaderEnum.KNETWK).trim(),
                new HorizontalPosition(sacHeaderData.getValue(SACHeaderEnum.STLA), sacHeaderData.getValue(SACHeaderEnum.STLO)));
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
        byte[] str = new byte[MAX_LENGTH];
        bb.get(str);
        String observerID = new String(str).trim();
        return new Observer(observerID, new HorizontalPosition(bb.getDouble(), bb.getDouble()));
    }

    public static Observer createObserver(String observerLine) {
        String[] ss = observerLine.trim().split("\\s+");
        String station = ss[0];
        String network = ss[1];
        double latitude = Double.parseDouble(ss[2]);
        double longitude = Double.parseDouble(ss[3]);
        HorizontalPosition position = new HorizontalPosition(latitude, longitude);
        return new Observer(station, network, position);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((position == null) ? 0 : position.hashCode());
        result = prime * result + ((station == null) ? 0 : station.hashCode());
        result = prime * result + ((network == null) ? 0 : network.hashCode());
//        result = 314159 * prime * station.hashCode() * network.hashCode();
        return result;
    }

    /**
     * Observers are considered equal if and only if
     * [network code is equal && station code is equal
     * && position is {@link HorizontalPosition#equals(Object)}].
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

        if (network == null) {
            if (other.network != null)
                return false;
        } else if (!network.equals(other.network))
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

    /**
     * Return name of observer in STA_NET format.
     */
    @Override
    public String toString() {
        if (StringUtils.isEmpty(network)) return station;
        else return station + "_" + network;
    }

    /**
     * @return (String) "station network"
     */
    public String toPaddedString() {
        return StringUtils.rightPad(station, STA_LENGTH) + " " + StringUtils.rightPad(network, NET_LENGTH);
    }

    /**
     * @return (String) "station network latitude longitude"
     */
    public String toPaddedInfoString() {
        return StringUtils.rightPad(station, STA_LENGTH) + " " + StringUtils.rightPad(network, NET_LENGTH)
                + " " + position.toString();
    }
}
