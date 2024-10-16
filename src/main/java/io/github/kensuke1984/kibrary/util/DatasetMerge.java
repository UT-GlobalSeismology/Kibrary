package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
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
            pw.println("########## Up to " + MAX_IN + " folders can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_IN; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " dataset.");
                pw.println("#inPath" + i + " ");
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
            if (property.containsKey(inKey)) {
                inPaths.add(property.parsePath(inKey, null, true, workPath));
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

        // each datset folder
        for (Path inPath : inPaths) {
            Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(inPath);

            // each event folder in dataset
            for (EventFolder eventDir : eventDirs) {
                Set<SACFileName> sacFiles = eventDir.sacFileSet();
                // skip event folder if it is empty
                if (sacFiles.size() == 0) continue;

                Path outEventPath = outPath.resolve(eventDir.getName());
                Files.createDirectories(outEventPath);

                // each sac file
                for (SACFileName sacFile : sacFiles) {

                    Path outSacPath = outEventPath.resolve(sacFile.getName());
                    if (Files.exists(outSacPath)) {
                        System.err.println("!! Duplication of " + sacFile.getName() + " , skipping.");
                    } else {
                        Files.createSymbolicLink(outSacPath, Paths.get("..", "..").resolve(sacFile.toPath()));
                    }
                }
            }
        }
    }
}
