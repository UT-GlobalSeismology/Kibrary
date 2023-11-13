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
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * An operation to select or resample BasicIDs.
 * This allows for selection of certain data to be used in the inversion,
 * as well as for preparation of bootstrap or subsampling tests.
 * <p>
 * To select BasicIDs of certain raypaths, supply with a {@link DataEntryListFile} including a list of raypaths to be selected.
 * Timewindows may be also selected by the phases that they must include.
 * Each waveform can be re-cut depending on the input wimewindow file.
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
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * path of basic waveform folder
     */
    private Path basicPath;
    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a data entry file for selection
     */
    private Path dataEntryPath;

    /**
     * Phases that must be included in timewindows to be selected
     */
    private String[] requiredPhases;
    /**
     * Whether to choose BasicIDs with duplication
     */
    private boolean bootstrap;
    /**
     * How many of the BasicIDs to sample [%] (100% is the total number after selection)
     */
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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic waveform folder, must be set");
            pw.println("#basicPath actual");
            pw.println("##Path of a timewindow file, if you want to re-cut waveforms");
            pw.println("#timewindowPath ");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Phases to be included in timewindows to use, listed using spaces. To use all phases, leave this unset.");
            pw.println("#requiredPhases ");
            pw.println("##(boolean) Perform a bootstrap test (false)");
            pw.println("#bootstrap ");
            pw.println("##(double) Percent of basic IDs to use in subsampling test (100)");
            pw.println("## Here, 100% is the number of basic IDs after selection.");
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
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        basicPath = property.parsePath("basicPath", null, true, workPath);
        if (property.containsKey("timewindowPath")) {
            timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        }
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }

        if (property.containsKey("requiredPhases"))
            requiredPhases = property.parseStringArray("requiredPhases", null);

        bootstrap = property.parseBoolean("bootstrap", "false");
        subsamplingPercent = property.parseDouble("subsamplingPercent", "100");
        if (subsamplingPercent < 0)
            throw new IllegalArgumentException("subsamplingPercent must be positive.");

    }

    @Override
    public void run() throws IOException {
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true).stream()
                .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());

        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
        obsIDs = pairer.getObsList();
        synIDs = pairer.getSynList();

        // select basicIDs to used based on criteria
        if (dataEntryPath != null || requiredPhases != null) {
            selectByCriteria();
        }
        if (obsIDs.size() == 0) return;

        // cut waveforms based on timewindow file
        if (timewindowPath != null) {
            cutWindow();
        }

        // select required number of basicIDs
        if (bootstrap) {
            resample(subsamplingPercent, true);
        } else if (!Precision.equals(subsamplingPercent, 100)) {
            resample(subsamplingPercent, false);
        }
        if (obsIDs.size() == 0) return;

        // collect all selected basicIDs
        List<BasicID> finalList = new ArrayList<>();
        finalList.addAll(obsIDs);
        finalList.addAll(synIDs);

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "rebuilt", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        BasicIDFile.write(finalList, outPath);

    }

    private void selectByCriteria() throws IOException {
        List<BasicID> selectedObsIDs = new ArrayList<>();
        List<BasicID> selectedSynIDs = new ArrayList<>();

        // read entry set to be used for selection
        Set<DataEntry> entrySet = null;
        if (dataEntryPath != null) {
            entrySet = DataEntryListFile.readAsSet(dataEntryPath);
        }

        for (int i = 0; i < obsIDs.size(); i++) {
            BasicID obsID = obsIDs.get(i);
            BasicID synID = synIDs.get(i);

            // check raypath
            if (entrySet != null) {
                DataEntry entry = new DataEntry(obsID.getGlobalCMTID(), obsID.getObserver(), obsID.getSacComponent());
                if (!entrySet.contains(entry)) {
                    continue;
                }
            }

            // check phases
            if (requiredPhases != null) {
                Set<Phase> requiredPhaseSet = Arrays.stream(requiredPhases).map(Phase::create).collect(Collectors.toSet());
                List<Phase> phases = Arrays.asList(obsID.getPhases());
                if (requiredPhaseSet.stream().allMatch(reqPhase -> phases.contains(reqPhase)) == false) {
                    continue;
                }
            }

            // add basicID that passed criteria
            selectedObsIDs.add(obsID);
            selectedSynIDs.add(synID);
        }
        System.err.println("Selected " + selectedObsIDs.size() + " pairs of basic IDs based on criteria.");

        // replace list by selected ones
        obsIDs = selectedObsIDs;
        synIDs = selectedSynIDs;
    }

    private void cutWindow() throws IOException {
        List<BasicID> cutObsIDs = new ArrayList<>();
        List<BasicID> cutSynIDs = new ArrayList<>();

        //read timewindow file and select based on component and entries
        Set<TimewindowData> timewindowSet = TimewindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

        for (TimewindowData timewindow : timewindowSet) {
            // select corresponding basicIDs with timewindow
            List<BasicID> correspondingObsIDs = obsIDs.stream().filter(id ->
                timewindow.getGlobalCMTID().equals(id.getGlobalCMTID()) && timewindow.getObserver().equals(id.getObserver()) &&
                timewindow.getComponent().equals(id.getSacComponent())).collect(Collectors.toList());
            List<BasicID> correspondingSynIDs = synIDs.stream().filter(id ->
                timewindow.getGlobalCMTID().equals(id.getGlobalCMTID()) && timewindow.getObserver().equals(id.getObserver()) &&
                timewindow.getComponent().equals(id.getSacComponent())).collect(Collectors.toList());

            for (int i = 0; i < correspondingObsIDs.size(); i++) {
                BasicID obsID = correspondingObsIDs.get(i);
                BasicID synID = correspondingSynIDs.get(i);

                Trace obsTrace = obsID.toTrace().cutWindow(timewindow);
                Trace synTrace = synID.toTrace().cutWindow(timewindow);

                BasicID cutObsID = new BasicID(WaveformType.OBS, obsID.getSamplingHz(), obsTrace.getMinX(), obsTrace.getLength(),
                        obsID.getObserver(), obsID.getGlobalCMTID(), obsID.getSacComponent(), obsID.getMinPeriod(),
                        obsID.getMaxPeriod(), obsID.getPhases(), obsID.isConvolved(), obsTrace.getY());
                BasicID cutSynID = new BasicID(WaveformType.SYN, synID.getSamplingHz(), synTrace.getMinX(), synTrace.getLength(),
                        synID.getObserver(), synID.getGlobalCMTID(), synID.getSacComponent(), synID.getMinPeriod(),
                        synID.getMaxPeriod(), synID.getPhases(), synID.isConvolved(), synTrace.getY());
                cutObsIDs.add(cutObsID);
                cutSynIDs.add(cutSynID);
            }
        }
        obsIDs = cutObsIDs;
        synIDs = cutSynIDs;
    }

    private void resample(double percent, boolean duplication) {
        int numToSample = (int) (obsIDs.size() * percent / 100);
        List<BasicID> selectedObsIDs = new ArrayList<>();
        List<BasicID> selectedSynIDs = new ArrayList<>();

        if (duplication) {
            System.err.println("Selecting " + numToSample + " pairs of basic IDs with duplication.");
            Random random = new Random();
            int[] shuffledIndices = random.ints(numToSample, 0, obsIDs.size()).toArray();
            for (int i = 0; i < numToSample; i++) {
                System.err.println(shuffledIndices[i]);
                selectedObsIDs.add(obsIDs.get(shuffledIndices[i]));
                selectedSynIDs.add(synIDs.get(shuffledIndices[i]));
            }
        } else {
            System.err.println("Selecting " + numToSample + " pairs of basic IDs without duplication.");
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
