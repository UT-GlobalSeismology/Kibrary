package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;

/**
 * An operation to select or resample BasicIDs.
 * This allows for extraction of certain data to be used in the inversion,
 * as well as for preparation of bootstrap or subsampling tests.
 *
 * @author otsuru
 * @since 2022/7/13
 */
public class BasicIDSelect  extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The first part of the name of output basic ID and waveform files
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * path of basic ID file
     */
    private Path basicIDPath;
    /**
     * path of waveform data
     */
    private Path basicPath;

    private boolean bootstrap;
    private double subsamplingPercent;

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) The first part of the name of output basic ID and waveform files (actual)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##(boolean) Perform a bootstrap test (false)");
            pw.println("#bootstrap ");
            pw.println("##(double) Percent of basic IDs to use in subsampling test (100)");
            pw.println("#subsamplingPercent ");
        }
        System.err.println(outPath + " is created.");
    }

    public BasicIDSelect(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "actual");
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);

        bootstrap = property.parseBoolean("bootstrap", "false");
        subsamplingPercent = property.parseDouble("subsamplingPercent", "false");

    }

    @Override
    public void run() throws IOException {

        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);


    }
}
