package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * This class converts variable type of given partial derivative waveforms.
 * <p>
 * Input partial must have pair of each partial with respect to each variable type.
 * Output folder will be made under the working folder, and all output partials will be written in the partilaID file and partialData file.
 * @author Rei
 * @since 2023/12/14 recreated former waveform.convert.*
 */
public class VariableTypeConvert extends Operation {

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
      * Path of the output folder
      */
     private Path outPath;
     /**
      * Partial waveform folder
      */
     private Path partialPath;
     /**
      * List of variable type to input
      */
     private List<VariableType> inputVariableTypes;
     /**
      * List of variable type to output
      */
     private List<VariableType> outputVariableTypes;
     /**
      * Path of structure file to use instead of PREM
      */
     private Path structurePath;
     /**
      * Structure to use
      */
     private String structureName;
     private PolynomialStructure structure;

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
            pw.println("##(String) A tag to include in output folder names. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##VariableTypes to input, listed using spaces (MU LAMBDA)");
            pw.println("#inputVariableTypes ");
            pw.println("##VariableTypes to output, listed using spaces (G KAPPA)");
            pw.println("#outputVariableTypes ");
            pw.println("##(boolean) Whether allow incomplete input variable types to calculate output variable types.");
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("#structurePath ");
            pw.println("##Name of a structure model you want to use (PREM)");
            pw.println("#structureName ");
        }
        System.err.println(outPath + " is created.");
    }

    public VariableTypeConvert(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        partialPath = property.parsePath("partialPath", null, true, workPath);
        inputVariableTypes = Arrays.stream(property.parseStringArray("inputVariableTypes", "MU LAMBDA")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        outputVariableTypes = Arrays.stream(property.parseStringArray("outputVariableTypes", "G KAPPA")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        if (inputVariableTypes.contains(VariableType.TIME) || outputVariableTypes.contains(VariableType.TIME))
            throw new IllegalArgumentException("This class does not handle time partials.");
        if (property.containsKey("structurePath")) {
            structurePath = property.parsePath("structurePath", null, true, workPath);
        } else {
            structureName = property.parseString("structureName", "PREM");
        }
    }

    @Override
    public void run() throws IOException {
        Map<VariableType, List<PartialID>> inputPartialMap = new HashMap<>();
        // read partials
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // set structure
        structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        // make map for each input varable type
        for (VariableType inType : inputVariableTypes) {
            System.err.println("Reading partials with respect to " + inType);
            List<PartialID> inPartials = new ArrayList<>();
            for (PartialID partialID : partialIDs) {
                if (inType.equals(partialID.getVariableType()))
                        inPartials.add(partialID);
            }
            inputPartialMap.put(inType, inPartials);
        }

        Map<VariableType, int[]> indexMap = makeIndexMap(inputPartialMap);

        // compute partials with respect to outputVariableTypes
        List<PartialID> outPartials = convertVariableType(inputPartialMap, indexMap);

        // create output folder and files
        outPath = DatasetAid.createOutputFolder(workPath, "partial", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
        PartialIDFile.write(outPartials, outPath);
    }

    private Map<VariableType, int[]> makeIndexMap(Map<VariableType, List<PartialID>> inputPartialMap) {
        System.err.println("making index");

        Map<VariableType, int[]> indexMap = new HashMap<>();
        // index map is made when plural inputVariableTypes are given
        if (inputVariableTypes.size() != 1) {
            List<PartialID> partialPrime = inputPartialMap.get(inputVariableTypes.get(0));
            final FullPosition[] positions = partialPrime.stream().map(p -> p.getVoxelPosition()).distinct().collect(Collectors.toList()).toArray(new FullPosition[0]);
            final PartialID[] partialsOrder = partialPrime.stream().parallel().filter(p -> p.getVoxelPosition().equals(positions[0])).collect(Collectors.toList()).toArray(new PartialID[0]);
            for (VariableType inType : inputVariableTypes) {
                int[] indexOrder = new int[partialPrime.size()];
                List<PartialID> partialsTmp = inputPartialMap.get(inType);
                IntStream.range(0, partialsTmp.size()).parallel().forEach(i -> {
                    int index = whichTimewindow(partialsTmp.get(i), partialsOrder) * positions.length + whichPosition(partialsTmp.get(i), positions);
                    indexOrder[index] = i;
                });
                indexMap.put(inType, indexOrder);
            }
        }
        return indexMap;
    }

    private static int whichTimewindow(PartialID partial, PartialID[] partialsOrder) {
        boolean find = false;
        int num = 0;
        for (int i = 0; i < partialsOrder.length; i++) {
            if (partial.isPair(partialsOrder[i])) {
                num = i;
                find = true;
            }
        }
        if (!find)
            throw new RuntimeException("partial; " + partial.toString() + "; cannot find the pair");
        return num;
    }

    private static int whichPosition(PartialID partial, FullPosition[] positions) {
        boolean find = false;
        int num = 0;
        for (int i = 0; i < positions.length; i++) {
            if (partial.getVoxelPosition().equals(positions[i])) {
                num = i;
                find = true;
            }
        }
        if (!find)
            throw new RuntimeException("partial; " + partial.toString() + "; cannot find the perturbed position");
        return num;
    }

    private List<PartialID> convertVariableType(Map<VariableType, List<PartialID>> inputPartialMap, Map<VariableType, int[]> indexMap) {
        System.err.println("converting variable type");
        if (outputVariableTypes.contains(VariableType.KAPPA) && outputVariableTypes.contains(VariableType.G))
            // convert from LAMBDA & MU to KAPPA & G
            return convertToElasticModuli(inputPartialMap, indexMap);
        else if (outputVariableTypes.contains(VariableType.LAMBDA) && outputVariableTypes.contains(VariableType.MU))
            // convert from KAPPA & G to LAMBDA & MU
            return convertToLameConstants(inputPartialMap, indexMap);
        else if (outputVariableTypes.contains(VariableType.Vp) && outputVariableTypes.contains(VariableType.Vs))
            // convert from LAMBDA & MU or from KAPPA & G to Vp & Vs
            return convertToIsotropicVelocities(inputPartialMap, indexMap);
        else if (outputVariableTypes.contains(VariableType.MU) && outputVariableTypes.contains(VariableType.XI))
            // convert from N & L to MU & XI
            return convertToAnisotropicParameters(inputPartialMap, indexMap);
        else if ((outputVariableTypes.contains(VariableType.MU) || outputVariableTypes.contains(VariableType.G) || outputVariableTypes.contains(VariableType.Vs)) && outputVariableTypes.size() == 1)
            // convert between MU, G, & Vs
            return convertShearModulus(inputPartialMap);
        else
            throw new IllegalArgumentException("given outputVariableTypes are not implemented");
    }

    /**
     *
     * @param inputPartialMap
     * @param indexMap
     * @return List of outPartials
     *
     * Compute partials with respect to &kappa; & G based on;<br>
     * <ul>
     * <li> &part;u/&part;&kappa; = &part;u/&part;&lambda;</li>
     * <li> &part;u/&part;G = &part;u/&part;&mu; - 2/3 &part;u/&part;&lambda;</li>
     * </ul>
     */
    private List<PartialID> convertToElasticModuli(Map<VariableType, List<PartialID>> inputPartialMap, Map<VariableType, int[]> indexMap) {
        List<PartialID> outPartials = new ArrayList<>();
        if (inputVariableTypes.contains(VariableType.LAMBDA) && inputVariableTypes.contains(VariableType.MU)) {
            List<PartialID> partialsLambda = inputPartialMap.get(VariableType.LAMBDA);
            List<PartialID> partialsMu = inputPartialMap.get(VariableType.MU);
            int[] indexLambda = indexMap.get(VariableType.LAMBDA);
            int[] indexMu = indexMap.get(VariableType.MU);

              for (int i = 0; i < partialsMu.size(); i++) {
                PartialID partialLambda = partialsLambda.get(indexLambda[i]);
                PartialID partialMu = partialsMu.get(indexMu[i]);
                if (!partialLambda.isPair(partialMu) || !partialLambda.getVoxelPosition().equals(partialMu.getVoxelPosition())) {
                    throw new RuntimeException("Order is different!!!");
                }

                Trace traceMu = partialMu.toTrace();
                Trace traceLambda = partialLambda.toTrace();
                Trace traceG = traceMu.add(traceLambda.multiply(-2./3.));

                PartialID partialG = new PartialID(partialMu.getObserver(), partialMu.getGlobalCMTID(), partialMu.getSacComponent(),
                        partialMu.getSamplingHz(), partialMu.getStartTime(), partialMu.getNpts(), partialMu.getMinPeriod(), partialMu.getMaxPeriod(),
                        partialMu.getPhases(), partialMu.isConvolved(), partialMu.getParameterType(), VariableType.G, partialMu.getVoxelPosition(),
                        traceG.getY());
                PartialID partialKappa = new PartialID(partialMu.getObserver(), partialMu.getGlobalCMTID(), partialMu.getSacComponent(),
                        partialMu.getSamplingHz(), partialMu.getStartTime(), partialMu.getNpts(), partialMu.getMinPeriod(), partialMu.getMaxPeriod(),
                        partialMu.getPhases(), partialMu.isConvolved(), partialMu.getParameterType(), VariableType.KAPPA, partialMu.getVoxelPosition(),
                        traceLambda.getY());
                outPartials.add(partialG);
                outPartials.add(partialKappa);
                }
        }
        else
            throw new IllegalArgumentException("given inputVariableTypes are insufficient to compute partials with respect to KAPPA & G");
        return outPartials;
    }

    /**
     *
     * @param inputPartialMap
     * @param indexMap
     * @return List of outPartials
     *
     * Compute partials with respect to &lambda; & &mu; based on;<br>
     * <ul>
     * <li> &part;u/&part;&lambda; = &part;u/&part;&kappa;</li>
     * <li> &part;u/&part;&mu; = &part;u/&part;G + 2/3 &part;u/&part;&kappa;</li>
     * </ul>
     */
    private List<PartialID> convertToLameConstants(Map<VariableType, List<PartialID>> inputPartialMap, Map<VariableType, int[]> indexMap) {
        List<PartialID> outPartials = new ArrayList<>();
        if (inputVariableTypes.contains(VariableType.KAPPA) && inputVariableTypes.contains(VariableType.G)) {
            List<PartialID> partialsKappa = inputPartialMap.get(VariableType.KAPPA);
            List<PartialID> partialsG = inputPartialMap.get(VariableType.G);
            int[] indexKappa = indexMap.get(VariableType.KAPPA);
            int[] indexG = indexMap.get(VariableType.G);

            for (int i = 0; i < partialsG.size(); i++) {
                PartialID partialKappa = partialsKappa.get(indexKappa[i]);
                PartialID partialG = partialsG.get(indexG[i]);
                Trace traceG = partialG.toTrace();
                Trace traceKappa = partialKappa.toTrace();
                Trace traceMu = traceG.add(traceKappa.multiply(2./3.));
                PartialID partialMu = new PartialID(partialG.getObserver(), partialG.getGlobalCMTID(), partialG.getSacComponent(),
                        partialG.getSamplingHz(), partialG.getStartTime(), partialG.getNpts(), partialG.getMinPeriod(), partialG.getMaxPeriod(),
                        partialG.getPhases(), partialG.isConvolved(), partialG.getParameterType(), VariableType.MU, partialG.getVoxelPosition(),
                        traceMu.getY());
                PartialID partialLambda = new PartialID(partialG.getObserver(), partialG.getGlobalCMTID(), partialG.getSacComponent(),
                        partialG.getSamplingHz(), partialG.getStartTime(), partialG.getNpts(), partialG.getMinPeriod(), partialG.getMaxPeriod(),
                        partialG.getPhases(), partialG.isConvolved(), partialG.getParameterType(), VariableType.LAMBDA, partialG.getVoxelPosition(),
                        traceKappa.getY());
                outPartials.add(partialMu);
                outPartials.add(partialLambda);
            }
        }
        else
            throw new IllegalArgumentException("given inputVariableTypes are insufficient to compute partials with respect to LAMBDA & MU");
        return outPartials;
    }

    /**
     *
     * @param inputPartialMap
     * @param indexMap
     * @return List of outPartials
     *
     * Compute partials with respect to Vp & Vs based on;<br>
     * <ul>
     * <li> &part;u/&part;Vp = 2&rho;Vp &part;u/&part;&lambda;</li>
     * <li> &part;u/&part;Vs = 2&rho;Vs &part;u/&part;&mu; - 4&rho;Vs &part;u/&part;&lambda;</li>
     * </ul>
     * or<br>
     * <ul>
     * <li> &part;u/&part;Vp = 2&rho;Vp &part;u/&part;&kappa;</li>
     * <li> &part;u/&part;Vs = 2&rho;Vs &part;u/&part;G - 8/3 &rho;Vs &part;u/&part;&kappa;</li>
     * </ul>
     */
    private List<PartialID> convertToIsotropicVelocities(Map<VariableType, List<PartialID>> inputPartialMap, Map<VariableType, int[]> indexMap) {
        List<PartialID> outPartials = new ArrayList<>();
        if (inputVariableTypes.contains(VariableType.LAMBDA) && inputVariableTypes.contains(VariableType.MU)) {
            List<PartialID> partialsLambda = inputPartialMap.get(VariableType.LAMBDA);
            List<PartialID> partialsMu = inputPartialMap.get(VariableType.MU);
            int[] indexLambda = indexMap.get(VariableType.LAMBDA);
            int[] indexMu = indexMap.get(VariableType.MU);

            for (int i = 0; i < partialsMu.size(); i++) {
                PartialID partialLambda = partialsLambda.get(indexLambda[i]);
                PartialID partialMu = partialsMu.get(indexMu[i]);
                Trace traceMu = partialMu.toTrace();
                Trace traceLambda = partialLambda.toTrace();
                double radius = partialMu.voxelPosition.getR();
                double rho = structure.getAtRadius(VariableType.RHO, radius);
                double vs = structure.getAtRadius(VariableType.Vs, radius);
                double vp = structure.getAtRadius(VariableType.Vp, radius);
                Trace traceVs = traceLambda.multiply(-4. * rho * vs).add(traceMu.multiply(2. * rho * vs));
                Trace traceVp = traceLambda.multiply(2. * rho * vp);
                PartialID partialVs = new PartialID(partialMu.getObserver(), partialMu.getGlobalCMTID(), partialMu.getSacComponent(),
                        partialMu.getSamplingHz(), partialMu.getStartTime(), partialMu.getNpts(), partialMu.getMinPeriod(), partialMu.getMaxPeriod(),
                        partialMu.getPhases(), partialMu.isConvolved(), partialMu.getParameterType(), VariableType.Vs, partialMu.getVoxelPosition(),
                        traceVs.getY());
                PartialID partialVp = new PartialID(partialMu.getObserver(), partialMu.getGlobalCMTID(), partialMu.getSacComponent(),
                        partialMu.getSamplingHz(), partialMu.getStartTime(), partialMu.getNpts(), partialMu.getMinPeriod(), partialMu.getMaxPeriod(),
                        partialMu.getPhases(), partialMu.isConvolved(), partialMu.getParameterType(), VariableType.Vp, partialMu.getVoxelPosition(),
                        traceVp.getY());
                outPartials.add(partialVs);
                outPartials.add(partialVp);
            }
        }
        else if (inputVariableTypes.contains(VariableType.KAPPA) && inputVariableTypes.contains(VariableType.G)) {
            List<PartialID> partialsKappa = inputPartialMap.get(VariableType.KAPPA);
            List<PartialID> partialsG = inputPartialMap.get(VariableType.G);
            int[] indexKappa = indexMap.get(VariableType.KAPPA);
            int[] indexG = indexMap.get(VariableType.G);

            for (int i = 0; i < partialsG.size(); i++) {
                PartialID partialKappa = partialsKappa.get(indexKappa[i]);
                PartialID partialG = partialsG.get(indexG[i]);
                Trace traceG = partialG.toTrace();
                Trace traceKappa = partialKappa.toTrace();
                double radius = partialG.voxelPosition.getR();
                double rho = structure.getAtRadius(VariableType.RHO, radius);
                double vs = structure.getAtRadius(VariableType.Vs, radius);
                double vp = structure.getAtRadius(VariableType.Vp, radius);
                Trace traceVs = traceKappa.multiply(-8. / 3. * rho * vs).add(traceG.multiply(2. * rho * vs));
                Trace traceVp = traceKappa.multiply(2. * rho * vp);
                PartialID partialVs = new PartialID(partialG.getObserver(), partialG.getGlobalCMTID(), partialG.getSacComponent(),
                        partialG.getSamplingHz(), partialG.getStartTime(), partialG.getNpts(), partialG.getMinPeriod(), partialG.getMaxPeriod(),
                        partialG.getPhases(), partialG.isConvolved(), partialG.getParameterType(), VariableType.Vs, partialG.getVoxelPosition(),
                        traceVs.getY());
                PartialID partialVp = new PartialID(partialG.getObserver(), partialG.getGlobalCMTID(), partialG.getSacComponent(),
                        partialG.getSamplingHz(), partialG.getStartTime(), partialG.getNpts(), partialG.getMinPeriod(), partialG.getMaxPeriod(),
                        partialG.getPhases(), partialG.isConvolved(), partialG.getParameterType(), VariableType.Vp, partialG.getVoxelPosition(),
                        traceVp.getY());
                outPartials.add(partialVs);
                outPartials.add(partialVp);
            }
        }
        else
            throw new IllegalArgumentException("given inputVariableTypes are insufficient to compute partials with respect to Vp & Vs");
        return outPartials;
    }

    /**
    *
    * @param inputPartialMap
    * @param indexMap
    * @return List of outPartials
    *
    * Compute partials with respect to &mu; & &xi; based on;<br>
    * <ul>
    * <li> &part;u/&part;&mu; = 3/(N+2L) (N &part;u/&part;N + L &part;u/&part;L)</li>
    * <li> &part;u/&part;&xi; = L<sup>2</sup>/(N+2L) (2 &part;u/&part;N - &part;u/&part;L)</li>
    * </ul>
    */
    private List<PartialID> convertToAnisotropicParameters(Map<VariableType, List<PartialID>> inputPartialMap, Map<VariableType, int[]> indexMap) {
        List<PartialID> outPartials = new ArrayList<>();
        if (inputVariableTypes.contains(VariableType.N) && inputVariableTypes.contains(VariableType.L)) {
            List<PartialID> partialsN = inputPartialMap.get(VariableType.N);
            List<PartialID> partialsL = inputPartialMap.get(VariableType.L);
            int[] indexN = indexMap.get(VariableType.N);
            int[] indexL = indexMap.get(VariableType.L);

            for (int i = 0; i < partialsL.size(); i++) {
                PartialID partialN = partialsN.get(indexN[i]);
                PartialID partialL = partialsL.get(indexL[i]);
                Trace traceL = partialL.toTrace();
                Trace traceN = partialN.toTrace();
                double radius = partialL.voxelPosition.getR();
                double rho = structure.getAtRadius(VariableType.RHO, radius);
                double vsh = structure.getAtRadius(VariableType.Vsh, radius);
                double vsv = structure.getAtRadius(VariableType.Vsv, radius);
                double n = rho * vsh * vsh;
                double l = rho * vsv * vsv;
                double n2l = n + 2. * l;
                Trace traceMu = traceN.multiply(n).add(traceL.multiply(l)).multiply(3. / n2l);
                Trace traceXi = traceN.multiply(2.).add(traceL.multiply(-1.)).multiply(l * l / n2l);
                PartialID partialMu = new PartialID(partialL.getObserver(), partialL.getGlobalCMTID(), partialL.getSacComponent(),
                        partialL.getSamplingHz(), partialL.getStartTime(), partialL.getNpts(), partialL.getMinPeriod(), partialL.getMaxPeriod(),
                        partialL.getPhases(), partialL.isConvolved(), partialL.getParameterType(), VariableType.MU, partialL.getVoxelPosition(),
                        traceMu.getY());
                PartialID partialXi = new PartialID(partialL.getObserver(), partialL.getGlobalCMTID(), partialL.getSacComponent(),
                        partialL.getSamplingHz(), partialL.getStartTime(), partialL.getNpts(), partialL.getMinPeriod(), partialL.getMaxPeriod(),
                        partialL.getPhases(), partialL.isConvolved(), partialL.getParameterType(), VariableType.XI, partialL.getVoxelPosition(),
                        traceXi.getY());
                outPartials.add(partialMu);
                outPartials.add(partialXi);
            }
        }
        else
            throw new IllegalArgumentException("given inputVariableTypes are insufficient to compute partials with respect to MU & XI");
        return outPartials;
    }

    /**
    *
    * @param inputPartialMap
    * @return List of outPartials
    *
    * Convert variable type between &mu;, G, & Vs based on;<br>
    * <ul>
    * <li> &part;u/&part;&mu; = &part;u/&part;G = 1/2&rho;Vs &part;u/&part;Vs
    * </ul>
    */
    private List<PartialID> convertShearModulus(Map<VariableType, List<PartialID>> inputPartialMap) {
        List<PartialID> outPartials = new ArrayList<>();
        if (outputVariableTypes.get(0).equals(VariableType.G)) {
            List<PartialID> partialsMu = inputPartialMap.get(VariableType.MU);
            for (PartialID partialMu : partialsMu) {
                PartialID partialG = new PartialID(partialMu.getObserver(), partialMu.getGlobalCMTID(), partialMu.getSacComponent(),
                        partialMu.getSamplingHz(), partialMu.getStartTime(), partialMu.getNpts(), partialMu.getMinPeriod(), partialMu.getMaxPeriod(),
                        partialMu.getPhases(), partialMu.isConvolved(), partialMu.getParameterType(), VariableType.G, partialMu.getVoxelPosition(),
                        partialMu.getData());
                outPartials.add(partialG);
            }
        }
        else if (outputVariableTypes.get(0).equals(VariableType.MU)) {
            List<PartialID> partialsG = inputPartialMap.get(VariableType.G);
            for (PartialID partialG : partialsG) {
                PartialID partialMu = new PartialID(partialG.getObserver(), partialG.getGlobalCMTID(), partialG.getSacComponent(),
                        partialG.getSamplingHz(), partialG.getStartTime(), partialG.getNpts(), partialG.getMinPeriod(), partialG.getMaxPeriod(),
                        partialG.getPhases(), partialG.isConvolved(), partialG.getParameterType(), VariableType.MU, partialG.getVoxelPosition(),
                        partialG.getData());
                outPartials.add(partialMu);
            }
        }
        else if (outputVariableTypes.get(0).equals(VariableType.Vs)) {
            List<PartialID> partials = inputVariableTypes.contains(VariableType.MU) ? inputPartialMap.get(VariableType.MU) : inputPartialMap.get(VariableType.G);
            for (PartialID partial : partials) {
                Trace trace = partial.toTrace();
                double radius = partial.voxelPosition.getR();
                double rho = structure.getAtRadius(VariableType.RHO, radius);
                double vs = structure.getAtRadius(VariableType.Vs, radius);
                Trace traceVs = trace.multiply(2. * rho * vs);
                PartialID partialVs = new PartialID(partial.getObserver(), partial.getGlobalCMTID(), partial.getSacComponent(),
                        partial.getSamplingHz(), partial.getStartTime(), partial.getNpts(), partial.getMinPeriod(), partial.getMaxPeriod(),
                        partial.getPhases(), partial.isConvolved(), partial.getParameterType(), VariableType.Vs, partial.getVoxelPosition(),
                        traceVs.getY());
                outPartials.add(partialVs);
            }
        }
        else
            throw new IllegalArgumentException("given inputVariableTypes are insufficient to compute partials with respect to " + outputVariableTypes.get(0));
        return outPartials;
    }
}
