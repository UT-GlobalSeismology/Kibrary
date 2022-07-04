package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * File of parameters with known values.
 * m in Am=d
 * <p>
 * Each line:
 * <ul>
 * <li> 3D(MU): PartialType lat lon r weighting value </li>
 * <li> 1D(PAR2): PartialType r weighting value </li>
 * </ul>
 * <p>
 * Duplication is NOT allowed.
 * Parameters with same type and position are regarded as same parameters, even if weighting differs.
 *
 * <p>
 * {@code List<KnownParameter>} is used instead of {@code Map<UnknownParameter, Double>} here
 * because {@link UnknownParameter} is not Comparable and thus cannot be sorted.
 * <p>
 * TODO ３次元と１次元の混在をさける
 *
 * @author otsuru
 * @since 2022/7/2
 */
public class KnownParameterFile {
    private KnownParameterFile() {}

    /**
     * @param parameteList (List) List of knwon parameters
     * @param outPath       for write
     * @param options       for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(List<KnownParameter> parameterList, Path outPath, OpenOption... options)
            throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            parameterList.forEach(pw::println);
        }
    }

    /**
     * @param path of a known parameter file.
     * @return <b>unmodifiable</b> List of known parameters in the path
     * @throws IOException if an I/O error occurs.
     */
    public static List<KnownParameter> read(Path path) throws IOException {
        List<KnownParameter> pars = new ArrayList<>();

        InformationFileReader reader = new InformationFileReader(path, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            UnknownParameter unknown = UnknownParameterFile.constructParameterFromParts(parts);
            double value = Double.parseDouble(parts[parts.length - 1]);
            pars.add(new KnownParameter(unknown, value));
        }

        for (int i = 0; i < pars.size() - 1; i++)
            for (int j = i + 1; j < pars.size(); j++)
                if (pars.get(i).equals(pars.get(j)))
                    System.err.println("!Caution there is duplication in " + path);
        return Collections.unmodifiableList(pars);
    }

}
