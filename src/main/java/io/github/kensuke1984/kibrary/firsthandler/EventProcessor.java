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

    EventProcessor(EventFolder eventDir, Path outPath) throws IOException {
        INPUT_DIR = eventDir;
        OUTPUT_PATH = outPath.resolve(eventDir.getName());

        event = eventDir.getGlobalCMTID().getEvent();
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
            preprocess();
            // merge uneven SAC files
            mergeUnevenSac(); // TODO ファイル名をSEED形式に変更したから、動くはず。（要確認）
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on pre-processing " + INPUT_DIR.getName(), e);
        }
        try {
//          System.out.println("Enter: modifySACs");
            modifySACs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error on modifying " + INPUT_DIR.getName(), e);
        }


        // Use only BH[12ENZ]
        // remove waveforms of .[~NEZ]
        try {
//          System.out.println("Enter: selectChannels");
           selectChannels();
        } catch (IOException e) {
            throw new RuntimeException("Error on selecting channels " + INPUT_DIR.getName(), e);
        }


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


        // trash
        try {
            toTrash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on moving files to the trash box " + INPUT_DIR.getName(), e);
        }

        problem = check();

        hadRun = true;

        if (removeIntermediateFiles) removeIntermediateFiles();

        System.err.println("finish");

    }

    private void preprocess() throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(INPUT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                SACFileName newFile = newSacName(sacPath);
                Path newSacPath = OUTPUT_PATH.resolve(newFile.toString());
                Files.copy(sacPath, newSacPath);

                StationInformationIRIS sii = new StationInformationIRIS(newFile);
                sii.readStationInformation(INPUT_DIR.toPath());
                fixHeader(newSacPath,sii);

//                fixDelta(sacPath);
            }
        }

    }

    /**
     * This method generates a SEED style SAC name from an MSEED style one.
     * MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC"
     * SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC"
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
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
                + "." + oldFile[3] + "." + "M" + ".SAC";
        return new SACFileName(newName);
    }

    /**
     * Set SAC headers related to stations via StationInformation downloaded from IRIS/WS
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sii (StationInformationIRIS) provides station information
     * @throws IOException
     * @author kenji
     */
    private void fixHeader(Path sacPath, StationInformationIRIS sii) throws IOException {
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sii.getAzimuth() + " cmpinc " + sii.getDip());
            sacD.inputCMD("ch stlo "  +sii.getLongitude() + " stla " +sii.getLatitude());
            sacD.inputCMD("interpolate delta " + DELTA);
            sacD.inputCMD("w over");
        }
    }

    /**
     * Eliminates problematic files made by rdseed, such as ones with different delta, and merge split files.
     */
    private void mergeUnevenSac() throws IOException {
        // merge
        UnevenSACMerger u = new UnevenSACMerger(OUTPUT_PATH);
        u.merge();
        u.move();
    }

    /**
     * modify merged SAC files
     */
    private void modifySACs() throws IOException {
        // System.out.println("Modifying sac files in "
        // + eventDir.getAbsolutePath());
        Path trashBoxPath = OUTPUT_PATH.resolve("trash");
        Path mergedBoxPath = OUTPUT_PATH.resolve("merged");

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(OUTPUT_PATH, "*.SAC")) {
            for (Path sacPath : sacPathStream) {
                SACModifierMSEED sm = new SACModifierMSEED(event, sacPath, byPDE);

                // TODO 00 01 "" duplication detect
                // header check khole e.t.c
                if (!sm.canInterpolate() || !sm.checkHeader()) {
                    Utilities.moveToDirectory(sacPath, trashBoxPath, true);
                    continue;
                }

                // remove trends in SAC files interpolate the files .SAC > .MOD
                sm.preprocess();

                sm.interpolate();

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
     * Remove files with suffixes other than [BH][HL][ENZ12]
     * @author anselme BH1 BH2 also kept
     */
    private void selectChannels() throws IOException {
        // System.out.println("Selecting Channels");
        Path trashBox = OUTPUT_PATH.resolve("invalidChannel");
        try (DirectoryStream<Path> modStream = Files.newDirectoryStream(OUTPUT_PATH, "*.MOD")) {
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

    /**
     * Deconvolute instrument function for all the MOD files in the event folder.
     * 対応するRESPのevalrespに失敗したMODファイルはNOSPECTRAMODへ
     * mseed2sacで解凍したSACのlocation(KHOLE)ヘッダーに"-12345"が入る場合に対応 (2021-08-23)
     */
    private void deconvoluteSACMSEED() { //TODO STATIONINFORMATIONも削除しないといけない。(kenji)
        // System.out.println("Conducting deconvolution");
        Path noSpectraPath = OUTPUT_PATH.resolve("noSpectraOrInvalidMOD");
        Path duplicateChannelPath = OUTPUT_PATH.resolve("duplicateChannel");
        // evalresp後のRESP.*ファイルを移動する TODO メソッドを分ける
//        Path respBoxPath = OUTPUT_PATH.resolve("resp");
        Path spectraBoxPath = OUTPUT_PATH.resolve("spectra");
        Path modBoxPath = OUTPUT_PATH.resolve("mod");
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
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
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
                    Utilities.moveToDirectory(modPath, noSpectraPath, true);
                    // throw SPECTRA files which cannot produce SPECTRA to noSpectraPath
                    // In case that outdated RESP file cannot produce any SPECTRA file
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                        Utilities.moveToDirectory(spectraPath, noSpectraPath, true);
                    }
                    // throw RESP files which cannot produce SPECTRA to noSpectraPath
//                    Utilities.moveToDirectory(respPath, noSpectraPath, true);
                    continue;
                }

                // move processed RESP files to respBox
//                Utilities.moveToDirectory(respPath, respBoxPath, true);

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
     * Run external process "evalresp".
     * Command: "evalresp station component year julian day minfreq maxfreq
     * npts -s lin -r cs -u vel"
     *
     * @param headerMap header of sac file
     * @return if succeed
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
     * Convert/rotate all files with (.E, .N), (.1, .2) to (.R, .T).
     * successful files are put in rotatedNE the others are in nonrotatedNE
     * The prpcess from (.1, .2) to (.R, .T)  is added (2021.08.21 kenji)
     */
    private void rotate() throws IOException {
        Path trashBox = OUTPUT_PATH.resolve("nonRotatedNE");
        Path neDir = OUTPUT_PATH.resolve("rotatedNE");

        try (DirectoryStream<Path> eStream = Files.newDirectoryStream(OUTPUT_PATH, "*.E")) {
            for (Path ePath : eStream) {
                String[] parts = ePath.getFileName().toString().split("\\.");
                Path nPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".N");
                Path rPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".T");

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
        try (DirectoryStream<Path> oneStream = Files.newDirectoryStream(OUTPUT_PATH, "*.1")) {
            for (Path onePath : oneStream) {
                String[] parts = onePath.getFileName().toString().split("\\.");
                Path twoPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".2");
                Path rPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".R");
                Path tPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + ".T");

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
        try (DirectoryStream<Path> nPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.N")) {
            for (Path nPath : nPaths)
                Utilities.moveToDirectory(nPath, trashBox, true);
        }

        // If there are files (.2) which had no pairs (.1), move them to trash (2021.08.21 kenji)
        try (DirectoryStream<Path> twoPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.2")) {
            for (Path twoPath : twoPaths)
                Utilities.moveToDirectory(twoPath, trashBox, true);
        }

    }

    /**
     * unused SPECTRA*, RESP* files ->trash
     */
    private void toTrash() throws IOException {
        Path trash = OUTPUT_PATH.resolve("trash");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(OUTPUT_PATH)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (name.contains("SPECTRA.") || name.contains("RESP.")) Utilities.moveToDirectory(path, trash, true);
            }
        }
    }

    /**
     * @return if any problem
     */
    private boolean check() {
        Path eventPath = OUTPUT_PATH;
        Path rdseedOutput = eventPath.resolve("rdseedOutputBackup");
        Path unmerged = eventPath.resolve("nonMergedUnevendata");
        Path unrotated = eventPath.resolve("nonRotatedNE");
        return Files.exists(rdseedOutput) || Files.exists(unmerged) || Files.exists(unrotated);
    }

    private void removeIntermediateFiles() {
        try {
            Path event = OUTPUT_PATH;
            FileUtils.deleteDirectory(event.resolve("merged").toFile());
            FileUtils.deleteDirectory(event.resolve("mod").toFile());
            FileUtils.deleteDirectory(event.resolve("rdseedOutputBackup").toFile());
//            FileUtils.deleteDirectory(event.resolve("resp").toFile());
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
     * @return if already run or not
     */
    boolean hadRun() {
        return hadRun;
    }

    /**
     * @return if there are no problems, returns true
     */
    boolean hasProblem() {
        return problem;
    }


}