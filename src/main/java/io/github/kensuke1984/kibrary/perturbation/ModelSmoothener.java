package io.github.kensuke1984.kibrary.perturbation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * For now, this just averages the perturbations in the vertical direction.
 *
 * @author otsuru
 * @since 2022/11/21
 */
public class ModelSmoothener extends Operation {

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
     * Path of perturbation file.
     */
    private Path perturbationPath;
    private LinearRange radiusRange;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of perturbation file, must be set.");
            pw.println("#perturbationPath vsPercent.lst");
            pw.println("##Lower limit of radius range [km]; [0:upperRadius). (0)");
            pw.println("#lowerRadius ");
            pw.println("##Upper limit of radius range [km]; (lowerRadius:). (6371)");
            pw.println("#upperRadius ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelSmoothener(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        perturbationPath = property.parsePath("perturbationPath", null, true, workPath);

        double lowerRadius = property.parseDouble("lowerRadius", "0");
        double upperRadius = property.parseDouble("upperRadius", "6371");
        radiusRange = new LinearRange("Radius", lowerRadius, upperRadius, 0.0);
    }

    @Override
    public void run() throws IOException {

        // read input
        // This will be obtained as unmodifiable LinkedHashMap
        Map<FullPosition, Double> perturbationMap = PerturbationListFile.read(perturbationPath);

        List<HorizontalPosition> horizontalPositions = perturbationMap.keySet().stream()
                .map(pos -> pos.toHorizontalPosition()).distinct().collect(Collectors.toList());
        double averagedRadius = perturbationMap.keySet().stream().mapToDouble(pos -> pos.getR()).distinct()
                .filter(r -> radiusRange.check(r)).average().getAsDouble();

        // This is created as LinkedHashMap to preserve the order of voxels
        Map<FullPosition, Double> smoothedMap = new LinkedHashMap<>();
        for (HorizontalPosition horizontalPosition : horizontalPositions) {
            double average = perturbationMap.entrySet().stream()
                    .filter(entry -> entry.getKey().toHorizontalPosition().equals(horizontalPosition))
                    .filter(entry -> radiusRange.check(entry.getKey().getR()))
                    .mapToDouble(entry -> entry.getValue()).average().getAsDouble();
            smoothedMap.put(horizontalPosition.toFullPosition(averagedRadius), average);
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "smoothed", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        Path outputPerturbationFile = outPath.resolve(perturbationPath.getFileName());
        PerturbationListFile.write(smoothedMap, outputPerturbationFile);
    }
}
