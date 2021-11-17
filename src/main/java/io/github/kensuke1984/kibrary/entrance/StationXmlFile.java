package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Class for downloading and reading Station XML files.
 * @see <a href=http://service.iris.edu/fdsnws/station/1/>IRIS DMC FDSNWS station Web Service</a>
 */
class StationXmlFile {

    private static final String STATION_URL_IRIS = "http://service.iris.edu/fdsnws/station/1/query?";
    private static final String STATION_URL_ORFEUS = "http://www.orfeus-eu.org/fdsnws/station/1/query?";
    private String url;
    private String xmlFile;
    private Path xmlPath;

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

    /**
     * Constructor with options to be used in IRIS DMC FDSNWS STATION Web Service.
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation</a>
     * @param network  (String) Regular network (ex. IU) or virtual network (ex. _FDSN).
     * @param station  (String) Station code.
     * @param location (String) Location code. Set "" if blank.
     * @param channel  (String) Channel code.
     * @param parentPath (Path) Path of folder to contain this Station Information File.
     */
    StationXmlFile(String network, String station, String location, String channel, Path parentPath) {

        this.network = network;
        this.station = station;
        this.location = location;
        this.channel = channel;

        // file name is "station.II.PFO.00.BHE.xml" or "station.IU.INU..BHE.xml"
        xmlFile = "station." + network + "." + station + "." + location + "." + channel + ".xml";
        xmlPath = parentPath.resolve(xmlFile);

    }

    /**
     * Sets the URL to be used in IRIS DMC FDSNWS STATION Web Service.
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param startTime   (LocalDateTime) Find the response for the given time.
     * @param endTime     (LocalDateTime) Find the response for the given time.
     */
    void setRequest(String datacenter, LocalDateTime startTime, LocalDateTime endTime) {

        String requestLocation = (location.isEmpty() ? "--" : location);

        // set url here (version 2021-08-23) Requested Level is "response".
        // TODO: virtual networks may not be accepted
        switch (datacenter) {
        case "IRIS":
            url = STATION_URL_IRIS;
            break;
        case "ORFEUS":
            url = STATION_URL_ORFEUS;
            break;
        default:
            throw new IllegalStateException("Invalid datacenter name");
        }
        url = url + "net=" + network + "&" + "sta=" + station
                + "&" + "loc=" + requestLocation + "&" + "cha=" + channel
                + "&" + "starttime=" + startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&" + "endtime=" + endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&level=response&format=xml&includecomments=true&nodata=404";

    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * The downloaded file name will take the form "station.II.PFO.00.BHE.xml" or "station.IU.INU..BHE.xml".
     * @return (boolean) true if download succeeded
     */
    boolean downloadStationXml() {
        try {
            URL IRISWSURL = new URL(url);
            long size = 0L;

            size = Files.copy(IRISWSURL.openStream(), xmlPath , StandardCopyOption.REPLACE_EXISTING);
            //System.out.println("Downloaded : " + xmlFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.err.println("!! Failed to download stationXML file.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Method to read a stationXML file.
     * @return (boolean) true if read succeeded
     */
    boolean readStationXml() {
        try {
            // 1. SAXParserFactoryを取得
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // 2. SAXParserを取得
            SAXParser parser = factory.newSAXParser();
            // 3. SAXのイベントハンドラを生成(下で定義しているクラスのインスタンス)
            StationXmlHandler handler = new StationXmlHandler();
            // 4. SAXParserにXMLを読み込ませて、SAXのイベントハンドラに処理を行わせる
            parser.parse(xmlPath.toFile(), handler);

        } catch (SAXException | ParserConfigurationException | IOException e) {
            System.err.println("!! Failed to read stationXML file.");
            e.printStackTrace();
            return false;
        }
        return true;
    }


    String getUrl() {
        return url;
    }


    void setUrl(String url) {
        this.url = url;
    }


    String getXmlFile() {
        return xmlFile;
    }


    String getNetwork() {
        return network;
    }


    String getStation() {
        return station;
    }


    String getLocation() {
        return location;
    }


    String getChannel() {
        return channel;
    }


    String getLatitude() {
        return latitude;
    }


    String getLongitude() {
        return longitude;
    }


    String getElevation() {
        return elevation;
    }


    String getDepth() {
        return depth;
    }


    String getAzimuth() {
        return azimuth;
    }


    String getDip() {
        return dip;
    }

    private class StationXmlHandler extends DefaultHandler {
        String text;
        boolean inChannel = false;

        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (qName.equals("Channel")) {
                inChannel = true;
            }
        }
        public void characters(char[] ch, int start, int length) {
            text = new String(ch, start, length);
        }
        public void endElement(String uri, String localName, String qName) {
            if (!inChannel) {
                return;
            }
            if (qName.equals("Latitude")) {
                latitude = text;
            } else if (qName.equals("Longitude")) {
                longitude = text;
            } else if (qName.equals("Elevation")) {
                elevation = text;
            } else if (qName.equals("Depth")) {
                depth = text;
            } else if (qName.equals("Azimuth")) {
                azimuth = text;
            } else if (qName.equals("Dip")) {
                dip = text;
            }
        }
    }

}
