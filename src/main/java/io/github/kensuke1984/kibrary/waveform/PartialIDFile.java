package io.github.kensuke1984.kibrary.waveform;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * Utilities for a pair of an ID file and a waveform file. <br>
 * The files are for partial waveforms.
 * <p>
 * The file contains<br>
 * Numbers of observers, events, period ranges and perturbation points<br>
 * Each observer information <br>
 * - station, network, position <br>
 * Each event <br>
 * - Global CMT ID Each period<br>
 * Each period range<br>
 * - min period, max period<br>
 * Each perturbation points<br>
 * - latitude, longitude, radius<br>
 * Each PartialID information<br>
 * - see in {@link #read(Path)}<br>
 *
 * @author Kensuke Konishi
 * @since version 0.3.2
 */
public final class PartialIDFile {
    private PartialIDFile() {}

    /**
     * [byte] File size for an ID
     */
    public static final int oneIDByte = 50;

    public static final String ID_FILE_NAME = "partialID.dat";
    public static final String DATA_FILE_NAME = "partialData.dat";

    /**
     * Write partialIDs into ID file and data file.
     * @param partialIDs (List of PartialID)
     * @param outPath (Path) The directory where partial ID and data files shall be created. The directory must exist.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/1/29
     */
    public static void write(List<PartialID> partialIDs, Path outPath) throws IOException {
        Files.createDirectories(outPath);
        Path outputIDPath = outPath.resolve(ID_FILE_NAME);
        Path outputDataPath = outPath.resolve(DATA_FILE_NAME);

        // extract set of observers, events, voxels, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<FullPosition> voxelPositionSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        partialIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            voxelPositionSet.add(id.getVoxelPosition());
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
        System.err.println("Outputting in " + outPath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputDataPath,
                observerSet, eventSet, periodRanges, phases, voxelPositionSet)) {
            for (PartialID id : partialIDs) {
                if (id.getWaveformType().equals(WaveformType.PARTIAL) == false) {
                    throw new IllegalStateException(id.toString() + "is not a partial, it is a " + id.getWaveformType().toString());
                }
                wdw.addPartialID(id);
            }
        }
    }

    /**
     * Write partialIDs into ID file and waveform file.
     * @param partialIDs
     * @param outputIDPath
     * @param outputWavePath
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/8/11
     * @deprecated
     */
    public static void write(List<PartialID> partialIDs, Path outputIDPath, Path outputWavePath) throws IOException {

        // extract set of observers, events, voxels, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<FullPosition> voxelPositionSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        partialIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            voxelPositionSet.add(id.getVoxelPosition());
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
        System.err.println("Outputting in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath,
                observerSet, eventSet, periodRanges, phases, voxelPositionSet)) {
            partialIDs.forEach(id -> {
                try {
                    if (id.getWaveformType().equals(WaveformType.PARTIAL) == false) {
                        throw new IllegalStateException(id.toString() + "is not a partial, it is a " + id.getWaveformType().toString());
                    }
                    wdw.addPartialID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * Reads partialIDs from file.
     * @param inPath (Path) The directory containing partial ID and data files
     * @param withData (boolean) Whether to read waveform data
     * @return (List of PartialID)
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/1/29
     */
    public static List<PartialID> read(Path inPath, boolean withData) throws IOException {
        if (withData) return Arrays.asList(read(inPath.resolve(ID_FILE_NAME), inPath.resolve(DATA_FILE_NAME)));
        else return Arrays.asList(read(inPath.resolve(ID_FILE_NAME)));
    }

    /**
     * Reads both the ID file and the waveform file.
     * @param idPath (Path) An ID file, if it does not exist, an IOException
     * @param dataPath (Path) A data file, if it does not exist, an IOException
     * @return Array of {@link PartialID} containing waveform data
     * @throws IOException if an I/O error occurs
     */
    public static PartialID[] read(Path idPath, Path dataPath) throws IOException {
        // Read IDs
        PartialID[] ids = read(idPath);

        // Read waveforms
        long t = System.nanoTime();
        long nptsTotal = Arrays.stream(ids).mapToLong(PartialID::getNpts).sum();
        long dataSize = Files.size(dataPath);
        if (dataSize != nptsTotal * Double.BYTES)
            throw new RuntimeException(dataPath + " is invalid for " + idPath);

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(dataPath)))) {
            for (int i = 0; i < ids.length; i++) {
                double[] data = new double[ids[i].npts];
                for (int j = 0; j < data.length; j++)
                    data[j] = dis.readDouble();
                ids[i] = ids[i].withData(data);
                if (i % (ids.length / 20) == 0)
                    System.err.print("\r Reading partial data ... " + Math.ceil(i * 100.0 / ids.length) + " %");
            }
            System.err.println("\r Reading partial data ... 100.0 %");
        }
        System.err.println(" Partial waveforms read in " + GadgetAid.toTimeString(System.nanoTime() - t));
        return ids;
    }

    /**
     * Reads only the ID file (and not the waveform file).
     * @param idPath (Path) An ID file, if it does not exist, an IOException
     * @return Array of {@link PartialID} without waveform data
     * @throws IOException if an I/O error occurs
     */
    public static PartialID[] read(Path idPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
            System.err.println("Reading partialID file: " + idPath);
            long t = System.nanoTime();
            long fileSize = Files.size(idPath);

            // Read header
            // short * 5
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            double[][] periodRanges = new double[dis.readShort()][2];
            Phase[] phases = new Phase[dis.readShort()];
            FullPosition[] voxelPositions = new FullPosition[dis.readShort()];
            // calculate number of bytes in header
            int headerBytes = 2 * 5 + (8 + 8 + 8 * 2) * observers.length + 15 * events.length + 8 * 2 * periodRanges.length
                    + 16 * phases.length + 8 * 3 * voxelPositions.length;
            long idParts = fileSize - headerBytes;
            if (idParts % oneIDByte != 0)
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
            // position(8*3)
            for (int i = 0; i < voxelPositions.length; i++) {
                voxelPositions[i] = new FullPosition(dis.readDouble(), dis.readDouble(), dis.readDouble());
            }

            // Read IDs
            int nid = (int) (idParts / oneIDByte);
            byte[][] bytes = new byte[nid][oneIDByte];
            for (int i = 0; i < nid; i++)
                dis.read(bytes[i]);
            PartialID[] ids = new PartialID[nid];
            IntStream.range(0, nid).parallel()
                .forEach(i -> ids[i] = createID(bytes[i], observers, events, periodRanges, phases, voxelPositions));
            System.err.println(" " + ids.length + " partialIDs are read in " + GadgetAid.toTimeString(System.nanoTime() - t));
            return ids;
        }
    }

    /**
     * An ID information contains<br>
     * observer number(2)<br>
     * event number(2)<br>
     * component(1)<br>
     * period range(1) <br>
     * phases numbers (10*2)<br>
     * start time(4)<br>
     * number of points(4)<br>
     * sampling hz(4) <br>
     * convoluted(or observed) or not(1)<br>
     * position of a waveform for the ID in the datafile(8)<br>
     * type of partial(1)<br>
     * point of perturbation(2)
     *
     * @param bytes
     *            for one ID
     * @return an ID written in the bytes
     */
    private static PartialID createID(byte[] bytes, Observer[] observers, GlobalCMTID[] events, double[][] periodRanges,
             Phase[] phases, FullPosition[] voxelPositions) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        Observer observer = observers[bb.getShort()];
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
        PartialType partialType = PartialType.getType(bb.get());
        FullPosition voxelPosition = voxelPositions[bb.getShort()];
        return new PartialID(observer, event, component, samplingHz, startTime, npts, period[0], period[1],
                usablephases, isConvolved, voxelPosition, partialType);
    }

    ////////////////////////////////////////////////////////////////////////////////////

    /**
     * Exports binary files in ascii format.
     * @param args [option]
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException{
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
    public static Options defineOptions() throws IOException{
        Options options = Summon.defaultOptions();
        //input
        options.addOption(Option.builder("p").longOpt("partial").hasArg().argName("partailFolder")
                .desc("The input partial waveform folder (.)").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException{
        Path partialPath = cmdLine.hasOption("p") ? Paths.get(cmdLine.getOptionValue("p")) : Paths.get(".");
        List<PartialID> ids = read(partialPath, false);

        if (cmdLine.hasOption("o")) {
            Path outputIdsPath = Paths.get(cmdLine.getOptionValue("o"));
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
                pw.println("#station, network, lat, lon, event, component, startTime, npts, samplingHz, "
                        + "minPeriod, maxPeriod, phases, convolved, voxelPosition{lat, lon, rad}, partialType");
                for (int i = 0; i < 10; i++)
                    pw.println(ids.get(i));
            }
        } else {
            System.out.println("#station, network, lat, lon, event, component, startTime, npts, samplingHz, "
                    + "minPeriod, maxPeriod, phases, convolved, voxelPosition{lat, lon, rad}, partialType");
            for (int i = 0; i < 10; i++)
                System.out.println(ids.get(i));
        }
    }
}
