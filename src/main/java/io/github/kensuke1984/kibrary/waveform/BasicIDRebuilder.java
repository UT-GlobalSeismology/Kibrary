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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * An operation to select or resample BasicIDs.
 * This allows for extraction of certain data to be used in the inversion,
 * as well as for preparation of bootstrap or subsampling tests.
 *
 * @author otsuru
 * @since 2022/7/13
 */
public class BasicIDRebuilder extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The first part of the name of output basic ID and waveform files
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * path of basic ID file
     */
    private Path basicIDPath;
    /**
     * path of waveform data
     */
    private Path basicPath;

    private String[] requiredPhases;
    private double minDistance;
    private double maxDistance;
    private double minMw;
    private double maxMw;

    private boolean bootstrap;
    private double subsamplingPercent;

    private List<BasicID> obsIDs;
    private List<BasicID> synIDs;


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
            pw.println("##(String) The first part of the name of output folder (actual)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Phases to be included in timewindows to use, listed using spaces. To use all phases, leave this unset.");
            pw.println("#requiredPhases ");
            pw.println("##(double) Minimum epicentral distance [deg] (0)");
            pw.println("#minDistance ");
            pw.println("##(double) Maximum epicentral distance [deg] (360)");
            pw.println("#maxDistance ");
            pw.println("##(double) Minimum Mw (0)");
            pw.println("#minMw ");
            pw.println("##(double) Maximum Mw (10)");
            pw.println("#maxMw ");
            pw.println("##(boolean) Perform a bootstrap test (false)");
            pw.println("#bootstrap ");
            pw.println("##(double) Percent of basic IDs to use in subsampling test (100)");
            pw.println("#subsamplingPercent ");
        }
        System.err.println(outPath + " is created.");
    }

    public BasicIDRebuilder(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "actual");
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);

        if (property.containsKey("requiredPhases"))
            requiredPhases = property.parseStringArray("requiredPhases", null);
        minDistance = property.parseDouble("minDistance", "0.");
        maxDistance = property.parseDouble("maxDistance", "360.");
        minMw = property.parseDouble("minMw", "0.");
        maxMw = property.parseDouble("maxMw", "10.");

        bootstrap = property.parseBoolean("bootstrap", "false");
        subsamplingPercent = property.parseDouble("subsamplingPercent", "100");

    }

    @Override
    public void run() throws IOException {

        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
        obsIDs = pairer.getObsList();
        synIDs = pairer.getSynList();

        selectByCriteria();

        if (bootstrap) {
            resample(subsamplingPercent, true);
        } else if (!Precision.equals(subsamplingPercent, 100)) {
            resample(subsamplingPercent, false);
        }

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "rebuilt", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        Path outputIDPath = outPath.resolve(nameRoot + "ID.dat");
        Path outputWavePath = outPath.resolve(nameRoot + ".dat");
        output(outputIDPath, outputWavePath);

    }

    private void output(Path outputIDPath, Path outputWavePath) throws IOException {

        // extract set of observers, events, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        obsIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
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

        System.err.println("Outputting in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath, observerSet, eventSet, periodRanges, phases)) {
            obsIDs.forEach(id -> {
                try {
                    wdw.addBasicID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            synIDs.forEach(id -> {
                try {
                    wdw.addBasicID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void selectByCriteria() {
        List<BasicID> selectedObsIDs = new ArrayList<>();
        List<BasicID> selectedSynIDs = new ArrayList<>();

        for (int i = 0; i < obsIDs.size(); i++) {
            BasicID obsID = obsIDs.get(i);
            BasicID synID = synIDs.get(i);

            // check phases
            if (requiredPhases != null) {
                Set<Phase> requiredPhaseSet = Arrays.stream(requiredPhases).map(Phase::create).collect(Collectors.toSet());
                List<Phase> phases = Arrays.asList(obsID.getPhases());
                if (requiredPhaseSet.stream().allMatch(reqPhase -> phases.contains(reqPhase)) == false) {
                    continue;
                }
            }

            // check distance
            double distance = obsID.getGlobalCMTID().getEventData().getCmtLocation()
                    .calculateEpicentralDistance(obsID.getObserver().getPosition());
            if (distance < minDistance || distance > maxDistance) {
                continue;
            }

            // check magnitude
            double mw = obsID.getGlobalCMTID().getEventData().getCmt().getMw();
            if (mw < minMw || mw > maxMw) {
                continue;
            }

            // add basicID that passed criteria
            selectedObsIDs.add(obsID);
            selectedSynIDs.add(synID);
        }

        // replace list by selected ones
        obsIDs = selectedObsIDs;
        synIDs = selectedSynIDs;
    }

    private void resample(double percent, boolean duplication) {
        int numToSample = (int) (obsIDs.size() * percent / 100);
        List<BasicID> selectedObsIDs = new ArrayList<>();
        List<BasicID> selectedSynIDs = new ArrayList<>();

        if (duplication) {
            Random random = new Random();
            int[] shuffledIndices = random.ints(numToSample, 0, obsIDs.size()).toArray();
            for (int i = 0; i < numToSample; i++) {
                selectedObsIDs.add(obsIDs.get(shuffledIndices[i]));
                selectedSynIDs.add(synIDs.get(shuffledIndices[i]));
            }
        } else {
            List<Integer> shuffledIndices = IntStream.range(0, obsIDs.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(shuffledIndices);
            for (int i = 0; i < numToSample; i++) {
                selectedObsIDs.add(obsIDs.get(shuffledIndices.get(i)));
                selectedSynIDs.add(synIDs.get(shuffledIndices.get(i)));
            }
        }

        // replace list by selected ones
        obsIDs = selectedObsIDs;
        synIDs = selectedSynIDs;
    }
}
