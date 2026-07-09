package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;


/**
 * Class for a set of R, T, and Z component SAC files of the same network, station, location, instrument, and quality.
 *
 * @author otsuru
 * @since 2021/10/04
 */
class SacTriplet {

    /**
     * threshold to judge which stations are in the same position [deg]
     */
    private double coordinateGrid;

    private String network;
    private String station;
    private String location;
    private String instrument;
    private String quality;
    private HorizontalPosition position;

    /**
     * name of the triplet
     */
    private String name;

    private Path rPath;
    private boolean rRegistered = false;
    private Path tPath;
    private boolean tRegistered = false;
    private Path zPath;
    private boolean zRegistered = false;

    private int number = 0;
    private boolean dismissed = false;

    /**
     * Constructor to set parameters, and register the first SAC file of the triplet.
     *
     * @param sacPath (Path) Path of the first SAC file to be registered in the triplet.
     * @param grid (double) The value of coordinateGrid to be set.
     * @throws IOException
     */
    SacTriplet(Path sacPath, double grid) throws IOException {
        SacFileName sacFile = new SacFileName(sacPath.getFileName().toString());
        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);

        //set variables
        name = sacFile.getTripletName();
        network = sacFile.getNetwork();
        station = sacFile.getStation();
        location = sacFile.getLocation();
        instrument = sacFile.getInstrument();
        quality = sacFile.getQuality();
        double latitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLA));
        double longitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLO));
        position = new HorizontalPosition(latitude, longitude);

        coordinateGrid = grid;

        //register
        register(sacPath, sacFile.getComponent());
    }

    /**
     * Method for registering second and third SAC files for the triplet.
     * This method first judges whether the given SAC file is related to this triplet, and if it is, it is registered.
     *
     * @param sacPath (Path) Path of the SAC file to be judged and possibly registered.
     * @return (boolean) true if the given SAC file is registered in this triplet.
     */
    boolean add(Path sacPath) {
        SacFileName sacFile = new SacFileName(sacPath.getFileName().toString());

        // if variables are same, register
        if (sacFile.getNetwork().equals(network) && sacFile.getStation().equals(station) && sacFile.getLocation().equals(location) &&
                sacFile.getInstrument().equals(instrument) && sacFile.getQuality().equals(quality)) {
            register(sacPath, sacFile.getComponent());
            return true;
        } else {
            return false;
        }
    }

    private void register(Path sacPath, String component) {
        switch (component) {
        case "R":
            rPath = sacPath;
            rRegistered = true;
            break;
        case "T":
            tPath = sacPath;
            tRegistered = true;
            break;
        case "Z":
            zPath = sacPath;
            zRegistered = true;
            break;
        }
    }

    /**
     * Check that all SAC files registered for this triplet have the same observer position.
     * @return (boolean) Whether the observer positions are consistent.
     */
    boolean checkPositionConsistency() {
        if (rRegistered) {
            if (!assessPosition(rPath)) return false;
        }
        if (tRegistered) {
            if (!assessPosition(tPath)) return false;
        }
        if (zRegistered) {
            if (!assessPosition(zPath)) return false;
        }
        return true;
    }
    private boolean assessPosition(Path sacPath) {
        try {
            Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
            double sacLatitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLA));
            double sacLongitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLO));
            HorizontalPosition sacPosition = new HorizontalPosition(sacLatitude, sacLongitude);
            return sacPosition.equals(position);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks whether the triplet is valid (i.e. has either {R,T,Z}, {R,T}, or {Z} components),
     * and counts the number of registered SAC files.
     *
     * @return (boolean) true if the triplet is valid
     */
    boolean checkValidity() {
        if (rRegistered && tRegistered && zRegistered) {
            number = 3;
            return true;
        } else if (rRegistered && tRegistered) {
            number = 2;
            return true;
        } else if (zRegistered) {
            number = 1;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Marks the triplet as dismissed.
     */
    void dismiss() {
        dismissed = true;
    }

    /**
     * Reports whether the triplet has been dismissed.
     * @return (boolean) true if the triplet has been dismissed.
     */
    boolean isDismissed() {
        return dismissed;
    }

    /**
     * Moves all files in the triplet to a given directory.
     *
     * @param dir (Path) Path of the directory where the files shall be moved to.
     * @throws IOException
     */
    void move(Path dir) throws IOException {
        if (rRegistered) {
            FileAid.moveToDirectory(rPath, dir, true);
        }
        if (tRegistered) {
            FileAid.moveToDirectory(tPath, dir, true);
        }
        if (zRegistered) {
            FileAid.moveToDirectory(zPath, dir, true);
        }
    }

    /**
     * Renames all files in the triplet to a finalized name, containing the event ID.
     *
     * @param event (String) The event ID that this triplet belongs to.
     * @throws IOException
     */
    void rename(String event) throws IOException {
        if (rRegistered) {
            SacFileName rFile = new SacFileName(rPath.getFileName().toString());
            Files.move(rPath, rPath.resolveSibling(rFile.getFinalFileName(event)));
        }
        if (tRegistered) {
            SacFileName tFile = new SacFileName(tPath.getFileName().toString());
            Files.move(tPath, tPath.resolveSibling(tFile.getFinalFileName(event)));
        }
        if (zRegistered) {
            SacFileName zFile = new SacFileName(zPath.getFileName().toString());
            Files.move(zPath, zPath.resolveSibling(zFile.getFinalFileName(event)));
        }
    }

    /**
     * Checks whether a given triplet is this triplet itself.
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if it is the same triplet
     */
    boolean isItself(SacTriplet other) {
        return other.getNetwork().equals(network) && other.getStation().equals(station) && other.getLocation().equals(location) &&
                other.getInstrument().equals(instrument) && other.getQuality().equals(quality);
    }

    /**
     * Checks whether the station of a given triplet is at or close to the station of this triplet.
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if the statons of the triplets are positioned at or close to each other
     */
    boolean atSamePosition(SacTriplet other) {
        if (other.getNetwork().equals(network) && other.getStation().equals(station)) return true;
        else if (Math.abs(getLatitude() - other.getLatitude()) < coordinateGrid &&
                Math.abs(getLongitude() - other.getLongitude()) < coordinateGrid)
            return true;
        else return false;
    }

    /**
     * Checks whether a given triplet complements this triplet (i.e. one is {R,T} and the other {Z}).
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if the triplets complement each other
     */
    boolean complements(SacTriplet other) {
        return other.getNumber() + number == 3;
    }

    /**
     * Checks whether this triplet is inferior to a given triplet.
     * The criteria are as follows:
     * <ol>
     * <li> A full triplet is preferred over incomplete triplets. </li>
     * <li> The instrument is ranked as BH > HH > BL > HL. </li>
     * <li> The network is ranked as trusted > permanent > temporary > SS. </li>
     * <li> The quality indicator is ranked as Q > M > D > R. </li>
     * <li> Locations younger in dictionary order is preferred ("--" is before "00"). </li>
     * <li> Otherwise (i.e. different stations but same instrument and location), this triplet is selected. </li>
     * </ol>
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) Whether this triplet is inferior.
     */
    boolean isInferiorTo(SacTriplet other) {
        // a full triplet is preferred over incomplete triplets
        if (number < other.getNumber()) return true;
        else if (number > other.getNumber()) return false;
        // choose instrument that is preferred
        else if (getInstrumentRank() < other.getInstrumentRank()) return true;
        else if (getInstrumentRank() > other.getInstrumentRank()) return false;
        // choose network that is preferred
        else if (getNetworkRank() < other.getNetworkRank()) return true;
        else if (getNetworkRank() > other.getNetworkRank()) return false;
        // choose quality that is preferred
        else if (getQualityRank() < other.getQualityRank()) return true;
        else if (getQualityRank() > other.getQualityRank()) return false;
        // locations younger in dictionary order is preferred
        // result of compareTo() is positive if [this location] is after [other location] in dictionary order
        else if (location.compareTo(other.getLocation()) > 0) return true;
        else return false;
    }

    /**
     * @return (int) the "rank" of the instrument
     */
    int getInstrumentRank() {
        switch (instrument) {
        case "BH":
            return 4;
        case "HH":
            return 3;
        case "BL":
            return 2;
        case "HL":
            return 1;
        }
        return 0;
    }

    /**
     * @return (int) the "rank" of the network
     */
    int getNetworkRank() {
        Set<String> globalTrusted = Set.of("IU", "II", "IC", "CU", "GT", "G", "GE", "MN");
        Set<String> temporary = Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "X", "Y", "Z");

        if (globalTrusted.contains(network)) return 10;
        else if (temporary.contains(network.substring(0, 1))) return 1;
        else if (network.equals("SS")) return 0;
        else return 5;
    }

    /**
     * @return (int) the "rank" of the quality
     */
    int getQualityRank() {
        switch (instrument) {
        case "Q":
            return 4;
        case "M":
            return 3;
        case "D":
            return 2;
        case "R":
            return 1;
        }
        return 0;
    }

    /**
     * @return network
     */
    String getNetwork() {
        return network;
    }

    /**
     * @return station
     */
    String getStation() {
        return station;
    }

    /**
     * @return location
     */
    String getLocation() {
        return location;
    }

    /**
     * @return instrument
     */
    String getInstrument() {
        return instrument;
    }

    /**
     * @return quality indicator
     */
    public String getQuality() {
        return quality;
    }

    /**
     * @return latitude
     */
    double getLatitude() {
        return position.getLatitude();
    }

    /**
     * @return longitude
     */
    double getLongitude() {
        return position.getLongitude();
    }

    /**
     * @return number of registered files
     */
    int getNumber() {
        return number;
    }

    /**
     * @return name of triplet
     */
    String getName() {
        return name;
    }

}
