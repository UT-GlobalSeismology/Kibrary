package io.github.kensuke1984.kibrary.waveform.addons;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.setup.AMatrixBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Calcurate the variance between residual waveforms and partial derivative waveforms.
 * To run this class, The paths to perturbed waveform, original waveform, partial derivative waveform,
 * and unknown file are needed.
 *
 * @author rei
 *
 */
public class CompareDSM1dAndPartial extends Operation {

    private static final int MAX_PAIR = 10;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String fileTag;
    private Path perturbedBasicPath;
    private Path originalBasicPath;
    private List<Path> partialPaths;
    private List<Path> unknownParameterPaths;
    private List<String> xValues;
    private List<String> yValues;

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
             pw.println("##(String) A tag to include in output file name. If no tag is needed, leave this blank.");
             pw.println("#fileTag ");
             for (int i = 1; i <= MAX_PAIR; i++) {
                 pw.println("##" + MathAid.ordinalNumber(i) + " folder");
                 pw.println("#basicPath" + i + " actual");
             }
        }
        System.err.println(outPath + " is created.");
    }

    public CompareDSM1dAndPartial(Property property) {
        this.property = property;
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        perturbedBasicPath = property.parsePath("perturbedBasicPath", null, true, workPath);
        originalBasicPath = property.parsePath("originalBasicPath", null, true, workPath);

        for (int i = 1; i <= MAX_PAIR; i++) {
            String partialKey = "partialPath" + i;
            String unknownParameterKey = "unknownParameterPath" + i;
            String xValueKey = "xValue" + i;
            int defaultXValue = (int) Math.pow(2, (i-1));
            if (!property.containsKey(partialKey) && !property.containsKey(unknownParameterKey)) {
                continue;
            } else if (!property.containsKey(partialKey) || !property.containsKey(unknownParameterKey)) {
                throw new IllegalArgumentException("Partial path and unknown parameter path must be set in sets.");
            }
            partialPaths.add(property.parsePath(partialKey, null, true, workPath));
            unknownParameterPaths.add(property.parsePath(unknownParameterKey, null, true, workPath));
            xValues.add(property.parseString(xValueKey, String.valueOf(defaultXValue)));
        }
    }

    @Override
    public void run() throws IOException {

        // Read files
        List<BasicID> perturbedIDs = BasicIDFile.read(perturbedBasicPath, true);
        List<BasicID> originalIDs = BasicIDFile.read(originalBasicPath, true);
        DVectorBuilder perturbedVectorBuilder = new DVectorBuilder(perturbedIDs);
        DVectorBuilder originalVectorBuilder = new DVectorBuilder(originalIDs);

        for (int i = 0 ; i < partialPaths.size() ; i++) {
            // Read files
            List<PartialID> partialIDs = PartialIDFile.read(partialPaths.get(i), true);
            List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPaths.get(i));
            AMatrixBuilder aMatrixBuilder = new AMatrixBuilder(partialIDs, parameterList, perturbedVectorBuilder);
        }
    }

}
