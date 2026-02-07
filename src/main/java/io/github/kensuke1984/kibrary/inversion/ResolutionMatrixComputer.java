package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.solve.ConjugateGradientMethod;
import io.github.kensuke1984.kibrary.inversion.solve.InverseMethodEnum;
import io.github.kensuke1984.kibrary.inversion.solve.SingularValueDecomposition;
import io.github.kensuke1984.kibrary.util.DatasetAid;

/**
 * Class to compute resolution matrix.
 *
 * @author otsuru
 * @since 2026/1/25
 */
public class ResolutionMatrixComputer {

    /**
     * Compute resolution matrix.
     * @param args Options.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder("r").longOpt("result").hasArg().argName("resultPath").required()
                .desc("Path of result directory.").build());
        options.addOption(Option.builder("m").longOpt("method").hasArg().argName("inverseMethod").required()
                .desc("Inverse method type, from {SVD, CG, LS}.").build());
        options.addOption(Option.builder("i").longOpt("index").hasArg().argName("iBasis").required()
                .desc("Number of basis vectors.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path resultPath = Paths.get(cmdLine.getOptionValue("r"));
        InverseMethodEnum inverseMethod = InverseMethodEnum.of(cmdLine.getOptionValue("m"));
        int i = Integer.parseInt(cmdLine.getOptionValue("i"));
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "resolution", fileTag, appendFileDate, null, ".lst");

        switch (inverseMethod) {
        case CONJUGATE_GRADIENT:
            ConjugateGradientMethod.computeResolutionMatrix(resultPath.toAbsolutePath().normalize().getParent(), resultPath, i, outputPath);
            break;
        case SINGULAR_VALUE_DECOMPOSITION:
            SingularValueDecomposition.computeResolutionMatrix(resultPath.toAbsolutePath().normalize().getParent(), resultPath, i, outputPath);
            break;
        default:
        }
    }

}
