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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
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
    private String tag;
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
    protected Path partialIDPath;
    /**
     * path of partial data
     */
    protected Path partialPath;
    private String mapRegion;
    private double scale;


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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##PartialTypes to be used, listed using spaces (MU)");
            pw.println("#partialTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces. To use all observers, leave this unset.");
            pw.println("#tendObservers ");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of scale (3)");
            pw.println("#scale ");
        }
        System.err.println(outPath + " is created.");
    }

    public SensitivityKernelMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "MU"))
                .map(PartialType::valueOf).collect(Collectors.toSet());
        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }
        if (property.containsKey("tendObservers")) {
            tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());
        }

        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);

        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");

    }

    @Override
    public void run() throws IOException {

        PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);
        double[] radii = Arrays.stream(partials).mapToDouble(partial -> partial.getPerturbationLocation().getR()).distinct().sorted().toArray();


        Path outPath = DatasetAid.createOutputFolder(workPath, "kernel", tag, GadgetAid.getTemporaryString());

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

            String phaselist = null;

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
                pw.println(partial.getPerturbationLocation() + " " + cumulativeSensitivity * 1e31);
            }

            MapperShellscript script = new MapperShellscript(ParameterType.Vs, radii, mapRegion, scale, fileNameRoot); //TODO parameter type not correct
            script.write(observerPath);

        }
    }
}
