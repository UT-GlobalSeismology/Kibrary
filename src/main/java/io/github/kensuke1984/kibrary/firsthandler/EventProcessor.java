package io.github.kensuke1984.kibrary.firsthandler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.entrance.RespDataFile;
import io.github.kensuke1984.kibrary.external.ExternalProcess;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Class for creating a dataset, for one event, that can be used in the inversion process.
 * An event directory with a "sac" folder containing SAC files and a "resp" folder containing RESP files must be given as input.
 * Input SAC file names must be formatted (ex. "IU.MAJO.00.BH2.M.2014.202.14.44.00.000.SAC").
 * <p>
 * SAC files will be deconvolved of instrumental response, and rotated to gain radial and transverse components.
 * Selection for the station coordinate and epicentral distance will be done based on the user's specifications.
 * <p>
 * This class requires that evalresp and sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/>evalresp</a> and
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/sac/>SAC</a> can be found at IRIS.
 * <p>
 * TODO: The sac "cut b n" command in SacModifier fails if the sac version is too new (102.0 and later?).
 *
 * @since 2021/09/14
 * @author otsuru
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
     * Path of the input folder containing SAC files.
     */
    private final Path inputSacSetPath;
    /**
     * Path of the input folder containing RESP files.
     */
    private final Path inputRespSetPath;
    /**
     * Path of the output event folder.
     */
    private final Path outputPath;

    /**
     * GlobalCMTData for the event in the seedfile.
     */
    private GlobalCMTAccess event;
    /**
     * true: the base time will be PDE time, false: CMT (default)
     */
    private boolean byPDE = false;
    /**
     * Whether this class has finished running.
     */
    private boolean hasRun = false;
    /**
     * true: exception has occurred, false: not (or not yet)
     */
    private boolean problem = false;

    /**
     * Epicentral distance range of SAC files to be output.
     */
    private LinearRange distanceRange = new LinearRange("Distance", 0.0, 180.0);
    private LinearRange latitudeRange = new LinearRange("Latitude", -90.0, 90.0);
    private CircularRange longitudeRange = new CircularRange("Longitude", -180.0, 180.0);

    /**
     * Threshold to judge which stations are in the same position [deg].
     */
    private double coordinateGrid = 0.01;
    /**
     * The maximum length of output time series.
     */
    private double maxTlen;
    /**
     * Whether to remove intermediate files.
     */
    private boolean removeIntermediateFiles = true;

    private Path doneMergePath;
    private Path doneModifyPath;
    private Path doneDeconvolvePath;
    private Path doneRotatePath;
    private Path unMergedPath;
    private Path unModifiedPath;
    private Path unRotatedPath;
    private Path invalidRespPath;
    private Path unwantedDistancePath;
    private Path duplicateComponentPath;
    private Path duplicateInstrumentPath;

    private PrintWriter eliminatedWriter;
    private Path eliminatedLogPath;

    /**
     * Constructor that specifies the input and output for this class.
     *
     * @param eventDir (EventFolder) The input event folder containing necessary SAC, STATION, and RESP files.
     * @param outPath (Path) The path where the ouput event directory should be made under.
     */
    EventProcessor(EventFolder eventDir, Path outPath) {
        inputSacSetPath = eventDir.toPath().resolve("sac");
        inputRespSetPath = eventDir.toPath().resolve("resp");
        outputPath = outPath.resolve(eventDir.getName());

        event = eventDir.getGlobalCMTID().getEventData();

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

        duplicateInstrumentPath = outputPath.resolve("duplicateInstrument");

        eliminatedLogPath = outputPath.resolve("eliminated.log");
    }

    /**
     * Sets parameters.
     *
     * @param distanceRange ({@link LinearRange}) Epicentral distance range.
     * @param latitudeRange ({@link LinearRange}) Latitude range.
     * @param longitudeRange ({@link CircularRange}) Longitude range.
     * @param grid (double) Threshold to judge which stations are in the same position.
     * @param maxTlen (double) The maximum length of output time series.
     * @param remove (boolean) If this is true, then all intermediate files will be removed at the end.
     */
    void setParameters(LinearRange distanceRange, LinearRange latitudeRange, CircularRange longitudeRange, double grid, double maxTlen, boolean remove) {
        this.distanceRange = distanceRange;
        this.latitudeRange = latitudeRange;
        this.longitudeRange = longitudeRange;
        this.coordinateGrid = grid;
        this.maxTlen = maxTlen;
        this.removeIntermediateFiles = remove;
    }

    @Override
    public void run() {

        try {
            // create event directory
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // This block (above) was seperated from the next block because the following BufferedWriter cannot be created
        //  when the event directory does not yet exist.

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(eliminatedLogPath, StandardOpenOption.CREATE_NEW))) {
            eliminatedWriter = pw;

            try {
                // select, copy, and set up SAC files
                setupSacs();
                // merge segmented SAC files
                mergeSacSegments();
                // remove trend, zero-pad, and cut SAC files
                modifySacs();
                // instrumentation function deconvolution
                deconvolveSacs();
                // rotation ((.N,.E) & (.1,.2) -> (.R,.T))
                rotate();
                // fix Z component SAC files
                fixZComponentSacs();
                // eliminating duplicate instruments and close stations
                // this is done after everything else so that we don't lose usable data (ex. if we choose an unrotatable triplet)
                duplicationElimination();

                if (removeIntermediateFiles) removeIntermediateFiles();

                problem = check();

                hasRun = true;

                GadgetAid.dualPrintln(eliminatedWriter, "** " + event.getGlobalCMTID() + " finished");

            } catch (Exception e) {
                // If anything wrong happens, export it in both standard error AND the log file.
                // This is written inside the try-with-resources block because the PrintWriter will close after finishing its try block
                e.printStackTrace();
                eliminatedWriter.println(ExceptionUtils.getStackTrace(e));
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    /**
     * This method sets up the SAC files to be used by carrying out the following:
     * <ul>
     * <li> check whether the channel is supported by this class; if not, skip </li>
     * <li> check whether the location is acceptable; if not, display warning </li>
     * <li> check whether the epicentral distance is within the wanted range; if not, skip </li>
     * <li> check whether the station coordinate is within the wanted range; if not, skip </li>
     * <li> if the station coordinate is (0,0), skip </li>
     * <li> check whether the dip value of the channel is valid; if not, skip </li>
     * <li> copy SAC file from the input directory to the output event directory with a new file name </li>
     * <li> interpolate the data with DELTA (which is currently 0.05 sec thus 20 Hz) </li>
     * </ul>
     * @throws IOException
     * @author Keisuke Otsuru
     */
    private void setupSacs() throws IOException {

        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(inputSacSetPath, "*.SAC")) {
            for (Path rawSacPath : sacPaths) {
                // rawSacPaths are paths of SAC files in the input directory; DO NOT MODIFY THEM

                SacFileName sacFile = new SacFileName(rawSacPath.getFileName().toString());

                // check channel validity
                if (!checkChannel(sacFile.getChannel())) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! unsupported channel : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // check location validity -> just display warning, but process the file nonetheless
                // TODO: this may have to be modified or removed
                if (!checkLocation(sacFile.getLocation())) {
                    GadgetAid.dualPrintln(eliminatedWriter, "?? may be untrustworthy location : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    // continue; <- this file will not be skipped
                }

                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(rawSacPath);
                double distance = Double.parseDouble(headerMap.get(SACHeaderEnum.GCARC));
                double latitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLA));
                double longitude = Double.parseDouble(headerMap.get(SACHeaderEnum.STLO));
                double inclination = Double.parseDouble(headerMap.get(SACHeaderEnum.CMPINC));
                HorizontalPosition position = new HorizontalPosition(latitude, longitude);

                // check epicentral distance
                if (!distanceRange.check(distance)) {
                    // this is not written in standard error because it is too noisy
                    eliminatedWriter.println("!! unwanted epicentral distance : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // check station coordinate
                if (!position.isInRange(latitudeRange, longitudeRange)) {
                    // this is not written in standard error because it is too noisy
                    eliminatedWriter.println("!! unwanted station coordinate : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // reject stations at (0,0) <- these are most likely stations that do not have correct coordinates written in.
                if (position.equals(new HorizontalPosition(0.0, 0.0))) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! rejecting station at coordinate (0,0) : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // check whether the inclination of a [vertical|horizontal] channel is perfectly [vertical|horizontal]
                // caution: up is inc=0, down is inc=180
                // TODO: are there stations with downwards Z ?
                if ((isVerticalChannel(sacFile.getChannel()) && Precision.equals(inclination, 0, 0.01) == false)
                        || (!isVerticalChannel(sacFile.getChannel()) && Precision.equals(inclination, 90, 0.01) == false)) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! invalid inclination : " + event.getGlobalCMTID() + " - " + sacFile.toString());
                    // no need to move files to trash, because nothing is copied yet
                    continue;
                }

                // copy SAC file from the input directory to the output event directory; file name changed here
                Path newSacPath = outputPath.resolve(sacFile.getSetFileName());
                Files.copy(rawSacPath, newSacPath);

                // interpolate data with DELTA
                fixDelta(newSacPath);
            }
        }

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
     * Sets SAC headers related to stations via StationInformation file,
     * and also interpolates SAC file with DELTA (which is currently 0.05 sec thus 20 Hz).
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sif (StationInformationFile) Station information file.
     * @param blankLocation (boolean) true if the location is blank
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
     * This method merges multiple SAC files that are supposed to be part of the same waveform.
     * This is done through {@link SegmentedSacMerger}.
     * Successful files are put in "doneMerge" and the others in "unMerged".
     *
     * @throws IOException
     */
    private void mergeSacSegments() throws IOException {
        SegmentedSacMerger s = new SegmentedSacMerger(outputPath, doneMergePath, unMergedPath, eliminatedWriter);
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
     * </ul>
     * Successful files are put in "doneModify", while files that failed to be zero-padded go in "unModified".
     * @throws IOException
     */
    private void modifySacs() throws IOException {

        try (DirectoryStream<Path> sacPathStream = Files.newDirectoryStream(outputPath, "*.MRG")) {
            for (Path sacPath : sacPathStream) {
                SacModifier sm = new SacModifier(event, sacPath, byPDE);

                // check whether the file can be zero-padded
                if (!sm.canBeZeroPadded()) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! start time is too late : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    FileAid.moveToDirectory(sacPath, unModifiedPath, true);
                    continue;
                }

                // check whether the file can be trimmed
                if (!sm.canBeTrimmed()) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! end time is before event time : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    FileAid.moveToDirectory(sacPath, unModifiedPath, true);
                    continue;
                }

                // remove trends in SAC files; output in file with new name .MRG > .MOD
                sm.removeTrend();

                // check whether the waveform has non-zero data
                // This is after removeTrend() because we also want to remove constant-value data.
                if (sm.isCompleteZero()) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! waveform is 0 or NaN : " + event.getGlobalCMTID() + " - " + sacPath.getFileName());
                    FileAid.moveToDirectory(sacPath, unModifiedPath, true);
                    FileAid.moveToDirectory(sm.getModifiedPath(), unModifiedPath, true);
                    continue;
                }

                // zero-pad SAC files that start after the event time
                sm.zeroPad();

                // SAC start time is set to the event time, and the SAC file is cut so that npts = 2^n
                sm.trim((int) Math.ceil(maxTlen * SAMPLING_HZ));

                // move SAC files after treatment into the merged folder
                FileAid.moveToDirectory(sacPath, doneModifyPath, true);
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
    private void deconvolveSacs() throws IOException {

        try (DirectoryStream<Path> eventDirStream = Files.newDirectoryStream(outputPath, "*.MOD")) {
            for (Path modPath : eventDirStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modPath);

                SacFileName modFile = new SacFileName(modPath.getFileName().toString());
                String afterName = modFile.getDeconvolvedFileName();
                Path afterPath = outputPath.resolve(afterName);

                RespDataFile respFile = new RespDataFile(modFile.getNetwork(), modFile.getStation(), modFile.getLocation(), modFile.getChannel());
                Path respPath = inputRespSetPath.resolve(respFile.getRespFile());
                Path spectraPath = outputPath.resolve(respFile.getSpectraFile());

                //System.out.println("deconvolute: "+ afterPath); // 4debug

                // on duplication of channel E&1 or N&2, choose E,N over 1,2 (otherwise, E&2 or 1&N may survive)
                if (Files.exists(afterPath)) {
                    if (modFile.getComponent().equals("N") || modFile.getComponent().equals("E")) {
                        GadgetAid.dualPrintln(eliminatedWriter, "!! duplicate (E&1 or N&2) component : " + event.getGlobalCMTID() + " - " + afterName);
                        // throw the pre-existing .X or .Y to duplicateComponentPath (because that one should have been .1 or .2)
                        FileAid.moveToDirectory(afterPath, duplicateComponentPath, true);
                        // keep handling this current modFile
                    } else {
                        GadgetAid.dualPrintln(eliminatedWriter, "!! duplicate (E&1 or N&2) component : " + event.getGlobalCMTID() + " - " + afterName);
                        // throw *.MOD files to duplicateComponentPath (because this one is .1 or .2)
                        FileAid.moveToDirectory(modPath, duplicateComponentPath, true);
                        continue;
                    }
                }

                // run evalresp
                // If it fails, throw MOD files to trash
                try {
                    if (!runEvalresp(headerMap, respPath)) {
                        GadgetAid.dualPrintln(eliminatedWriter, "!! evalresp failed : " + event.getGlobalCMTID() + " - " + afterName);
                        // throw MOD.* files which cannot produce SPECTRA to trash
                        FileAid.moveToDirectory(modPath, invalidRespPath, true);
                        continue;
                    }
                } catch (IOException e) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! evalresp failed : " + event.getGlobalCMTID() + " - " + afterName);
                    e.printStackTrace();
                    // throw MOD.* files which cannot produce SPECTRA to trash
                    FileAid.moveToDirectory(modPath, invalidRespPath, true);
                    continue;
                }
                // spectra file should be created by evalresp; if not, throw away the MOD file
                if(!Files.exists(spectraPath)) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! spectra file not created : " + event.getGlobalCMTID() + " - " + afterName);
                    // throw MOD.* files which cannot produce SPECTRA to trash
                    FileAid.moveToDirectory(modPath, invalidRespPath, true);
                    continue;
                }

                int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));

                SacDeconvolution sd = new SacDeconvolution(modPath, spectraPath, afterPath, SAMPLING_HZ / npts, SAMPLING_HZ);

                // execute deconvolution
                try {
                    sd.compute();
                } catch (IOException e) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! deconvolution failed : " + event.getGlobalCMTID() + " - " + afterName);
                    e.printStackTrace();
                    // throw *.MOD files to trash
                    FileAid.moveToDirectory(modPath, invalidRespPath, true);
                    // throw SPECTRA files to trash
                    FileAid.moveToDirectory(spectraPath, invalidRespPath, true);
                    continue;
                }

                if(sd.isNaN()) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! spectra file is NAN or empty : " + event.getGlobalCMTID() + " - " + afterName);
                    FileAid.moveToDirectory(modPath, invalidRespPath, true);
                    FileAid.moveToDirectory(spectraPath, invalidRespPath, true);
                    continue;
                }

                // move processed SPECTRA files to archive
                FileAid.moveToDirectory(spectraPath, doneDeconvolvePath, true);

                // move processed MOD files to archive
                FileAid.moveToDirectory(modPath, doneDeconvolvePath, true);
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
     * @return (boolean) true if success
     * @throws IOException
     */
    private boolean runEvalresp(Map<SACHeaderEnum, String> headerMap, Path inputPath) throws IOException {
        int npts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));
        double minFreq = SAMPLING_HZ / npts;
        String command =
                "evalresp " + headerMap.get(SACHeaderEnum.KSTNM) + " " + headerMap.get(SACHeaderEnum.KCMPNM) + " " +
                        event.getCMTTime().getYear() + " " + event.getCMTTime().getDayOfYear() + " " + minFreq + " " +
                        SAMPLING_HZ + " " + headerMap.get(SACHeaderEnum.NPTS) +
                        " -n " + headerMap.get(SACHeaderEnum.KNETWK) + " -l " + headerMap.get(SACHeaderEnum.KHOLE) +
                        " -f " + inputPath.toAbsolutePath() +
                        " -s lin -r cs -u vel";

        ExternalProcess xProcess = ExternalProcess.launch(command, outputPath);
        return xProcess.waitFor() == 0;
    }

    /**
     * Converts/rotates all files with (.X, .Y) to (.R, .T).
     * Successful files are put in "doneRotate" the others in "unRotated".
     * @throws IOException
     */
    private void rotate() throws IOException {

        try (DirectoryStream<Path> xStream = Files.newDirectoryStream(outputPath, "*.X")) {
            for (Path xPath : xStream) {
                SacFileName xFile = new SacFileName(xPath.getFileName().toString());
                Path yPath = outputPath.resolve(xFile.getNameWithComponent("Y"));
                Path rPath = outputPath.resolve(xFile.getNameWithComponent("R"));
                Path tPath = outputPath.resolve(xFile.getNameWithComponent("T"));

                // throw away .X file if its pair .Y file does not exist
                if (!Files.exists(yPath)) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! pair .Y file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + xFile.toString());
                    FileAid.moveToDirectory(xPath, unRotatedPath, true);
                    continue;
                }
                boolean rotated = SACUtil.rotate(xPath, yPath, rPath, tPath);
                if (rotated) {
                    FileAid.moveToDirectory(xPath, doneRotatePath, true);
                    FileAid.moveToDirectory(yPath, doneRotatePath, true);
                } else {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! rotate failed : " + event.getGlobalCMTID() + " - " + xFile.toString());
                    FileAid.moveToDirectory(xPath, unRotatedPath, true);
                    FileAid.moveToDirectory(yPath, unRotatedPath, true);
                }
            }
        }

        // If there are files (.Y) which had no pairs (.X), move them to trash
        try (DirectoryStream<Path> yPaths = Files.newDirectoryStream(outputPath, "*.Y")) {
            for (Path yPath : yPaths) {
                GadgetAid.dualPrintln(eliminatedWriter, "!! pair .X file unfound, unable to rotate : " + event.getGlobalCMTID() + " - " + yPath.getFileName());
                FileAid.moveToDirectory(yPath, unRotatedPath, true);
            }
        }
    }

    /**
     * Changes the KCMPNM of vertical component SAC files to "Z".
     * @throws IOException
     */
    private void fixZComponentSacs() throws IOException {
        try (DirectoryStream<Path> zStream = Files.newDirectoryStream(outputPath, "*.Z")) {
            for (Path zPath : zStream) {
                Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(zPath);
                double[] sacdata = SACUtil.readSACData(zPath);

                headerMap.put(SACHeaderEnum.KCMPNM, SACComponent.Z.toString());
                SACUtil.writeSAC(zPath, headerMap, sacdata);
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

        // triplets should consist of either {RTZ}, {RT}, or {Z}
        for (SacTriplet oneTriplet : sacTripletSet) {
            if (!oneTriplet.checkValidity()) {
                throw new IllegalStateException("!!! incomplete triplet : " + event.getGlobalCMTID() + " - " + oneTriplet.getName());
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
                    GadgetAid.dualPrintln(eliminatedWriter, "!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
                            oneTriplet.getName() + " ( :: " + otherTriplet.getName() + " )");
                    oneTriplet.dismiss();
                    oneTriplet.move(duplicateInstrumentPath);
                    // there is no need to keep comparing anymore
                    break;
                } else {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! same or close station, eliminating : " + event.getGlobalCMTID() + " - " +
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
        return Files.exists(invalidRespPath);
    }

    /**
     * Removes directories containing intermediate files.
     * @throws IOException
     */
    private void removeIntermediateFiles() throws IOException {
        removeDirectory(doneMergePath.toFile());
        removeDirectory(doneModifyPath.toFile());
        removeDirectory(doneDeconvolvePath.toFile());
        removeDirectory(doneRotatePath.toFile());
        removeDirectory(unMergedPath.toFile());
        removeDirectory(unModifiedPath.toFile());
        removeDirectory(unRotatedPath.toFile());
        removeDirectory(invalidRespPath.toFile());
        removeDirectory(unwantedDistancePath.toFile());
        removeDirectory(duplicateComponentPath.toFile());
        removeDirectory(duplicateInstrumentPath.toFile());
    }

    /**
     * Deletes a given directory.
     * Attempts to delete the directory is made two times before throwing an IOException.
     * This method was made because DirectoryNotEmptyException was sometimes thrown
     * even if the cleaning of the directory seemed to have succeeded and the directory was empty.
     * @param dirFile (File) Directory to remove
     * @throws IOException
     */
    private void removeDirectory (File dirFile) throws IOException {

        try {
            FileUtils.deleteDirectory(dirFile);
        } catch (IOException e1) {
            // wait
            try {
                Thread.sleep(1000 * 5);
            } catch (InterruptedException e2) {
                // InterruptedException means that someone wants the current thread to stop,
                // but the 'interrupted' flag is reset when InterruptedException is thrown in sleep(),
                // so the flag should be set back up.
                // Then, throw RuntimeException to halt the program.
                Thread.currentThread().interrupt();
                throw new RuntimeException(e2);
            }

            // retry
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