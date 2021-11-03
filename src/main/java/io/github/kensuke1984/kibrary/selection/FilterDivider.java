package io.github.kensuke1984.kibrary.selection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.butterworth.BandPassFilter;
import io.github.kensuke1984.kibrary.butterworth.BandStopFilter;
import io.github.kensuke1984.kibrary.butterworth.ButterworthFilter;
import io.github.kensuke1984.kibrary.butterworth.HighPassFilter;
import io.github.kensuke1984.kibrary.butterworth.LowPassFilter;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that filters observed and synthetic SAC files in eventFolders under obsDir and synDir.
 *
 * 観測波形ディレクトリobsDir、理論波形ディレクトリsynDir双方の下に存在するイベントフォルダの理論波形と観測波形に フィルターを掛ける <br>
 * できたファイルはoutDir下にイベントフォルダを作りそこにつくる sacのUSER0とUSER1に最短周期、最長周期の情報を書き込む
 *
 * @author Kensuke Konishi
 * @version 0.2.4
 */
public class FilterDivider implements Operation {

    private final Properties property;
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
     * The value 'DELTA' in SAC files. The SAC files with another value of
     * 'DELTA' are to be ignored.
     */
    private double delta;
    /**
     * components to be applied the filter
     */
    private Set<SACComponent> components;
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

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(FilterDivider.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan FilterDivider");
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
            pw.println("##Lower limit of the frequency band [Hz] (0.005)");
            pw.println("#lowFreq");
            pw.println("##Higher limit of the frequency band [Hz] (0.08)");
            pw.println("#highFreq");
            pw.println("##Filter type to be applied (bandpass)");
            pw.println("##The filter can be 'lowpass', 'highpass', 'bandpass', 'bandstop'");
            pw.println("#filter");
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
        this.property = (Properties) property.clone();
        set();
    }

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

    /**
     * @param args [a property file name]
     * @throws Exception if any
     */
    public static void main(String[] args) throws Exception {
        FilterDivider divider = new FilterDivider(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(FilterDivider.class.getName() + " is going.");
        divider.run();
        System.err.println(FilterDivider.class.getName() + " finished in " +
                Utilities.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws Exception {
        setFilter(lowFreq, highFreq, np);
        Set<EventFolder> events = new HashSet<>();
        events.addAll(Files.exists(obsPath) ? Utilities.eventFolderSet(obsPath) : Collections.emptySet());
        events.addAll(Files.exists(synPath) ? Utilities.eventFolderSet(synPath) : Collections.emptySet());
        if (events.isEmpty()) return;

        outPath = workPath.resolve("filtered" + Utilities.getTemporaryString());
        System.err.println("Output folder is " + outPath);
        Files.createDirectories(outPath);

        int nThreads = Runtime.getRuntime().availableProcessors();
        System.err.println("Running on " + nThreads + " processors");
        ExecutorService es = Executors.newFixedThreadPool(nThreads);

        events.stream().map(this::process).forEach(es::submit);
        es.shutdown();

        while (!es.isTerminated()) {
            System.err.print("\rFiltering " + Math.ceil(100.0 * processedFolders.get() / events.size()) + "%");
            Thread.sleep(100);
        }
        System.err.println("\rFiltering finished.");
    }

    private AtomicInteger processedFolders = new AtomicInteger(); // already processed

    private Runnable process(EventFolder folder) {
        return () -> {
            String eventname = folder.getName();
            try {
                Set<SACFileName> set = folder.sacFileSet();
                Files.createDirectories(outPath.resolve(eventname));
                set.removeIf(s -> !components.contains(s.getComponent()));
                set.forEach(this::filterAndout);
            } catch (Exception e) {
                System.err.println("Error on " + folder);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
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

    /**
     * @param fMin [Hz] 透過帯域 最小周波数
     * @param fMax [Hz] 透過帯域 最大周波数
     * @param n    parameter n
     */
    private void setFilter(double fMin, double fMax, int n) {
        double omegaH = fMax * 2 * Math.PI * delta;
        double omegaL = fMin * 2 * Math.PI * delta;
        switch (property.getProperty("filter")) {
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
                throw new RuntimeException("No such filter.");
        }
        filter.setBackward(backward);
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

    @Override
    public Path getWorkPath() {
        return workPath;
    }

}
