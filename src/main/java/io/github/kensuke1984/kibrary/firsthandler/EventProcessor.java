package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTData;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for creating a dataset, for one event, that can be used in the inversion process.
 * An event directory with SAC files, STATION files, and RESP files must be given as input.
 * <p>
 * This class requires that evalresp and sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/>evalresp</a> and
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/sac/>SAC</a> can be found at IRIS.
 *
 * @author Keisuke Otsuru
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
    private Path doneDeconvolvePath;
    private Path doneRotatePath;
    private Path doneEliminatePath;
    private Path unModifiedPath;
    private Path unMergedPath;
    private Path unDeconvolvedPath;
    private Path unRotatedPath;
    private Path unEliminatedPath;
    private Path invalidStationPath;
    private Path invalidDistancePath;
    private Path duplicateComponentPath;
    private Path duplicateInstrumentPath;

    /**
     * Constructor that specifies the input and output for this class.
     *
     * @param eventDir (EventFolder) The input event folder containing necessary SAC, STATION, and RESP files.
     * @param outPath (Path) The path where the ouput event directory should be made under.
     * @throws IOException
     */
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

        doneDeconvolvePath = OUTPUT_PATH.resolve("doneDeconvolve");
        unDeconvolvedPath = OUTPUT_PATH.resolve("unDeconvolved");
        duplicateComponentPath = OUTPUT_PATH.resolve("duplicateComponent");

        doneRotatePath = OUTPUT_PATH.resolve("doneRotate");
        unRotatedPath = OUTPUT_PATH.resolve("unRotated");

        doneEliminatePath = OUTPUT_PATH.resolve("doneEliminate");
        unEliminatedPath = OUTPUT_PATH.resolve("unEliminated");
        duplicateInstrumentPath = OUTPUT_PATH.resolve("duplicateInstrument");

    }

    /**
     * Sets {@link #removeIntermediateFiles}.
     *
     * @param b (boolean) If this is true, then all intermediate files will be removed at the end.
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
            mergeSacSegments(); // TODO ファイル名をSEED形式に変更したから、動くはず。（要確認）
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
        deconvolveSacs();
        // rotation ((.N,.E) & (.1 & .2) -> (.R,.T))
        try {
            rotate();
            duplicationElimination();
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

        System.err.println(event.getGlobalCMTID() + " finished");

    }

    /**
     * This method sets up the SAC files to be used by carrying out the following:
     * <ul>
     * <li> check whether the channel is supported by this class; if not, skip the SAC file </li>
     * <li> check whether the location is acceptable; if not, skip the SAC file </li>
     * <li> copy SAC file from the input directory to the output event directory with a new file name </li>
     * <li> read Station file; if unreadable, throw away the new SAC file </li>
     * <li> check whether the unit is velocity (m/s); if not, throw away the new SAC file </li>
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
            for (Path rawSacPath : sacPaths) {  // rawSacPaths are paths of SAC files in the input directory; DO NOT MODIFY THEM
//                SACFileName newFile = newSacName(rawSacPath); // newFile will be made in the output event directory

                SACFileName sacFile = new SACFileName(rawSacPath.getFileName().toString());

                // check channel validity
                if (!checkChannel(sacFile.getChannel())) {
                    System.err.println("!! unsupported channel : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // check location validity  TODO: this may have to be modified or removed
                if (!checkLocation(sacFile.getLocation())) {
                    System.err.println("!! bad location : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // copy SAC file from the input directory to the output event directory; file name changed here
                Path newSacPath = OUTPUT_PATH.resolve(newSacName(rawSacPath, sacFile));
                Files.copy(rawSacPath, newSacPath);

                // read Station file; throw away new SAC file if Station file is unfound or unreadable
                StationInformationFile sif = new StationInformationFile(sacFile.getNetwork(), sacFile.getStation(),
                        sacFile.getLocation(), sacFile.getChannel()); // this probably won't fail, since it is merely substitution of values
                try {
                    sif.readStationInformation(INPUT_DIR.toPath()); // this will fail if Station file is unfound, etc.
                } catch (IOException e) {
                    System.err.println("!! unable to read Station file : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                // if the unit used in the Station file is not in velocity (m/s or M/S), throw away the new SAC file
                // TODO: is this criterion valid?
                if (!sif.getScaleunits().equals("M/S") && !sif.getScaleunits().equals("m/s")) {
                    System.err.println("!! invalid unit - not M/S : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                // if the dip of a [vertical|horizontal] channel is not perfectly [vertical|horizontal], throw away the new SAC file
                // caution: up is dip=-90, down is dip=90
                // TODO: are there stations with downwards Z ?
                if ((isVerticalChannel(sacFile.getChannel()) && Double.parseDouble(sif.getDip()) != -90)
                        || (!isVerticalChannel(sacFile.getChannel()) && Double.parseDouble(sif.getDip()) != 0)) {
                    System.err.println("!! invalid dip (i.e. CMPINC) value : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                //TODO: check station location here?

                // set sac headers using sii, and interpolate data with DELTA
                fixHeaderAndDelta(newSacPath, sif, sacFile.getLocation().isEmpty());
            }
        }

    }

    /**
     * This method generates a new SAC file name containing the starting time of the internal data.
     * @param sacPath (Path) Path of the SAC file whose name will be fixed.
     * @param sacFile (SACFileName) The SAC file whose name will be fixed.
     * @return (String) The new SAC file name
     * @throws IOException
     */
    private String newSacName(Path sacPath, SACFileName sacFile) throws IOException {
//        String[] oldFile = sacPath.getFileName().toString().split("\\.");

        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
        int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
        int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
        int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
        int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
        int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
        int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

        return sacFile.getSetFileName(i1, i2, i3, i4, i5, i6);

/*        String newName = i1 + "." + i2 + "." + i3 + "." + i4 + "." + i5 + "." + i6
                + "." + oldFile[0] + "." + oldFile[1] + "." + oldFile[2]
                + "." + oldFile[3] + "." + "M" + ".SAC"; //the number of digits may be different from SEED style name
        return new SACFileName(newName);
*/
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
     * Checks whether the location isn't insane.
     * Currently, "" "00" "01" "02" are accepted.
     * @param location (String) The name of location to check
     * @return (boolean) true if location is fine
     */
    private boolean checkLocation(String location) {
        return location.isEmpty() || location.equals("00") || location.equals("01") || location.equals("02");
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
     * @param sif (StationInformationFile) Station information file.
     * @param blankLocation (boolean) true if the location is blank
     * @throws IOException
     */
    private void fixHeaderAndDelta(Path sacPath, StationInformationFile sif, boolean blankLocation) throws IOException {
        double inclination = Double.parseDouble(sif.getDip()) + 90.0; // CAUTION: up is dip=-90 but CMPINC=0, horizontal is dip=0 but CMPINC=90
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sif.getAzimuth() + " cmpinc " + String.valueOf(inclination));
            sacD.inputCMD("ch stlo "  + sif.getLongitude() + " stla " + sif.getLatitude());
            if (blankLocation) {
                sacD.inputCMD("ch khole ''"); // files with empty locations may have khole '-12345', so it is set to ''
            }
            sacD.inputCMD("interpolate delta " + DELTA);
            sacD.inputCMD("w over");
        }
    }

    /**
     * This method merges multiple SAC files that are supposed to be part of the same waveform.
     * This is done through {@link SegmentedSacMerger}.
     * Successful files are put in "doneMerge" and the others in "unMerged".
     *
     * @throws IOException
     */
    private void mergeSacSegments() throws IOException {
        // merge
        SegmentedSacMerger s = new SegmentedSacMerger(OUTPUT_PATH, doneMergePath, unMergedPath);
        s.merge();
        s.move();
    }

    /**
     * Modifies merged SAC files by {@link SACModifierMSEED}, as follows:
     * <ul>
     * <li> check whether the SAC file can be zero-padded; if not, throw it away </li>
     * <li> remove the trend in the data </li>
     * <li> zero-pad SAC files that start after the event time </li>
     * <li> write headers related to the event </li>
     * <li> SAC start time is set to the event time </li>
     * <li> SAC file is cut so that npts = 2^n </li>
     * <li> check whether the epicentral distance is in the wanted range; if not, throw the file away </li>
     * </ul>
     * Successful files are put in "doneModify", while files that failed to be zero-padded go in "unModified"
     * and those with invalid epicentral distances end up in "invalidDistance".
     */
    private void modifySacs() throws IOException {
        // System.out.println("Modifying sac files in "
        // + eventDir.getAbsolutePath());
//        Path unableBoxPath = OUTPUT_PATH.resolve("unModified");
//        Path invalidBoxPath = OUTPUT_PATH.resolve("invalidDistance");
//        Path mergedBoxPath = OUTPUT_PATH.resolve("doneModify");

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(OUTPUT_PATH, "*.MRG")) {
            for (Path sacPath : sacPathStream) {
                SACModifierMSEED sm = new SACModifierMSEED(event, sacPath, byPDE);

                // check whether the file can be zero-padded
                if (!sm.canInterpolate()) {
                    System.err.println("!! unable to zero-pad : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, unModifiedPath, true);
                    continue;
                }

                // remove trends in SAC files; output in file with new name .MRG > .MOD
                sm.removeTrend();

                // zero-pad SAC files that start after the event time
                sm.zeroPad();

                // SAC start time is set to the event time, and the SAC file is cut so that npts = 2^n
                sm.rebuild();

                // filter by distance
                //TODO: this is currently always 0~180
                if (!sm.checkEpicentralDistance(MINIMUM_EPICENTRAL_DISTANCE, MAXIMUM_EPICENTRAL_DISTANCE)) {
                    System.err.println("!! invalid epicentral distance : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, invalidDistancePath, true);
                    Utilities.moveToDirectory(sm.getModifiedPath(), invalidDistancePath, true);
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
                String channel = name.split("\\.")[3]; // changed from 3 to 9 (kenji) -> returned to 3
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
     * Deconvolve instrument function for all the MOD files in the event folder.
     * The resulting SAC file names will have components of either "X", "Y", or "Z".
     * If the deconvolution fails, files will be moved to "unDeconvolved".
     * If there are both ["1" and "E"] or ["2" and "N"] files for the same instrument, one will be moved to "duplicateComponent".
     * If everything succeeds, unneeded files will be moved to "doneDeconvolve".
     */
    private void deconvolveSacs() {
        // System.out.println("Conducting deconvolution");
//        Path noSpectraPath = OUTPUT_PATH.resolve("noSpectraOrInvalidMOD");
//        Path duplicateChannelPath = OUTPUT_PATH.resolve("duplicateChannel");
//        Path respBoxPath = OUTPUT_PATH.resolve("resp");
//        Path spectraBoxPath = OUTPUT_PATH.resolve("spectra");
//        Path modBoxPath = OUTPUT_PATH.resolve("mod");
        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(OUTPUT_PATH, "*.MOD")) {
//            String resp = "RESP.";
//            String spectra = "SPECTRA.";
            for (Path modPath : eventDirStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modPath);

                SACFileName modFile = new SACFileName(modPath.getFileName().toString());
                RespDataFile respFile = new RespDataFile(modFile.getNetwork(), modFile.getStation(), modFile.getLocation(), modFile.getChannel());
/*
                String channel = headerMap.get(SACHeaderEnum.KCMPNM);
                String location = headerMap.get(SACHeaderEnum.KHOLE);
//                if(khole.matches("-12345")) {khole = "--";} // this is for MSEEDSAC
                String respFileName =
                        resp + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                location + "." + channel;
///               if(khole.matches("--")) {khole = "";} // this is for MSEEDSAC
               String spectraFileName =
                        spectra + headerMap.get(SACHeaderEnum.KNETWK) + "." + headerMap.get(SACHeaderEnum.KSTNM) + "." +
                                location + "." + channel;
*/
                Path respPath = INPUT_DIR.toPath().resolve(respFile.getRespFile());
                Path spectraPath = OUTPUT_PATH.resolve(respFile.getSpectraFile());
/*                switch (componentName) {
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
*/
                String afterName = modFile.getDeconvolvedFileName();
                Path afterPath = OUTPUT_PATH.resolve(afterName);
//                System.out.println("deconvolute: "+ afterPath); // 4debug

                // run evalresp
                // If it fails, throw MOD files to trash
                if (!runEvalresp(headerMap, respPath)) {
                    System.err.println("!! evalresp failed : " + event.getGlobalCMTID() + " - " + afterName);
                    // throw MOD.* files which cannot produce SPECTRA to trash
                    Utilities.moveToDirectory(modPath, unDeconvolvedPath, true);
                    continue;
                }

                // execute deconvolution
                try {
                    int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));

                    // duplication of channel  TODO: this should choose E,N over 1,2 (otherwise, E&2 or 1&N may survive)
                    if (Files.exists(afterPath)) {
                        System.err.println("!! duplicate channel : " + event.getGlobalCMTID() + " - " + afterName);
                        // throw *.MOD files to duplicateComponentPath
                        Utilities.moveToDirectory(modPath, duplicateComponentPath, true);
                        // throw SPECTRA files to duplicateComponentPath
                        Utilities.moveToDirectory(spectraPath, duplicateComponentPath, true);
                        continue;
                    }

                    SACDeconvolution.compute(modPath, spectraPath, afterPath, samplingHz / npts, samplingHz);

                } catch (Exception e) {
                    System.err.println("!! deconvolution failed : " + event.getGlobalCMTID() + " - " + afterName);
                    // throw *.MOD files to trash
                    Utilities.moveToDirectory(modPath, unDeconvolvedPath, true);
                    // throw SPECTRA files to trash
                    // In case that outdated RESP file cannot produce any SPECTRA file,
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                        Utilities.moveToDirectory(spectraPath, unDeconvolvedPath, true);
                    }
                    continue;
                }

                // move processed SPECTRA files to archive
                Utilities.moveToDirectory(spectraPath, doneDeconvolvePath, true);

                // move processed MOD files to archive
                Utilities.moveToDirectory(modPath, doneDeconvolvePath, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Run external process "evalresp".
     * Command: "evalresp station component year julianday minfreq maxfreq npts
     * -n network -l location -f inputpath -s lin -r cs -u vel"
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
                        " -n " + headerMap.get(SACHeaderEnum.KNETWK) + " -l " + headerMap.get(SACHeaderEnum.KHOLE) +
                        " -f " + inputPath.toAbsolutePath() +
                        " -s lin -r cs -u vel";
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
     * Converts/rotates all files with (.X, .Y) to (.R, .T).
     * Successful files are put in "doneRotate" the others in "unRotated".
     * @throws IOException
     */
    private void rotate() throws IOException {
//        Path trashBox = OUTPUT_PATH.resolve("unRotated");
//        Path neDir = OUTPUT_PATH.resolve("doneRotate");

        try (DirectoryStream<Path> xStream = Files.newDirectoryStream(OUTPUT_PATH, "*.X")) {
            for (Path xPath : xStream) {
//                String[] parts = ePath.getFileName().toString().split("\\.");
                SACFileName xFile = new SACFileName(xPath.getFileName().toString());
                Path yPath = OUTPUT_PATH.resolve(xFile.getNameWithComponent("Y"));
                Path rPath = OUTPUT_PATH.resolve(xFile.getNameWithComponent("R"));
                Path tPath = OUTPUT_PATH.resolve(xFile.getNameWithComponent("T"));

                // throw away .X file if its pair .Y file does not exist
                if (!Files.exists(yPath)) {
                    System.err.println("!! pair .Y file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + xFile.toString());
                    Utilities.moveToDirectory(xPath, unRotatedPath, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(xPath, yPath, rPath, tPath);
                if (rotated) {
                    Utilities.moveToDirectory(xPath, doneRotatePath, true);
                    Utilities.moveToDirectory(yPath, doneRotatePath, true);
                } else {
                    System.err.println("!! rotate failed : " + event.getGlobalCMTID() + " - " + xFile.toString());
                    Utilities.moveToDirectory(xPath, unRotatedPath, true);
                    Utilities.moveToDirectory(yPath, unRotatedPath, true);
                }
            }
        }
/*
        // if the difference in CMPAZ is 90 degrees, (.1, .2) is converted to (.R, .T)  (2021.08.21 kenji)
        try (DirectoryStream<Path> oneStream = Files.newDirectoryStream(OUTPUT_PATH, "*.1")) {
            for (Path onePath : oneStream) {
                String[] parts = onePath.getFileName().toString().split("\\.");
                Path twoPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + "." + parts[2] + ".2");
                Path rPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + "." + parts[2] + ".R");
                Path tPath = OUTPUT_PATH.resolve(parts[0] + "." + parts[1] + "." + parts[2] + ".T");

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
*/

        // If there are files (.Y) which had no pairs (.X), move them to trash
        try (DirectoryStream<Path> yPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.Y")) {
            for (Path yPath : yPaths) {
                System.err.println("!! pair .X file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + yPath.getFileName());
                Utilities.moveToDirectory(yPath, unRotatedPath, true);
            }
        }
/*
        // If there are files (.2) which had no pairs (.1), move them to trash (2021.08.21 kenji)
        try (DirectoryStream<Path> twoPaths = Files.newDirectoryStream(OUTPUT_PATH, "*.2")) {
            for (Path twoPath : twoPaths) {
                System.err.println("!! pair .1 file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + twoPath.getFileName());
                Utilities.moveToDirectory(twoPath, unRotatedPath, true);
            }
        }
*/
    }

    // TODO 00 01 "" duplication detect
    private void duplicationElimination() throws IOException {

        // read R, T, and Z files into SacTriplet set
        Set<SacTriplet> sacTripletSet = new HashSet<>();
        try (DirectoryStream<Path> rStream = Files.newDirectoryStream(OUTPUT_PATH, "*.R")) {
            for (Path rPath : rStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(rPath))) sacTripletSet.add(new SacTriplet(rPath));
            }
        }
        try (DirectoryStream<Path> tStream = Files.newDirectoryStream(OUTPUT_PATH, "*.T")) {
            for (Path tPath : tStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(tPath))) sacTripletSet.add(new SacTriplet(tPath));
            }
        }
        try (DirectoryStream<Path> zStream = Files.newDirectoryStream(OUTPUT_PATH, "*.Z")) {
            for (Path zPath : zStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(zPath))) sacTripletSet.add(new SacTriplet(zPath));
            }
        }

        // throw away triplets that consist of neither {RTZ}, {RT}, nor {Z}
        for (SacTriplet oneTriplet : sacTripletSet) {
            if (!oneTriplet.checkValidity()) {
                System.err.println("!! incomplete triplet : " + event.getGlobalCMTID() + " - " + oneTriplet.getName());
                oneTriplet.dismiss();
                oneTriplet.move(unEliminatedPath);
            }
        }

        // eliminate files of same network, station, and component
        for (SacTriplet oneTriplet : sacTripletSet) {
            System.err.println("<> " + oneTriplet.getStation()); //debug

            if (oneTriplet.isDismissed()) continue;

            if (oneTriplet.getStation().equals("KMBO")) System.err.println("0 " + oneTriplet.getNetwork()); //debug

            for (SacTriplet otherTriplet : sacTripletSet) {
                if (otherTriplet.isDismissed()) continue;

                if (oneTriplet.getStation().equals("KMBO") && otherTriplet.getStation().equals("KMBO")) System.err.println("1"); //debug

                // if the two refer to the same triplet, skip
                if (oneTriplet.isItself(otherTriplet)) continue;

                if (oneTriplet.getStation().equals("KMBO") && otherTriplet.getStation().equals("KMBO")) System.err.println("2"); //debug

                // if the two triplets are of different stations and coordinates, skip
                if (!oneTriplet.atSameStation(otherTriplet)) continue;

                if (oneTriplet.getStation().equals("KMBO") && otherTriplet.getStation().equals("KMBO")) System.err.println("3"); //debug

                //if one is {RT} and the other is {Z}, leave both
                if (oneTriplet.complements(otherTriplet)) continue;

                if (oneTriplet.getStation().equals("KMBO") && otherTriplet.getStation().equals("KMBO")) System.err.println("4"); //debug

                // remove triplet that has less components, worst instruments, or larger location codes
                if(oneTriplet.isInferiorTo(otherTriplet)) {
                    System.err.println("!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
                            oneTriplet.getName() + " ( :: " + otherTriplet.getName() + " )");
                    oneTriplet.dismiss();
                    oneTriplet.move(duplicateInstrumentPath);
                    break; // no need to keep comparing
                } else {
                    System.err.println("!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
                            oneTriplet.getName() + " ( :: " + otherTriplet.getName() + " )");
                    otherTriplet.dismiss();
                    otherTriplet.move(duplicateInstrumentPath);
                }
            }
        }

        for (SacTriplet oneTriplet : sacTripletSet) {
            if (!oneTriplet.isDismissed()) {
                oneTriplet.rename(event.toString());
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
        return Files.exists(unMergedPath) || Files.exists(unModifiedPath) || Files.exists(unDeconvolvedPath) ||
                Files.exists(unRotatedPath) || Files.exists(unEliminatedPath);
    }

    private void removeIntermediateFiles() {
        try {
            FileUtils.deleteDirectory(doneModifyPath.toFile());
            FileUtils.deleteDirectory(doneMergePath.toFile());
            FileUtils.deleteDirectory(doneDeconvolvePath.toFile());
            FileUtils.deleteDirectory(doneRotatePath.toFile());
            FileUtils.deleteDirectory(doneEliminatePath.toFile());
            FileUtils.deleteDirectory(unModifiedPath.toFile());
            FileUtils.deleteDirectory(unMergedPath.toFile());
            FileUtils.deleteDirectory(unDeconvolvedPath.toFile());
            FileUtils.deleteDirectory(unRotatedPath.toFile());
            FileUtils.deleteDirectory(unEliminatedPath.toFile());
            FileUtils.deleteDirectory(invalidStationPath.toFile());
            FileUtils.deleteDirectory(invalidDistancePath.toFile());
            FileUtils.deleteDirectory(duplicateComponentPath.toFile());
            FileUtils.deleteDirectory(duplicateInstrumentPath.toFile());
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