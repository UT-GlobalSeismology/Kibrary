package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTData;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for extracting a mseed file. It creates SAC files from the mseed file.
 * <p>
 * This class requires that mseed2sac, evalresp and sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/mseed2sac/>mseed2sac</a>,
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/>evalresp</a> and
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/sac/>SAC</a> can be found in IRIS.
 *
 * @author Kenji Kawai
 * @version 0.1.1
 */
class XMseedSAC implements Runnable {

    /**
     * [s] delta for SAC files. SAC files with different delta will be interpolated
     * or downsampled.
     */
    private static final double delta = 0.05;
    /**
     * [Hz] Sampling Hz in write SAC files
     */
    private static final double samplingHz = 20;
    /**
     * if remove intermediate files
     */
    private boolean removeIntermediateFiles = true;
    /**
     * If cmpMod is true, this modifies delta, cmpinc &amp; cmpaz
     */
    private final boolean CMPMOD = true;
    /**
     * GlobalCMTData for the event in the seedfile
     */
    private GlobalCMTData event;
    /**
     * where output goes.
     */
    private final EventFolder EVENT_DIR;
    /**
     * seed file to process
     */
    private final MSEEDFile MSEED_FILE;
    /**
     * event ID
     */
    private GlobalCMTID id;
    /**
     * [deg] Minimum epicentral distance of SAC files to be output
     */
    private final double MINIMUM_EPICENTRAL_DISTANCE = 0;
    /**
     * [deg] Maximum epicentral distance of SAC files to be output
     */
    private final double MAXIMUM_EPICENTRAL_DISTANCE = 180;

    private final boolean EVENT_DIR_ALREADY_EXIST;
    /**
     * true: the base time will be PDE time, false: CMT (default)
     */
    private boolean byPDE;
    private boolean hadRun;
    /**
     * true: exception has occurred, false: not
     */
    private boolean problem;

    /**
     * @param mseedPath            to be extracted from
     * @param outputDirectoryPath Path where extracted files are placed
     * @throws IOException if the outputDirectoryPath already has events which also
     *                     exists in the seed file or an error occurs
     */
    XMseedSAC(Path mseedPath, Path outputDirectoryPath) throws IOException {
        this(mseedPath, outputDirectoryPath, null);
    }

    /**
     * mseed file to extract
     *
     * @param mseedPath           MSEED file to extract
     * @param outputDirectoryPath inside this folder, the seed file is extracted. If the folder
     *                            does not exist, it will be created.
     * @param id                  global cmt id
     * @throws IOException If the folder already has event folders which also exists in
     *                     the seed file.
     */
    XMseedSAC(Path mseedPath, Path outputDirectoryPath, GlobalCMTID id) throws IOException {

        MSEED_FILE = new MSEEDFile(mseedPath);
        if (id != null && this.id != null) this.id = id; // slighly changed (kenji)
        else setID();

        if (!idValidity()) throw new RuntimeException("The ID " + this.id + " is invalid for " + mseedPath);

        Files.createDirectories(outputDirectoryPath);
        EVENT_DIR = new EventFolder(outputDirectoryPath.resolve(this.id.toString()));

        if (EVENT_DIR.exists()) EVENT_DIR_ALREADY_EXIST = false;
        else if (!EVENT_DIR.mkdirs()) throw new RuntimeException("Can't create " + EVENT_DIR);
        else EVENT_DIR_ALREADY_EXIST = true;
        MSEED_FILE.createLink(EVENT_DIR.toPath());
    }

    /**
     * Set DELTA in a sac file to {@link #delta}. (mostly it is down sampling.)
     * Change cmpaz cmpinc BHN BHE BHZ のときはcmpaz cmpincを変更する
     *
     * @param sacPath Path of a file to fix
     * @throws IOException if an I/O error occurs
     */
    private static void fixDelta(Path sacPath) throws IOException {
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            if (sacPath.toString().contains(".BHN.") || sacPath.toString().contains(".BLN."))
                sacD.inputCMD("ch cmpaz 0 cmpinc 90");
            else if (sacPath.toString().contains(".BHE.") || sacPath.toString().contains(".BLE."))
                sacD.inputCMD("ch cmpaz 90 cmpinc 90");
            else if (sacPath.toString().contains(".BHZ.") || sacPath.toString().contains(".BLZ."))
                sacD.inputCMD("ch cmpinc 0");
            sacD.inputCMD("interpolate delta " + delta);
            sacD.inputCMD("w over");
        }
    }

    Path getSeedPath() {
        return MSEED_FILE.getSeedPath();
    }

    /**
     * If true then all intermediate files will be removed at the end.
     *
     * @param b set {@link #removeIntermediateFiles}
     */
    void setRemoveIntermediateFiles(boolean b) {
        removeIntermediateFiles = b;
    }

    /**
     * set GlobalCMTID
     */
    private void setID() {
        // try to find id in the name of the file
        id = findIDinFilename();
        if (id != null) return;

        if (GlobalCMTID.isGlobalCMTID(MSEED_FILE.getVolumeLabel())) {
            id = new GlobalCMTID(MSEED_FILE.getVolumeLabel());
          return;
        }

        System.err.println("Dataset in this seed file starts " + MSEED_FILE.getStartingDate());
        GlobalCMTSearch sc = new GlobalCMTSearch(MSEED_FILE.getStartingDate(), MSEED_FILE.getEndingDate());
        id = sc.select();
        Objects.requireNonNull(id, "There is no event in the global CMT catalog.");
    }

    /**
     * TODO use volumeID in seed files
     *
     * @return look for GlobalCMTID in the name of the seed file otherwise
     * returns null
     */
    private GlobalCMTID findIDinFilename() {
        String fileName = MSEED_FILE.getSeedPath().getFileName().toString();
        Matcher m1 = GlobalCMTID.RECENT_GLOBALCMTID_PATTERN.matcher(fileName);
        if (m1.find()) return new GlobalCMTID(m1.group());

        Matcher m0 = GlobalCMTID.PREVIOUS_GLOBALCMTID_PATTERN.matcher(fileName);
        return m0.find() ? new GlobalCMTID(m0.group()) : null;
    }

    /**
     * @return global cmt id が日付的に合っているかどうか （startが発震時刻より前かつendがCMT時刻より後かどうか）
     */
    private boolean idValidity() {

    	event = id.getEvent();
//        return event != null && id != null && MSEED_FILE.getStartingDate().isBefore(event.getPDETime()) &&
 //               MSEED_FILE.getEndingDate().isAfter(event.getCMTTime()); // TODO this expression should be deleted (kenji)
        return event != null && id != null;

    }

    /**
     * Deconvolute instrument function for all the MOD files in the event folder.
     * 対応するRESPのevalrespに失敗したMODファイルはNOSPECTRAMODへ
     */
    private void deconvolute() {
        // System.out.println("Conducting deconvolution");
        Path noSpectraPath = EVENT_DIR.toPath().resolve("noSpectraOrInvalidMOD");
        Path duplicateChannelPath = EVENT_DIR.toPath().resolve("duplicateChannel");
        // evalresp後のRESP.*ファイルを移動する TODO メソッドを分ける
        Path respBoxPath = EVENT_DIR.toPath().resolve("resp");
        Path spectraBoxPath = EVENT_DIR.toPath().resolve("spectra");
        Path modBoxPath = EVENT_DIR.toPath().resolve("mod");
        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.MOD")) {
            String resp = "RESP.";
            String spectra = "SPECTRA.";
            for (Path modPath : eventDirStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modPath);
                String componentName = headerMap.get(SACHeaderEnum.KCMPNM);
            	String khole = headerMap.get(SACHeaderEnum.KHOLE);
 //           	if(khole.matches("-12345")) {khole = "--";} // this is for MSEEDSAC
                String respFileName =
                        resp + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                khole + "." + componentName;
                String spectraFileName =
                        spectra + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                khole + "." + componentName;
                Path spectraPath = EVENT_DIR.toPath().resolve(spectraFileName);
                Path respPath = EVENT_DIR.toPath().resolve(respFileName);
                String component;
                switch (componentName) {
                    case "BHE":
                    case "BLE":
                    case "HHE":
                    case "HLE":
                        component = "E";
                        break;
                    case "BHN":
                    case "BLN":
                    case "HHN":
                    case "HLN":
                        component = "N";
                        break;
                    case "BHZ":
                    case "BLZ":
                    case "HHZ":
                    case "HLZ":
                        component = "Z";
                        break;
                    case "BH1":
                    case "BL1":
                    case "HH1":
                    case "HL1":
                        component = "1";
                        break;
                    case "BH2":
                    case "BL2":
                    case "HH2":
                    case "HL2":
                        component = "2";
                        break;
                    default:
                        continue;
                }

                String afterName = headerMap.get(SACHeaderEnum.KSTNM) + "." + event + "." + component;
                Path afterPath = EVENT_DIR.toPath().resolve(afterName);
//                System.out.println("deconvolute: "+ afterPath); // 4debug

                // run evalresp
                // If it fails, throw MOD and RESP files to trash
                if (!runEvalresp(headerMap)) {
                    // throw MOD.* files which cannot produce SPECTRA to noSpectra
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
                    // throw RESP.* files which cannot produce SPECTRA to noSpectra
                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // run seedsac
                try {
                    int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
                    // duplication of channel
                    if (Files.exists(afterPath)) {
                        // throw *.MOD files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(modPath, duplicateChannelPath, true);
                        // throw SPECTRA files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(spectraPath, duplicateChannelPath, true);
                        // throw RESP files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(respPath, duplicateChannelPath, true);
                        continue;
                    }
                    SACDeconvolution.compute(modPath, spectraPath, afterPath, samplingHz / npts, samplingHz);
                } catch (Exception e) {
                    // throw *.MOD files which cannot produce SPECTRA to noSpectraPath
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
                    // throw SPECTRA files which cannot produce SPECTRA to noSpectraPath
                    // In case that outdated RESP file cannot produce any SPECTRA file
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                    	Utilities.moveToDirectory(spectraPath, noSpectraPath, true);
                    }
                    // throw RESP files which cannot produce SPECTRA to noSpectraPath
                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // move processed RESP files to respBox
                Utilities.moveToDirectory(respPath, respBoxPath, true);

                // move processed SPECTRA files to spectraBox
                Utilities.moveToDirectory(spectraPath, spectraBoxPath, true);

                // move processed MOD files to modBox
                Utilities.moveToDirectory(modPath, modBoxPath, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Deconvolute instrument function for all the MOD files in the event folder.
     * 対応するRESPのevalrespに失敗したMODファイルはNOSPECTRAMODへ
     * mseed2sacで解凍したSACのlocation(KHOLE)ヘッダーに"-12345"が入る場合に対応 (2021-08-23)
     */
    private void deconvoluteSACMSEED() { //TODO STATIONINFORMATIONも削除しないといけない。(kenji)
        // System.out.println("Conducting deconvolution");
        Path noSpectraPath = EVENT_DIR.toPath().resolve("noSpectraOrInvalidMOD");
        Path duplicateChannelPath = EVENT_DIR.toPath().resolve("duplicateChannel");
        // evalresp後のRESP.*ファイルを移動する TODO メソッドを分ける
        Path respBoxPath = EVENT_DIR.toPath().resolve("resp");
        Path spectraBoxPath = EVENT_DIR.toPath().resolve("spectra");
        Path modBoxPath = EVENT_DIR.toPath().resolve("mod");
        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.MOD")) {
            String resp = "RESP.";
            String spectra = "SPECTRA.";
            for (Path modPath : eventDirStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modPath);
                String componentName = headerMap.get(SACHeaderEnum.KCMPNM);
            	String khole = headerMap.get(SACHeaderEnum.KHOLE);
            	if(khole.matches("-12345")) {khole = "--";} // this is for MSEEDSAC
                String respFileName =
                        resp + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                khole + "." + componentName;
               if(khole.matches("--")) {khole = "";} // this is for MSEEDSAC
               String spectraFileName =
                        spectra + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                khole + "." + componentName;
                Path spectraPath = EVENT_DIR.toPath().resolve(spectraFileName);
                Path respPath = EVENT_DIR.toPath().resolve(respFileName);
                String component;
                switch (componentName) {
                    case "BHE":
                    case "BLE":
                    case "HHE":
                    case "HLE":
                        component = "E";
                        break;
                    case "BHN":
                    case "BLN":
                    case "HHN":
                    case "HLN":
                        component = "N";
                        break;
                    case "BHZ":
                    case "BLZ":
                    case "HHZ":
                    case "HLZ":
                        component = "Z";
                        break;
                    case "BH1":
                    case "BL1":
                    case "HH1":
                    case "HL1":
                        component = "1";
                        break;
                    case "BH2":
                    case "BL2":
                    case "HH2":
                    case "HL2":
                        component = "2";
                        break;
                    default:
                        continue;
                }

                String afterName = headerMap.get(SACHeaderEnum.KSTNM) + "_" + headerMap.get(SACHeaderEnum.KNETWK) +
                        "." + event + "." + component;
                Path afterPath = EVENT_DIR.toPath().resolve(afterName);
//                System.out.println("deconvolute: "+ afterPath); // 4debug

                // run evalresp
                // If it fails, throw MOD and RESP files to trash
                if (!runEvalresp(headerMap)) {
                    // throw MOD.* files which cannot produce SPECTRA to noSpectra
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
                    // throw RESP.* files which cannot produce SPECTRA to noSpectra
                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // run seedsac
                try {
                    int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
                    // duplication of channel
                    if (Files.exists(afterPath)) {
                        // throw *.MOD files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(modPath, duplicateChannelPath, true);
                        // throw SPECTRA files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(spectraPath, duplicateChannelPath, true);
                        // throw RESP files which cannot produce SPECTRA to duplicateChannelPath
                        Utilities.moveToDirectory(respPath, duplicateChannelPath, true);
                        continue;
                    }
                    SACDeconvolution.compute(modPath, spectraPath, afterPath, samplingHz / npts, samplingHz);
                } catch (Exception e) {
                    // throw *.MOD files which cannot produce SPECTRA to noSpectraPath
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
                    // throw SPECTRA files which cannot produce SPECTRA to noSpectraPath
                    // In case that outdated RESP file cannot produce any SPECTRA file
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                    	Utilities.moveToDirectory(spectraPath, noSpectraPath, true);
                    }
                    // throw RESP files which cannot produce SPECTRA to noSpectraPath
                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // move processed RESP files to respBox
                Utilities.moveToDirectory(respPath, respBoxPath, true);

                // move processed SPECTRA files to spectraBox
                Utilities.moveToDirectory(spectraPath, spectraBoxPath, true);

                // move processed MOD files to modBox
                Utilities.moveToDirectory(modPath, modBoxPath, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * @return if already run or not
     */
    boolean hadRun() {
        return hadRun;
    }

    /**
     * Eliminates problematic files made by rdseed, such as ones with different delta, and merge split files.
     */
    private void mergeUnevenSac() throws IOException {
        // merge
        SegmentedSacMerger u = new SegmentedSacMerger(EVENT_DIR.toPath());
        u.merge();
        u.move();
    }

    /**
     * modify merged SAC files
     */
    private void modifySACs() throws IOException {
        // System.out.println("Modifying sac files in "
        // + eventDir.getAbsolutePath());
        Path trashBoxPath = EVENT_DIR.toPath().resolve("trash");
        Path mergedBoxPath = EVENT_DIR.toPath().resolve("merged");

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPathStream) {
                SACModifierMSEED sm = new SACModifierMSEED(event, sacPath, byPDE);

                // TODO 00 01 "" duplication detect
                // header check khole e.t.c
                if (!sm.canInterpolate() || !sm.checkHeader()) {
                    Utilities.moveToDirectory(sacPath, trashBoxPath, true);
                    continue;
                }

                // remove trends in SAC files interpolate the files .SAC > .MOD
                sm.removeTrend();

                sm.zeroPad();

                sm.rebuild();

                // filter by distance
                if (!sm.checkEpicentralDistance(MINIMUM_EPICENTRAL_DISTANCE, MAXIMUM_EPICENTRAL_DISTANCE)) {
                    Utilities.moveToDirectory(sacPath, trashBoxPath, true);
                    continue;
                }

                // move SAC files after treatment in the merged folder
                Utilities.moveToDirectory(sacPath, mergedBoxPath, true);
            }
        }
    }

    /**
     * Convert/rotate all files with (.E, .N), (.1, .2) to (.R, .T).
     * successful files are put in rotatedNE the others are in nonrotatedNE
     * The prpcess from (.1, .2) to (.R, .T)  is added (2021.08.21 kenji)
     */
    private void rotate() throws IOException {
        Path trashBox = EVENT_DIR.toPath().resolve("nonRotatedNE");
        Path neDir = EVENT_DIR.toPath().resolve("rotatedNE");

        try (DirectoryStream<Path> eStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.E")) {
            for (Path ePath : eStream) {
                String[] parts = ePath.getFileName().toString().split("\\.");
                Path nPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".N");
                Path rPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".T");

                if (!Files.exists(nPath)) {
                    Utilities.moveToDirectory(ePath, trashBox, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(ePath, nPath, rPath, tPath);
                if (rotated) {
                    Utilities.moveToDirectory(nPath, neDir, true);
                    Utilities.moveToDirectory(ePath, neDir, true);
                } else {
                    Utilities.moveToDirectory(ePath, trashBox, true);
                    Utilities.moveToDirectory(nPath, trashBox, true);
                }
            }
        }

        // if the difference in CMPAZ is 90 degrees, (.1, .2) is converted to (.R, .T)  (2021.08.21 kenji)
        try (DirectoryStream<Path> oneStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.1")) {
            for (Path onePath : oneStream) {
                String[] parts = onePath.getFileName().toString().split("\\.");
                Path twoPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".2");
                Path rPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = EVENT_DIR.toPath().resolve(parts[0] + "." + parts[1] + ".T");

                if (!Files.exists(twoPath)) {
                    Utilities.moveToDirectory(onePath, trashBox, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(onePath, twoPath, rPath, tPath);
                if (rotated) {
                    Utilities.moveToDirectory(twoPath, neDir, true);
                    Utilities.moveToDirectory(onePath, neDir, true);
                } else {
                    Utilities.moveToDirectory(onePath, trashBox, true);
                    Utilities.moveToDirectory(twoPath, trashBox, true);
                }
            }
        }


        // If there are files (.N) which had no pairs (.E), move them to trash
        try (DirectoryStream<Path> nPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.N")) {
            for (Path nPath : nPaths)
                Utilities.moveToDirectory(nPath, trashBox, true);
        }

        // If there are files (.2) which had no pairs (.1), move them to trash (2021.08.21 kenji)
        try (DirectoryStream<Path> twoPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.2")) {
            for (Path twoPath : twoPaths)
                Utilities.moveToDirectory(twoPath, trashBox, true);
        }

    }

    /**
     * OBSOLETE
     * Adjusts delta, cmpinc and cmpaz in SAC files extracted by 'rdseed'
     * The SAC files are copied in 'rdseedOutputBackup' for backup.
     */
    private void preprocess() throws IOException {
        Path backupPath = EVENT_DIR.toPath().resolve("rdseedOutputBackup");
        Files.createDirectories(backupPath);
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                Files.copy(sacPath, backupPath.resolve(sacPath.getFileName()));
                fixDelta(sacPath);
            }
        }

    }

    /**
     * This method downloads both station information (STATION...) and instrument response (RESP...) files from IRIS/WS
     * and fixes SAC headers related to stations (STLA, STLO, CMPAZ, CMPINC) not included with SAC files extracted form miniseed file.
     */
    private void downloadViaIRISWS() throws IOException {
        Path backupPath = EVENT_DIR.toPath().resolve("rdseedOutputBackup");
        Files.createDirectories(backupPath);
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
//            	System.out.println("Enter: "+sacPath.toString());
            	String[] tmp = sacPath.toString().split("\\.");
            	if(tmp.length !=9) {continue;} //TODO too ad-hoc!!!
            	Files.copy(sacPath, backupPath.resolve(sacPath.getFileName()));

                // $time for IRIS/WS is made from SAC File name extracted from MiniSeed
                LocalDateTime time = LocalDateTime.of(Integer.parseInt(sacPath.getFileName().toString().split("\\.")[5]),1,1,0,0,0)
                		.withDayOfYear(Integer.parseInt(sacPath.getFileName().toString().split("\\.")[6]));
                // $location for IRIS/WS is made from SAC File name extracted from MiniSeed
                String loc = "";
                if(sacPath.getFileName().toString().split("\\.")[2].isEmpty()) {loc = "--";}
                else {loc = sacPath.getFileName().toString().split("\\.")[2];}

                // request STATION files via IRIS/WS
//                System.out.println("Enter: STATIONFILES");
                StationInformationFile sii = new StationInformationFile(sacPath.getFileName().toString().split("\\.")[0], sacPath.getFileName().toString().split("\\.")[1],
                		loc, sacPath.getFileName().toString().split("\\.")[3]);//, time, time);
                sii.downloadStationInformation(EVENT_DIR.toPath());
                sii.readStationInformation(EVENT_DIR.toPath());

                // set SAC headers related to stations
 //               System.out.println("Enter: FIXHEADER");
                fixHeader(sacPath,sii);
                Files.move(EVENT_DIR.toPath().resolve(sii.getStationFile()), backupPath.resolve(sii.getStationFile())); //なぜか重い？
 //               System.out.println("Enter: FIXNAME");
                fixSACName(sacPath);

                // request RESP files via IRIS/WS
  //              System.out.println("Enter: RESPFILES");
                RespDataFile rdi = new RespDataFile(sacPath.getFileName().toString().split("\\.")[0], sacPath.getFileName().toString().split("\\.")[1],
                		loc, sacPath.getFileName().toString().split("\\.")[3]);//, time);
                rdi.downloadRespData(EVENT_DIR.toPath());

//               System.exit(0); // TODO 一旦止める
//                System.out.println("download: "+sacPath+" "+ sacPath.getFileName());

            }
        }
    }


    /**
     * Set SAC headers related to stations via StationInformation downloaded from IRIS/WS
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sii (StationInformationIRIS) provides station information
     * @throws IOException
     * @author kenji
     */
    private void fixHeader(Path sacPath, StationInformationFile sii) throws IOException {
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sii.getAzimuth() + " cmpinc " + sii.getDip());
            sacD.inputCMD("ch stlo "  +sii.getLongitude() + " stla " +sii.getLatitude());
            sacD.inputCMD("interpolate delta " + delta);
            sacD.inputCMD("w over");
        }
	}

    /**
     * This method makes SAC name changed from MSEED style to SEED style.
     * MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC"
     * SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC"
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @throws IOException
     * @author kenji
     */
    private void fixSACName(Path sacPath) throws IOException {
    	String[] oldFile = sacPath.getFileName().toString().split("\\.");

    	Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
    	int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
    	int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
    	int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
    	int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
    	int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
    	int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

    	String newName = i1 + "." + i2 + "." + i3 + "." + i4 + "." + i5 + "." + i6
    			+ "." + oldFile[0] + "." + oldFile[1] + "." + oldFile[2]
    			+ "." + oldFile[3] + "." + "M" + ".SAC";
    	try{
    		  Files.move(sacPath, EVENT_DIR.toPath().resolve(newName), StandardCopyOption.REPLACE_EXISTING);
    		}catch(IOException e){
    		  System.out.println(e);
    		}
    }

	/**
	 * This main method is obsolete.
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("It works only for one mseed file.");
        Path seedPath = Paths.get(args[0]);
        if (!Files.exists(seedPath)) throw new NoSuchFileException(seedPath.toString());
        Path out = seedPath.resolveSibling("seedSAC" + Utilities.getTemporaryString());
        new XMseedSAC(seedPath, out).run();
        System.err.println(seedPath + " is extracted in " + out);
    }


    @Override
    public void run() {
        if (!EVENT_DIR_ALREADY_EXIST) throw new RuntimeException("The condition is no good.");
        System.err.println("Opening " + MSEED_FILE + " in " + EVENT_DIR.getPath());
        // run rdseed -q [output directory] -fRd
        try {
            MSEED_FILE.extract(EVENT_DIR.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on extracting " + MSEED_FILE, e);
        }


        try {
            if (CMPMOD)
            // download station information
            	downloadViaIRISWS();
            // fix delta values
//            	preprocess(); // この前処理は、downloadViaIRISWSで実行したので、後で消す。
            // merge uneven SAC files
//            	mergeUnevenSac(); // TODO ファイル名をSEED形式に変更したから、動くはず。（要確認）
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on pre-processing " + MSEED_FILE, e);
        }

        try {
//        	System.out.println("Enter: modifySACs");
        	modifySACs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error on modifying " + MSEED_FILE, e);
        }


        // Use only BH[12ENZ]
        // remove waveforms of .[~NEZ]
        try {
//        	System.out.println("Enter: selectChannels");
           selectChannels();
        } catch (IOException e) {
            throw new RuntimeException("Error on selecting channels " + MSEED_FILE, e);
        }


        // instrumentation function deconvolution
 //   	System.out.println("Enter: deconvolute");
        deconvoluteSACMSEED();
        // rotation ((.N,.E) & (.1 & .2) -> (.R,.T))
        try {
            rotate();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on rotating " + MSEED_FILE, e);
        }

        // trash
        try {
            toTrash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on moving files to the trash box " + MSEED_FILE, e);
        }

        problem = check();

        hadRun = true;

        if (removeIntermediateFiles) removeIntermediateFiles();

        System.err.println("finish");
    }

    private void removeIntermediateFiles() {
        try {
            Path event = EVENT_DIR.toPath();
            FileUtils.deleteDirectory(event.resolve("merged").toFile());
            FileUtils.deleteDirectory(event.resolve("mod").toFile());
            FileUtils.deleteDirectory(event.resolve("rdseedOutputBackup").toFile());
            FileUtils.deleteDirectory(event.resolve("resp").toFile());
            FileUtils.deleteDirectory(event.resolve("rotatedNE").toFile());
            FileUtils.deleteDirectory(event.resolve("nonRotatedNE").toFile());
            FileUtils.deleteDirectory(event.resolve("spectra").toFile());
            FileUtils.deleteDirectory(event.resolve("trash").toFile());
            FileUtils.deleteDirectory(event.resolve("mergedUnevendata").toFile());
            FileUtils.deleteDirectory(event.resolve("invalidChannel").toFile());
            FileUtils.deleteDirectory(event.resolve("nonMergedUnevendata").toFile());
            FileUtils.deleteDirectory(event.resolve("noSpectraOrInvalidMOD").toFile());
            FileUtils.deleteDirectory(event.resolve("duplicateChannel").toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return if there are no problems, returns true
     */
    boolean hasProblem() {
        return problem;
    }

    /**
     * @return if any problem
     */
    private boolean check() {
        Path eventPath = EVENT_DIR.toPath();
        Path rdseedOutput = eventPath.resolve("rdseedOutputBackup");
        Path unmerged = eventPath.resolve("nonMergedUnevendata");
        Path unrotated = eventPath.resolve("nonRotatedNE");
        return Files.exists(rdseedOutput) || Files.exists(unmerged) || Files.exists(unrotated);
    }

    /**
     * unused SPECTRA*, RESP* files ->trash
     */
    private void toTrash() throws IOException {
        Path trash = EVENT_DIR.toPath().resolve("trash");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(EVENT_DIR.toPath())) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (name.contains("SPECTRA.") || name.contains("RESP.")) Utilities.moveToDirectory(path, trash, true);
            }
        }
    }

    /**
     * Run external process "evalresp".
     * Command: "evalresp station component year julian day minfreq maxfreq
     * npts -s lin -r cs -u vel"
     *
     * @param headerMap header of sac file
     * @return if succeed
     */
    private boolean runEvalresp(Map<SACHeaderEnum, String> headerMap) {
        int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
        double minFreq = samplingHz / npts;
        String command =
                "evalresp " + headerMap.get(SACHeaderEnum.KSTNM) + " " + headerMap.get(SACHeaderEnum.KCMPNM) + " " +
                        event.getCMTTime().getYear() + " " + event.getCMTTime().getDayOfYear() + " " + minFreq + " " +
                        samplingHz + " " + headerMap.get(SACHeaderEnum.NPTS) + " -s lin -r cs -u vel";
//        System.out.println("runevalresp: "+ command);// 4debug
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s"));
        pb.directory(EVENT_DIR.getAbsoluteFile());
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Remove files with suffixes other than [BH][HL][ENZ12]
     * @author anselme BH1 BH2 also kept
     */
    private void selectChannels() throws IOException {
        // System.out.println("Selecting Channels");
        Path trashBox = EVENT_DIR.toPath().resolve("invalidChannel");
        try (DirectoryStream<Path> modStream = Files.newDirectoryStream(EVENT_DIR.toPath(), "*.MOD")) {
            for (Path modPath : modStream) {
                String name = modPath.getFileName().toString();
                String channel = name.split("\\.")[9]; // changed from 3 to 9 (kenji)
                if (channel.equals("BHZ") || channel.equals("BHN") || channel.equals("BHE") ||
                        channel.equals("BLZ") || channel.equals("BLN") || channel.equals("BLE") ||
                        channel.equals("HHZ") || channel.equals("HHN") || channel.equals("HHE") ||
                        channel.equals("HLZ") || channel.equals("HLN") || channel.equals("HLE") ||
                        channel.equals("BH1") || channel.equals("BH2") ||
                        channel.equals("HH1") || channel.equals("HH2")) continue;
                Utilities.moveToDirectory(modPath, trashBox, true);
            }
        }
    }

    @Override
    public String toString() {
        return MSEED_FILE.toString();
    }
}
