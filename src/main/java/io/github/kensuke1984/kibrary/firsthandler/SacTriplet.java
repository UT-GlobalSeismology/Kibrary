package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.Utilities;

public class SacTriplet {

    private String network;
    private String station;
    private String location;
    private String instrument;

    private Path rPath;
    private Path tPath;
    private Path zPath;

    private int number = 0;
    private boolean dismissed = false;

    public SacTriplet(Path sacPath){
        SACFileName sacFile = new SACFileName(sacPath.getFileName().toString());

        //set variables
        network = sacFile.getNetwork();
        station = sacFile.getStation();
        location = sacFile.getLocation();
        instrument = sacFile.getInstrument();

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
            break;
        case "T":
            tPath = sacPath;
            break;
        case "Z":
            zPath = sacPath;
            break;
        }
    }

    public boolean isValid() {
        if (!rPath.toString().isEmpty() && !tPath.toString().isEmpty() && !zPath.toString().isEmpty()) {
            number = 3;
            return true;
        } else if (!rPath.toString().isEmpty() && !tPath.toString().isEmpty()) {
            number = 2;
            return true;
        } else if (!zPath.toString().isEmpty()) {
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
        if (!rPath.toString().isEmpty()) {
            Utilities.moveToDirectory(rPath, dir, true);
        }
        if (!tPath.toString().isEmpty()) {
            Utilities.moveToDirectory(tPath, dir, true);
        }
        if (!zPath.toString().isEmpty()) {
            Utilities.moveToDirectory(zPath, dir, true);
        }
    }

    public void rename(String event) throws IOException {
        if (!rPath.toString().isEmpty()) {
            SACFileName rFile = new SACFileName(rPath.getFileName().toString());
            Files.move(rPath, rPath.resolveSibling(rFile.getFinalFileName(event)));
        }
        if (!tPath.toString().isEmpty()) {
            SACFileName tFile = new SACFileName(tPath.getFileName().toString());
            Files.move(tPath, tPath.resolveSibling(tFile.getFinalFileName(event)));
        }
        if (!zPath.toString().isEmpty()) {
            SACFileName zFile = new SACFileName(zPath.getFileName().toString());
            Files.move(zPath, zPath.resolveSibling(zFile.getFinalFileName(event)));
        }
    }

    public boolean isItself (SacTriplet other) {
        return other.getNetwork().equals(network) && other.getStation().equals(station) &&
                other.getLocation().equals(location) && other.getInstrument().equals(instrument);
    }

    public boolean atSameStation (SacTriplet other) {
        return other.getNetwork().equals(network) && other.getStation().equals(station);
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
     * @return number of registered files
     */
    public int getNumber() {
        return number;
    }

}
