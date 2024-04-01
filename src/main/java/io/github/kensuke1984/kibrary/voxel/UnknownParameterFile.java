package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;

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
 * @since a long time ago
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
        DatasetAid.printNumOutput(parameterList.size(), "unknown parameter", "unknown parameters", outputPath);

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

        DatasetAid.printNumInput(parameters.size(), "unknown parameter", "unknown parameters", inputPath);

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

}
