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
import io.github.kensuke1984.kibrary.util.ThreadAid;

/**
 * Constructs the dataset from downloaded mseed or seed files.
 * In case of mseed files, StationXML files will be downloaded, and RESP files will be created from them.
 * In case of seed files, RESP files will be created directly.
 * SAC file names will be formatted, and information of the event and station will be written in SAC file headers.
 * <p>
 * The input mseed files must be in "eventDir/mseed/" and seed files in "eventDir/seed/" under the current directory.
 * All mseed files must be for the same datacenter.
 * Output SAC, StationXML, and RESP files will each be placed in "eventDir/sac", "eventDir/station", and "eventDir/resp".
 *
 * @since 2021/11/17
 * @author otsuru
 */
public class DataAligner {
    private final boolean forSeed;
    private final String datacenter;

    /**
     * Number of processed event folders
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
                .desc("Operate for mseed files, and download from the specified datacenter, chosen from {IRIS, ORFEUS}")
                .build());
        inputOption.addOption(Option.builder("s").longOpt("seed")
                .desc("Operate for seed files").build());
        options.addOptionGroup(inputOption);

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

        DataAligner aligner = new DataAligner(forSeed, datacenter);
        aligner.align();
    }

    private DataAligner(boolean forSeed, String datacenter) {
        this.forSeed = forSeed;
        this.datacenter = datacenter;
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
                    if (!edp.openMseeds()) {
                        // if open fails, skip the event
                        return;
                    }
                    edp.downloadXmlMseed(datacenter);
                }
            } catch (IOException e) {
                // Here, suppress exceptions for events that failed, and move on to the next event.
                System.err.println("!!! Operation for " + eventDir + " failed, skipping.");
                e.printStackTrace();
            }
        });

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Straightening SAC files ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + Math.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "% of events done");
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
