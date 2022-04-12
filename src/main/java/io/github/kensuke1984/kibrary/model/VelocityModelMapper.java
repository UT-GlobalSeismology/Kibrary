package io.github.kensuke1984.kibrary.model;

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

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.InverseMethodEnum;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.visual.MapperShellscript;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class VelocityModelMapper extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;

    /**
     * The root folder containing results of inversion
     */
    private Path resultPath;
    /**
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;
    private int maxNum;
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
            pw.println("##Path of a root folder containing results of inversion (.)");
            pw.println("#resultPath ");
            pw.println("##Path of an initial structure file used. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of an initial structure model used (PREM)");
            pw.println("#structureName ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(int) Maximum number of basis vector to map (20)");
            pw.println("#maxNum ");
        }
        System.err.println(outPath + " is created.");
    }

    public VelocityModelMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        resultPath = property.parsePath("resultPath", ".", true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        maxNum = property.parseInt("maxNum", "20");

    }

    @Override
    public void run() throws IOException {

        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = new PolynomialStructure(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        Path unknownsPath = resultPath.resolve("unknownParameterOrder.inf");
        List<UnknownParameter> unknownsList = UnknownParameterFile.read(unknownsPath);

        Path outPath = workPath.resolve("map" + GadgetAid.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        //~write list files
        for (InverseMethodEnum inverse : inverseMethods) {
            Path methodPath = resultPath.resolve(inverse.simple());
            if (!Files.exists(methodPath)) {
                System.err.println("Results for " + inverse.simple() + " do not exist, skipping.");
                continue;
            }

            for (int k = 1; k <= maxNum; k++){
                Path answerPath = methodPath.resolve(inverse.simple() + k + ".txt");
                double[] answers = readAnswer(answerPath);
                PerturbationModel model = new PerturbationModel(unknownsList, answers, structure);

                Path outBasisPath = outPath.resolve(inverse.simple() + k);
                Files.createDirectories(outBasisPath);
                Path outputPercentPath = outBasisPath.resolve("vsPercent.lst");

                PerturbationModelFile.writePercentForType(ParameterType.Vs, model, outputPercentPath);
            }
        }

        //~write shellscripts for mapping
        writeParentShellscript(outPath.resolve("plotmap.sh"));
        double[] radii = unknownsList.stream().mapToDouble(unknown -> unknown.getPosition().getR()).distinct().sorted().toArray();
        MapperShellscript script = new MapperShellscript(radii, "vsPercent");
        script.write(outPath);
    }

    private static double[] readAnswer(Path answerPath) throws IOException {
        return Files.readAllLines(answerPath).stream().mapToDouble(Double::parseDouble).toArray();
    }

    private void writeParentShellscript(Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("for i in `seq 1 " + maxNum + "`");
            pw.println("do");
            pw.println("    cd CG$i");
            pw.println("    ln -s ../gridmaker.sh .");
            pw.println("    ln -s ../makemap.sh .");
            pw.println("    ln -s ../cp_master.cpt .");
            pw.println("    sh gridmaker.sh");
            pw.println("    wait");
            pw.println("    sh makemap.sh");
            pw.println("    wait");
            pw.println("    rm -rf *.grd gmt.* cp.cpt");
            pw.println("    unlink gridmaker.sh");
            pw.println("    unlink makemap.sh");
            pw.println("    unlink cp_master.cpt");
            pw.println("    cd ..");
            pw.println("done");
        }
    }

}
