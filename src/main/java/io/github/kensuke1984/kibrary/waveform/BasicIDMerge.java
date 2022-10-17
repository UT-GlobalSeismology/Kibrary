package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Operation for merging pairs of basic ID files and basic waveform files.
 *
 * @author otsuru
 * @since 2022/1/2 Created based on the original BasicIDMerge which was in kibrary.waveform.addons.
 */
public class BasicIDMerge extends Operation {

    private static final int MAX_PAIR = 10;

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
    private String fileTag;

    private List<Path> basicIDPaths = new ArrayList<>();
    private List<Path> basicPaths = new ArrayList<>();


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
            pw.println("##(String) The first part of the name of output basic ID and waveform files (actual)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##########From here on, list up pairs of the paths of a basic ID file and a basic waveform file.");
            pw.println("########## Up to " + MAX_PAIR + " pairs can be managed. Any pair may be left blank.");
            for (int i = 1; i <= MAX_PAIR; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " pair");
                pw.println("#basicIDPath" + i + " actualID" + i + ".dat");
                pw.println("#basicPath" + i + " actual" + i + ".dat");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public BasicIDMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "actual");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        for (int i = 1; i <= MAX_PAIR; i++) {
            String basicIDKey = "basicIDPath" + i;
            String basicKey = "basicPath" + i;
            if (!property.containsKey(basicIDKey) && !property.containsKey(basicKey)) {
                continue;
            } else if (!property.containsKey(basicIDKey) || !property.containsKey(basicKey)) {
                throw new IllegalArgumentException("Basic ID file and basic waveform file must be set in pairs.");
            }
            basicIDPaths.add(property.parsePath(basicIDKey, null, true, workPath));
            basicPaths.add(property.parsePath(basicKey, null, true, workPath));
        }
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
        List<BasicID> basicIDs = new ArrayList<>();
        for (int i = 0; i < pairNum; i++) {
            BasicID[] srcIDs = BasicIDFile.read(basicIDPaths.get(i), basicPaths.get(i));
            Arrays.stream(srcIDs).forEach(id -> basicIDs.add(id));
        }

        // extract set of observers, events
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        basicIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
        });

        // output merged files
        String dateStr = GadgetAid.getTemporaryString();
        Path observerFilePath = workPath.resolve("observer" + dateStr + ".lst");
        Path eventFilePath = workPath.resolve("event" + dateStr + ".lst");
        Path outputIDPath = workPath.resolve(DatasetAid.generateOutputFileName(nameRoot + "ID", fileTag, dateStr, ".dat"));
        Path outputWavePath = workPath.resolve(DatasetAid.generateOutputFileName(nameRoot, fileTag, dateStr, ".dat"));

        System.err.println("Outputting in " + observerFilePath);
        ObserverListFile.write(observerSet, workPath.resolve(observerFilePath));

        System.err.println("Outputting in " + eventFilePath);
        EventListFile.write(eventSet, workPath.resolve(eventFilePath));

        BasicIDFile.write(basicIDs, outputIDPath, outputWavePath);
    }

}
