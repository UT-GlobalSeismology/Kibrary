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

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * This class converts variable type of given partial derivative waveforms. <p>
 * In this current version, the conversion is implemented from &mu; & &lambda; to &kappa; and from N & L to &xi;
 * <p>
 * Input partial must have pair of each partial with respect to each variable type.
 * Output folder will be made under the working folder, and all output partials will be written in the partilaID file and partialData file.
 * @author Rei
 * @since 2023/12/14
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
      * Whether allow incomplete input variable types to calculate output variable types.
      * If this is true, 0 is substituted for empty partials
      */
     private boolean allowIncomplete;
     /**
      * Path of structure file to use instead of PREM
      */
     private Path structurePath;
     /**
      * Structure to use
      */
     private String structureName;

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
            pw.println("##VariableTypes to output, listed using spaces (MU KAPPA)");
            pw.println("#outputVariableTypes ");
            pw.println("##(boolean) Whether allow incomplete input variable types to calculate output variable types.");
            pw.println("##If this is true, 0 is substituted for empty partials (false)");
            pw.println("#allowIncomplete true");
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
        outputVariableTypes = Arrays.stream(property.parseStringArray("outputVariableTypes", "MU KAPPA")).map(VariableType::valueOf)
                .collect(Collectors.toList());
        if (inputVariableTypes.contains(VariableType.TIME) || outputVariableTypes.contains(VariableType.TIME))
            throw new IllegalArgumentException("This class does not handle time partials.");
        allowIncomplete = property.parseBoolean("allowIncomplete", "true");
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
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        // make map for each input varable type
        for (VariableType inType : inputVariableTypes) {
            List<PartialID> inPartials = new ArrayList<>();
            for (PartialID partialID : partialIDs) {
                if (inType.equals(partialID.getVariableType()))
                        inPartials.add(partialID);
            }
            inputPartialMap.put(inType, inPartials);
        }

        // compute partials with respect to outputVariableTypes
        List<PartialID> outPartials = new ArrayList<>();
        for (VariableType outType : outputVariableTypes) {
            if (inputPartialMap.containsKey(outType))
                outPartials.addAll(inputPartialMap.get(outType));
            else
                outPartials.addAll(convertVariableType(outType, inputPartialMap, structure, allowIncomplete));
        }

        // create output folder and files
        outPath = DatasetAid.createOutputFolder(workPath, "partial", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
        PartialIDFile.write(outPartials, outPath);
    }

    /**
     * Calculate partilas with respect to outType from input partials
     * @param outType {@link VariableType} Only &kappa; and &xi; are allowed now
     * @param inputPartialMap {@link Map} of input partials with respect to each input variable type
     * @param structure {@link PolynomialStructure}
     * @param allowIncomplete (boolean) If it is true, 0 is substituted for empty partials
     * @return partialIDs after conversion of variable type.
     */
    private List<PartialID> convertVariableType(VariableType outType, Map<VariableType, List<PartialID>>inputPartialMap,
            PolynomialStructure structure, boolean allowIncomplete) {
        switch(outType) {
        case KAPPA:
            List<PartialID> partialsMU = (allowIncomplete && !inputPartialMap.containsKey(VariableType.MU)) ? null :
                inputPartialMap.get(VariableType.MU);
            List<PartialID> partialsLAMBDA = (allowIncomplete && !inputPartialMap.containsKey(VariableType.LAMBDA)) ? null :
                inputPartialMap.get(VariableType.LAMBDA);
            if (partialsMU == null && partialsLAMBDA == null)
                throw new RuntimeException("To compute partials with respect to KAPPA, MU and/or LAMBDA are required as inputVariableType");
            return convertToKAPPA(partialsMU, partialsLAMBDA);
        case XI:
            List<PartialID> partialsN = (allowIncomplete && !inputPartialMap.containsKey(VariableType.N)) ? null :
                inputPartialMap.get(VariableType.N);
            List<PartialID> partialsL = (allowIncomplete && !inputPartialMap.containsKey(VariableType.L)) ? null :
                inputPartialMap.get(VariableType.L);
            if (partialsN == null && partialsL == null)
                throw new RuntimeException("To compute partials with respect to XI, N and/or L are required as inputVariableType");
            return convertToXI(partialsN, partialsL, structure);
        default:
            throw new RuntimeException("VariableType; " + outType + " is NOT utilized yet.");
        }
    }

    /**
     * Calculate partilas with respect to &kappa; based on the following equation;
     * <p>
     * &part;u/&part;&kappa; = &part;u/&part;&lambda; + 1.5 &part;u/&part;&mu;
     * @param partialsMU with respect to &mu;
     * @param partialsLAMBDA with respect to &lambda;
     * @return partialIDs with respect to &kappa;
     */
    private List<PartialID> convertToKAPPA(List<PartialID> partialsMU, List<PartialID> partialsLAMBDA) {
        List<PartialID> partialIDs = new ArrayList<>();
        // convert only from LAMBDA
        if (partialsMU == null) {
            for (PartialID partialLAMBDA : partialsLAMBDA) {
                // calculate a partial with respect to KAPPA
                Trace traceKAPPA = partialLAMBDA.toTrace();
                PartialID partialID = new PartialID(partialLAMBDA.getObserver(), partialLAMBDA.getGlobalCMTID(), partialLAMBDA.getSacComponent(),
                        partialLAMBDA.getSamplingHz(), partialLAMBDA.getStartTime(), partialLAMBDA.getNpts(), partialLAMBDA.getMinPeriod(), partialLAMBDA.getMaxPeriod(),
                        partialLAMBDA.getPhases(), partialLAMBDA.isConvolved(), partialLAMBDA.getParameterType(), VariableType.KAPPA, partialLAMBDA.getVoxelPosition(),
                        traceKAPPA.getY());
                partialIDs.add(partialID);
            }
        }
        // convert only from MU
        else if (partialsLAMBDA == null) {
            for (PartialID partialMU : partialsMU) {
                // calculate a partial with respect to KAPPA
                Trace traceKAPPA = partialMU.toTrace().multiply(1.5);
                PartialID partialID = new PartialID(partialMU.getObserver(), partialMU.getGlobalCMTID(), partialMU.getSacComponent(),
                        partialMU.getSamplingHz(), partialMU.getStartTime(), partialMU.getNpts(), partialMU.getMinPeriod(), partialMU.getMaxPeriod(),
                        partialMU.getPhases(), partialMU.isConvolved(), partialMU.getParameterType(), VariableType.KAPPA, partialMU.getVoxelPosition(),
                        traceKAPPA.getY());
                partialIDs.add(partialID);
            }
        }
        // convert from MU & LAMBDA
        else {
            for (PartialID partialMU : partialsMU) {
                // search the pair of partials
                PartialID partialLAMBDA = null;
                for (PartialID tmpPartial : partialsLAMBDA)
                    if (partialMU.isPair(tmpPartial))
                        partialLAMBDA = tmpPartial;
                if (partialLAMBDA == null)
                    throw new RuntimeException("The partial; " + partialMU.toString() + "; don't have pair");
                // calculate a partial with respect to KAPPA
                Trace traceMU = partialMU.toTrace();
                Trace traceLAMBDA = partialLAMBDA.toTrace();
                Trace traceKAPPA = traceLAMBDA.add(traceMU.multiply(1.5));
                PartialID partialID = new PartialID(partialMU.getObserver(), partialMU.getGlobalCMTID(), partialMU.getSacComponent(),
                        partialMU.getSamplingHz(), partialMU.getStartTime(), partialMU.getNpts(), partialMU.getMinPeriod(), partialMU.getMaxPeriod(),
                        partialMU.getPhases(), partialMU.isConvolved(), partialMU.getParameterType(), VariableType.KAPPA, partialMU.getVoxelPosition(),
                        traceKAPPA.getY());
                partialIDs.add(partialID);
            }
        }
        return partialIDs;
    }

    /**
     * Calculate partilas with respect to &xi; based on the following equation;
     * <p>
     * &xi; &part;u/&part;&xi; = N &part;u/&part;N - L &part;u/&part;L
     * @param partialsMU with respect to &mu;
     * @param partialsLAMBDA with respect to &lambda;
     * @return partialIDs with respect to &kappa;
     */
    private List<PartialID> convertToXI(List<PartialID> partialsN, List<PartialID> partialsL, PolynomialStructure structure) {
        List<PartialID> partialIDs = new ArrayList<>();
        // convert only from L
        if (partialsN == null) {
            for (PartialID partialL : partialsL) {
                // compute coefficients
                double vsh = structure.getAtRadius(VariableType.Vsh, partialL.getVoxelPosition().getR());
                double vsv = structure.getAtRadius(VariableType.Vsv, partialL.getVoxelPosition().getR());
                double rho = structure.getAtRadius(VariableType.RHO, partialL.getVoxelPosition().getR());
                double n = vsh * vsh * rho;
                double l = vsv * vsv * rho;
                double coeff = - l * l / n;
                // calculate a partial with respect to XI
                Trace traceXI = partialL.toTrace().multiply(coeff);
                PartialID partialID = new PartialID(partialL.getObserver(), partialL.getGlobalCMTID(), partialL.getSacComponent(),
                        partialL.getSamplingHz(), partialL.getStartTime(), partialL.getNpts(), partialL.getMinPeriod(), partialL.getMaxPeriod(),
                        partialL.getPhases(), partialL.isConvolved(), partialL.getParameterType(), VariableType.XI, partialL.getVoxelPosition(),
                        traceXI.getY());
                partialIDs.add(partialID);
            }
        }
        // convert only from N
        else if (partialsL == null) {
            for (PartialID partialN : partialsN) {
                // compute coefficients
                double vsv = structure.getAtRadius(VariableType.Vsv, partialN.getVoxelPosition().getR());
                double rho = structure.getAtRadius(VariableType.RHO, partialN.getVoxelPosition().getR());
                double l = vsv * vsv * rho;
                // calculate a partial with respect to XI
                Trace traceXI = partialN.toTrace().multiply(l);
                PartialID partialID = new PartialID(partialN.getObserver(), partialN.getGlobalCMTID(), partialN.getSacComponent(),
                        partialN.getSamplingHz(), partialN.getStartTime(), partialN.getNpts(), partialN.getMinPeriod(), partialN.getMaxPeriod(),
                        partialN.getPhases(), partialN.isConvolved(), partialN.getParameterType(), VariableType.XI, partialN.getVoxelPosition(),
                        traceXI.getY());
                partialIDs.add(partialID);
            }
        }
        // convert from N & L
        else {
            for (PartialID partialN : partialsN) {
                // search the pair of partials
                PartialID partialL = null;
                for (PartialID tmpPartial : partialsL)
                    if (partialN.isPair(tmpPartial))
                        partialL = tmpPartial;
                if (partialL == null)
                    throw new RuntimeException("The partial; " + partialN.toString() + "; don't have pair");
                // compute coefficients
                double vsh = structure.getAtRadius(VariableType.Vsh, partialL.getVoxelPosition().getR());
                double vsv = structure.getAtRadius(VariableType.Vsv, partialL.getVoxelPosition().getR());
                double rho = structure.getAtRadius(VariableType.RHO, partialL.getVoxelPosition().getR());
                double n = vsh * vsh * rho;
                double l = vsv * vsv * rho;
                double coeff = - l * l / n;
                // calculate a partial with respect to XI
                Trace traceN = partialN.toTrace();
                Trace traceL = partialL.toTrace();
                Trace traceXI = traceL.multiply(coeff).add(traceN.multiply(l));
                PartialID partialID = new PartialID(partialN.getObserver(), partialN.getGlobalCMTID(), partialN.getSacComponent(),
                        partialN.getSamplingHz(), partialN.getStartTime(), partialN.getNpts(), partialN.getMinPeriod(), partialN.getMaxPeriod(),
                        partialN.getPhases(), partialN.isConvolved(), partialN.getParameterType(), VariableType.XI, partialN.getVoxelPosition(),
                        traceXI.getY());
                partialIDs.add(partialID);
            }
        }
        return partialIDs;
    }
}
