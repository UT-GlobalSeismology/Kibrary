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
     * Number of bytes used for one ID.
     */
    public static final int oneIDByte = 50;

    public static final String ID_FILE_NAME = "partialID.dat";
    public static final String DATA_FILE_NAME = "partialData.dat";

    /**
     * Write partialIDs into ID file and data file.
     * @param partialIDs (List of {@link PartialID}) PartialIDs to write.
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
            wdw.flush();
        }
    }

    /**
     * Reads partialIDs from a partial folder.
     * @param inPath (Path) The directory containing partial ID and data files
     * @param withData (boolean) Whether to read waveform data
     * @return (List of PartialID) The partialIDs read in. Not sorted.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/1/29
     */
    public static List<PartialID> read(Path inPath, boolean withData) throws IOException {
        System.err.println("Reading partial folder: " + inPath);
        if (withData) return Arrays.asList(read(inPath.resolve(ID_FILE_NAME), inPath.resolve(DATA_FILE_NAME)));
        else return Arrays.asList(read(inPath.resolve(ID_FILE_NAME)));
    }

    /**
     * Reads both the ID file and the waveform file.
     * @param idPath (Path) ID file
     * @param dataPath (Path) Data file
     * @return ({@link PartialID}[]) PartialIDs containing waveform data
     * @throws IOException if an I/O error occurs
     * @deprecated (make this method private)
     */
    public static PartialID[] read(Path idPath, Path dataPath) throws IOException {
        // Read IDs
        PartialID[] ids = read(idPath);

        // Read waveforms
        System.err.print(" Reading data file ...");
        long t = System.nanoTime();
        long nptsTotal = Arrays.stream(ids).mapToLong(PartialID::getNpts).sum();
        long dataSize = Files.size(dataPath);
        if (dataSize != nptsTotal * Double.BYTES)
            throw new RuntimeException(dataPath + " is invalid for " + idPath);

        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(dataPath))) {
            byte[][] bytes = new byte[ids.length][];
            Arrays.parallelSetAll(bytes, i -> new byte[ids[i].npts * Double.BYTES]);
            for (int i = 0; i < ids.length; i++)
                bis.read(bytes[i]);
            IntStream.range(0, ids.length).parallel().forEach(i -> {
                PartialID id = ids[i];
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
     * @return ({@link PartialID}[]) PartialIDs without waveform data
     * @throws IOException if an I/O error occurs
     */
    private static PartialID[] read(Path idPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
            System.err.print(" Reading ID file ...");
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
            int headerBytes = Short.BYTES * 5 + (Observer.MAX_LENGTH + Double.BYTES * 2) * observers.length
                    + GlobalCMTID.MAX_LENGTH * events.length + 16 * phases.length
                    + Double.BYTES * 2 * periodRanges.length + Double.BYTES * 3 * voxelPositions.length;
            long idParts = fileSize - headerBytes;
            if (idParts % oneIDByte != 0)
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
            System.err.println("\r " + ids.length + " IDs read in " + GadgetAid.toTimeString(System.nanoTime() - t));
            return ids;
        }
    }

    /**
     * Method for reading the actual ID part.
     * <p>
     * An ID information contains<br>
     * observer number(2)<br>
     * event number(2)<br>
     * component(1)<br>
     * period range(1) <br>
     * phases numbers (10*2)<br>
     * start time(4)<br>
     * number of points(4)<br>
     * sampling hz(4) <br>
     * convolved (or observed) or not(1)<br>
     * position of a waveform for the ID in the data file(8)<br>
     * type of partial(1)<br>
     * voxel number(2)
     *
     * @param bytes (byte[]) Input data for one ID
     * @param observers ({@link Observer}[]) Set of observers contained in dataset
     * @param events ({@link GlobalCMTID}[]) Set of events contained in dataset
     * @param periodRanges (double[][]) Set of period ranges contained in dataset
     * @param phases ({@link Phase}[]) Set of phases contained in dataset
     * @param voxelPositions ({@link FullPosition}[]) Set of voxels contained in dataset
     * @return ({@link PartialID}) Created ID
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
                usablephases, isConvolved, partialType.toParameterType(), partialType.toVariableType(), voxelPosition);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Exports binary files in ascii format.
     * @param args Options.
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
                .desc("Specify path of output file. When not set, writes in standard output.").build());
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
