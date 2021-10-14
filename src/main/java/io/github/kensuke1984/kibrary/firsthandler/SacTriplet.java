package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;


/**
 * Class for a set of R, T, and Z component SAC files of the same network, station, location, and instrument.
 *
 */
public class SacTriplet {

    /**
     * threshold to judge which stations are in the same position [deg]
     */
    private double coordinateGrid;

    private String network;
    private String station;
    private String location;
    private String instrument;
    private double latitude;
    private double longitude;

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
    public SacTriplet(Path sacPath, double grid) throws IOException {
        SACFileName sacFile = new SACFileName(sacPath.getFileName().toString());
        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);

        //set variables
        name = sacFile.getTripletName();
        network = sacFile.getNetwork();
        station = sacFile.getStation();
        location = sacFile.getLocation();
        instrument = sacFile.getInstrument();
        latitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLA));
        longitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLO));

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
    public boolean add(Path sacPath) {
        SACFileName sacFile = new SACFileName(sacPath.getFileName().toString());

        //if variables are same, register
        if(sacFile.getNetwork().equals(network) && sacFile.getStation().equals(station) &&
                sacFile.getLocation().equals(location) && sacFile.getInstrument().equals(instrument)) {
            register(sacPath, sacFile.getComponent());
            return true;
        } else {
            return false;
        }
    }

    private void register(Path sacPath, String component) {
        //register R or T or Z
        switch(component) {
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
     * Checks whether the triplet is valid (i.e. has either {R,T,Z}, {R,T}, or {Z} components),
     * and counts the number of registered SAC files.
     *
     * @return (boolean) true if the triplet is valid
     */
    public boolean checkValidity() {
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
    public void dismiss() {
        dismissed = true;
    }

    /**
     * Reports whether the triplet has been dismissed.
     * @return (boolean) true if the triplet has been dismissed.
     */
    public boolean isDismissed() {
        return dismissed;
    }

    /**
     * Moves all files in the triplet to a given directory.
     *
     * @param dir (Path) Path of the directory where the files shall be moved to.
     * @throws IOException
     */
    public void move(Path dir) throws IOException {
        if (rRegistered) {
            Utilities.moveToDirectory(rPath, dir, true);
        }
        if (tRegistered) {
            Utilities.moveToDirectory(tPath, dir, true);
        }
        if (zRegistered) {
            Utilities.moveToDirectory(zPath, dir, true);
        }
    }

    /**
     * Renames all files in the triplet to a finalized name, containing the event ID.
     *
     * @param event (String) The event ID that this triplet belongs to.
     * @throws IOException
     */
    public void rename(String event) throws IOException {
        if (rRegistered) {
            SACFileName rFile = new SACFileName(rPath.getFileName().toString());
            Files.move(rPath, rPath.resolveSibling(rFile.getFinalFileName(event)));
        }
        if (tRegistered) {
            SACFileName tFile = new SACFileName(tPath.getFileName().toString());
            Files.move(tPath, tPath.resolveSibling(tFile.getFinalFileName(event)));
        }
        if (zRegistered) {
            SACFileName zFile = new SACFileName(zPath.getFileName().toString());
            Files.move(zPath, zPath.resolveSibling(zFile.getFinalFileName(event)));
        }
    }

    /**
     * Checks whether a given triplet is this triplet itself.
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if it is the same triplet
     */
    public boolean isItself (SacTriplet other) {
        return other.getNetwork().equals(network) && other.getStation().equals(station) &&
                other.getLocation().equals(location) && other.getInstrument().equals(instrument);
    }

    /**
     * Checks whether the station of a given triplet is at or close to the station of this triplet.
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if the statons of the triplets are positioned at or close to each other
     */
    public boolean atSamePosition (SacTriplet other) {
        if (other.getNetwork().equals(network) && other.getStation().equals(station)) return true;
        else if (Math.abs(latitude - other.getLatitude()) < coordinateGrid &&
                Math.abs(longitude - other.getLongitude()) < coordinateGrid) return true;
        else return false;
    }

    /**
     * Checks whether a given triplet complements this triplet (i.e. one is {R,T} and the other {Z}).
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if the triplets complement each other
     */
    public boolean complements (SacTriplet other) {
        return other.getNumber() + number == 3;
    }

    /**
     * Checks whether this triplet is inferior to a given triplet.
     * The criteria are as follows:
     * <ol>
     * <li> A full triplet is prefered over incomplete triplets. </li>
     * <li> The instrument is ranked as BH > HH > BL > HL. </li>
     * <li> Locations younger in dictionary order is prefered. </li>
     * <li> Otherwise (i.e. different stations but different location and instrument), the selection is random. </li>
     * </ol>
     * @param other (SacTriplet) The triplet to be compared to.
     * @return (boolean) true if this triplet is inferior
     */
    public boolean isInferiorTo (SacTriplet other) {
        // a full triplet is prefered over incomplete triplets
        if (number < other.getNumber()) return true;
        else if (number > other.getNumber()) return false;
        // choose instrument that is prefered
        else if (getInstrumentRank() < other.getInstrumentRank()) return true;
        else if (getInstrumentRank() > other.getInstrumentRank()) return false;
        // locations younger in dictionary order is prefered
        // result of compareTo() is positive if [this location] is after [other location] in dictionary order
        else if (location.compareTo(other.getLocation()) > 0) return true;
        else return false;
    }

    /**
     * @return (int) the "rank" of the instrument
     */
    public int getInstrumentRank() {
        int rank = 0;
        switch (instrument) {
        case "BH":
            rank = 4;
            break;
        case "HH":
            rank = 3;
            break;
        case "BL":
            rank = 2;
            break;
        case "HL":
            rank = 1;
            break;
        }
        return rank;
    }

    /**
     * @return network
     */
    public String getNetwork() {
        return network;
    }

    /**
     * @return station
     */
    public String getStation() {
        return station;
    }

    /**
     * @return location
     */
    public String getLocation() {
        return location;
    }

    /**
     * @return instrument
     */
    public String getInstrument() {
        return instrument;
    }

    /**
     * @return latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * @return longitude
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * @return number of registered files
     */
    public int getNumber() {
        return number;
    }

    /**
     * @return name of triplet
     */
    public String getName() {
        return name;
    }

}
