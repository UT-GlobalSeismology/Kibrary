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
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.FullPosition;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for downloading mseed file, opening it, and downloading necessary metadata for a given event.
 * Information about the station and event is written into the header.
 * Operations on mseed files (or full seed files) that are already downloaded can also be done by using the main method.
 * <p>
 * Note that the convention of resulting SAC file names will be formatted as:
 * <ul>
 * <li> MSEED style: "IU.MAJO.00.BH2.M.2014.202.14.44.00.000.SAC" </li>
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
    /**
     * [s] delta for SAC files. SAC files with different delta will be interpolated
     * or downsampled.
     */
    private static final double  DELTA = 0.05;

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
     * Sets time for request of metadata files, using event time.
     * This method shall be used when the mseed (or full seed) file already exists.
     */
    public void setRequestTime () {
        startTime = eventData.getCMTTime();
        endTime = eventData.getCMTTime();
    }

    /**
     * Downloads Mseed file from FDSNWS using specified parameters.
     * @param networks (String) Network names for request, listed using commas. Wildcards (*, ?) allowed. Virtual networks are unsupported.
     * @param channels (String) Channels to be requested, listed using commas. Wildcards (*, ?) allowed.
     * @param headAdjustment (int) [min] The starting time of request with respect to event time.
     * @param footAdjustment (int) [min] The ending time of request with respect to event time.
     * @param mseedFileName (String) Name of output mseed file
     * @throws IOException
     */
    public void downloadMseed(String networks, String channels, int headAdjustment, int footAdjustment, String mseedFileName)
            throws IOException {

        LocalDateTime cmtTime = eventData.getCMTTime();
        startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        String urlString = DATASELECT_URL + "net=" + networks + "&sta=*&loc=*&cha=" + channels +
                "&starttime=" + toLine(startTime) + "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);
        long size = 0L;

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
     * Runs mseed2sac to extract SAC files from mseed: "mseed2sac [mseedfile]".
     * @param mseedFileName (String) Name of mseedFile
     * @return (boolean) true if mseed2sac succeeds
     * @throws IOException
     */
    public boolean openMseed(String mseedFileName) throws IOException {
        String command = "mseed2sac " + mseedFileName;
        ExternalProcess xProcess = ExternalProcess.launch(command, eventDir.toPath());
        return xProcess.waitFor() == 0;
    }
    /**
     * Runs rdseed to extract SAC files and RESP files from full seed: "rdseed -fRd [seedfile]"
     * @param seedFileName (String) Name of seedFile
     * @return (boolean) true if rdseed succeeds
     * @throws IOException
     */
    public boolean openSeed(String seedFileName) throws IOException {
        String command = "rdseed -fRd " + seedFileName;
        ExternalProcess xProcess = ExternalProcess.launch(command, eventDir.toPath());
        return xProcess.waitFor() == 0;
    }

    /**
     * Downloads Station files and Resp files for the event, each in "station" and "resp", given a set of SAC files.
     * Station and event information will be written into the SAC header. Then, the SAC file will be interpolated.
     * The downloads may be skipped if the SAC file name is not in mseed-style.
     * @throws IOException
     */
    public void downloadMetadataMseed() throws IOException {
        Files.createDirectories(stationSetPath);
        Files.createDirectories(respSetPath);

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(eventDir.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "mseed");
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
                FullPosition sourcePosition = eventData.getCmtLocation();
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

                // interpolate delta, and also stimulate automatic calculations of DIST, GCARC, AZ, and BAZ
                fixDelta(sacPath);
            }
        }
    }

    /**
     * Treats SAC and RESP files produced by rdseed.
     * RESP files are moved to "resp".
     * SAC files will have event information written into the header, and will then be interpolated.
     * The procedures may be skipped if the SAC file name is not in seed-style.
     * @throws IOException
     */
    public void organizeFilesSeed() throws IOException {
        Files.createDirectories(respSetPath);

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(eventDir.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "seed");
                String network = sacFile.getNetwork();
                String station = sacFile.getStation();
                String location = sacFile.getLocation();
                String channel = sacFile.getChannel();

                // move resp file
                RespDataFile respData = new RespDataFile(network, station, location, channel);
                Files.move(eventDir.toPath().resolve(respData.getRespFile()), respSetPath.resolve(respData.getRespFile()));

                // read SAC file
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
                double[] sacdata = SACUtil.readSACData(sacPath);

                // set observer info
                // Files with empty locations may have khole "-12345", so it is set to "".
                if (location.isEmpty()) {
                    headerMap.put(SACHeaderEnum.KHOLE, "");
                }
                // other observer info should be filled

                // set event info
                FullPosition sourcePosition = eventData.getCmtLocation();
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

                // interpolate delta, and also stimulate automatic calculations of DIST, GCARC, AZ, and BAZ
                fixDelta(sacPath);
            }
        }
    }

    /**
     * Interpolates SAC file with DELTA (which is currently 0.05 sec thus 20 Hz).
     * Automatic calculations of DIST, GCARC, AZ, and BAZ will be stimulated here.
     * @param sacPath (Path) Path of SAC file to be treated.
     * @throws IOException
     */
    private void fixDelta(Path sacPath) throws IOException {

        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();

            // set current directory
            sacD.inputCMD("cd " + cwd);
            // read
            sacD.inputCMD("r " + sacPath.getFileName());

            sacD.inputCMD("interpolate delta " + DELTA);
            sacD.inputCMD("w over");
        }
    }

    /**
     * Renames all SAC files in the event directory from a given style to a formatted one,
     * and moves them into "sac".
     * @param inputStyle (String) Style of input SAC file names.
     * @throws IOException
     */
    public void formatSacFileNames(String inputStyle) throws IOException {
        Files.createDirectories(sacSetPath);

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

            // for each mseed file (though there is probably only one)
            // Mseed must be treated before seed so that SAC files from seed will not be picked up in downloadMetadata().
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    System.err.println("operating for " + mseedPath + " ...");
                    // expand mseed file
                    edp.openMseed(mseedPath.getFileName().toString());
                    // set time of request for metadata download
                    edp.setRequestTime();
                    // download metadata for all the expanded SAC files in the event directory
                    edp.downloadMetadataMseed();
                    // format mseed-style SAC file names
                    edp.formatSacFileNames("mseed");
                }
            }

            // for each (full) seed file (in case some exist)
            try (DirectoryStream<Path> seedPaths = Files.newDirectoryStream(eventDir.toPath(), "*.seed")) {
                for (Path seedPath : seedPaths) {
                    System.err.println("operating for " + seedPath + " ...");
                    // expand mseed file
                    edp.openSeed(seedPath.getFileName().toString());
                    //set (or reset) seed file name
                    edp.organizeFilesSeed();
                    // format seed-style SAC file names
                    edp.formatSacFileNames("seed");
                }
            }


        }
        System.err.println("Finished!");

    }
}