package io.github.kensuke1984.kibrary.correction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Maker of static correction suggested by Nozomu Takeuchi.
 * It seeks up-and-down two peaks in given {@link TimewindowData} for each path.
 * <p>
 * Values of the correction is by the average time of arrivals and amplitudes of those peaks.
 * <p>
 * Start time for identification is a start time in the given {@link TimewindowDataFile}.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class TakeuchiStaticCorrection extends Operation {

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
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * The time window data file to work for.
     */
    private Path timewindowPath;
    /**
     * Folder containing observed data.
     */
    private Path obsPath;
    /**
     * Folder containing synthetic data.
     */
    private Path synPath;
    /**
     * Whether the synthetics have already been convolved.
     */
    private boolean convolved;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet = Collections.synchronizedSet(new HashSet<>());

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a root directory containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root directory containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved. (true)");
            pw.println("#convolved ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. (20)");
            pw.println("#sacSamplingHz ");
        }
        System.err.println(outPath + " is created.");
    }

    public TakeuchiStaticCorrection(Property property) throws IOException {
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
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");
    }

    @Override
    public void run() throws IOException {
        // gather all time windows to be processed
        sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());

        Set<SACFileName> nameSet;
        try {
            nameSet = DatasetAid.sacFileNameSet(obsPath);
        } catch (Exception e3) {
            throw new RuntimeException(obsPath + " may have problems");
        }
        nameSet.parallelStream().filter(name -> components.contains(name.getComponent())).forEach(this::compare);

        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "staticCorrection", fileTag, appendFileDate, null, ".dat");
        StaticCorrectionDataFile.write(staticCorrectionSet, outputPath);
    }

    private void compare(SACFileName obsSacFileName) {
        try {
            compare(obsSacFileName, getPair(obsSacFileName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SACFileName getPair(SACFileName obsSacFileName) {
        String ext = obsSacFileName.getComponent() + (convolved ? "sc" : "s");
        String eventID = obsSacFileName.getGlobalCMTID().toString();
        String name = obsSacFileName.getObserverID() + '.' + eventID + '.' + ext;
        return new SACFileName(synPath.resolve(eventID + "/" + name));
    }

    private void compare(SACFileName obsName, SACFileName synName) throws IOException {
        String observerID = obsName.getObserverID();
        GlobalCMTID id = obsName.getGlobalCMTID();
        SACComponent component = obsName.getComponent();
        Set<TimewindowData> timeWindowSet = sourceTimewindowSet.stream()
                .filter(info -> info.getObserver().toString().equals(observerID))
                .filter(info -> info.getGlobalCMTID().equals(id))
                .filter(info -> info.getComponent() == component).collect(Collectors.toSet());
        if (timeWindowSet.size() != 1) throw new RuntimeException(timewindowPath + " is invalid.");

        TimewindowData timeWindow = timeWindowSet.iterator().next();
        SACFileAccess obsSac = obsName.read();
        SACFileAccess synSac = synName.read();
        Observer station = obsSac.getObserver();
        Trace obsTrace = obsSac.createTrace().cutWindow(timeWindow, sacSamplingHz);
        Trace synTrace = synSac.createTrace().cutWindow(timeWindow, sacSamplingHz);
        double obsT = (obsTrace.getXforMaxYValue() + obsTrace.getXforMinYValue()) / 2;
        double synT = (synTrace.getXforMaxYValue() + synTrace.getXforMinYValue()) / 2;
        double timeShift = synT - obsT;
        double obsAmp = (obsTrace.getMaxY() - obsTrace.getMinY()) / 2;
        double synAmp = (synTrace.getMaxY() - synTrace.getMinY()) / 2;
        double amplitudeRatio = obsAmp / synAmp;
        StaticCorrectionData sc = new StaticCorrectionData(station, id, component, timeWindow.getStartTime(),
                        timeShift, amplitudeRatio, timeWindow.getPhases());
        staticCorrectionSet.add(sc);
    }

}
