package io.github.kensuke1984.kibrary.firsthandler;

import java.time.LocalDateTime;

/**
 * Class for handling tasks related to the name of SAC files.
 * To be used in {@link EventProcessor}.
 *
 * @since 2016/1/25
 * @author Kensuke Konishi
 */
class SacFileName implements Comparable<SacFileName> {

    private String name;
    private LocalDateTime startTime;
    /**
     * Network identifier.
     */
    private String network;
    /**
     * Station name.
     */
    private String station;
    /**
     * Location ID.
     */
    private String location;
    /**
     * Channel. BHE, BHZ, etc.
     */
    private String channel;
    /**
     * Instrument. BH, HL, etc. (1st & 2nd letters of channel name)
     */
    private String instrument;
    /**
     * Component. E, Z, R, T, etc. (3rd letter of channel name)
     */
    private String component;
    /**
     * Quality indicator. D=Data of undetermined state, M=Merged data, R=Raw waveform data, Q=Quality controlled data.
     */
    private String quality;


    SacFileName(String sacFileName) {
        name = sacFileName;

        String[] parts = sacFileName.split("\\.");
        switch (parts[parts.length - 1]) {
        case "SAC":
        case "SET":
            startTime = LocalDateTime
                    .of(Integer.parseInt(parts[5]), 1, 1, Integer.parseInt(parts[7]), Integer.parseInt(parts[8]),
                            Integer.parseInt(parts[9]), Integer.parseInt(parts[10]) * 1000 * 1000)
                    .withDayOfYear(Integer.parseInt(parts[6]));
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            quality = parts[4];
            break;
        case "MRG":
        case "MOD":
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            quality = parts[4];
            // "instrument" is the 1st&2nd letters of channel name
            instrument = channel.substring(0, 2);
            // "component" is the 3rd letter of channel name
            component = channel.substring(2);
            break;
        case "X":
        case "Y":
        case "Z":
        case "R":
        case "T":
            network = parts[0];
            station = parts[1];
            location = parts[2];
            instrument = parts[3];
            quality = parts[4];
            component = parts[5];
            break;
        }
    }

    /**
     * Creates a new SAC file name for the resulting file after being set up.
     * @return (String) SAC file name of the form "network.station.location.channel.quality.year.jday.hour.min.sec.msec.SET"
     */
    String getSetFileName() {
        return network + "." + station + "." + location + "." + channel + "." + quality + "." +
                startTime.getYear() + "." + startTime.getDayOfYear() + "." + startTime.getHour() + "." +
                startTime.getMinute() + "." + startTime.getSecond() + "." + startTime.getNano() / 1000 / 1000 + ".SET";
//                year + "." + jday + "." + hour + "." + min + "." + sec + "." + msec + ".SET";
    }

    /**
     * Creates a new SAC file name for the resulting file after being merged.
     * @return (String) SAC file name of the form "network.station.location.channel.quality.MRG"
     */
    String getMergedFileName() {
        return network + "." + station + "." + location + "." + channel + "." + quality + ".MRG";
    }

    /**
     * Creates a new SAC file name for the resulting file after being modified.
     * @return (String) SAC file name of the form "network.station.location.channel.quality.MOD"
     */
    String getModifiedFileName() {
        return network + "." + station + "." + location + "." + channel + "." + quality + ".MOD";
    }

    /**
     * Creates a new SAC file name for the resulting file after being deconvolved.
     * Components "1" and "E" will be renamed to "X", and "2" and "N" to "Y".
     * @return (String) SAC file name of the form "network.station.location.instrument.quality.[XYZ]"
     */
    String getDeconvolvedFileName() {
        String newComponent = "";
        switch (component) {
        case "1":
        case "E":
            newComponent = "X";
            break;
        case "2":
        case "N":
            newComponent = "Y";
            break;
        case "Z":
            newComponent = "Z";
            break;
        }
        return network + "." + station + "." + location + "." + instrument + "." + quality + "." + newComponent;
    }

    /**
     * Returns SAC file name corresponding to the specified component.
     * @return (String) SAC file name of the form "network.station.location.instrument.quality.[specified component]"
     */
    String getNameWithComponent(String specifiedComponent) {
        return network + "." + station + "." + location + "." + instrument + "." + quality + "." + specifiedComponent;
    }

    /**
     * Returns name of triplet.
     * @return (String) Name of the form "network.station.location.instrument.quality.*"
     */
    String getTripletName() {
        return network + "." + station + "." + location + "." + instrument + "." + quality + ".*";
    }

    /**
     * Creates a new SAC file name for the resulting file after duplications are eliminated.
     * @return (String) SAC file name of the form "station_network.event.component"
     */
    String getFinalFileName(String event) {
        return station + "_" + network + "." + event + "." + component;
    }

    /**
     * @return (network).station.location.BHN.D.SAC
     */
    String getNetwork() {
        return network;
    }

    /**
     * @return network.(station).location.BHN.D.SAC
     */
    String getStation() {
        return station;
    }

    /**
     * @return network.station.(location).BHN.D.SAC
     */
    String getLocation() {
        return location;
    }

    /**
     * @return network.station.location.(BHN).D.SAC
     */
    String getChannel() {
        return channel;
    }

    /**
     * @return network.station.location.(BH)N.D.SAC
     */
    String getInstrument() {
        return instrument;
    }

    /**
     * @return network.station.location.BH(N).D.SAC
     */
    String getComponent() {
        return component;
    }

    /**
     * @return network.station.location.BHN.(D).SAC
     */
    String getQuality() {
        return quality;
    }

    LocalDateTime getStartTime() {
        return startTime;
    }

    @Override
    public int compareTo(SacFileName o) {
        int c = network.compareTo(o.network);
        if (c != 0) return c;
        else if ((c = station.compareTo(o.station)) != 0) return c;
        else if ((c = location.compareTo(o.location)) != 0) return c;
        else if ((c = channel.compareTo(o.channel)) != 0) return c;
        else if ((c = quality.compareTo(o.quality)) != 0) return c;
        else return startTime.compareTo(o.startTime);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Judges whether two SAC files are parts of what is supposed to be in a single SAC file.
     * Judgements will be made based on the SAC filenames; files with the same
     * network, station, location, channel, and qualityControl
     * will be judged as related.
     *
     * @param sacFileName (SACFileName) The SAC file to be checked.
     * This file will be compared with the SAC file given to the constructor of this class.
     * @return (boolean) true if the two SAC files are related.
     */
    boolean isRelated(SacFileName sacFileName) {
        return sacFileName.channel.equals(channel) && sacFileName.network.equals(network) &&
                sacFileName.station.equals(station) && sacFileName.location.equals(location) &&
                sacFileName.quality.equals(quality);
    }

}
