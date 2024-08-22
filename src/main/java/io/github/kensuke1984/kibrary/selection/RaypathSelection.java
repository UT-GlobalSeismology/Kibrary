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

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation to extract or eliminate data entries from {@link DataEntryListFile}
 * depending on the geometries of each raypath.
 * <p>
 * A data entry list file must be provided as input. A new data entry list file will be created as the output.
 *
 *
 * @author otsuru
 * @since 2022/1/4
 */
public class RaypathSelection extends Operation {

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
     * Path of the output data entry list file.
     */
    private Path outputSelectedPath;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of the input data entry list file.
     */
    private Path dataEntryPath;

    /**
     * Whether to eliminate certaion raypaths or to extract them.
     */
    private boolean eliminationMode;

    /**
     * Moment magnitude range.
     */
    private LinearRange eventMwRange;
    /**
     * DEPTH range [km].
     */
    private LinearRange eventDepthRange;
    private LinearRange eventLatitudeRange;
    private CircularRange eventLongitudeRange;
    private LinearRange observerLatitudeRange;
    private CircularRange observerLongitudeRange;
    private LinearRange turningLatitudeRange;
    private CircularRange turningLongitudeRange;
    private LinearRange distanceRange;
    private CircularRange azimuthRange;
    private CircularRange backAzimuthRange;
    private CircularRange turningAzimuthRange;
    /**
     * Whether criteria for turning point position exists.
     */
    private boolean selectTurningPosition;
    /**
     * Whether criteria for turning point azimuth exists.
     */
    private boolean selectTurningAzimuth;
    /**
     * Name of structure to use for calculating turning point.
     */
    private String structureName;
    /**
     * Phase to use when computing turning point.
     */
    private String turningPointPhase;

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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Sac components to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry list file, must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##(boolean) Whether to eliminate the specified raypaths instead of extracting them. (false)");
            pw.println("#eliminationMode true");
            pw.println("##########Raypaths that satisfy all of the following criteria will be extracted/eliminated.");
            pw.println("##########Selection criteria of events##########");
            pw.println("##(double) Lower limit of Mw, inclusive; (:upperEventMw). (0)");
            pw.println("#lowerEventMw ");
            pw.println("##(double) Upper limit of Mw, exclusive; (lowerEventMw:). (10)");
            pw.println("#upperEventMw ");
            pw.println("##(double) Shallower limit of event DEPTH [km], inclusive; (:upperEventDepth). (0)");
            pw.println("#lowerEventDepth ");
            pw.println("##(double) Deeper limit of event DEPTH [km], exclusive; (lowerEventDepth:). (1000)");
            pw.println("#upperEventDepth ");
            pw.println("##(double) Lower limit of event latitude [deg], inclusive; [-90:upperEventLatitude). (-90)");
            pw.println("#lowerEventLatitude ");
            pw.println("##(double) Upper limit of event latitude [deg], exclusive; (lowerEventLatitude:90]. (90)");
            pw.println("#upperEventLatitude ");
            pw.println("##(double) Lower limit of event longitude [deg], inclusive; [-180:360]. (-180)");
            pw.println("#lowerEventLongitude ");
            pw.println("##(double) Upper limit of event longitude [deg], exclusive; [-180:360]. (180)");
            pw.println("#upperEventLongitude ");
            pw.println("##########Selection criteria of observers##########");
            pw.println("##(double) Lower limit of observer latitude [deg], inclusive; [-90:upperObserverLatitude). (-90)");
            pw.println("#lowerObserverLatitude ");
            pw.println("##(double) Upper limit of observer latitude [deg], exclusive; (lowerObserverLatitude:90]. (90)");
            pw.println("#upperObserverLatitude ");
            pw.println("##(double) Lower limit of observer longitude [deg], inclusive; [-180:360]. (-180)");
            pw.println("#lowerObserverLongitude ");
            pw.println("##(double) Upper limit of observer longitude [deg], exclusive; [-180:360]. (180)");
            pw.println("#upperObserverLongitude ");
            pw.println("##########Selection criteria of turning points##########");
            pw.println("##(double) Lower limit of turning point latitude [deg], inclusive; [-90:upperTurningLatitude). (-90)");
            pw.println("#lowerTurningLatitude ");
            pw.println("##(double) Upper limit of turning point latitude [deg], exclusive; (lowerTurningLatitude:90]. (90)");
            pw.println("#upperTurningLatitude ");
            pw.println("##(double) Lower limit of turning point longitude [deg], inclusive; [-180:360]. (-180)");
            pw.println("#lowerTurningLongitude ");
            pw.println("##(double) Upper limit of turning point longitude [deg], exclusive; [-180:360]. (180)");
            pw.println("#upperTurningLongitude ");
            pw.println("##########Selection criteria of raypaths##########");
            pw.println("##(double) Lower limit of epicentral distance range [deg], inclusive; [0:upperDistance). (0)");
            pw.println("#lowerDistance 70");
            pw.println("##(double) Upper limit of epicentral distance range [deg], exclusive; (lowerDistance:180]. (180)");
            pw.println("#upperDistance 100");
            pw.println("##(double) Lower limit of azimuth range [deg], inclusive; [-180:360]. (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of azimuth range [deg], exclusive; [-180:360]. (360)");
            pw.println("#upperAzimuth ");
            pw.println("##(double) Lower limit of back azimuth range [deg], inclusive; [-180:360]. (0)");
            pw.println("#lowerBackAzimuth ");
            pw.println("##(double) Upper limit of back azimuth range [deg], exclusive; [-180:360]. (360)");
            pw.println("#upperBackAzimuth ");
            pw.println("##(double) Lower limit of turning point azimuth range [deg], inclusive; [-180:360]. (0)");
            pw.println("#lowerTurningAzimuth ");
            pw.println("##(double) Upper limit of turning point azimuth range [deg], exclusive; [-180:360]. (360)");
            pw.println("#upperTurningAzimuth ");
            pw.println("##########When criteria for turning points are set, the following is used:##########");
            pw.println("##(String) Name of structure to use for calculating turning point. (prem)");
            pw.println("#structureName ");
            pw.println("##Phase to compute turning point for. (ScS)");
            pw.println("#turningPointPhase ");
        }
        System.err.println(outPath + " is created.");
    }

    public RaypathSelection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        eliminationMode = property.parseBoolean("eliminationMode", "false");

        double lowerEventMw = property.parseDouble("lowerMw", "0.");
        double upperEventMw = property.parseDouble("upperMw", "10.");
        eventMwRange = new LinearRange("Event magnitude", lowerEventMw, upperEventMw);
        double lowerEventDepth = property.parseDouble("lowerEventDepth", "0");
        double upperEventDepth = property.parseDouble("upperEventDepth", "1000");
        eventDepthRange = new LinearRange("Event depth", lowerEventDepth, upperEventDepth);
        double lowerEventLatitude = property.parseDouble("lowerEventLatitude", "-90");
        double upperEventLatitude = property.parseDouble("upperEventLatitude", "90");
        eventLatitudeRange = new LinearRange("Event latitude", lowerEventLatitude, upperEventLatitude, -90.0, 90.0);
        double lowerEventLongitude = property.parseDouble("lowerEventLongitude", "-180");
        double upperEventLongitude = property.parseDouble("upperEventLongitude", "180");
        eventLongitudeRange = new CircularRange("Event longitude", lowerEventLongitude, upperEventLongitude, -180.0, 360.0);

        double lowerObserverLatitude = property.parseDouble("lowerObserverLatitude", "-90");
        double upperObserverLatitude = property.parseDouble("upperObserverLatitude", "90");
        observerLatitudeRange = new LinearRange("Observer latitude", lowerObserverLatitude, upperObserverLatitude, -90.0, 90.0);
        double lowerObserverLongitude = property.parseDouble("lowerObserverLongitude", "-180");
        double upperObserverLongitude = property.parseDouble("upperObserverLongitude", "180");
        observerLongitudeRange = new CircularRange("Observer longitude", lowerObserverLongitude, upperObserverLongitude, -180.0, 360.0);

        if (property.containsKey("lowerTurningLatitude") || property.containsKey("upperTurningLatitude") ||
                property.containsKey("lowerTurningLongitude") || property.containsKey("upperTurningLongitude")) {
            selectTurningPosition = true;
        }
        double lowerTurningLatitude = property.parseDouble("lowerTurningLatitude", "-90");
        double upperTurningLatitude = property.parseDouble("upperTurningLatitude", "90");
        turningLatitudeRange = new LinearRange("Turning point latitude", lowerTurningLatitude, upperTurningLatitude, -90.0, 90.0);
        double lowerTurningLongitude = property.parseDouble("lowerTurningLongitude", "-180");
        double upperTurningLongitude = property.parseDouble("upperTurningLongitude", "180");
        turningLongitudeRange = new CircularRange("Turning point longitude", lowerTurningLongitude, upperTurningLongitude, -180.0, 360.0);

        double lowerDistance = property.parseDouble("lowerDistance", "0");
        double upperDistance = property.parseDouble("upperDistance", "180");
        distanceRange = new LinearRange("Distance", lowerDistance, upperDistance, 0.0, 180.0);
        double lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        double upperAzimuth = property.parseDouble("upperAzimuth", "360");
        azimuthRange = new CircularRange("Azimuth", lowerAzimuth, upperAzimuth, -180.0, 360.0);
        double lowerBackAzimuth = property.parseDouble("lowerBackAzimuth", "0");
        double upperBackAzimuth = property.parseDouble("upperBackAzimuth", "360");
        backAzimuthRange = new CircularRange("Back azimuth", lowerBackAzimuth, upperBackAzimuth, -180.0, 360.0);

        if (property.containsKey("lowerTurningAzimuth") || property.containsKey("upperTurningAzimuth")) {
            selectTurningAzimuth = true;
        }
        double lowerTurningAzimuth = property.parseDouble("lowerTurningAzimuth", "0");
        double upperTurningAzimuth = property.parseDouble("upperTurningAzimuth", "360");
        turningAzimuthRange = new CircularRange("Turning point azimuth", lowerTurningAzimuth, upperTurningAzimuth, -180.0, 360.0);

        structureName = property.parseString("structureName", "prem");
        turningPointPhase = property.parseString("turningPointPhase", "ScS");

        String dateStr = GadgetAid.getTemporaryString();
        outputSelectedPath = DatasetAid.generateOutputFilePath(workPath, "selectedEntry", fileTag, appendFileDate, dateStr, ".lst");
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);
        Set<DataEntry> selectedEntrySet = new HashSet<>();

        // compute turning points if needed
        TauPPierceWrapper pierceTool = null;
        if (selectTurningPosition || selectTurningAzimuth) {
            try {
                pierceTool = new TauPPierceWrapper(structureName, turningPointPhase);
                pierceTool.compute(entrySet);
            } catch (TauModelException e) {
                throw new RuntimeException(e);
            }
        }

        for (DataEntry entry : entrySet) {
            // components are checked regardless of mode (extraction or elimination)
            if (!components.contains(entry.getComponent())) continue;

            // in extraction mode (eliminationMode=false), ignore raypaths that are not within range
            // in elimination mode (eliminationMode=true), select raypaths that are not within range

            // observer position
            HorizontalPosition observerPosition = entry.getObserver().getPosition();
            if (observerPosition.isInRange(observerLatitudeRange, observerLongitudeRange)
                    == false) {
                if (eliminationMode) {
                    selectedEntrySet.add(entry);
                }
                continue;
            }

            // event magnitude and position
            double eventMw = entry.getEvent().getEventData().getCmt().getMw();
            boolean magnitudeCheck = eventMwRange.check(eventMw);
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtPosition();
            boolean horizontalCheck = eventPosition.isInRange(eventLatitudeRange, eventLongitudeRange);
            boolean verticalCheck = eventDepthRange.check(eventPosition.getDepth());
            if ((magnitudeCheck && horizontalCheck && verticalCheck) == false) {
                if (eliminationMode) {
                    selectedEntrySet.add(entry);
                }
                continue;
            }

            // distance, azimuth, back-azimuth
            double distance = eventPosition.computeEpicentralDistanceRad(observerPosition) * 180. / Math.PI;
            boolean distanceCheck = distanceRange.check(distance);
            double azimuth = eventPosition.computeAzimuthRad(observerPosition) * 180. / Math.PI;
            boolean azimuthCheck = azimuthRange.check(azimuth);
            double backAzimuth = eventPosition.computeBackAzimuthRad(observerPosition) * 180. / Math.PI;
            boolean backAzimuthCheck = backAzimuthRange.check(backAzimuth);
            if ((distanceCheck && azimuthCheck && backAzimuthCheck) == false) {
                if (eliminationMode) {
                    selectedEntrySet.add(entry);
                }
                continue;
            }

            // turning point position and its azimuth
            if (selectTurningPosition || selectTurningAzimuth) {
                boolean turningPositionCheck = false;
                boolean turningAzimuthCheck = false;
                // conduct check when raypath of the specified phaseName exists; otherwise, false
                if (pierceTool.hasRaypaths(entry)) {
                    // When there are several raypaths for a given phase name, the first arrival is chosen.
                    // When there are multiple bottoming points for a raypath, the first one is used.
                    // Any phase (except for "p" or "s") should have a bottoming point, so a non-existence is not considered.
                    FullPosition turningPosition = pierceTool.get(entry, 0).findTurningPoint(0);
                    turningPositionCheck = turningPosition.isInRange(turningLatitudeRange, turningLongitudeRange);
                    double turningAzimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
                    turningAzimuthCheck = turningAzimuthRange.check(turningAzimuth);
                }
                if ((turningPositionCheck && turningAzimuthCheck) == false) {
                    if (eliminationMode) {
                        selectedEntrySet.add(entry);
                    }
                    continue;
                }
            }

            // the entry is selected, so add it
            if (!eliminationMode) {
                selectedEntrySet.add(entry);
            }
        }

        System.err.println(selectedEntrySet.size() + " data entries are selected.");
        if (selectedEntrySet.size() > 0) DataEntryListFile.writeFromSet(selectedEntrySet, outputSelectedPath);
    }

}
