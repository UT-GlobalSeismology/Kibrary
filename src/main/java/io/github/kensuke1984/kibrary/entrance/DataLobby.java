package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
 * TODO this is only true for 2005 or later
 * <p>
 * See also {@link EventDataPreparer}.
 *
 * @since 2021/09/13
 * @author otsuru
 */
public class DataLobby extends Operation {

    private final Property property;
    /**
     * Path for the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;

    private String datacenter;
    private String networks;
    private String channels;
    private int headAdjustment;
    private int footAdjustment;

    private LocalDate startDate;
    /**
     * including the date
     */
    private LocalDate endDate;
    private double lowerMw;
    private double upperMw;
    /**
     * not radius but distance from the surface
     */
    private double lowerDepth;
    /**
     * not radius but distance from the surface
     */
    private double upperDepth;
    private double lowerLatitude;
    private double upperLatitude;
    private double lowerLongitude;
    private double upperLongitude;

    private Set<GlobalCMTID> requestedEvents;


    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
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
            pw.println("##Start date yyyy-mm-dd, must be set.");
            pw.println("#startDate 1990-01-01");
            pw.println("##End date yyyy-mm-dd, must be set.");
            pw.println("#endDate 2019-12-31");
            pw.println("##Lower limit of Mw; (:upperMw). (5.5)");
            pw.println("#lowerMw ");
            pw.println("##Upper limit of Mw; (lowerMw:). (7.3)");
            pw.println("#upperMw ");
            pw.println("##Shallower limit of DEPTH [km]; (:upperDepth). (100)");
            pw.println("#lowerDepth ");
            pw.println("##Deeper limit of DEPTH [km]; (lowerDepth:). (700)");
            pw.println("#upperDepth ");
            pw.println("##Lower limit of latitude [deg]; [-90:upperLatitude). (-90)");
            pw.println("#lowerLatitude ");
            pw.println("##Upper limit of latitude [deg]; (lowerLatitude:90]. (90)");
            pw.println("#upperLatitude ");
            pw.println("##Lower limit of longitude [deg]; [-180:upperLongitude). (-180)");
            pw.println("#lowerLongitude ");
            pw.println("##Upper limit of longitude [deg]; (lowerLongitude:360]. (180)");
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
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        datacenter = property.parseStringSingle("datacenter", "IRIS");
        networks = property.parseStringSingle("networks", null);
        channels = property.parseStringSingle("channels", "BH?");
        headAdjustment = property.parseInt("headAdjustment", null);
        footAdjustment = property.parseInt("footAdjustment", null);

        startDate = LocalDate.parse(property.parseString("startDate", null));
        endDate = LocalDate.parse(property.parseString("endDate", null));
        if (startDate.isAfter(endDate))
            throw new IllegalArgumentException("Date range " + startDate + " , " + endDate + " is invalid.");

        lowerMw = property.parseDouble("lowerMw", "5.5");
        upperMw = property.parseDouble("upperMw", "7.3");
        if (lowerMw > upperMw)
            throw new IllegalArgumentException("Magnitude range " + lowerMw + " , " + upperMw + " is invalid.");

        lowerDepth = property.parseDouble("lowerDepth", "100");
        upperDepth = property.parseDouble("upperDepth", "700");
        if (lowerDepth > upperDepth)
            throw new IllegalArgumentException("Depth range " + lowerDepth + " , " + upperDepth + " is invalid.");

        lowerLatitude = property.parseDouble("lowerLatitude", "-90");
        upperLatitude = property.parseDouble("upperLatitude", "90");
        if (lowerLatitude < -90 || lowerLatitude > upperLatitude || 90 < upperLatitude)
            throw new IllegalArgumentException("Latitude range " + lowerLatitude + " , " + upperLatitude + " is invalid.");

        lowerLongitude = property.parseDouble("lowerLongitude", "-180");
        upperLongitude = property.parseDouble("upperLongitude", "180");
        if (lowerLongitude < -180 || lowerLongitude > upperLongitude || 360 < upperLongitude)
            throw new IllegalArgumentException("Longitude range " + lowerLongitude + " , " + upperLongitude + " is invalid.");
    }

    @Override
    public void run() throws IOException {
        requestedEvents = listEvents();
        int n_total = requestedEvents.size();
        if (!DatasetAid.checkNum(n_total, "event", "events")) {
            return;
        }

        Path outPath = DatasetAid.createOutputFolder(workPath, "dl", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        final AtomicInteger n = new AtomicInteger();
        requestedEvents.stream().map(GlobalCMTID::getEventData).sorted(Comparator.comparing(GlobalCMTAccess::getCMTTime)).forEach(event -> {
            try {
                n.incrementAndGet();
                System.err.println(event + " (# " + n + " of " + n_total + ")");

                // create event folder
                EventFolder ef = new EventFolder(outPath.resolve(event.toString()));
                if (!ef.mkdirs()) throw new IOException("Can't create " + ef);

                // download by EventDataPreparer
                EventDataPreparer edp = new EventDataPreparer(ef);
                String mseedFileName = event + "." + GadgetAid.getTemporaryString() + ".mseed";
                if (!edp.downloadMseed(datacenter, networks, channels, headAdjustment, footAdjustment, mseedFileName)) {
                    System.err.println("!!! Data not found for " + event + ", skipping.");
                    return;
                }

                // wait 2 minutes befere moving on to the next event, so that the Datacenter has some time to rest
                System.err.println(" ~ Resting for 2 minutes ...");
                ThreadAid.sleep(1000 * 60 * 2);

            } catch (IOException e) {
                // Here, suppress exceptions for events that failed, and move on to the next event.
                System.err.println("!!! Download for " + event + " failed, skipping.");
                e.printStackTrace();
            }
        });

    }

    private Set<GlobalCMTID> listEvents() {
        GlobalCMTSearch search = new GlobalCMTSearch(startDate, endDate);
        search.setLatitudeRange(lowerLatitude, upperLatitude);
        search.setLongitudeRange(lowerLongitude, upperLongitude);
        search.setMwRange(lowerMw, upperMw);
        search.setDepthRange(lowerDepth, upperDepth);
        return search.search();
    }

}
