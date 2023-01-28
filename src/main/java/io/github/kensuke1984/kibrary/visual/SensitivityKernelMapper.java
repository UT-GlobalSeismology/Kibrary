package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

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
     * components to make maps for
     */
    private Set<SACComponent> components;
    /**
     * partial types to make maps for
     */
    private Set<PartialType> partialTypes;
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<String> tendObservers = new HashSet<>();

    /**
     * path of partial ID file
     */
    private Path partialIDPath;
    /**
     * path of partial data
     */
    private Path partialPath;
    private double[] boundaries;
    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private int nPanelsPerRow;
    private String mapRegion;
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
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##PartialTypes to be used, listed using spaces (MU)");
            pw.println("#partialTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces, must be set");
            pw.println("#tendObservers ");
            pw.println("##(double[]) The display values of each layer boundary, listed from the inside using spaces (0 50 100 150 200 250 300 350 400)");
            pw.println("#boundaries ");
            pw.println("##(int[]) Indices of layers to display, listed from the inside using spaces, when specific layers are to be displayed");
            pw.println("##  Layers are numbered 0, 1, 2, ... from the inside.");
            pw.println("#displayLayers ");
            pw.println("##(int) Number of panels to display in each row (4)");
            pw.println("#nPanelsPerRow ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of scale (3)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing (false)");
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
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "MU"))
                .map(PartialType::valueOf).collect(Collectors.toSet());
        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());

        boundaries = property.parseDoubleArray("boundaries", "0 50 100 150 200 250 300 350 400");
        if (property.containsKey("displayLayers")) displayLayers = property.parseIntArray("displayLayers", null);
        nPanelsPerRow = property.parseInt("nPanelsPerRow", "4");
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
        mosaic = property.parseBoolean("mosaic", "false");
    }

    @Override
    public void run() throws IOException {

        PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);
        Set<FullPosition> positions = Arrays.stream(partials).map(partial -> partial.getVoxelPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);
        double positionInterval = PerturbationMapShellscript.findPositionInterval(positions);

        Path outPath = DatasetAid.createOutputFolder(workPath, "kernel", folderTag, GadgetAid.getTemporaryString());

        for (PartialID partial : partials) {
            SACComponent component = partial.getSacComponent();
            PartialType partialType = partial.getPartialType();
            if (!components.contains(component)) continue;
            if (!partialTypes.contains(partialType)) continue;
            if (!tendEvents.contains(partial.getGlobalCMTID())) continue;
            if (!tendObservers.contains(partial.getObserver().toString())) continue;

            Path eventPath = outPath.resolve(partial.getGlobalCMTID().toString());
            Path observerPath = eventPath.resolve(partial.getObserver().toString());
            Files.createDirectories(observerPath);

            double[] data = partial.getData();
            double t0 = partial.getStartTime();

            String phaselist = "";

            for (Phase phase : partial.getPhases()) {
                phaselist = phaselist + phase;
            }

            double cumulativeSensitivity = 0.;
            for (int i = 0; i < data.length; i++) {
                cumulativeSensitivity += data[i] * data[i];
            }

            String fileNameRoot = "kernel_" + phaselist + "_" + component+ "_" + partialType + String.format("_t0%d", (int) t0);
            Path filePath = observerPath.resolve(fileNameRoot + ".lst");
            if (!Files.exists(filePath))
                Files.createFile(filePath);

            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath, StandardOpenOption.APPEND))) {
                pw.println(partial.getVoxelPosition() + " " + cumulativeSensitivity * 1e31);
            }

            PerturbationMapShellscript script
                    = new PerturbationMapShellscript(VariableType.Vs, radii, boundaries, mapRegion, positionInterval, scale, fileNameRoot, nPanelsPerRow); //TODO parameter type not correct
            script.setMosaic(mosaic);
            if (displayLayers != null) script.setDisplayLayers(displayLayers);
            script.write(observerPath);
        }
    }
}
