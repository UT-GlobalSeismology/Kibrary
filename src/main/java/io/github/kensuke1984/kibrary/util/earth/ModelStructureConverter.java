package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationModel;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;

/**
 * Operation that converts a 1-D model to a {@link PolynomialStructure}.
 *
 * @author otsuru
 * @since 2023/7/17
 */
public class ModelStructureConverter extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * The first part of the name of output structure file
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

    /**
     * File of 1D structure used in inversion
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion
     */
    private String initialStructureName;
    /**
     * Model file with perturbation information
     */
    private Path modelPath;
    /**
     * Variable types to perturb
     */
    private Set<VariableType> variableTypes;

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
            pw.println("##(String) The first part of the name of output structure file. (PREMp)");
            pw.println("#nameRoot ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#fileTag ");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
            pw.println("##Variable types to perturb, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#variableTypes ");
        }
        System.err.println(outPath + " is created.");
    }

    public ModelStructureConverter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        nameRoot = property.parseStringSingle("nameRoot", "PREMp");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
    }

   @Override
   public void run() throws IOException {
       // set initial structure
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
       double[] radii = knowns.stream().mapToDouble(known -> known.getParameter().getPosition().getR()).toArray();
       PerturbationModel model = new PerturbationModel(knowns, initialStructure);

       // introduce perturbations in polynomial structure
       PolynomialStructure perturbedStructure = convertModel(initialStructure, radii, model);

       // output structure
       Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName(nameRoot, fileTag, GadgetAid.getTemporaryString(), ".structure"));
       PolynomialStructureFile.write(perturbedStructure, outputPath);
   }

   private PolynomialStructure convertModel(PolynomialStructure initialStructure, double[] radii, PerturbationModel model) {
       PolynomialStructure originalStructure = initialStructure.withBoundaries(radii);
       // structure information
       int nZone = originalStructure.getNZone();
       int nCoreZone = originalStructure.getNCoreZone();
       double[] rMin = originalStructure.getRmin();
       double[] rMax = originalStructure.getRmax();
       PolynomialFunction[] rho = originalStructure.getRho();
       PolynomialFunction[] vpv = originalStructure.getVpv();
       PolynomialFunction[] vph = originalStructure.getVph();
       PolynomialFunction[] vsv = originalStructure.getVsv();
       PolynomialFunction[] vsh = originalStructure.getVsh();
       PolynomialFunction[] eta = originalStructure.getEta();
       double[] qMu = originalStructure.getQMu();
       double[] qKappa = originalStructure.getQKappa();
       // planet radius
       double planetRadius = originalStructure.planetRadius();

       for (VariableType variable : variableTypes) {
           Map<FullPosition, Double> discreteMap = model.getAbsoluteForType(variable);
           for (int i = 1; i < radii.length; i++) {
               double x0 = radii[i - 1] / planetRadius;
               double x1 = radii[i] / planetRadius;
               double y0 = discreteMap.get(new FullPosition(0, 0, radii[i - 1]));
               double y1 = discreteMap.get(new FullPosition(0, 0, radii[i]));
               //slope
               double a = (y1 - y0) / (x1 - x0);
               // intercept
               double b = (x1 * y0 - x0 * y1) / (x1 - x0);
               double[] coeffs = {b, a};
               PolynomialFunction lineFunction = new PolynomialFunction(coeffs);

               // which zones this layer corresponds to
               int iZoneR1 = originalStructure.zoneOf(radii[i - 1]);
               int iZoneR2 = originalStructure.zoneOf(radii[i]);

               // overwrite structure information for all zones within this depth range
               for (int iZone = iZoneR1; iZone < iZoneR2; iZone++) {
                   switch(variable) {
                   case RHO: rho[iZone] = lineFunction; break;
                   case Vpv: vpv[iZone] = lineFunction; break;
                   case Vph: vph[iZone] = lineFunction; break;
                   case Vsv: vsv[iZone] = lineFunction; break;
                   case Vsh: vsh[iZone] = lineFunction; break;
                   case ETA: eta[iZone] = lineFunction; break;
                   case Vp: vpv[iZone] = lineFunction; vph[iZone] = lineFunction; break;
                   case Vs: vsv[iZone] = lineFunction; vsh[iZone] = lineFunction; break;
                   default: throw new IllegalArgumentException("Variable " + variable + " cannot be changed.");
                   }
               }
           }
       }

       return new PolynomialStructure(nZone, nCoreZone, rMin, rMax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa);
   }
}
