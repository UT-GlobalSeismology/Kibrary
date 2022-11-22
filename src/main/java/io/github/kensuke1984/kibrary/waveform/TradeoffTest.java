package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Operation for performing trade-off test of structure in the target region and structure outside it.
 *
 * @author otsuru
 * @since 2022/11/22
 */
public class TradeoffTest extends Operation {

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
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * path of partial ID file for the target region,
     */
    private Path mainPartialIDPath;
    /**
     * path of partial data for the target region,
     */
    private Path mainPartialPath;
    /**
     * path of partial ID file created for this test
     */
    private Path testPartialIDPath;
    /**
     * path of partial data created for this test
     */
    private Path testPartialPath;
    private GlobalCMTID specificEvent;
    private String specificObserverName;

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
            pw.println("##Path of a partial ID file for the target region, must be set");
            pw.println("#mainPartialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file for the target region, must be set");
            pw.println("#mainPartialPath partial.dat");
            pw.println("##Path of a partial ID file created for this test, must be set");
            pw.println("#testPartialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file created for this test, must be set");
            pw.println("#testPartialPath partial.dat");
            pw.println("##GCMT ID of a specific event, must be set");
            pw.println("#specificEvent ");
            pw.println("##A specific observer, in the form STA_NET, must be set");
            pw.println("#specificObserverName ");
        }
        System.err.println(outPath + " is created.");
    }

    public TradeoffTest(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        mainPartialIDPath = property.parsePath("mainPartialIDPath", null, true, workPath);
        mainPartialPath = property.parsePath("mainPartialPath", null, true, workPath);
        testPartialIDPath = property.parsePath("testPartialIDPath", null, true, workPath);
        testPartialPath = property.parsePath("testPartialPath", null, true, workPath);

        specificEvent = new GlobalCMTID(property.parseString("specificEvent", null));
        specificObserverName = property.parseString("specificObserverName", null);
    }

   @Override
   public void run() throws IOException {
       PartialID[] mainPartialIDs = PartialIDFile.read(mainPartialIDPath, mainPartialPath);
       mainPartialIDs = Arrays.stream(mainPartialIDs).filter(id -> components.contains(id.getSacComponent())).toArray(PartialID[]::new);
       PartialID[] testPartialIDs = PartialIDFile.read(testPartialIDPath, testPartialPath);
       testPartialIDs = Arrays.stream(testPartialIDs).filter(id -> components.contains(id.getSacComponent())).toArray(PartialID[]::new);

       List<FullPosition> mainVoxelPositions = Arrays.stream(mainPartialIDs)
               .map(partial -> partial.getVoxelPosition()).distinct().sorted().collect(Collectors.toList());
       List<FullPosition> testVoxelPositions = Arrays.stream(testPartialIDs)
               .map(partial -> partial.getVoxelPosition()).distinct().sorted().collect(Collectors.toList());

       for (FullPosition testVoxel : testVoxelPositions) {
           for (FullPosition mainVoxel : mainVoxelPositions) {

           }
       }
   }
}
