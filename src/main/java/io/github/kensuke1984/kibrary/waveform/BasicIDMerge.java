package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.EventInformationFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverInformationFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Operation for merging pairs of basic ID files and basic waveform files.
 *
 * @author otsuru
 * @since 2022/1/2 Created based on the original BasicIDMerge which was in kibrary.waveform.addons.
 */
public class BasicIDMerge implements Operation {

    private static final int MAX_PAIR = 10;

    private final Properties property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    private List<Path> basicIDPaths = new ArrayList<>();
    private List<Path> basicPaths = new ArrayList<>();

    private String nameRoot;

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##(String) The first part of the name of output basic ID and waveform files (actual)");
            pw.println("#nameRoot");
            pw.println("##From here on, list up pairs of the paths of a basic ID file and a basic waveform file.");
            pw.println("##Up to " + MAX_PAIR + " pairs can be managed. Any pair may be left blank.");
            for (int i = 1; i <= MAX_PAIR; i++) {
                switch(i) {
                case 1:
                    pw.println("##1st pair");
                    break;
                case 2:
                    pw.println("##2nd pair");
                    break;
                case 3:
                    pw.println("##3rd pair");
                    break;
                default:
                    pw.println("##" + i + "th pair");
                    break;
                }
                pw.println("#basicIDPath" + i + " actualID" + i + ".dat");
                pw.println("#basicPath" + i + " actual" + i + ".dat");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public BasicIDMerge(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("nameRoot")) property.setProperty("nameRoot", "actual");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        nameRoot = property.getProperty("nameRoot");

        for (int i = 1; i <= MAX_PAIR; i++) {
            String basicIDKey = "basicIDPath" + i;
            String basicKey = "basicPath" + i;
            if (!property.containsKey(basicIDKey) && !property.containsKey(basicKey)) {
                continue;
            } else if (!property.containsKey(basicIDKey) || !property.containsKey(basicKey)) {
                throw new IllegalArgumentException("Basic ID file and basic waveform file must be set in pairs.");
            }
            Path basicIDPath = getPath(basicIDKey);
            Path basicPath = getPath(basicKey);
            if (!Files.exists(basicIDPath))
                throw new NoSuchFileException("The basic ID file " + basicIDPath + " does not exist");
            if (!Files.exists(basicPath))
                throw new NoSuchFileException("The basic waveform file " + basicPath + " does not exist");
            basicIDPaths.add(basicIDPath);
            basicPaths.add(basicPath);
        }
    }

    /**
     * @param args [parameter file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        BasicIDMerge bim = new BasicIDMerge(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(BasicIDMerge.class.getName() + " is operating.");
        bim.run();
        System.err.println(BasicIDMerge.class.getName() + " finished in " +
                GadgetAid.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws IOException {
        int pairNum = basicIDPaths.size();
        if (basicPaths.size() != pairNum) {
            throw new IllegalStateException("The number of basic ID files and basic waveform files is different.");
        }
        if (pairNum == 0) {
            System.err.println("No input files found.");
            return;
        } else if (pairNum == 1) {
            System.err.println("Only 1 pair of input files found. Merging will not be done.");
            return;
        }

        // read BasicIDs from all input files
        Set<BasicID> basicIDs = new HashSet<>();
        for (int i = 0; i < pairNum; i++) {
            BasicID[] srcIDs = BasicIDFile.read(basicIDPaths.get(i), basicPaths.get(i));
            Stream.of(srcIDs).forEach(id -> basicIDs.add(id));
        }

        // extract set of observers, events, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        basicIDs.forEach(id -> {
            eventSet.add(id.getGlobalCMTID());
            observerSet.add(id.getObserver());
            boolean add = true;
            for (double[] periods : periodSet) {
                if (id.getMinPeriod() == periods[0] && id.getMaxPeriod() == periods[1])
                    add = false;
            }
            if (add)
                periodSet.add(new double[] {id.getMinPeriod(), id.getMaxPeriod()});
            for (Phase phase : id.getPhases())
                phaseSet.add(phase);
        });

        double[][] periodRanges = new double[periodSet.size()][];
        int j = 0;
        for (double[] periods : periodSet)
            periodRanges[j++] = periods;
        Phase[] phases = phaseSet.toArray(new Phase[phaseSet.size()]);

        // output merged files
        String dateStr = GadgetAid.getTemporaryString();
        Path observerFilePath = workPath.resolve("observer" + dateStr + ".inf");
        Path eventFilePath = workPath.resolve("event" + dateStr + ".inf");
        Path outputIDPath = workPath.resolve(nameRoot + "ID" + dateStr + ".dat");
        Path outputWavePath = workPath.resolve(nameRoot + dateStr + ".dat");

        System.err.println("Outputting in " + observerFilePath);
        ObserverInformationFile.write(observerSet, workPath.resolve(observerFilePath));

        System.err.println("Outputting in " + eventFilePath);
        EventInformationFile.write(eventSet, workPath.resolve(eventFilePath));

        System.err.println("Outputting in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath, observerSet, eventSet, periodRanges, phases)) {
            basicIDs.forEach(id -> {
                try {
                    wdw.addBasicID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
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
