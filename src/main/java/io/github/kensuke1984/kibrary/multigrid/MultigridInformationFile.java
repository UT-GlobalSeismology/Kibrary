package io.github.kensuke1984.kibrary.multigrid;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * @author otsuru
 * @since 2022/8/3
 */
public class MultigridInformationFile {
    private MultigridInformationFile() {}

    /**
     * @param design (MultigridDesign)
     * @param outPath       for write
     * @param options       for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(MultigridDesign design, Path outPath, OpenOption... options)
            throws IOException {
        List<List<UnknownParameter>> originalParameters = design.getOriginalParameters();
        List<UnknownParameter> fusedParameters = design.getFusedParameters();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            for (int i = 0; i < fusedParameters.size(); i++) {
                pw.println("#" + (i + 1));
                for (UnknownParameter param : originalParameters.get(i)) {
                    pw.println("- " + param);
                }
                pw.println("+ " + fusedParameters.get(i));
            }
        }
    }

    /**
     * @param path of a multigrid information file.
     * @return (MultigridDesign) design of multigrid
     * @throws IOException if an I/O error occurs.
     */
    public static MultigridDesign read(Path path) throws IOException {
        MultigridDesign multigrid = new MultigridDesign();

        InformationFileReader reader = new InformationFileReader(path, true);
        List<UnknownParameter> originalParams = new ArrayList<>();
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            String[] unknownParts = Arrays.stream(parts).skip(1).toArray(String[]::new);
            if (parts[0].equals("-")) {
                originalParams.add(UnknownParameterFile.constructParameterFromParts(unknownParts));
            } else if (parts[0].equals("+")) {
                UnknownParameter fusedParam = UnknownParameterFile.constructParameterFromParts(unknownParts);
                multigrid.add(originalParams, fusedParam);
                originalParams = new ArrayList<>();
            } else {
                throw new IllegalArgumentException("Line should start with \"-\" or \"+\"");
            }
        }

        return multigrid;
    }
}
