package io.github.kensuke1984.kibrary.correction;

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
import java.util.stream.IntStream;
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
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;


/**
 * Information file containing static corrections for each timewindow. See {@link StaticCorrectionData}. Binary-format.
 *
 * <p>
 * The file consists of 5 sections:
 * <ol>
 * <li>Numbers of observers, events, and phases</li>
 * <li>Each observer information <br>
 * - station, network, position </li>
 * <li>Each event <br>
 * - GlobalCMTID</li>
 * <li>Each phase <br>
 * - phase</li>
 * <li>Each static correction information, composed of {@value #ONE_CORRECTION_BYTE} bytes:
 * <ul>
 * <li>Station index (2)</li>
 * <li>GlobalCMTID index (2)</li>
 * <li>existing phases (20)</li>
 * <li>component (1)</li>
 * <li>Float start time (4) (round off to the third decimal place)</li>
 * <li>Float time shift (4) (round off to the third decimal place)</li>
 * <li>Float amplitude ratio(obs/syn) (4) (round off to the third decimal place)</li>
 * </ul>
 * </ol>
 *
 * <p>
 * Observers that satisfy {@link Observer#equals(Object)} are considered to be same observers,
 * and one position (latitude, longitude) is chosen to be output in the timewindow file.
 *
 * <p>
 * When the main method of this class is executed,
 * the input binary-format file is output in ascii format in the standard output.
 *
 * @author Kensuke Konishi
 * @version 0.2.2
 * @author anselme add phase information
 */
public final class StaticCorrectionDataFile {
    private StaticCorrectionDataFile() {}

    /**
     * The number of bytes for one time shift data
     */
    public static final int ONE_CORRECTION_BYTE = 37;

    /**
     * @param outputPath       of an write file.
     * @param correctionSet of static correction to write
     * @param options       for write
     * @throws IOException if an I/O error occurs.
     */
    public static void write(Set<StaticCorrectionData> correctionSet, Path outputPath, OpenOption... options)
            throws IOException {
        System.err.println("Outputting "
            + MathAid.switchSingularPlural(correctionSet.size(), "static correction", "static corrections")
            + " in " + outputPath);

        Observer[] observers = correctionSet.stream().map(StaticCorrectionData::getObserver).distinct().sorted()
                .toArray(Observer[]::new);
        GlobalCMTID[] events = correctionSet.stream().map(StaticCorrectionData::getGlobalCMTID).distinct().sorted()
                .toArray(GlobalCMTID[]::new);
        Phase[] phases = correctionSet.stream().map(StaticCorrectionData::getPhases).flatMap(p -> Stream.of(p))
                .distinct().toArray(Phase[]::new);

        Map<Observer, Integer> observerMap = IntStream.range(0, observers.length).boxed()
                .collect(Collectors.toMap(i -> observers[i], i -> i));
        Map<GlobalCMTID, Integer> eventMap = IntStream.range(0, events.length).boxed()
                .collect(Collectors.toMap(i -> events[i], i -> i));
        Map<Phase, Integer> phaseMap = new HashMap<>();

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath, options)))) {
            dos.writeShort(observers.length);
            dos.writeShort(events.length);
            dos.writeShort(phases.length);
            for (int i = 0; i < observers.length; i++) {
                dos.writeBytes(StringUtils.rightPad(observers[i].getStation(), 8));
                dos.writeBytes(StringUtils.rightPad(observers[i].getNetwork(), 8));
                HorizontalPosition pos = observers[i].getPosition();
                dos.writeDouble(pos.getLatitude());
                dos.writeDouble(pos.getLongitude());
            }
            for (int i = 0; i < events.length; i++) {
                dos.writeBytes(StringUtils.rightPad(events[i].toString(), 15));
            }
            for (int i = 0; i < phases.length; i++) {
                phaseMap.put(phases[i], i);
                if (phases[i] == null)
                    throw new NullPointerException(i + " " + "phase is null");
                dos.writeBytes(StringUtils.rightPad(phases[i].toString(), 16));
            }

            for (StaticCorrectionData correction : correctionSet) {
                dos.writeShort(observerMap.get(correction.getObserver()));
                dos.writeShort(eventMap.get(correction.getGlobalCMTID()));
                Phase[] Infophases = correction.getPhases();
                for (int i = 0; i < 10; i++) {
                    if (i < Infophases.length) {
                        dos.writeShort(phaseMap.get(Infophases[i]));
                    }
                    else
                        dos.writeShort(-1);
                }
                dos.writeByte(correction.getComponent().valueOf());
                dos.writeFloat((float) correction.getSynStartTime());
                dos.writeFloat((float) correction.getTimeshift());
                dos.writeFloat((float) correction.getAmplitudeRatio());
            }
        }

    }

    /**
     * @param infoPath of the correction must exist
     * @return <b>Thread safe</b> set of StaticCorrection
     * @throws IOException if an I/O error occurs
     */
    public static Set<StaticCorrectionData> read(Path infoPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(infoPath)))) {
            long fileSize = Files.size(infoPath);
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] cmtIDs = new GlobalCMTID[dis.readShort()];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = 3 * 2 + (8 + 8 + 8 * 2) * observers.length + 15 * cmtIDs.length + 16 * phases.length;;
            long infoParts = fileSize - headerBytes;
            if (infoParts % ONE_CORRECTION_BYTE != 0)
                throw new RuntimeException(infoPath + " is not valid.. " + (infoParts / (double) ONE_CORRECTION_BYTE));
            // name(8),network(8),position(8*2)
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
            int nInfo = (int) (infoParts / ONE_CORRECTION_BYTE);
            byte[][] bytes = new byte[nInfo][ONE_CORRECTION_BYTE];
            for (int i = 0; i < nInfo; i++)
                dis.read(bytes[i]);

            Set<StaticCorrectionData> staticCorrectionSet = Arrays.stream(bytes).parallel()
                    .map(b -> createCorrection(b, observers, cmtIDs, phases)).collect(Collectors.toSet());
            DatasetAid.checkNum(staticCorrectionSet.size(), "static correction", "static corrections");
            return Collections.unmodifiableSet(staticCorrectionSet);
        }
    }

    /**
     * create an instance for 1 static correction information
     *
     * @param bytes containing infomation above.
     * @return created static correction
     */
    private static StaticCorrectionData createCorrection(byte[] bytes, Observer[] observers, GlobalCMTID[] ids, Phase[] phases) {
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
        SACComponent comp = SACComponent.getComponent(bb.get());
        double start = bb.getFloat();
        double timeshift = bb.getFloat();
        double amplitude = bb.getFloat();
        return new StaticCorrectionData(observer, id, comp, start, timeshift, amplitude, usablephases);
    }

    /**
     * Shows all static corrections in a file
     *
     * @param args [static correction file name]
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
        options.addOption(Option.builder("c").longOpt("correction").hasArg().argName("staticCorrectionFile")
                .desc("Set input static correction file").build());
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
        if (cmdLine.hasOption("c")) {
            filePath = Paths.get(cmdLine.getOptionValue("c"));
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

        // read static correction file
        Set<StaticCorrectionData> corrections = StaticCorrectionDataFile.read(filePath);
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
            pw.println("#station, network, lat, lon, event, component, startTime, phases, "
                    + "timeShift, amplitudeRatio");
            corrections.stream().sorted().forEach(pw::println);
        }
    }

}
