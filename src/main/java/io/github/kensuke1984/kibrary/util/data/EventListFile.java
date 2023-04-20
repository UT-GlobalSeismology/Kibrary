package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * File containing list of events. See {@link GlobalCMTID}.
 * <p>
 * Each line: globalCMTID, latitude, longitude, radius.
 * <p>
 * Only the globalCMTID is the part used to convey data;
 * the rest of the information is just for the users to see.
 *
 * @author ???
 * @since a long time ago
 */
public class EventListFile {
    private EventListFile() {}

    /**
     * Writes an event list file given a set of GlobalCMTIDs.
     * @param eventSet Set of events
     * @param outputPath  of write file
     * @param options  for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<GlobalCMTID> eventSet, Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(eventSet.size(), "event", "events")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# GCMTID latitude longitude radius depth Mw");
            eventSet.stream().sorted().forEach(event -> {
                pw.println(event.toPaddedString() + " " + event.getEventData().getCmtPosition()
                         + " " + event.getEventData().getCmtPosition().getDepth() + " " + event.getEventData().getCmt().getMw());
            });
        }
    }

    /**
     * Reads an event list file. Only the GlobalCMTID is read in; other information are ignored.
     * @param inputPath of event information file
     * @return (<b>unmodifiable</b>) Set of events
     * @throws IOException if an I/O error occurs
     *
     * @author otsuru
     * @since 2022/2/5
     */
    public static Set<GlobalCMTID> read(Path inputPath) throws IOException {
        Set<GlobalCMTID> eventSet = new HashSet<>();
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while(reader.hasNext()) {
            String[] parts = reader.next().split("\\s+");
            GlobalCMTID event = new GlobalCMTID(parts[0]);
            if (!eventSet.add(event))
                throw new RuntimeException("There is duplication of " + event + " in " + inputPath + ".");
        }

        DatasetAid.checkNum(eventSet.size(), "event", "events");
        return Collections.unmodifiableSet(eventSet);
    }

    /**
     * Reads event information from an input source
     * and creates an event list file under the working folder.
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

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("d").longOpt("dataset").hasArg().argName("datasetFolder")
                .desc("Use dataset folder containing event folders as input").build());
        inputOption.addOption(Option.builder("e").longOpt("entry").hasArg().argName("dataEntryFile")
                .desc("Use data entry file as input").build());
        inputOption.addOption(Option.builder("t").longOpt("timewindow").hasArg().argName("timewindowFile")
                .desc("Use timewindow file as input").build());
        inputOption.addOption(Option.builder("b").longOpt("basic").hasArg().argName("basicFolder")
                .desc("Use basic waveform folder as input").build());
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

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("event" + GadgetAid.getTemporaryString() + ".lst");

        Set<GlobalCMTID> eventSet;
        if (cmdLine.hasOption("d")) {
            eventSet = DatasetAid.globalCMTIDSet(Paths.get(cmdLine.getOptionValue("d")));
        } else if (cmdLine.hasOption("e")) {
            Set<DataEntry> entries = DataEntryListFile.readAsSet(Paths.get(cmdLine.getOptionValue("e")));
            eventSet = entries.stream().map(DataEntry::getEvent).collect(Collectors.toSet());
        } else if (cmdLine.hasOption("t")) {
            Set<TimewindowData> timewindows =  TimewindowDataFile.read(Paths.get(cmdLine.getOptionValue("t")));
            eventSet = timewindows.stream().map(TimewindowData::getGlobalCMTID).collect(Collectors.toSet());
        } else if (cmdLine.hasOption("b")) {
            List<BasicID> basicIDs =  BasicIDFile.read(Paths.get(cmdLine.getOptionValue("b")), false);
            eventSet = basicIDs.stream().map(BasicID::getGlobalCMTID).collect(Collectors.toSet());
        } else {
            String pathString = "";
            Path inPath;
            do {
                pathString = GadgetAid.readInputDialogOrLine("Input folder?", pathString);
                if (pathString == null || pathString.isEmpty()) return;
                inPath = Paths.get(pathString);
            } while (!Files.exists(inPath) || !Files.isDirectory(inPath));
            eventSet = DatasetAid.globalCMTIDSet(inPath);
        }

        if (!DatasetAid.checkNum(eventSet.size(), "event", "events")) return;
        write(eventSet, outputPath);
    }

}
