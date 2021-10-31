package io.github.kensuke1984.kibrary.timewindow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * The file containing timewindow information.
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
 * @author Kensuke Konishi
 * @version 0.3.1
 * @author anselme add phase information
 */
public final class TimewindowInformationFile {

    /**
     * bytes for one time window information
     * @author anselme increased the byte size of a time window to add phase information
     */
    public static final int ONE_WINDOW_BYTE = 33;

    private TimewindowInformationFile() {
    }


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
    public static void write(Set<TimewindowInformation> infoSet, Path outputPath, OpenOption... options)
            throws IOException {
        if (infoSet.isEmpty())
            throw new RuntimeException("Input information is empty..");
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath, options)))) {
            GlobalCMTID[] ids = infoSet.stream().map(TimewindowInformation::getGlobalCMTID).distinct().sorted()
                    .toArray(GlobalCMTID[]::new);
            Observer[] observers = infoSet.stream().map(TimewindowInformation::getObserver).distinct().sorted()
                    .toArray(Observer[]::new);
            Phase[] phases = infoSet.stream().map(TimewindowInformation::getPhases).flatMap(p -> Stream.of(p))
                .distinct().toArray(Phase[]::new);

            Map<GlobalCMTID, Integer> idMap = new HashMap<>();
            Map<Observer, Integer> observerMap = new HashMap<>();
            Map<Phase, Integer> phaseMap = new HashMap<>();
            dos.writeShort(observers.length);
            dos.writeShort(ids.length);
            dos.writeShort(phases.length);
            for (int i = 0; i < observers.length; i++) {
                observerMap.put(observers[i], i);
                dos.writeBytes(StringUtils.rightPad(observers[i].getStation(), 8));
                dos.writeBytes(StringUtils.rightPad(observers[i].getNetwork(), 8));
                HorizontalPosition pos = observers[i].getPosition();
                dos.writeDouble(pos.getLatitude());
                dos.writeDouble(pos.getLongitude());
            }
            for (int i = 0; i < ids.length; i++) {
                idMap.put(ids[i], i);
                dos.writeBytes(StringUtils.rightPad(ids[i].toString(), 15));
            }
            for (int i = 0; i < phases.length; i++) {
                phaseMap.put(phases[i], i);
                if (phases[i] == null)
                    throw new NullPointerException(i + " " + "phase is null");
                dos.writeBytes(StringUtils.rightPad(phases[i].toString(), 16));
            }
            for (TimewindowInformation info : infoSet) {
                dos.writeShort(observerMap.get(info.getObserver()));
                dos.writeShort(idMap.get(info.getGlobalCMTID()));
                Phase[] Infophases = info.getPhases();
                for (int i = 0; i < 10; i++) {
                    if (i < Infophases.length) {
                        dos.writeShort(phaseMap.get(Infophases[i]));
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
     * @param infoPath of the information file to read
     * @return <b>unmodifiable</b> Set of timewindow information
     * @throws IOException if an I/O error occurs
     * @author Kensuke Konishi
     * @author anselme add phase information
     */
    public static Set<TimewindowInformation> read(Path infoPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(infoPath)));) {
            long t = System.nanoTime();
            long fileSize = Files.size(infoPath);
            // Read header
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] cmtIDs = new GlobalCMTID[dis.readShort()];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = 3 * 2 + (8 + 8 + 8 * 2) * observers.length + 15 * cmtIDs.length + 16 * phases.length;
            long windowParts = fileSize - headerBytes;
            if (windowParts % ONE_WINDOW_BYTE != 0)
                throw new RuntimeException(infoPath + " has some problems.");
            // station(8),network(8),position(8*2)
            byte[] observerBytes = new byte[32];
            for (int i = 0; i < observers.length; i++) {
                dis.read(observerBytes);
                observers[i] = Observer.createObserver(observerBytes);
            }
            byte[] cmtIDBytes = new byte[15];
            for (int i = 0; i < cmtIDs.length; i++) {
                dis.read(cmtIDBytes);
                cmtIDs[i] = new GlobalCMTID(new String(cmtIDBytes).trim());
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
            Set<TimewindowInformation> infoSet = Arrays.stream(bytes).map(b -> create(b, observers, cmtIDs, phases))
                    .collect(Collectors.toSet());
            System.err.println(
                    infoSet.size() + " timewindow data were found in " + Utilities.toTimeString(System.nanoTime() - t));
            return Collections.unmodifiableSet(infoSet);
        }
    }

    /**
     * create an instance for 1 timewindow information
     *
     * @param bytes    byte array
     * @param observers station array
     * @param ids      id array
     * @return TimewindowInformation
     * @author anselme add phase information
     */
    private static TimewindowInformation create(byte[] bytes, Observer[] observers, GlobalCMTID[] ids, Phase[] phases) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        Observer observer = observers[bb.getShort()];
        GlobalCMTID id = ids[bb.getShort()];
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
        return new TimewindowInformation(startTime, endTime, observer, id, component, usablephases);
    }

    /**
     * @param args [information file name]
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Set<TimewindowInformation> set;
        if (args.length == 1)
            set = TimewindowInformationFile.read(Paths.get(args[0]));
        else if (args.length == 2 && (args[0] == "--debug" || args[1] == "--debug")) {
            String timewindowname;
            if (args[0] == "--debug")
                timewindowname = args[1];
            else
                timewindowname = args[0];
            set = TimewindowInformationFile.read(Paths.get(timewindowname));

            Path outpathStation = Paths.get(timewindowname.split(".inf")[0] + "_observer.inf");
            Path outpathEvent = Paths.get(timewindowname.split(".inf")[0] + "_event.inf");

        }
        else {
            String s = "";
            Path f;
            do {
                s = JOptionPane.showInputDialog("file?", s);
                if (s == null || s.isEmpty())
                    return;
                f = Paths.get(s);
            } while (!Files.exists(f) || Files.isDirectory(f));
            set = TimewindowInformationFile.read(f);
        }

        set.stream().sorted().forEach(tw -> {System.out.println(tw + " " + tw.getObserver().getPosition());});

        Set<Observer> observers = set.stream().map(tw -> tw.getObserver()).collect(Collectors.toSet());
        Path observerFile = Paths.get("timewindow.observer");
        Files.deleteIfExists(observerFile);
        Files.createFile(observerFile);
        try {
            for (Observer s : observers)
                Files.write(observerFile, (s.getStation() + " " + s.getNetwork() + " " + s.getPosition() + "\n").getBytes()
                        , StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
