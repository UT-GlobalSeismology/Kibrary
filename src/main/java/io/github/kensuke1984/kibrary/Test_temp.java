package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.selection.DataFeatureListFile;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {

        Set<DataFeature> featureSet = DataFeatureListFile.read(Paths.get(args[0]));

        Path acceptedPath = Paths.get("acceptedEntry.lst");
        Set<DataEntry> acceptedSet = featureSet.stream().filter(feature -> 0.2 < feature.getAbsRatio() && feature.getAbsRatio() < 5)
                .map(feature -> feature.getTimewindow().toDataEntry()).collect(Collectors.toSet());

        DataEntryListFile.writeFromSet(acceptedSet, acceptedPath);
    }
}
