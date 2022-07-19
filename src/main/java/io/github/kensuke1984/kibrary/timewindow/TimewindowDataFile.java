package io.github.kensuke1984.kibrary.timewindow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * File containing {@link TimewindowData}. Binary-format.
 * <p>
 * The file consists of 5 sections:
 * <ol>
 * <li>Number of observers, events, and phases</li>
 * <li>Each observer information <br>
 * - station, network, position </li>
 * <li>Each event <br>
 * - GlobalCMTID </li>
 * <li>Each phase <br>
 * - phase </li>
 * <li>Each timewindow information, composed of {@value #ONE_WINDOW_BYTE} bytes:
 * <ul>
 * <li>Station index (2)</li>
 * <li>GlobalCMTID index (2)</li>
 * <li>existing phases (20)</li>
 * <li>component (1)</li>
 * <li>Float starting time (4) (round off to the third decimal place) </li>
 * <li>Float end time (4) (round off to the third decimal place) </li>
 * </ul>
 * </ol>
 *
 * <p>
 * Observers that satisfy {@link Observer#equals(Object)} are considered to be same observers,
 * and one position (latitude, longitude) is chosen to be output in the timewindow file.
 *
 * <p>
 * When the main method of this class is executed,
 * the input binary-format file is output in ascii format, as follows:
 * <ol>
 * <li> In the standard output, information of each timewindow is written.</li>
 * <li> In 'timewindow.observer', information of each observer is written.</li>
 * </ol>
 *
 */
public final class TimewindowDataFile {
    private TimewindowDataFile() {}

    /**
     * bytes for one time window information
     * @author anselme increased the byte size of a time window to add phase information
     */
    public static final int ONE_WINDOW_BYTE = 33;

    /**
     * Output TimeWindowInformation in binary format
     *
     * @param outputPath to write the information on
     * @param infoSet    Set of timewindow information
     * @param options    for write
     * @throws IOException if an I/O error occurs.
     * @author Kensuke Konishi
     * @author anselme add phase information
     */
    public static void write(Set<TimewindowData> infoSet, Path outputPath, OpenOption... options)
            throws IOException {
        if (infoSet.isEmpty())
            throw new RuntimeException("Input information is empty..");
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath, options)))) {
            GlobalCMTID[] events = infoSet.stream().map(TimewindowData::getGlobalCMTID).distinct().sorted()
                    .toArray(GlobalCMTID[]::new);
            Observer[] observers = infoSet.stream().map(TimewindowData::getObserver).distinct().sorted()
                    .toArray(Observer[]::new);
            Phase[] phases = infoSet.stream().map(TimewindowData::getPhases).flatMap(p -> Stream.of(p))
                .distinct().toArray(Phase[]::new);

            Map<GlobalCMTID, Integer> eventMap = new HashMap<>();
            Map<Observer, Integer> observerMap = new HashMap<>();
            Map<Phase, Integer> phaseMap = new HashMap<>();
            dos.writeShort(observers.length);
            dos.writeShort(events.length);
            dos.writeShort(phases.length);
            for (int i = 0; i < observers.length; i++) {
                observerMap.put(observers[i], i);
                dos.writeBytes(StringUtils.rightPad(observers[i].getStation(), 8));
                dos.writeBytes(StringUtils.rightPad(observers[i].getNetwork(), 8));
                HorizontalPosition pos = observers[i].getPosition();
                dos.writeDouble(pos.getLatitude());
                dos.writeDouble(pos.getLongitude());
            }
            for (int i = 0; i < events.length; i++) {
                eventMap.put(events[i], i);
                dos.writeBytes(StringUtils.rightPad(events[i].toString(), 15));
            }
            for (int i = 0; i < phases.length; i++) {
                phaseMap.put(phases[i], i);
                if (phases[i] == null)
                    throw new NullPointerException(i + " " + "phase is null");
                dos.writeBytes(StringUtils.rightPad(phases[i].toString(), 16));
            }
            for (TimewindowData info : infoSet) {
                dos.writeShort(observerMap.get(info.getObserver()));
                dos.writeShort(eventMap.get(info.getGlobalCMTID()));
                Phase[] infophases = info.getPhases();
                for (int i = 0; i < 10; i++) {
                    if (i < infophases.length) {
                        dos.writeShort(phaseMap.get(infophases[i]));
                    }
                    else
                        dos.writeShort(-1);
                }
                dos.writeByte(info.getComponent().valueOf());
                float startTime = (float) Precision.round(info.startTime, 3);
                float endTime = (float) Precision.round(info.endTime, 3);
                dos.writeFloat(startTime);
                dos.writeFloat(endTime);
            }
        }
    }

    /**
     * Read timewindow information from binary format file
     *
     * @param inputPath of the information file to read
     * @return <b>unmodifiable</b> Set of timewindow information
     * @throws IOException if an I/O error occurs
     * @author Kensuke Konishi
     * @author anselme add phase information
     */
    public static Set<TimewindowData> read(Path inputPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(inputPath)));) {
            long t = System.nanoTime();
            long fileSize = Files.size(inputPath);
            // Read header
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = 3 * 2 + (8 + 8 + 8 * 2) * observers.length + 15 * events.length + 16 * phases.length;
            long windowParts = fileSize - headerBytes;
            if (windowParts % ONE_WINDOW_BYTE != 0)
                throw new RuntimeException(inputPath + " has some problems.");
            // station(8),network(8),position(8*2)
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
            byte[] phaseBytes = new byte[16];
            for (int i = 0; i < phases.length; i++) {
                dis.read(phaseBytes);
                phases[i] = Phase.create(new String(phaseBytes).trim());
            }
            int nwindow = (int) (windowParts / ONE_WINDOW_BYTE);
            byte[][] bytes = new byte[nwindow][ONE_WINDOW_BYTE];
            for (int i = 0; i < nwindow; i++)
                dis.read(bytes[i]);
//			Set<TimewindowInformation> infoSet = Arrays.stream(bytes).parallel().map(b -> create(b, stations, cmtIDs, phases))
//					.collect(Collectors.toSet());
            Set<TimewindowData> infoSet = Arrays.stream(bytes).map(b -> create(b, observers, events, phases))
                    .collect(Collectors.toSet());
            System.err.println(
                    infoSet.size() + " timewindow data were found in " + GadgetAid.toTimeString(System.nanoTime() - t));
            return Collections.unmodifiableSet(infoSet);
        }
    }

    /**
     * create an instance for 1 timewindow information
     *
     * @param bytes    byte array
     * @param observers station array
     * @param events      id array
     * @return TimewindowInformation
     * @author anselme add phase information
     */
    private static TimewindowData create(byte[] bytes, Observer[] observers, GlobalCMTID[] events, Phase[] phases) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        Observer observer = observers[bb.getShort()];
        GlobalCMTID event = events[bb.getShort()];
        Set<Phase> tmpset = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            short iphase = bb.getShort();
            if (iphase != -1)
                tmpset.add(phases[iphase]);
        }
        Phase[] usablephases = new Phase[tmpset.size()];
        usablephases = tmpset.toArray(usablephases);
        SACComponent component = SACComponent.getComponent(bb.get());
        double startTime = bb.getFloat();
        double endTime = bb.getFloat();
        return new TimewindowData(startTime, endTime, observer, event, component, usablephases);
    }

    /**
     * The binary-format timewindow information file is output in the standard output.
     *
     * @param args [information file name]
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
        // input
        options.addOption(Option.builder("t").longOpt("timewindow").hasArg().argName("timewindowFile")
                .desc("Set input timewindow file").build());
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
    public static void run(CommandLine cmdLine) throws IOException {

        Path filePath;
        if (cmdLine.hasOption("t")) {
            filePath = Paths.get(cmdLine.getOptionValue("t"));
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                System.err.println(filePath + " does not exist or is a directory.");
                return;
            }
        } else {
            String pathString = "";
            do {
                pathString = GadgetAid.readInputDialogOrLine("File?", pathString);
                if (pathString == null || pathString.isEmpty()) return;
                filePath = Paths.get(pathString);
            } while (!Files.exists(filePath) || Files.isDirectory(filePath));
        }

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("timewindow" + GadgetAid.getTemporaryString() + ".txt");

        Set<TimewindowData> set = TimewindowDataFile.read(filePath);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            set.stream().sorted().forEach(pw::println);
        }
    }
}
