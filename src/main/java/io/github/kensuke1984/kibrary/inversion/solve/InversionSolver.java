package io.github.kensuke1984.kibrary.inversion.solve;

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
import io.github.kensuke1984.kibrary.inversion.ResultEvaluation;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.math.MatrixFile;
import io.github.kensuke1984.kibrary.math.VectorFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation to solve inverse problem.
 *
 * @author otsuru
 * @since 2022/7/7 created based on part of inversion.LetMeInvert
 */
public class InversionSolver extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder names. When this is empty, no tag is used.
     */
    private String folderTag;

    /**
     * Solvers for equation.
     */
    private Set<InverseMethodEnum> inverseMethods;
    /**
     * &alpha; for AIC. The number of independent data is n/&alpha;.
     */
    private double[] alpha;
    private int evaluateNum;

    private double[] lambdas_LS;
    private Path tMatrixPath_LS;
    private Path etaVectorPath_LS;
    private Path m0VectorPath_CG;

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
            pw.println("##(String) A tag to include in output folder names. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LS,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(double[]) The empirical redundancy parameter alpha to compute AIC for, listed using spaces. (1 100 500 1000)");
            pw.println("#alpha ");
            pw.println("##(int) Maximum number of basis vectors to evaluate variance and AIC. (10)");
            pw.println("#evaluateNum ");
            pw.println("##########Settings for Least Squares method.");
            pw.println("##(double[]) Reguralization parameters, listed using spaces. (0)");
            pw.println("#lambdas_LS ");
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

    public InversionSolver(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        alpha = property.parseDoubleArray("alpha", "1 100 500 1000");
        evaluateNum = property.parseInt("evaluateNum", "10");

        lambdas_LS = property.parseDoubleArray("lambdas_LS", "0");
        if (property.containsKey("tMatrixPath_LS"))
            tMatrixPath_LS = property.parsePath("tMatrixPath_LS", null, true, workPath);
        if (property.containsKey("etaVectorPath_LS"))
            etaVectorPath_LS = property.parsePath("etaVectorPath_LS", null, true, workPath);
        if (property.containsKey("m0VectorPath_CG"))
            m0VectorPath_CG = property.parsePath("m0VectorPath_CG", null, true, workPath);
    }

    @Override
    public void run() throws IOException {
        String dateString = GadgetAid.getTemporaryString();

        // read input
        RealMatrix tMatrix_LS = (tMatrixPath_LS != null) ? MatrixFile.read(tMatrixPath_LS) : null;
        RealVector etaVector_LS = (etaVectorPath_LS != null) ? VectorFile.read(etaVectorPath_LS) : null;
        RealVector m0Vector_CG = (m0VectorPath_CG != null) ? VectorFile.read(m0VectorPath_CG) : null;
        double[] dInfo = MatrixAssembly.readDInfo(workPath.resolve("dInfo.inf"));
        List<UnknownParameter> unknowns = UnknownParameterFile.read(workPath.resolve("unknowns.lst"));
        RealMatrix ata = MatrixFile.read(workPath.resolve("ata.lst"));
        RealVector atd = VectorFile.read(workPath.resolve("atd.lst"));

        // solve inversion and evaluate
        ResultEvaluation evaluation = new ResultEvaluation(ata, atd, dInfo[0], dInfo[1], dInfo[2]);
        for (InverseMethodEnum method : inverseMethods) {
            Path outMethodPath = DatasetAid.createOutputFolder(workPath, method.simpleName(), folderTag, false, dateString);

            // solve problem
            InversionMethod inversion = InversionMethod.construct(method, ata, atd, lambdas_LS, tMatrix_LS, etaVector_LS, m0Vector_CG);
            inversion.compute();
            inversion.outputAnswers(unknowns, outMethodPath);

            // compute normalized variance and AIC
            switch (method) {
            case LEAST_SQUARES:
                evaluation.evaluate_LS(inversion.getAnswers(), lambdas_LS, outMethodPath);
                break;
            default:
                evaluation.evaluate(inversion.getAnswers(), evaluateNum, alpha, outMethodPath);
            }
        }
    }

}
