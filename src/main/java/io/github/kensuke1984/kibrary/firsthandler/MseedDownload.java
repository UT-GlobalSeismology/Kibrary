package io.github.kensuke1984.kibrary.firsthandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Downloads mseed file and additional metadata for a given event
 *
 * @author Keisuke Otsuru
 * @version 2021/09/14
 */
public class MseedDownload {

    private static final String DATASELECT_URL = "http://service.iris.edu/fdsnws/dataselect/1/query?";
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

    /**
     * download process for all data needed in one event
     * @throws IOException
     */
    public void downloadAll() throws IOException {

        downloadMseed();
        mseed2sac();

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                downloadMetadata(sacPath);
            }
        }
    }

    /**
     * downloads Mseed file
     * @throws IOException
     */
    private void downloadMseed() throws IOException {
        String urlString = DATASELECT_URL + "net=" + networks + "&sta=*&loc=*&cha=BH?&starttime=" + toLine(startTime) +
                "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);
        long size = 0L;

        size = Files.copy(url.openStream(), MSEED_PATH , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.err.println("Downloaded : " + id + " - " + size + " bytes");
    }

    private String toLine(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * mseed2sac を行ったときの出力を返す
     * @return (boolean)
     * @throws IOException
     */
    private boolean mseed2sac() throws IOException {
        String command = "mseed2sac " + MSEED_FILENAME;
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); //  runevalresp in MseedSAC.javaを参考にした

        pb.directory(EVENT_DIR.getAbsoluteFile());
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String str;
            BufferedReader brstd = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while((str = brstd.readLine()) != null) {
                System.out.println(str);
            }
            brstd.close();
//            InputStream is = p.getInputStream();
//            int c;
//            while((c = is.read()) != -1);
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * downloads Station file and Resp file
     * @param sacPath
     * @throws IOException
     */
    private void downloadMetadata(Path sacPath) throws IOException {
        String[] sacInfo = sacPath.getFileName().toString().split("\\.");
        if(sacInfo.length != 9) {
            System.err.println("invalid sac file name; skipping");
            return;
        }

        // set information based on SAC File name extracted from MiniSeed
        String network = sacInfo[0];
        String station = sacInfo[1];
        String location = (sacInfo[2].isEmpty() ? "--" : sacInfo[2]);
        String channel = sacInfo[3];

        StationInformationIRIS stationInfo = new StationInformationIRIS(network, station, location, channel, startTime, endTime);
        stationInfo.downloadStationInformation(EVENT_DIR.toPath());

        RespDataIRIS respData = new RespDataIRIS(network, station, location, channel, startTime);
        respData.downloadRespData(EVENT_DIR.toPath());

    }
}