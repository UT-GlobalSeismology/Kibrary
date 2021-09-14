package io.github.kensuke1984.kibrary.datarequest;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * It makes a data requesting mail.
 *
 * @author Keisuke Otsuru
 * @version 2021/09/14
 */
public class MseedDownload {

    private static final String DATASELECT_URL = "http://service.iris.edu/fdsnws/dataselect/1/query?";
    private static final String STATION_URL = "http://service.iris.edu/fdsnws/station/1/query?";
    private static final String RESP_URL = "http://service.iris.edu/irisws/resp/1/query?";
    private String date = Utilities.getTemporaryString();

    private GlobalCMTID id;
    private String networks;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private final EventFolder EVENT_DIR;
    private final String MSEED_FILENAME;
    private final Path MSEED_PATH;

    public MseedDownload (GlobalCMTID id, String networks, int headAdjustment, int footAdjustment, Path outPath) {
        this.id = id;
        this.networks = networks;
        LocalDateTime cmtTime = id.getEvent().getCMTTime();
        startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        EVENT_DIR = new EventFolder(outPath.resolve(id.toString()));
        if (!EVENT_DIR.mkdirs()) throw new RuntimeException("Can't create " + EVENT_DIR);

        MSEED_FILENAME = id + "." + date + ".mseed";
        MSEED_PATH = EVENT_DIR.toPath().resolve(MSEED_FILENAME); // 出力パスの指定
    }

    public void downloadAll() throws IOException {

        downloadMseed();
        mseed2sac();

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                downloadMetadata(sacPath);
            }
        }
    }

    private void downloadMseed() throws IOException {
        String urlString = DATASELECT_URL + "net=" + networks + "&sta=*&loc=*&cha=BH?&starttime=" + toLine(startTime) +
                "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);
        long size = 0L;

        size = Files.copy(url.openStream(), MSEED_PATH , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.out.println("Downloaded : " + id + " - " + size + " bytes");
    }

    private String toLine(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private boolean mseed2sac() throws IOException {
        String command = "mseed2sac " + MSEED_FILENAME;
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); //  runevalresp in MseedSAC.javaを参考にした

        pb.directory(EVENT_DIR.getAbsoluteFile());
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadMetadata(Path sacPath) throws IOException {
        String[] sacInfo = sacPath.getFileName().toString().split("\\.");
        if(sacInfo.length != 9) {
            System.out.println("invalid sac file name; skipping");
            return;
        }

        // set information based on SAC File name extracted from MiniSeed
        String network = sacInfo[0];
        String station = sacInfo[1];
        String location = (sacInfo[2].isEmpty() ? "--" : sacInfo[2]);
        String channel = sacInfo[3];

        // STATION : set url (version 2021-08-23). Request Level is "Channel."
        String stationUrlString = STATION_URL + "net=" + network + "&sta=" + station + "&loc=" + location + "&cha=" + channel
                + "&starttime=" + toLine(startTime) + "&endtime=" + toLine(endTime)
                + "&level=channel&format=text&includecomments=true&nodata=404";
        URL stationUrl = new URL(stationUrlString);
        long stationFileSize = 0L;

        // file name is "STATION.II.PFO.00.BHE" or "STATION.IU.INU.--.BHE"
        String stationFile = "STATION." + network + "." + station + "." + location + "." + channel;
        Path stationPath = EVENT_DIR.toPath().resolve(stationFile); // 出力パスの指定

        stationFileSize = Files.copy(stationUrl.openStream(), stationPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.out.println("Downloaded : " + stationFile + " - " + stationFileSize + " bytes");

        // RESP : set url (version 2021-08-23).
        String respUrlString = RESP_URL + "net=" + network + "&sta=" + station + "&cha=" + channel + "&loc=" + location
                + "&time=" + toLine(startTime);
        URL respUrl = new URL(respUrlString);
        long respFileSize = 0L;

        // file name is "RESP.II.PFO.00.BHE" or "RESP.IU.INU.--.BHE"
        String respFile = "RESP." + network + "." + station + "." + location + "." + channel;
        Path respPath = EVENT_DIR.toPath().resolve(respFile); // 出力パスの指定

        respFileSize = Files.copy(respUrl.openStream(), respPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.out.println("Downloaded : " + respFile + " - " + respFileSize + " bytes");
    }
}