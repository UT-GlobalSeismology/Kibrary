package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Class for RESP files, which allows us to download RESP files IRIS DMC IRISWS RESP Web Service
 * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web Service Documentation
 * @author Kenji Kawai
 * @version 0.1.3
 */
public class RespDataIRIS {

    private static final String RESP_URL = "http://service.iris.edu/irisws/resp/1/query?";
    private String url;
    private String responseFile;

    /**
     * Constructor with options for IRIS DMC IRISWS RESP Web Service
     *
     * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
     *      Service Documentation
     * @param network  (String) Regular network (ex. IU) or virtual network (ex. _FDSN).
     * @param station  (String) Station code.
     * @param location (String) Location code. Set "" if blank.
     * @param channel  (String) Channel code.
     * @param time     (LocalDateTime) Find the response for the given time.
     */
    public RespDataIRIS(String network, String station, String location, String channel, LocalDateTime time) {

        String requestLocation = (location.isEmpty() ? "--" : location);

        // set url here (version 2021-08-23).
        url = RESP_URL + "net=" + network + "&" + "sta=" + station + "&" + "cha="
                + channel + "&" + "loc=" + requestLocation + "&" + "time=" + time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // file name is "RESP.II.PFO.00.BHE" or "RESP.IU.INU..BHE"
        responseFile = "RESP." + network + "." + station + "." + location + "." + channel;

    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * Output directory is here.
     */
    public void downloadRespData() {
        Path outPath = Paths.get(responseFile);

        try {
            URL IRISWSURL = new URL(url);
            long size = 0L;

            size = Files.copy(IRISWSURL.openStream(), outPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
            System.out.println("Downloaded : " + responseFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.out.println(e);
        }
    }

    /**
     * Method downloading the Station information from IRIS/WS.
     * @param outDir (Path) Output directory
     */
    public void downloadRespData (Path outDir) {
        Path outPath = outDir.resolve(responseFile); // 　出力のディレクトリの指定

        try {
            URL IRISWSURL = new URL(url);
            long size = 0L;

            size = Files.copy(IRISWSURL.openStream(), outPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
            System.out.println("Downloaded : " + responseFile + " - " + size + " bytes");

        } catch (IOException e) {
            System.out.println(e);
        }
    }


    /**
     * This main method is for debug.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        LocalDateTime time = LocalDateTime.of(2000,1,1,0,0,0);
        RespDataIRIS rd = new RespDataIRIS("II", "PFO", "00", "BHE", time);
        rd.downloadRespData();
    }

}
