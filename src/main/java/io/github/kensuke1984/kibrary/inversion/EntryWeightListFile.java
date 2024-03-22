package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * File containing list of weights for each {@link DataEntry}.
 * <p>
 * Each line: globalCMTID station network latitude longitude component weight
 * <p>
 * Here, "station network latitude longitude" is for the observer.
 *
 * @author otsuru
 * @since 2024/3/22
 */
public class EntryWeightListFile {

    public static void write(Map<DataEntry, Double> weightMap, Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(weightMap.size(), "data entry weight", "data entry weights")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# globalCMTID station network latitude longitude component weight");
            weightMap.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey()))
                    .forEach(entry -> pw.println(entry.getKey().toString() + " " + entry.getValue()));
        }
    }

    public static Map<DataEntry, Double> read(Path inputPath) throws IOException {
        Map<DataEntry, Double> weightMap = new HashMap<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            GlobalCMTID event = new GlobalCMTID(parts[0]);
            HorizontalPosition hp = new HorizontalPosition(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
            Observer observer = new Observer(parts[1], parts[2], hp);
            SACComponent component = SACComponent.valueOf(parts[5]);
            double weight = Double.parseDouble(parts[6]);

            DataEntry entry = new DataEntry(event, observer, component);
            weightMap.put(entry, weight);
        }

        DatasetAid.checkNum(weightMap.size(), "data entry weight", "data entry weights");
        return Collections.unmodifiableMap(weightMap);
    }

}
