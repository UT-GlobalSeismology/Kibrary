package io.github.kensuke1984.kibrary.visual;

import java.io.FileWriter;
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

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

public class CatalogueErrorCalculator extends Operation {

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
     * Observers to work for. If this is empty, work for all events in workPath.
     */
    private String tendObservers;

    /**
     * Voxel to work for. Each elements are 0: latitude, 1: longitude, 2: r.
     */
    private double[] voxelPosition = new double[3];
    /**
     * Interval of voxel. Each elements are 0: dLatitude, 1: dLongitude, 2: dR.
     */
    private double[] voxelInterval = new double[3];

    /**
     * path of partial ID file without epicentral distance catalogue
     */
    private Path exactPartialIDPath;
    /**
     * path of partial data without epicentral distance catalogue
     */
    private Path exactPartialPath;

    /**
     * Theta range for the BP catalog
     */
    private String dtheta;
    /**
     * path of catalogue partial ID file
     */
    private Path catPartialIDPath;
    /**
     * path of catalogue partial data
     */
    private Path catPartialPath;


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
            pw.println("#Voxel to work for, in the form LAT LONG R, listed using spaces, must be set.");
            pw.println("#tendVoxelPosition ");
            pw.println("#Interval of voxel, in the form dLAT dLONG dR, listed using spaces, must be set");
            pw.println("#voxelInterval ");
            pw.println("##Path of a exact (without ericentral distance catalogue) partial ID file, must be set");
            pw.println("#exactPartialIDPath partialID.dat");
            pw.println("##Path of a exact partial waveform file, must be set");
            pw.println("#exactPartialPath partial.dat");
            pw.println("##Theta range for the BP catalog (0.01)");
            pw.println("#dtheta ");
            pw.println("##Path of a catalogue partial ID file, must be set");
            pw.println("#catPartialIDPath partialID.dat");
            pw.println("##Path of a catalogue partial waveform file, must be set");
            pw.println("#catPartialPath partial.dat");
        }
        System.err.println(outPath + " is created.");
    }

    public CatalogueErrorCalculator(Property property) {
        this.property = property;
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialTypes = Arrays.stream(property.parseStringArray("partialTypes", "MU"))
                .map(PartialType::valueOf).collect(Collectors.toSet());

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        tendObservers = property.parseString("tendObservers", null);

        voxelPosition = property.parseDoubleArray("tendVoxelPosition", null);
        voxelInterval = property.parseDoubleArray("voxelInterval", null);

        exactPartialIDPath = property.parsePath("exactPartialIDPath", null, true, workPath);
        exactPartialPath = property.parsePath("exactPartialPath", null, true, workPath);

        dtheta = property.parseString("dtheta", "0.01");
        catPartialIDPath = property.parsePath("catPartialIDPath", null, true, workPath);
        catPartialPath = property.parsePath("catPartialPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {
        PartialID[] exactPartials = PartialIDFile.read(exactPartialIDPath, exactPartialPath);
        PartialID[] catPartials = PartialIDFile.read(catPartialIDPath, catPartialPath);

        Path outPath = DatasetAid.createOutputFolder(workPath, "relativeError", tag, GadgetAid.getTemporaryString());

        for (PartialID exactPartial : exactPartials) {
            SACComponent component = exactPartial.getSacComponent();
            PartialType partialType = exactPartial.getPartialType();
            if (!components.contains(component)) continue;
            if (!partialTypes.contains(partialType)) continue;
            if (!tendEvents.contains(exactPartial.getGlobalCMTID())) continue;
            if (!tendObservers.contains(exactPartial.getObserver().toString())) continue;
            if (Math.abs(voxelPosition[0] - exactPartial.getVoxelPosition().getLatitude()) > 0.5 * voxelInterval[0]) continue;
            if (Math.abs(voxelPosition[1] - exactPartial.getVoxelPosition().getLongitude()) > 0.5 * voxelInterval[1]) continue;
            if (Math.abs(voxelPosition[2] - exactPartial.getVoxelPosition().getR()) > 0.5 * voxelInterval[2]) continue;

            Path eventPath = outPath.resolve(exactPartial.getGlobalCMTID().toString());
            Path observerPath = eventPath.resolve(exactPartial.getObserver().toString());
            Files.createDirectories(observerPath);

            Path filePath = observerPath.resolve("relativeError_" + component+ "_" + partialType + ".lst");
            if (!Files.exists(filePath))
                Files.createFile(filePath);

            PrintWriter pw = new PrintWriter(new FileWriter(filePath.toString(), true));

            for (PartialID catPartial : catPartials) {
                if (!components.contains(catPartial.getSacComponent())) continue;
                if (!partialTypes.contains(catPartial.getPartialType())) continue;
                if (!tendEvents.contains(catPartial.getGlobalCMTID())) continue;
                if (!tendObservers.contains(catPartial.getObserver().toString())) continue;

                if (exactPartial.getVoxelPosition().equals(catPartial.getVoxelPosition())) {

                    double[] exactData = exactPartial.getData();
                    double[] catData = catPartial.getData();
                    if (exactData.length != catData.length) {
                        System.err.println("data lengths are differnent between two partials");
                        continue;
                    }

                    double diffDataAbs = 0.;
                    double exactDataAbs = 0.;

                    for (int i = 0; i < exactData.length; i++) {
                        double diffData = exactData[i] - catData[i];

                        diffDataAbs += diffData * diffData;
                        exactDataAbs += exactData[i] * exactData[i];
                    }
                    diffDataAbs = Math.sqrt(diffDataAbs);
                    exactDataAbs = Math.sqrt(exactDataAbs);
                    double relativeError = diffDataAbs / exactDataAbs * 100.;

                    pw.println(dtheta + " " + relativeError);
                }
            }
            pw.close();
        }
    }
}
