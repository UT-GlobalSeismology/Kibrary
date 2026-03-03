package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Operation for merging datasets in multiple partial waveform folders.
 *
 * @author otsuru
 * @since 2022/2/12 Modified the original PartialIDMerge which was in kibrary.waveform.addons.
 */
public class PartialIDMerge extends Operation {

    private static final int MAX_INPUT = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * The first part of the name of output partial folder.
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

    private List<Path> partialPaths = new ArrayList<>();

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
            pw.println("##(String) The first part of the name of output partial waveform folder. (partial)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##########From here on, list up paths of partial waveform folders to merge.");
            pw.println("########## Up to " + MAX_INPUT + " folders can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " folder.");
                pw.println("#partialPath" + i + " partial");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public PartialIDMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "partial");
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        for (int i = 1; i <= MAX_INPUT; i++) {
            String partialKey = "partialPath" + i;
            if (property.containsKey(partialKey)) {
                partialPaths.add(property.parsePath(partialKey, null, true, workPath));
            }
        }
    }


    @Override
    public void run() throws IOException {
        int inputNum = partialPaths.size();
        if (inputNum == 0) {
            System.err.println("!! No input folders found.");
            return;
        } else if (inputNum == 1) {
            System.err.println("!! Only 1 input folder found. Merging will not be done.");
            return;
        }

        // read PartialIDs from all input files
        List<PartialID> partialIDs = new ArrayList<>();
        for (int i = 0; i < inputNum; i++) {
            List<PartialID> srcIDs = PartialIDFile.read(partialPaths.get(i), true);
            partialIDs.addAll(srcIDs);
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, nameRoot, folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output merged files
        PartialIDFile.write(partialIDs, outPath);

    }
}
