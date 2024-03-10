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
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Maps the sensitivity kernel.
 * <p>
 * NOTE: the voxel volume is NOT multiplied.
 *
 * @see Interpolation#inEachMapLayer(Map, double, double, boolean, double, boolean, boolean)
 * @author otsuru
 * @since 2022/4/14
 */
public class SensitivityKernelMapper extends Operation {

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
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
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
    private double[] boundaries;
    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private int nPanelsPerRow;
    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;
    private double marginLatitude;
    private boolean setLatitudeByKm;
    private double marginLongitude;
    private boolean setLongitudeByKm;
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
            pw.println("##(double[]) The display values of each layer boundary, listed from the inside using spaces. (0 50 100 150 200 250 300 350 400)");
            pw.println("#boundaries ");
            pw.println("##(int[]) Indices of layers to display, listed from the inside using spaces, when specific layers are to be displayed.");
            pw.println("##  Layers are numbered 0, 1, 2, ... from the inside.");
            pw.println("#displayLayers ");
            pw.println("##(int) Number of panels to display in each row. (4)");
            pw.println("#nPanelsPerRow ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,360] lat:[-90,90].");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##########The following should be set to half of dLatitude and dLongitude used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##########Parameters for perturbation values");
            pw.println("##(double) The factor to amplify the sensitivity values. (1e29)");
            pw.println("#amplification ");
            pw.println("##(double) Range of scale. (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing. (false)");
            pw.println("#mosaic ");
        }
        System.err.println(outPath + " is created.");
    }

    public SensitivityKernelMapper(Property property) throws IOException {
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

        boundaries = property.parseDoubleArray("boundaries", "0 50 100 150 200 250 300 350 400");
        if (property.containsKey("displayLayers")) displayLayers = property.parseIntArray("displayLayers", null);
        nPanelsPerRow = property.parseInt("nPanelsPerRow", "4");
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitude = property.parseDouble("marginLatitudeKm", null);
            setLatitudeByKm = true;
        } else {
            marginLatitude = property.parseDouble("marginLatitudeDeg", "2.5");
            setLatitudeByKm = false;
        }
        if (marginLatitude <= 0) throw new IllegalArgumentException("marginLatitude must be positive");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitude = property.parseDouble("marginLongitudeKm", null);
            setLongitudeByKm = true;
        } else {
            marginLongitude = property.parseDouble("marginLongitudeDeg", "2.5");
            setLongitudeByKm = false;
        }
        if (marginLongitude <= 0) throw new IllegalArgumentException("marginLongitude must be positive");

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
        Set<FullPosition> positions = partialIDs.stream().map(partial -> partial.getVoxelPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        double gridInterval = PerturbationMapShellscript.decideGridSampling(positions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "kernel", folderTag, appendFolderDate, GadgetAid.getTemporaryString());
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

                        for (double startTime : startTimes) {
                            List<PartialID> partialsForWindow = partialsForEntry.stream()
                                    .filter(partial -> partial.getStartTime() == startTime).collect(Collectors.toList());

                            List<String> phaseStrings = Stream.of(partialsForEntry.get(0).getPhases()).map(Phase::toString).collect(Collectors.toList());
                            String phaselist = String.join("-", phaseStrings);

                            // compute sensitivity at each voxel
                            Map<FullPosition, Double> discreteMap = new HashMap<>();
                            for (PartialID partial : partialsForWindow) {
                                double[] data = partial.getData();

                                double cumulativeSensitivity = 0;
                                for (int i = 0; i < data.length; i++) {
                                    cumulativeSensitivity += data[i] * data[i];
                                }
                                discreteMap.put(partial.getVoxelPosition(), cumulativeSensitivity * amplification);
                            }

                            // output discrete perturbation file
                            String fileNameRoot = "kernel_" + phaselist + "_" + component + "_" + variableType + String.format("_t0%d", (int) startTime);
                            Path outputDiscretePath = observerPath.resolve(fileNameRoot + ".lst");
                            PerturbationListFile.write(discreteMap, outputDiscretePath);
                            // output interpolated perturbation file, in range [0:360) when crossDateLine==true so that mapping will succeed
                            Map<FullPosition, Double> interpolatedMap = Interpolation.inEachMapLayer(discreteMap, gridInterval,
                                    marginLatitude, setLatitudeByKm, marginLongitude, setLongitudeByKm, mosaic);
                            Path outputInterpolatedPath = observerPath.resolve(fileNameRoot + "XY.lst");
                            PerturbationListFile.write(interpolatedMap, crossDateLine, outputInterpolatedPath);

                            PerturbationMapShellscript script = new PerturbationMapShellscript(variableType, radii,
                                    boundaries, mapRegion, gridInterval, scale, fileNameRoot, nPanelsPerRow); //TODO unit in scale is not correct
                            if (displayLayers != null) script.setDisplayLayers(displayLayers);
                            script.write(observerPath);
                        }
                    }
                }
            }
        }
        System.err.println("After this finishes, please enter each " + outPath
                + "/event_observerFolder/ and run *Grid.sh and *Map.sh");
    }

}
