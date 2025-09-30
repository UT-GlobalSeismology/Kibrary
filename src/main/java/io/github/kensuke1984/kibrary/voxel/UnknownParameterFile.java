package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * File of parameters of which their values are not yet known. See {@link UnknownParameter}.
 * <p>
 * m in Am=d
 * <p>
 * Each line:
 * <ul>
 * <li> 1D: ParameterType(LAYER) variableType r size </li>
 * <li> 3D: ParameterType(VOXEL) variableType lat lon r size </li>
 * </ul>
 * <p>
 * Duplication is NOT allowed.
 * Parameters with same type and position are regarded as same parameters, even if weighting differs. TODO really?
 * <p>
 * TODO ３次元と１次元の混在をさける
 *
 * @author Kensuke Konishi
 * @since version 0.0.6
 */
public class UnknownParameterFile {
    private UnknownParameterFile() {}

    /**
     * Write {@link UnknownParameter}s into a file.
     * @param parameterList (List of {@link UnknownParameter}) Parameters to write.
     * @param outputPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException if an I/O error occurs
     */
    public static void write(List<UnknownParameter> parameterList, Path outputPath, OpenOption... options)
            throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(parameterList.size(), "unknown parameter", "unknown parameters")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            parameterList.forEach(pw::println);
        }
    }

    /**
     * Read {@link UnknownParameter}s from file.
     * @param inputPath (Path) An unknown parameter file.
     * @return (<b>unmodifiable</b> List of {@link UnknownParameter}) Unknown parameters that are read in.
     * @throws IOException if an I/O error occurs.
     */
    public static List<UnknownParameter> read(Path inputPath) throws IOException {
        List<UnknownParameter> parameters = new ArrayList<>();
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            parameters.add(constructParameterFromParts(parts));
        }

        for (int i = 0; i < parameters.size() - 1; i++)
            for (int j = i + 1; j < parameters.size(); j++)
                if (parameters.get(i).equals(parameters.get(j)))
                    System.err.println("!Caution there is duplication in " + inputPath);

        DatasetAid.checkNum(parameters.size(), "unknown parameter", "unknown parameters");

        return Collections.unmodifiableList(parameters);
    }

    public static UnknownParameter constructParameterFromParts(String[] parts) {
        ParameterType type = ParameterType.valueOf(parts[0]);
        switch (type) {
        case SOURCE:
            return TimeSourceSideParameter.constructFromParts(parts);
        case RECEIVER:
            return TimeReceiverSideParameter.constructFromParts(parts);
        case LAYER:
            return Physical1DParameter.constructFromParts(parts);
        case VOXEL:
            return Physical3DParameter.constructFromParts(parts);
        default:
            throw new IllegalArgumentException("Unknown ParameterType.");
        }
    }

    /**
     * Return the new UnknownParameter of which variable type is changed from the one in given Unknownparameter to specied one.
     * @param ({@link UnknownParameter}) parameter
     * @param ({@link VariableType}) variableType
     * @return ({@link UnknownParameter}) New unknown parameters
     * @author Rei
     * @since 2024/6/11
     */
    public static UnknownParameter convertVariableType(UnknownParameter parameter, VariableType variableType) {
        switch (parameter.getParameterType()) {
        case SOURCE:
            if (!variableType.equals(VariableType.TIME))
                throw new IllegalArgumentException("Variable Type cannot change from VariableType.TIME for ParameterType.SOURCE");
            return parameter;
        case RECEIVER:
            if (!variableType.equals(VariableType.TIME))
                throw new IllegalArgumentException("Variable Type cannot change from VariableType.TIME for ParameterType.RECEIVER");
            return parameter;
        case LAYER:
            return new Physical1DParameter(variableType, parameter.getPosition().getR(), parameter.getSize());
        case VOXEL:
            return new Physical3DParameter(variableType, parameter.getPosition(), parameter.getSize());
        default:
            throw new IllegalArgumentException("Unknown ParameterType.");
        }
    }
}
