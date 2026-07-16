package io.github.kensuke1984.kibrary.entrance;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.math3.util.Precision;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Class for downloading and reading StationXML files.
 * @see <a href=https://www.fdsn.org/webservices/>FDSNWS Web Services</a>
 * @see <a href=https://service.earthscope.org/fdsnws/station/1/>EarthScope FDSNWS station Web Service</a>
 * @see <a href=https://www.orfeus-eu.org/data/eida/webservices/station/>ORFUES FDSNWS station Web Service</a>
 *
 * @since 2021/11/15
 * @author otsuru
 */
class StationXmlFile {

    private URL url;

    private final String xmlFileName;
    private final Path xmlPath;
    private final Path infPath;

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
    private String networkDescription = "";
    private String doi = "";

    /**
     * Constructor with options to be used in FDSNWS STATION Web Service.
     *
     * @param network  (String) Regular network (ex. IU).
     * @param station  (String) Station code.
     * @param location (String) Location code. Set "" if blank.
     * @param channel  (String) Channel code.
     * @param parentPath (Path) Path of folder to contain this StationXML File.
     */
    StationXmlFile(String network, String station, String location, String channel, Path parentPath) {

        this.network = network;
        this.station = station;
        this.location = location;
        this.channel = channel;

        // file name is "station.II.PFO.00.BHE.xml" or "station.IU.INU..BHE.xml"
        xmlFileName = "station." + network + "." + station + "." + location + "." + channel + ".xml";
        xmlPath = parentPath.resolve(xmlFileName);

        // inf file name is same as XML file but replacing ".xml" with ".inf"
        String infFileName = xmlFileName.substring(0, xmlFileName.lastIndexOf('.')) + ".inf";
        infPath = parentPath.resolve(infFileName);
    }

    StationXmlFile(Path xmlPath) {
        this.xmlPath = xmlPath;
        this.xmlFileName = xmlPath.getFileName().toString();

        // inf file name is same as XML file but replacing ".xml" with ".inf"
        String infFileName = xmlFileName.substring(0, xmlFileName.lastIndexOf('.')) + ".inf";
        infPath = xmlPath.getParent().resolve(infFileName);

        String[] parts = xmlFileName.split("\\.");
        network = parts[1];
        station = parts[2];
        location = parts[3];
        channel = parts[4];
    }

    /**
     * Sets the URL to be used in FDSNWS STATION Web Service.
     *
     * @see <a href=https://www.fdsn.org/webservices/>FDSNWS Web Services</a>
     * @see <a href=https://service.earthscope.org/fdsnws/station/1/>EarthScope FDSNWS station Web Service</a>
     * @see <a href=https://www.orfeus-eu.org/data/eida/webservices/station/>ORFUES FDSNWS station Web Service</a>
     *
     * @param urlHeader (String) Data-center-dependent part of the URL.
     * @param startTime (LocalDateTime) Start of time interval to find the response.
     * @param endTime (LocalDateTime) End of time interval to find the response.
     */
    void setRequest(String urlHeader, LocalDateTime startTime, LocalDateTime endTime) throws IOException {

        String requestLocation = (location.isEmpty() ? "--" : location);

        // set url here (version 2021-08-23) Requested Level is "response".
        // TODO: virtual networks may not be accepted
        String urlString = urlHeader + "net=" + network + "&" + "sta=" + station
                + "&" + "loc=" + requestLocation + "&" + "cha=" + channel
                + "&" + "starttime=" + startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&" + "endtime=" + endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "&level=response&format=xml&nodata=404";

        url = new URL(urlString);
    }

    /**
     * Method downloading station information from FDSNWS STATION Web Service.
     * The downloaded file name will take the form "station.II.PFO.00.BHE.xml" or "station.IU.INU..BHE.xml".
     * @return (boolean) true if download succeeded or if file not found ; false if download failed.
     * @throws IOException
     */
    boolean downloadStationXml() throws IOException {
        try (ReadableByteChannel readChannel = Channels.newChannel(url.openStream());
                FileOutputStream fos = new FileOutputStream(xmlPath.toFile());
                FileChannel outChannel = fos.getChannel()) {
            outChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
            return true;

        } catch (FileNotFoundException e) {
            // If stationXML file not found, return true.
            System.err.println("  ! File not found for " + network + " " + station + " " + location + " " + channel);
            List<String> lines = List.of("File not found for " + network + " " + station + " " + location + " " + channel);
            Files.write(infPath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            return true;

        } catch (IOException e) {
            // If stationXML file cannot be downloaded, return false.
            System.err.println("!! Failed to download stationXML file.");
            System.err.println(e.toString());
            return false;
        }
    }

    /**
     * Method to read a stationXML file.
     * @return (boolean) Whether reading succeeded.
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

        } catch (FileNotFoundException e) {
            // If stationXML file not found, return false.
            System.err.println("!! File not found for " + network + " " + station + " " + location + " " + channel);
            return false;
        } catch (SAXException | ParserConfigurationException | IOException e) {
            // If stationXML file cannot be read, return false.
            System.err.println("!! Failed to read " + xmlFileName + " : " + e.toString());
            return false;
        }
        return check();
    }

    /**
     * Checks whether latitude, longitude, dip, and azimuth has been properly set.
     * Their individual values are not checked.
     * @return (boolean) Whether the 4 values are set.
     */
    private boolean check() {
        if (latitude.isEmpty()) {
            System.err.println("!! Latitude empty: " + xmlFileName);
            return false;
        } else if (longitude.isEmpty()) {
            System.err.println("!! Longitude empty: " + xmlFileName);
            return false;
        } else if (dip.isEmpty()) {
            System.err.println("!! Dip empty: " + xmlFileName);
            return false;
        } else if (azimuth.isEmpty()) {
            // for channels of Z component, it is OK if azimuth is empty; it is set 0 here to prevent NumberFormatException
            // CAUTION: up is dip=-90, horizontal is dip=0
            if (Precision.equals(Double.parseDouble(dip), -90, 0.01)) {
                azimuth = "0";
            } else {
                System.err.println("!! Azimuth empty: " + xmlFileName);
                return false;
            }
        }
        return true;
    }

    String getXmlFileName() {
        return xmlFileName;
    }

    Path getXmlPath() {
        return xmlPath;
    }

    Path getInfPath() {
        return infPath;
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

    /**
     * @return (String) MAY BE EMPTY!!
     */
    String getElevation() {
        return elevation;
    }

    /**
     * @return (String) MAY BE EMPTY!!
     */
    String getDepth() {
        return depth;
    }

    String getAzimuth() {
        return azimuth;
    }

    String getDip() {
        return dip;
    }

    String getNetworkDescription() {
        return networkDescription;
    }

    String getDOI() {
        return doi;
    }

    private class StationXmlHandler extends DefaultHandler {
        String text;
        /**
         * Whether in the beginning part of "Network", before "Station" starts.
         */
        boolean atNetworkBeginning = false;
        /**
         * Whether reading "DOI".
         */
        boolean inDOI = false;
        /**
         * Whether "Channel" has started.
         */
        boolean inChannel = false;

        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (qName.equals("Network")) {
                atNetworkBeginning = true;
            }
            if (qName.equals("Identifier") && attributes.getValue("type").equals("DOI")) {
                inDOI = true;
            }
            if (qName.equals("Station")) {
                atNetworkBeginning = false;
            }
            if (qName.equals("Channel")) {
                inChannel = true;
            }
        }
        public void characters(char[] ch, int start, int length) {
            text = new String(ch, start, length);
        }
        public void endElement(String uri, String localName, String qName) {
            if (atNetworkBeginning) {
                if (qName.equals("Description")) {
                    // if there is a meaningless " ()" at the end of description, get rid of it
                    networkDescription = (text.endsWith(" ()") ? text.substring(0, text.length() - 3) : text);
                }
            }
            if (inDOI) {
                if (qName.equals("Identifier")) {
                    // remove new line that is usually after the DOI
                    doi = text.replace("\n", "").replace("\r", "");
                }
                inDOI = false;
            }
            if (inChannel) {
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

}
