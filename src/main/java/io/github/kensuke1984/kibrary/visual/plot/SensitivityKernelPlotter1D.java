package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Plots the sensitivity kernel for 1D perturbations.
 * <p>
 * NOTE: the layer volume is NOT multiplied.
 *
 * @since 2025/3/9
 * @author otsuru
 */
public class SensitivityKernelPlotter1D extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;
    /**
     * Partial waveform folder.
     */
    private Path partialPath;
    /**
     * Variable types to make maps for.
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

    private double amplification;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##VariableTypes to be used, listed using spaces. (MU)");
            pw.println("#variableTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces, must be set.");
            pw.println("#tendObservers ");
            pw.println("##########Parameters for perturbation values");
            pw.println("##(double) The factor to amplify the normalized sensitivity values. (1)");
            pw.println("#amplification ");
        }
        System.err.println(outPath + " is created.");
    }

    public SensitivityKernelPlotter1D(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialPath = property.parsePath("partialPath", null, true, workPath);
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU"))
                .map(VariableType::valueOf).collect(Collectors.toSet());
        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());

        amplification = property.parseDouble("amplification", "1");
    }

    @Override
    public void run() throws IOException {

        // read input
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream()
                .filter(id -> id.getParameterType().equals(ParameterType.LAYER)).collect(Collectors.toList());
        if (partialIDs.size() == 0) {
            System.err.println("No 1-D partials.");
            return;
        }
        double[] radii = partialIDs.stream().mapToDouble(partial -> partial.getVoxelPosition().getR()).distinct().sorted().toArray();
        double minR = Arrays.stream(radii).min().getAsDouble();
        double maxR = Arrays.stream(radii).max().getAsDouble();

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "kernel1D", folderTag, appendFolderDate, null);
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
                        System.err.println("Working for " + component + " " + variableType + " " + event + " " + observerName);

                        Path observerPath = outPath.resolve(event.toString() + "_" + observerName.toString());
                        Files.createDirectories(observerPath);

                        double[] startTimes = partialsForEntry.stream().mapToDouble(PartialID::getStartTime).distinct().sorted().toArray();

                        for (double startTime : startTimes) {
                            List<PartialID> partialsForWindow = partialsForEntry.stream()
                                    .filter(partial -> partial.getStartTime() == startTime)
                                    .sorted(Comparator.comparing(partial -> partial.getVoxelPosition()))
                                    .collect(Collectors.toList());

                            List<String> phaseStrings = Stream.of(partialsForEntry.get(0).getPhases()).map(Phase::toString).collect(Collectors.toList());
                            String phaselist = String.join("-", phaseStrings);

                            // compute sensitivity at each voxel
                            Map<FullPosition, Double> discreteMap = new LinkedHashMap<>();
                            for (PartialID partial : partialsForWindow) {
                                double[] data = partial.getData();

                                double cumulativeSensitivity = 0;
                                for (int i = 0; i < data.length; i++) {
                                    cumulativeSensitivity += data[i] * data[i];
                                }
                                discreteMap.put(partial.getVoxelPosition(), cumulativeSensitivity);
                            }

                            // normalize
                            double max = discreteMap.values().stream().mapToDouble(Double::valueOf).max().getAsDouble();
                            System.err.println("  Normalizing by maximum value " + max);
                            System.err.println("    and amplifying by " + amplification);
                            discreteMap.entrySet().forEach(entry -> discreteMap.put(entry.getKey(), entry.getValue() / max * amplification));

                            // output discrete perturbation file
                            ScalarType scalarType = ScalarType.kernelOf(component);
                            String tag = phaselist + String.format("_t0%d", (int) startTime);
                            Path outputDiscretePath = observerPath.resolve(ScalarListFile.generateFileName(variableType, scalarType, tag));
                            ScalarListFile.write(discreteMap, outputDiscretePath);

                            Path plotPath = observerPath.resolve("plot.plt");
                            createScript(plotPath, outputDiscretePath, minR, maxR);
                        }
                    }
                }
            }
        }
    }

    private void createScript(Path scriptPath, Path kernelPath, double minR, double maxR) throws IOException {
        String fileNameRoot = FileAid.extractNameRoot(scriptPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptPath))) {
            pw.println("set term pngcairo enhanced size 400,600 font 'Helvetica,20'");
            pw.println("set output '" + fileNameRoot + ".png'");
            pw.println("set xrange [0:" + amplification + "]");
            pw.println("set yrange [" + minR + ":" + maxR + "]");
            pw.println("p '" + kernelPath.getFileName() + "' u 4:3 w l notitle, \\");
            pw.println("  3480 w l notitle, \\");
            pw.println("  6371 w l notitle");
        }
        GnuplotFile plot = new GnuplotFile(scriptPath);
        plot.execute();
    }

}
