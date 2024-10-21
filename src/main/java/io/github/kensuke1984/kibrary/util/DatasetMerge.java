package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
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

                    // each sac file
                    for (SACFileName sacName : sacNames) {

                        // select based on data entry file if it is specified
                        if (entrySet != null) {
                            if (!entrySet.contains(sacName.readHeader().toDataEntry())) continue;
                        }

                        // create soft link
                        Path outSacPath = outEventPath.resolve(sacName.getName());
                        if (Files.exists(outSacPath)) {
                            System.err.println("!! Duplication of " + sacName.getName() + " , skipping.");
                        } else {
                            Files.createSymbolicLink(outSacPath, Paths.get("..", "..").resolve(sacName.toPath()));
                        }
                    }

                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

            });
        }
    }
}
