package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * File of parameters of which their values are not yet known. See {@link UnknownParameter}.
 * <p>
 * m in Am=d
 * <p>
 * Each line:
 * <ul>
 * <li> 3D(MU): PartialType lat lon r weighting </li>
 * <li> 1D(PAR2): PartialType r weighting </li>
 * </ul>
 * <p>
 * Duplication is NOT allowed.
 * Parameters with same type and position are regarded as same parameters, even if weighting differs. TODO really?
 * <p>
 * TODO ３次元と１次元の混在をさける
 *
 * @author Kensuke Konishi
 * @version 0.0.6
 */
public class UnknownParameterFile {
    private UnknownParameterFile() {}

    /**
     * @param parameterList List of unknown parameters
     * @param outputPath       for write
     * @param options       for write
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
     * @param inputPath of an unknown parameter file.
     * @return <b>unmodifiable</b> List of unknown parameters in the path //TODO this is now modifiable
     * @throws IOException if an I/O error occurs.
     */
    public static List<UnknownParameter> read(Path inputPath) throws IOException {
        List<UnknownParameter> pars = new ArrayList<>();
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            pars.add(constructParameterFromParts(parts));
        }

        for (int i = 0; i < pars.size() - 1; i++)
            for (int j = i + 1; j < pars.size(); j++)
                if (pars.get(i).equals(pars.get(j)))
                    System.err.println("!Caution there is duplication in " + inputPath);

        DatasetAid.checkNum(pars.size(), "unknown parameter", "unknown parameters");

//		return Collections.unmodifiableList(pars); //TODO why not this?
        return pars;
    }

    public static UnknownParameter constructParameterFromParts(String[] parts) {
        PartialType type = PartialType.valueOf(parts[0]);
        UnknownParameter unknown;
        switch (type) {
        case TIME_SOURCE:
            unknown = new TimeSourceSideParameter(new GlobalCMTID(parts[1]));
            break;
        case TIME_RECEIVER:
            unknown = new TimeReceiverSideParameter(new Observer(parts[1],
                    parts[2],
                    new HorizontalPosition(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]))), Integer.parseInt(parts[5]));
            break;
        case PAR1:
        case PAR2:
        case PARA:
        case PARC:
        case PARF:
        case PARL:
        case PARN:
        case PARQ:
        case PARVS:
        case PARVP:
            unknown = new Physical1DParameter(type, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            break;
        case A:
        case C:
        case F:
        case L:
        case N:
        case Q:
        case MU:
        case LAMBDA:
        case Vs:
        case LAMBDA2MU:
        case KAPPA:
        default:
            unknown = new Physical3DParameter(type, new FullPosition(Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3])), Double.parseDouble(parts[4]));
        }
        return unknown;
    }

}
