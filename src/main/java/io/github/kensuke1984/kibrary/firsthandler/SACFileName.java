package io.github.kensuke1984.kibrary.firsthandler;

import java.time.LocalDateTime;

/**
 * Class for handling tasks related to the name of SAC files.
 * This class is only for SEED-type SAC filenames used as intermediate files in {@link FirstHandler} or {@link DataKitchen}.
 *
 * @author Kensuke Konishi
 * @version 0.0.5.1
 */
class SACFileName implements Comparable<SACFileName> {

    private String name;
    private LocalDateTime startTime;
    /**
     * network identifier ネットワーク名
     */
    private String network;
    /**
     * components BHE BHZとか
     */
    private String channel;
    /**
     * quality control marker D=Data of Undetermined state, M=Merged Data, R=Raw
     * waveform Data, Q=QC'd data
     */
    private String qualityControl;
    /**
     * location IDの部分
     */
    private String location;
    /**
     * station name
     */
    private String station;

    SACFileName(String sacFileName) {
        name = sacFileName;

        String[] parts = sacFileName.split("\\.");
        // year = parts[0]; hour = parts[2];
        // min = parts[3]; sec = parts[4];
        // sec/10000 = parts[5]; jday = parts[1];
        switch (parts[parts.length - 1]) {
        case "SAC":
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            qualityControl = parts[4];
            break;
        case "SET":
            startTime = LocalDateTime
                    .of(Integer.parseInt(parts[5]), 1, 1, Integer.parseInt(parts[7]), Integer.parseInt(parts[8]),
                            Integer.parseInt(parts[9]), Integer.parseInt(parts[10]) * 100 * 1000)
                    .withDayOfYear(Integer.parseInt(parts[6]));
            // System.out.println(msec+" "+millisec);
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
            break;

        }
    }

    /**
     * Creates a new SAC file name for the resulting file after being set up.
     * @return (String) SAC file name of the form "network.station.location.channel.qualityControl.year.jday.hour.min.sec.msec.SET"
     */
    String getSetFileName(int year, int jday, int hour, int min, int sec, int msec) {
        return network + "." + station + "." + location + "." + channel + "." + qualityControl + "." +
                year + "." + jday + "." + hour + "." + min + "." + sec + "." + msec + ".SET";
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
     * @return (network).station.location.BHN.D.SAC
     */
    String getNetwork() {
        return network;
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
     * @return network.station.(location).BHN.D.SAC
     */
    String getLocation() {
        return location;
    }

    /**
     * @return network.(station).location.BHN.D.SAC
     */
    String getStation() {
        return station;
    }

    LocalDateTime getStartTime() {
        return startTime;
    }

    @Override
    public int compareTo(SACFileName o) {
        int c = network.compareTo(o.network);
        if (c != 0) return c;
        else if ((c = station.compareTo(o.station)) != 0) return c;
        else if ((c = location.compareTo(o.location)) != 0) return c;
        else if ((c = channel.compareTo(o.channel)) != 0) return c;
        else if ((c = qualityControl.compareTo(o.qualityControl)) != 0) return c;
        else return startTime.compareTo(o.startTime);
        // return 0;
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
    boolean isRelated(SACFileName sacFileName) {
        return sacFileName.channel.equals(channel) && sacFileName.network.equals(network) &&
                sacFileName.station.equals(station) && sacFileName.location.equals(location) &&
                sacFileName.qualityControl.equals(qualityControl);
    }

}
