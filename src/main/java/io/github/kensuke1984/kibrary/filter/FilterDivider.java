package io.github.kensuke1984.kibrary.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation_new;
import io.github.kensuke1984.kibrary.Property_new;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that filters observed and synthetic SAC files in eventFolders under obsDir and synDir.
 * Resulting files will all be placed inside event folders under outDir.
 * <p>
 * Filtering will be done even if only observed (and not synthetic) SAC files exist, or vice versa.
 * If both obsDir and synDir is empty for a certain event, the corresponding event folder will not be created.
 * <p>
 * The lower and upper period limits of the filter will be written in headers USER0 and USER1 of resulting SAC files.
 *
 */
public class FilterDivider extends Operation_new {

    private final Property_new property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * Path of the output folder
     */
    private Path outPath;
    /**
     * The root folder containing event folders which have observed SAC files to
     * be filtered
     */
    private Path obsPath;
    /**
     * The root folder containing event folders which have synthetic SAC files
     * to be filtered
     */
    private Path synPath;

    private ButterworthFilter filter;
    /**
     * The value 'DELTA' in SAC files.
     * TODO: The SAC files with another value of 'DELTA' are to be ignored.
     */
    private double delta;
    /**
     * components to be applied the filter
     */
    private Set<SACComponent> components;
    /**
     * Type of filter to apply, from {lowpass, highpass, bandpass, bandstop}
     */
    private String filterType;
    /**
     * minimum frequency [Hz] フィルターバンドの最小周波数
     */
    private double lowFreq;
    /**
     * maximum frequency [Hz] フィルターバンドの最大周波数
     */
    private double highFreq;
    /**
     * If backward computation is performed. true: zero-phase false: causal
     */
    private boolean backward;
    /**
     * see Saito, n
     */
    private int np;

    /**
     * SAC files with NPTS over this value will be slimmed.
     */
    private int npts;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation_new.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property_new.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath");
            pw.println("##SacComponents to be applied the filter, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a root folder containing observed dataset (.)");
            pw.println("#obsPath");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#synPath");
            pw.println("##DELTA in SAC files. The SAC files with other values of DELTA are to be ignored. (0.05)");
            pw.println("#delta");
            pw.println("##Filter type to be applied, from {lowpass, highpass, bandpass, bandstop} (bandpass)");
            pw.println("#filterType");
            pw.println("##Lower limit of the frequency band [Hz] (0.005)");
            pw.println("#lowFreq");
            pw.println("##Higher limit of the frequency band [Hz] (0.08)");
            pw.println("#highFreq");
            pw.println("##The value of NP for the filter (4)");
            pw.println("#np");
            pw.println("##If backward computation is performed. true: zero phase, false: causal  (true)");
            pw.println("#backward");
            pw.println("##NPTS, only if you want to slim SAC files down to that specific number, must be a power of 2");
            pw.println(
                    "##When this npts is set, SAC files are slimmed. SAC files with a value of NPTS over the set value are not slimmed.");
            pw.println("#npts");
        }
        System.err.println(outPath + " is created.");
    }

    public FilterDivider(Properties property) throws IOException {
        this.property = (Property_new) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", "", true, Paths.get(""));
        components = Arrays.stream(property.parseString("components", "Z R T")
                .split("\\s+")).map(SACComponent::valueOf).collect(Collectors.toSet());

        obsPath = property.parsePath("obsPath", "", true, workPath);
        synPath = property.parsePath("synPath", "", true, workPath);

        delta = property.parseDouble("delta", "0.05");
        filterType = property.parseString("filterType", "bandpass");
        highFreq = property.parseDouble("highFreq", "0.08");
        lowFreq = property.parseDouble("lowFreq", "0.005");
        backward = property.parseBoolean("backward", "true");
        np = property.parseInt("np", "4");
        npts = property.parseInt("npts", String.valueOf(Integer.MAX_VALUE));

    }

/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("obsPath")) property.setProperty("obsPath", "");
        if (!property.containsKey("synPath")) property.setProperty("synPath", "");
        if (!property.containsKey("delta")) property.setProperty("delta", "0.05");
        if (!property.containsKey("highFreq")) property.setProperty("highFreq", "0.08");
        if (!property.containsKey("lowFreq")) property.setProperty("lowFreq", "0.005");
        if (!property.containsKey("backward")) property.setProperty("backward", "true");
        if (!property.containsKey("np")) property.setProperty("np", "4");
        if (!property.containsKey("filter")) property.setProperty("filter", "bandpass");
        if (!property.containsKey("npts")) property.setProperty("npts", String.valueOf(Integer.MAX_VALUE));
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        obsPath = getPath("obsPath");
        if (!Files.exists(obsPath)) throw new NoSuchFileException("The obsPath " + obsPath + " does not exist");
        synPath = getPath("synPath");
        if (!Files.exists(synPath)) throw new NoSuchFileException("The synPath " + synPath + " does not exist");

        delta = Double.parseDouble(property.getProperty("delta"));
        highFreq = Double.parseDouble(property.getProperty("highFreq"));
        lowFreq = Double.parseDouble(property.getProperty("lowFreq"));
        backward = Boolean.parseBoolean(property.getProperty("backward"));
        np = Integer.parseInt(property.getProperty("np"));
        npts = Integer.parseInt(property.getProperty("npts"));
    }
*/

    @Override
    public void run() throws IOException {
        setFilter(lowFreq, highFreq, np);

        Set<EventFolder> eventDirs = new HashSet<>();
        eventDirs.addAll(Files.exists(obsPath) ? DatasetAid.eventFolderSet(obsPath) : Collections.emptySet());
        int obsNum = eventDirs.size();
        System.err.println("Number of events in obsDir: " + obsNum);
        eventDirs.addAll(Files.exists(synPath) ? DatasetAid.eventFolderSet(synPath) : Collections.emptySet());
        int totalNum = eventDirs.size();
        System.err.println("Number of events in synDir: " + (totalNum - obsNum));
        if (totalNum == 0) {
            System.err.println("No events found.");
            return;
        }

        outPath = workPath.resolve("filtered" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Filtering SAC files ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + Math.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "% of events done");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all events.");
    }

    private AtomicInteger processedFolders = new AtomicInteger(); // already processed

    private Runnable process(EventFolder eventDir) {
        return () -> {
            String eventname = eventDir.getName();
            try {
                Set<SACFileName> set = eventDir.sacFileSet();
                set.removeIf(s -> !components.contains(s.getComponent()));

                // escape if the event folder was blank. The 'finally' will be executed, so count will be incremented.
                if(set.size() == 0) {
                    return;
                }

                Files.createDirectories(outPath.resolve(eventname));
                set.forEach(this::filterAndout);
            } catch (Exception e) {
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
    }

    /**
     * @param fMin [Hz] 透過帯域 最小周波数
     * @param fMax [Hz] 透過帯域 最大周波数
     * @param n    parameter n
     */
    private void setFilter(double fMin, double fMax, int n) {
        double omegaH = fMax * 2 * Math.PI * delta;
        double omegaL = fMin * 2 * Math.PI * delta;
        switch (filterType) {
            case "lowpass":
                filter = new LowPassFilter(omegaL, n);
                break;
            case "highpass":
                filter = new HighPassFilter(omegaH, n);
                break;
            case "bandpass":
                filter = new BandPassFilter(omegaH, omegaL, n);
                break;
            case "bandstop":
                filter = new BandStopFilter(omegaH, omegaL, n);
                break;
            default:
                throw new IllegalArgumentException("No such filter as " + filterType);
        }
        filter.setBackward(backward);
    }

    /**
     * Apply the filter on the sacFile and write in the outDir
     *
     * @param name a name of a SAC file to be filtered
     */
    private void filterAndout(SACFileName name) {
        try {
            SACFileAccess sacFile = name.read().applyButterworthFilter(filter);
            Path out = outPath.resolve(name.getGlobalCMTID() + "/" + name.getName());
            sacFile.writeSAC(out);
            if (npts < sacFile.getInt(SACHeaderEnum.NPTS)) slim(out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void slim(Path path) throws IOException {
        try (SAC sac = SAC.createProcess()) {
            sac.inputCMD("cut b n " + npts);
            sac.inputCMD("r " + path.toAbsolutePath());
            sac.inputCMD("w over");
        }
    }

}
