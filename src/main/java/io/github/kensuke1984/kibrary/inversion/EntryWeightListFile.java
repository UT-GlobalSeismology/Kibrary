package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.RecordEntry;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * File containing list of weights (or amplitudes) for each {@link DataEntry}.
 * <p>
 * Each line: globalCMTID station network latitude longitude component weight
 * <p>
 * Here, "station network latitude longitude" is for the observer.
 *
 * @author otsuru
 * @since 2024/3/22
 */
public class EntryWeightListFile {

    public static void write(Map<RecordEntry, Double> weightMap, Path outputPath, OpenOption... options) throws IOException {
        DatasetAid.printNumOutput(weightMap.size(), "data entry weight", "data entry weights", outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# globalCMTID station network latitude longitude component weight");
            weightMap.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey()))
                    .forEach(entry -> pw.println(entry.getKey().toString() + " " + entry.getValue()));
        }
    }

    public static Map<RecordEntry, Double> read(Path inputPath) throws IOException {
        Map<RecordEntry, Double> weightMap = new HashMap<>();

        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while (reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            GlobalCMTID event = new GlobalCMTID(parts[0]);
            HorizontalPosition hp = new HorizontalPosition(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
            Observer observer = new Observer(parts[1], parts[2], hp);
            SACComponent component = SACComponent.valueOf(parts[5]);
            Phase[] phases = Arrays.stream(parts[6].split(",")).map(Phase::create).toArray(Phase[]::new);
            double weight = Double.parseDouble(parts[7]);

            RecordEntry entry = new RecordEntry(event, observer, component, phases);
            weightMap.put(entry, weight);
        }

        DatasetAid.printNumInput(weightMap.size(), "data entry weight", "data entry weights", inputPath);
        return Collections.unmodifiableMap(weightMap);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads basicIDs and creates an entry amplitude list file under the working folder.
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
        options.addOption(Option.builder("p").longOpt("phases")
                .desc("Name of phases to use to weight, listed using comma. (S,ScS)").build());
        options.addOption(Option.builder("S").longOpt("syn")
                .desc("Use amplitude of synthetic waveform (instead of observed waveform).").build());

        // input
        options.addOption(Option.builder("b").longOpt("basic").hasArg().argName("basicFolder")
                .desc("Path of basic waveform folder. (.)").build());

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
        Set<SACComponent> components = cmdLine.hasOption("c")
                ? Arrays.stream(cmdLine.getOptionValue("c").split(",")).map(SACComponent::valueOf).collect(Collectors.toSet())
                : SACComponent.componentSetOf("ZRT");
        Phase[] phases = cmdLine.hasOption("p")
                ? Arrays.stream(cmdLine.getOptionValue("p").split(",")).map(Phase::create).toArray(Phase[]::new)
                : Phase.phaseArrayOf("S,ScS");
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        boolean useSynAmp = cmdLine.hasOption("S");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "entryAmplitude", fileTag, appendFileDate, null, ".lst");

        // read input basic folder
        Path basicPath = cmdLine.hasOption("b") ? Paths.get(cmdLine.getOptionValue("b")) : Paths.get(".");
        List<BasicID> ids = BasicIDFile.read(basicPath, true);
        if (useSynAmp) {
            ids = ids.stream()
                    .filter(id -> components.contains(id.getSacComponent()) && phases.equals(id.getPhases()) && id.getWaveformType() == WaveformType.SYN)
                    .collect(Collectors.toList());
        } else {
            ids = ids.stream()
                    .filter(id -> components.contains(id.getSacComponent()) && phases.equals(id.getPhases()) && id.getWaveformType() == WaveformType.OBS)
                    .collect(Collectors.toList());
        }
        System.err.println(" Using " + ids.size() + " ids.");

        // find amplitude of each entry
        Map<RecordEntry, Double> amplitudeMap = new HashMap<>();
        for (BasicID id : ids) {
            RecordEntry entry = new RecordEntry(id.getGlobalCMTID(), id.getObserver(), id.getSacComponent(), id.getPhases());
            RealVector dataVector = new ArrayRealVector(id.getData());
            double amplitude = dataVector.getLInfNorm();
            amplitudeMap.put(entry, amplitude);
        }

        // output
        write(amplitudeMap, outputPath);
    }

}
