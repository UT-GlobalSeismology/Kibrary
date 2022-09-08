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
import io.github.kensuke1984.kibrary.external.TauPPierceReader;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
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
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output data entry list file
     */
    private Path outputSelectedPath;
    /**
     * components for computation
     */
    private Set<SACComponent> components;

    /**
     * Path of the input data entry list file
     */
    private Path dataEntryPath;

    /**
     * Whether to eliminate certaion raypaths or to extract them
     */
    private boolean eliminationMode;

    private double lowerEventMw;
    private double upperEventMw;
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
    private double lowerTurningLatitude;
    private double upperTurningLatitude;
    private double lowerTurningLongitude;
    private double upperTurningLongitude;
    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;
    private double lowerBackAzimuth;
    private double upperBackAzimuth;
    private double lowerTurningAzimuth;
    private double upperTurningAzimuth;
    /**
     * Whether criteria for turning point position exists
     */
    private boolean selectTurningPosition;
    /**
     * Whether criteria for turning point azimuth exists
     */
    private boolean selectTurningAzimuth;
    /**
     * Name of structure to use for calculating turning point
     */
    private String structureName;
    /**
     * Phase to use when computing turning point
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
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Sac components to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a data entry list file, must be defined");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##(boolean) Whether to eliminate the specified raypaths instead of extracting them (false)");
            pw.println("#eliminationMode");
            pw.println("##########Raypaths that satisfy all of the following criteria will be extracted/eliminated.");
            pw.println("##########Selection criteria of events##########");
            pw.println("##(double) Lower limit of Mw (0)");
            pw.println("#lowerEventMw ");
            pw.println("##(double) Upper limit of Mw (10)");
            pw.println("#upperEventMw ");
            pw.println("##(double) Shallower limit of event DEPTH (0)");
            pw.println("#lowerEventDepth NOT SUPPORTED YET");
            pw.println("##(double) Deeper limit of event DEPTH (1000)");
            pw.println("#upperEventDepth NOT SUPPORTED YET");
            pw.println("##(double) Lower limit of event latitude [deg] [-90:upperEventLatitude) (-90)");
            pw.println("#lowerEventLatitude");
            pw.println("##(double) Upper limit of event latitude [deg] (lowerEventLatitude:90] (90)");
            pw.println("#upperEventLatitude");
            pw.println("##(double) Lower limit of event longitude [deg] [-180:upperEventLongitude) (-180)");
            pw.println("#lowerEventLongitude");
            pw.println("##(double) Upper limit of event longitude [deg] (lowerEventLongitude:360] (180)");
            pw.println("#upperEventLongitude");
            pw.println("##########Selection criteria of observers##########");
            pw.println("##(double) Lower limit of observer latitude [deg] [-90:upperObserverLatitude) (-90)");
            pw.println("#lowerObserverLatitude");
            pw.println("##(double) Upper limit of observer latitude [deg] (lowerObserverLatitude:90] (90)");
            pw.println("#upperObserverLatitude");
            pw.println("##(double) Lower limit of observer longitude [deg] [-180:upperObserverLongitude) (-180)");
            pw.println("#lowerObserverLongitude");
            pw.println("##(double) Upper limit of observer longitude [deg] (lowerObserverLongitude:360] (180)");
            pw.println("#upperObserverLongitude");
            pw.println("##########Selection criteria of turning points##########");
            pw.println("##(double) Lower limit of turning point latitude [deg] [-90:upperTurningLatitude) (-90)");
            pw.println("#lowerTurningLatitude");
            pw.println("##(double) Upper limit of turning point latitude [deg] (lowerTurningLatitude:90] (90)");
            pw.println("#upperTurningLatitude");
            pw.println("##(double) Lower limit of turning point longitude [deg] [-180:upperTurningLongitude) (-180)");
            pw.println("#lowerTurningLongitude");
            pw.println("##(double) Upper limit of turning point longitude [deg] (lowerTurningLongitude:360] (180)");
            pw.println("#upperTurningLongitude");
            pw.println("##########Selection criteria of raypaths##########");
            pw.println("##(double) Lower limit of epicentral distance range [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance 70");
            pw.println("##(double) Upper limit of epicentral distance range [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance 100");
            pw.println("##(double) Lower limit of azimuth range [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth");
            pw.println("##(double) Upper limit of azimuth range [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth");
            pw.println("##(double) Lower limit of back azimuth range [deg] [-360:upperBackAzimuth) (0)");
            pw.println("#lowerBackAzimuth");
            pw.println("##(double) Upper limit of back azimuth range [deg] (lowerBackAzimuth:360] (360)");
            pw.println("#upperBackAzimuth");
            pw.println("##(double) Lower limit of turning point azimuth range [deg] [-360:upperTurningAzimuth) (0)");
            pw.println("#lowerTurningAzimuth");
            pw.println("##(double) Upper limit of turning point azimuth range [deg] (lowerTurningAzimuth:360] (360)");
            pw.println("#upperTurningAzimuth");
            pw.println("##########When criteria for turning points are set, the following is used:##########");
            pw.println("##(String) Name of structure to use for calculating turning point (prem)");
            pw.println("#structureName ");
            pw.println("##Phase to compute turning point for (ScS)");
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
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        eliminationMode = property.parseBoolean("eliminationMode", "false");

        lowerEventMw = property.parseDouble("lowerMw", "0.");
        upperEventMw = property.parseDouble("upperMw", "10.");
        if (lowerEventMw > upperEventMw)
            throw new IllegalArgumentException("Event magnitude range " + lowerEventMw + " , " + upperEventMw + " is invalid.");
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

        if (property.containsKey("lowerTurningLatitude") || property.containsKey("upperTurningLatitude") ||
                property.containsKey("lowerTurningLongitude") || property.containsKey("upperTurningLongitude")) {
            selectTurningPosition = true;
        }
        lowerTurningLatitude = property.parseDouble("lowerTurningLatitude", "-90");
        upperTurningLatitude = property.parseDouble("upperTurningLatitude", "90");
        if (lowerTurningLatitude < -90 || lowerTurningLatitude > upperTurningLatitude || 90 < upperTurningLatitude)
            throw new IllegalArgumentException("Turning point latitude range " + lowerTurningLatitude + " , " + upperTurningLatitude + " is invalid.");
        lowerTurningLongitude = property.parseDouble("lowerTurningLongitude", "-180");
        upperTurningLongitude = property.parseDouble("upperTurningLongitude", "180");
        if (lowerTurningLongitude < -180 || lowerTurningLongitude > upperTurningLongitude || 360 < upperTurningLongitude)
            throw new IllegalArgumentException("Turning point longitude range " + lowerTurningLongitude + " , " + upperTurningLongitude + " is invalid.");

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");
        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");
        lowerBackAzimuth = property.parseDouble("lowerBackAzimuth", "0");
        upperBackAzimuth = property.parseDouble("upperBackAzimuth", "360");
        if (lowerBackAzimuth < -360 || lowerBackAzimuth > upperBackAzimuth || 360 < upperBackAzimuth)
            throw new IllegalArgumentException("Back-azimuth range " + lowerBackAzimuth + " , " + upperBackAzimuth + " is invalid.");

        if (property.containsKey("lowerTurningAzimuth") || property.containsKey("upperTurningAzimuth")) {
            selectTurningAzimuth = true;
        }
        lowerTurningAzimuth = property.parseDouble("lowerTurningAzimuth", "0");
        upperTurningAzimuth = property.parseDouble("upperTurningAzimuth", "360");
        if (lowerTurningAzimuth < -360 || lowerTurningAzimuth > upperTurningAzimuth || 360 < upperTurningAzimuth)
            throw new IllegalArgumentException("Turning point azimuth range " + lowerTurningAzimuth + " , " + upperTurningAzimuth + " is invalid.");

        structureName = property.parseString("structureName", "prem");
        turningPointPhase = property.parseString("turningPointPhase", "ScS");

        String dateStr = GadgetAid.getTemporaryString();
        outputSelectedPath = workPath.resolve(DatasetAid.generateOutputFileName("selectedEntry", tag, dateStr, ".lst"));
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);
        Set<DataEntry> selectedEntrySet = new HashSet<>();

        // compute turning points if needed
        TauPPierceReader pierceTool = null;
        if (selectTurningPosition || selectTurningAzimuth) {
            try {
                pierceTool = new TauPPierceReader(structureName, turningPointPhase);
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
            if (observerPosition.isInRange(lowerObserverLatitude, upperObserverLatitude, lowerObserverLongitude, upperObserverLongitude)
                    == false) {
                if (eliminationMode) {
                    selectedEntrySet.add(entry);
                }
                continue;
            }

            // event magnitude and position
            double eventMw = entry.getEvent().getEventData().getCmt().getMw();
            boolean magnitudeCheck = (lowerEventMw <= eventMw && eventMw <= upperEventMw);
            FullPosition eventPosition = entry.getEvent().getEventData().getCmtLocation();
            boolean horizontalCheck = eventPosition.isInRange(lowerEventLatitude, upperEventLatitude, lowerEventLongitude, upperEventLongitude);
            double depth = eventPosition.getDepth();
            boolean verticalCheck = (lowerEventDepth <= depth && depth <= upperEventDepth);
            if ((magnitudeCheck && horizontalCheck && verticalCheck) == false) {
                if (eliminationMode) {
                    selectedEntrySet.add(entry);
                }
                continue;
            }

            // distance, azimuth, back-azimuth
            double distance = eventPosition.computeEpicentralDistance(observerPosition) * 180. / Math.PI;
            boolean distanceCheck = (lowerDistance <= distance && distance <= upperDistance);
            double azimuth = eventPosition.computeAzimuth(observerPosition) * 180. / Math.PI;
            boolean azimuthCheck = MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth);
            double backAzimuth = eventPosition.computeBackAzimuth(observerPosition) * 180. / Math.PI;
            boolean backAzimuthCheck = MathAid.checkAngleRange(backAzimuth, lowerBackAzimuth, upperBackAzimuth);
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
                    turningPositionCheck = turningPosition.isInRange(lowerTurningLatitude, upperTurningLatitude, lowerTurningLongitude, upperTurningLongitude);
                    double turningAzimuth = pierceTool.get(entry, 0).computeTurningAzimuthDeg(0);
                    turningAzimuthCheck = MathAid.checkAngleRange(turningAzimuth, lowerTurningAzimuth, upperTurningAzimuth);
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

        System.err.println("Outputting selected entries in " + outputSelectedPath);
        DataEntryListFile.writeFromSet(selectedEntrySet, outputSelectedPath);
        System.err.println(selectedEntrySet.size() + " entries were selected.");
    }

}
