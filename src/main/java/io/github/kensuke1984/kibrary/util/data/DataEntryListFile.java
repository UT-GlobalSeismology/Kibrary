package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * File containing list of {@link DataEntry}s.
 * <p>
 * Each line: globalCMTID station network latitude longitude component
 * <p>
 * Here, "station network latitude longitude" is for the observer.
 *
 * @author otsuru
 * @since 2022/5/25
 */
public class DataEntryListFile {

    public static void writeFromMap(Map<GlobalCMTID, Set<DataEntry>> entryMap, Path outputPath, OpenOption... options) throws IOException {
        Set<DataEntry> entrySet = new HashSet<>();
        for (GlobalCMTID event : entryMap.keySet()) {
            entrySet.addAll(entryMap.get(event));
        }
        writeFromSet(entrySet, outputPath, options);
    }

    public static void writeFromSet(Set<DataEntry> entrySet, Path outputPath, OpenOption... options) throws IOException {
        DatasetAid.printNumOutput(entrySet.size(), "data entry", "data entries", outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# globalCMTID station network latitude longitude component");
            entrySet.stream().sorted().forEach(entry -> pw.println(entry.toString()));
        }
    }

    public static Map<GlobalCMTID, Set<DataEntry>> readAsMap(Path inputPath) throws IOException {
        Set<DataEntry> entrySet = readAsSet(inputPath);

        // rearrange Set into Map
        Map<GlobalCMTID, Set<DataEntry>> entryMap = new HashMap<>();
        for (DataEntry entry : entrySet) {
            GlobalCMTID event = entry.getEvent();
            if (!entryMap.containsKey(event)) entryMap.put(event, new HashSet<>());
            entryMap.get(event).add(entry);
        }
        return Collections.unmodifiableMap(entryMap);
    }

    public static Set<DataEntry> readAsSet(Path inputPath) throws IOException {
        Set<DataEntry> entrySet = new HashSet<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            GlobalCMTID event = new GlobalCMTID(parts[0]);
            HorizontalPosition hp = new HorizontalPosition(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
            Observer observer = new Observer(parts[1], parts[2], hp);
            SACComponent component = SACComponent.valueOf(parts[5]);

            DataEntry entry = new DataEntry(event, observer, component);
            if (!entrySet.add(entry))
                throw new RuntimeException("There is duplication of " + entry + " in " + inputPath + ".");
        }

        DatasetAid.printNumInput(entrySet.size(), "data entry", "data entries", inputPath);
        return Collections.unmodifiableSet(entrySet);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads dataset information from an input source
     * and creates a data entry list file under the working folder.
     * The input source may be SAC files in event directories under a dataset folder,
     * a timewindow file, or a basic waveform folder.
     * @param args Options.
     * @throws IOException if an I/O error occurs
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

        // settings
        options.addOption(Option.builder("c").longOpt("components").hasArg().argName("components")
                .desc("Components to use, listed using commas.").build());

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("d").longOpt("dataset").hasArg().argName("datasetFolder")
                .desc("Use dataset folder containing event folders as input.").build());
        inputOption.addOption(Option.builder("t").longOpt("timewindow").hasArg().argName("timewindowFile")
                .desc("Use timewindow file as input.").build());
        inputOption.addOption(Option.builder("b").longOpt("basic").hasArg().argName("basicFolder")
                .desc("Use basic waveform folder as input.").build());
        inputOption.addOption(Option.builder("e").longOpt("entry").hasArg().argName("dataEntryFile")
                .desc("Use data entry file as input.").build());
        options.addOptionGroup(inputOption);

        // output
        options.addOption(Option.builder("n").longOpt("number")
                .desc("Just count number without creating output files.").build());
        options.addOption(Option.builder("x").longOpt("convert").hasArg().argName("components")
                .desc("To convert entries to certain components, list them using commas.").build());
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
        Set<SACComponent> components = cmdLine.hasOption("c")
                ? Arrays.stream(cmdLine.getOptionValue("c").split(",")).map(SACComponent::valueOf).collect(Collectors.toSet())
                : SACComponent.componentSetOf("ZRT");
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "dataEntry", fileTag, appendFileDate, null, ".lst");

        // read input
        Set<DataEntry> entrySet;
        if (cmdLine.hasOption("d")) {
            entrySet = collectFromDataset(Paths.get(cmdLine.getOptionValue("d")), components);
        } else if (cmdLine.hasOption("t")) {
            Set<TimewindowData> timewindows = TimewindowDataFile.read(Paths.get(cmdLine.getOptionValue("t")));
            entrySet = timewindows.stream().filter(timewindow -> components.contains(timewindow.getComponent()))
                    .map(timewindow -> new DataEntry(timewindow.getGlobalCMTID(), timewindow.getObserver(), timewindow.getComponent()))
                    .collect(Collectors.toSet());
        } else if (cmdLine.hasOption("b")) {
            List<BasicID> basicIDs = BasicIDFile.read(Paths.get(cmdLine.getOptionValue("b")), false);
            entrySet = basicIDs.stream().filter(id -> components.contains(id.getSacComponent()))
                    .map(id -> new DataEntry(id.getGlobalCMTID(), id.getObserver(), id.getSacComponent()))
                    .collect(Collectors.toSet());
        } else if (cmdLine.hasOption("e")) {
            entrySet = DataEntryListFile.readAsSet(Paths.get(cmdLine.getOptionValue("e")))
                    .stream().filter(entry -> components.contains(entry.getComponent())).collect(Collectors.toSet());
        } else {
            String pathString = "";
            Path inPath;
            do {
                pathString = GadgetAid.readInputDialogOrLine("Input folder?", pathString);
                if (pathString == null || pathString.isEmpty()) return;
                inPath = Paths.get(pathString);
            } while (!Files.exists(inPath) || !Files.isDirectory(inPath));
            entrySet = collectFromDataset(inPath, components);
        }

        System.err.println(MathAid.switchSingularPlural(entrySet.size(), "data entry", "data entries") + " created.");
        if (entrySet.size() == 0) return;
        if (cmdLine.hasOption("n")) return;

        // convert components
        if (cmdLine.hasOption("x")) {
            Set<DataEntry> newEntrySet = new HashSet<>();
            Set<SACComponent> xComponents = Arrays.stream(cmdLine.getOptionValue("x").split(","))
                    .map(SACComponent::valueOf).collect(Collectors.toSet());
            for (SACComponent component : xComponents) {
                entrySet.stream().map(entry -> new DataEntry(entry.getEvent(), entry.getObserver(), component)).forEach(newEntrySet::add);
            }
            entrySet = newEntrySet;
        }

        // output
        writeFromSet(entrySet, outputPath);
    }

    private static Set<DataEntry> collectFromDataset(Path datasetPath, Set<SACComponent> components) throws IOException {
        Set<SACFileName> sacNameSet = DatasetAid.sacFileNameSet(datasetPath);
        return sacNameSet.stream().filter(sacname -> components.contains(sacname.getComponent()))
                .map(sacname -> sacname.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                .map(header -> header.toDataEntry())
                .collect(Collectors.toSet());
    }

}
