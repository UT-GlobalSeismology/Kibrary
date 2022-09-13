package io.github.kensuke1984.kibrary.visual;

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
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.multigrid.MultigridDesign;
import io.github.kensuke1984.kibrary.multigrid.MultigridInformationFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Creates shellscripts to map a set of inversion results.
 *
 * @author otsuru
 * @since 2022/4/9
 * @version 2022/7/17 moved and renamed from model.VelocityModelMapper to visual.ModelSetMapper
 */
public class ModelSetMapper extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * The root folder containing results of inversion
     */
    private Path resultPath;
    /**
     * file of 1D structure used in inversion
     */
    private Path initialStructurePath;
    /**
     * name of 1D structure used in inversion
     */
    private String initialStructureName;
    /**
     * file of 1D structure to map perturbations against
     */
    private Path referenceStructurePath;
    /**
     * name of 1D structure to map perturbations against
     */
    private String referenceStructureName;
    /**
     * Path of a {@link MultigridInformationFile}
     */
    private Path multigridPath;
    private Set<VariableType> variableTypes;
    /**
     * Solvers for equation
     */
    private Set<InverseMethodEnum> inverseMethods;
    private int maxNum;
    private String mapRegion;
    private double scale;

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
            pw.println("##Path of a root folder containing results of inversion (.)");
            pw.println("#resultPath ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a structure file to map perturbations against. If this is unset, the following referenceStructureName will be referenced.");
            pw.println("#referenceStructurePath ");
            pw.println("##Name of a structure model to map perturbations against (PREM)");
            pw.println("#referenceStructureName ");
            pw.println("##Path of a multigrid information file, if multigrid inversion is conducted");
            pw.println("#multigridPath ");
            pw.println("##Variable types to map, listed using spaces (Vs)");
            pw.println("#variableTypes ");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
            pw.println("##(int) Maximum number of basis vectors to map (20)");
            pw.println("#maxNum ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##(double) Range of percent scale (3)");
            pw.println("#scale ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelSetMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        resultPath = property.parsePath("resultPath", ".", true, workPath);
        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        if (property.containsKey("referenceStructurePath")) {
            referenceStructurePath = property.parsePath("referenceStructurePath", null, true, workPath);
        } else {
            referenceStructureName = property.parseString("referenceStructureName", "PREM");
        }
        if (property.containsKey("multigridPath"))
            multigridPath = property.parsePath("multigridPath", null, true, workPath);

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        inverseMethods = Arrays.stream(property.parseStringArray("inverseMethods", "CG")).map(InverseMethodEnum::of)
                .collect(Collectors.toSet());
        maxNum = property.parseInt("maxNum", "20");

        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        scale = property.parseDouble("scale", "3");
    }

    @Override
    public void run() throws IOException {

        // read initial structure
        PolynomialStructure initialStructure = null;
        if (initialStructurePath != null) {
            initialStructure = PolynomialStructureFile.read(initialStructurePath);
        } else {
            initialStructure = PolynomialStructure.of(initialStructureName);
        }
        PolynomialStructure referenceStructure = null;
        if (referenceStructurePath != null) {
            referenceStructure = PolynomialStructureFile.read(referenceStructurePath);
        } else {
            referenceStructure = PolynomialStructure.of(referenceStructureName);
        }

        // read parameters
        Path unknownsPath = resultPath.resolve("unknowns.lst");
        Set<FullPosition> positions = UnknownParameterFile.read(unknownsPath).stream()
                .map(unknown -> unknown.getPosition()).collect(Collectors.toSet());
        double[] radii = positions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        // read multigrid file
        MultigridDesign multigrid = null;
        if (multigridPath != null) {
            multigrid = MultigridInformationFile.read(multigridPath);
        }

        // decide map region
        if (mapRegion == null) mapRegion = PerturbationMapShellscript.decideMapRegion(positions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMaps", tag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // write list files
        for (InverseMethodEnum method : inverseMethods) {
            Path methodPath = resultPath.resolve(method.simpleName());
            if (!Files.exists(methodPath)) {
                System.err.println("Results for " + method.simpleName() + " do not exist, skipping.");
                continue;
            }

            for (int k = 1; k <= maxNum; k++){
                Path answerPath = methodPath.resolve(method.simpleName() + k + ".lst");
                List<KnownParameter> knowns = KnownParameterFile.read(answerPath);

                if (multigridPath != null) knowns = multigrid.reverseFusion(knowns);

                PerturbationModel model = new PerturbationModel(knowns, initialStructure);
                if (!referenceStructure.equals(initialStructure)) {
                    model = model.withInitialStructureAs(referenceStructure);
                }

                Path outBasisPath = outPath.resolve(method.simpleName() + k);
                Files.createDirectories(outBasisPath);

                for (VariableType variable : variableTypes) {
                    Path outputPercentPath = outBasisPath.resolve(variable.toString().toLowerCase() + "Percent.lst");
                    PerturbationListFile.writePercentForType(variable, model, outputPercentPath);
                }
            }
        }

        // write shellscripts for mapping
        for (VariableType variable : variableTypes) {
            String variableName = variable.toString().toLowerCase();
            writeParentShellscript(variableName, outPath.resolve(variableName + "PercentAllMap.sh"));
            PerturbationMapShellscript script = new PerturbationMapShellscript(variable, radii, mapRegion, scale, variableName + "Percent");
            script.write(outPath);
            System.err.println("After this finishes, please run " + outPath + "/" + variableName + "PercentAllMap.sh");
        }
    }

    private void writeParentShellscript(String paramName, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            for (InverseMethodEnum method : inverseMethods) {
                pw.println("");
                pw.println("for i in `seq 1 " + maxNum + "`");
                pw.println("do");
                pw.println("    cd " + method.simpleName() + "$i");
                pw.println("    ln -s ../" + paramName + "PercentGrid.sh .");
                pw.println("    ln -s ../" + paramName + "PercentMap.sh .");
                pw.println("    ln -s ../cp_master.cpt .");
                pw.println("    sh " + paramName + "PercentGrid.sh");
                pw.println("    wait");
                pw.println("    sh " + paramName + "PercentMap.sh");
                pw.println("    wait");
                pw.println("    rm -rf *.grd gmt.* cp.cpt");
                pw.println("    unlink " + paramName + "PercentGrid.sh");
                pw.println("    unlink " + paramName + "PercentMap.sh");
                pw.println("    unlink cp_master.cpt");
                pw.println("    cd ..");
                pw.println("done");
            }
        }
    }

}
