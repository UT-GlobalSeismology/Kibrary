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
import io.github.kensuke1984.kibrary.timewindow.TimeWindowData;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * An operation to select or resample BasicIDs.
 * This allows for selection of certain data to be used in the inversion,
 * as well as for preparation of bootstrap or subsampling tests.
 * <p>
 * To select BasicIDs of certain raypaths, supply with a {@link DataEntryListFile} including a list of raypaths to be selected.
 * Time windows may be also selected by the phases that they must include.
 * Each waveform can be re-cut depending on the input wimewindow file.
 *
 * @author otsuru
 * @since 2022/7/13
 */
public class BasicIDRebuilder extends Operation {

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
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Path of basic waveform folder.
     */
    private Path basicPath;
    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a data entry file for selection.
     */
    private Path dataEntryPath;

    /**
     * Phases that must be included in time windows to be selected.
     */
    private String[] requiredPhases;
    /**
     * How much of the data to sample [%]. (100% is the total number after the other selections.)
     */
    private double subsamplingPercent;
    /**
     * Whether to choose by events instead of basic IDs.
     */
    private boolean selectByEvents;
    /**
     * Whether to choose with duplication.
     */
    private boolean duplication;

    private List<BasicID> obsIDs;
    private List<BasicID> synIDs;

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
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic waveform folder, must be set.");
            pw.println("#basicPath actual");
            pw.println("##Path of a timewindow file, if you want to re-cut waveforms");
            pw.println("#timewindowPath ");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Phases to be included in time windows to use, listed using spaces. To use all phases, leave this unset.");
            pw.println("#requiredPhases ");
            pw.println("##########Settings for subsampling test.");
            pw.println("##(double) Percent to use in subsampling test. (100)");
            pw.println("##  Here, 100% is the number after the above selections.");
            pw.println("#subsamplingPercent ");
            pw.println("##(boolean) Whether to choose by events instead of basic IDs. (false)");
            pw.println("#selectByEvents ");
            pw.println("##(boolean) Whether to choose with duplication (for bootstrap test). (false)");
            pw.println("#duplication ");
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
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
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

        subsamplingPercent = property.parseDouble("subsamplingPercent", "100");
        if (subsamplingPercent < 0)
            throw new IllegalArgumentException("subsamplingPercent must be positive.");
        selectByEvents = property.parseBoolean("selectByEvents", "false");
        duplication = property.parseBoolean("duplication", "false");
    }

    @Override
    public void run() throws IOException {
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true).stream()
                .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());

        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs, true);
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
        // select required number of basicIDs
        if (!Precision.equals(subsamplingPercent, 100) || duplication) {
            if (selectByEvents) {
                resampleIDsByEvents(subsamplingPercent, duplication);
            } else {
                resampleIDs(subsamplingPercent, duplication);
            }
        }

        // collect all selected basicIDs
        List<BasicID> finalList = new ArrayList<>();
        finalList.addAll(obsIDs);
        finalList.addAll(synIDs);

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "rebuilt", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        BasicIDFile.write(finalList, outPath);}
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
        Set<TimeWindowData> timewindowSet = TimeWindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

        for (TimeWindowData timewindow : timewindowSet) {
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

                // cut traces
                Trace obsTrace = obsID.toTrace();
                Trace synTrace = synID.toTrace();
                double shift = synTrace.getMinX() - obsTrace.getMinX();
                obsTrace = obsTrace.cutWindow(timewindow.getStartTime() - shift, timewindow.getEndTime() - shift);
                synTrace = synTrace.cutWindow(timewindow);

                // remake basicIDs
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

    private void resampleIDsByEvents(double percent, boolean duplication) {
        List<GlobalCMTID> events = obsIDs.stream().map(BasicID::getGlobalCMTID).distinct().sorted().collect(Collectors.toList());
        int numToSample = (int) (events.size() * percent / 100);
        List<GlobalCMTID> selectedEvents = new ArrayList<>();

        if (duplication) {
            System.err.println("Selecting " + numToSample + " events from " + events.size() + " events with duplication.");
            Random random = new Random();
            int[] shuffledIndices = random.ints(numToSample, 0, events.size()).toArray();
            for (int i = 0; i < numToSample; i++) {
//                System.err.println(shuffledIndices[i]);
                selectedEvents.add(events.get(shuffledIndices[i]));
            }
        } else {
            System.err.println("Selecting " + numToSample + " of " + events.size() + " events without duplication.");
            List<Integer> shuffledIndices = IntStream.range(0, events.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(shuffledIndices);
            for (int i = 0; i < numToSample; i++) {
                selectedEvents.add(events.get(shuffledIndices.get(i)));
            }
        }

        // extract IDs of selected events
        obsIDs = obsIDs.stream().filter(id -> selectedEvents.contains(id.getGlobalCMTID())).collect(Collectors.toList());
        synIDs = synIDs.stream().filter(id -> selectedEvents.contains(id.getGlobalCMTID())).collect(Collectors.toList());
    }

    private void resampleIDs(double percent, boolean duplication) {
        int numToSample = (int) (obsIDs.size() * percent / 100);
        List<BasicID> selectedObsIDs = new ArrayList<>();
        List<BasicID> selectedSynIDs = new ArrayList<>();

        if (duplication) {
            System.err.println("Selecting " + numToSample + " pairs of basic IDs with duplication.");
            Random random = new Random();
            int[] shuffledIndices = random.ints(numToSample, 0, obsIDs.size()).toArray();
            for (int i = 0; i < numToSample; i++) {
//                System.err.println(shuffledIndices[i]);
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
