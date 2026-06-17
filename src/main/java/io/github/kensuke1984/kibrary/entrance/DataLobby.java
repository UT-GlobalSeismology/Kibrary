package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;

/**
 * Operation that downloads mseed files for events that satisfy specifications.
 * Data for stations included in networks specified by the user is downloaded.
 * TODO: the use of virtual networks is currently not supported.
 * <p>
 * Output directory "dl*" is created under the work path,
 * and eventDir/mseed created under this directory will include the downloaded files.
 * (memo: All download procedures were gathered here because downloading using multiple threads in FirstHandler caused errors.)
 * <p>
 * Events are downloaded in chronological order, so if processing fails at a certain event,
 * you can restart downloading from that event onward.
 * <p>
 * See also {@link EventDataPreparer}.
 *
 * @author otsuru
 * @since 2021/09/13
 */
public class DataLobby extends Operation {

    private final Property property;
    /**
     * Path for the work folder.
     */
    private Path workPath;
    /**
     * Path of folder to retry download.
     */
    private Path retryPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    private String datacenter;
    private String networks;
    private String channels;
    private int headAdjustment;
    private int footAdjustment;

    /**
     * Start of date range, inclusive.
     */
    private LocalDate startDate;
    /**
     * End of date range, INCLUSIVE.
     */
    private LocalDate endDate;

    /**
     * Moment magnitude range.
     */
    private LinearRange mwRange;
    /**
     * DEPTH range [km].
     */
    private LinearRange depthRange;
    private LinearRange latitudeRange;
    private CircularRange longitudeRange;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##Path of folder to retry download. Otherwise, leave this unset.");
            pw.println("#retryPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Datacenter to send request, from {IRIS, ORFEUS}. (IRIS)");
            pw.println("#datacenter ");
            pw.println("##Network names for request, listed using commas, must be set.");
            pw.println("##  Wildcards (*, ?) are allowed. Virtual networks are currently not supported.");
            pw.println("##  Note that a request will be made for all stations in the networks.");
            pw.println("#networks II,IU");
            pw.println("##Channels to be requested, listed using commas, from {BH?,HH?,BL?,HL?}. (BH?)");
            pw.println("#channels BH?,HH?,BL?,HL?");
            pw.println("##(int) Adjustment at the head [min], must be set.");
            pw.println("#headAdjustment -10");
            pw.println("##(int) Adjustment at the foot [min], must be set.");
            pw.println("#footAdjustment 120");
            pw.println("##########The following parameters are for seismic events to be searched for.");
            pw.println("##Start date in yyyy-mm-dd format, inclusive, must be set.");
            pw.println("#startDate 1990-01-01");
            pw.println("##End date in yyyy-mm-dd format, INCLUSIVE, must be set.");
            pw.println("#endDate 2020-12-31");
            pw.println("##Lower limit of Mw, inclusive; (:upperMw). (5.5)");
            pw.println("#lowerMw ");
            pw.println("##Upper limit of Mw, exclusive; (lowerMw:). (7.31)");
            pw.println("#upperMw ");
            pw.println("##SHALLOWER limit of DEPTH [km], inclusive; (:upperDepth). (100)");
            pw.println("#lowerDepth ");
            pw.println("##DEEPER limit of DEPTH [km], exclusive; (lowerDepth:). (700)");
            pw.println("#upperDepth ");
            pw.println("##Lower limit of latitude [deg], inclusive; [-90:upperLatitude). (-90)");
            pw.println("#lowerLatitude ");
            pw.println("##Upper limit of latitude [deg], exclusive; (lowerLatitude:90]. (90)");
            pw.println("#upperLatitude ");
            pw.println("##Lower limit of longitude [deg], inclusive; [-180:360]. (-180)");
            pw.println("#lowerLongitude ");
            pw.println("##Upper limit of longitude [deg], exclusive; [-180:360]. (180)");
            pw.println("#upperLongitude ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataLobby(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("retryPath")) retryPath = property.parsePath("retryPath", ".", true, workPath);
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        datacenter = property.parseStringSingle("datacenter", "IRIS");
        networks = property.parseStringSingle("networks", null);
        channels = property.parseStringSingle("channels", "BH?");
        headAdjustment = property.parseInt("headAdjustment", null);
        footAdjustment = property.parseInt("footAdjustment", null);

        startDate = LocalDate.parse(property.parseString("startDate", null));
        endDate = LocalDate.parse(property.parseString("endDate", null));
        MathAid.checkDateRangeValidity(startDate, endDate);

        double lowerMw = property.parseDouble("lowerMw", "5.5");
        double upperMw = property.parseDouble("upperMw", "7.31");
        mwRange = new LinearRange("Magnitude", lowerMw, upperMw);

        double lowerDepth = property.parseDouble("lowerDepth", "100");
        double upperDepth = property.parseDouble("upperDepth", "700");
        depthRange = new LinearRange("Depth", lowerDepth, upperDepth);

        double lowerLatitude = property.parseDouble("lowerLatitude", "-90");
        double upperLatitude = property.parseDouble("upperLatitude", "90");
        latitudeRange = new LinearRange("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        double lowerLongitude = property.parseDouble("lowerLongitude", "-180");
        double upperLongitude = property.parseDouble("upperLongitude", "180");
        longitudeRange = new CircularRange("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);
    }

    @Override
    public void run() throws IOException {
        List<GlobalCMTAccess> requestedEvents = listEvents();
        int nTotal = requestedEvents.size();
        if (!DatasetAid.checkNum(nTotal, "event", "events")) {
            return;
        }

        Path outPath;
        if (retryPath != null) {
            outPath = retryPath;
            System.err.println("Retrying for " + retryPath);
        } else {
            outPath = DatasetAid.createOutputFolder(workPath, "dl", folderTag, appendFolderDate, null);
            property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));
        }

        System.err.println("Downloading from " + datacenter);

        // loop for each event
        int n = 0;
        List<GlobalCMTID> failedEvents = new ArrayList<>();
        for (GlobalCMTAccess event : requestedEvents) {
            Path eventPath = outPath.resolve(event.toString());

            n++;
            System.err.println(event + " (# " + n + " of " + nTotal + ")  "
                    + DateTimeFormatter.ofPattern("<yyyy/MM/dd HH:mm:ss>").format(LocalDateTime.now()));

            // create event folder if it does not yet exist
            EventFolder ef = new EventFolder(eventPath);
            if (!Files.exists(ef.toPath())) {
                if (!ef.mkdirs()) {
                    System.err.println("Can't create " + ef);
                    continue;
                }
            }

            // download by EventDataPreparer
            EventDataPreparer edp = new EventDataPreparer(ef);
            int downloadStatus = edp.downloadMseeds(datacenter, networks, channels, headAdjustment, footAdjustment);
            switch (downloadStatus) {
            case -1:  // no attempt
                break;
            case 0:  // attempted but did not exist
                // wait 2 minutes befere moving on to the next event, so that the Datacenter has some time to rest
                if (n < nTotal) {
                    System.err.println(" ~ Resting for 2 minutes ...");
                    ThreadAid.sleep(1000 * 60 * 2);
                }
                break;
            case 99:  // download failed
                // add to list of events that failed, and move on to the next event after waiting
                failedEvents.add(event.getGlobalCMTID());
            case 1:  // download success
                // wait 15 minutes befere moving on to the next event, so that the data center has some time to rest
                if (n < nTotal) {
                    System.err.println(" ~ Resting for 15 minutes ...");
                    ThreadAid.sleep(1000 * 60 * 15);
                }
                break;
            }
        }

        if (failedEvents.size() > 0) {
            System.err.println("Failed events:");
            failedEvents.stream().forEach(ev -> System.err.println(" " + ev));
        } else {
            System.err.println("Everything succeeded!");
        }

        System.err.println("Finished downloading in " + outPath + " " + DateTimeFormatter.ofPattern("<yyyy/MM/dd HH:mm:ss>").format(LocalDateTime.now()));
    }

    private List<GlobalCMTAccess> listEvents() {
        GlobalCMTSearch search = new GlobalCMTSearch(startDate, endDate);
        search.setMwRange(mwRange);
        search.setDepthRange(depthRange);
        search.setLatitudeRange(latitudeRange);
        search.setLongitudeRange(longitudeRange);
        Set<GlobalCMTID> eventSet = search.search();
        return eventSet.stream().map(GlobalCMTID::getEventData).sorted(Comparator.comparing(GlobalCMTAccess::getCMTTime)).collect(Collectors.toList());
    }

}
