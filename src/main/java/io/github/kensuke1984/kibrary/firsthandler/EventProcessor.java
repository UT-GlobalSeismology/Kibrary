package io.github.kensuke1984.kibrary.firsthandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.entrance.RespDataFile;
import io.github.kensuke1984.kibrary.entrance.StationInformationFile;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for creating a dataset, for one event, that can be used in the inversion process.
 * An event directory with SAC files, STATION files, and RESP files must be given as input.
 * Input SAC file names must be in the mseed-style format (ex. "IU.MAJO.00.BH2.M.2014.202.144400.SAC").
 * <p>
 * SAC files will be deconvolved of instrumental response, and rotated to gain radial and transverse components.
 * Information about the station and event is written into the header.
 * Selection for the station coordinate and epicentral distance will be done based on the user's specifications.
 * <p>
 * This class requires that evalresp and sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/>evalresp</a> and
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/sac/>SAC</a> can be found at IRIS.
 * <p>
 * TODO: The sac "cut b n" command in SacModifier fails if the sac version is too new (102.0 and later?).
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
    private static final double SAMPLING_HZ = 20;

    /**
     * The input event folder
     */
    private final EventFolder inputDir;
    /**
     * Path of the output event folder
     */
    private final Path outputPath;

    /**
     * GlobalCMTData for the event in the seedfile
     */
    private GlobalCMTAccess event;
    /**
     * true: the base time will be PDE time, false: CMT (default)
     */
    private boolean byPDE = false;
    private boolean hasRun;
    /**
     * true: exception has occurred, false: not
     */
    private boolean problem;

    /**
     * [deg] Minimum epicentral distance of SAC files to be output
     */
    private double minDistance = 0;
    /**
     * [deg] Maximum epicentral distance of SAC files to be output
     */
    private double maxDistance = 180;
    private double minLatitude = -90;
    private double maxLatitude = 90;
    private double minLongitude = -180;
    private double maxLongitude = 180;
    /**
     * threshold to judge which stations are in the same position [deg]
     */
    private double coordinateGrid = 0.01;
    /**
     * if remove intermediate files
     */
    private boolean removeIntermediateFiles = true;

    private Path doneMergePath;
    private Path doneModifyPath;
    private Path doneDeconvolvePath;
    private Path doneRotatePath;
    private Path unSetPath;
    private Path unMergedPath;
    private Path unModifiedPath;
    private Path unRotatedPath;
    private Path invalidStationPath;
    private Path invalidRespPath;
    private Path invalidTripletPath;
    private Path unwantedCoordinatePath;
    private Path unwantedDistancePath;
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
        inputDir = eventDir;
        outputPath = outPath.resolve(eventDir.getName());

        event = eventDir.getGlobalCMTID().getEvent();

        unSetPath = outputPath.resolve("unSet");
        invalidStationPath = outputPath.resolve("invalidStation");
        unwantedCoordinatePath = outputPath.resolve("unwantedCoordinate");

        doneMergePath = outputPath.resolve("doneMerge");
        unMergedPath = outputPath.resolve("unMerged");

        doneModifyPath = outputPath.resolve("doneModify");
        unModifiedPath = outputPath.resolve("unModified");
        unwantedDistancePath = outputPath.resolve("unwantedDistance");

        doneDeconvolvePath = outputPath.resolve("doneDeconvolve");
        invalidRespPath = outputPath.resolve("invalidResp");
        duplicateComponentPath = outputPath.resolve("duplicateComponent");

        doneRotatePath = outputPath.resolve("doneRotate");
        unRotatedPath = outputPath.resolve("unRotated");

        invalidTripletPath = outputPath.resolve("invalidTriplet");
        duplicateInstrumentPath = outputPath.resolve("duplicateInstrument");

    }

    /**
     * Sets parameters.
     *
     * @param minD (double) lower limit of epicentral distance
     * @param maxD (double) upper limit of epicentral distance
     * @param minLa (double) lower limit of latitude
     * @param maxLa (double) upper limit of latitude
     * @param minLo (double) lower limit of longitude
     * @param maxLo (double) upper limit of longitude
     * @param grid (double) threshold to judge which stations are in the same position
     * @param remove (boolean) If this is true, then all intermediate files will be removed at the end.
     */
    void setParameters(double minD, double maxD, double minLa, double maxLa, double minLo, double maxLo, double grid, boolean remove) {
        minDistance = minD;
        maxDistance = maxD;
        minLatitude = minLa;
        maxLatitude = maxLa;
        minLongitude = minLo;
        maxLongitude = maxLo;
        coordinateGrid = grid;
        removeIntermediateFiles = remove;
    }

    @Override
    public void run() {

        // copy, select, and set up SAC files
        try {
            Files.createDirectories(outputPath);
            setupSacs();
        } catch (IOException e) {
            System.err.println("!!!!!!! Error on setup : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on setup : " + inputDir.getName(), e);
        }

        // merge segmented SAC files
        try {
            mergeSacSegments();
        } catch (IOException e) {
            System.err.println("!!!!!!! Error on merge : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on merge : " + inputDir.getName(), e);
        }

        // remove trend, zero-pad, and cut SAC files
        try {
            modifySacs();
        } catch (Exception e) {
            System.err.println("!!!!!!! Error on modify : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on modify : " + inputDir.getName(), e);
        }

        // instrumentation function deconvolution
        try {
            deconvolveSacs();
        } catch (IOException e) {
            System.err.println("!!!!!!! Error on deconvolution : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on deconvolution : " + inputDir.getName(), e);
        }

        // rotation ((.N,.E) & (.1,.2) -> (.R,.T))
        try {
            rotate();
        } catch (IOException e) {
            System.err.println("!!!!!!! Error on rotation : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on rotation : " + inputDir.getName(), e);
        }

        // eliminating duplicate instruments and close stations
        // this is done after everything else so that we don't lose usable data (ex. if we choose an unrotatable triplet)
        try {
            duplicationElimination();
        } catch (IOException e) {
            System.err.println("!!!!!!! Error on elimination : " + inputDir.getName());
            e.printStackTrace();
            throw new RuntimeException("Error on elimination : " + inputDir.getName(), e);
        }

        if (removeIntermediateFiles) removeIntermediateFiles();

        problem = check();

        hasRun = true;

        System.err.println("** " + event.getGlobalCMTID() + " finished");

    }

    /**
     * This method sets up the SAC files to be used by carrying out the following:
     * <ul>
     * <li> check whether the channel is supported by this class; if not, skip the SAC file </li>
     * <li> check whether the location is acceptable; if not, display warning </li>
     * <li> copy SAC file from the input directory to the output event directory with a new file name </li>
     * <li> read Station file; if unreadable, throw away the new SAC file </li>
     * <li> check whether the station coordinate is within the wanted range; if not, throw away the new SAC file </li>
     * <li> if the station coordinate is (0,0), throw away the new SAC file </li>
     * <li> check whether the dip value of the channel is valid; if not, throw away the new SAC file </li>
     * <li> set SAC headers related to the station </li>
     * <li> interpolate the data with DELTA (which is currently 0.05 sec thus 20 Hz) </li>
     * </ul>
     * If STATION files are unreadable, the corresponding SAC files are thrown in "invalidStation".
     * SAC files that cannot be handled is put in "unSet",
     * and those with unwanted coordinates are put in "unwantedCoordinate".
     * @throws IOException
     * @author Keisuke Otsuru
     */
    private void setupSacs() throws IOException {

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(inputDir.toPath(), "*.SAC")) {
            for (Path rawSacPath : sacPaths) {
                // rawSacPaths are paths of SAC files in the input directory; DO NOT MODIFY THEM

                SACFileName sacFile = new SACFileName(rawSacPath.getFileName().toString());

                // check channel validity
                if (!checkChannel(sacFile.getChannel())) {
                    System.err.println("!! unsupported channel : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // check location validity -> just display warning, but process the file nonetheless
                // TODO: this may have to be modified or removed
                if (!checkLocation(sacFile.getLocation())) {
                    System.err.println("?? may be untrustworthy location : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    // continue; <- this file will not be skipped
                }

                // copy SAC file from the input directory to the output event directory; file name changed here
                Path newSacPath = outputPath.resolve(newSacName(rawSacPath, sacFile));
                Files.copy(rawSacPath, newSacPath);

                // read Station file; throw away new SAC file if Station file is unfound or unreadable
                // creating sif probably won't fail since it is merely substitution of values
                StationInformationFile sif = new StationInformationFile(sacFile.getNetwork(), sacFile.getStation(),
                        sacFile.getLocation(), sacFile.getChannel());
                try {
                    // this will fail if Station file is unfound, etc.
                    sif.readStationInformation(inputDir.toPath());
                } catch (Exception e) {
                    System.err.println("!!! unable to read Station file : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, invalidStationPath, true);
                    continue;
                }

                // check station coordinate
                if (!checkStationCoordinate(Double.parseDouble(sif.getLatitude()), Double.parseDouble(sif.getLongitude()))) {
                    //System.err.println("!! unwanted station coordinate : " + event.getGlobalCMTID() + " - " + sacFile.toString()); too noisy
                    Utilities.moveToDirectory(newSacPath, unwantedCoordinatePath, true);
                    continue;
                }

                // rejetct stations at (0,0) <- these are most likely stations that do not have correct coordinates written in.
                if (Double.parseDouble(sif.getLatitude()) == 0.0 && Double.parseDouble(sif.getLongitude()) == 0.0) {
                    System.err.println("!! rejecting station at coordinate (0,0) : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, unSetPath, true);
                    continue;
                }

                // if the dip of a [vertical|horizontal] channel is not perfectly [vertical|horizontal], throw away the new SAC file
                // caution: up is dip=-90, down is dip=90
                // TODO: are there stations with downwards Z ?
                if ((isVerticalChannel(sacFile.getChannel()) && Double.parseDouble(sif.getDip()) != -90)
                        || (!isVerticalChannel(sacFile.getChannel()) && Double.parseDouble(sif.getDip()) != 0)) {
                    System.err.println("!! invalid dip (i.e. CMPINC) value : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    Utilities.moveToDirectory(newSacPath, unSetPath, true);
                    continue;
                }

                // set sac headers using sii, and interpolate data with DELTA
                fixHeaderAndDelta(newSacPath, sif, sacFile.getLocation().isEmpty());
            }
        }

    }

    /**
     * This method generates a new SAC file name containing the starting time of the internal data.
     * The starting time will be read from the SAC header, not the SAC file name.
     * @param sacPath (Path) Path of the SAC file whose name will be fixed.
     * @param sacFile (SACFileName) The SAC file whose name will be fixed.
     * @return (String) The new SAC file name
     * @throws IOException
     */
    private String newSacName(Path sacPath, SACFileName sacFile) throws IOException {

        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
        int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
        int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
        int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
        int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
        int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
        int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

        return sacFile.getSetFileName(i1, i2, i3, i4, i5, i6);
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
        // make sure to change isVerticalChannel() if you start accepting non-3-letter channels
    }

    /**
     * Checks whether the location isn't insane.
     * Currently, "" and "00"~"99" are accepted.
     * @param location (String) The name of location to check
     * @return (boolean) true if location is fine
     */
    private boolean checkLocation(String location) {
        return location.isEmpty() || ( location.compareTo("00") >= 0 && location.compareTo("99") <= 0 );
        //return location.isEmpty() || location.equals("00") || location.equals("01") || location.equals("02");
    }

    /**
     * Checks whether the channel is vertical (i.e. ??Z).
     * @param channel (String) The name of channel to check; must be a 3-letter name
     * @return (boolean) true if channel is vertical
     */
    private boolean isVerticalChannel(String channel) {
        // since checkChannel() is done, input should always be 3 letters
        if (channel.substring(2).equals("Z")) return true;
        else return false;
    }

    /**
     * Checks whether the station is positioned in the wanted coordinate range.
     * @param latitude (double) Latitude of the station to check
     * @param longitude (double) Longitude of the station to check
     * @return (boolean) true if position is fine
     */
    private boolean checkStationCoordinate(double latitude, double longitude) {
        //latitude
        if (latitude < minLatitude || maxLatitude < latitude) return false;
        // longitude [-180, 180]
        if (maxLongitude <= 180) if (longitude < minLongitude || maxLongitude < longitude) return false;
        // longitude [0, 360]
        if (minLongitude <= 180 && 180 < maxLongitude) if (longitude < minLongitude && maxLongitude - 360 < longitude) return false;
        if (180 < minLongitude && 180 < maxLongitude) if (longitude < minLongitude - 360 || maxLongitude - 360 < longitude) return false;
        // else
        return true;
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
        // CAUTION: up is dip=-90 but CMPINC=0, horizontal is dip=0 but CMPINC=90
        double inclination = Double.parseDouble(sif.getDip()) + 90.0;

        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();

            // set current directory
            sacD.inputCMD("cd " + cwd);
            // read
            sacD.inputCMD("r " + sacPath.getFileName());
            // overwrite permission
            sacD.inputCMD("ch lovrok true");
            // write station-related parameters
            sacD.inputCMD("ch kstnm " + sif.getStation() + " knetwk " + sif.getNetwork());
            sacD.inputCMD("ch cmpaz " + sif.getAzimuth() + " cmpinc " + String.valueOf(inclination));
            sacD.inputCMD("ch stlo "  + sif.getLongitude() + " stla " + sif.getLatitude());
            // files with empty locations may have khole '-12345', so it is set to ''
            if (blankLocation) {
                sacD.inputCMD("ch khole ''");
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
        SegmentedSacMerger s = new SegmentedSacMerger(outputPath, doneMergePath, unMergedPath);
        s.merge();
        s.move();
    }

    /**
     * Modifies merged SAC files by {@link SacModifier}, as follows:
     * <ul>
     * <li> check whether the SAC file can be zero-padded; if not, throw it away </li>
     * <li> remove the trend in the data </li>
     * <li> check whether the SAC file has non-zero data; if not, throw it away </li>
     * <li> zero-pad SAC files that start after the event time </li>
     * <li> write headers related to the event </li>
     * <li> SAC start time is set to the event time </li>
     * <li> SAC file is cut so that npts = 2^n </li>
     * <li> check whether the epicentral distance is in the wanted range; if not, throw the file away </li>
     * </ul>
     * Successful files are put in "doneModify", while files that failed to be zero-padded go in "unModified"
     * and those with unwanted epicentral distances end up in "unwantedDistance".
     * @throws IOException
     */
    private void modifySacs() throws IOException {

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(outputPath, "*.MRG")) {
            for (Path sacPath : sacPathStream) {
                SacModifier sm = new SacModifier(event, sacPath, byPDE);

                // check whether the file can be zero-padded
                if (!sm.canInterpolate()) {
                    System.err.println("!! unable to zero-pad : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, unModifiedPath, true);
                    continue;
                }

                // remove trends in SAC files; output in file with new name .MRG > .MOD
                sm.removeTrend();

                // check whether the waveform has non-zero data
                if (sm.isCompleteZero()) {
                    System.err.println("!! waveform is 0 or NaN : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    Utilities.moveToDirectory(sacPath, unModifiedPath, true);
                    Utilities.moveToDirectory(sm.getModifiedPath(), unModifiedPath, true);
                    continue;
                }

                // zero-pad SAC files that start after the event time
                sm.zeroPad();

                // SAC start time is set to the event time, and the SAC file is cut so that npts = 2^n
                sm.rebuild();

                // filter stations by epicentral distance
                if (!sm.checkEpicentralDistance(minDistance, maxDistance)) {
                    //System.err.println("!! unwanted epicentral distance : " + event.getGlobalCMTID() + " - " + sacPath.getFileName()); too noisy
                    Utilities.moveToDirectory(sacPath, unwantedDistancePath, true);
                    Utilities.moveToDirectory(sm.getModifiedPath(), unwantedDistancePath, true);
                    continue;
                }

                // move SAC files after treatment into the merged folder
                Utilities.moveToDirectory(sacPath, doneModifyPath, true);
            }
        }
    }

    /**
     * Deconvolve instrument function for all the MOD files in the event folder.
     * The resulting SAC file names will have components of either "X", "Y", or "Z".
     * If the deconvolution fails, files will be moved to "invalidResp".
     * If there are both ["1" and "E"] or ["2" and "N"] files for the same instrument, one will be moved to "duplicateComponent".
     * If everything succeeds, unneeded files will be moved to "doneDeconvolve".
     * @throws IOException
     */
    private void deconvolveSacs() throws IOException{

        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(outputPath, "*.MOD")) {
            for (Path modPath : eventDirStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modPath);

                SACFileName modFile = new SACFileName(modPath.getFileName().toString());
                String afterName = modFile.getDeconvolvedFileName();
                Path afterPath = outputPath.resolve(afterName);

                RespDataFile respFile = new RespDataFile(modFile.getNetwork(), modFile.getStation(), modFile.getLocation(), modFile.getChannel());
                Path respPath = inputDir.toPath().resolve(respFile.getRespFile());
                Path spectraPath = outputPath.resolve(respFile.getSpectraFile());

                //System.out.println("deconvolute: "+ afterPath); // 4debug

                // run evalresp
                // If it fails, throw MOD files to trash
                if (!runEvalresp(headerMap, respPath)) {
                    System.err.println("!!! evalresp failed : " + event.getGlobalCMTID() + " - " + afterName);
                    // throw MOD.* files which cannot produce SPECTRA to trash
                    Utilities.moveToDirectory(modPath, invalidRespPath, true);
                    continue;
                }

                // execute deconvolution
                try {
                    int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));

                    // duplication of channel E,N and 1,2  TODO: this should choose E,N over 1,2 (otherwise, E&2 or 1&N may survive)
                    if (Files.exists(afterPath)) {
                        System.err.println("!! duplicate channel : " + event.getGlobalCMTID() + " - " + afterName);
                        // throw *.MOD files to duplicateComponentPath
                        Utilities.moveToDirectory(modPath, duplicateComponentPath, true);
                        // throw SPECTRA files to duplicateComponentPath
                        Utilities.moveToDirectory(spectraPath, duplicateComponentPath, true);
                        continue;
                    }

                    SacDeconvolution.compute(modPath, spectraPath, afterPath, SAMPLING_HZ / npts, SAMPLING_HZ);

                } catch (Exception e) {
                    System.err.println("!! deconvolution failed : " + event.getGlobalCMTID() + " - " + afterName);
                    // throw *.MOD files to trash
                    Utilities.moveToDirectory(modPath, invalidRespPath, true);
                    // throw SPECTRA files to trash
                    // In case that outdated RESP file cannot produce any SPECTRA file,
                    // the existence condition is added (2021.08.21 kenji)
                    if(Files.exists(spectraPath)) {
                        Utilities.moveToDirectory(spectraPath, invalidRespPath, true);
                    }
                    continue;
                }

                // move processed SPECTRA files to archive
                Utilities.moveToDirectory(spectraPath, doneDeconvolvePath, true);

                // move processed MOD files to archive
                Utilities.moveToDirectory(modPath, doneDeconvolvePath, true);
            }
        }

    }

    /**
     * Run external process "evalresp".
     * <p>
     * Command: "evalresp station component year julianday minfreq maxfreq npts
     * -n network -l location -f inputpath -s lin -r cs -u vel"
     * <p>
     * By setting "-u vel", the output file will give the instrument response from velocity(input) to counts(output),
     * no matter what the actual physical input of the instrument was.
     * The output file will have the name "SPECTRA.NET.STA.LOC.CHA"
     * <p>
     * See <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/5-0-0/manual/>the evalresp manual</a> for details.
     *
     * @param headerMap (Map<SACHeaderEnum, String>) Header of sac file
     * @param inputPath (Path) Path of RESP file to be used, or the directory containing it.
     * @return (boolean) true if succeed
     */
    private boolean runEvalresp(Map<SACHeaderEnum, String> headerMap, Path inputPath) {
        int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
        double minFreq = SAMPLING_HZ / npts;
        String command =
                "evalresp " + headerMap.get(SACHeaderEnum.KSTNM) + " " + headerMap.get(SACHeaderEnum.KCMPNM) + " " +
                        event.getCMTTime().getYear() + " " + event.getCMTTime().getDayOfYear() + " " + minFreq + " " +
                        SAMPLING_HZ + " " + headerMap.get(SACHeaderEnum.NPTS) +
                        " -n " + headerMap.get(SACHeaderEnum.KNETWK) + " -l " + headerMap.get(SACHeaderEnum.KHOLE) +
                        " -f " + inputPath.toAbsolutePath() +
                        " -s lin -r cs -u vel";
        //System.out.println("runevalresp: "+ command);// 4debug
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s"));
        pb.directory(outputPath.toFile());
        try {
            // the standard error stream will be redirected to the standard output stream
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // The buffer of the output must be kept reading, or else, the process will freeze when the buffer becomes full.
            // Even if you want to stop printing the output from mseed2sac, just erase the line with println() and nothing else.
            String str;
            BufferedReader brstd = new BufferedReader(new InputStreamReader(p.getInputStream()));
            // reading the buffer
            while((str = brstd.readLine()) != null) {
                System.out.println(str); // Comment out only this single line if you don't want the output.
            }
            brstd.close();

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

        try (DirectoryStream<Path> xStream = Files.newDirectoryStream(outputPath, "*.X")) {
            for (Path xPath : xStream) {
                SACFileName xFile = new SACFileName(xPath.getFileName().toString());
                Path yPath = outputPath.resolve(xFile.getNameWithComponent("Y"));
                Path rPath = outputPath.resolve(xFile.getNameWithComponent("R"));
                Path tPath = outputPath.resolve(xFile.getNameWithComponent("T"));

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

        // If there are files (.Y) which had no pairs (.X), move them to trash
        try (DirectoryStream<Path> yPaths = Files.newDirectoryStream(outputPath, "*.Y")) {
            for (Path yPath : yPaths) {
                System.err.println("!! pair .X file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + yPath.getFileName());
                Utilities.moveToDirectory(yPath, unRotatedPath, true);
            }
        }
    }

    /**
     * Eliminates duplication in the data.
     * If there are multiple files with the same network and station for a given component,
     * or the there are files for several stations that are positioned very close to each other with the same component,
     * one is selected and the others are discarded.
     * If there are invalid triplets, they will be put in "invalidTriplet".
     * Eliminated SAC files will be put in "duplicateInstrument".
     * @throws IOException
     */
    private void duplicationElimination() throws IOException {

        // read R, T, and Z files into SacTriplet set
        Set<SacTriplet> sacTripletSet = new HashSet<>();
        try (DirectoryStream<Path> rStream = Files.newDirectoryStream(outputPath, "*.R")) {
            for (Path rPath : rStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(rPath))) sacTripletSet.add(new SacTriplet(rPath, coordinateGrid));
            }
        }
        try (DirectoryStream<Path> tStream = Files.newDirectoryStream(outputPath, "*.T")) {
            for (Path tPath : tStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(tPath))) sacTripletSet.add(new SacTriplet(tPath, coordinateGrid));
            }
        }
        try (DirectoryStream<Path> zStream = Files.newDirectoryStream(outputPath, "*.Z")) {
            for (Path zPath : zStream) {
                if (sacTripletSet.stream().noneMatch(triplet -> triplet.add(zPath))) sacTripletSet.add(new SacTriplet(zPath, coordinateGrid));
            }
        }

        // throw away triplets that consist of neither {RTZ}, {RT}, nor {Z}
        for (SacTriplet oneTriplet : sacTripletSet) {
            if (!oneTriplet.checkValidity()) {
                System.err.println("!!! incomplete triplet : " + event.getGlobalCMTID() + " - " + oneTriplet.getName());
                oneTriplet.dismiss();
                oneTriplet.move(invalidTripletPath);
            }
        }

        // eliminate files of same network, station, and component
        for (SacTriplet oneTriplet : sacTripletSet) {
            if (oneTriplet.isDismissed()) continue;

            for (SacTriplet otherTriplet : sacTripletSet) {
                if (otherTriplet.isDismissed()) continue;

                // if the two refer to the same triplet, skip
                if (oneTriplet.isItself(otherTriplet)) continue;
                // if the two triplets are of different stations and coordinates, skip
                if (!oneTriplet.atSamePosition(otherTriplet)) continue;
                // if one is {RT} and the other is {Z}, leave both
                if (oneTriplet.complements(otherTriplet)) continue;

                // remove triplet that has less components, worst instruments, or larger location codes
                if(oneTriplet.isInferiorTo(otherTriplet)) {
                    System.err.println("!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
                            oneTriplet.getName() + " ( :: " + otherTriplet.getName() + " )");
                    oneTriplet.dismiss();
                    oneTriplet.move(duplicateInstrumentPath);
                    // there is no need to keep comparing anymore
                    break;
                } else {
                    System.err.println("!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
                            otherTriplet.getName() + " ( :: " + oneTriplet.getName() + " )");
                    otherTriplet.dismiss();
                    otherTriplet.move(duplicateInstrumentPath);
                }
            }
        }

        // rename files that survived
        for (SacTriplet oneTriplet : sacTripletSet) {
            if (!oneTriplet.isDismissed()) {
                oneTriplet.rename(event.toString());
            }
        }
    }

    /**
     * @return (boolean) true if any problem has occured
     */
    private boolean check() {
        return Files.exists(invalidRespPath) || Files.exists(invalidStationPath) || Files.exists(invalidTripletPath);
    }

    /**
     * Removes directories containing intermediate files.
     */
    private void removeIntermediateFiles() {
        try {
            removeDirectory(doneMergePath.toFile());
            removeDirectory(doneModifyPath.toFile());
            removeDirectory(doneDeconvolvePath.toFile());
            removeDirectory(doneRotatePath.toFile());
            removeDirectory(unSetPath.toFile());
            removeDirectory(unMergedPath.toFile());
            removeDirectory(unModifiedPath.toFile());
            removeDirectory(unRotatedPath.toFile());
            removeDirectory(invalidStationPath.toFile());
            removeDirectory(invalidRespPath.toFile());
            removeDirectory(invalidTripletPath.toFile());
            removeDirectory(unwantedDistancePath.toFile());
            removeDirectory(unwantedCoordinatePath.toFile());
            removeDirectory(duplicateComponentPath.toFile());
            removeDirectory(duplicateInstrumentPath.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes a given directory.
     * Attempts to delete the directory is made two times before throwing an Exception.
     * This method was made because DirectoryNotEmptyException was sometimes thrown
     * even if the cleaning of the directory seemed to have succeeded and the directory was empty.
     * @param dirFile (File) Directory to remove
     * @throws Exception
     */
    private void removeDirectory (File dirFile) throws Exception {
        try {
            FileUtils.deleteDirectory(dirFile);
        } catch (Exception e) {
            Thread.sleep(1000 * 5);
            FileUtils.deleteDirectory(dirFile);
        }
    }

    /**
     * @return (boolean) true if already run
     */
    boolean hasRun() {
        return hasRun;
    }

    /**
     * @return (boolean) true if there have been any problems
     */
    boolean hadProblem() {
        return problem;
    }

    /**
     * @return (String) GCMT ID of event
     */
    String getEventID() {
        return event.getGlobalCMTID().toString();
    }

}