package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.external.TauPPhase;

/**
 * @author otsuru
 * @since 2022/7/23
 */
public class TravelTimeInformationFile {

    /**
     * Writes a file with information of travel times.
     * @param observerSet Set of observers
     * @param outputPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<Phase> usePhases, Set<Phase> avoidPhases, Set<TravelTimeInformation> informationSet,
            Path outputPath, OpenOption... options) throws IOException {
        List<Phase> useList = new ArrayList<>(usePhases);
        List<Phase> avoidList = new ArrayList<>(avoidPhases);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# usePhases...");
            useList.forEach(phase -> pw.print(phase + " "));
            pw.println("# avoidPhases...");
            avoidList.forEach(phase -> pw.print(phase + " "));

            pw.println("# eventID station network latitude longitude travelTimes...");
            informationSet.stream().forEach(info -> {
                pw.print(info.getEvent() + " " + info.getObserver().toPaddedInfoString());
                for (Phase phase : useList) {
                    TauPPhase phaseData = info.dataFor(phase);
                    if (phaseData != null) {
                        pw.print(" " + phaseData.getTravelTime());
                    } else {
                        pw.print(" -");
                    }
                }
                for (Phase phase : avoidList) {
                    TauPPhase phaseData = info.dataFor(phase);
                    if (phaseData != null) {
                        pw.print(" " + phaseData.getTravelTime());
                    } else {
                        pw.print(" -");
                    }
                }
                pw.println();
            });
        }
    }

}
