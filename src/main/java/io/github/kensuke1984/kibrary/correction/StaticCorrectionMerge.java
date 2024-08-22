package io.github.kensuke1984.kibrary.correction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Merges {@link StaticCorrectionDataFile}s.
 *
 * @author otsuru
 * @since 2023/8/16
 */
public class StaticCorrectionMerge extends Operation {

    private static final int MAX_NUM = 10;

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
     * Paths of input static correction files.
     */
    private List<Path> staticCorrectionPaths = new ArrayList<>();


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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##########From here on, list up paths of static correction files.");
            pw.println("##########  Up to " + MAX_NUM + " files can be managed. Any index may be left blank.");
            for (int i = 1; i <= MAX_NUM; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " file.");
                pw.println("#staticCorrectionPath" + i + " staticCorrection.dat");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public StaticCorrectionMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        for (int i = 1; i <= MAX_NUM; i++) {
            String staticCorrectionKey = "staticCorrectionPath" + i;
            if (property.containsKey(staticCorrectionKey)) {
                staticCorrectionPaths.add(property.parsePath(staticCorrectionKey, null, true, workPath));
            }
        }
    }

    @Override
    public void run() throws IOException {
        int fileNum = staticCorrectionPaths.size();
        if (fileNum == 0) {
            System.err.println("!! No input files found.");
            return;
        } else if (fileNum == 1) {
            System.err.println("!! Only 1 input file found. Merging will not be done.");
            return;
        }

        // read timewindows from all input files
        Set<StaticCorrectionData> staticCorrections = new HashSet<>();
        for (Path staticCorrectionPath : staticCorrectionPaths) {
            Set<StaticCorrectionData> srcStaticCorrections = StaticCorrectionDataFile.read(staticCorrectionPath);
            staticCorrections.addAll(srcStaticCorrections);
        }

        // output merged file
        String dateStr = GadgetAid.getTemporaryString();
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "staticCorrection", fileTag, appendFileDate, dateStr, ".dat");
        StaticCorrectionDataFile.write(staticCorrections, outputPath);
    }


}
