package io.github.kensuke1984.kibrary.entrance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Downloads mseed file, opens it, and downloads necessary metadata for a given event.
 * <p>
 * Note the convention of resulting SAC file names:
 * <ul>
 * <li> MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC" </li>
 * <li> SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC" </li>
 * </ul>
 * <p>
 * This class requires that mseed2sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/mseed2sac/>mseed2sac</a>
 * can be found at IRIS.
 *
 * @author Keisuke Otsuru
 * @version 2021/09/14
 */
public class EventDataPreparer {

    private static final String DATASELECT_URL = "http://service.iris.edu/fdsnws/dataselect/1/query?";
    private String date = Utilities.getTemporaryString();

    private final EventFolder EVENT_DIR;
    private final GlobalCMTID ID;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String MSEED_FILENAME;

    public EventDataPreparer (EventFolder eventFolder) {

        EVENT_DIR = eventFolder;
        ID = eventFolder.getGlobalCMTID();

    }

    /**
     * Sets parameters. This method shall be used when the mseed file already exists.
     * @param mseedFile (String) Name of existing mseed file
     */
    public void setParameters (String mseedFile) {
        startTime = ID.getEvent().getCMTTime();
        endTime = ID.getEvent().getCMTTime();

        MSEED_FILENAME = mseedFile;
    }

    /**
     * Downloads Mseed file using specifications set by the constructor.
     * @throws IOException
     */
    public void downloadMseed(String networks, String channels, int headAdjustment, int footAdjustment) throws IOException {
        LocalDateTime cmtTime = ID.getEvent().getCMTTime();
        startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        String urlString = DATASELECT_URL + "net=" + networks + "&sta=*&loc=*&cha=" + channels +
                "&starttime=" + toLine(startTime) + "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);
        long size = 0L;

        MSEED_FILENAME = ID + "." + date + ".mseed";
        Path mseedPath = EVENT_DIR.toPath().resolve(MSEED_FILENAME); // 出力パスの指定
        size = Files.copy(url.openStream(), mseedPath, StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.err.println("Downloaded : " + ID + " - " + size + " bytes");
    }

    /**
     * Turns a date and time into a format accepted by FDSNWS
     * @param time (LocalDateTime)
     * @return (String) time in the format accepted by FDSNWS
     */
    private String toLine(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Runs mseed2sac.
     * @return (boolean) true if mseed2sac succeeds
     * @throws IOException
     */
    public boolean mseed2sac() throws IOException {
        System.err.println("mseed2sac " + MSEED_FILENAME);
        String command = "mseed2sac " + MSEED_FILENAME;
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); // runevalresp in MseedSAC.javaを参考にした

        pb.directory(EVENT_DIR.getAbsoluteFile()); // this will be the working directory of the command
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            pb.redirectErrorStream(true); // the standard error stream will be redirected to the standard output stream
            Process p = pb.start();

            // The buffer of the output must be kept reading, or else, the process will freeze when the buffer becomes full.
            // Even if you want to stop printing the output from mseed2sac, just erase the line with println() and nothing else.
            String str;
            BufferedReader brstd = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while((str = brstd.readLine()) != null) { // reading the buffer
                System.out.println(str); // Comment out only this single line if you don't want the output.
            }
            brstd.close();

            return p.waitFor() == 0; // wait until the command is finished
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Downloads Station files and Resp files for the event, given a set of SAC files.
     * The downloads may be skipped if the SAC file name does not take a valid form.
     * @throws IOException
     */
    public void downloadMetadata() throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                String[] sacInfo = sacPath.getFileName().toString().split("\\.");
                if(sacInfo.length != 9) {
                    System.err.println("invalid sac file name; skipping");
                    return;
                }

                // set information based on SAC File name created by mseed2sac
                String network = sacInfo[0];
                String station = sacInfo[1];
                String location = sacInfo[2]; //(sacInfo[2].isEmpty() ? "--" : sacInfo[2]);
                String channel = sacInfo[3];

                StationInformationFile stationInfo = new StationInformationFile(network, station, location, channel);
                stationInfo.setRequest(startTime, endTime);
                stationInfo.downloadStationInformation(EVENT_DIR.toPath());

                RespDataFile respData = new RespDataFile(network, station, location, channel);
                respData.setRequest(startTime);
                respData.downloadRespData(EVENT_DIR.toPath());
            }
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException{
        // working directory is set to current directory
        Path workPath = Paths.get("");

        // import event directories in working directory
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(workPath);
        if (eventDirs.isEmpty()) {
            System.err.println("No events found.");
            return;
        }
        System.err.println(eventDirs.size() + " events are found.");

        // for each event directory
        for (EventFolder eventDir : eventDirs) {
            // create new instance for the event
            EventDataPreparer edp = new EventDataPreparer(eventDir);

            System.err.println("edp prepared");

            //for each mseed file (though there is probably only one)
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    System.err.println("operating for " + mseedPath + " ...");
                    //set (or reset) mseed file name
                    edp.setParameters(mseedPath.getFileName().toString());
                    // expand mseed file
                    edp.mseed2sac();
                }
            }

            // download metadata for the expanded SAC files
            edp.downloadMetadata();

        }
        System.err.println("Finished!");

    }
}