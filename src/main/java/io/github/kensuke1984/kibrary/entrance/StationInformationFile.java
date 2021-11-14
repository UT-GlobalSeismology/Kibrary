package io.github.kensuke1984.kibrary.entrance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Class for Station files, which allows us to download Station Information files.
 * @see <a href=http://service.iris.edu/fdsnws/station/1/> IRIS DMC FDSNWS station Web Service
 */
public class StationInformationFile {

    private static final String STATION_URL = "http://service.iris.edu/fdsnws/station/1/query?";
    private String url;
    private String stationFile;
    private Path stationPath;

    private String network = "";
    private String station = "";
    private String location = "";
    private String channel = "";
    private String latitude = "";
    private String longitude = "";
    private String elevation = "";
    private String depth = "";
    private String azimuth = "";
    private String dip = "";
    private String sensorDescription = "";
    private String scale = "";
    private String scalefreq = "";
    private String scaleunits = "";
    private String samplerate = "";
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Constructor with options to be used in IRIS DMC FDSNWS STATION Web Service.
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param network  (String) Regular network (ex. IU) or virtual network (ex. _FDSN).
     * @param station  (String) Station code.
     * @param location (String) Location code. Set "" if blank.
     * @param channel  (String) Channel code.
     * @param parentPath (Path) Path of folder to contain this Station Information File.
     */
    public StationInformationFile(String network, String station, String location, String channel, Path parentPath) {

        this.network = network;
        this.station = station;
        this.location = location;
        this.channel = channel;

        // file name is "STATION.II.PFO.00.BHE" or "STATION.IU.INU..BHE"
        stationFile = "STATION." + network + "." + station + "." + location + "." + channel;
        stationPath = parentPath.resolve(stationFile);

    }

    /**
     * Sets the URL to be used in IRIS DMC FDSNWS STATION Web Service.
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param startTime   (LocalDateTime) Find the response for the given time.
     * @param endTime     (LocalDateTime) Find the response for the given time.
     */
    public void setRequest(LocalDateTime startTime, LocalDateTime endTime) {

        String requestLocation = (location.isEmpty() ? "--" : location);

        // set url here (version 2021-08-23) Requested Level is "Channel."
        // TODO: virtual networks may not be accepted
        url = STATION_URL + "net=" + network + "&" + "sta=" + station
                + "&" + "loc=" + requestLocation + "&" + "cha=" + channel
                + "&" + "starttime=" + startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&" + "endtime=" + endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&level=channel&format=text&includecomments=true&nodata=404";

    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * The downloaded file name will take the form "STATION.II.PFO.00.BHE" or "STATION.IU.INU..BHE"
     */
    public void downloadStationInformation() {
        try {
            URL IRISWSURL = new URL(url);
            long size = 0L;

            size = Files.copy(IRISWSURL.openStream(), stationPath , StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Downloaded : " + stationFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.out.println(e);
        }
    }

    /**
     * Methods reading the station information and setting them as local variables.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void readStationInformation() throws FileNotFoundException, IOException {
        File file = new File(stationPath.toString());
        String line1 = ""; //TODO 複数行の対応
        String line2 = "";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String text;
            while((text = br.readLine())!=null) {
                if(text.startsWith("#")) { line1 = text.replace("#",""); continue; }
                line2 = text;
            }
        }

        String [] head = line1.split("[\\s]*\\|[\\s]*");
        String [] data = line2.split("[\\s]*\\|[\\s]*");
        if (head.length != data.length) {
            throw new IOException("invalid StationInformationFile");
        }

        for(int i =0; i< head.length; i++) {
            if(head[i].matches("Network")) {network = data[i];}
            else if(head[i].matches("Station")) {station = data[i];}
            else if(head[i].matches("Location")) {location = data[i];}
            else if(head[i].matches("Channel")) {channel = data[i];}
            else if(head[i].matches("Latitude")) {latitude = data[i];}
            else if(head[i].matches("Longitude")) {longitude = data[i];}
            else if(head[i].matches("Elevation")) {elevation = data[i];}
            else if(head[i].matches("Depth")) {depth = data[i];}
            else if(head[i].matches("Azimuth")) {azimuth = data[i];}
            else if(head[i].matches("Dip")) {dip = data[i];}
            else if(head[i].matches("SensorDescription")) {sensorDescription = data[i];}
            else if(head[i].matches("Scale")) {scale = data[i];}
            else if(head[i].matches("ScaleFreq")) {scalefreq = data[i];}
            else if(head[i].matches("ScaleUnits")) {scaleunits = data[i];}
            else if(head[i].matches("SampleRate")) {samplerate = data[i];}
            else if(head[i].matches("StartTime")) {startTime = LocalDateTime.parse(data[i]);}
//          else if(head[i].matches("EndTime")) {if(data[i]!=null){endTime = LocalDateTime.parse(data[i]);}} // TODO Some station information not including EndTime
        }
    }

    public String getUrl() {
        return url;
    }


    public void setUrl(String url) {
        this.url = url;
    }


    public String getStationFile() {
        return stationFile;
    }


    public String getNetwork() {
        return network;
    }


    public String getStation() {
        return station;
    }


    public String getLocation() {
        return location;
    }


    public String getChannel() {
        return channel;
    }


    public String getLatitude() {
        return latitude;
    }


    public String getLongitude() {
        return longitude;
    }


    public String getElevation() {
        return elevation;
    }


    public String getDepth() {
        return depth;
    }


    public String getAzimuth() {
        return azimuth;
    }


    public String getDip() {
        return dip;
    }


    public String getSensorDescription() {
        return sensorDescription;
    }


    public String getScale() {
        return scale;
    }


    public String getScalefreq() {
        return scalefreq;
    }


    public String getScaleunits() {
        return scaleunits;
    }


    public String getSamplerate() {
        return samplerate;
    }


    public LocalDateTime getStartTime() {
        return startTime;
    }


    public LocalDateTime getEndTime() {
        return endTime;
    }


}
