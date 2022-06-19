package io.github.kensuke1984.kibrary.selection;

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
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation to extract or eliminate timewindows from {@link TimewindowDataFile}
 * depending on the geometries of each raypath.
 * <p>
 * A timewindow data file must be provided as input. A new timewindow data file will be created as the output.
 *
 * @author otsuru
 * @since 2022/1/4
 */
public class RaypathSelection extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output timewindow file
     */
    private Path outputSelectedPath;
    /**
     * components for computation
     */
    private Set<SACComponent> components;

    /**
     * Path of the input timewindow file
     */
    private Path timewindowPath;

    /**
     * Whether to eliminate certaion raypaths or to extract them
     */
    private boolean eliminationMode;

    /**
     * not radius but distance from the surface
     */
    private double lowerEventDepth;
    /**
     * not radius but distance from the surface
     */
    private double upperEventDepth;
    private double lowerEventLatitude;
    private double upperEventLatitude;
    private double lowerEventLongitude;
    private double upperEventLongitude;
    private double lowerObserverLatitude;
    private double upperObserverLatitude;
    private double lowerObserverLongitude;
    private double upperObserverLongitude;
    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Sac components to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a timewindow file, must be defined");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##(boolean) Whether to eliminate the specified raypaths instead of extracting them (false)");
            pw.println("#eliminationMode");
            pw.println("##########Raypaths that satisfy all of the following criteria will be extracted/eliminated.");
            pw.println("##########Selection criteria of events##########");
            pw.println("##(double) Shallower limit of event DEPTH (0)");
            pw.println("#lowerEventDepth NOT SUPPORTED YET");
            pw.println("##(double) Deeper limit of event DEPTH (1000)");
            pw.println("#upperEventDepth NOT SUPPORTED YET");
            pw.println("##(double) Lower limit of event latitude [deg] [-90:upperLatitude) (-90)");
            pw.println("#lowerEventLatitude");
            pw.println("##(double) Upper limit of event latitude [deg] (lowerLatitude:90] (90)");
            pw.println("#upperEventLatitude");
            pw.println("##(double) Lower limit of event longitude [deg] [-180:upperLongitude) (-180)");
            pw.println("#lowerEventLongitude");
            pw.println("##(double) Upper limit of event longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperEventLongitude");
            pw.println("##########Selection criteria of observers##########");
            pw.println("##(double) Lower limit of observer latitude [deg] [-90:upperLatitude) (-90)");
            pw.println("#lowerObserverLatitude");
            pw.println("##(double) Upper limit of observer latitude [deg] (lowerLatitude:90] (90)");
            pw.println("#upperObserverLatitude");
            pw.println("##(double) Lower limit of observer longitude [deg] [-180:upperLongitude) (-180)");
            pw.println("#lowerObserverLongitude");
            pw.println("##(double) Upper limit of observer longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperObserverLongitude");
            pw.println("##########Selection criteria of raypaths##########");
            pw.println("##(double) Lower limit of epicentral distance range [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance 70");
            pw.println("##(double) Upper limit of epicentral distance range [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance 100");
            pw.println("##(double) Lower limit of azimuth range [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth");
            pw.println("##(double) Upper limit of azimuth range [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth");
        }
        System.err.println(outPath + " is created.");
    }

    public RaypathSelection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        eliminationMode = property.parseBoolean("eliminationMode", "false");

        lowerEventDepth = property.parseDouble("lowerEventDepth", "0");
        upperEventDepth = property.parseDouble("upperEventDepth", "1000");
        if (lowerEventDepth > upperEventDepth)
            throw new IllegalArgumentException("Event depth range " + lowerEventDepth + " , " + upperEventDepth + " is invalid.");
        lowerEventLatitude = property.parseDouble("lowerEventLatitude", "-90");
        upperEventLatitude = property.parseDouble("upperEventLatitude", "90");
        if (lowerEventLatitude < -90 || lowerEventLatitude > upperEventLatitude || 90 < upperEventLatitude)
            throw new IllegalArgumentException("Event latitude range " + lowerEventLatitude + " , " + upperEventLatitude + " is invalid.");
        lowerEventLongitude = property.parseDouble("lowerEventLongitude", "-180");
        upperEventLongitude = property.parseDouble("upperEventLongitude", "180");
        if (lowerEventLongitude < -180 || lowerEventLongitude > upperEventLongitude || 360 < upperEventLongitude)
            throw new IllegalArgumentException("Event longitude range " + lowerEventLongitude + " , " + upperEventLongitude + " is invalid.");
        lowerObserverLatitude = property.parseDouble("lowerObserverLatitude", "-90");
        upperObserverLatitude = property.parseDouble("upperObserverLatitude", "90");
        if (lowerObserverLatitude < -90 || lowerObserverLatitude > upperObserverLatitude || 90 < upperObserverLatitude)
            throw new IllegalArgumentException("Observer latitude range " + lowerObserverLatitude + " , " + upperObserverLatitude + " is invalid.");
        lowerObserverLongitude = property.parseDouble("lowerObserverLongitude", "-180");
        upperObserverLongitude = property.parseDouble("upperObserverLongitude", "180");
        if (lowerObserverLongitude < -180 || lowerObserverLongitude > upperObserverLongitude || 360 < upperObserverLongitude)
            throw new IllegalArgumentException("Observer longitude range " + lowerObserverLongitude + " , " + upperObserverLongitude + " is invalid.");
        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");
        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");

        String dateStr = GadgetAid.getTemporaryString();
        outputSelectedPath = workPath.resolve(DatasetAid.generateOutputFileName("selectedTimewindow", tag, dateStr, ".dat"));
    }
/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("timewindowFilePath"))
            throw new IllegalArgumentException("No timewindow file specified");
        if (!property.containsKey("eliminationMode")) property.setProperty("eliminationMode", "false");

        if (!property.containsKey("lowerEventDepth")) property.setProperty("lowerEventDepth", "0");
        if (!property.containsKey("upperEventDepth")) property.setProperty("upperEventDepth", "1000");
        if (!property.containsKey("lowerEventLatitude")) property.setProperty("lowerEventLatitude", "-90");
        if (!property.containsKey("upperEventLatitude")) property.setProperty("upperEventLatitude", "90");
        if (!property.containsKey("lowerEventLongitude")) property.setProperty("lowerEventLongitude", "-180");
        if (!property.containsKey("upperEventLongitude")) property.setProperty("upperEventLongitude", "180");
        if (!property.containsKey("lowerObserverLatitude")) property.setProperty("lowerObserverLatitude", "-90");
        if (!property.containsKey("upperObserverLatitude")) property.setProperty("upperObserverLatitude", "90");
        if (!property.containsKey("lowerObserverLongitude")) property.setProperty("lowerObserverLongitude", "-180");
        if (!property.containsKey("upperObserverLongitude")) property.setProperty("upperObserverLongitude", "180");
        if (!property.containsKey("lowerDistance")) property.setProperty("lowerDistance", "0");
        if (!property.containsKey("upperDistance")) property.setProperty("upperDistance", "180");
        if (!property.containsKey("lowerAzimuth")) property.setProperty("lowerAzimuth", "0");
        if (!property.containsKey("upperAzimuth")) property.setProperty("upperAzimuth", "360");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        timewindowFilePath = getPath("timewindowFilePath");
        if (!Files.exists(timewindowFilePath))
            throw new NoSuchFileException("The timewindow file " + timewindowFilePath + " does not exist");
        eliminationMode = Boolean.parseBoolean(property.getProperty("eliminationMode"));

        String dateStr = GadgetAid.getTemporaryString();
        selectedTimewindowFilePath = workPath.resolve("selectedTimewindow" + dateStr + ".dat");

        lowerEventDepth = Double.parseDouble(property.getProperty("lowerEventDepth"));
        upperEventDepth = Double.parseDouble(property.getProperty("upperEventDepth"));
        if (lowerEventDepth > upperEventDepth)
            throw new IllegalArgumentException("Event depth range " + lowerEventDepth + " , " + upperEventDepth + " is invalid.");
        lowerEventLatitude = Double.parseDouble(property.getProperty("lowerEventLatitude"));
        upperEventLatitude = Double.parseDouble(property.getProperty("upperEventLatitude"));
        if (lowerEventLatitude < -90 || lowerEventLatitude > upperEventLatitude || 90 < upperEventLatitude)
            throw new IllegalArgumentException("Event latitude range " + lowerEventLatitude + " , " + upperEventLatitude + " is invalid.");
        lowerEventLongitude = Double.parseDouble(property.getProperty("lowerEventLongitude"));
        upperEventLongitude = Double.parseDouble(property.getProperty("upperEventLongitude"));
        if (lowerEventLongitude < -180 || lowerEventLongitude > upperEventLongitude || 360 < upperEventLongitude)
            throw new IllegalArgumentException("Event longitude range " + lowerEventLongitude + " , " + upperEventLongitude + " is invalid.");
        lowerObserverLatitude = Double.parseDouble(property.getProperty("lowerObserverLatitude"));
        upperObserverLatitude = Double.parseDouble(property.getProperty("upperObserverLatitude"));
        if (lowerObserverLatitude < -90 || lowerObserverLatitude > upperObserverLatitude || 90 < upperObserverLatitude)
            throw new IllegalArgumentException("Observer latitude range " + lowerObserverLatitude + " , " + upperObserverLatitude + " is invalid.");
        lowerObserverLongitude = Double.parseDouble(property.getProperty("lowerObserverLongitude"));
        upperObserverLongitude = Double.parseDouble(property.getProperty("upperObserverLongitude"));
        if (lowerObserverLongitude < -180 || lowerObserverLongitude > upperObserverLongitude || 360 < upperObserverLongitude)
            throw new IllegalArgumentException("Observer longitude range " + lowerObserverLongitude + " , " + upperObserverLongitude + " is invalid.");
        lowerDistance = Double.parseDouble(property.getProperty("lowerDistance"));
        upperDistance = Double.parseDouble(property.getProperty("upperDistance"));
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");
        lowerAzimuth = Double.parseDouble(property.getProperty("lowerAzimuth"));
        upperAzimuth = Double.parseDouble(property.getProperty("upperAzimuth"));
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");

    }
*/

    @Override
    public void run() throws IOException {
        Set<TimewindowData> timewindowSet = TimewindowDataFile.read(timewindowPath);
        Set<TimewindowData> selectedTimewindowSet = new HashSet<>();

        for (TimewindowData window : timewindowSet) {
            // components are checked regardless of mode (extraction or elimination)
            if (!components.contains(window.getComponent())) continue;

            // in extraction mode (eliminationMode=false), ignore raypaths that are not within range
            // in elimination mode (eliminationMode=true), select raypaths that are not within range
            FullPosition eventPosition = window.getGlobalCMTID().getEvent().getCmtLocation();
            if (eventPosition.isInRange(lowerEventLatitude, upperEventLatitude, lowerEventLongitude, upperEventLongitude)
                    == false) {
                if (eliminationMode) {
                    selectedTimewindowSet.add(window);
                }
                continue;
            }
            // TODO: event depth. conversion to radius has to be considered.

            HorizontalPosition observerPosition = window.getObserver().getPosition();
            if (observerPosition.isInRange(lowerObserverLatitude, upperObserverLatitude, lowerObserverLongitude, upperObserverLongitude)
                    == false) {
                if (eliminationMode) {
                    selectedTimewindowSet.add(window);
                }
                continue;
            }

            double distance = eventPosition.calculateEpicentralDistance(observerPosition) * 180. / Math.PI;
            double azimuth = eventPosition.calculateAzimuth(observerPosition) * 180. / Math.PI;
            if ((lowerDistance <= distance && distance <= upperDistance && MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth))
                    == false) {
                if (eliminationMode) {
                    selectedTimewindowSet.add(window);
                }
                continue;
            }

            if (!eliminationMode) {
                selectedTimewindowSet.add(window);
            }
        }

        System.err.println("Outputting selected timewindows in " + outputSelectedPath);
        TimewindowDataFile.write(selectedTimewindowSet, outputSelectedPath);
        System.err.println(selectedTimewindowSet.size() + " timewindows were selected.");
    }

}
