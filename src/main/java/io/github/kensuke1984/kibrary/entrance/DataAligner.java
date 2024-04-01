package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;

/**
 * Constructs the dataset from downloaded mseed or seed files.
 * In case of mseed files, StationXML files will be downloaded, and RESP files will be created from them.
 * In case of seed files, RESP files will be created directly.
 * SAC file names will be formatted, and information of the event and station will be written in SAC file headers.
 * <p>
 * The input mseed files must be in "eventDir/mseed/" and seed files in "eventDir/seed/" under the current directory.
 * All mseed files must be for the same datacenter.
 * Output SAC, StationXML, and RESP files will each be placed in "eventDir/sa/c", "eventDir/station/", and "eventDir/resp/".
 * <p>
 * <ul>
 * <li>
 * In normal mode, opening mseed/seed will always be done, regardless of whether they have already been opened before.
 * Download of stationXML files will be skipped if they already exist.
 * <li>
 * In mode with "-d" option (only for mseeds), opening mseed will not be done.
 * Download of stationXML files will be done for all SAC files left in "eventDir/mseed/", even if they already exist.
 * Use this mode when configuration has failed for some SACs due to broken stationXML files.
 * <li>
 * In mode with "-c" option, opening mseed/seed and download of stationXML files will not be done.
 * Use this mode when the program stopped after opening & downloading (= the single-thread part)
 * but before configuring (= the parallelized part).
 * </ul>
 *
 * @since 2021/11/17
 * @author otsuru
 */
public class DataAligner {

    private final boolean forSeed;
    private final String datacenter;
    private final boolean fromDownload;
    private final boolean fromConfigure;

    /**
     * Number of processed event folders.
     */
    private AtomicInteger processedFolders = new AtomicInteger();

    /**
     * Constructs the dataset from downloaded mseed or seed files.
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

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.setRequired(true);
        inputOption.addOption(Option.builder("m").longOpt("mseed").hasArg().argName("datacenter")
                .desc("Operate for mseed files, and download from the specified datacenter, chosen from {IRIS, ORFEUS}.")
                .build());
        inputOption.addOption(Option.builder("s").longOpt("seed")
                .desc("Operate for seed files.").build());
        options.addOptionGroup(inputOption);

        // option
        options.addOption(Option.builder("d").longOpt("fromDownload")
                .desc("Whether to redo from stationXML downloads for all unconfigured SACs, without opening mseed. Only for mseed mode.").build());
        options.addOption(Option.builder("c").longOpt("fromConfigure")
                .desc("Whether to redo from SAC configuration, without opening seed/mseed or downloading stationXMLs.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        boolean forSeed = false;
        String datacenter = "";

        if (cmdLine.hasOption("s")) {
            forSeed = true;
        } else if (cmdLine.hasOption("m")) {
            datacenter = cmdLine.getOptionValue("m");
        } else {
            throw new IllegalArgumentException("Invalid arguments.");
        }

        // check redo mode
        boolean fromDownload = false;
        if (cmdLine.hasOption("d")) {
            if (forSeed) throw new IllegalArgumentException("The -d option is only for mseed.");
            else fromDownload = true;
        }
        boolean fromConfigure = cmdLine.hasOption("c");

        DataAligner aligner = new DataAligner(forSeed, datacenter, fromDownload, fromConfigure);
        aligner.align();
    }

    private DataAligner(boolean forSeed, String datacenter, boolean fromDownload, boolean fromConfigure) {
        this.forSeed = forSeed;
        this.datacenter = datacenter;
        this.fromDownload = fromDownload;
        this.fromConfigure = fromConfigure;
    }

    private void align() throws IOException {

        // working directory is set to current directory
        Path workPath = Paths.get(".");

        // import event directories in working directory
        Set<EventFolder> eventDirs = DatasetAid.eventFolderSet(workPath);
        int n_total = eventDirs.size();
        if (!DatasetAid.checkNum(n_total, "event", "events")) {
            return;
        }

        // for each event directory
        // This part is not parallelized because SocketException occurs when many threads download files simultaneously.
        if (!fromConfigure) {
            final AtomicInteger n = new AtomicInteger();
            eventDirs.stream().sorted().forEach(eventDir -> {
                try {
                    n.incrementAndGet();
                    System.err.println(eventDir + " (# " + n + " of " + n_total + ")");

                    // create new instance for the event
                    EventDataPreparer edp = new EventDataPreparer(eventDir);

                    if (forSeed) {
                        if (!edp.openSeeds()) {
                            // if open fails, skip the event
                            return;
                        }
                    } else {
                        if (!fromDownload) {
                            if (!edp.openMseeds()) {
                                // if open fails, skip the event
                                return;
                            }
                        }
                        edp.downloadXmlMseed(datacenter, fromDownload);
                    }
                } catch (IOException e) {
                    // Here, suppress exceptions for events that failed, and move on to the next event.
                    System.err.println("!!! Operation for " + eventDir + " failed, skipping.");
                    e.printStackTrace();
                }
            });
        }

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Straightening SAC files ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + MathAid.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "% of events done");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all events.");
    }

    private Runnable process(EventFolder eventDir) {
        return () -> {
            try {
                EventDataPreparer edp = new EventDataPreparer(eventDir);
                if (forSeed) {
                    edp.organizeFilesSeed();
                } else {
                    edp.configureFilesMseed();
                }
            } catch (Exception e) {
                System.err.println("!!! Error on " + eventDir);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
    }


}
