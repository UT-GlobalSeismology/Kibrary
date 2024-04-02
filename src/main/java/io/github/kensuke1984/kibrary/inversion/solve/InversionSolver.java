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
     * Path of ata file.
     */
    private Path ataPath;
    /**
     * Path of atd data.
     */
    private Path atdPath;
    /**
     * Path of d vector info file.
     */
    private Path dInfoPath;
    /**
     * Path of unknown parameter file.
     */
    private Path unknownParameterPath;
    /**
     * Solvers for equation.
     */
    private Set<InverseMethodEnum> inverseMethods;
    /**
     * &alpha; for AIC. The number of independent data is n/&alpha;.
     */
    private double[] alpha;
    private int evaluateNum;

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
            pw.println("##Path of ata file. (ata.lst)");
            pw.println("#ataPath ");
            pw.println("##Path of atd file. (atd.lst)");
            pw.println("#atdPath ");
            pw.println("##Path of dInfo file. (dInfo.inf)");
            pw.println("#dInfoPath ");
            pw.println("##Path of an unknown parameter list file. (unknowns.lst)");
            pw.println("#unknownParameterPath ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LS,NNLS,BCGS,FCG,FCGD,NCG,CCG}. (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(double[]) The empirical redundancy parameter alpha to compute AIC for, listed using spaces. (1 100 1000)");
            pw.println("#alpha ");
            pw.println("##(int) Maximum number of basis vectors to evaluate variance and AIC. (100)");
            pw.println("#evaluateNum ");
            pw.println("##########Settings for Least Squares method.");
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

    public InversionSolver(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        ataPath = property.parsePath("ataPath", "ata.lst", true, workPath);
        atdPath = property.parsePath("atdPath", "atd.lst", true, workPath);
        dInfoPath = property.parsePath("dInfoPath", "dInfo.inf", true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", "unknowns.lst", true, workPath);

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        alpha = property.parseDoubleArray("alpha", "1 100 1000");
        evaluateNum = property.parseInt("evaluateNum", "100");

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
        RealMatrix tMatrix_LS = (tMatrixPath_LS != null) ? MatrixFile.read(tMatrixPath_LS) : null;
        RealVector etaVector_LS = (etaVectorPath_LS != null) ? VectorFile.read(etaVectorPath_LS) : null;
        RealVector m0Vector_CG = (m0VectorPath_CG != null) ? VectorFile.read(m0VectorPath_CG) : null;
        double[] dInfo = MatrixAssembly.readDInfo(dInfoPath);
        List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownParameterPath);
        RealMatrix ata = MatrixFile.read(ataPath);
        RealVector atd = VectorFile.read(atdPath);

        // solve inversion and evaluate
        ResultEvaluation evaluation = new ResultEvaluation(ata, atd, dInfo[0], dInfo[1], dInfo[2]);
        for (InverseMethodEnum method : inverseMethods) {
            Path outMethodPath = workPath.resolve(method.simpleName());

            // solve problem
            InversionMethod inversion = InversionMethod.construct(method, ata, atd, lambda_LS, tMatrix_LS, etaVector_LS, m0Vector_CG);
            inversion.compute();
            inversion.outputAnswers(unknowns, outMethodPath);

            // compute normalized variance and AIC
            evaluation.evaluate(inversion.getAnswers(), evaluateNum, alpha, outMethodPath);
        }
    }

}
