package io.github.kensuke1984.kibrary.inv_new;

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

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inv_new.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.inversion.InverseMethodEnum;
import io.github.kensuke1984.kibrary.inversion.InverseProblem;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Operation for operating inversion.
 *
 * @author otsuru
 * @since 2022/4/28 recreated inversion.LetMeInvert
 */
public class LetMeInvert extends Operation {

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
     * Path of the output folder
     */
    private Path outPath;

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
    /**
     * Solvers for equation
     */
    private Set<InverseMethodEnum> inverseMethods;

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
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
        }
        System.err.println(outPath + " is created.");
    }

    public LetMeInvert(Property property) throws IOException {
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

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
    }

    @Override
    public void run() throws IOException {

        // read input
        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "inversion", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        UnknownParameterFile.write(parameterList, outPath.resolve("unknowns.lst"));

        // assemble matrices
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, parameterList, weightingType);
        RealMatrix ata = assembler.getAta();
        RealVector atd = assembler.getAtd();

        // solve inversion and output
        for (InverseMethodEnum method : inverseMethods) {
            InverseProblem inverseProblem = method.formProblem(ata, atd);
            inverseProblem.compute();
            inverseProblem.outputAnswers(parameterList, outPath.resolve(method.simple()));
        }

    }

}
