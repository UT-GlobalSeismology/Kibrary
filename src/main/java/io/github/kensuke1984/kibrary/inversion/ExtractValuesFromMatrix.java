package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Class to extract specified components of a matrix to get values for each {@link UnknownParameter}.
 *
 * @since 2023/6/29
 * @version 2026/1/25 Renamed from DiagATAExtract to ExtractValuesFromMatrix.
 * @author otsuru
 */
public class ExtractValuesFromMatrix {

    /**
     * Extract specified components of an input matrix.
     * @param args (String[]) Options.
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
     * @return (Options) Options that can be specified by user.
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder("u").longOpt("unknowns").hasArg().argName("unknownParameterFile").required()
                .desc("Path of unknown parameter file.").build());
        options.addOption(Option.builder("v").longOpt("variable").hasArg().argName("variableType").required()
                .desc("Variable type.").build());
        options.addOption(Option.builder("m").longOpt("matrix").hasArg().argName("matrixFile").required()
                .desc("Path of input matrix file.").build());

        // criteria of values to extract
        OptionGroup criteriaOption = new OptionGroup();
        criteriaOption.setRequired(true);
        criteriaOption.addOption(Option.builder("d").longOpt("diagonals")
                .desc("Extract diagonals.").build());
        criteriaOption.addOption(Option.builder("r").longOpt("row").hasArg().argName("iRow")
                .desc("Extract i-th row.").build());
        criteriaOption.addOption(Option.builder("c").longOpt("column").hasArg().argName("iColumn")
                .desc("Extract i-th column.").build());
        options.addOptionGroup(criteriaOption);

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine (CommandLine) Options specified by user.
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path unknownsPath = Paths.get(cmdLine.getOptionValue("u"));
        VariableType variable = VariableType.valueOf(cmdLine.getOptionValue("v"));
        Path matrixPath = Paths.get(cmdLine.getOptionValue("m"));
        boolean diagonals = cmdLine.hasOption("d");
        int iRow = cmdLine.hasOption("r") ? Integer.parseInt(cmdLine.getOptionValue("r")) : -1;
        int iColumn = cmdLine.hasOption("c") ? Integer.parseInt(cmdLine.getOptionValue("c")) : -1;
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = ScalarListFile.generateFilePath(Paths.get(""), null, ScalarType.ABSOLUTE, fileTag, appendFileDate, null);

        // read parameter information and matrix
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        List<String> lines = Files.readAllLines(matrixPath);
        if (lines.size() != parameterList.size())
            throw new IllegalStateException("Unknowns and ATA do not match.");
        double[][] values = new double[lines.size()][lines.size()];
        for (int i = 0; i < parameterList.size(); i++) {
            String[] entries = lines.get(i).split("\\s+");
            for (int j = 0; j < parameterList.size(); j++) {
                values[i][j] = Double.parseDouble(entries[j]);
            }
        }

        // extract specified components of the matrix
        Map<FullPosition, Double> scalarMap = new LinkedHashMap<>();
        for (int i = 0; i < parameterList.size(); i++) {
            UnknownParameter unknown = parameterList.get(i);
            if (!unknown.getVariableType().equals(variable)) continue;

            // get value from specified column
            double value;
            if (diagonals) value = values[i][i];
            else if (iRow >= 0) value = values[iRow][i];
            else if (iColumn >= 0) value = values[i][iColumn];
            else throw new IllegalArgumentException("Criteria of components to extract must be set.");

            scalarMap.put(unknown.getPosition(), value);
        }
        ScalarListFile.write(scalarMap, outputPath);
    }

}
