package io.github.kensuke1984.kibrary.entrance;

/**
 * Class for handling tasks related to the name of SAC files.
 * To be used in {@link EventDataPreparer}.
 * <p>
 * Note the difference in convention of SAC file names:
 * <ul>
 * <li> MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC" </li>
 * <li> SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC" </li>
 * </ul>
 */
class SacFileName {
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
     * quality control marker D=Data of Undetermined state, M=Merged Data, R=Raw
     * waveform Data, Q=QC'd data
     */
    private String qualityControl;

    SacFileName(String sacFileName, String style) {
        String[] parts = sacFileName.split("\\.");

        if (style.equals("mseed")) {
            if (parts.length != 9) {
                throw new IllegalArgumentException("Invalid SAC file name for mseed style.");
            }
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            qualityControl = parts[4];

        } else if (style.equals("seed")) {
            if (parts.length != 12) {
                throw new IllegalArgumentException("Invalid SAC file name for seed style.");
            }
            network = parts[6];
            station = parts[7];
            location = parts[8];
            channel = parts[9];
            qualityControl = parts[10];

        } else if (style.equals("formatted")) {
            if (parts.length != 12) {
                throw new IllegalArgumentException("Invalid formatted SAC file name.");
            }
            network = parts[0];
            station = parts[1];
            location = parts[2];
            channel = parts[3];
            qualityControl = parts[4];

        } else {
            throw new IllegalArgumentException("Invalid SAC file style.");
        }

    }

    /**
     * Creates a new SAC file name for the resulting file after being set up.
     * @return (String) SAC file name of the form "network.station.location.channel.qualityControl.year.jday.hour.min.sec.msec.SET"
     */
    String getFormattedFileName(int year, int jday, int hour, int min, int sec, int msec) {
        return network + "." + station + "." + location + "." + channel + "." + qualityControl + "." +
                year + "." + jday + "." + hour + "." + min + "." + sec + "." + msec + ".SAC";
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



}
