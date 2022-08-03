package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.multigrid.MultigridDesign;
import io.github.kensuke1984.kibrary.multigrid.MultigridInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Computes correlation between partial waveforms of each unknown parameter.
 *
 * @author otsuru
 * @since 2022/8/1
 */
public class PartialWaveformEvaluator extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
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
    /**
     * path of partial ID file
     */
    private Path partialIDPath;
    /**
     * path of partial data
     */
    private Path partialPath;
    /**
     * Path of unknown parameter file
     */
    private Path unknownParameterPath;

    private WeightingType weightingType;

    private double minDiagonalAmplitude;
    private double minCorrelation;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of an unknown parameter list file, must be set");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Weighting type, from {LOWERUPPERMANTLE,RECIPROCAL,TAKEUCHIKOBAYASHI,IDENTITY,FINAL} (RECIPROCAL)");
            pw.println("#weightingType ");
            pw.println("##(double) minDiagonalAmplitude");
            pw.println("#minDiagonalAmplitude ");
            pw.println("##(double) minCorrelation (0.75)");
            pw.println("#minCorrelation ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialWaveformEvaluator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);

        weightingType = WeightingType.valueOf(property.parseString("weightingType", "RECIPROCAL"));
        minDiagonalAmplitude = property.parseDouble("minDiagonalAmplitude", "5");
        minCorrelation = property.parseDouble("minCorrelation", "0.75");
    }

    @Override
    public void run() throws IOException {

        // read input
        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);

        // assemble matrices
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, parameterList, weightingType);
        RealMatrix ata = assembler.getAta();

        // output unknown parameter with large diagonal component and correlation
        MultigridDesign multigrid = new MultigridDesign();
        for (int i = 0; i < parameterList.size(); i++) {
            for (int j = 0; j < parameterList.size(); j++) {
                if (i == j) continue;
                double coeff = ata.getEntry(i, j) * ata.getEntry(i, j) / ata.getEntry(i, i) / ata.getEntry(j, j);
                if (ata.getEntry(i, i) > minDiagonalAmplitude && coeff > minCorrelation) {
                    System.out.println(i + " " + j + " " + ata.getEntry(i, i) + " " + ata.getEntry(i, j) + " " + coeff);
                    System.out.println(" - " + parameterList.get(i));
                    System.out.println(" - " + parameterList.get(j));
                    multigrid.addFusion(parameterList.get(i), parameterList.get(j));
                }
            }
        }

        // output multigrid design file
        Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName("multigrid", tag, GadgetAid.getTemporaryString(), ".inf"));
        MultigridInformationFile.write(multigrid, outputPath);
    }

}
