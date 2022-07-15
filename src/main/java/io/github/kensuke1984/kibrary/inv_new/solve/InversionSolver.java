package io.github.kensuke1984.kibrary.inv_new.solve;

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
import io.github.kensuke1984.kibrary.inv_new.setup.AtAFile;
import io.github.kensuke1984.kibrary.inv_new.setup.AtdFile;
import io.github.kensuke1984.kibrary.inv_old.InverseMethodEnum;
import io.github.kensuke1984.kibrary.inv_old.InverseProblem;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
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
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * path of ata file
     */
    private Path ataPath;
    /**
     * path of atd data
     */
    private Path atdPath;
    /**
     * Path of unknown parameter file
     */
    private Path unknownParameterPath;
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
            pw.println("##Path of ata file (ata.lst)");
            pw.println("#ataPath ");
            pw.println("##Path of atd file (atd.lst)");
            pw.println("#atdPath ");
            pw.println("##Path of an unknown parameter list file (unknowns.lst)");
            pw.println("#unknownParameterPath ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
        }
        System.err.println(outPath + " is created.");
    }

    public InversionSolver(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        ataPath = property.parsePath("ataPath", "ata.lst", true, workPath);
        atdPath = property.parsePath("atdPath", "atd.lst", true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", "unknowns.lst", true, workPath);

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());

    }

    @Override
    public void run() throws IOException {

        // read input
        RealMatrix ata = AtAFile.read(ataPath);
        RealVector atd = AtdFile.read(atdPath);
        List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownParameterPath);

        // solve inversion and output
        for (InverseMethodEnum method : inverseMethods) {
            InverseProblem inverseProblem = method.formProblem(ata, atd);
            inverseProblem.compute();
            inverseProblem.outputAnswers(unknowns, workPath.resolve(method.simple()));
        }

    }
}
