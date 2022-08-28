package io.github.kensuke1984.kibrary.selection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;


/**
 * File containing information of data features. Ascii-format.
 *
 * @since a long time ago
 * @version 2022/8/27 renamed from selection.DataSelectionInformationFile to selection.DataFeatureListFile
 */
public class DataFeatureListFile {
    private DataFeatureListFile() {}

    public static void write(List<DataFeature> featureList, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("#station, network, lat, lon, event, component, startTime, endTime, phases, "
                    + "maxRatio, minRatio, absRatio, variance, cc, S/N, selected");
            for (DataFeature feature : featureList)
                pw.println(feature);
        }
    }

    public static List<DataFeature> read(Path inputPath) throws IOException {
        List<DataFeature> featureList = new ArrayList<>();

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

            featureList.add(feature);
        }

        return featureList;
    }

    public static void main(String[] args) throws IOException {
        Path infoPath = Paths.get(args[0]);
        read(infoPath).stream().forEach(info -> {
            System.out.println(info);
        });
    }

}
