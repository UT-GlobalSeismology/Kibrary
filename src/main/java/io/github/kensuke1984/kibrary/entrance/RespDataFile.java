package io.github.kensuke1984.kibrary.entrance;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Class for RESP files, which allows us to download RESP files from IRIS DMC IRISWS RESP Web Service.
 * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web Service Documentation
 *
 * @since 2021/08/25
 * @author Kenji Kawai
 */
public class RespDataFile {

    private static final String RESP_URL = "http://service.iris.edu/irisws/resp/1/query?";
    private URL url;
    private String responseFile;
    private String spectraFile;

    private String network = "";
    private String station = "";
    private String location = "";
    private String channel = "";

    /**
     * Constructor with options for IRIS DMC IRISWS RESP Web Service
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param network  (String) Regular network (ex. IU) or virtual network (ex. _FDSN).
     * @param station  (String) Station code.
     * @param location (String) Location code. Set "" if blank.
     * @param channel  (String) Channel code.
     */
    public RespDataFile(String network, String station, String location, String channel) {

        this.network = network;
        this.station = station;
        this.location = location;
        this.channel = channel;

        // file name is "RESP.II.PFO.00.BHE" or "RESP.IU.INU..BHE"
        responseFile = "RESP." + network + "." + station + "." + location + "." + channel;
        // file name is "SPECTRA.II.PFO.00.BHE" or "SPECTRA.IU.INU..BHE"
        spectraFile = "SPECTRA." + network + "." + station + "." + location + "." + channel;

    }
    /**
     * Set the URL to be used in IRIS DMC IRISWS RESP Web Service
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param time     (LocalDateTime) Find the response for the given time.
     */
    public void setRequest(LocalDateTime time) throws IOException {

        String requestLocation = (location.isEmpty() ? "--" : location);

        // set url here (version 2021-08-23).
        String urlString = RESP_URL + "net=" + network + "&" + "sta=" + station + "&" + "cha="
                + channel + "&" + "loc=" + requestLocation + "&" + "time=" + time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        url = new URL(urlString);
    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * Output directory is here.
     */
    public void downloadRespData() {
        Path outPath = Paths.get(responseFile);

        try (ReadableByteChannel readChannel = Channels.newChannel(url.openStream());
                FileOutputStream fos = new FileOutputStream(outPath.toFile()); FileChannel outChannel = fos.getChannel()) {
            long size = outChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
            System.err.println("Downloaded : " + responseFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.err.println(e.toString());
        }
    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * @param outDir (Path) Output directory
     */
    public void downloadRespData(Path outDir) {
        Path outPath = outDir.resolve(responseFile);

        try (ReadableByteChannel readChannel = Channels.newChannel(url.openStream());
                FileOutputStream fos = new FileOutputStream(outPath.toFile()); FileChannel outChannel = fos.getChannel()) {
            long size = outChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
            System.err.println("Downloaded : " + responseFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.err.println(e.toString());
        }
    }

    /**
     * @return (String) Name of RESP file
     */
    public String getRespFile() {
        return responseFile;
    }

    /**
     * @return (String) Name of SPECTRA file
     */
    public String getSpectraFile() {
        return spectraFile;
    }

}