package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.aid.ThreadAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;

/**
 * Operation to process downloaded SAC (and RESP) files so that they can be used in the inversion process.
 * <p>
 * Event directories with a "sac" folder containing SAC files and a "resp" folder containing RESP files must be given as input.
 * Input SAC file names must be formatted (ex. "IU.MAJO.00.BH2.M.2014.202.14.44.00.000.SAC").
 * Output directory "processed*" will be created under the work path, and output event directories will be made under it.
 * Event if all data in an event directory are trashed, the empty event directory will be left.
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
public class DataKitchen implements Operation {

    private final Properties property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * Path of the output folder
     */
    private Path outPath;

    private double samplingHz;
    /**
     * which catalog to use 0:CMT 1: PDE
     */
    private int catalog;

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
     * if remove intermediate file
     */
    private boolean removeIntermediateFile;

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(DataKitchen.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan DataKitchen");
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##(String) The name of catalog to use from [cmt, pde]  (cmt)");
            pw.println("#catalog  CANT CHANGE NOW"); // TODO
            pw.println("##(double) Sampling Hz, can not be changed now (20)");
            pw.println("#samplingHz CANT CHANGE NOW");
            pw.println("##Lower limit of epicentral distance range [deg] [0:maxDistance) (0)");
            pw.println("#minDistance 70");
            pw.println("##Upper limit of epicentral distance range [deg] (minDistance:180] (180)");
            pw.println("#maxDistance 100");
            pw.println("##Lower limit of station latitude [deg] [-90:maxLatitude) (-90)");
            pw.println("#minLatitude");
            pw.println("##Upper limit of station latitude [deg] (minLatitude:90] (90)");
            pw.println("#maxLatitude");
            pw.println("##Lower limit of station longitude [deg] [-180:maxLongitude) (-180)");
            pw.println("#minLongitude");
            pw.println("##Upper limit of station longitude [deg] (minLongitude:360] (180)");
            pw.println("#maxLongitude");
            pw.println("##Threshold to judge which stations are in the same position, non-negative [deg] (0.01)"); // = about 1 km
            pw.println("##If two stations are closer to each other than this threshold, one will be eliminated.");
            pw.println("#coordinateGrid");
            pw.println("##(boolean) If this is true, remove intermediate files (true)");
            pw.println("#removeIntermediateFile");
        }
        System.err.println(outPath + " is created.");
    }

    public DataKitchen(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("catalog")) property.setProperty("catalog", "cmt");
        if (!property.containsKey("samplingHz")) property.setProperty("samplingHz", "20"); // TODO
        if (!property.containsKey("minDistance")) property.setProperty("minDistance", "0");
        if (!property.containsKey("maxDistance")) property.setProperty("maxDistance", "180");
        if (!property.containsKey("minLatitude")) property.setProperty("minLatitude", "-90");
        if (!property.containsKey("maxLatitude")) property.setProperty("maxLatitude", "90");
        if (!property.containsKey("minLongitude")) property.setProperty("minLongitude", "-180");
        if (!property.containsKey("maxLongitude")) property.setProperty("maxLongitude", "180");
        if (!property.containsKey("coordinateGrid")) property.setProperty("coordinateGrid", "0.01");
        if (!property.containsKey("removeIntermediateFile")) property.setProperty("removeIntermediateFile", "true");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        switch (property.getProperty("catalog")) {
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
        minDistance = Double.parseDouble(property.getProperty("minDistance"));
        maxDistance = Double.parseDouble(property.getProperty("maxDistance"));
        if (minDistance < 0 || minDistance > maxDistance || 180 < maxDistance)
            throw new IllegalArgumentException("Distance range " + minDistance + " , " + maxDistance + " is invalid.");
        minLatitude = Double.parseDouble(property.getProperty("minLatitude"));
        maxLatitude = Double.parseDouble(property.getProperty("maxLatitude"));
        if (minLatitude < -90 || minLatitude > maxLatitude || 90 < maxLatitude)
            throw new IllegalArgumentException("Latitude range " + minLatitude + " , " + maxLatitude + " is invalid.");
        minLongitude = Double.parseDouble(property.getProperty("minLongitude"));
        maxLongitude = Double.parseDouble(property.getProperty("maxLongitude"));
        if (minLongitude < -180 || minLongitude > maxLongitude || 360 < maxLongitude)
            throw new IllegalArgumentException("Longitude range " + minLongitude + " , " + maxLongitude + " is invalid.");
        coordinateGrid = Double.parseDouble(property.getProperty("coordinateGrid"));
        if (coordinateGrid < 0)
            throw new IllegalArgumentException("coordinateGrid must be non-negative.");
        removeIntermediateFile = Boolean.parseBoolean(property.getProperty("removeIntermediateFile"));
    }

    /**
     * @param args [parameter file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        DataKitchen dk = new DataKitchen(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(DataKitchen.class.getName() + " is operating.");
        dk.run();
        System.err.println(DataKitchen.class.getName() + " finished in " +
                Utilities.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws IOException {
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(workPath);
        if (eventDirs.isEmpty()) {
            System.err.println("No events found.");
            return;
        }

        outPath = workPath.resolve("processed" + Utilities.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        // create processors for each event
        Set<EventProcessor> eps = eventDirs.stream().map(eventDir -> {
           try {
                return new EventProcessor(eventDir, outPath);
            } catch (Exception e) {
                // If there is something wrong, skip the event (suppress exceptions).
                try {
                    System.err.println(eventDir + " has problems. ");
                    e.printStackTrace();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        // set parameters
        eps.forEach(p -> p.setParameters(minDistance, maxDistance, minLatitude, maxLatitude,
                minLongitude, maxLongitude, coordinateGrid, removeIntermediateFile));

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

    @Override
    public Path getWorkPath() {
        return workPath;
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

}
