package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.github.kensuke1984.kibrary.aid.ThreadAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;

public class DataPolisher {
    private final boolean forSeed;
    private final String datacenter;

    /**
     * A method to expand existing mseed files and download associated STATION and RESP files.
     * The input mseed files must be in event directories under the current directory.
     * Output files will be placed in each input event directory.
     * @param args [option]
     * <ul>
     * <li> -s : operate for seed files</li>
     * <li> -m datacenter : operate for mseed files, and download from the specified datacenter</li>
     * </ul>
     * You must specify one or the other.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        boolean forSeed = false;
        String datacenter = "";

        if (args.length == 1 && args[0].equals("-s")) {
            forSeed = true;;
        } else if (args.length == 2 && args[0].equals("-m")) {
            datacenter = args[1];
        } else {
            System.err.println("Usage:");
            System.err.println(" [-s] : operate for seed files");
            System.err.println(" [-m datacenter] : operate for mseed files, and download from the specified datacenter");
            System.err.println("You must specify one option or the other.");
            return;
        }

        DataPolisher polisher = new DataPolisher(forSeed, datacenter);
        polisher.polish();

    }

    private DataPolisher(boolean forSeed, String datacenter) {
        this.forSeed = forSeed;
        this.datacenter = datacenter;
    }

    private void polish() throws IOException {

        // working directory is set to current directory
        Path workPath = Paths.get("");

        // import event directories in working directory
        Set<EventFolder> eventDirs = Utilities.eventFolderSet(workPath);
        if (eventDirs.isEmpty()) {
            System.err.println("No events found.");
            return;
        } else if (eventDirs.size() == 1) {
            System.err.println(eventDirs.size() + " event is found.");
        } else {
            System.err.println(eventDirs.size() + " events are found.");
        }

        // for each event directory
        for (EventFolder eventDir : eventDirs) {
            // create new instance for the event
            EventDataPreparer edp = new EventDataPreparer(eventDir);

            if (forSeed) {
                if (!edp.openSeeds()) {
                    // if open fails, skip the event
                    continue;
                }
            } else {
                if (!edp.openMseeds()) {
                    // if open fails, skip the event
                    continue;
                }
                edp.downloadXmlMseed(datacenter);
            }
        }

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Finishing up ...");
        while (!es.isTerminated()) {
            ThreadAid.sleep(100);
        }
        System.err.println("Finished!");
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
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            }
        };
    }


}
