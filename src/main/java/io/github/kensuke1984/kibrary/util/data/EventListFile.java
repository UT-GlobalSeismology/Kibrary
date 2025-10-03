package io.github.kensuke1984.kibrary.util.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
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
 * @since a long time ago
 * @version 2022/4/22 Renamed from statistics.EventInformationFile to util.data.EventListFile.
 */
public class EventListFile {
    private EventListFile() {}

    /**
     * Writes an event list file given a set of {@link GlobalCMTID}s.
     * @param eventSet (Set of {@link GlobalCMTID}) Events.
     * @param outputPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<GlobalCMTID> eventSet, Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(eventSet.size(), "event", "events")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# GCMTID latitude longitude radius depth");
            eventSet.stream().sorted().forEach(event -> {
                pw.println(event.toPaddedString() + " " + event.getEventData().getCmtPosition().toString()
                         + " " + event.getEventData().getCmtPosition().getDepth());
            });
        }
    }

    /**
     * Writes an event list file with full information, given a set of {@link GlobalCMTID}s.
     * @param eventSet (Set of {@link GlobalCMTID}) Events.
     * @param outputPath (Path) Output file.
     * @param options (OpenOption...) Options for write.
     * @throws IOException if an I/O error occurs
     */
    public static void writeFullInfo(Set<GlobalCMTID> eventSet, Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Outputting "
                + MathAid.switchSingularPlural(eventSet.size(), "event", "events")
                + " in " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# GCMTID date time latitude longitude radius depth Mw HalfDuration");
            eventSet.stream().sorted().forEach(event -> {
                pw.println(event.toPaddedString()
                        + " " + event.getEventData().getCMTTime().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SS"))
                        + " " + event.getEventData().getCmtPosition().toString()
                        + " " + event.getEventData().getCmtPosition().getDepth()
                        + " " + event.getEventData().getCmt().getMw() + " " + event.getEventData().getHalfDuration());
            });
        }
    }

    /**
     * Reads an event list file. Only the GlobalCMTID is read in; other information is ignored.
     * @param inputPath (Path) Event information file.
     * @return (<b>unmodifiable</b> Set of {@link GlobalCMTID}) Events.
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads event information from an input source
     * and creates an event list file under the working folder.
     * The input source may be SAC files in event directories under a dataset folder,
     * a timewindow file, or a basic ID file.
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

        // option
        options.addOption(Option.builder("f").longOpt("full")
                .desc("Whether to write full information of events in output file.").build());

        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Whether to omit date string in output file name.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "event", fileTag, appendFileDate, GadgetAid.getTemporaryString(), ".lst");

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

        if (cmdLine.hasOption("f")) writeFullInfo(eventSet, outputPath);
        else write(eventSet, outputPath);
    }

}
