package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

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
     * @param args [option]
     * <ul>
     * <li> -s : operate for seed files</li>
     * <li> -m datacenter : operate for mseed files, and download from the specified datacenter</li>
     * </ul>
     * You must specify one or the other.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println("-----");
            usage().forEach(System.err::println);
        }
    }

    public static List<String> usage() {
        List<String> usageList = new ArrayList<>();
        usageList.add("Usage:");
        usageList.add(" [-s] : operate for seed files");
        usageList.add(" [-m datacenter] : operate for mseed files, and download from the specified datacenter");
        usageList.add("   Choose datacenter from IRIS, ORFEUS.");
        usageList.add(" You must specify one option or the other.");
        return usageList;
    }

    public static void run(String[] args) throws IOException {
        boolean forSeed = false;
        String datacenter = "";

        if (args.length == 1 && args[0].equals("-s")) {
            forSeed = true;
        } else if (args.length == 2 && args[0].equals("-m")) {
            datacenter = args[1];
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
        if (!DatasetAid.checkEventNum(n_total)) {
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

    private AtomicInteger processedFolders = new AtomicInteger(); // already processed

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
