package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;

/**
 * Operation to process downloaded SAC (and RESP) files so that they can be used in the inversion process.
 * <p>
 * Event directories with a "sac" folder containing SAC files and a "resp" folder containing RESP files must be given as input.
 * Input SAC file names must be formatted (ex. "IU.MAJO.00.BH2.M.2014.202.14.44.00.000.SAC").
 * Output directory "processed*" will be created under the work path, and output event directories will be made under it.
 * The log of trashed SAC files will be displayed in standard error and also written in log files under each event directory.
 * Even if all data in an event directory are trashed, the empty event directory will be left.
 * In default settings, intermediate files created during this process will be deleted at the end.
 * If you want to see them, you have to explicitly specify to leave them.
 * <p>
 * See also {@link EventProcessor}.
 * <p>
 * This class is a modification of FirstHandler, which was the Java version of First handler ported from the perl software.
 * <p>
 * TODO NPTSで合わないものを捨てる？
 *
 * @since 2021/09/14
 * @author otsuru
 */
public class DataKitchen extends Operation {

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
     * Path of the output folder
     */
    private Path outPath;

    /**
     * which catalog to use 0:CMT 1: PDE
     */
    private int catalog;
    private double samplingHz;

    private double minDistance;
    private double maxDistance;
    private double minLatitude;
    private double maxLatitude;
    private double minLongitude;
    private double maxLongitude;
    /**
     * threshold to judge which stations are in the same position [deg]
     */
    private double coordinateGrid;
    /**
     * The maximum length of output time series
     */
    private double maxTlen;
    /**
     * if remove intermediate file
     */
    private boolean removeIntermediateFile;

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##The name of catalog to use from {cmt, pde}  (cmt)");
            pw.println("#catalog  CANT CHANGE NOW"); // TODO
            pw.println("##(double) Sampling Hz, can not be changed now (20)");
            pw.println("#samplingHz CANT CHANGE NOW");
            pw.println("##Lower limit of epicentral distance range [deg] [0:maxDistance) (0)");
            pw.println("#minDistance 70");
            pw.println("##Upper limit of epicentral distance range [deg] (minDistance:180] (180)");
            pw.println("#maxDistance 100");
            pw.println("##Lower limit of station latitude [deg] [-90:maxLatitude) (-90)");
            pw.println("#minLatitude ");
            pw.println("##Upper limit of station latitude [deg] (minLatitude:90] (90)");
            pw.println("#maxLatitude ");
            pw.println("##Lower limit of station longitude [deg] [-180:maxLongitude) (-180)");
            pw.println("#minLongitude ");
            pw.println("##Upper limit of station longitude [deg] (minLongitude:360] (180)");
            pw.println("#maxLongitude ");
            pw.println("##Threshold to judge which stations are in the same position, non-negative [deg] (0.01)"); // = about 1 km
            pw.println("## If two stations are closer to each other than this threshold, one will be eliminated.");
            pw.println("#coordinateGrid ");
            pw.println("##(double) The maximum length of output time series (3276.8)");
            pw.println("## This should be shorter than 20 times the earliest arrival time of the phases you wish to use.");
            pw.println("## The acutal length will be decided so that npts is a power of 2 and does not exceed this timelength nor the SAC data length.");
            pw.println("#maxTlen ");
            pw.println("##(boolean) If this is true, remove intermediate files (true)");
            pw.println("#removeIntermediateFile ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataKitchen(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        switch (property.parseString("catalog", "cmt")) { // TODO
            case "cmt":
            case "CMT":
                catalog = 0;
                break;
            case "pde":
            case "PDE":
                catalog = 0;
                break;
            default:
                throw new IllegalArgumentException("Invalid catalog name.");
        }
        samplingHz = property.parseDouble("samplingHz", "20"); // TODO

        minDistance = property.parseDouble("minDistance", "0");
        maxDistance = property.parseDouble("maxDistance", "180");
        if (minDistance < 0 || minDistance > maxDistance || 180 < maxDistance)
            throw new IllegalArgumentException("Distance range " + minDistance + " , " + maxDistance + " is invalid.");

        minLatitude = property.parseDouble("minLatitude", "-90");
        maxLatitude = property.parseDouble("maxLatitude", "90");
        if (minLatitude < -90 || minLatitude > maxLatitude || 90 < maxLatitude)
            throw new IllegalArgumentException("Latitude range " + minLatitude + " , " + maxLatitude + " is invalid.");

        minLongitude = property.parseDouble("minLongitude", "-180");
        maxLongitude = property.parseDouble("maxLongitude", "180");
        if (minLongitude < -180 || minLongitude > maxLongitude || 360 < maxLongitude)
            throw new IllegalArgumentException("Longitude range " + minLongitude + " , " + maxLongitude + " is invalid.");

        coordinateGrid = property.parseDouble("coordinateGrid", "0.01");
        if (coordinateGrid < 0)
            throw new IllegalArgumentException("coordinateGrid must be non-negative.");

        maxTlen = property.parseDouble("maxTlen", "3276.8");
        removeIntermediateFile = property.parseBoolean("removeIntermediateFile", "true");
    }

    @Override
    public void run() throws IOException {
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(workPath);
        if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
            return;
        }

        outPath = DatasetAid.createOutputFolder(workPath, "processed", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // create processors for each event
        Set<EventProcessor> eps = eventDirs.stream().map(eventDir -> {
           try {
                return new EventProcessor(eventDir, outPath);
            } catch (Exception e) {
                // If there is something wrong, skip the event (suppress exceptions).
                try {
                    System.err.println("!!! " + eventDir + " has problems. ");
                    e.printStackTrace();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        // set parameters
        eps.forEach(p -> p.setParameters(minDistance, maxDistance, minLatitude, maxLatitude,
                minLongitude, maxLongitude, coordinateGrid, maxTlen, removeIntermediateFile));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eps.forEach(es::execute);
        es.shutdown();
        // check if everything is done every 5 seconds
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000 * 5);
        }

        // print overall result
        boolean success = true;
        System.err.println("Overall result:");
        for (EventProcessor processor : eps) {
            if (!processor.hasRun()) {
                System.err.println("! " + processor.getEventID() + " failed.");
                success = false;
            }
            if (processor.hadProblem()) {
                System.err.println("! " + processor.getEventID() + " encountered problems during execution.");
                success = false;
            }
        }
        if (success) {
            System.err.println(" Everything succeeded!");
        }
    }

}
