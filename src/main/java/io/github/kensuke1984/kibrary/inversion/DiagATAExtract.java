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
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Class to extract diagonal components of ATA matrix.
 *
 * @author otsuru
 * @since 2023/6/29
 */
public class DiagATAExtract {

    /**
     * Extract diagonal components of ATA matrix.
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

        options.addOption(Option.builder("u").longOpt("unknowns").hasArg().argName("unknownParameterFile").required()
                .desc("Path of unknown parameter file.").build());
        options.addOption(Option.builder("a").longOpt("ata").hasArg().argName("ataFile").required()
                .desc("Path of ATA file.").build());
        options.addOption(Option.builder("v").longOpt("variable").hasArg().argName("variableType").required()
                .desc("Variable type.").build());
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
        Path unknownsPath = Paths.get(cmdLine.getOptionValue("u"));
        Path ataPath = Paths.get(cmdLine.getOptionValue("a"));
        VariableType variable = VariableType.valueOf(cmdLine.getOptionValue("v"));
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "diagATA", fileTag, appendFileDate, null, ".lst");

        // read parameter information and ATA
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        List<String> lines = Files.readAllLines(ataPath);
        if (lines.size() != parameterList.size())
            throw new IllegalStateException("Unknowns and ATA do not match.");
        double[][] values = new double[lines.size()][lines.size()];
        for (int i = 0; i < parameterList.size(); i++) {
            String[] entries = lines.get(i).split("\\s+");
            for (int j = 0; j < parameterList.size(); j++) {
                values[i][j] = Double.parseDouble(entries[j]);
            }
        }

        // extract diagonal components of ATA
        Map<FullPosition, Double> diagATAMap = new LinkedHashMap<>();
        for (int i = 0; i < parameterList.size(); i++) {
            UnknownParameter unknown = parameterList.get(i);
            if (!unknown.getVariableType().equals(variable))
                continue;
            double diagonal = values[i][i];
            diagATAMap.put(unknown.getPosition(), diagonal);
        }
        PerturbationListFile.write(diagATAMap, outputPath);
    }

}
