package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTCatalog;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.ParameterType;

/**
 * @author otsuru
 * @since 2023/3/18 Extracted time partial part from PartialWaveformAssembler3D
 * TODO This class has been created just by extracting parts that seem to be related to time partials. No testing has been done!
 */
public class TimePartialsAssembler extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * output directory Path
     */
    private Path outPath;
    /**
     * components to be used
     */
    private Set<SACComponent> components;
    /**
     * spcFileをコンボリューションして時系列にする時のサンプリングHz デフォルトは２０ TODOまだ触れない
     */
    private double partialSamplingHz = 20;
    /**
     * 最後に時系列で切り出す時のサンプリングヘルツ(Hz)
     */
    private double finalSamplingHz;

    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a data entry file
     */
    private Path dataEntryPath;
    /**
     * set of partial type for computation
     */
    private Set<PartialType> partialTypes;
    private Path timePartialPath;
    /**
     * bandpassの最小周波数（Hz）
     */
    private double minFreq;
    /**
     * bandpassの最大周波数（Hz）
     */
    private double maxFreq;

    /**
     * sacdataを何ポイントおきに取り出すか
     */
    private int step;
    private Path logPath;
    private Set<GlobalCMTID> eventSet;
    private Set<Observer> observerSet;
    private Set<TimewindowData> timewindowSet;
    private List<PartialID> partialIDs = Collections.synchronizedList(new ArrayList<>());

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
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Sac sampling Hz (20)");
            pw.println("#partialSamplingHz cant change now");
            pw.println("##(double) Value of sampling Hz in output files, must be a factor of sacSamplingHz (1)");
            pw.println("#finalSamplingHz ");
            pw.println("##Path of a time window file, must be set");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Path of the time partials directory, must be set");
            pw.println("#timePartialPath ");
            pw.println("##PartialTypes to compute for, listed using spaces, from {TIME_SOURCE, TIME_RECEIVER} (TIME_SOURCE)");
            pw.println("#partialTypes ");
            pw.println("##(double) Minimum value of passband (0.005)");
            pw.println("#minFreq ");
            pw.println("##(double) Maximum value of passband (0.08)");
            pw.println("#maxFreq ");
        }
        System.err.println(outPath + " is created.");
    }

    public TimePartialsAssembler(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialSamplingHz = 20;  // TODO property.parseDouble("sacSamplingHz", "20");
        finalSamplingHz = property.parseDouble("finalSamplingHz", "1");
        if (partialSamplingHz % finalSamplingHz != 0)
            throw new IllegalArgumentException("Must choose a finalSamplingHz that divides " + partialSamplingHz);

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        timePartialPath = property.parsePath("timePartialPath", null, true, workPath);
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "TIME_SOURCE")).map(PartialType::valueOf)
                .collect(Collectors.toSet());

        maxFreq = property.parseDouble("maxFreq", "0.08");
        minFreq = property.parseDouble("minFreq", "0.005");
    }

    @Override
    public void run() throws IOException {

        // create output folder
        outPath = DatasetAid.createOutputFolder(workPath, "assembled", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
        logPath = outPath.resolve("assembler" + GadgetAid.getTemporaryString() + ".log");
        Files.createFile(logPath);

        final int N_THREADS = Runtime.getRuntime().availableProcessors();
//      final int N_THREADS = 1;
        writeLog("Running " + N_THREADS + " threads");
        writeLog("CMTcatalogue: " + GlobalCMTCatalog.getCatalogPath().toString());
        collectTimewindowInformation();

        // sacdataを何ポイントおきに取り出すか
        step = (int) (partialSamplingHz / finalSamplingHz);

        // time partials for each event
        if (timePartialPath != null) {
            computeTimePartial(N_THREADS);
        }

        PartialIDFile.write(partialIDs, outPath.resolve("partial"));
    }

    /**
     * Reads timewindow information include observer and GCMTid
     *
     * @throws IOException if any
     */
    private void collectTimewindowInformation() throws IOException {
        // タイムウインドウの情報を読み取る。
        System.err.println("Reading timewindow information");
        if (dataEntryPath != null) {
            // read entry set to be used for selection
            Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

            // read timewindows and select based on component and entries
            timewindowSet = TimewindowDataFile.read(timewindowPath)
                    .stream().filter(window -> components.contains(window.getComponent()) &&
                            entrySet.contains(new DataEntry(window.getGlobalCMTID(), window.getObserver(), window.getComponent())))
                    .collect(Collectors.toSet());
        } else {
            // read timewindows and select based on component
            timewindowSet = TimewindowDataFile.read(timewindowPath)
                    .stream().filter(window -> components.contains(window.getComponent()))
                    .collect(Collectors.toSet());
        }
        eventSet = new HashSet<>();
        observerSet = new HashSet<>();
        timewindowSet.forEach(t -> {
            eventSet.add(t.getGlobalCMTID());
            observerSet.add(t.getObserver());
        });

        writeLog(timewindowSet.size() + " timewindows are found in " + timewindowPath + ". " + eventSet.size()
                + " events and " + observerSet.size() + " stations.");
    }

    private void computeTimePartial(int N_THREADS) throws IOException {
        ExecutorService execs = Executors.newFixedThreadPool(N_THREADS);
        Set<EventFolder> timePartialEventDirs = DatasetAid.eventFolderSet(timePartialPath);
        for (EventFolder eventDir : timePartialEventDirs) {
            execs.execute(new WorkerTimePartial(eventDir));
            System.err.println("Working for time partials for " + eventDir);
        }
        execs.shutdown();
        while (!execs.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.err.println();
    }


    private class WorkerTimePartial implements Runnable {

        private EventFolder eventDir;
        private GlobalCMTID id;

        @Override
        public void run() {
            try {
                writeLog("Running on " + id);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            Path timePartialFolder = eventDir.toPath();

            if (!Files.exists(timePartialFolder)) {
                throw new RuntimeException(timePartialFolder + " does not exist...");
            }

            Set<SACFileName> sacnameSet;
            try {
                sacnameSet = eventDir.sacFileSet()
                        .stream()
                        .filter(sacname -> sacname.isTemporalPartial())
                        .collect(Collectors.toSet());
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }

//          System.out.println(sacnameSet.size());
//          sacnameSet.forEach(name -> System.out.println(name));

            Set<TimewindowData> timewindowCurrentEvent = timewindowSet
                    .stream()
                    .filter(tw -> tw.getGlobalCMTID().equals(id))
                    .collect(Collectors.toSet());

            // すべてのsacファイルに対しての処理
            for (SACFileName sacname : sacnameSet) {
                try {
                    addTemporalPartial(sacname, timewindowCurrentEvent);
                } catch (ClassCastException e) {
                    // 出来上がったインスタンスがOneDPartialSpectrumじゃない可能性
                    System.err.println(sacname + "is not 1D partial.");
                    continue;
                } catch (Exception e) {
                    System.err.println(sacname + " is invalid.");
                    e.printStackTrace();
                    try {
                        writeLog(sacname + " is invalid.");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
            }
            System.err.print(".");
        }

        private void addTemporalPartial(SACFileName sacname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
            Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
                    .filter(info -> info.getObserver().toString().equals(sacname.getObserverID())) //TODO this may not get unique observer
                    .collect(Collectors.toSet());
            if (tmpTws.size() == 0) {
                return;
            }

            System.err.println(sacname + " (time partials)");

            SACFileAccess sacdata = sacname.read();
            Observer station = sacdata.getObserver();

            for (SACComponent component : components) {
                Set<TimewindowData> tw = tmpTws.stream()
                        .filter(info -> info.getObserver().equals(station))
                        .filter(info -> info.getGlobalCMTID().equals(id))
                        .filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

                if (tw.isEmpty()) {
                    tmpTws.forEach(window -> {
                        System.err.println(window);
                        System.err.println(window.getObserver().getPosition());
                    });
                    System.err.println(station.getPosition());
                    System.err.println("Ignoring empty timewindow " + sacname + " " + station);
                    continue;
                }

                for (TimewindowData t : tw) {
                    double[] filteredUt = sacdata.createTrace().getY();
                    cutAndWrite(station, filteredUt, t);
                }
            }
        }
        /**
         * @param u
         *            partial waveform
         * @param timewindowInformation
         *            cut information
         * @return u cut by considering sampling Hz
         */
        private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * finalSamplingHz);
            double[] sampleU = new double[outnpts];
            // cutting a waveform for outputting
            Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

            return sampleU;
        }

        private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t) {

            double[] cutU = sampleOutput(filteredUt, t);
            FullPosition stationLocation = new FullPosition(station.getPosition().getLatitude(), station.getPosition().getLongitude(), Earth.EARTH_RADIUS);

//            if (userSourceTimeFunctionPath != null)
//                System.err.println("Warning: check that the source time function used for the time partial is the same as the one used here.");

            PartialID PIDReceiverSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), true, ParameterType.RECEIVER, VariableType.TIME, stationLocation,
                    cutU);
            PartialID PIDSourceSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    1 / maxFreq, 1 / minFreq, t.getPhases(), true, ParameterType.RECEIVER, VariableType.TIME, id.getEventData().getCmtPosition(),
                    cutU);

            if (partialTypes.contains(PartialType.TIME_RECEIVER))
                partialIDs.add(PIDReceiverSide);
            if (partialTypes.contains(PartialType.TIME_SOURCE))
                partialIDs.add(PIDSourceSide);
        }

        private WorkerTimePartial(EventFolder eventDir) {
            this.eventDir = eventDir;
            id = eventDir.getGlobalCMTID();
        };
    }

    private synchronized void writeLog(String line) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            pw.print(new Date() + " : ");
            pw.println(line);
        }
    }

}
