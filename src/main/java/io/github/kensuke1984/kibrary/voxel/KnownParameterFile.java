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
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * File of parameters with known values. See {@link KnownParameter}.
 * <p>
 * m in Am=d.
 * Values should be in delta (difference from initial model).
 * <p>
 * Each line:
 * <ul>
 * <li> 3D(MU): PartialType lat lon r weighting value </li>
 * <li> 1D(PAR2): PartialType r weighting value </li>
 * </ul>
 * <p>
 * Duplication is NOT allowed.
 * Parameters with same type and position are regarded as same parameters, even if weighting differs. //TODO really?
 *
 * <p>
 * {@code List<KnownParameter>} is used instead of {@code Map<UnknownParameter, Double>} here
 * because {@link UnknownParameter} is not Comparable and thus cannot be sorted.
 * (Map mixes up the order. Even with LinkedHashMap, keySet() cannot get the keys in order.)
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
    public static void write(List<KnownParameter> parameterList, Path outputPath, OpenOption... options)
            throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(parameterList.size(), "known parameter", "known parameters")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            parameterList.forEach(pw::println);
        }
    }

    public static void write(List<UnknownParameter> unknownList, double[] values, Path outputPath, OpenOption... options)
            throws IOException {
        if (unknownList.size() != values.length) throw new IllegalArgumentException("Number of unknowns and values differ.");

        // Output of the number of known parameters is not done here because it will be noisy in InverseProblem.

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            for (int i = 0; i < unknownList.size(); i++) {
                pw.println(unknownList.get(i) + " " + values[i]);
            }
        }
    }

    /**
     * @param inputPath (Path) a known parameter file.
     * @return <b>unmodifiable</b> List of known parameters in the path
     * @throws IOException if an I/O error occurs.
     */
    public static List<KnownParameter> read(Path inputPath) throws IOException {
        List<KnownParameter> pars = new ArrayList<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            UnknownParameter unknown = UnknownParameterFile.constructParameterFromParts(parts);
            double value = Double.parseDouble(parts[parts.length - 1]);
            pars.add(new KnownParameter(unknown, value));
        }

        for (int i = 0; i < pars.size() - 1; i++)
            for (int j = i + 1; j < pars.size(); j++)
                if (pars.get(i).equals(pars.get(j)))
                    System.err.println("!Caution there is duplication in " + inputPath);

        DatasetAid.checkNum(pars.size(), "known parameter", "known parameters");
        return Collections.unmodifiableList(pars);
    }

}
