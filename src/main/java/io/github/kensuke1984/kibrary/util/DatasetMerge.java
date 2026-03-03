package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Operation for merging datasets of SAC files.
 * Event directories are created in outPath, and symbolic links to each SAC file is created in the event directories.
 *
 * @author otsuru
 * @since 2022/4/18
 */
public class DatasetMerge extends Operation {

    private static final int MAX_IN = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * The first part of the name of output basic ID and waveform files.
     */
    private String nameRoot;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    /**
     * Threshold to judge which stations are in the same position [deg].
     */
    private double coordinateGrid;

    /**
     * List of paths of input dataset folders.
     */
    private List<Path> inPaths = new ArrayList<>();
    /**
     * List of paths of data entry files, when selecting entries.
     */
    private List<Path> entryPaths = new ArrayList<>();

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
            pw.println("##(String) The first part of the name of output dataset folders, must be set.");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Threshold to judge which stations are in the same position, non-negative [deg]. (0.01)"); // = about 1 km
            pw.println("##  If two stations are closer to each other than this threshold, one will be eliminated.");
            pw.println("#coordinateGrid ");
            pw.println("##########From here on, list up paths of dataset folders to merge.");
            pw.println("##########  Additionally, paths of data entry files can be set when selecting entries.");
            pw.println("##########  Up to " + MAX_IN + " folders can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_IN; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " dataset.");
                pw.println("#inPath" + i + " ");
                pw.println("#entryPath" + i + " ");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public DatasetMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", null);
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        coordinateGrid = property.parseDouble("coordinateGrid", "0.01");
        if (coordinateGrid < 0)
            throw new IllegalArgumentException("coordinateGrid must be non-negative.");

        for (int i = 1; i <= MAX_IN; i++) {
            String inKey = "inPath" + i;
            String entryKey = "entryPath" + i;
            if (property.containsKey(inKey)) {
                inPaths.add(property.parsePath(inKey, null, true, workPath));
                // if entry path is specified, register it; otherwise, add null
                if (property.containsKey(entryKey)) entryPaths.add(property.parsePath(entryKey, null, true, workPath));
                else entryPaths.add(null);
            }
        }
    }

    @Override
    public void run() throws IOException {
        int inputNum = inPaths.size();
        if (inputNum == 0) {
            System.err.println("!! No input dataset folders found.");
            return;
        } else if (inputNum == 1) {
            System.err.println("!! Only 1 dataset folder found. Merging will not be done.");
            return;
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, nameRoot, folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // Map to store observer positions for each event
        Map<GlobalCMTID, Set<HorizontalPosition>> observerPositionMap = new HashMap<>();

        // each datset folder
        for (int i = 0; i < inPaths.size(); i++) {
            Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(inPaths.get(i));

            Set<DataEntry> entrySet = (entryPaths.get(i) != null) ? DataEntryListFile.readAsSet(entryPaths.get(i)) : null;

            // each event folder in dataset
            eventDirs.parallelStream().forEach(eventDir -> {
                try {
                    Set<SACFileName> sacNames = eventDir.sacFileSet();
                    // skip event folder if it is empty
                    if (sacNames.size() == 0) return;

                    Path outEventPath = outPath.resolve(eventDir.getName());
                    Files.createDirectories(outEventPath);

                    // get Set of observer positions for this event
                    GlobalCMTID event = eventDir.getGlobalCMTID();
                    Set<HorizontalPosition> originalPositionSet = null;
                    if (observerPositionMap.containsKey(event)) originalPositionSet = observerPositionMap.get(event);
                    Set<HorizontalPosition> additionalPositionSet = new HashSet<>();

                    // each sac file
                    for (SACFileName sacName : sacNames) {
                        DataEntry entry = sacName.readHeader().toDataEntry();
                        HorizontalPosition observerPosition = entry.getObserver().getPosition();

                        // select based on data entry file if it is specified
                        if (entrySet != null) {
                            if (!entrySet.contains(entry)) continue;
                        }

                        // check observer position duplication
                        if (originalPositionSet != null) {
                            boolean isDuplicate = originalPositionSet.stream()
                                    .anyMatch(pos -> observerPosition.computeEpicentralDistanceDeg(pos) < coordinateGrid);
                            if (isDuplicate) {
                                System.err.println("!! Duplication of observer position, skipping: " + sacName.getName());
                                continue;
                            }
                        }

                        // add observer position to new Set
                        additionalPositionSet.add(observerPosition);

                        // create soft link
                        Path outSacPath = outEventPath.resolve(sacName.getName());
                        if (Files.exists(outSacPath)) {
                            System.err.println("!! Duplication of " + sacName.getName() + " , skipping.");
                        } else {
                            Files.createSymbolicLink(outSacPath, Paths.get("..", "..").resolve(sacName.toPath()));
                        }
                    }

                    if (originalPositionSet != null) {
                        originalPositionSet.addAll(additionalPositionSet);
                    } else {
                        observerPositionMap.put(event, additionalPositionSet);
                    }

                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

}
