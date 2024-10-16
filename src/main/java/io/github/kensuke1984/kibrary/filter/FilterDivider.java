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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that filters observed and synthetic SAC files in eventFolders under obsDir and synDir.
 * Resulting files will all be placed inside event folders under outDir.
 * <p>
 * SAC files that satisfy the following criteria will be filtered:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * Even synthetic SAC files under obsDir and observed SAC files under synDir will be processed.
 * Filtering will be done even if only observed (and not synthetic) SAC files exist, or vice versa.
 * If both obsDir and synDir are empty for a certain event, the corresponding event folder will not be created.
 * <p>
 * The lower and upper period limits of the filter will be written in headers USER0 and USER1 of resulting SAC files.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2021/11/18 moved from selection.FilterDivider to filter.FilterDivider
 */
public class FilterDivider extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Path of the output folder.
     */
    private Path outPath;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * The root folder containing event folders which have observed SAC files to be filtered.
     */
    private Path obsPath;
    /**
     * The root folder containing event folders which have synthetic SAC files to be filtered.
     */
    private Path synPath;

    private ButterworthFilter filter;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;
    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;
    /**
     * Type of filter to apply, from {lowpass, highpass, bandpass, bandstop}.
     */
    private String filterType;
    /**
     * Lower cut-off frequency [Hz].
     */
    private double lowFreq;
    /**
     * Upper cut-off frequency [Hz].
     */
    private double highFreq;
    /**
     * see Saito, n
     */
    private int filterNp;
    /**
     * Whether to apply causal filter. {true: causal, false: zero-phase}
     */
    private boolean causal;
    /**
     * SAC files with NPTS over this value will be slimmed.
     */
    private int npts;

    Set<DataEntry> entrySet;
    /**
     * Number of processed event folders
     */
    private AtomicInteger processedFolders = new AtomicInteger();

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be applied the filter, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. Files with a different value will be ignored. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Filter type to be applied, from {lowpass, highpass, bandpass, bandstop}. (bandpass)");
            pw.println("#filterType ");
            pw.println("##Lower limit of the frequency band [Hz]. (0.005)");
            pw.println("#lowFreq ");
            pw.println("##Higher limit of the frequency band [Hz]. (0.08)");
            pw.println("#highFreq ");
            pw.println("##(int) The value of NP for the filter. (4)");
            pw.println("#filterNp ");
            pw.println("##(boolean) Whether to apply causal filter. When false, zero-phase filter is applied. (false)");
            pw.println("#causal ");
            pw.println("##NPTS, only if you want to slim SAC files down to that specific number, must be a power of 2.");
            pw.println("##  When this is set, SAC files are slimmed. SAC files with a value of NPTS below the set value are not slimmed.");
            pw.println("#npts ");
        }
        System.err.println(outPath + " is created.");
    }

    public FilterDivider(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");

        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }

        filterType = property.parseString("filterType", "bandpass");
        lowFreq = property.parseDouble("lowFreq", "0.005");
        highFreq = property.parseDouble("highFreq", "0.08");
        filterNp = property.parseInt("filterNp", "4");
        causal = property.parseBoolean("causal", "false");
        npts = property.parseInt("npts", String.valueOf(Integer.highestOneBit(Integer.MAX_VALUE)));
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");
    }

    @Override
    public void run() throws IOException {
        setFilter();

        if (dataEntryPath != null) entrySet = DataEntryListFile.readAsSet(dataEntryPath);

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

        outPath = DatasetAid.createOutputFolder(workPath, "filtered", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Filtering SAC files ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + MathAid.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "% of events done");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all events.");
    }

    private Runnable process(EventFolder eventDir) {
        return () -> {
            try {
                Set<SACFileName> sacNameSet = eventDir.sacFileSet();
                sacNameSet.removeIf(s -> !judgeSAC(s));

                // escape if the event folder was blank. The 'finally' will be executed, so count will be incremented.
                if(sacNameSet.size() == 0) {
                    return;
                }

                Files.createDirectories(outPath.resolve(eventDir.getName()));
                sacNameSet.forEach(this::filterAndout);
            } catch (Exception e) {
                // if an exception is thrown, ignore that event folder and finish up the rest
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
    }

    private boolean judgeSAC(SACFileName sacName) {
        if (!components.contains(sacName.getComponent())) return false;
        try {
            SACHeaderAccess sacHeader = sacName.readHeader();
            if (entrySet != null && entrySet.contains(sacHeader.toDataEntry()) == false) return false;
            double delta = MathAid.roundForPrecision(1.0 / sacSamplingHz);
            if (sacHeader.getValue(SACHeaderEnum.DELTA) != delta) {
                System.err.println("! Sampling frequency is not " + sacSamplingHz + ", skipping: " + sacName.toString());
                return false;
            }
            return true;
        } catch (IOException e) {
            System.err.println("!! Failed to read header of " + sacName.toString() + ", skipping.");
            return false;
        }
    }

    private void setFilter() {
        double omegaH = highFreq * 2 * Math.PI / sacSamplingHz;
        double omegaL = lowFreq * 2 * Math.PI / sacSamplingHz;
        switch (filterType) {
            case "lowpass":
                System.err.println("Designing filter. - " + highFreq);
                filter = new LowPassFilter(omegaH, filterNp);
                break;
            case "highpass":
                System.err.println("Designing filter. " + lowFreq + " - ");
                filter = new HighPassFilter(omegaL, filterNp);
                break;
            case "bandpass":
                System.err.println("Designing filter. " + lowFreq + " - " + highFreq);
                filter = new BandPassFilter(omegaH, omegaL, filterNp);
                break;
            case "bandstop":
                System.err.println("Designing filter. - " + lowFreq + " , " + highFreq + " -");
                filter = new BandStopFilter(omegaH, omegaL, filterNp);
                break;
            default:
                throw new IllegalArgumentException("No such filter as " + filterType);
        }
        filter.setCausal(causal);
    }

    /**
     * Apply the filter on the sacFile and write in the outDir
     *
     * @param name a name of a SAC file to be filtered
     */
    private void filterAndout(SACFileName name) {
        try {
            SACFileAccess sacFile = name.read().applyButterworthFilter(filter);
            Path out = outPath.resolve(name.getGlobalCMTID().toString()).resolve(name.getName());
            // write SAC file. If there are SAC files with the same name, this throws an exception
            sacFile.writeSAC(out, StandardOpenOption.CREATE_NEW);
            if (npts < sacFile.getInt(SACHeaderEnum.NPTS)) slim(out);
        } catch (Exception e) {
            // if an exception is thrown, move on to the next SAC file
            System.err.println("Error on " + name.getPath());
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
