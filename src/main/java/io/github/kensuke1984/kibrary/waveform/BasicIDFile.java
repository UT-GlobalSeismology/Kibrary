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
 *
 * @since a long time ago
 * @version 2021/11/3 moved from waveformdata to waveform
 */
public final class BasicIDFile {
    private BasicIDFile() {}

    /**
     * [byte] File size for an ID
     */
    public static final int ONE_ID_BYTE = 48;

    /**
     * Write basicIDs into ID file and waveform file.
     * @param basicIDs
     * @param outputIDPath
     * @param outputWavePath
     * @throws IOException
     *
     * @author otsuru
     */
    public static void write(List<BasicID> basicIDs, Path outputIDPath, Path outputWavePath) throws IOException {

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
                + MathAid.switchSingularPlural(basicIDs.size(), "basicID", "basicIDs") + " (total of obs and syn)");
        System.err.println(" in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath, observerSet, eventSet, periodRanges, phases)) {
            for (BasicID id : basicIDs) {
                wdw.addBasicID(id);
            }
        }
    }

    /**
     * Reads both the ID file and the waveform file.
     * @param idPath (Path) An ID file, if it does not exist, an IOException
     * @param dataPath (Path) A data file, if it does not exist, an IOException
     * @return Array of {@link BasicID} containing waveform data
     * @throws IOException if an I/O error occurs
     */
    public static BasicID[] read(Path idPath, Path dataPath) throws IOException {
        // Read IDs
        BasicID[] ids = read(idPath);

        // Read waveforms
        long t = System.nanoTime();
        long dataSize = Files.size(dataPath);
        BasicID lastID = ids[ids.length - 1];
        if (dataSize != lastID.startByte + lastID.npts * 8)
            throw new RuntimeException(dataPath + " is invalid for " + idPath);
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(dataPath))) {
            byte[][] bytes = new byte[ids.length][];
            Arrays.parallelSetAll(bytes, i -> new byte[ids[i].npts * 8]);
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
        System.err.println(" Basic waveforms read in " + GadgetAid.toTimeString(System.nanoTime() - t));
        return ids;
    }

    /**
     * Reads only the ID file (and not the waveform file).
     * @param idPath (Path) An ID file, if it does not exist, an IOException
     * @return Array of {@link BasicID} without waveform data
     * @throws IOException if an I/O error occurs
     */
    public static BasicID[] read(Path idPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
            System.err.println("Reading basicID file: " + idPath);
            long t = System.nanoTime();
            long fileSize = Files.size(idPath);

            // Read header
            // short * 4
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            double[][] periodRanges = new double[dis.readShort()][2];
            Phase[] phases = new Phase[dis.readShort()];
            // calculate number of bytes in header
            int headerBytes = 2 * 4 + (8 + 8 + 8 * 2) * observers.length + 15 * events.length
                    + 16 * phases.length + 8 * 2 * periodRanges.length;
            long idParts = fileSize - headerBytes;
            if (idParts % ONE_ID_BYTE != 0)
                throw new RuntimeException(idPath + " is invalid.");
            // station(8),network(8),position(8*2)
            byte[] observerBytes = new byte[32];
            for (int i = 0; i < observers.length; i++) {
                dis.read(observerBytes);
                observers[i] = Observer.createObserver(observerBytes);
            }
            // eventID(15)
            byte[] eventBytes = new byte[15];
            for (int i = 0; i < events.length; i++) {
                dis.read(eventBytes);
                events[i] = new GlobalCMTID(new String(eventBytes).trim());
            }
            // period(8*2)
            for (int i = 0; i < periodRanges.length; i++) {
                periodRanges[i][0] = dis.readDouble();
                periodRanges[i][1] = dis.readDouble();
            }
            // phase(16)
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
            System.err.println(" " + ids.length + " basicIDs are read in " + GadgetAid.toTimeString(System.nanoTime() - t));
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
     * convoluted(or observed) or not(1)<br>
     * position of a waveform for the ID in the datafile(8)
     *
     * @param bytes
     *            for one ID
     * @return an ID written in the bytes
     */
    private static BasicID createID(byte[] bytes, Observer[] stations, GlobalCMTID[] events, double[][] periodRanges, Phase[] phases) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        WaveformType type = 0 < bb.get() ? WaveformType.OBS : WaveformType.SYN;
        Observer station = stations[bb.getShort()];
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
        double startTime = bb.getFloat(); // starting time
        int npts = bb.getInt(); // データポイント数
        double samplingHz = bb.getFloat();
        boolean isConvolved = 0 < bb.get();
        long startByte = bb.getLong();
        BasicID bid = new BasicID(type, samplingHz, startTime, npts, station, event, component, period[0], period[1],
                usablephases, startByte, isConvolved);
        return bid;
    }

    /**
     * Exports data files in ascii format.
     *
     * @param args [option]
     * <ul>
     * <li> [-i IDFile] : exports ID file in standard output</li>
     * <li> [-w IDFile WaveformFile] : exports waveforms in event directories under current path</li>
     * </ul>
     * You must specify one or the other.
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
        options.addOption(Option.builder("i").longOpt("id").hasArg().argName("basicIDFile").required()
                .desc("Export content of basic ID file").build());
        options.addOption(Option.builder("w").longOpt("waveform").hasArg().argName("basicFile")
                .desc("Export waveforms in event directories under current path").build());
        // output
        options.addOption(Option.builder("n").longOpt("number")
                .desc("Just count number without creating output files").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        // read input
        BasicID[] ids;
        if (cmdLine.hasOption("w")) {
            ids = read(Paths.get(cmdLine.getOptionValue("i")), Paths.get(cmdLine.getOptionValue("w")));
            if (cmdLine.hasOption("n")) return;
            outputWaveforms(ids);
        } else {
            ids = read(Paths.get(cmdLine.getOptionValue("i")));
            if (cmdLine.hasOption("n")) return;
        }

        // set output
        Path outputIdsPath;
        if (cmdLine.hasOption("o")) {
            outputIdsPath = Paths.get(cmdLine.getOptionValue("o"));
        } else {
            // set the output file name the same as the input, but with extension changed to txt
            String idFileName = Paths.get(cmdLine.getOptionValue("i")).getFileName().toString();
            outputIdsPath = Paths.get(idFileName.substring(0, idFileName.lastIndexOf('.')) + ".txt");
        }

        // output
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
            pw.println("#station, network, lat, lon, event, component, type, startTime, npts, "
                    + "samplingHz, minPeriod, maxPeriod, phases, startByte, convolved");
            Arrays.stream(ids).forEach(pw::println);
        }
    }

    /**
     * Outputs waveform data for each event-observer pair into txt files.
     *
     * @param ids
     * @throws IOException
     */
    private static void outputWaveforms(BasicID[] ids) throws IOException {

        BasicIDPairUp pairer = new BasicIDPairUp(ids);
        List<BasicID> obsList = pairer.getObsList();
        List<BasicID> synList = pairer.getSynList();

        Set<GlobalCMTID> events = obsList.stream().map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());

        for (GlobalCMTID event : events) {

            // create event directory under current path
            Path eventPath = Paths.get(event.toString());
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
     * The name of text file which is to contain waveform data.
     * @param oneID
     * @return
     */
    public static String getWaveformTxtFileName(BasicID oneID) {
        return oneID.getObserver() + "." + oneID.getGlobalCMTID() + "." + oneID.getSacComponent() + ".txt";
    }

}
