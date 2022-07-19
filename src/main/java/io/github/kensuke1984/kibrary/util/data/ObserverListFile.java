package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * File containing list of observers.
 * <p>
 * Each line: station code, network code, latitude, longitude.
 * <p>
 * Only the station, network, latitude, and longitude are the parts used to convey data;
 * the rest of the information is just for the users to see.
 *
 * @author Kensuke Konishi
 * @version 0.2.0.4
 */
public final class ObserverListFile {
    private ObserverListFile() {}

    /**
     * Writes an observer list file given a set of Observers.
     * @param observerSet Set of observers
     * @param outputPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<Observer> observerSet, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# station network latitude longitude");
            observerSet.stream().sorted().forEach(observer -> {
                pw.println(observer.toPaddedInfoString());
            });
        }
    }

    /**
     * Reads an observer list file.
     * Only the station, network, latitude, and longitude is read in; other information are ignored.
     * @param inputPath of station list file
     * @return (<b>unmodifiable</b>) Set of stations
     * @throws IOException if an I/O error occurs
     */
    public static Set<Observer> read(Path inputPath) throws IOException {
        Set<Observer> observerSet = new HashSet<>();
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while(reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            HorizontalPosition hp = new HorizontalPosition(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            Observer observer = new Observer(parts[0], parts[1], hp);
            if (!observerSet.add(observer))
                throw new RuntimeException("There is duplication of " + observer + " in " + inputPath + ".");
        }

//        // If there are observers with same name and different position, write them in standard output. TODO: should become unneeded?
//        if (observerSet.size() != observerSet.stream().map(Observer::toString).distinct().count()){
//            System.err.println("CAUTION!! Observers with same station and network but different positions detected!");
//            Map<String, List<Observer>> nameToObserver = new HashMap<>();
//            observerSet.forEach(sta -> {
//                if (nameToObserver.containsKey(sta.toString())) {
//                    List<Observer> tmp = nameToObserver.get(sta.toString());
//                    tmp.add(sta);
//                    nameToObserver.put(sta.toString(), tmp);
//                }
//                else {
//                    List<Observer> tmp = new ArrayList<>();
//                    tmp.add(sta);
//                    nameToObserver.put(sta.toString(), tmp);
//                }
//            });
//            nameToObserver.forEach((name, obs) -> {
//                if (obs.size() > 1) {
//                    obs.stream().forEach(s -> System.out.println(s + " " + s.getPosition()));
//                }
//            });
//        }

        DatasetAid.checkNum(observerSet.size(), "observer", "observers");
        return Collections.unmodifiableSet(observerSet);
    }

    /**
     * Reads observer information from an input source
     * and creates an observer list file under the working folder.
     * The input source may be SAC files in event directories under a dataset folder,
     * a timewindow file, or a basic ID file.
     *
     * @param args
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
                .desc("Components to use, listed using commas").build());

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("d").longOpt("dataset").hasArg().argName("datasetFolder")
                .desc("Use dataset folder containing event folders as input").build());
        inputOption.addOption(Option.builder("t").longOpt("timewindow").hasArg().argName("timewindowFile")
                .desc("Use timewindow file as input").build());
        inputOption.addOption(Option.builder("b").longOpt("basicID").hasArg().argName("basicIDFile")
                .desc("Use basic ID file as input").build());
        options.addOptionGroup(inputOption);

        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());

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

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("observer" + GadgetAid.getTemporaryString() + ".lst");

        Set<Observer> observerSet;
        if (cmdLine.hasOption("d")) {
            observerSet = collectFromDataset(Paths.get(cmdLine.getOptionValue("d")), components);
        } else if (cmdLine.hasOption("t")) {
            Set<TimewindowData> timewindows =  TimewindowDataFile.read(Paths.get(cmdLine.getOptionValue("t")));
            observerSet = timewindows.stream().filter(timewindow -> components.contains(timewindow.getComponent()))
                    .map(TimewindowData::getObserver).collect(Collectors.toSet());
        } else if (cmdLine.hasOption("b")) {
            BasicID[] basicIDs =  BasicIDFile.read(Paths.get(cmdLine.getOptionValue("b")));
            observerSet = Arrays.stream(basicIDs).filter(id -> components.contains(id.getSacComponent()))
                    .map(BasicID::getObserver).collect(Collectors.toSet());
        } else {
            String pathString = "";
            Path inPath;
            do {
                pathString = GadgetAid.readInputDialogOrLine("Input folder?", pathString);
                if (pathString == null || pathString.isEmpty()) return;
                inPath = Paths.get(pathString);
            } while (!Files.exists(inPath) || !Files.isDirectory(inPath));
            observerSet = collectFromDataset(inPath, components);
        }

        if (!DatasetAid.checkNum(observerSet.size(), "observer", "observers")) return;
        write(observerSet, outputPath);
    }

    private static Set<Observer> collectFromDataset(Path datasetPath, Set<SACComponent> components) throws IOException {
        Set<SACFileName> sacNameSet = DatasetAid.sacFileNameSet(datasetPath);
        return sacNameSet.stream().filter(sacname -> components.contains(sacname.getComponent()))
                .map(sacname -> sacname.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                .map(Observer::of).collect(Collectors.toSet());
    }

}
