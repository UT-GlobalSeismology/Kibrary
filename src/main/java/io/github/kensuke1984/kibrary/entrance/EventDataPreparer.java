package io.github.kensuke1984.kibrary.entrance;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import io.github.kensuke1984.kibrary.external.ExternalProcess;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for downloading mseed file, opening it, and downloading necessary metadata for a given event.
 * Information about the station and event is written into the header.
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
 * <p>
 * Additionally, xml2resp (which is included in the evalresp package) may also be needed in your PATH.
 * <p>
 * (memo: this class does not hold "datacenter" beacuse it is not needed for seed files that already exist.)
 *
 * @author otsuru
 * @since 2021/09/14
 */
class EventDataPreparer {

    private static final String DATASELECT_URL_IRIS = "http://service.iris.edu/fdsnws/dataselect/1/query?";
    private static final String DATASELECT_URL_ORFEUS = "http://www.orfeus-eu.org/fdsnws/dataselect/1/query?";
    /**
     * [s] delta for SAC files. SAC files with different delta will be interpolated
     * or downsampled.
     */
    private static final double  DELTA = 0.05;

    /**
     * The event folder to download in
     */
    private final EventFolder eventDir;
    private final Path mseedSetPath;
    private final Path seedSetPath;
    private final Path sacSetPath;
    private final Path stationSetPath;
    private final Path respSetPath;

    private final GlobalCMTAccess eventData;

    /**
     * The event folder and its GCMT ID is set.
     * @param eventFolder (EventFolder) The event folder.
     * Input files must be in this folder if there are any. Output files will also be placed in this folder.
     */
    EventDataPreparer (EventFolder eventFolder) {
        eventDir = eventFolder;
        mseedSetPath = eventDir.toPath().resolve("mseed");
        seedSetPath = eventDir.toPath().resolve("seed");
        sacSetPath = eventDir.toPath().resolve("sac");
        stationSetPath = eventDir.toPath().resolve("station");
        respSetPath = eventDir.toPath().resolve("resp");
        eventData = eventFolder.getGlobalCMTID().getEventData();
    }

    /**
     * Downloads Mseed file from FDSNWS using specified parameters.
     * @param datacenter (String) The name of the datacenter to download from.
     * @param networks (String) Network names for request, listed using commas. Wildcards (*, ?) allowed. Virtual networks are unsupported.
     * @param channels (String) Channels to be requested, listed using commas. Wildcards (*, ?) allowed.
     * @param headAdjustment (int) [min] The starting time of request with respect to event time.
     * @param footAdjustment (int) [min] The ending time of request with respect to event time.
     * @param mseedFileName (String) Name of output mseed file
     * @return (boolean) whether an mseed file was downloaded
     * @throws IOException
     */
    boolean downloadMseed(String datacenter, String networks, String channels, int headAdjustment, int footAdjustment, String mseedFileName)
            throws IOException {

        LocalDateTime cmtTime = eventData.getCMTTime();
        LocalDateTime startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        LocalDateTime endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        String urlString;
        switch (datacenter) {
        case "IRIS":
            urlString = DATASELECT_URL_IRIS;
            break;
        case "ORFEUS":
            urlString = DATASELECT_URL_ORFEUS;
            break;
        default:
            throw new IllegalArgumentException("Invalid datacenter name.");
        }
        urlString = urlString + "net=" + networks + "&sta=*&loc=*&cha=" + channels +
                "&starttime=" + toLine(startTime) + "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(urlString);

        Files.createDirectories(mseedSetPath);
        Path mseedPath = mseedSetPath.resolve(mseedFileName);

        System.err.print(" ~ Downloading mseed file ...");
        try (InputStream inputStream = url.openStream()) {
            double sizeMiB = (double) Files.copy(inputStream, mseedPath, StandardCopyOption.REPLACE_EXISTING) / 1024 / 1024;
            System.err.println("\r ~ Downloaded : " + eventData + " - " + MathAid.roundToString(sizeMiB, 3) + " MiB  "
                    + DateTimeFormatter.ofPattern("<yyyy/MM/dd HH:mm:ss>").format(LocalDateTime.now()));
        } catch (FileNotFoundException e) {
            // if there is no available data for this request, return false
            return false;
        }
        return true;
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
     * Opens all mseed files under "mseed" using mseed2sac.
     * @return (boolean) true if success; false if mseed2sac failed or if no mseed files are found.
     * @throws IOException
     */
    boolean openMseeds() throws IOException {
        boolean flag = false;

        if (Files.exists(mseedSetPath)) {

            // SAC files that are left here may have been broken during configuration, so delete them
            try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(mseedSetPath, "*.SAC")) {
                for (Path sacPath : sacPaths) {
                    Files.delete(sacPath);
                }
            }

            // open all mseeds, though there is probably only one
            try (DirectoryStream<Path> mseedPaths = Files.newDirectoryStream(mseedSetPath, "*.mseed")) {
                for (Path mseedPath : mseedPaths) {
                    flag = true;
                    System.err.println(" ~ Opening " + mseedPath + " ...");
                    // expand mseed file
                    if (!mseed2sac(mseedPath.getFileName().toString())) {
                        System.err.println("!!! mseed2sac for "+ mseedPath + " failed.");
                        return false;
                    }
                }
            }
        }

        if (flag) {
            return true;
        } else {
            System.err.println("!!! No mseed files found.");
            return false;
        }
    }

    /**
     * Runs mseed2sac to extract SAC files from mseed: "mseed2sac [mseedfile]".
     * The mseed file must be placed under "eventDir/mseed".
     * @param mseedFileName (String) Name of mseedFile
     * @return (boolean) true if mseed2sac succeeds
     * @throws IOException
     */
    private boolean mseed2sac(String mseedFileName) throws IOException {
        String command = "mseed2sac " + mseedFileName;
        ExternalProcess xProcess = ExternalProcess.launch(command, mseedSetPath);
        return xProcess.waitFor() == 0;
    }

    /**
     * Opens all seed files under "seed" using rdseed.
     * @return (boolean) true if success; false if rdseed failed or if no seed files are found.
     * @throws IOException
     */
    boolean openSeeds() throws IOException {
        boolean flag = false;

        if (Files.exists(seedSetPath)) {

            // SAC files that are left here may have been broken during configuration, so delete them
            try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(seedSetPath, "*.SAC")) {
                for (Path sacPath : sacPaths) {
                    Files.delete(sacPath);
                }
            }

            // open all seeds, though there is probably only one
            try (DirectoryStream<Path> seedPaths = Files.newDirectoryStream(seedSetPath, "*.seed")) {
                for (Path seedPath : seedPaths) {
                    flag = true;
                    System.err.println(" ~ Opening " + seedPath + " ...");
                    // expand seed file
                    if (!rdseed(seedPath.getFileName().toString())) {
                        System.err.println("!!! rdseed for "+ seedPath + " failed.");
                        return false;
                    }
                }
            }
        }

        if (flag) {
            return true;
        } else {
            System.err.println("!!! No seed files found.");
            return false;
        }
    }

    /**
     * Runs rdseed to extract SAC files and RESP files from full seed: "rdseed -fRd [seedfile]"
     * The seed file must be placed under "eventDir/seed".
     * @param seedFileName (String) Name of seedFile
     * @return (boolean) true if rdseed succeeds
     * @throws IOException
     */
    private boolean rdseed(String seedFileName) throws IOException {
        String command = "rdseed -fRd " + seedFileName;
        ExternalProcess xProcess = ExternalProcess.launch(command, seedSetPath);
        return xProcess.waitFor() == 0;
    }

    /**
     * Runs xml2resp to create RESP file from xml file: "xml2resp -o [outputfile] [inputfile]"
     * @param xmlFile (StationXmlFile) Name of input XML file
     * @param respFile (RespDataFile) Name of output RESP file
     * @return (boolean) true if rdseed succeeds
     * @throws IOException
     */
    private boolean xml2resp(StationXmlFile xmlFile, RespDataFile respFile) throws IOException {
        String command = "xml2resp -o " + respSetPath.getFileName().resolve(respFile.getRespName())
                + " " + stationSetPath.getFileName().resolve(xmlFile.getXmlFile());
        //System.err.println(command);
        ExternalProcess xProcess = ExternalProcess.launch(command, eventDir.toPath());
        return xProcess.waitFor() == 0;
    }

    /**
     * Downloads Station files and Resp files for the event, each in "station" and "resp", given a set of SAC files.
     * Station and event information will be written into the SAC header. Then, the SAC file will be interpolated.
     * The downloads may be skipped if the SAC file name is not in mseed-style.
     * @throws IOException
     */
/*    public void downloadMetadataMseed() throws IOException {
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
                headerMap.put(SACHeaderEnum.CMPINC, String.valueOf(inclination));
                headerMap.put(SACHeaderEnum.CMPAZ, stationInfo.getAzimuth());

                // set event info
                FullPosition sourcePosition = eventData.getCmtLocation();
                headerMap.put(SACHeaderEnum.KEVNM, eventData.toString());
                headerMap.put(SACHeaderEnum.EVLA, String.valueOf(sourcePosition.getLatitude()));
                headerMap.put(SACHeaderEnum.EVLO, String.valueOf(sourcePosition.getLongitude()));
                headerMap.put(SACHeaderEnum.EVDP, String.valueOf(6371 - sourcePosition.getR()));

                // set SAC settings
                // overwrite permission
                headerMap.put(SACHeaderEnum.LOVROK, String.valueOf(true));
                // calculate DIST, GCARC, AZ, and BAZ automatically   TODO: calculate manually
                headerMap.put(SACHeaderEnum.LCALDA, String.valueOf(true));

                // overwrite SAC file
                SACUtil.writeSAC(sacPath, headerMap, sacdata);

                // interpolate delta, and also stimulate automatic calculations of DIST, GCARC, AZ, and BAZ
                fixDelta(sacPath);
            }
        }
    }
*/
    /**
     * Downloads StationXML files for the event into "eventDir/station/", given a set of SAC files.
     * The downloads might be skipped if the SAC file name is not in mseed-style.
     * @param datacenter (String) The name of the datacenter to download from.
     * @param redo (boolean) Whether to download existing stationXml files again.
     * @throws IOException
     */
    void downloadXmlMseed(String datacenter, boolean redo) throws IOException {
        if (!Files.exists(mseedSetPath)) {
            return;
        }

        Files.createDirectories(stationSetPath);
        System.err.println(" ~ Downloading XML files ...");

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(mseedSetPath, "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "mseed");
                String network = sacFile.getNetwork();
                String station = sacFile.getStation();
                String location = sacFile.getLocation();
                String channel = sacFile.getChannel();

                StationXmlFile stationInfo = new StationXmlFile(network, station, location, channel, stationSetPath);
                if (!Files.exists(stationInfo.getXmlPath()) || redo) {
                    stationInfo.setRequest(datacenter, eventData.getCMTTime(), eventData.getCMTTime());
                    stationInfo.downloadStationXml();
                }
            }
        }
    }

    /**
     * Constructs SAC and RESP files for an event, given a set of SAC files derived from an mseed.
     * RESP files will be created by xml2resp.
     * Information of the station and event will be written into the SAC header.
     * Then, the SAC file will be interpolated.
     * The procedures may be skipped if the SAC file name is not in mseed-style.
     * @throws IOException
     */
    void configureFilesMseed() throws IOException {
        if (!Files.exists(mseedSetPath)) {
            return;
        }

        Files.createDirectories(respSetPath);
        Files.createDirectories(sacSetPath);

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(mseedSetPath, "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "mseed");
                String network = sacFile.getNetwork();
                String station = sacFile.getStation();
                String location = sacFile.getLocation();
                String channel = sacFile.getChannel();

                // read stationXML file
                StationXmlFile stationInfo = new StationXmlFile(network, station, location, channel, stationSetPath);
                if (!stationInfo.readStationXml()) {
                    // if the read fails, skip the SAC file
                    // exception log is written inside the method
                    continue;
                }

                // create resp file
                RespDataFile respFile = new RespDataFile(network, station, location, channel);
                if (!xml2resp(stationInfo, respFile)) {
                    // if RESP file fails to be created, skip the SAC file
                    System.err.println("!!! xml2resp for "+ sacPath + " failed.");
                    continue;
                }

                // read SAC file
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
                double[] sacdata = SACUtil.readSACData(sacPath);
                int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
                int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
                int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
                int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
                int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
                int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

                // set observer info
                // Files with empty locations may have khole "-12345", so it is set to "".
                if (location.isEmpty()) {
                    headerMap.put(SACHeaderEnum.KHOLE, "");
                }
                headerMap.put(SACHeaderEnum.STLA, stationInfo.getLatitude());
                headerMap.put(SACHeaderEnum.STLO, stationInfo.getLongitude());
                // CAUTION: up is dip=-90 but CMPINC=0, horizontal is dip=0 but CMPINC=90
                double inclination = Double.parseDouble(stationInfo.getDip()) + 90.0;
                headerMap.put(SACHeaderEnum.CMPINC, String.valueOf(inclination));
                headerMap.put(SACHeaderEnum.CMPAZ, stationInfo.getAzimuth());

                // set event info
                FullPosition sourcePosition = eventData.getCmtPosition();
                headerMap.put(SACHeaderEnum.KEVNM, eventData.toString());
                headerMap.put(SACHeaderEnum.EVLA, String.valueOf(sourcePosition.getLatitude()));
                headerMap.put(SACHeaderEnum.EVLO, String.valueOf(sourcePosition.getLongitude()));
                headerMap.put(SACHeaderEnum.EVDP, String.valueOf(6371 - sourcePosition.getR()));

                // set SAC settings
                // overwrite permission
                headerMap.put(SACHeaderEnum.LOVROK, String.valueOf(true));
                // calculate DIST, GCARC, AZ, and BAZ automatically   TODO: calculate manually
                headerMap.put(SACHeaderEnum.LCALDA, String.valueOf(true));

                // overwrite SAC file
                SACUtil.writeSAC(sacPath, headerMap, sacdata);

                // interpolate delta, and also stimulate automatic calculations of DIST, GCARC, AZ, and BAZ
                fixDelta(sacPath);

                // move completed SAC file to "sac"
                Files.move(sacPath, sacSetPath.resolve(sacFile.getFormattedFileName(i1, i2, i3, i4, i5, i6)),
                        StandardCopyOption.REPLACE_EXISTING);
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
    void organizeFilesSeed() throws IOException {
        if (!Files.exists(seedSetPath)) {
            return;
        }

        Files.createDirectories(respSetPath);
        Files.createDirectories(sacSetPath);

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(seedSetPath, "*.SAC")) {
            for (Path sacPath : sacPaths) {

                // set information based on SAC File name
                SacFileName sacFile = new SacFileName(sacPath.getFileName().toString(), "seed");
                String network = sacFile.getNetwork();
                String station = sacFile.getStation();
                String location = sacFile.getLocation();
                String channel = sacFile.getChannel();

                // move resp file
                String respFileName = new RespDataFile(network, station, location, channel).getRespName();
                // There are cases where 2 SAC files exist for 1 channel, in which case there is only 1 resp file.
                //   The resp file will be moved when treating the 1st SAC file, so nothing has to be moved for the 2nd SAC file.
                if (Files.exists(seedSetPath.resolve(respFileName))) {
                    Files.move(seedSetPath.resolve(respFileName), respSetPath.resolve(respFileName),
                            StandardCopyOption.REPLACE_EXISTING);
                }

                // read SAC file
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
                double[] sacdata = SACUtil.readSACData(sacPath);
                int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
                int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
                int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
                int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
                int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
                int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

                // set observer info
                // Files with empty locations may have khole "-12345", so it is set to "".
                if (location.isEmpty()) {
                    headerMap.put(SACHeaderEnum.KHOLE, "");
                }
                // other observer info should be filled

                // set event info
                FullPosition sourcePosition = eventData.getCmtPosition();
                headerMap.put(SACHeaderEnum.KEVNM, eventData.toString());
                headerMap.put(SACHeaderEnum.EVLA, String.valueOf(sourcePosition.getLatitude()));
                headerMap.put(SACHeaderEnum.EVLO, String.valueOf(sourcePosition.getLongitude()));
                headerMap.put(SACHeaderEnum.EVDP, String.valueOf(6371 - sourcePosition.getR()));

                // set SAC settings
                // overwrite permission
                headerMap.put(SACHeaderEnum.LOVROK, String.valueOf(true));
                // calculate DIST, GCARC, AZ, and BAZ automatically   TODO: calculate manually
                headerMap.put(SACHeaderEnum.LCALDA, String.valueOf(true));

                // overwrite SAC file
                SACUtil.writeSAC(sacPath, headerMap, sacdata);

                // interpolate delta, and also stimulate automatic calculations of DIST, GCARC, AZ, and BAZ
                fixDelta(sacPath);

                // move completed SAC file to "sac"
                Files.move(sacPath, sacSetPath.resolve(sacFile.getFormattedFileName(i1, i2, i3, i4, i5, i6)),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** TODO: this need not be here if we can calculate manually.
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

}