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
 * @since version 0.2.2
 */
public final class StaticCorrectionDataFile {
    private StaticCorrectionDataFile() {}

    /**
     * Number of bytes used for one time shift data.
     */
    public static final int ONE_CORRECTION_BYTE = 37;

    /**
     * Output {@link StaticCorrectionData} in binary format.
     *
     * @param correctionSet (Set of {@link StaticCorrectionData}) Static corrections to write.
     * @param outputPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
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
                dos.writeBytes(StringUtils.rightPad(observers[i].toString(), Observer.MAX_LENGTH));
                HorizontalPosition pos = observers[i].getPosition();
                dos.writeDouble(pos.getLatitude());
                dos.writeDouble(pos.getLongitude());
            }
            for (int i = 0; i < events.length; i++) {
                dos.writeBytes(StringUtils.rightPad(events[i].toString(), GlobalCMTID.MAX_LENGTH));
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
     * Read static correction data from a binary format {@link StaticCorrectionDataFile}.
     *
     * @param inputPath (Path) The {@link StaticCorrectionDataFile} to read.
     * @return (<b>unmodifiable</b> Set of {@link StaticCorrectionData}) Static corrections that are read.
     * @throws IOException if an I/O error occurs
     */
    public static Set<StaticCorrectionData> read(Path inputPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(inputPath)))) {
            long fileSize = Files.size(inputPath);

            // Read header
            // short * 3
            Observer[] observers = new Observer[dis.readShort()];
            GlobalCMTID[] events = new GlobalCMTID[dis.readShort()];
            Phase[] phases = new Phase[dis.readShort()];
            int headerBytes = Short.BYTES * 3 + (Observer.MAX_LENGTH + Double.BYTES * 2) * observers.length
                    + GlobalCMTID.MAX_LENGTH * events.length + 16 * phases.length;
            long correctionParts = fileSize - headerBytes;
            if (correctionParts % ONE_CORRECTION_BYTE != 0)
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

            int nCorrection = (int) (correctionParts / ONE_CORRECTION_BYTE);
            byte[][] bytes = new byte[nCorrection][ONE_CORRECTION_BYTE];
            for (int i = 0; i < nCorrection; i++)
                dis.read(bytes[i]);

            Set<StaticCorrectionData> staticCorrectionSet = Arrays.stream(bytes).parallel()
                    .map(b -> createCorrection(b, observers, events, phases)).collect(Collectors.toSet());
            DatasetAid.checkNum(staticCorrectionSet.size(), "static correction", "static corrections");
            return Collections.unmodifiableSet(staticCorrectionSet);
        }
    }

    /**
     * Create an instance for 1 static correction.
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Shows all static corrections in a file.
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
        options.addOption(Option.builder("c").longOpt("correction").hasArg().argName("staticCorrectionFile")
                .desc("Set input static correction file").build());
        // output
        options.addOption(Option.builder("n").longOpt("number")
                .desc("Just count number without creating output files").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Specify path of output file. When not set, output is same as input with extension changed to '.txt'.").build());
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
