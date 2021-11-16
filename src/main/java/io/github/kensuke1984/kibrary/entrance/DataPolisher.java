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

    /**
     * A method to expand existing mseed files and download associated STATION and RESP files.
     * The input mseed files must be in event directories under the current directory.
     * Output files will be placed in each input event directory.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        DataPolisher polisher = new DataPolisher();
        polisher.polish();

    }

    private DataPolisher() {
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

            edp.openMseeds();
            edp.downloadXmlMseed("IRIS");

            edp.openSeeds();
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
                edp.configureFilesMseed();
                edp.organizeFilesSeed();
            } catch (Exception e) {
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            }
        };
    }


}
