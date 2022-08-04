package io.github.kensuke1984.kibrary.multigrid;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

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


        return multigrid;
    }
}
