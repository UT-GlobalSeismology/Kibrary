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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
     * Pairs up observed and synthetic BasicIDs included inside an array with random order.
     * @param ids (BasicID[]) Array of IDs to be paired up
     * @param resultObsList ({@literal List<BasicID>}) An empty list to write in the resulting observed IDs
     * @param resultSynList ({@literal List<BasicID>}) An empty list to write in the resulting synthetic IDs
     */
    public static void pairUp(BasicID[] ids, List<BasicID> resultObsList, List<BasicID> resultSynList) {

        // 観測波形の抽出 list observed IDs
        List<BasicID> obsList = Arrays.stream(ids).filter(id -> id.getWaveformType() == WaveformType.OBS)
                .collect(Collectors.toList());
                //.filter(CHOOSER::test).collect(Collectors.toList());

        // 重複チェック 重複が見つかればここから進まない
        for (int i = 0; i < obsList.size(); i++)
            for (int j = i + 1; j < obsList.size(); j++)
                if (obsList.get(i).equals(obsList.get(j)))
                    throw new RuntimeException("Duplicate observed detected");

        // 理論波形の抽出
        List<BasicID> synList = Arrays.stream(ids).filter(id -> id.getWaveformType() == WaveformType.SYN)
                .collect(Collectors.toList());
                //.filter(CHOOSER::test).collect(Collectors.toList());

        // 重複チェック
        for (int i = 0; i < synList.size() - 1; i++)
            for (int j = i + 1; j < synList.size(); j++)
                if (synList.get(i).equals(synList.get(j)))
                    throw new RuntimeException("Duplicate synthetic detected");

        System.err.println("Number of obs IDs before pairing with syn IDs = " + obsList.size());
        if (obsList.size() != synList.size())
            System.err.println("The numbers of observed IDs " + obsList.size() + " and " + " synthetic IDs "
                    + synList.size() + " are different ");

        for (int i = 0; i < synList.size(); i++) {
            boolean foundPair = false;
            for (int j = 0; j < obsList.size(); j++) {
                if (BasicID.isPair(synList.get(i), obsList.get(j))) {
                    resultObsList.add(obsList.get(j));
                    resultSynList.add(synList.get(i));
                    foundPair = true;
                    break;
                }
            }
            if (!foundPair) {
                System.err.println("Didn't find OBS for " + synList.get(i));
            }
        }

        if (resultObsList.size() != resultSynList.size())
            throw new RuntimeException("unanticipated");

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

        if (args.length == 2 && args[0].equals("-i")) {
            BasicID[] ids = read(Paths.get(args[1]));
            Arrays.stream(ids).forEach(System.out::println);
        } else if (args.length == 3 && args[0].equals("-w")) {
            BasicID[] ids = read(Paths.get(args[1]), Paths.get(args[2]));
            outputWaveforms(ids);
        } else {
            System.err.println("Usage:");
            System.err.println(" [-i IDFile] : exports ID file in standard output");
            System.err.println(" [-w IDFile WaveformFile] : exports waveforms in event directories under current path");
        }

    }

    /**
     * Outputs waveform data for each event-observer pair into txt files.
     *
     * @param ids
     * @throws IOException
     */
    private static void outputWaveforms(BasicID[] ids) throws IOException {

        List<BasicID> obsList = new ArrayList<>();
        List<BasicID> synList = new ArrayList<>();
        pairUp(ids, obsList, synList);

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
        PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

        for (int j = 0; j < obsData.length; j++) {
            double obsTime = obsStartTime + j * obsSamplingHz;
            double synTime = synStartTime + j * synSamplingHz;
            pwTrace.println(obsTime + " " + obsData[j] + " " + synTime + " " + synData[j]);
        }
        pwTrace.close();
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
