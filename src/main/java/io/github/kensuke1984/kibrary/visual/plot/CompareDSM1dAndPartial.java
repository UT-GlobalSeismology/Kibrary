package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.setup.AMatrixBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
    /**
     * Path of the perturbed basic folder (i.e. synthetic is calcurated by using DSMsyn1d)
     */
    private Path perturbedBasicPath;
    /**
     * Path of the original basic folder (i.e. synthetic is calcurated by using DSMsyn)
     */
    private Path originalBasicPath;
    /**
     * List of the path of the partial files
     */
    private List<Path> partialPaths = new ArrayList<>();;
    /**
     * List of the path of the unknown parameter files
     */
    private List<Path> unknownParameterPaths = new ArrayList<>();;
    /**
     * List of values to be plotted
     */
    private List<String> xValues = new ArrayList<>();;
    private List<String> yValues = new ArrayList<>();;

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
             pw.println("##Path of a perturbed basic waveform folder (.)");
             pw.println("#perturbedBasicPath ");
             pw.println("##Path of a original (i.e. not perturbed) basic waveform folder (.)");
             pw.println("#originalBasicPath ");
             pw.println("##########From here on, list up sets of paths of partial files, unknown parameter files, and x values for plot.");
             pw.println("##########Must be set partialPath and unknownParameterPath.");
             pw.println("##########Default values of xValue is 2^(i-1).");
             pw.println("########## Up to " + MAX_PAIR + " pairs can be managed. Any set may be left blank.");
             for (int i = 1; i <= MAX_PAIR; i++) {
                 pw.println("##" + MathAid.ordinalNumber(i) + " set");
                 pw.println("#partialPath" + i + " partial" + i);
                 pw.println("#unknownParameterPath" + i + " unknown" + i + ".lst");
                 pw.println("#xValue" + i + " ");
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
        // Read basic files
        List<BasicID> perturbedIDs = BasicIDFile.read(perturbedBasicPath, true);
        List<BasicID> originalIDs = BasicIDFile.read(originalBasicPath, true);
        DVectorBuilder perturbedVectorBuilder = new DVectorBuilder(perturbedIDs);
        DVectorBuilder originalVectorBuilder = new DVectorBuilder(originalIDs);
        // Assemble vector of residual waveforms
        System.err.println("Assembling d vectors");
        RealVector v = perturbedVectorBuilder.fullSynVec().subtract(originalVectorBuilder.fullSynVec());

        for (int i = 0 ; i < partialPaths.size() ; i++) {
            // Read partial files & unknown parameter files
            List<PartialID> partialIDs = PartialIDFile.read(partialPaths.get(i), true);
            List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPaths.get(i));
            // Assemble A
            System.err.println("Assembling A matrix");
            AMatrixBuilder aMatrixBuilder = new AMatrixBuilder(partialIDs, parameterList, perturbedVectorBuilder);
            ParallelizedMatrix a = aMatrixBuilder.build();
            // Sum up each column of A
            RealVector sumPartial = new ArrayRealVector(v.getDimension());
            for (int j = 0 ; j < a.getColumnDimension() ; j++)
                sumPartial = sumPartial.add(a.getColumnVector(j));
            // Compute relative error
            sumPartial = sumPartial.subtract(v);
            double y = sumPartial.getNorm() / v.getNorm();
            yValues.add(String.valueOf(y));
        }
        if (xValues.size() != yValues.size())
                throw new RuntimeException("The numbers of xValues ( " + xValues.size() + " ) and relative errors ( " +
                        yValues.size() + " ) are different");
        makeOutput();
    }

    private void makeOutput(OpenOption... options) throws IOException {
        String dateStr = GadgetAid.getTemporaryString();
        Path outPath = workPath.resolve(DatasetAid.generateOutputFileName("relativeError", fileTag, dateStr, ".lst"));
        Path plotPath = workPath.resolve(DatasetAid.generateOutputFileName("plot", fileTag, dateStr, ".plt"));

        // Make output file
        System.err.println("List up relative errors in " + outPath);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            for (int i = 0 ; i < xValues.size() ; i++)
                pw.println(xValues.get(i) + " " + yValues.get(i));
        }

        // Make plot file
        System.err.println("Making plot file");
        try (PrintWriter pw2 = new PrintWriter(Files.newBufferedWriter(plotPath, options))) {
            pw2.println("set term pngcairo enhanced font 'Helvetica,14'");
            pw2.println("set xlabel 'd{/Symbol q}'");
            pw2.println("set ylabel '#Error(%)'");
            pw2.println("set logscale");
            pw2.println("set xtics nomirror");
            pw2.println("set ytics nomirror");
            pw2.println("set sample 11");
            pw2.println("set output 'relativeError_" + dateStr + ".png'");
            pw2.println("p " + outPath + " u 1:2 w l");
        }
        System.err.println("After finish working, please run " + plotPath);
    }

}
