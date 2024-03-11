package io.github.kensuke1984.kibrary.waveform;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Utilities for reading a pair of an ID file and a waveform file created using {@link WaveformDataWriter}.
 * The files are for observed and synthetic waveforms (NOT partial).
 * <p>
 * The file contains
 * <p>(File information)</p>
 * <dl><dd>Numbers of observers, events and period ranges</dd>
 * </dl>
 * <p>(All waveforms information)</p>
 * <dl>
 * <dt>Each observer information</dt>
 * <dd>station, network, position</dd>
 * <dt>Each event</dt>
 * <dd>Global CMT ID</dd>
 * <dt>Each period range</dt>
 * <dd>min period, max period</dd>
 * </dl>
 * <p>(Each waveform information)</p>
 * <dl>
 * <dt>Each BasicID information</dt>
 * <dt>See in {@link #createID(byte[], Observer[], GlobalCMTID[], double[][], Phase[])}</dt>
 * </dl>
 *
 * <p>
 * This class also contains methods for exporting waveform data in ascii-format text files.
 * When the main method of this class is executed,
 * the input binary-format files can be exported in ascii format.
 * If desired, waveform data can be exported in txt files under the basic waveform folder.
 *
 * @since a long time ago
 * @version 2021/11/3 moved from waveformdata to waveform
 */
public final class BasicIDFile {
    private BasicIDFile() {}

    /**
     * Number of bytes used for one ID.
     */
    public static final int ONE_ID_BYTE = 48;

    public static final String ID_FILE_NAME = "basicID.dat";
    public static final String DATA_FILE_NAME = "basicData.dat";

    /**
     * Write basicIDs into ID file and data file.
     * @param basicIDs (List of {@link BasicID}) BasicIDs to write.
     * @param outPath (Path) The directory where basic ID and data files shall be created. The directory must exist.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/1/29
     */
    public static void write(List<BasicID> basicIDs, Path outPath) throws IOException {
        Files.createDirectories(outPath);
        Path outputIDPath = outPath.resolve(ID_FILE_NAME);
        Path outputDataPath = outPath.resolve(DATA_FILE_NAME);

        // extract set of observers, events, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        basicIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            boolean add = true;
            for (double[] periods : periodSet) {
                if (id.getMinPeriod() == periods[0] && id.getMaxPeriod() == periods[1])
                    add = false;
            }
            if (add)
                periodSet.add(new double[] {id.getMinPeriod(), id.getMaxPeriod()});
            for (Phase phase : id.getPhases())
                phaseSet.add(phase);
        });

        double[][] periodRanges = new double[periodSet.size()][];
        int j = 0;
        for (double[] periods : periodSet)
            periodRanges[j++] = periods;
        Phase[] phases = phaseSet.toArray(new Phase[phaseSet.size()]);

        // output
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(basicIDs.size(), "basicID", "basicIDs") + " (total of obs and syn)"
                + " in " + outPath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputDataPath, observerSet, eventSet, periodRanges, phases)) {
            for (BasicID id : basicIDs) {
                wdw.addBasicID(id);
            }
            wdw.flush();
        }
    }

    /**
     * Reads basicIDs from a basic folder.
     * @param inPath (Path) The directory containing basic ID and data files
     * @param withData (boolean) Whether to read waveform data
     * @return (List of BasicID) The basicIDs read in. Not sorted.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/1/29
     */
    public static List<BasicID> read(Path inPath, boolean withData) throws IOException {
        System.err.println("Reading basic folder: " + inPath);
        if (withData) return Arrays.asList(read(inPath.resolve(ID_FILE_NAME), inPath.resolve(DATA_FILE_NAME)));
        else return Arrays.asList(read(inPath.resolve(ID_FILE_NAME)));
    }

    /**
     * Reads both the ID file and the data file.
     * @param idPath (Path) ID file
     * @param dataPath (Path) Data file
     * @return ({@link BasicID}[]) BasicIDs containing waveform data
     * @throws IOException if an I/O error occurs
     * @deprecated (make this method private)
     */
    public static BasicID[] read(Path idPath, Path dataPath) throws IOException {
        // Read IDs
        BasicID[] ids = read(idPath);

        // Read waveforms
        System.err.print(" Reading data file ...");
        long t = System.nanoTime();
        long nptsTotal = Arrays.stream(ids).mapToLong(BasicID::getNpts).sum();
        long dataSize = Files.size(dataPath);
        if (dataSize != nptsTotal * Double.BYTES)
            throw new RuntimeException(dataPath + " is invalid for " + idPath);

        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(dataPath))) {
            byte[][] bytes = new byte[ids.length][];
            Arrays.parallelSetAll(bytes, i -> new byte[ids[i].npts * Double.BYTES]);
            for (int i = 0; i < ids.length; i++)
                bis.read(bytes[i]);
            IntStream.range(0, ids.length).parallel().forEach(i -> {
                BasicID id = ids[i];
                ByteBuffer bb = ByteBuffer.wrap(bytes[i]);
                double[] data = new double[id.npts];
                for (int j = 0; j < data.length; j++)
                    data[j] = bb.getDouble();
                ids[i] = id.withData(data);
            });
        }
        System.err.println("\r Waveform data read in " + GadgetAid.toTimeString(System.nanoTime() - t));
        return ids;
    }

    /**
     * Reads only the ID file (and not the data file).
     * @param idPath (Path) ID file
     * @return ({@link BasicID}[]) BasicIDs without waveform data
     * @throws IOException if an I/O error occurs
     * @deprecated (make this method private)
     */
    public static BasicID[] read(Path idPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
            System.err.print(" Reading ID file ...");
            long t = System.nanoTime();
            long fileSize = Files.size(idPath);

            // Read header
            // short * 4
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            double[][] periodRanges = new double[dis.readShort()][2];
            Phase[] phases = new Phase[dis.readShort()];
            // calculate number of bytes in header
            int headerBytes = Short.BYTES * 4 + (Observer.MAX_LENGTH + Double.BYTES * 2) * observers.length
                    + GlobalCMTID.MAX_LENGTH * events.length + 16 * phases.length
                    + Double.BYTES * 2 * periodRanges.length;
            long idParts = fileSize - headerBytes;
            if (idParts % ONE_ID_BYTE != 0)
                throw new IllegalStateException(idPath + " is invalid.");

            byte[] observerBytes = new byte[Observer.MAX_LENGTH + Double.BYTES * 2];
            for (int i = 0; i < observers.length; i++) {
                dis.read(observerBytes);
                observers[i] = Observer.createObserver(observerBytes);
            }
            byte[] eventBytes = new byte[GlobalCMTID.MAX_LENGTH];
            for (int i = 0; i < events.length; i++) {
                dis.read(eventBytes);
                events[i] = new GlobalCMTID(new String(eventBytes).trim());
            }
            for (int i = 0; i < periodRanges.length; i++) {
                periodRanges[i][0] = dis.readDouble();
                periodRanges[i][1] = dis.readDouble();
            }
            byte[] phaseBytes = new byte[16];
            for (int i = 0; i < phases.length; i++) {
                dis.read(phaseBytes);
                phases[i] = Phase.create(new String(phaseBytes).trim());
            }

            // Read IDs
            int nid = (int) (idParts / ONE_ID_BYTE);
            byte[][] bytes = new byte[nid][ONE_ID_BYTE];
            for (int i = 0; i < nid; i++)
                dis.read(bytes[i]);
            BasicID[] ids = new BasicID[nid];
            IntStream.range(0, nid).parallel().forEach(i -> {
                ids[i] = createID(bytes[i], observers, events, periodRanges, phases);
            });
            System.err.println("\r " + ids.length + " IDs read in " + GadgetAid.toTimeString(System.nanoTime() - t));
            return ids;
        }
    }

    /**
     * Method for reading the actual ID part.
     * <p>
     * An ID information contains<br>
     * obs or syn(1)<br>
     * observer number(2)<br>
     * event number(2)<br>
     * component(1)<br>
     * period range(1) <br>
     * phases numbers(10*2)<br>
     * start time(4)<br>
     * number of points(4)<br>
     * sampling hz(4) <br>
     * convolved (or observed) or not(1)<br>
     * position of a waveform for the ID in the data file(8)
     *
     * @param bytes (byte[]) Input data for one ID
     * @param observers ({@link Observer}[]) Set of observers contained in dataset
     * @param events ({@link GlobalCMTID}[]) Set of events contained in dataset
     * @param periodRanges (double[][]) Set of period ranges contained in dataset
     * @param phases ({@link Phase}[]) Set of phases contained in dataset
     * @return ({@link BasicID}) Created ID
     */
    private static BasicID createID(byte[] bytes, Observer[] observers, GlobalCMTID[] events, double[][] periodRanges, Phase[] phases) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        WaveformType type = 0 < bb.get() ? WaveformType.OBS : WaveformType.SYN;
        Observer station = observers[bb.getShort()];
        GlobalCMTID event = events[bb.getShort()];
        SACComponent component = SACComponent.getComponent(bb.get());
        double[] period = periodRanges[bb.get()];
        Set<Phase> tmpset = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            short iphase = bb.getShort();
            if (iphase != -1)
                tmpset.add(phases[iphase]);
        }
        Phase[] usablephases = new Phase[tmpset.size()];
        usablephases = tmpset.toArray(usablephases);
        double startTime = bb.getFloat();
        int npts = bb.getInt();
        double samplingHz = bb.getFloat();
        boolean isConvolved = 0 < bb.get();
        // startByte is read, but not used
        long startByte = bb.getLong();
        return new BasicID(type, samplingHz, startTime, npts, station, event, component, period[0], period[1],
                usablephases, isConvolved);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Exports binary files in ascii format.
     * @param args Options.
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();
        //input
        options.addOption(Option.builder("b").longOpt("basic").hasArg().argName("basicFolder")
                .desc("The input basic waveform folder (.)").build());
        options.addOption(Option.builder("w").longOpt("waveform")
                .desc("Export waveforms in event directories under the input basic waveform folder").build());
        // output
        options.addOption(Option.builder("n").longOpt("number")
                .desc("Just count number without creating output files").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Specify path of output file.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path basicPath = cmdLine.hasOption("b") ? Paths.get(cmdLine.getOptionValue("b")) : Paths.get(".");

        // read input
        List<BasicID> ids;
        if (cmdLine.hasOption("w")) {
            ids = read(basicPath, true);
            if (cmdLine.hasOption("n")) return;
            outputWaveformTxts(ids, basicPath);
        } else {
            ids = read(basicPath, false);
            if (cmdLine.hasOption("n")) return;
        }

        // set output
        Path outputIdsPath;
        if (cmdLine.hasOption("o")) {
            outputIdsPath = Paths.get(cmdLine.getOptionValue("o"));
        } else {
            outputIdsPath = Paths.get("basicID.txt");
        }

        // output
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
            pw.println("#station, network, lat, lon, event, component, type, startTime, npts, samplingHz, "
                    + "minPeriod, maxPeriod, phases, convolved");
            ids.forEach(pw::println);
        }
    }

    /**
     * Outputs waveform data for each event-observer pair into txt files.
     *
     * @param ids
     * @throws IOException
     */
    public static void outputWaveformTxts(List<BasicID> ids, Path basicPath) throws IOException {

        BasicIDPairUp pairer = new BasicIDPairUp(ids);
        List<BasicID> obsList = pairer.getObsList();
        List<BasicID> synList = pairer.getSynList();

        Set<GlobalCMTID> events = obsList.stream().map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());

        for (GlobalCMTID event : events) {

            // create event directory under basicPath
            Path eventPath = basicPath.resolve(event.toString());
            Files.createDirectories(eventPath);

            for (int i = 0; i < obsList.size(); i++) {
                if (obsList.get(i).getGlobalCMTID().equals(event)) {
                    BasicID obsID = obsList.get(i);
                    BasicID synID = synList.get(i);

                    outputWaveformTxt(eventPath, obsID, synID);
                }
            }
        }
    }

    /**
     * Writes data of a given pair of observed and synthetic waveforms into a text file.
     * @param eventPath (Path) The event directory in which to create the text file
     * @param obsID
     * @param synID
     * @throws IOException
     */
    public static void outputWaveformTxt(Path eventPath, BasicID obsID, BasicID synID) throws IOException {
        double[] obsData = obsID.getData();
        double[] synData = synID.getData();
        double obsStartTime = obsID.getStartTime();
        double synStartTime = synID.getStartTime();
        double obsSamplingHz = obsID.getSamplingHz();
        double synSamplingHz = synID.getSamplingHz();

        Path outputPath = eventPath.resolve(getWaveformTxtFileName(obsID));

        try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
            for (int j = 0; j < obsData.length; j++) {
                double obsTime = obsStartTime + j / obsSamplingHz;
                double synTime = synStartTime + j / synSamplingHz;
                pwTrace.println(obsTime + " " + obsData[j] + " " + synTime + " " + synData[j]);
            }
        }
    }

    /**
     * The name of text file which is to contain waveform data. TODO there may be multiple timewindows for a dataEntry
     * @param oneID
     * @return
     */
    public static String getWaveformTxtFileName(BasicID oneID) {
        return oneID.getObserver() + "." + oneID.getGlobalCMTID() + "." + oneID.getSacComponent() + ".txt";
    }

}
