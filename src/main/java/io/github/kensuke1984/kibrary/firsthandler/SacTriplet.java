package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;


public class SacTriplet {

    private final double COORDINATE_GRID = 0.01; // = about 1 km

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

    public SacTriplet(Path sacPath) throws IOException {
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

        //register
        register(sacPath, sacFile.getComponent());
    }

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

    public void dismiss() {
        dismissed = true;
    }

    public boolean isDismissed() {
        return dismissed;
    }

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

    public boolean isItself (SacTriplet other) {
        return other.getNetwork().equals(network) && other.getStation().equals(station) &&
                other.getLocation().equals(location) && other.getInstrument().equals(instrument);
    }

    public boolean atSameStation (SacTriplet other) {
        if (other.getNetwork().equals(network) && other.getStation().equals(station)) return true;
        else if (Math.abs(latitude - other.getLatitude()) < COORDINATE_GRID &&
                Math.abs(longitude - other.getLongitude()) < COORDINATE_GRID) return true;
        else return false;
    }

    public boolean complements (SacTriplet other) {
        return other.getNumber() + number == 3;
    }

    public boolean isInferiorTo (SacTriplet other) {
        if (number < other.getNumber()) return true;
        else if (number > other.getNumber()) return false;
        else if (getInstrumentRank() < other.getInstrumentRank()) return true;
        else if (getInstrumentRank() > other.getInstrumentRank()) return false;
        else if (location.compareTo(other.getLocation()) > 0) return true;
        else return false;
    }

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
