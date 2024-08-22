package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Merges {@link TimewindowDataFile}s.
 *
 * TODO merge windows of same entry
 *
 * @author otsuru
 * @since 2022/12/13
 */
public class TimewindowMerge extends Operation {

    private static final int MAX_NUM = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * Paths of input time window files.
     */
    private List<Path> timewindowPaths = new ArrayList<>();


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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##########From here on, list up paths of time window files.");
            pw.println("##########  Up to " + MAX_NUM + " files can be managed. Any index may be left blank.");
            for (int i = 1; i <= MAX_NUM; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " file.");
                pw.println("#timewindowPath" + i + " timewindow.dat");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public TimewindowMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        for (int i = 1; i <= MAX_NUM; i++) {
            String timewindowKey = "timewindowPath" + i;
            if (property.containsKey(timewindowKey)) {
                timewindowPaths.add(property.parsePath(timewindowKey, null, true, workPath));
            }
        }
    }

    @Override
    public void run() throws IOException {
        int fileNum = timewindowPaths.size();
        if (fileNum == 0) {
            System.err.println("!! No input files found.");
            return;
        } else if (fileNum == 1) {
            System.err.println("!! Only 1 input file found. Merging will not be done.");
            return;
        }

        // read time windows from all input files
        Set<TimewindowData> timewindows = new HashSet<>();
        for (Path timewindowPath : timewindowPaths) {
            Set<TimewindowData> srcTimewindows = TimewindowDataFile.read(timewindowPath);
            timewindows.addAll(srcTimewindows);
        }

        // output merged file
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "timewindow", fileTag, appendFileDate, null, ".dat");
        TimewindowDataFile.write(timewindows, outputPath);
    }

}
