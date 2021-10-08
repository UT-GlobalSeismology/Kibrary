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
 * Class for downloading mseed file, opening it, and downloading necessary metadata for a given event.
 * Operations on mseed files that are already downloaded can also be done by using the main method.
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
    private boolean IS_FULL_SEED = false;

    /**
     * The event folder and its GCMT ID is set.
     * @param eventFolder (EventFolder) The event folder.
     * Input files must be in this folder if there are any. Output files will also be placed in this folder.
     */
    public EventDataPreparer (EventFolder eventFolder) {

        EVENT_DIR = eventFolder;
        ID = eventFolder.getGlobalCMTID();

    }

    /**
     * Sets parameters. This method shall be used when the mseed file already exists.
     * @param mseedFile (String) Name of existing mseed file
     * @param full (boolean) Whether the input file is actually a full seed file
     */
    public void setParameters (String mseedFile, boolean full) {
        startTime = ID.getEvent().getCMTTime();
        endTime = ID.getEvent().getCMTTime();

        MSEED_FILENAME = mseedFile;
        IS_FULL_SEED = full;
    }

    /**
     * Downloads Mseed file from FDSNWS using specified parameters.
     * @param networks (String) Network names for request, listed using commas. Wildcards (*, ?) allowed. Virtual networks are unsupported.
     * @param channels (String) Channels to be requested, listed using commas. Wildcards (*, ?) allowed.
     * @param headAdjustment (int) [min] The starting time of request with respect to event time.
     * @param footAdjustment (int) [min] The ending time of request with respect to event time.
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
     * Runs mseed2sac to extract SAC files: "mseed2sac [mseedfile]".
     * In case the input is a full seed file, rdseed is run instead: "rdseed -fd [seedfile]" (this does create RESP files).
     * @return (boolean) true if mseed2sac succeeds
     * @throws IOException
     */
    public boolean openSeed() throws IOException {
        String command;
        if (!IS_FULL_SEED) {
            //System.err.println("mseed2sac " + MSEED_FILENAME);
            command = "mseed2sac " + MSEED_FILENAME;
        } else {
            //System.err.println("rdseed -fd " + MSEED_FILENAME);
            command = "rdseed -fd " + MSEED_FILENAME;
        }

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
     * Renames seed style SAC file names to mseed style names.
     * <p>
     * Note the difference in convention of SAC file names:
     * <ul>
     * <li> MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC" </li>
     * <li> SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC" </li>
     * </ul>
     * @throws IOException
     */
    public void renameToMseedStyle() throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                String[] parts = sacPath.getFileName().toString().split("\\.");
                if(parts.length != 12) {
                    System.err.println("invalid sac file name; skipping " + sacPath.getFileName());
                    continue;
                }

                String newName = parts[6] + "." + parts[7] + "." + parts[8] + "." + parts[9] + "." + parts[10] + "." +
                        parts[0] + "." + parts[1] + "." + parts[2] + parts[3] + parts[4] + ".SAC";
                Files.move(sacPath, sacPath.resolveSibling(newName));
            }
        }

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
                    System.err.println("invalid sac file name; skipping " + sacPath.getFileName());
                    continue;
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
     * A method to expand existing mseed files and download associated STATION and RESP files.
     * The input mseed files must be in event directories under the current directory.
     * Output files will be placed in the input event directory.
     * @param args
     * @throws IOException
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

            // for each (full) seed file (in case some exist)
            // this is done before handling mseed files so that renameToMseedStyle() does not get applied to SAC files from mseed
            try (DirectoryStream<Path> seedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.seed")) {
                for (Path seedPath : seedPaths) {
                    System.err.println("operating for " + seedPath + " ...");
                    //set (or reset) seed file name
                    edp.setParameters(seedPath.getFileName().toString(), true);
                    // expand mseed file
                    edp.openSeed();
                    // rename seed style SAC file names to mseed style
                    edp.renameToMseedStyle();
                }
            }

            // for each mseed file (though there is probably only one)
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    System.err.println("operating for " + mseedPath + " ...");
                    //set (or reset) mseed file name
                    edp.setParameters(mseedPath.getFileName().toString(), false);
                    // expand mseed file
                    edp.openSeed();
                }
            }

            // download metadata for all the expanded SAC files in the event directory
            edp.downloadMetadata();

        }
        System.err.println("Finished!");

    }
}