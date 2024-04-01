package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Operation that creates a data file of timewindows.
 * <p>
 * Either a {@link DataEntryListFile} or a dataset folder containing observed SAC files should be given as input.
 * <p>
 * When a dataset folder is given as input,
 * timewindows are created for all SAC files in event folders under obsDir that satisfy the following criteria:
 * <ul>
 * <li> is observed waveform </li>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * <p>
 * When a {@link DataEntryListFile} is provided as input,
 * timewindows are created for all data entries that satisfy the following criteria:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * </ul>
 * <p>
 * For each input (event, observer, component) data pair,
 * several timewindows may be created for different phases.
 * Thus, output data can be specified as a (event, observer, component, timeframe)-pair.
 * <p>
 * This class creates a window for each specified phase,
 * starting from (arrival time - frontShift) and ending at (arrival time + rearShift).
 * It also creates a window for each phase to be avoided,
 * starting from (arrival time - avoidFrontShift) and ending at (arrival time + avoidRearShift),
 * and abandons overlapped parts between these.
 * Arrival times are computed by TauP.
 * <p>
 * Timewindow information is written in binary format in "timewindow*.dat".
 * Data entries that could not produce timewindows are written in "invalidTimewindow*.txt".
 * Travel time information is written in "travelTime*.inf".
 * See {@link TimewindowDataFile}.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class TimewindowMaker extends Operation {

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
     * Path of the output timewindow file.
     */
    private Path outTimewindowPath;
    /**
     * Path of output file to list up SAC files that could not produce timewindows.
     */
    private Path outInvalidPath;
    /**
     * Path of the output travel time file.
     */
    private Path outTravelTimePath;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;
    /**
     * Path of a root folder containing observed dataset.
     */
    private Path obsPath;

    /**
     * Phases to be included in timewindows.
     */
    private Set<Phase> usePhases;
    /**
     * Phases not to be included in timewindows.
     */
    private Set<Phase> avoidPhases;
    /**
     * Time length that the timewindow shall include before each phase arrival [sec].
     * If the value is 5 (not -5), each timewindow starts 5 sec before the first usePhase arrival.
     */
    private double frontShift;
    /**
     * Time length that the timewindow shall include after each phase arrival [sec].
     * If the value is 10, each timewindow ends 10 secs after the last usephase arrival.
     */
    private double rearShift;
    /**
     * Time length that the timewindow must not include before each arrival of phases to be avoided [sec].
     */
    private double avoidFrontShift;
    /**
     * Time length that the timewindow must not include after each arrival of phases to be avoided [sec].
     */
    private double avoidRearShift;

    /**
     * Minimum length of each timewindow.
     */
    private double minLength;

    /**
     * Whether to allow split timewindows.
     * If not, timewindows are discarded when avoidPhases arrive between or near usePhases
     * and blanks are filled when timewindows of usePhases aren't overlapped.
     */
    private boolean allowSplitWindows;
    /**
     * Name of structure to compute travel times.
     */
    private String structureName;

    private boolean majorArc;
    /**
     * Whether to use duplicate arrivals of usePhases when deciding timewindows.
     * In case of triplication of usePhases, use only the first arrival.
     */
    private boolean useDuplicatePhases;

    private Set<DataEntry> entrySet;
    private Set<TimewindowData> timewindowSet = Collections.synchronizedSet(new HashSet<>());
    private Set<TravelTimeInformation> travelTimeSet = Collections.synchronizedSet(new HashSet<>());

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
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##########Input: either a data entry list file or a dataset folder will be read.");
            pw.println("##Path of a data entry list file. If not set, the following obsPath will be used.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##########Settings of timewindows to be created.");
            pw.println("##TauPPhases to be included in timewindow, listed using spaces. (S)");
            pw.println("#usePhases S ScS");
            pw.println("##TauPPhases not to be included in timewindow, listed using spaces, if any.");
            pw.println("#avoidPhases sS sScS");
            pw.println("##(double) Time length to include before phase arrival of usePhases [s]. (20)");
            pw.println("#frontShift ");
            pw.println("##(double) Time length to include after phase arrival of usePhases [s]. (60)");
            pw.println("#rearShift ");
            pw.println("##(double) Time length to be excluded before phase arrival of avoidPhases [s]. (5)");
            pw.println("#avoidFrontShift ");
            pw.println("##(double) Time length to be excluded after phase arrival of avoidPhases [s]. (60)");
            pw.println("#avoidRearShift ");
            pw.println("##(double) Minimum length of timewindows [sec]. (0)");
            pw.println("#minLength ");
            pw.println("##(boolean) Whether to allow split timewindows. (false)");
            pw.println("##  If not, timewindows are discarded when avoidPhases arrive between or near usePhases");
            pw.println("##    and blanks are filled when timewindows of usePhases aren't overlapped.");
            pw.println("#allowSplitWindows ");
            pw.println("##(String) Name of structure to compute travel times using TauP. (prem)");
            pw.println("#structureName ");
            pw.println("##(boolean) Whether or not to use major arc phases. (false)");
            pw.println("#majorArc ");
            pw.println("##(boolean) Whether to use duplicate arrivals of usePhases when deciding timewindows (e.g. in case of triplication). (true)");
            pw.println("##  If not, only the first arrival of each usePhase will be considered.");
            pw.println("#useDuplicatePhases ");
        }
        System.err.println(outPath + " is created.");
    }

    public TimewindowMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        } else {
            obsPath = property.parsePath("obsPath", ".", true, workPath);
        }

        usePhases = phaseSet(property.parseString("usePhases", "S"));
        avoidPhases = property.containsKey("avoidPhases") ? phaseSet(property.parseString("avoidPhases", null)) : phaseSet(null);
        frontShift = property.parseDouble("frontShift", "20");
        rearShift = property.parseDouble("rearShift", "60");
        avoidFrontShift = property.parseDouble("avoidFrontShift", "5");
        avoidRearShift = property.parseDouble("avoidRearShift", "60");
        minLength = property.parseDouble("minLength", "0");
        allowSplitWindows = property.parseBoolean("allowSplitWindows","false");
        structureName = property.parseString("structureName", "prem").toLowerCase();
        majorArc = property.parseBoolean("majorArc", "false");
        useDuplicatePhases = property.parseBoolean("useDuplicatePhases","true");

        String dateString = GadgetAid.getTemporaryString();
        outTimewindowPath = DatasetAid.generateOutputFilePath(workPath, "timewindow", fileTag, appendFileDate, dateString, ".dat");
        outInvalidPath = DatasetAid.generateOutputFilePath(workPath, "invalidTimewindow", fileTag, appendFileDate, dateString, ".txt");
        outTravelTimePath = DatasetAid.generateOutputFilePath(workPath, "travelTime", fileTag, appendFileDate, dateString, ".inf");
    }

    private static Set<Phase> phaseSet(String arg) {
        return (arg == null || arg.isEmpty()) ? Collections.emptySet()
                : Arrays.stream(arg.split("\\s+")).map(Phase::create).collect(Collectors.toSet());
    }

    @Override
    public void run() throws IOException {
        System.err.println("Invalid files, if any, will be listed in " + outInvalidPath);

        // read input file
        if (dataEntryPath != null) {
            entrySet = DataEntryListFile.readAsSet(dataEntryPath).stream()
                    .filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());
        } else {
            entrySet = new HashSet<>();
            for (EventFolder eventDir : DatasetAid.eventFolderSet(obsPath)) {
                Set<SACFileName> sacFileNames = eventDir.sacFileSet().stream()
                        .filter(sfn -> sfn.isOBS() && components.contains(sfn.getComponent())).collect(Collectors.toSet());
                for (SACFileName sacFileName : sacFileNames) {
                    SACFileAccess sacFile = sacFileName.read();
                    entrySet.add(new DataEntry(sacFile.getGlobalCMTID(), sacFile.getObserver(), sacFile.getComponent()));
                }
            }
        }
        Set<GlobalCMTID> eventSet = entrySet.stream().map(DataEntry::getEvent).collect(Collectors.toSet());

        // work for each event
        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventSet.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(100);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        // output
        if (timewindowSet.isEmpty()) {
            System.err.println("No timewindows are created.");
        } else {
            TimewindowDataFile.write(timewindowSet, outTimewindowPath);
        }
        TravelTimeInformationFile.write(usePhases, avoidPhases, travelTimeSet, outTravelTimePath);
    }

    private Runnable process(GlobalCMTID event) {
        return () -> {
            try {
                // set up taup_time tool
                // This is done per event (not reusing a single tool) because each thread needs its own instance.
                // This is done per event (not at each observer) because computation takes time when changing source depth (see TauP manual).
                String[] phaseNames = Stream.concat(usePhases.stream(), avoidPhases.stream()).map(Phase::toString).toArray(String[]::new);
                TauP_Time timeTool = new TauP_Time(structureName);
                timeTool.setPhaseNames(phaseNames);
                timeTool.setSourceDepth(event.getEventData().getCmtPosition().getDepth());

                Set<DataEntry> correspondingEntrySet = entrySet.stream()
                        .filter(entry -> entry.getEvent().equals(event) && components.contains(entry.getComponent()))
                        .collect(Collectors.toSet());
                for (DataEntry entry: correspondingEntrySet) {
                    makeTimeWindows(entry, timeTool);
                }
                System.err.print(".");
            } catch (Exception e) {
                System.err.println("!!! Error on " + event);
                e.printStackTrace();
            }
        };
    }

    /**
     * Make timewindow for the input data entry.
     * @param entry ({@link DataEntry}) Data entry to work for.
     * @param timeTool (TauP_Time) Time tool, with structure, phases, and event already set.
     * @throws IOException
     * @throws TauModelException
     */
    private void makeTimeWindows(DataEntry entry, TauP_Time timeTool) throws IOException, TauModelException {
        GlobalCMTID event = entry.getEvent();
        Observer observer = entry.getObserver();
        SACComponent component = entry.getComponent();

        // Compute phase arrivals
        double epicentralDistanceDeg = event.getEventData().getCmtPosition().computeEpicentralDistanceDeg(observer.getPosition());
        timeTool.calculate(epicentralDistanceDeg);
        List<Arrival> arrivals = timeTool.getArrivals();
        List<Arrival> useArrivals = new ArrayList<>();
        List<Arrival> avoidArrivals = new ArrayList<>();
        if (useDuplicatePhases) {
            // use all arrivals for usePhases
            arrivals.stream().filter(arrival -> usePhases.contains(Phase.create(arrival.getPhase().getName()))).forEach(useArrivals::add);
        } else {
            // use only the first arrival of each usePhase
            for (Phase phase : usePhases) {
                arrivals.stream().filter(arrival -> Phase.create(arrival.getPhase().getName()).equals(phase)).findFirst().ifPresent(useArrivals::add);
            }
        }
        // for avoidPhases, use all arrivals
        arrivals.stream().filter(arrival -> avoidPhases.contains(Phase.create(arrival.getPhase().getName()))).forEach(avoidArrivals::add);

        // refine useArrivals
        if (!majorArc) {
            useArrivals.removeIf(arrival -> arrival.getDistDeg() >= 180.);
        }
        if (useArrivals.isEmpty()) {
            writeInvalid(entry, "No usePhases arrive");
            return;
        }

        // extract arrival times
        double[] usePhaseTimes = useArrivals.stream().mapToDouble(Arrival::getTime).toArray();
        double[] avoidPhaseTimes = avoidArrivals.stream().mapToDouble(Arrival::getTime).toArray();

        // create windows
        Timewindow[] windows;
        if (allowSplitWindows) {
            // create windows allowing them to be split
            windows = createWindowsAllowingSplits(usePhaseTimes, avoidPhaseTimes);
        } else {
            // find first and last usePhase arrival times
            double firstUseTime = Arrays.stream(usePhaseTimes).min().getAsDouble();
            double lastUseTime = Arrays.stream(usePhaseTimes).max().getAsDouble();
            // skip if an avoidPhase is between or near usePhases
            for (Arrival avoidArrival : avoidArrivals) {
                double avoidTime = avoidArrival.getTime();
                if (firstUseTime <= (avoidTime + avoidRearShift) && (avoidTime - avoidFrontShift) <= lastUseTime) {
                    writeInvalid(entry, avoidArrival.getPhase().getName() + " arrives between or near usePhases");
                    return;
                }
            }
            // create single window
            windows = createSingleWindow(firstUseTime, lastUseTime, avoidPhaseTimes);
        }
        if (windows == null) {
            writeInvalid(entry, "Nothing remains after eliminating windows of avoidPhases");
            return;
        }

        // window fix and check
        List<TimewindowData> windowList = Arrays.stream(windows)
                .map(window -> new TimewindowData(window.getStartTime(), window.getEndTime(), observer, event, component,
                        findContainedPhases(window, useArrivals)))
                .filter(tw -> tw.getLength() > minLength).collect(Collectors.toList());
        if (windowList.size() == 0) {
            writeInvalid(entry, "Timewindow too short.");
            return;
        }

        // add final result
        timewindowSet.addAll(windowList);
        travelTimeSet.add(new TravelTimeInformation(event, observer, useArrivals, avoidArrivals));

    }

    /**
     * Creates timewindows allowing them to be split.
     * If timewindows of avoidPhases overlap the timewindows of usePhases, the timewindow will be cut.
     * @param usePhaseTimes (double[]) Travel times of phases to be used, must be in order.
     * @param avoidPhaseTimes (double[]) Travel times of phases not to be included, must be in order.
     * @return ({@link Timewindow}[]) created timewindows. If nothing remains after eliminating avoidWindows, null.
     * @author anselme
     */
    private Timewindow[] createWindowsAllowingSplits(double[] usePhaseTimes, double[] avoidPhaseTimes) {
        // create windows of usePhases
        Timewindow[] windows = Arrays.stream(usePhaseTimes)
                .mapToObj(time -> new Timewindow(time - frontShift, time + rearShift))
                .sorted().toArray(Timewindow[]::new);
        windows = mergeWindows(windows);
        // when avoidPhases do not exist
        if (avoidPhaseTimes.length == 0) return windows;
        // create windows of avoidPhases
        Timewindow[] avoidWindows = Arrays.stream(avoidPhaseTimes)
                .mapToObj(time -> new Timewindow(time - avoidFrontShift, time + avoidRearShift))
                .sorted().toArray(Timewindow[]::new);
        avoidWindows = mergeWindows(avoidWindows);
        // cut avoidWindows out of useWindows
        return considerAvoidPhases(windows, avoidWindows);
    }

    /**
     * Creates a timewindow given the first and last arrival times.
     * The blank between them is filled, even if they are far apart.
     * @param initialPhaseTime (double) Travel time of first usePhase
     * @param finalPhaseTime (double) Travel time of last usePhase
     * @param avoidPhaseTimes (double[]) Travel times of avoidPhases, must be in order.
     * @return ({@link Timewindow}[]) Array containing the single created timewindow.
     *          If nothing remains after eliminating avoidWindows, null.
     * @author rei
     */
    private Timewindow[] createSingleWindow(double initialPhaseTime, double finalPhaseTime, double[] avoidPhaseTimes) {
        // create window containing all usePhases
        Timewindow[] window = new Timewindow[1];
        window[0] = new Timewindow(initialPhaseTime - frontShift, finalPhaseTime + rearShift);
        // when avoidPhases do not exist
        if (avoidPhaseTimes.length == 0) return window;
        // create windows of avoidPhases
        Timewindow[] avoidWindows = Arrays.stream(avoidPhaseTimes)
                .mapToObj(time -> new Timewindow(time - avoidFrontShift, time + avoidRearShift))
                .sorted().toArray(Timewindow[]::new);
        avoidWindows = mergeWindows(avoidWindows);
        // cut avoidWindows out of the window
        // Note that the result still has only one timewindow.
        return considerAvoidPhases(window, avoidWindows);
    }

    /**
     * If there are any overlapping timewindows, merge them.
     * @param windows ({@link Timewindow}[]) Timewindows to be merged, must be in order by start time
     * @return ({@link Timewindow}[]) Timewindows containing all the input windows in order
     */
    private static Timewindow[] mergeWindows(Timewindow[] windows) {
        if (windows.length == 1)
            return windows;
        List<Timewindow> windowList = new ArrayList<>();
        Timewindow windowA = windows[0];
        for (int i = 1; i < windows.length; i++) {
            Timewindow windowB = windows[i];
            if (windowA.overlap(windowB)) {
                windowA = windowA.merge(windowB);
            } else {
                windowList.add(windowA);
                windowA = windowB;
            }
            if (i == windows.length - 1)
                windowList.add(windowA);
        }
        return windowList.toArray(new Timewindow[windowList.size()]);
    }

    /**
     * Eliminate timewindows of avoidPhases from timewindows of usePhases.
     * If a timewindow of avoidPhases fits inside a timewindow of usePhases, only the first usable part is selected.
     * @param useWindows ({@link Timewindow}[]) Timewindows to use, must be in order by start time
     * @param avoidWindows ({@link Timewindow}[]) Timewindows to avoid, must be in order by start time
     * @return ({@link Timewindow}[]) Timewindows to use. If nothing remains, null.
     */
    private static Timewindow[] considerAvoidPhases(Timewindow[] useWindows, Timewindow[] avoidWindows) {
        List<Timewindow> resultWindows = new ArrayList<>();
        for (Timewindow window : useWindows) {
            for (Timewindow avoidWindow : avoidWindows) {
                window = cutWindow(window, avoidWindow);
                if (window == null) break;
            }
            if (window != null) resultWindows.add(window);
        }

        return resultWindows.size() == 0 ? null : resultWindows.toArray(new Timewindow[0]);
    }

    /**
     * Eliminate avoidTimewindow from useTimewindow.
     * If avoidTimewindow fits inside useTimewindow, the first usable part is selected.
     * @param useWindow ({@link Timewindow}) Timewindow to use
     * @param avoidWindow ({@link Timewindow}) Timewindow to avoid
     * @return ({@link Timewindow}) Timewindow to use. If nothing remains, null.
     */
    private static Timewindow cutWindow(Timewindow useWindow, Timewindow avoidWindow) {
        if (!useWindow.overlap(avoidWindow)) return useWindow;
        if (avoidWindow.startTime <= useWindow.startTime) {
            return useWindow.endTime <= avoidWindow.endTime ? null :
                    new Timewindow(avoidWindow.endTime, useWindow.endTime);
        } else {
            return new Timewindow(useWindow.startTime, avoidWindow.startTime);
        }
    }

    /**
     * @param window
     * @param usePhases
     * @return
     * @author anselme
     */
    private Phase[] findContainedPhases(Timewindow window, List<Arrival> useArrivals) {
        Set<Phase> phases = new HashSet<>();
        for (Arrival arrival : useArrivals) {
            double time = arrival.getTime();
            if (time <= window.endTime && time >= window.startTime)
                phases.add(Phase.create(arrival.getPhase().getName()));
        }
        return phases.toArray(new Phase[phases.size()]);
    }

    /**
     * Report invalid entry in invalidList
     * @param entry ({@link DataEntry}) Data entry to report.
     * @param reason (String) Why the entry is invalid.
     * @throws IOException
     */
    private synchronized void writeInvalid(DataEntry entry, String reason) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(outInvalidPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            pw.println(entry + " : " + reason);
        }
    }

}
