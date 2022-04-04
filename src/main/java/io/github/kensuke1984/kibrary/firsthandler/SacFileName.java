package io.github.kensuke1984.kibrary.firsthandler;

import java.time.LocalDateTime;

/**
 * Class for handling tasks related to the name of SAC files.
 * To be used in {@link EventProcessor}.
 *
 * @author Kensuke Konishi
 */
class SacFileName implements Comparable<SacFileName> {

    private String name;
    private LocalDateTime startTime;
    /**
     * network identifier ネットワーク名
     */
    private String network;
    /**
     * station name
     */
    private String station;
    /**
     * location IDの部分
     */
    private String location;
    /**
     * channel BHE BHZ とか
     */
    private String channel;
    /**
     * component E Z R T とか (3rd letter of channel name)
     */
    private String component;
    /**
     * instrument BH HL とか (1st&2nd letters of channel name)
     */
    private String instrument;
    /**
     * quality control marker D=Data of Undetermined state, M=Merged Data, R=Raw
     * waveform Data, Q=QC'd data
     */
    private String qualityControl;


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
            qualityControl = parts[4];
            break;
        case "MRG":
        case "MOD":
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            qualityControl = parts[4];
            // "component" is the 3rd letter of channel name
            component = channel.substring(2);
            // "instrument" is the 1st&2nd letters of channel name
            instrument = channel.substring(0, 2);
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
            qualityControl = parts[4];
            component = parts[5];
            break;

        }
    }

    /**
     * Creates a new SAC file name for the resulting file after being set up.
     * @return (String) SAC file name of the form "network.station.location.channel.qualityControl.year.jday.hour.min.sec.msec.SET"
     */
    String getSetFileName() {
        return network + "." + station + "." + location + "." + channel + "." + qualityControl + "." +
                startTime.getYear() + "." + startTime.getDayOfYear() + "." + startTime.getHour() + "." +
                startTime.getMinute() + "." + startTime.getSecond() + "." + startTime.getNano()/1000/1000 + ".SET";
//                year + "." + jday + "." + hour + "." + min + "." + sec + "." + msec + ".SET";
    }

    /**
     * Creates a new SAC file name for the resulting file after being merged.
     * @return (String) SAC file name of the form "network.station.location.channel.qualityControl.MRG"
     */
    String getMergedFileName() {
        return network + "." + station + "." + location + "." + channel + "." + qualityControl + ".MRG";
    }

    /**
     * Creates a new SAC file name for the resulting file after being modified.
     * @return (String) SAC file name of the form "network.station.location.channel.qualityControl.MOD"
     */
    String getModifiedFileName() {
        return network + "." + station + "." + location + "." + channel + "." + qualityControl + ".MOD";
    }

    /**
     * Creates a new SAC file name for the resulting file after being deconvolved.
     * Components "1" and "E" will be renamed to "X", and "2" and "N" to "Y".
     * @return (String) SAC file name of the form "network.station.location.instrument.qualityControl.[XYZ]"
     */
    String getDeconvolvedFileName() {
        String newComponent = "";
        switch(component) {
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
        return network + "." + station + "." + location + "." + instrument + "." + qualityControl + "." + newComponent;
    }

    /**
     * Returns SAC file name corresponding to the specified component.
     * @return (String) SAC file name of the form "network.station.location.instrument.qualityControl.[specified component]"
     */
    String getNameWithComponent(String specifiedComponent) {
        return network + "." + station + "." + location + "." + instrument + "." + qualityControl + "." + specifiedComponent;
    }

    /**
     * Returns name of triplet.
     * @return (String) Name of the form "network.station.location.instrument.qualityControl.*"
     */
    String getTripletName() {
        return network + "." + station + "." + location + "." + instrument + "." + qualityControl + ".*";
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
     * @return network.station.location.BHN.(D).SAC
     */
    String getQualityControl() {
        return qualityControl;
    }

    /**
     * @return network.station.location.BH(N).D.SAC
     */
    String getComponent() {
        return component;
    }

    /**
     * @return network.station.location.(BH)N.D.SAC
     */
    String getInstrument() {
        return instrument;
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
        else if ((c = qualityControl.compareTo(o.qualityControl)) != 0) return c;
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
                sacFileName.qualityControl.equals(qualityControl);
    }

}
