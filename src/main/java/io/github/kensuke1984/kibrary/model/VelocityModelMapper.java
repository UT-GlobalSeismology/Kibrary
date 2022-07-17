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
import io.github.kensuke1984.kibrary.inv_old.InverseMethodEnum;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile;
import io.github.kensuke1984.kibrary.util.earth.VariableType;
import io.github.kensuke1984.kibrary.visual.MapperShellscript;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class VelocityModelMapper extends Operation {

    /**
     * The interval of deciding map size
     */
    private static final int INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map
     */
    private static final int MAP_RIM = 5;

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
     * structure file instead of PREM
     */
    private Path structurePath;
    private String structureName;
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
            pw.println("##Path of an initial structure file used. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of an initial structure model used (PREM)");
            pw.println("#structureName ");
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

    public VelocityModelMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        resultPath = property.parsePath("resultPath", ".", true, workPath);
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }

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

        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = PolynomialStructureFile.read(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }

        Path unknownsPath = resultPath.resolve("unknowns.lst");
        List<UnknownParameter> unknownsList = UnknownParameterFile.read(unknownsPath);
        double[] radii = unknownsList.stream().mapToDouble(unknown -> unknown.getPosition().getR()).distinct().sorted().toArray();

        Path outPath = DatasetAid.createOutputFolder(workPath, "modelMap", tag, GadgetAid.getTemporaryString());

        //~write list files
        for (InverseMethodEnum inverse : inverseMethods) {
            Path methodPath = resultPath.resolve(inverse.simple());
            if (!Files.exists(methodPath)) {
                System.err.println("Results for " + inverse.simple() + " do not exist, skipping.");
                continue;
            }

            for (int k = 1; k <= maxNum; k++){
                Path answerPath = methodPath.resolve(inverse.simple() + k + ".lst");
                List<KnownParameter> answers = KnownParameterFile.read(answerPath);
                PerturbationModel model = new PerturbationModel(answers, structure);

                Path outBasisPath = outPath.resolve(inverse.simple() + k);
                Files.createDirectories(outBasisPath);

                for (VariableType variable : variableTypes) {
                    Path outputPercentPath = outBasisPath.resolve(variable.toString().toLowerCase() +  "Percent.lst");
                    PerturbationModelFile.writePercentForType(variable, model, outputPercentPath);
                }
            }
        }

        //~write shellscripts for mapping
        for (VariableType variable : variableTypes) {
            String paramName = variable.toString().toLowerCase();
            writeParentShellscript(paramName, outPath.resolve(paramName + "PercentAllMap.sh"));
            MapperShellscript script = new MapperShellscript(variable, radii, decideMapRegion(unknownsList), scale, paramName + "Percent");
            script.write(outPath);
        }
    }

    private String decideMapRegion(List<UnknownParameter> unknowns) throws IOException {
        if (mapRegion != null) {
            return mapRegion;
        } else {
            double latMin, latMax, lonMin, lonMax;
            // set one position as an initial value
            HorizontalPosition pos0 = unknowns.get(0).getPosition();
            latMin = latMax = pos0.getLatitude();
            lonMin = lonMax = pos0.getLongitude();
            // search all unknowns
            for (UnknownParameter unknown : unknowns) {
                HorizontalPosition pos = unknown.getPosition();
                if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
                if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
                if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
                if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
            }
            // expand the region a bit more
            latMin = Math.floor(latMin / INTERVAL) * INTERVAL - MAP_RIM;
            latMax = Math.ceil(latMax / INTERVAL) * INTERVAL + MAP_RIM;
            lonMin = Math.floor(lonMin / INTERVAL) * INTERVAL - MAP_RIM;
            lonMax = Math.ceil(lonMax / INTERVAL) * INTERVAL + MAP_RIM;
            // return as String
            return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
        }
    }

    private void writeParentShellscript(String paramName, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("for i in `seq 1 " + maxNum + "`");
            pw.println("do");
            pw.println("    cd CG$i");
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
