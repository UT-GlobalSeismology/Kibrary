package io.github.kensuke1984.kibrary.correction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation to set static correction data based on time shift values of other timewindows.
 * <p>
 * This can be used, for example, when you want to correct S-phase travel times of R component waveforms
 * using time shift values of their corresponding T component waveforms.
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> time shift data for the (event, observer)-pair, regardless of component and startTime, is included in the input static correction file </li>
 * </ul>
 *
 * @author otsuru
 * @since 2022/10/17
 */
public class StaticCorrectionForger extends Operation {

    private final Property property;
    /**
     * Path of the work folder
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
     * components for computation
     */
    private Set<SACComponent> components;

    /**
     * Path of a timewindow information file
     */
    private Path timewindowPath;
    /**
     * Path of a reference static correction file
     */
    private Path refStaticCorrectionPath;

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
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a timewindow file, must be set.");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a reference static correction file, must be set.");
            pw.println("#refStaticCorrectionPath staticCorrection.dat");
        }
        System.err.println(outPath + " is created.");
    }

    public StaticCorrectionForger(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        refStaticCorrectionPath = property.parsePath("refStaticCorrectionPath", null, true, workPath);

    }

    @Override
    public void run() throws IOException {

        // gather all timewindows to be processed
        Set<TimewindowData> timewindowSet = TimewindowDataFile.read(timewindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());

        // read reference static correction data
        Set<StaticCorrectionData> refStaticCorrectionSet = StaticCorrectionDataFile.read(refStaticCorrectionPath);

        // forge static corrections for new dataset
        Set<StaticCorrectionData> forgedStaticCorrectionSet = new HashSet<>();
        for (TimewindowData window : timewindowSet) {

            // choose reference static correction data based on event and observer
            List<StaticCorrectionData> refStaticCorrectionsTmp = refStaticCorrectionSet.stream()
                    .filter(s -> s.getGlobalCMTID().equals(window.getGlobalCMTID()) && s.getObserver().equals(window.getObserver()))
                    .collect(Collectors.toList());

            if (refStaticCorrectionsTmp.size() == 0) {
                // if static correction for a timewindow does not exist, skip
                System.err.println("Found no static correction for window " + window + " , skipping.");
                continue;
            } else if (refStaticCorrectionsTmp.size() > 1) {
                // if more than one static correction exists for a timewindow, choose one
                System.err.println("Caution: found more than 1 static correction for window " + window);
            }
            StaticCorrectionData refStaticCorrection = refStaticCorrectionsTmp.get(0);

            StaticCorrectionData forgedStaticCorrection = new StaticCorrectionData(
                    window.getObserver(), window.getGlobalCMTID(), window.getComponent(), window.getStartTime(),
                    refStaticCorrection.getTimeshift(), refStaticCorrection.getAmplitudeRatio(), window.getPhases());
            forgedStaticCorrectionSet.add(forgedStaticCorrection);
        }

        String dateStr = GadgetAid.getTemporaryString();
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "staticCorrection", fileTag, appendFileDate, dateStr, ".dat");

        StaticCorrectionDataFile.write(forgedStaticCorrectionSet, outputPath);
    }
}
