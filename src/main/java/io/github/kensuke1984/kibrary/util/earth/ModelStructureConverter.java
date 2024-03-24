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
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * The first part of the name of output structure file.
     */
    private String nameRoot;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * File of 1D structure used in inversion.
     */
    private Path initialStructurePath;
    /**
     * Name of 1D structure used in inversion.
     */
    private String initialStructureName;
    /**
     * Model file with perturbation information.
     */
    private Path modelPath;
    /**
     * Variable types to perturb.
     */
    private Set<VariableType> variableTypes;
    /**
     * Whether to connect the upper end with the initial structure.
     */
    private boolean tieInLowerEnd;
    /**
     * Whether to connect the lower end with the initial structure.
     */
    private boolean tieInUpperEnd;

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
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Path of an initial structure file used in inversion. If this is unset, the following initialStructureName will be referenced.");
            pw.println("#initialStructurePath ");
            pw.println("##Name of an initial structure model used in inversion. (PREM)");
            pw.println("#initialStructureName ");
            pw.println("##Path of a model file to use, must be set.");
            pw.println("#modelPath ");
            pw.println("##Variable types to perturb, listed using spaces, from {RHO,Vp,Vpv,Vph,Vs,Vsv,Vsh,ETA}. (Vs)");
            pw.println("#variableTypes ");
            pw.println("##(boolean) Whether to connect the lower end with the initial structure. (false)");
            pw.println("#tieInLowerEnd ");
            pw.println("##(boolean) Whether to connect the upper end with the initial structure. (false)");
            pw.println("#tieInUpperEnd ");
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
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        if (property.containsKey("initialStructurePath")) {
            initialStructurePath = property.parsePath("initialStructurePath", null, true, workPath);
        } else {
            initialStructureName = property.parseString("initialStructureName", "PREM");
        }
        modelPath = property.parsePath("modelPath", null, true, workPath);

        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "Vs")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        tieInLowerEnd = property.parseBoolean("tieInLowerEnd", "false");
        tieInUpperEnd = property.parseBoolean("tieInUpperEnd", "false");
    }

   @Override
   public void run() throws IOException {
       // set initial structure
       PolynomialStructure initialStructure = PolynomialStructure.setupFromFileOrName(initialStructurePath, initialStructureName);

       // read model
       List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
       double[] radii = knowns.stream().mapToDouble(known -> known.getParameter().getPosition().getR()).distinct().sorted().toArray();
       PerturbationModel model = new PerturbationModel(knowns, initialStructure);

       // introduce perturbations in polynomial structure
       PolynomialStructure perturbedStructure = convertModel(initialStructure, radii, model);

       // output structure
       Path outputPath = DatasetAid.generateOutputFilePath(workPath, nameRoot, fileTag, appendFileDate, null, ".structure");
       PolynomialStructureFile.write(perturbedStructure, outputPath);
   }

   private PolynomialStructure convertModel(PolynomialStructure initialStructure, double[] definedRadii, PerturbationModel model) {
       // extend the bottommost and topmost layers by 0.5*layerThickness (when not tieing in)
       double lowerExtendedRadius = (3 * definedRadii[0] - definedRadii[1]) / 2;
       double upperExtendedRadius = (3 * definedRadii[definedRadii.length - 1] - definedRadii[definedRadii.length - 2]) / 2;

       // set the radii of the layer borders
       double[] borderRadii = new double[definedRadii.length];
       for (int i = 0; i < definedRadii.length; i++) borderRadii[i] = definedRadii[i];
       if (!tieInLowerEnd) borderRadii[0] = lowerExtendedRadius;
       if (!tieInUpperEnd) borderRadii[borderRadii.length - 1] = upperExtendedRadius;

       // split up the polynomial structure at boundaries
       PolynomialStructure originalStructure = initialStructure.withBoundaries(borderRadii);
       if (tieInLowerEnd) originalStructure = originalStructure.withBoundaries(lowerExtendedRadius);
       if (tieInUpperEnd) originalStructure = originalStructure.withBoundaries(upperExtendedRadius);
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

           // for each layer
           // i=0: bottommost(below definedRadii[0])
           // i=definedRadii.length: topmost(above definedRadii[definedRadii.length-1])
           for (int i = 0; i < definedRadii.length + 1; i++) {

               // compute parameters in the layer, depending on each case
               double x0, x1, y0, y1;
               int iZone0, iZone1;
               if (i == 0) {
                   // bottommost layer; only when tieing in
                   if (!tieInLowerEnd) continue;
                   // the two points that the line segment should pass through
                   x0 = lowerExtendedRadius / planetRadius;
                   x1 = definedRadii[0] / planetRadius;
                   y0 = initialStructure.mediumAt(lowerExtendedRadius).get(variable);
                   y1 = discreteMap.get(new FullPosition(0, 0, definedRadii[0]));
                   // which zones this layer corresponds to
                   iZone0 = originalStructure.zoneOf(lowerExtendedRadius);
                   iZone1 = originalStructure.zoneOf(borderRadii[0]);
               } else if (i == definedRadii.length) {
                   // topmost layer; only when tieing in
                   if (!tieInUpperEnd) continue;
                   // the two points that the line segment should pass through
                   x0 = definedRadii[definedRadii.length - 1] / planetRadius;
                   x1 = upperExtendedRadius / planetRadius;
                   y0 = discreteMap.get(new FullPosition(0, 0, definedRadii[definedRadii.length - 1]));
                   y1 = initialStructure.mediumAt(upperExtendedRadius).get(variable);
                   // which zones this layer corresponds to
                   iZone0 = originalStructure.zoneOf(borderRadii[borderRadii.length - 1]);
                   iZone1 = originalStructure.zoneOf(upperExtendedRadius);
               } else {
                   // the two points that the line segment should pass through
                   x0 = definedRadii[i - 1] / planetRadius;
                   x1 = definedRadii[i] / planetRadius;
                   y0 = discreteMap.get(new FullPosition(0, 0, definedRadii[i - 1]));
                   y1 = discreteMap.get(new FullPosition(0, 0, definedRadii[i]));
                   // which zones this layer corresponds to
                   iZone0 = originalStructure.zoneOf(borderRadii[i - 1]);
                   iZone1 = originalStructure.zoneOf(borderRadii[i]);
               }

               // slope
               double a = (y1 - y0) / (x1 - x0);
               // intercept
               double b = (x1 * y0 - x0 * y1) / (x1 - x0);
               // function form
               double[] coeffs = {b, a};
               PolynomialFunction lineFunction = new PolynomialFunction(coeffs);

               // overwrite structure information for all zones within this depth range
               for (int iZone = iZone0; iZone < iZone1; iZone++) {
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
