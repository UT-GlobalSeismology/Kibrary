package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation to create a virtual dataset.
 * <p>
 * The specified events and components will be used.
 * A virtual observer set will be created in specified ranges.
 * Their network and station names will be the latitude and longitude, respectively (with "P" and "N" for "+" and "-").
 *
 * @author otsuru
 * @since 2023/5/23
 */
public class VirtualDatasetMaker extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;
    /**
     * components to be used
     */
    private Set<SACComponent> components;

    /**
     * Events to work for.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();

    private int lowerLatitude;
    private int upperLatitude;
    private int lowerLongitude;
    private int upperLongitude;
    private int dLatitudeDeg;
    private int dLongitudeDeg;

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. (000000A)");
            pw.println("#tendEvents ");
            pw.println("##########The following parameters are for the virtual observers to create.");
            pw.println("##(int) Lower limit of latitude [deg]; [-90:upperLatitude). (0)");
            pw.println("#lowerLatitude ");
            pw.println("##(int) Upper limit of latitude [deg]; (lowerLatitude:90]. (0)");
            pw.println("#upperLatitude ");
            pw.println("##(int) Lower limit of longitude [deg]; [-180:upperLongitude). (10)");
            pw.println("#lowerLongitude ");
            pw.println("##(int) Upper limit of longitude [deg]; (lowerLongitude:360]. (170)");
            pw.println("#upperLongitude ");
            pw.println("##(int) Latitude spacing [deg], (0:). (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(int) Longitude spacing [deg], (0:). (5)");
            pw.println("#dLongitudeDeg ");
        }
        System.err.println(outPath + " is created.");
    }

    public VirtualDatasetMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", "000000A")).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        for (GlobalCMTID event : tendEvents) {
            if (!event.exists()) throw new IllegalArgumentException(event + " does not exist in catalog.");
        }

        lowerLatitude = property.parseInt("lowerLatitude", "0");
        upperLatitude = property.parseInt("upperLatitude", "0");
        if (lowerLatitude < -90 || lowerLatitude > upperLatitude || 90 < upperLatitude)
            throw new IllegalArgumentException("Latitude range " + lowerLatitude + " , " + upperLatitude + " is invalid.");

        lowerLongitude = property.parseInt("lowerLongitude", "10");
        upperLongitude = property.parseInt("upperLongitude", "170");
        if (lowerLongitude < -180 || lowerLongitude > upperLongitude || 360 < upperLongitude)
            throw new IllegalArgumentException("Longitude range " + lowerLongitude + " , " + upperLongitude + " is invalid.");

        dLatitudeDeg = property.parseInt("dLatitudeDeg", "5");
        if (dLatitudeDeg <= 0)
            throw new IllegalArgumentException("dLatitudeDeg must be positive");
        dLongitudeDeg = property.parseInt("dLongitudeDeg", "5");
        if (dLongitudeDeg <= 0)
            throw new IllegalArgumentException("dLongitudeDeg must be positive");
    }

    @Override
    public void run() throws IOException {

        // synthetic observer set
        Set<Observer> synObserverSet = new HashSet<>();
        for (int latitude = lowerLatitude; latitude <= upperLatitude; latitude += dLatitudeDeg) {
            String networkName = valueToString(latitude);
            for (int longitude = lowerLongitude; longitude <= upperLongitude; longitude += dLongitudeDeg) {
                String stationName = valueToString(longitude);
                Observer observer = new Observer(stationName, networkName, new HorizontalPosition(latitude, longitude));
                synObserverSet.add(observer);
            }
        }

        // virtual data entries
        Set<DataEntry> entrySet = new HashSet<>();
        for (GlobalCMTID event : tendEvents) {
            for (Observer observer : synObserverSet) {
                for (SACComponent component : components) {
                    entrySet.add(new DataEntry(event, observer, component));
                }
            }
        }

        // output
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "dataEntry", fileTag, appendFileDate, GadgetAid.getTemporaryString(), ".lst");
        DataEntryListFile.writeFromSet(entrySet, outputPath);
    }

    private String valueToString(int value) {
        String signString = (value >= 0) ? "P" : "N";
        String numberString = String.format("%03d", Math.abs(value));
        return signString + numberString;
    }

}
