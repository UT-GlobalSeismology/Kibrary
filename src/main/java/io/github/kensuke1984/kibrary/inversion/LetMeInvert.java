package io.github.kensuke1984.kibrary.inversion;

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
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.inversion.solve.InversionMethod;
import io.github.kensuke1984.kibrary.math.MatrixFile;
import io.github.kensuke1984.kibrary.math.VectorFile;
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
 * This solves A<sup>T</sup>Am = A<sup>T</sup>d.
 * <p>
 * Weighting will be applied, thus the inversion equation will take the form<br>
 * (WA')<sup>T</sup>(WA')m = (WA')<sup>T</sup>Wd'<br>
 * where A = WA' is the weighted partial waveform matrix and d = Wd' is the weighted residual vector.
 *
 * @author otsuru
 * @since 2022/4/28 recreated former inversion.LetMeInvert
 */
public class LetMeInvert extends Operation {

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
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * basic waveform folder
     */
    private Path basicPath;
    /**
     * partial waveform folder
     */
    private Path partialPath;
    /**
     * unknown parameter file
     */
    private Path unknownParameterPath;

    private Path weightingPropertiesPath;
    /**
     * Solvers for equation
     */
    private Set<InverseMethodEnum> inverseMethods;
    /**
    * α for AIC 独立データ数:n/α
    */
    private double[] alpha;
    /**
     * Maximum number of basis vectors to evaluate variance and AIC
     */
    private int evaluateNum;
    /**
     * Fill 0 to empty partial waveforms or not.
     */
    private boolean fillEmptyPartial;

    private double lambda_LS;
    private Path tMatrixPath_LS;
    private Path etaVectorPath_LS;
    private Path m0VectorPath_CG;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of a basic waveform folder, must be set.");
            pw.println("#basicPath actual");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##Path of an unknown parameter list file, must be set.");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Path of a weighting properties file, must be set.");
            pw.println("#weightingPropertiesPath weighting.properties");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LS,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(double[]) The empirical redundancy parameter alpha to compute AIC for, listed using spaces. (1 100 1000)");
            pw.println("#alpha ");
            pw.println("##(int) Maximum number of basis vectors to evaluate variance and AIC. (100)");
            pw.println("#evaluateNum ");
            pw.println("##(boolean) Fill 0 to empty partial waveforms. (false)");
            pw.println("#fillEmptyPartial ");
            pw.println("##########Settings for Least Squares method");
            pw.println("##(double) Reguralization parameter. (0)");
            pw.println("#lambda_LS ");
            pw.println("##(Path) Path of matrix for complex regularization patterns, when needed.");
            pw.println("#tMatrixPath_LS ");
            pw.println("##(Path) Path of vector that Tm should approach, when needed.");
            pw.println("#etaVectorPath_LS ");
            pw.println("##########Settings for Conjugate Gradient method.");
            pw.println("##(Path) Path of initial vector m_0, when needed.");
            pw.println("#m0VectorPath_CG ");
        }
        System.err.println(outPath + " is created.");
    }

    public LetMeInvert(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);

        weightingPropertiesPath = property.parsePath("weightingPropertiesPath", null, true, workPath);

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        alpha = property.parseDoubleArray("alpha", "1 100 1000");
        evaluateNum = property.parseInt("evaluateNum", "100");

        fillEmptyPartial = property.parseBoolean("fillEmptyPartial", "false");

        lambda_LS = property.parseDouble("lambda_LS", "0");
        if (property.containsKey("tMatrixPath_LS"))
            tMatrixPath_LS = property.parsePath("tMatrixPath_LS", null, true, workPath);
        if (property.containsKey("etaVectorPath_LS"))
            etaVectorPath_LS = property.parsePath("etaVectorPath_LS", null, true, workPath);
        if (property.containsKey("m0VectorPath_CG"))
            m0VectorPath_CG = property.parsePath("m0VectorPath_CG", null, true, workPath);
    }

    @Override
    public void run() throws IOException {

        // read input
        WeightingHandler weightingHandler = new WeightingHandler(weightingPropertiesPath);
        List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownParameterPath);
        RealMatrix tMatrix_LS = (tMatrixPath_LS != null) ? MatrixFile.read(tMatrixPath_LS) : null;
        RealVector etaVector_LS = (etaVectorPath_LS != null) ? VectorFile.read(etaVectorPath_LS) : null;
        RealVector m0Vector_CG = (m0VectorPath_CG != null) ? VectorFile.read(m0VectorPath_CG) : null;
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // assemble matrices
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, unknowns, weightingHandler, fillEmptyPartial);
        RealMatrix ata = assembler.getAta();
        RealVector atd = assembler.getAtd();
        int dLength = assembler.getD().getDimension();
        double dNorm = assembler.getD().getNorm();
        double obsNorm = assembler.getObs().getNorm();
        System.err.println("Normalized variance of input waveforms is " + assembler.getNormalizedVariance());

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "inversion", folderTag, appendFolderDate, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output matrices
        MatrixFile.write(ata, outPath.resolve("ata.lst"));
        VectorFile.write(atd, outPath.resolve("atd.lst"));
        MatrixAssembly.writeDInfo(dLength, dNorm, obsNorm, outPath.resolve("dInfo.inf"));
        UnknownParameterFile.write(unknowns, outPath.resolve("unknowns.lst"));

        // solve inversion and evaluate
        ResultEvaluation evaluation = new ResultEvaluation(ata, atd, dLength, dNorm, obsNorm);
        for (InverseMethodEnum method : inverseMethods) {
            Path outMethodPath = outPath.resolve(method.simpleName());

            // solve problem
            InversionMethod inversion = InversionMethod.construct(method, ata, atd, lambda_LS, tMatrix_LS, etaVector_LS, m0Vector_CG);
            inversion.compute();
            inversion.outputAnswers(unknowns, outMethodPath);

            // compute normalized variance and AIC
            evaluation.evaluate(inversion.getAnswers(), evaluateNum, alpha, outMethodPath);
        }
    }

}
