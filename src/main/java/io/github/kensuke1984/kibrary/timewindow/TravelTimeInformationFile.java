package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * File of travel time data for an arbitrary number of phases, listed for each event-observer pair.
 *
 * @author otsuru
 * @since 2022/7/23
 */
public class TravelTimeInformationFile {

    /**
     * Number of Strings at the head of each line to specify event and observer
     */
    private static int N_ENTRY_TAG = 1 + 4;

    /**
     * Writes a file with information of travel times.
     * @param usePhases (Set of Phase) Phases that are used in timewindow.
     * @param avoidPhases (Set of Phase) Phases that are avoided in timewindow.
     * @param informationSet (Set of {@link TravelTimeInformation}) Travel time information.
     * @param outputPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException if an I/O error occurs.
     */
    public static void write(Set<Phase> usePhases, Set<Phase> avoidPhases, Set<TravelTimeInformation> informationSet,
            Path outputPath, OpenOption... options) throws IOException {
        List<Phase> useList = new ArrayList<>(usePhases);
        List<Phase> avoidList = new ArrayList<>(avoidPhases);

        System.err.println("Outputting travel times for "
                + MathAid.switchSingularPlural(informationSet.size(), "event-observer pair", "event-observer pairs")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# usePhases...");
            if (useList.isEmpty()) pw.print("-");
            else useList.forEach(phase -> pw.print(phase + " "));
            pw.println();

            pw.println("# avoidPhases...");
            if (avoidList.isEmpty()) pw.print("-");
            else avoidList.forEach(phase -> pw.print(phase + " "));
            pw.println();

            pw.println("# eventID station network latitude longitude travelTimes...");
            informationSet.stream().forEach(info -> {
                pw.print(info.getEvent().toPaddedString() + " " + info.getObserver().toPaddedInfoString());
                for (Phase phase : useList) {
                    Double travelTime = info.timeOf(phase);
                    if (travelTime != null) {
                        pw.print(" " + MathAid.padToString(travelTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.DECIMALS, false));
                    } else {
                        pw.print(" -");
                    }
                }
                for (Phase phase : avoidList) {
                    Double travelTime = info.timeOf(phase);
                    if (travelTime != null) {
                        pw.print(" " + MathAid.padToString(travelTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.DECIMALS, false));
                    } else {
                        pw.print(" -");
                    }
                }
                pw.println();
            });
        }
    }

    /**
     * Reads travel time information from a {@link TravelTimeInformationFile}.
     * @param inputPath (Path) The {@link TravelTimeInformationFile} to read.
     * @return (Set of {@link TravelTimeInformation}) Travel time information that is read.
     * @throws IOException
     */
    public static Set<TravelTimeInformation> read(Path inputPath) throws IOException {
        Set<TravelTimeInformation> informationSet = new HashSet<>();
        InformationFileReader reader = new InformationFileReader(inputPath, true);

        // read 1st and 2nd lines
        String useListString = reader.next();
        List<Phase> useList = (useListString.equals("-")) ? Collections.emptyList() :
                Arrays.stream(useListString.split("\\s+")).map(Phase::create).collect(Collectors.toList());
        String avoidListString = reader.next();
        List<Phase> avoidList = (avoidListString.equals("-")) ? Collections.emptyList() :
                Arrays.stream(avoidListString.split("\\s+")).map(Phase::create).collect(Collectors.toList());

        // read rest of file
        String line;
        while ((line = reader.next()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length != N_ENTRY_TAG + useList.size() + avoidList.size()) throw new IllegalArgumentException("Illegal line");

            GlobalCMTID event = new GlobalCMTID(parts[0]);
            HorizontalPosition position = new HorizontalPosition(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
            Observer observer = new Observer(parts[1], parts[2], position);

            Map<Phase, Double> usePhaseTimes = new HashMap<>();
            for (int i = 0; i < useList.size(); i++) {
                String valString = parts[N_ENTRY_TAG + i];
                if (valString != "-") {
                    usePhaseTimes.put(useList.get(i), Double.parseDouble(valString));
                }
            }
            Map<Phase, Double> avoidPhaseTimes = new HashMap<>();
            for (int i = 0; i < avoidList.size(); i++) {
                String valString = parts[N_ENTRY_TAG + useList.size() + i];
                if (!valString.equals("-")) {
                    avoidPhaseTimes.put(avoidList.get(i), Double.parseDouble(valString));
                }
            }
            informationSet.add(new TravelTimeInformation(event, observer, usePhaseTimes, avoidPhaseTimes));
        }

        System.err.println("Travel time data for "
                + MathAid.switchSingularPlural(informationSet.size(), "event-observer pair is", "event-observer pairs are")
                + " found.");
        return informationSet;
    }

}
