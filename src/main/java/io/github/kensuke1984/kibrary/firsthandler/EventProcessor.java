package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTData;
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
class EventProcessor implements Runnable {
    /**
     * [s] delta for SAC files. SAC files with different delta will be interpolated
     * or downsampled.
     */
    private static final double  DELTA = 0.05;
    /**
     * [Hz] Sampling Hz in write SAC files
     */
    private static final double samplingHz = 20;

    private final EventFolder INPUT_DIR;
    private final Path OUTPUT_PATH;

    /**
     * GlobalCMTData for the event in the seedfile
     */
    private GlobalCMTData event;
    /**
     * true: the base time will be PDE time, false: CMT (default)
     */
    private boolean byPDE = false;
    /**
     * [deg] Minimum epicentral distance of SAC files to be output
     */
    private final double MINIMUM_EPICENTRAL_DISTANCE = 0;
    /**
     * [deg] Maximum epicentral distance of SAC files to be output
     */
    private final double MAXIMUM_EPICENTRAL_DISTANCE = 180;
    private boolean hadRun;
    /**
     * true: exception has occurred, false: not
     */
    private boolean problem;
    /**
     * if remove intermediate files
     */
    private boolean removeIntermediateFiles = true;

    private Path doneModifyPath;
    private Path doneMergePath;
    private Path doneDeconvolutePath;
    private Path doneRotatePath;
    private Path unModifiedPath;
    private Path unMergedPath;
    private Path unDeconvolutedPath;
    private Path unRotatedPath;
    private Path invalidStationPath;
    private Path invalidDistancePath;
    private Path duplicateChannelPath;

    EventProcessor(EventFolder eventDir, Path outPath) throws IOException {
        INPUT_DIR = eventDir;
        OUTPUT_PATH = outPath.resolve(eventDir.getName());

        event = eventDir.getGlobalCMTID().getEvent();

        invalidStationPath = OUTPUT_PATH.resolve("invalidStation");

        doneMergePath = OUTPUT_PATH.resolve("doneMerge");
        unMergedPath = OUTPUT_PATH.resolve("unMerged");

        doneModifyPath = OUTPUT_PATH.resolve("doneModify");
        unModifiedPath = OUTPUT_PATH.resolve("unModified");
        invalidDistancePath = OUTPUT_PATH.resolve("invalidDistance");

        doneDeconvolutePath = OUTPUT_PATH.resolve("doneDeconvolute");
        unDeconvolutedPath = OUTPUT_PATH.resolve("unDeconvoluted");
        duplicateChannelPath = OUTPUT_PATH.resolve("duplicateChannel");

        doneRotatePath = OUTPUT_PATH.resolve("doneRotate");
        unRotatedPath = OUTPUT_PATH.resolve("unRotated");

    }

    /**
     * If true then all intermediate files will be removed at the end.
     *
     * @param b set {@link #removeIntermediateFiles}
     */
    void setRemoveIntermediateFiles(boolean b) {
        removeIntermediateFiles = b;
    }

    @Override
    public void run() {

        try {
            Files.createDirectories(OUTPUT_PATH);
            setupSacs();
            // merge uneven SAC files
            mergeUnevenSac(); // TODO ファイル名をSEED形式に変更したから、動くはず。（要確認）
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on pre-processing " + INPUT_DIR.getName(), e);
        }
        try {
//          System.out.println("Enter: modifySACs");
            modifySacs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error on modifying " + INPUT_DIR.getName(), e);
        }

/* this was moved inside setupSacs() (Keisuke Otsuru 2021.09.19)
        // Use only BH[12ENZ]
        // remove waveforms of .[~NEZ]
        try {
//          System.out.println("Enter: selectChannels");
           selectChannels();
        } catch (IOException e) {
            throw new RuntimeException("Error on selecting channels " + INPUT_DIR.getName(), e);
        }
*/

        // instrumentation function deconvolution
 //     System.out.println("Enter: deconvolute");
        deconvoluteSACMSEED();
        // rotation ((.N,.E) & (.1 & .2) -> (.R,.T))
        try {
            rotate();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on rotating " + INPUT_DIR.getName(), e);
        }

/* this has become unneeded since STATION and RESP files are in dl* directory (Keisuke Otsuru 2021.09.19)
        // trash
        try {
            toTrash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on moving files to the trash box " + INPUT_DIR.getName(), e);
        }
*/
        problem = check();

        hadRun = true;

        if (removeIntermediateFiles) removeIntermediateFiles();

        System.err.println("finish");

    }

    /**
     * This method sets up the SAC files to be used by carrying out the following:
     * <ul>
     * <li> check whether the channel is supported by this class; if not, skip the SAC file </li>
     * <li> copy SAC file from the input directory to the output event directory with a new file name </li>
     * <li> read Station file; if unreadable, throw away the new SAC file </li>
     * <li> check whether the dip value of the channel is valid; if not, throw away the new SAC file </li>
     * <li> set SAC headers related to the station </li>
     * <li> interpolate the data with DELTA (which is currently 0.05 sec thus 20 Hz) </li>
     * </ul>
     * Thrown away files are put in "invalidStation".
     * @throws IOException
     * @author Keisuke Otsuru
     */
    private void setupSacs() throws IOException {
//        Path invalidStationBoxPath = OUTPUT_PATH.resolve("invalidStation");

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(INPUT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {  // sacPaths are paths of SAC files in the input directory; DO NOT MODIFY THEM
                SACFileName newFile = newSacName(sacPath); // newFile will be made in the output event directory

                // check channel validity
                if (!checkChannel(newFile.getChannel())) {
                    System.err.println("!! unsupported channel : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // copy SAC file from the input directory to the output event directory; file name changed here
                Path newSacPath = OUTPUT_PATH.resolve(newFile.toString());
                Files.copy(sacPath, newSacPath);

                // read Station file; throw away new SAC file if Station file is unfound or unreadable
                StationInformationIRIS sii = new StationInformationIRIS(newFile); // this probably won't fail, since it is merely substitution of values
                try {
                    sii.readStationInformation(INPUT_DIR.toPath()); // this will fail if Station file is unfound, etc.
                } catch (IOException e) {
                    System.err.println("!! unable to read Station file : " + event.getGlobalCMTID() + " - " + newSacPath.getFileName());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                // if the dip of a [vertical|horizontal] channel is not perfectly [vertical|horizontal], throw away the new SAC file
                // caution: up is dip=-90, down is dip=90
                // TODO: are there stations with downwards Z ?
                if ((isVerticalChannel(newFile.getChannel()) && Double.parseDouble(sii.getDip()) != -90)
                        || (!isVerticalChannel(newFile.getChannel()) && Double.parseDouble(sii.getDip()) != 0)) {
                    System.err.println("!! invalid dip (or CMPINC) value : " + event.getGlobalCMTID() + " - " + newSacPath.getFileName());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                //TODO: check station location here?

                // set sac headers using sii, and interpolate data with DELTA
                fixHeaderAndDelta(newSacPath,sii);
            }
        }

    }

    /**
     * This method generates a SEED style SAC name from an MSEED style one.
     * MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC"
     * SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC"
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @return SACFileName object with the new SAC file name
     * @throws IOException
     * @author kenji
     */
    private SACFileName newSacName(Path sacPath) throws IOException {
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
                + "." + oldFile[3] + "." + "M" + ".SAC"; //TODO: the number of digits may be different from SEED style name
        return new SACFileName(newName);
    }

    /**
     * Checks whether the channel is supported by this class.
     * Files with channels other than [BH][HL][ZNE12] will not be accepted.
     * @param channel (String) The name of channel to check
     * @return (boolean) true if channel is supported
     */
    private boolean checkChannel(String channel) {
        return (channel.equals("BHZ") || channel.equals("BHN") || channel.equals("BHE") ||
                channel.equals("BH1") || channel.equals("BH2") ||
                channel.equals("BLZ") || channel.equals("BLN") || channel.equals("BLE") ||
                channel.equals("BL1") || channel.equals("BL2") ||
                channel.equals("HHZ") || channel.equals("HHN") || channel.equals("HHE") ||
                channel.equals("HH1") || channel.equals("HH2") ||
                channel.equals("HLZ") || channel.equals("HLN") || channel.equals("HLE") ||
                channel.equals("HL1") || channel.equals("HL2"));
        //make sure to change isVerticalChannel() if you start accepting non-3-letter channels
    }

    /**
     * Checks whether the channel is vertical (i.e. ??Z).
     * @param channel (String) The name of channel to check; must be a 3-letter name
     * @return (boolean) true if channel is vertical
     */
    private boolean isVerticalChannel(String channel) {
        if (channel.substring(2).equals("Z")) return true; // since checkChannel() is done, input should always be 3 letters
        else return false;
    }

    /**
     * Sets SAC headers related to stations via StationInformation file,
     * and also interpolates SAC file with DELTA (which is currently 0.05 sec thus 20 Hz).
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sii (StationInformationIRIS) provides station information
     * @throws IOException
     * @author kenji
     */
    private void fixHeaderAndDelta(Path sacPath, StationInformationIRIS sii) throws IOException {
        double inclination = Double.parseDouble(sii.getDip()) + 90.0; // CAUTION: up is dip=-90 but CMPINC=0, horizontal is dip=0 but CMPINC=90
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sii.getAzimuth() + " cmpinc " + String.valueOf(inclination));
            sacD.inputCMD("ch stlo "  + sii.getLongitude() + " stla " + sii.getLatitude());
            sacD.inputCMD("interpolate delta " + DELTA);
            sacD.inputCMD("w over");
        }
    }

    /**
     * This method merges multiple SAC files that are supposed to be part of the same waveform.
     * This is done through {@link UnevenSACMerger}.
     * Successful files are put in "doneMerge" and the others in "unMerged".
     *
     * @throws IOException
     */
    private void mergeUnevenSac() throws IOException {
        // merge
        UnevenSACMerger u = new UnevenSACMerger(OUTPUT_PATH, doneMergePath, unMergedPath);
        u.merge();
        u.move();
    }

    /**
     * Modifies merged SAC files by {@link SACModifierMSEED}.
     * Successful files are put in "doneModify", while files that failed to be zero-padded go in "unModified"
     * and those others with invaled epicentral distances end up in "invalidDistance".
     */
    private void modifySacs() throws IOException {
        // System.out.println("Modifying sac files in "
        // + eventDir.getAbsolutePath());
//        Path unableBoxPath = OUTPUT_PATH.resolve("unModified");
//        Path invalidBoxPath = OUTPUT_PATH.resolve("invalidDistance");
//        Path mergedBoxPath = OUTPUT_PATH.resolve("doneModify");

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(OUTPUT_PATH, "*.SAC")) {
            for (Path sacPath : sacPathStream) {
                SACModifierMSEED sm = new SACModifierMSEED(event, sacPath, byPDE);

                // TODO 00 01 "" duplication detect
                // header check khole e.t.c
                if (!sm.canInterpolate() || !sm.checkHeader()) {
                    System.err.println("!! unable to zero-pad : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, unModifiedPath, true);
                    continue;
                }

                // remove trends in SAC files; output in file with new name .SAC > .MOD
                sm.removeTrend();

                // interpolate the files ???
                sm.interpolate(); // TODO: throw away if it fails?

                //
                sm.rebuild();

                // filter by distance
                //TODO: this is currently always 0~180
                if (!sm.checkEpicentralDistance(MINIMUM_EPICENTRAL_DISTANCE, MAXIMUM_EPICENTRAL_DISTANCE)) {
                    System.err.println("!! invalid epicentral distance : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, invalidDistancePath, true);
                    continue;
                }

                // move SAC files after treatment into the merged folder
                Utilities.moveToDirectory(sacPath, doneModifyPath, true);
            }
        }
    }

    /**
     * Remove files with suffixes other than [BH][HL][ENZ12]
     * @author anselme BH1 BH2 also kept
     */
/*    private void selectChannels() throws IOException {
        // System.out.println("Selecting Channels");
        Path trashBox = OUTPUT_PATH.resolve("invalidChannel");
        try (DirectoryStream<Path> modStream = Files.newDirectoryStream(OUTPUT_PATH, "*.MOD")) {
            for (Path modPath : modStream) {
                String name = modPath.getFileName().toString();
                String channel = name.split("\\.")[3]; // changed from 3 to 9 (kenji) -> returned to 3 TODO
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
*/

    /**
     * Deconvolute instrument function for all the MOD files in the event folder.
     * 対応するRESPのevalrespに失敗したMODファイルはNOSPECTRAMODへ
     * mseed2sacで解凍したSACのlocation(KHOLE)ヘッダーに"-12345"が入る場合に対応 (2021-08-23)
     */
    private void deconvoluteSACMSEED() { //TODO STATIONINFORMATIONも削除しないといけない。(kenji)
        // System.out.println("Conducting deconvolution");
//        Path noSpectraPath = OUTPUT_PATH.resolve("noSpectraOrInvalidMOD");
//        Path duplicateChannelPath = OUTPUT_PATH.resolve("duplicateChannel");
        // evalresp後のRESP.*ファイルを移動する TODO メソッドを分ける
//        Path respBoxPath = OUTPUT_PATH.resolve("resp");
//        Path spectraBoxPath = OUTPUT_PATH.resolve("spectra");
//        Path modBoxPath = OUTPUT_PATH.resolve("mod");
        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(OUTPUT_PATH, "*.MOD")) {
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
                Path spectraPath = OUTPUT_PATH.resolve(spectraFileName);
                Path respPath = INPUT_DIR.toPath().resolve(respFileName);
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
                Path afterPath = OUTPUT_PATH.resolve(afterName);
//                System.out.println("deconvolute: "+ afterPath); // 4debug

                // run evalresp
                // If it fails, throw MOD and RESP files to trash
                if (!runEvalresp(headerMap, respPath)) {
                    // throw MOD.* files which cannot produce SPECTRA to noSpectra
                    Utilities.moveToDirectory(modPath, unDeconvolutedPath, true);
                    // throw RESP.* files which cannot produce SPECTRA to noSpectra
//                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
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
//                        Utilities.moveToDirectory(respPath, duplicateChannelPath, true);
                        continue;
                    }
                    SACDeconvolution.compute(modPath, spectraPath, afterPath, samplingHz / npts, samplingHz);
                } catch (Exception e) {
                    // throw *.MOD files which cannot produce SPECTRA to noSpectraPath
                    Utilities.moveToDirectory(modPath, unDeconvolutedPath, true);
                    // throw SPECTRA files which cannot produce SPECTRA to noSpectraPath
                    // In case that outdated RESP file cannot produce any SPECTRA file
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                        Utilities.moveToDirectory(spectraPath, unDeconvolutedPath, true);
                    }
                    // throw RESP files which cannot produce SPECTRA to noSpectraPath
//                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // move processed RESP files to respBox
//                Utilities.moveToDirectory(respPath, respBoxPath, true);

                // move processed SPECTRA files to spectraBox
                Utilities.moveToDirectory(spectraPath, doneDeconvolutePath, true);

                // move processed MOD files to modBox
                Utilities.moveToDirectory(modPath, doneDeconvolutePath, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Run external process "evalresp".
     * Command: "evalresp station component year julian day minfreq maxfreq
     * npts -s lin -r cs -u vel"
     *
     * @param headerMap (Map<SACHeaderEnum, String>) Header of sac file
     * @param inputPath (Path) Path of RESP file to be used, or the directory containing it.
     * @return (boolean) true if succeed
     */
    private boolean runEvalresp(Map<SACHeaderEnum, String> headerMap, Path inputPath) {
        int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
        double minFreq = samplingHz / npts;
        String command =
                "evalresp " + headerMap.get(SACHeaderEnum.KSTNM) + " " + headerMap.get(SACHeaderEnum.KCMPNM) + " " +
                        event.getCMTTime().getYear() + " " + event.getCMTTime().getDayOfYear() + " " + minFreq + " " +
                        samplingHz + " " + headerMap.get(SACHeaderEnum.NPTS) +
                        " -f " + inputPath.toAbsolutePath() + " -s lin -r cs -u vel";
//        System.out.println("runevalresp: "+ command);// 4debug
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s"));
        pb.directory(OUTPUT_PATH.toFile());
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Converts/rotates all files with (.E, .N), (.1, .2) to (.R, .T).
     * Successful files are put in "doneRotateNE" the others in "unRotatedNE".
     * The process from (.1, .2) to (.R, .T) is added (2021.08.21 kenji)
     * @throws IOException
     */
    private void rotate() throws IOException {
//        Path trashBox = OUTPUT_PATH.resolve("unRotated");
//        Path neDir = OUTPUT_PATH.resolve("doneRotate");

        try (DirectoryStream<Path> eStream = Files.newDirectoryStream(OUTPUT_PATH, "*.E")) {
            for (Path ePath : eStream) {
                String[] parts = ePath.getFileName().toString().split("\\.");
                Path nPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".N");
                Path rPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".T");

                // throw away .E file if its pair .N file does not exist
                if (!Files.exists(nPath)) {
                    System.err.println("!! pair .N file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + ePath.getFileName());
                    Utilities.moveToDirectory(ePath, unRotatedPath, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(ePath, nPath, rPath, tPath);
                if (rotated) {
                    Utilities.moveToDirectory(nPath, doneRotatePath, true);
                    Utilities.moveToDirectory(ePath, doneRotatePath, true);
                } else {
                    System.err.println("!! rotate failed : " + event.getGlobalCMTID() + " - " + ePath.getFileName());
                    Utilities.moveToDirectory(ePath, unRotatedPath, true);
                    Utilities.moveToDirectory(nPath, unRotatedPath, true);
                }
            }
        }

        // if the difference in CMPAZ is 90 degrees, (.1, .2) is converted to (.R, .T)  (2021.08.21 kenji)
        try (DirectoryStream<Path> oneStream = Files.newDirectoryStream(OUTPUT_PATH, "*.1")) {
            for (Path onePath : oneStream) {
                String[] parts = onePath.getFileName().toString().split("\\.");
                Path twoPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".2");
                Path rPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".T");

                // throw away .1 file if its pair .2 file does not exist
                if (!Files.exists(twoPath)) {
                    System.err.println("!! pair .2 file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + onePath.getFileName());
                    Utilities.moveToDirectory(onePath, unRotatedPath, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(onePath, twoPath, rPath, tPath);
                if (rotated) {
                    Utilities.moveToDirectory(twoPath, doneRotatePath, true);
                    Utilities.moveToDirectory(onePath, doneRotatePath, true);
                } else {
                    System.err.println("!! rotate failed : " + event.getGlobalCMTID() + " - " + onePath.getFileName());
                    Utilities.moveToDirectory(onePath, unRotatedPath, true);
                    Utilities.moveToDirectory(twoPath, unRotatedPath, true);
                }
            }
        }


        // If there are files (.N) which had no pairs (.E), move them to trash
        try (DirectoryStream<Path> nPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.N")) {
            for (Path nPath : nPaths) {
                System.err.println("!! pair .E file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + nPath.getFileName());
                Utilities.moveToDirectory(nPath, unRotatedPath, true);
            }
        }

        // If there are files (.2) which had no pairs (.1), move them to trash (2021.08.21 kenji)
        try (DirectoryStream<Path> twoPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.2")) {
            for (Path twoPath : twoPaths) {
                System.err.println("!! pair .1 file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + twoPath.getFileName());
                Utilities.moveToDirectory(twoPath, unRotatedPath, true);
            }
        }

    }

    /**
     * unused SPECTRA*, RESP* files ->trash
     */
/*    private void toTrash() throws IOException {
        Path trash = OUTPUT_PATH.resolve("trash");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(OUTPUT_PATH)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (name.contains("SPECTRA.") || name.contains("RESP.")) Utilities.moveToDirectory(path, trash, true);
            }
        }
    }
*/
    /**
     * @return (boolean) true if any problem has occured
     */
    private boolean check() {
//        Path eventPath = OUTPUT_PATH;
//        Path rdseedOutput = eventPath.resolve("rdseedOutputBackup");
//        Path unmerged = eventPath.resolve("unMerged");
//        Path unrotated = eventPath.resolve("unRotated");
        return Files.exists(unMergedPath) || Files.exists(unModifiedPath) || Files.exists(unDeconvolutedPath) || Files.exists(unRotatedPath);
    }

    private void removeIntermediateFiles() {
        try {
            FileUtils.deleteDirectory(doneModifyPath.toFile());
            FileUtils.deleteDirectory(doneMergePath.toFile());
            FileUtils.deleteDirectory(doneDeconvolutePath.toFile());
            FileUtils.deleteDirectory(doneRotatePath.toFile());
            FileUtils.deleteDirectory(unModifiedPath.toFile());
            FileUtils.deleteDirectory(unMergedPath.toFile());
            FileUtils.deleteDirectory(unDeconvolutedPath.toFile());
            FileUtils.deleteDirectory(unRotatedPath.toFile());
            FileUtils.deleteDirectory(invalidStationPath.toFile());
            FileUtils.deleteDirectory(invalidDistancePath.toFile());
            FileUtils.deleteDirectory(duplicateChannelPath.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return (boolean) true if already run
     */
    boolean hadRun() {
        return hadRun;
    }

    /**
     * @return (boolean) true if there have been any problems
     */
    boolean hasProblem() {
        return problem;
    }


}