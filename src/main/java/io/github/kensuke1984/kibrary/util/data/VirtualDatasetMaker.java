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
import org.apache.commons.math3.util.Precision;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.Latitude;
import io.github.kensuke1984.kibrary.util.earth.Longitude;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation to create a virtual dataset.
 * <p>
 * The specified events and components will be used.
 * A virtual observer set will be created in specified ranges.
 * Their network and station names will be the latitude and longitude, respectively (with "P" and "N" for "+" and "-").
 *
 * @since 2023/5/23
 * @author otsuru
 */
public class VirtualDatasetMaker extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
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
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Events to work for.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();

    private double lowerLatitude;
    private double upperLatitude;
    private double lowerLongitude;
    private double upperLongitude;

    private double dLatitudeKm;
    private double dLatitudeDeg;
    private boolean setLatitudeByKm;
    private double latitudeOffset;

    private double dLongitudeKm;
    private double dLongitudeDeg;
    private boolean setLongitudeByKm;
    private double longitudeOffset;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
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
            pw.println("##(double) Lower limit of latitude [deg], inclusive; [-90:upperLatitude). (-90)");
            pw.println("#lowerLatitude ");
            pw.println("##(double) Upper limit of latitude [deg], inclusive; (lowerLatitude:90]. (90)");
            pw.println("#upperLatitude ");
            pw.println("##(double) Lower limit of longitude [deg], inclusive; [-180:upperLongitude). (-180)");
            pw.println("#lowerLongitude ");
            pw.println("##(double) Upper limit of longitude [deg], inclusive; (lowerLongitude:360]. (180)");
            pw.println("#upperLongitude ");
            pw.println("##(double) Latitude spacing [km]; (0:). If unset, the following dLatitudeDeg will be used.");
            pw.println("##  Earth radius will be used to convert this to degrees.");
            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg]; (0:). (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(double) Offset of latitude [deg]; [0:). (0)");
            pw.println("#latitudeOffset ");
            pw.println("##(double) Longitude spacing [km]; (0:). If unset, the following dLongitudeDeg will be used.");
            pw.println("##  Earth radius will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg]; (0:). (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Offset of longitude [deg]. (0)");
            pw.println("#longitudeOffset ");
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

        lowerLatitude = property.parseDouble("lowerLatitude", "-90");
        upperLatitude = property.parseDouble("upperLatitude", "90");
        LinearRange.checkValidity("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        lowerLongitude = property.parseDouble("lowerLongitude", "-180");
        upperLongitude = property.parseDouble("upperLongitude", "180");
        LinearRange.checkValidity("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);

        if (property.containsKey("dLatitudeKm")) {
            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
            if (dLatitudeKm <= 0.0) throw new IllegalArgumentException("dLatitudeKm must be positive.");
            setLatitudeByKm = true;
        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0.0) throw new IllegalArgumentException("dLatitudeDeg must be positive.");
            setLatitudeByKm = false;
        }
        latitudeOffset = property.parseDouble("latitudeOffset", "0");
        if (latitudeOffset < 0.0) throw new IllegalArgumentException("latitudeOffset must be non-negative.");

        if (property.containsKey("dLongitudeKm")) {
            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
            if (dLongitudeKm <= 0.0) throw new IllegalArgumentException("dLongitudeKm must be positive.");
            setLongitudeByKm = true;
        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0.0) throw new IllegalArgumentException("dLongitudeDeg must be positive.");
            setLongitudeByKm = false;
        }
        longitudeOffset = property.parseDouble("longitudeOffset", "0");
    }

    @Override
    public void run() throws IOException {

        // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
        double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / Earth.EARTH_RADIUS) : dLatitudeDeg;

        int lowerLatitudeIndex = getLowerIndex(lowerLatitude, dLatitude, latitudeOffset);
        int upperLatitudeIndex = getUpperIndex(upperLatitude, dLatitude, latitudeOffset);

        //~decide the longitude at which to align voxels
        double baseLongitude;
        if (setLongitudeByKm) {
            baseLongitude = (lowerLongitude + upperLongitude) / 2 + longitudeOffset;
        } else {
            baseLongitude = longitudeOffset;
        }

        //~decide horizontal positions of voxels
        Set<Observer> synObserverSet = new HashSet<>();
        for (int i = lowerLatitudeIndex; i <= upperLatitudeIndex; i++) {
            // compute center latitude of the voxel row
            double latitude = i * dLatitude + latitudeOffset;
            String networkName = latitudeToString(latitude);

            // decide longitude interval for current latitude
            double dLongitudeForRow;
            if (setLongitudeByKm) {
                // Voxel points will be aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm.
                double smallCircleRadius = Earth.EARTH_RADIUS * Math.cos(Math.toRadians(latitude));
                dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
            } else {
                // All longitudes will be set on (n * dLongitudeDeg + longitudeOffset).
                dLongitudeForRow = dLongitudeDeg;
            }

            // voxel longitudes are set so that all voxel-centers are included in range
            int lowerLongitudeIndex = getLowerIndex(lowerLongitude, dLongitudeForRow, baseLongitude);
            int upperLongitudeIndex = getUpperIndex(upperLongitude, dLongitudeForRow, baseLongitude);
            for (int j = lowerLongitudeIndex; j <= upperLongitudeIndex; j++) {
                // compute center longitude of the voxel
                double longitude = baseLongitude + j * dLongitudeForRow;
                String stationName = longitudeToString(longitude);
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
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "dataEntry", fileTag, appendFileDate, null, ".lst");
        DataEntryListFile.writeFromSet(entrySet, outputPath);
    }

    private String latitudeToString(double value) {
        if (Precision.equals(value, Math.round(value), HorizontalPosition.LATITUDE_EPSILON)) {
            int integer = (int) Math.round(value);
            String signString = (integer >= 0) ? "P" : "N";
            String numberString = String.format("%02d", Math.abs(integer));
            return signString + numberString;
        } else {
            int integer = (int) Math.round(value * Math.pow(10, Latitude.DECIMALS));
            String signString = (integer >= 0) ? "P" : "N";
            String numberString = String.format("%06d", Math.abs(integer));
            return signString + numberString;
        }
    }
    private String longitudeToString(double value) {
        if (Precision.equals(value, Math.round(value), HorizontalPosition.LONGITUDE_EPSILON)) {
            int integer = (int) Math.round(value);
            String signString = (integer >= 0) ? "P" : "N";
            String numberString = String.format("%03d", Math.abs(integer));
            return signString + numberString;
        } else {
            int integer = (int) Math.round(value * Math.pow(10, Longitude.DECIMALS));
            String signString = (integer >= 0) ? "P" : "N";
            String numberString = String.format("%07d", Math.abs(integer));
            return signString + numberString;
        }
    }

    private static int getLowerIndex(double lowerValue, double interval, double offset) {
        return (int) MathAid.ceil((lowerValue - offset) / interval);
    }
    private static int getUpperIndex(double upperValue, double interval, double offset) {
        return (int) MathAid.floor((upperValue - offset) / interval);
    }

}
