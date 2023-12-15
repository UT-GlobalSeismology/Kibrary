package io.github.kensuke1984.kibrary.waveform;

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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

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
      * components to be used
      */
     private Set<SACComponent> components;
     /**
      * partial waveform folder
      */
     private Path partialPath;
     /**
      * set of variable type to input
      */
     private Set<VariableType> inputVariableTypes;
     /**
      * set of variable type to output
      */
     private Set<VariableType> outputVariableTypes;
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
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##VariableTypes to input, listed using spaces (MU LAMBDA)");
            pw.println("#inputVariableTypes ");
            pw.println("##VariableTypes to output, listed using spaces (MU KAPPA)");
            pw.println("#outputVariableTypes ");
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

        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        partialPath = property.parsePath("partialPath", null, true, workPath);
        inputVariableTypes = Arrays.stream(property.parseStringArray("inputVariableTypes", "MU LAMBDA")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
        outputVariableTypes = Arrays.stream(property.parseStringArray("outputVariableTypes", "MU KAPPA")).map(VariableType::valueOf)
                .collect(Collectors.toSet());
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
        // read partials
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // set structure
        PolynomialStructure structure = PolynomialStructure.setupFromFileOrName(structurePath, structureName);

        outPath = DatasetAid.createOutputFolder(workPath, "partial", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
    }
}
