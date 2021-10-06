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
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;

/**
 * Java version First handler ported from the perl software.<br>
 * Processes extraction along the information file.
 * This extracts {@link SEEDFile}s under a
 * working golder
 * <p>
 * 解凍後の.seed ファイルをまとめる rdseed -fRd rtrend seed解凍後 channelが[BH]H[ENZ]のものだけから読み込む
 * <p>
 * <a href=http://ds.iris.edu/ds/nodes/dmc/manuals/rdseed/>rdseed</a> and <a
 * href=http://ds.iris.edu/ds/nodes/dmc/manuals/evalresp/>evalresp</a> must be
 * in PATH. </p> If you want to remove intermediate files.
 * <p>
 * TODO NPTSで合わないものを捨てる？
 * <p>
 * Even if a seed file contains both BH? and HH?, it will not throw errors,
 * however, no one knows which channel is used for extraction until you see the
 * intermediate files. If you want to see them, you have to leave the
 * intermediate files explicitly.
 * <p>
 * mseedに対応した (v0.3.1; 2021-08-24)
 * @author Kensuke Konishi & Kenji Kawai
 * @version 0.3.1
 */
public class DataKitchen implements Operation {

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
    /**
     * write directory
     */
    private Path outPath;
    private Path workPath;
    private Properties property;

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(DataKitchen.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan DataKitchen");
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##(String) a name of catalog to use from [cmt, pde]  (cmt)");
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
            pw.println("##Threshold to judge which stations are in the same position [deg] (0.01)"); // = about 1 km
            pw.println("##If two stations are closer to each other than this threshold, one will be eliminated.");
            pw.println("#coordinateGrid 0.01");
            pw.println("##(boolean) if this is true, remove intermediate files (true)");
            pw.println("#removeIntermediateFile");
        }
        System.err.println(outPath + " is created.");
    }

    public DataKitchen(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException(workPath + " (workPath)");
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
                throw new RuntimeException("Invalid catalog name.");
        }
        minDistance = Double.parseDouble(property.getProperty("minDistance"));
        maxDistance = Double.parseDouble(property.getProperty("maxDistance"));
        minLatitude = Double.parseDouble(property.getProperty("minLatitude"));
        maxLatitude = Double.parseDouble(property.getProperty("maxLatitude"));
        minLongitude = Double.parseDouble(property.getProperty("minLongitude"));
        maxLongitude = Double.parseDouble(property.getProperty("maxLongitude"));
        coordinateGrid = Double.parseDouble(property.getProperty("coordinateGrid"));
        removeIntermediateFile = Boolean.parseBoolean(property.getProperty("removeIntermediateFile"));
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

    /**
     * @param args [parameter file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        DataKitchen dk = new DataKitchen(Property.parse(args));
        long startT = System.nanoTime();
        System.err.println(DataKitchen.class.getName() + " is going");
        dk.run();
        System.err.println(
                DataKitchen.class.getName() + " finished in " + Utilities.toTimeString(System.nanoTime() - startT));
    }

    @Override
    public void run() throws IOException {
        if (!Files.exists(workPath)) throw new NoSuchFileException(workPath.toString());
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(workPath);
        if (eventDirs.isEmpty()) return;
        outPath = workPath.resolve("processed" + Utilities.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output directory is " + outPath);

        //
        Set<EventProcessor> processors = eventDirs.stream().map(eventDir -> {
           try {
                return new EventProcessor(eventDir, outPath);
            } catch (Exception e) {
                try {
                    System.err.println(eventDir + " has problems. " + e);
//                    Utilities.moveToDirectory(seedPath, ignoredSeedPath, true);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        processors.forEach(p -> p.setParameters(minDistance, maxDistance, minLatitude, maxLatitude,
                minLongitude, maxLongitude, coordinateGrid, removeIntermediateFile));

        int threadNum = Runtime.getRuntime().availableProcessors();
        ExecutorService es = Executors.newFixedThreadPool(threadNum);

        processors.forEach(es::submit);

        es.shutdown();
        try {
            while (!es.isTerminated()) Thread.sleep(1000 * 5);
        } catch (Exception e2) {
            e2.printStackTrace();
        }

        System.err.println("Result:");
        for (EventProcessor processor : processors) {
            if (!processor.hasRun()) System.err.println("! " + processor.getEventID() + " failed.");
            if (processor.hadProblem()) System.err.println("! " + processor.getEventID() + " encountered problems.");
        }
/*
        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        eventDirs.stream().map(this::process).forEach(es::submit);
        es.shutdown();
        while (!es.isTerminated()) {
            System.err.print("\rProcessing " + Math.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "%");
            Thread.sleep(100);
        }
        System.err.println("\rProcessing finished.");
        */
    }
/*
    private AtomicInteger processedFolders = new AtomicInteger(); // already processed

    private Runnable process(EventFolder folder) {
        return () -> {
            String eventname = folder.getName();
            try {
                RawSacSet set = new RawSacSet(folder, outPath);
                set.processAll();
            } catch (Exception e) {
                System.err.println("Error on " + folder);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
    }
*/
    @Override
    public Path getWorkPath() {
        return workPath;
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

}
