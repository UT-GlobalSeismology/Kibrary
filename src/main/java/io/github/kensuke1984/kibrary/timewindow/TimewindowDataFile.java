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

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * File containing a list of timewindows. See {@link TimewindowData}. Binary-format.
 *
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
 * <li>Observer index (2)</li>
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
 * @author Kensuke Konishi
 * @since version 0.3.1
 * @version 2021/11/2 Renamed from timewindow.TimewindowInformationFile to timewindow.TimewindowDataFile.
 */
public final class TimewindowDataFile {
    private TimewindowDataFile() {}

    /**
     * bytes for one time window information
     * @author anselme increased the byte size of a time window to add phase information
     */
    public static final int ONE_WINDOW_BYTE = 33;

    /**
     * Output {@link TimewindowData} in binary format.
     *
     * @param infoSet (Set of {@link TimewindowData}) Timewindows to write.
     * @param outputPath (Path) The output file.
     * @param options (OpenOptions...) Options for write.
     * @throws IOException if an I/O error occurs.
     * @author Kensuke Konishi
     */
    public static void write(Set<TimewindowData> infoSet, Path outputPath, OpenOption... options)
            throws IOException {
        if (infoSet.isEmpty())
            throw new RuntimeException("Input information is empty..");

        System.err.println("Outputting "
                + MathAid.switchSingularPlural(infoSet.size(), "timewindow", "timewindows")
                + " in " + outputPath);

        Observer[] observers = infoSet.stream().map(TimewindowData::getObserver).distinct().sorted()
                .toArray(Observer[]::new);
        GlobalCMTID[] events = infoSet.stream().map(TimewindowData::getGlobalCMTID).distinct().sorted()
                .toArray(GlobalCMTID[]::new);
        Phase[] phases = infoSet.stream().map(TimewindowData::getPhases).flatMap(p -> Stream.of(p))
            .distinct().toArray(Phase[]::new);

        Map<Observer, Integer> observerMap = new HashMap<>();
        Map<GlobalCMTID, Integer> eventMap = new HashMap<>();
        Map<Phase, Integer> phaseMap = new HashMap<>();

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath, options)))) {
            dos.writeShort(observers.length);
            dos.writeShort(events.length);
            dos.writeShort(phases.length);
            for (int i = 0; i < observers.length; i++) {
                observerMap.put(observers[i], i);
                dos.writeBytes(StringUtils.rightPad(observers[i].toString(), Observer.MAX_LENGTH));
                HorizontalPosition pos = observers[i].getPosition();
                dos.writeDouble(pos.getLatitude());
                dos.writeDouble(pos.getLongitude());
            }
            for (int i = 0; i < events.length; i++) {
                eventMap.put(events[i], i);
                dos.writeBytes(StringUtils.rightPad(events[i].toString(), GlobalCMTID.MAX_LENGTH));
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
                float startTime = (float) info.startTime;
                float endTime = (float) info.endTime;
                dos.writeFloat(startTime);
                dos.writeFloat(endTime);
            }
        }
    }

    /**
     * Read timewindow data from a binary format {@link TimewindowDataFile}
     * and select those to use based on {@link DataEntry}s and {@link SACComponent}s.
     *
     * @param timewindowPath (Path) The {@link TimewindowDataFile} to read.
     * @param dataEntryPath (Path) The {@link DataEntryListFile} for selection.
     * @param components (Set of {@link SACComponent}) Components to use.
     * @return (<b>unmodifiable</b> Set of {@link TimewindowData}) Timewindows that are read.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/4/8
     */
    public static Set<TimewindowData> readAndSelect(Path timewindowPath, Path dataEntryPath, Set<SACComponent> components) throws IOException {
        Set<TimewindowData> timewindowSet;
        if (dataEntryPath != null) {
            // read entry set to be used for selection
            Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

            // read timewindows and select based on component and entries
            timewindowSet = TimewindowDataFile.read(timewindowPath).stream()
                    .filter(window -> components.contains(window.getComponent()) && entrySet.contains(window.toDataEntry()))
                    .collect(Collectors.toSet());
        } else {
            // read timewindows and select based on component
            timewindowSet = TimewindowDataFile.read(timewindowPath).stream()
                    .filter(window -> components.contains(window.getComponent()))
                    .collect(Collectors.toSet());
        }
        System.err.println("Selected " + timewindowSet.size() + " timewindows.");
        return Collections.unmodifiableSet(timewindowSet);
    }

    /**
     * Read timewindow data from a binary format {@link TimewindowDataFile}.
     *
     * @param inputPath (Path) The {@link TimewindowDataFile} to read.
     * @return (<b>unmodifiable</b> Set of {@link TimewindowData}) Timewindows that are read.
     * @throws IOException if an I/O error occurs
     * @author Kensuke Konishi
     */
    public static Set<TimewindowData> read(Path inputPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(inputPath)));) {
            long fileSize = Files.size(inputPath);

            // Read header
            // short * 3
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = Short.BYTES * 3 + (Observer.MAX_LENGTH + Double.BYTES * 2) * observers.length
                    + GlobalCMTID.MAX_LENGTH * events.length + 16 * phases.length;
            long windowParts = fileSize - headerBytes;
            if (windowParts % ONE_WINDOW_BYTE != 0)
                throw new IllegalStateException(inputPath + " has some problems.");

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
            byte[] phaseBytes = new byte[16];
            for (int i = 0; i < phases.length; i++) {
                dis.read(phaseBytes);
                phases[i] = Phase.create(new String(phaseBytes).trim());
            }

            int nWindow = (int) (windowParts / ONE_WINDOW_BYTE);
            byte[][] bytes = new byte[nWindow][ONE_WINDOW_BYTE];
            for (int i = 0; i < nWindow; i++)
                dis.read(bytes[i]);

            Set<TimewindowData> timewindowSet = Arrays.stream(bytes).map(b -> create(b, observers, events, phases))
                    .collect(Collectors.toSet());
            DatasetAid.checkNum(timewindowSet.size(), "timewindow", "timewindows");
            return Collections.unmodifiableSet(timewindowSet);
        }
    }

    /**
     * Create an instance for 1 timewindow.
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The binary-format timewindow information file is output in ascii format.
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
        // input
        options.addOption(Option.builder("t").longOpt("timewindow").hasArg().argName("timewindowFile")
                .desc("Set input timewindow file").build());
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
        // set input file path
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

        // read timewindow file
        Set<TimewindowData> windows = TimewindowDataFile.read(filePath);
        if (cmdLine.hasOption("n")) return;

        // set output
        Path outputPath;
        if (cmdLine.hasOption("o")) {
            outputPath = Paths.get(cmdLine.getOptionValue("o"));
        } else {
            // set the output file name the same as the input, but with extension changed to "txt"
            outputPath = Paths.get(FileAid.extractNameRoot(filePath) + ".txt");
        }

        // output
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#station, network, lat, lon, event, component, startTime, endTime, phases, "
                    + "epicentralDistance, azimuth");
            windows.stream().sorted().forEach(window -> {
                double distance = window.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeEpicentralDistanceDeg(window.getObserver().getPosition());
                double azimuth = window.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeAzimuthDeg(window.getObserver().getPosition());
                pw.println(window.toString() + " "
                        + MathAid.padToString(distance, 3, 2, false) + " " + MathAid.padToString(azimuth, 3, 2, false));
            });
        }
    }
}
