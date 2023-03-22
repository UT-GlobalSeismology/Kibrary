package io.github.kensuke1984.kibrary.abandon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import io.github.kensuke1984.kibrary.Summon;

/**
 * Class to clean threeDPartial folders when they are not needed any more.
 * This deletes all files under FPpool/modelName/ and BPpool/modelName/ (which should all be SPC files).
 * @author otsuru
 * @since 2023/3/21
 */
public class ThreeDPartialCleanup {

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

        options.addOption(Option.builder("d").longOpt("delete").required()
                .desc("Delete spc files in FP and BP folders").build());
        options.addOption(Option.builder("f").hasArg().argName("fpPath")
                .desc("Path of FP folder (FPpool)").build());
        options.addOption(Option.builder("b").hasArg().argName("bpPath")
                .desc("Path of BP folder (BPpool)").build());
        options.addOption(Option.builder("m").hasArg().argName("model")
                .desc("Model name (prem)").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        if (!cmdLine.hasOption("d")) return;

        Path fpPath = cmdLine.hasOption("f") ? Paths.get(cmdLine.getOptionValue("f")) : Paths.get("FPpool");
        Path bpPath = cmdLine.hasOption("b") ? Paths.get(cmdLine.getOptionValue("b")) : Paths.get("BPpool");
        String modelName = cmdLine.hasOption("m") ?cmdLine.getOptionValue("m") : "PREM";

        // clean FP folder
        System.err.println("Cleaning " + fpPath);
        Set<Path> fpModelFolders = collectModelFolders(fpPath, modelName);
        for (Path modelFolder : fpModelFolders) {
            FileUtils.cleanDirectory(modelFolder.toFile());
        }

        // clean BP folder
        System.err.println("Cleaning " + bpPath);
        Set<Path> bpModelFolders = collectModelFolders(bpPath, modelName);
        for (Path modelFolder : bpModelFolders) {
            FileUtils.cleanDirectory(modelFolder.toFile());
        }

    }

    private static Set<Path> collectModelFolders(Path inPath, String modelName) throws IOException {
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(inPath)) {
            return stream.filter(path -> Files.isDirectory(path)).map(path -> path.resolve(modelName)).collect(Collectors.toSet());
        }
    }
}
