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
import io.github.kensuke1984.kibrary.inversion.setup.AtAFile;
import io.github.kensuke1984.kibrary.inversion.setup.AtdFile;
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
     * Path of the work folder
     */
    private Path workPath;

    /**
     * path of ata file
     */
    private Path ataPath;
    /**
     * path of atd data
     */
    private Path atdPath;
    /**
     * path of d vector info file
     */
    private Path dInfoPath;
    /**
     * Path of unknown parameter file
     */
    private Path unknownParameterPath;
    /**
     * Solvers for equation
     */
    private Set<InverseMethodEnum> inverseMethods;
    /**
    * α for AIC 独立データ数:n/α
    */
    private double[] alpha;
    private int evaluateNum;


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
            pw.println("##Path of ata file (ata.lst)");
            pw.println("#ataPath ");
            pw.println("##Path of atd file (atd.lst)");
            pw.println("#atdPath ");
            pw.println("##Path of dInfo file (dInfo.inf)");
            pw.println("#dInfoPath ");
            pw.println("##Path of an unknown parameter list file (unknowns.lst)");
            pw.println("#unknownParameterPath ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
            pw.println("##The empirical redundancy parameter alpha to compute AIC for, listed using spaces (1 100 1000)");
            pw.println("#alpha ");
            pw.println("##(int) Maximum number of basis vectors to evaluate variance and AIC (100)");
            pw.println("#evaluateNum ");
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
    }

    @Override
    public void run() throws IOException {

        // read input
        RealMatrix ata = AtAFile.read(ataPath);
        RealVector atd = AtdFile.read(atdPath);
        double[] dInfo = AtdFile.readDInfo(dInfoPath);
        List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownParameterPath);

        // solve inversion and evaluate
        ResultEvaluation evaluation = new ResultEvaluation(ata, atd, (int) dInfo[0], dInfo[1], dInfo[2]);
        for (InverseMethodEnum method : inverseMethods) {
            Path outMethodPath = workPath.resolve(method.simpleName());

            // solve problem
            InverseProblem inverseProblem = method.formProblem(ata, atd);
            inverseProblem.compute();
            inverseProblem.outputAnswers(unknowns, outMethodPath);

            // compute normalized variance and AIC
            evaluation.evaluate(inverseProblem.getANS(), evaluateNum, alpha, outMethodPath);
        }
    }

}
