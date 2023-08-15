package io.github.kensuke1984.kibrary.waveform;

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
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Operation for merging datasets in multiple basic waveform folders.
 *
 * @author otsuru
 * @since 2022/1/2 Created based on the original BasicIDMerge which was in kibrary.waveform.addons.
 */
public class BasicIDMerge extends Operation {

    private static final int MAX_INPUT = 10;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The first part of the name of output basic folder
     */
    private String nameRoot;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;

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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) The first part of the name of output basic waveform folder. (actual)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##########From here on, list up paths of basic waveform folders to merge.");
            pw.println("########## Up to " + MAX_INPUT + " folders can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " folder.");
                pw.println("#basicPath" + i + " actual");
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
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        for (int i = 1; i <= MAX_INPUT; i++) {
            String basicKey = "basicPath" + i;
            if (property.containsKey(basicKey)) {
                basicPaths.add(property.parsePath(basicKey, null, true, workPath));
            }
        }
    }

    @Override
    public void run() throws IOException {
        int inputNum = basicPaths.size();
        if (inputNum == 0) {
            System.err.println("!! No input folders found.");
            return;
        } else if (inputNum == 1) {
            System.err.println("!! Only 1 input folder found. Merging will not be done.");
            return;
        }

        // read BasicIDs from all input files
        List<BasicID> basicIDs = new ArrayList<>();
        for (int i = 0; i < inputNum; i++) {
            List<BasicID> srcIDs = BasicIDFile.read(basicPaths.get(i), true);
            basicIDs.addAll(srcIDs);
        }

        // extract set of observers, events
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        basicIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
        });

        Path outPath = DatasetAid.createOutputFolder(workPath, nameRoot, folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output merged files
        BasicIDFile.write(basicIDs, outPath);
        Path observerFilePath = outPath.resolve("observer.lst");
        Path eventFilePath = outPath.resolve("event.lst");
        ObserverListFile.write(observerSet, observerFilePath);
        EventListFile.write(eventSet, eventFilePath);
    }

}
