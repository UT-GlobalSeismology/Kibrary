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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 *
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

        // check whether the pair of partials exist
        if (inputVariableTypes.size() != 1)
            checkPair(inputPartialMap);

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

    private void checkPair(Map<VariableType, List<PartialID>> inputPartialMap) {
        List<PartialID> firstPartials = inputPartialMap.get(inputVariableTypes.get(0));
        for (int i = 1; i < inputVariableTypes.size(); i++) {
            List<PartialID> tmpPartials = inputPartialMap.get(inputVariableTypes.get(i));
            if (firstPartials.size() != tmpPartials.size())
                throw new IllegalArgumentException("The number of partials with respect to each variable type must be same");
            for (PartialID firstPartial : firstPartials) {
                boolean hasSet = false;
                for (PartialID tmpPartial : tmpPartials)
                    if (firstPartial.isPair(tmpPartial))
                        hasSet = true;
                if (!hasSet)
                    throw new IllegalArgumentException("The partial; " + firstPartial.toString() + "; don't have pair");
            }
        }
    }

    /**
     * @param outType
     * @param inputPartialMap
     * @param structure
     * @param allowIncomplete
     * @return
     */
    private List<PartialID> convertVariableType(VariableType outType, Map<VariableType, List<PartialID>>inputPartialMap,
            PolynomialStructure structure, boolean allowIncomplete) {
        List<PartialID> partials = new ArrayList<PartialID>();
        switch(outType) {
        case KAPPA:
        case XI:

        }
        return partials;
    }
}
