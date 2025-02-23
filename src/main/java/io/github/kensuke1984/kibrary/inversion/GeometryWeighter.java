package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Weight data based on (bounce point, distance, azimuth)-pair bins.
 *
 * @author otsuru
 * @since 2025/2/23
 */
public class GeometryWeighter extends Operation {

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
     * Path of the input data entry list file.
     */
    private Path dataEntryPath;


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
            pw.println("##Sac components to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry list file, must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
        }
        System.err.println(outPath + " is created.");
    }

    public GeometryWeighter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

    }

}
