package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Creates a movie of partials inside a cross section.
 * <p>
 * NOTE: the voxel volume is NOT multiplied.
 *
 * @author otsuru
 * @since 2023/5/29
 */
public class PartialsMovieMaker extends Operation {

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
     * components to make maps for
     */
    private Set<SACComponent> components;
    /**
     * variable types to make maps for
     */
    private Set<VariableType> variableTypes;
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<String> tendObservers = new HashSet<>();

    /**
     * partial waveform folder
     */
    private Path partialPath;

    private double amplification;
    private double scale;
    /**
     * Whether to display map as mosaic without smoothing
     */
    private boolean mosaic;


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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial waveform folder, must be set");
            pw.println("#partialPath partial");
            pw.println("##VariableTypes to be used, listed using spaces (MU)");
            pw.println("#variableTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces, must be set");
            pw.println("#tendObservers ");

            pw.println("##########Parameters for perturbation values");
            pw.println("##(double) The factor to amplify the sensitivity values (1e29)");
            pw.println("#amplification ");
            pw.println("##(double) Range of scale (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing (false)");
            pw.println("#mosaic ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsMovieMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialPath = property.parsePath("partialPath", null, true, workPath);
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU"))
                .map(VariableType::valueOf).collect(Collectors.toSet());
        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());

        amplification = property.parseDouble("amplification", "1e29");
        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
    }

    @Override
    public void run() throws IOException {

        // read input
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream()
                .filter(id -> id.getParameterType().equals(ParameterType.VOXEL)).collect(Collectors.toList());
        if (partialIDs.size() == 0) {
            System.err.println("No 3-D partials.");
            return;
        }

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "movie", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (SACComponent component : components) {
            for (VariableType variableType : variableTypes) {
                for (GlobalCMTID event : tendEvents) {
                    for (String observerName : tendObservers) {
                        List<PartialID> partialsForEntry = partialIDs.stream().filter(partial ->
                                partial.getSacComponent().equals(component)
                                && partial.getVariableType().equals(variableType)
                                && partial.getGlobalCMTID().equals(event)
                                && partial.getObserver().toString().equals(observerName))
                                .collect(Collectors.toList());
                        if (partialsForEntry.size() == 0) continue;
                        System.err.println("Working for " + component  + " " + variableType + " " + event + " " + observerName);

                        Path observerPath = outPath.resolve(event.toString() + "_" + observerName.toString());
                        Files.createDirectories(observerPath);

                        double[] startTimes = partialsForEntry.stream().mapToDouble(PartialID::getStartTime).distinct().sorted().toArray();

                        // for each timewindow
                        for (double startTime : startTimes) {
                            List<PartialID> partialsForWindow = partialsForEntry.stream()
                                    .filter(partial -> partial.getStartTime() == startTime).collect(Collectors.toList());

                            // get npts and samplingHz for this window
                            double[] nptsTmp = partialsForWindow.stream().mapToDouble(PartialID::getNpts).distinct().sorted().toArray();
                            if (nptsTmp.length != 1) throw new IllegalStateException("npts mismatch in partial IDs for " + event + " " + observerName);
                            double npts = nptsTmp[0];
                            double[] samplingHzTmp = partialsForWindow.stream().mapToDouble(PartialID::getNpts).distinct().sorted().toArray();
                            if (samplingHzTmp.length != 1) throw new IllegalStateException("samplingHz mismatch in partial IDs for " + event + " " + observerName);
                            double samplingHz = samplingHzTmp[0];

                            // for each time step
                            for (int i = 0; i < npts; i++) {
                                double time = startTime + i / samplingHz;

                                // value of partial derivative at each voxel
                                Map<FullPosition, Double> discreteMap = new HashMap<>();
                                for (PartialID partial : partialsForWindow) {
                                    double[] data = partial.getData();
                                    discreteMap.put(partial.getVoxelPosition(), data[i] * amplification);
                                }

                                // output discrete perturbation file
                                // The number part of output file names has to be padded with 0 for the "convert" command to work.
                                String fileNameRoot = "snapshot_" + component + "_" + variableType + "_t"
                                        + MathAid.padToString(time, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.PRECISION, true);
                                Path outputDiscretePath = observerPath.resolve(fileNameRoot + ".lst");
                                PerturbationListFile.write(discreteMap, outputDiscretePath);
                            }
                        }
                    }
                }
            }
        }
        System.err.println("After this finishes, please enter each " + outPath
                + "/event_observerFolder/ and run *Grid.sh and *Map.sh");
    }

}
