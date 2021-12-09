package io.github.kensuke1984.kibrary.waveform;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
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

import org.apache.commons.io.FilenameUtils;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
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
 * <dl><dd>Numbers of stations, events and period ranges</dd>
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
 * <dt>See in {@link #read(Path)}</dt>
 * </dl>
 *
 * <p>
 * When the main method of this class is executed,
 * the input binary-format files can be exported in ascii format.
 */
public final class BasicIDFile {

    /**
     * [byte] File size for an ID
     */
    public static final int ONE_ID_BYTE = 48;

    private BasicIDFile() {
    }

    /**
     * Reads both the ID file and the waveform file.
     * @param idPath
     *            {@link Path} of an ID file, if it does not exist, an
     *            IOException
     * @param dataPath
     *            {@link Path} of an data file, if it does not exist, an
     *            IOException
     * @return Array of {@link BasicID} containing waveform data
     * @throws IOException
     *             if an I/O error happens,
     */
    public static BasicID[] read(Path idPath, Path dataPath) throws IOException {
        BasicID[] ids = read(idPath);
        long dataSize = Files.size(dataPath);
        long t = System.nanoTime();
        BasicID lastID = ids[ids.length - 1];
        if (dataSize != lastID.START_BYTE + lastID.NPTS * 8)
            throw new RuntimeException(dataPath + " is invalid for " + idPath);
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(dataPath))) {
            byte[][] bytes = new byte[ids.length][];
            Arrays.parallelSetAll(bytes, i -> new byte[ids[i].NPTS * 8]);
            for (int i = 0; i < ids.length; i++)
                bis.read(bytes[i]);
            IntStream.range(0, ids.length).parallel().forEach(i -> {
                BasicID id = ids[i];
                ByteBuffer bb = ByteBuffer.wrap(bytes[i]);
                double[] data = new double[id.NPTS];
                for (int j = 0; j < data.length; j++)
                    data[j] = bb.getDouble();
                ids[i] = id.setData(data);
            });
        }
        System.err.println("Reading waveform done in " + GadgetUtils.toTimeString(System.nanoTime() - t));
        return ids;
    }

    /**
     * Reads only the ID file (and not the waveform file).
     *
     * @param idPath {@link Path} of an ID file
     * @return Array of {@link BasicID} without waveform data
     * @throws IOException if an I/O error occurs
     */
    public static BasicID[] read(Path idPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
            long t = System.nanoTime();
            long fileSize = Files.size(idPath);
            // Read header
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            double[][] periodRanges = new double[dis.readShort()][2];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = 2 * 4 + (8 + 8 + 8 * 2) * observers.length + 15 * events.length
                    + 16 * phases.length + 8 * 2 * periodRanges.length;
            long idParts = fileSize - headerBytes;
            if (idParts % ONE_ID_BYTE != 0)
                throw new RuntimeException(idPath + " is invalid");
            // name(8),network(8),position(8*2)
            byte[] observerBytes = new byte[32];
            for (int i = 0; i < observers.length; i++) {
                dis.read(observerBytes);
                observers[i] = Observer.createObserver(observerBytes);
            }
            byte[] eventBytes = new byte[15];
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

            int nid = (int) (idParts / ONE_ID_BYTE);
            BasicID[] ids = new BasicID[nid];
            byte[][] bytes = new byte[nid][ONE_ID_BYTE];
            for (int i = 0; i < nid; i++)
                dis.read(bytes[i]);
            IntStream.range(0, nid).parallel().forEach(i -> {
                ids[i] = createID(bytes[i], observers, events, periodRanges, phases);
            });
            System.err.println(
                    "Reading " + ids.length + " basic IDs done in " + GadgetUtils.toTimeString(System.nanoTime() - t));
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
     * <li> [-w IDFile WaveformFile] : exports ID file in standard output and waveforms in event directories under current path</li>
     * </ul>
     * You must specify one or the other.
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {

        if (args.length == 1) {
            BasicID[] ids = read(Paths.get(args[0]));
            // print(Paths.get(args[0]));
            String header = FilenameUtils.getBaseName(Paths.get(args[0]).getFileName().toString());
            try {
                outputStations(header, ids);
                outputGlobalCMTID(header, ids);
            } catch (Exception e) {
                System.err.println("Could not write information about " + args[0]);
                System.err.println("If you want to see all IDs inside, then use a '-a' option.");
            }
        } else if (args.length == 2 && args[0].equals("-i")) {
            BasicID[] ids = read(Paths.get(args[1]));
            Arrays.stream(ids).forEach(System.out::println);
        } else if (args.length == 3 && args[0].equals("-w")) {
            BasicID[] ids = read(Paths.get(args[1]), Paths.get(args[2]));
            Arrays.stream(ids).forEach(System.out::println);
        } else {
            System.err.println("Usage:");
            System.err.println(" [-i IDFile] : exports ID file in standard output");
            System.err.println(" [-w IDFile WaveformFile] : exports ID file in standard output and waveforms in event directories under current path");
        }

    }

    /**
     * @param header
     * @param ids
     * @throws IOException
     * @deprecated
     */
    private static void outputGlobalCMTID(String header, BasicID[] ids) throws IOException {
        Path outPath = Paths.get(header + ".globalCMTID");
        List<String> lines = Arrays.stream(ids).parallel().map(id -> id.event).distinct()
                .map(id -> id.toString() + " " + id.getEvent().getCmtLocation()).sorted()
                .collect(Collectors.toList());
        Files.write(outPath, lines, StandardOpenOption.CREATE_NEW);
        System.err.println(outPath + " is created as a list of global CMT IDs.");
    }

    /**
     * @param header
     * @param ids
     * @throws IOException
     * @deprecated
     */
    private static void outputStations(String header, BasicID[] ids) throws IOException {
        Path outPath = Paths.get(header + ".station");
        List<String> lines = Arrays.stream(ids).parallel().map(id -> id.observer).distinct().sorted()
                .map(s -> s.getStation() + " " + s.getNetwork() + " " + s.getPosition()).collect(Collectors.toList());
        Files.write(outPath, lines, StandardOpenOption.CREATE_NEW);
        System.err.println(outPath + " is created as a list of stations.");
    }


}
