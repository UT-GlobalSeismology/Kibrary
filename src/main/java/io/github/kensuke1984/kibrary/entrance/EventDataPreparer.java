package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.external.ExternalProcess;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.FullPosition;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for downloading mseed file, opening it, and downloading necessary metadata for a given event.
 * Operations on mseed files (or full seed files) that are already downloaded can also be done by using the main method.
 * <p>
 * Note that the convention of resulting SAC file names will be in <strong>mseed format</strong>.
 * <ul>
 * <li> MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC" </li>
 * <li> SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC" </li>
 * </ul>
 * <p>
 * This class requires that mseed2sac (or rdseed in case of full seed files) exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/mseed2sac/>mseed2sac</a>
 * can be found at IRIS.
 *
 */
public class EventDataPreparer {

    private static final String DATASELECT_URL = "http://service.iris.edu/fdsnws/dataselect/1/query?";
    private String date = Utilities.getTemporaryString();

    /**
     * The event folder to download in
     */
    private final EventFolder eventDir;
    private Path sacSetPath;
    private Path stationSetPath;
    private Path respSetPath;

    private final GlobalCMTAccess eventData;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String mseedFileName;
    private boolean isFullSeed = false;

    /**
     * The event folder and its GCMT ID is set.
     * @param eventFolder (EventFolder) The event folder.
     * Input files must be in this folder if there are any. Output files will also be placed in this folder.
     */
    public EventDataPreparer (EventFolder eventFolder) {

        eventDir = eventFolder;
        sacSetPath = eventDir.toPath().resolve("sac");
        stationSetPath = eventDir.toPath().resolve("station");
        respSetPath = eventDir.toPath().resolve("resp");
        eventData = eventFolder.getGlobalCMTID().getEvent();

    }

    /**
     * Sets parameters. This method shall be used when the mseed (or full seed) file already exists.
     * @param mseedFile (String) Name of existing mseed (or full seed) file
     * @param full (boolean) Whether the input file is actually a full seed file
     */
    public void setParameters (String mseedFile, boolean full) {
        startTime = eventData.getCMTTime();
        endTime = eventData.getCMTTime();

        mseedFileName = mseedFile;
        isFullSeed = full;
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
        LocalDateTime cmtTime = eventData.getCMTTime();
        startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        String urlString = DATASELECT_URL + "net=" + networks + "&sta=*&loc=*&cha=" + channels +
                "&starttime=" + toLine(startTime) + "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);
        long size = 0L;

        mseedFileName = eventData + "." + date + ".mseed";
        Path mseedPath = eventDir.toPath().resolve(mseedFileName);
        size = Files.copy(url.openStream(), mseedPath, StandardCopyOption.REPLACE_EXISTING);
        System.err.println("Downloaded : " + eventData + " - " + size + " bytes");
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
     * In case the input is a full seed file, rdseed is run instead: "rdseed -fd [seedfile]" (this does not create RESP files).
     * @return (boolean) true if mseed2sac (or rdseed) succeeds
     * @throws IOException
     */
    public boolean openSeed() throws IOException {
        String command;
        if (!isFullSeed) {
            //System.err.println("mseed2sac " + MSEED_FILENAME);
            command = "mseed2sac " + mseedFileName;
        } else {
            //System.err.println("rdseed -fd " + MSEED_FILENAME);
            command = "rdseed -fd " + mseedFileName;
        }

        ExternalProcess xProcess = ExternalProcess.launch(command, eventDir.toPath());
        return xProcess.waitFor() == 0;
    }

    /**
     * Renames all SAC files in the event directory from a given style to a formatted one,
     * and moves them to "sac".
     * @param inputStyle (String) Style of input SAC file names.
     * @throws IOException
     */
    public void formatSacFileNames(String inputStyle) throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(eventDir.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
                int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
                int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
                int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
                int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
                int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
                int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), inputStyle);
                Files.move(sacPath, sacSetPath.resolve(sacFile.getFormattedFileName(i1, i2, i3, i4, i5, i6)));
            }
        }
    }

    /**
     * Downloads Station files and Resp files for the event, given a set of SAC files.
     * The downloads may be skipped if the SAC file name is not formatted.
     * @throws IOException
     */
    public void downloadMetadata() throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(sacSetPath, "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "formatted");
                String network = sacFile.getNetwork();
                String station = sacFile.getStation();
                String location = sacFile.getLocation();
                String channel = sacFile.getChannel();

                RespDataFile respData = new RespDataFile(network, station, location, channel);
                respData.setRequest(startTime);
                respData.downloadRespData(respSetPath);

                StationInformationFile stationInfo = new StationInformationFile(network, station, location, channel, stationSetPath);
                stationInfo.setRequest(startTime, endTime);
                stationInfo.downloadStationInformation();
                stationInfo.readStationInformation();

                FullPosition sourcePosition = eventData.getCmtLocation();

                // read SAC file
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
                double[] sacdata = SACUtil.readSACData(sacPath);

                // set observer info
                // Files with empty locations may have khole "-12345", so it is set to "".
                if (location.isEmpty()) {
                    headerMap.put(SACHeaderEnum.KHOLE, "");
                }
                headerMap.put(SACHeaderEnum.STLA, stationInfo.getLatitude());
                headerMap.put(SACHeaderEnum.STLO, stationInfo.getLongitude());
                // CAUTION: up is dip=-90 but CMPINC=0, horizontal is dip=0 but CMPINC=90
                double inclination = Double.parseDouble(stationInfo.getDip()) + 90.0;
                headerMap.put(SACHeaderEnum.CMPINC, Double.toString(inclination));
                headerMap.put(SACHeaderEnum.CMPAZ, stationInfo.getAzimuth());

                // set event info
                headerMap.put(SACHeaderEnum.KEVNM, eventData.toString());
                headerMap.put(SACHeaderEnum.EVLA, Double.toString(sourcePosition.getLatitude()));
                headerMap.put(SACHeaderEnum.EVLO, Double.toString(sourcePosition.getLongitude()));
                headerMap.put(SACHeaderEnum.EVDP, Double.toString(6371 - sourcePosition.getR()));

                // set SAC settings
                // overwrite permission
                headerMap.put(SACHeaderEnum.LOVROK, Boolean.toString(true));
                // calculate DIST, GCARC, AZ, and BAZ automatically
                headerMap.put(SACHeaderEnum.LCALDA, Boolean.toString(true));

                // overwrite SAC file
                SACUtil.writeSAC(sacPath, headerMap, sacdata);
            }
        }
    }

    /**
     * A method to expand existing mseed files and download associated STATION and RESP files.
     * The input mseed files must be in event directories under the current directory.
     * Output files will be placed in each input event directory.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

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
            // The procedures up to format must be done here so that seed-style and mseed-style SACs do not get mixed up.
            try (DirectoryStream<Path> seedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.seed")) {
                for (Path seedPath : seedPaths) {
                    System.err.println("operating for " + seedPath + " ...");
                    //set (or reset) seed file name
                    edp.setParameters(seedPath.getFileName().toString(), true);
                    // expand mseed file
                    edp.openSeed();
                    // format seed-style SAC file names
                    edp.formatSacFileNames("seed");
                }
            }

            // for each mseed file (though there is probably only one)
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    System.err.println("operating for " + mseedPath + " ...");
                    // set (or reset) mseed file name
                    edp.setParameters(mseedPath.getFileName().toString(), false);
                    // expand mseed file
                    edp.openSeed();
                    // format mseed-style SAC file names
                    edp.formatSacFileNames("mseed");
                }
            }

            // download metadata for all the expanded SAC files in the event directory
            edp.downloadMetadata();

        }
        System.err.println("Finished!");

    }
}