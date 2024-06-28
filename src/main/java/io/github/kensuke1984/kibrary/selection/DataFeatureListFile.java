package io.github.kensuke1984.kibrary.selection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;


/**
 * File containing information of data features. Ascii-format.
 * <p>
 * The following values are output for each timewindow.
 * <ul>
 * <li> maximum ratio: (maximum value in synthetic waveform)/(maximum value in observed waveform)</li>
 * <li> minimum ratio: (minimum value in synthetic waveform)/(minimum value in observed waveform) </li>
 * <li> absolute ratio: (maximum absolute amplitude in synthetic waveform)/(maximum absolute amplitude in observed waveform) </li>
 * <li> normalized variance: (variance of differential waveform)/(variance of observed waveform) </li>
 * <li> correlation coefficient of observed and synthetic waveforms </li>
 * <li> S/N ratio </li>
 * </ul>
 *
 * @author ?
 * @since a long time ago
 * @version 2022/8/27 renamed from selection.DataSelectionInformationFile to selection.DataFeatureListFile
 */
public class DataFeatureListFile {
    private DataFeatureListFile() {}

    public static void write(Set<DataFeature> featureSet, Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Outputting data feature values for "
                + MathAid.switchSingularPlural(featureSet.size(), "time window", "time windows")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("#station, network, lat, lon, event, component, startTime, endTime, phases, "
                    + "posSideRatio, negSideRatio, absRatio, variance, correlation, S/N, selected");
            featureSet.stream().sorted(Comparator.comparing(DataFeature::getTimewindow)).forEach(pw::println);
        }
    }

    public static Set<DataFeature> read(Path inputPath) throws IOException {
        Set<DataFeature> featureSet = new HashSet<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            Observer observer = new Observer(parts[0], parts[1], new HorizontalPosition(Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            Phase[] phases = Stream.of(parts[8].split(",")).map(string -> Phase.create(string)).toArray(Phase[]::new);

            TimewindowData timewindow = new TimewindowData(Double.parseDouble(parts[6]), Double.parseDouble(parts[7]), observer,
                    new GlobalCMTID(parts[4]), SACComponent.valueOf(parts[5]), phases);

            DataFeature feature = new DataFeature(timewindow, Double.parseDouble(parts[12]),
                    Double.parseDouble(parts[13]), Double.parseDouble(parts[9]), Double.parseDouble(parts[10]),
                    Double.parseDouble(parts[11]), Double.parseDouble(parts[14]), Boolean.parseBoolean(parts[15]));

            featureSet.add(feature);
        }

        System.err.println("Data feature values for "
                + MathAid.switchSingularPlural(featureSet.size(), "time window is", "time windows are") + " read.");
        return featureSet;
    }

    public static void main(String[] args) throws IOException {
        Path infoPath = Paths.get(args[0]);
        read(infoPath).stream().forEach(info -> {
            System.out.println(info);
        });
    }

}
