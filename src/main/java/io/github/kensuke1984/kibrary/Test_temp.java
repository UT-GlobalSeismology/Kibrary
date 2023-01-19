package io.github.kensuke1984.kibrary;

import java.io.IOException;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {
        HorizontalPosition posE = new HorizontalPosition(-14, -69);
        HorizontalPosition posS = new HorizontalPosition(-16, 28);

        System.err.println(posE.computeEpicentralDistanceDeg(posS));

        double baz = posS.computeAzimuthDeg(posE);
        System.err.println(baz);
        System.err.println(posS.pointAlongAzimuth(baz, 24));
        System.err.println(posS.pointAlongAzimuth(baz, 60));

        System.err.println(posS.pointAlongAzimuth(0, 10));
/*
        Set<DataFeature> featureSet = DataFeatureListFile.read(Paths.get(args[0]));

        Path acceptedPath = Paths.get("acceptedEntry.lst");
        Set<DataEntry> acceptedSet = featureSet.stream().filter(feature -> 0.2 < feature.getAbsRatio() && feature.getAbsRatio() < 5)
                .map(feature -> feature.getTimewindow().toDataEntry()).collect(Collectors.toSet());

        DataEntryListFile.writeFromSet(acceptedSet, acceptedPath);
 */
    }
}
