package io.github.kensuke1984.kibrary.datarequest;

import java.io.IOException;
import java.net.URL;
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
 * @author Kensuke Konishi
 * @version 0.1.4
 */
public class MseedDownload {

    private String dataselectURL = "http://service.iris.edu/fdsnws/dataselect/1/query?";
    private String date = Utilities.getTemporaryString();

    private GlobalCMTID id;
    private String networks;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private EventFolder eventDir;
    private String mseedFile;
    private Path mseedPath;

    public MseedDownload (GlobalCMTID id, String networks, int headAdjustment, int footAdjustment, Path outPath) {

        this.id = id;
        this.networks = networks;
        LocalDateTime cmtTime = id.getEvent().getCMTTime();
        startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        eventDir = new EventFolder(outPath.resolve(id.toString()));
        if (!eventDir.mkdirs()) throw new RuntimeException("Can't create " + eventDir);

        mseedFile = id + "." + date + ".mseed";
        mseedPath = eventDir.toPath().resolve(mseedFile); // 出力パスの指定
}

    public void downloadAll() throws IOException {
        downloadMseed();
        mseed2sac();
    }

    private void downloadMseed() throws IOException {
        String url_string = dataselectURL + "net=" + networks + "&sta=*&loc=*&cha=BH?&starttime=" + toLine(startTime) +
                "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(url_string);
        long size = 0L;

        size = Files.copy(url.openStream(), mseedPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.out.println("Downloaded : " + id + " - " + size + " bytes");
    }

    private String toLine(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private boolean mseed2sac() throws IOException {
        String command = "mseed2sac " + mseedFile;
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); //  runevalresp in MseedSAC.javaを参考にした

        pb.directory(eventDir.getAbsoluteFile());
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}