package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * An operation to select or resample PartialIDs.
 * This allows for selection of certain data to be used in the inversion.
 * <p>
 * To select PartialIDs of certain raypaths, supply with a {@link DataEntryListFile} including a list of raypaths to be selected.
 * Timewindows is selected by the phases that they must include.
 * Each partial can be re-cut depending on the input wimewindow file.
 *
 * @author rei
 * @since 2023/11/13
 */
public class PartialIDRebuilder extends Operation {

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
         * components to be used
         */
        private Set<SACComponent> components;
        /**
         * partial waveform folder
         */
        private Path partialPath;
        /**
         * Path of a timewindow information file
         */
        private Path timewindowPath;
        /**
         * Path of a data entry file for selection
         */
        private Path dataEntryPath;
        /**
         * Information file about voxels for perturbations
         */
        private Path voxelPath;
        private Set<FullPosition> voxelPositionSet;
        /**
         * set of partial type for computation
         */
        private Set<VariableType> variableTypes;
        /**
         * Phases that must be included in timewindows to be selected
         */
        private String[] requiredPhases;

        private List<PartialID> partialIDs;

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
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath ");
            pw.println("##Path of a timewindow file, if you want to re-cut waveforms");
            pw.println("#timewindowPath ");
            pw.println("##Path of a data entry list file, if you want to select raypaths");
            pw.println("#dataEntryPath ");
            pw.println("##Path of a voxel information file, if you want to select the voxels to be worked for");
            pw.println("#voxelPath ");
            pw.println("##VariableTypes to use, listed using spaces (MU)");
            pw.println("#variableTypes ");
            pw.println("##Phases to be included in timewindows to use, listed using spaces. To use all phases, leave this unset.");
            pw.println("#requiredPhases ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialIDRebuilder(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialPath = property.parsePath("partialPath", null, true, workPath);
        if (property.containsKey("timewindowPath")) {
            timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        }
        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        if (property.containsKey("voxelPath")) {
            voxelPath = property.parsePath("voxelPath", null, true, workPath);
        }
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        if (property.containsKey("requiredPhases"))
            requiredPhases = property.parseStringArray("requiredPhases", null);
    }

    @Override
    public void run() throws IOException {
        // read input
        partialIDs = PartialIDFile.read(partialPath, true);
        if (voxelPath != null) voxelPositionSet = new VoxelInformationFile(voxelPath).fullPositionSet();

        // check components, variable types, and voxel
        partialIDs = partialIDs.stream().filter(id -> components.contains(id.getSacComponent())
                && variableTypes.contains(id.getVariableType())).collect(Collectors.toList());
        if (voxelPath != null) partialIDs = partialIDs.stream().
                filter(id -> voxelPositionSet.contains(id.getVoxelPosition())).collect(Collectors.toList());
        if (partialIDs.size() == 0) return;

        // select partialIDs to used based on criteria
        if (dataEntryPath != null || requiredPhases != null) {
            selectByCriteria();
        }
        if (partialIDs.size() == 0) return;

        // cut partialIDs based on timewindow file
        if (timewindowPath != null) {
            cutWindow();
        }
        if (partialIDs.size() == 0) return;

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "rebuilt", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        PartialIDFile.write(partialIDs, outPath);
    }

    private void selectByCriteria() throws IOException {
        List<PartialID> selectedPartialIDs = new ArrayList<>();

        // read entry set to be used for selection
        Set<DataEntry> entrySet = null;
        if (dataEntryPath != null) entrySet = DataEntryListFile.readAsSet(dataEntryPath);

        for (PartialID partialID : partialIDs) {
            // check raypath
            if (entrySet != null) {
                DataEntry entry = new DataEntry(partialID.getGlobalCMTID(), partialID.getObserver(), partialID.getSacComponent());
                if (!entrySet.contains(entry)) continue;
            }

            // check phases
            if (requiredPhases != null) {
                Set<Phase> requiredPhaseSet = Arrays.stream(requiredPhases).map(Phase::create).collect(Collectors.toSet());
                List<Phase> phases = Arrays.asList(partialID.getPhases());
                if (requiredPhaseSet.stream().allMatch(reqPhase -> phases.contains(reqPhase)) == false) {
                    continue;
                }
            }

            // add partialID that passed criteria
            selectedPartialIDs.add(partialID);
        }
        System.err.println("Selected " + selectedPartialIDs.size() + " partial IDs based on criteria.");

        // replace list by selected one
        partialIDs = selectedPartialIDs;
    }

    private void cutWindow() throws IOException {
        List<PartialID> cutPartialIDs = new ArrayList<>();

        //read timewindow file and select based on component and entries
        Set<TimewindowData> timewindowSet = TimewindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);

        for (TimewindowData timewindow : timewindowSet) {
            // select corresponding partialIDs with timewindow
            List<PartialID> correspondingIDs = partialIDs.stream().filter(id ->
                timewindow.getGlobalCMTID().equals(id.getGlobalCMTID()) && timewindow.getObserver().equals(id.getObserver()) &&
                timewindow.getComponent().equals(id.getSacComponent())).collect(Collectors.toList());

            for (PartialID id : correspondingIDs) {
                Trace trace = id.toTrace().cutWindow(timewindow);

                PartialID cutPartialID = new PartialID(id.getObserver(), id.getGlobalCMTID(), id.getSacComponent(), id.getSamplingHz(),
                        trace.getMinX(), trace.getLength(), id.getMinPeriod(), id.getMaxPeriod(), timewindow.getPhases(),
                        id.isConvolved(), id.getParameterType(), id.getVariableType(), id.getVoxelPosition(), trace.getY());
                cutPartialIDs.add(cutPartialID);
            }
        }
        partialIDs = cutPartialIDs;
    }
}
