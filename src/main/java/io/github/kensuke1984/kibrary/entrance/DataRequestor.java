package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;

/**
 * This Operation class makes breqfast mails to request data and sends them.
 * Requests are made for events that satisfy specifications, and for stations included in networks specified by the user.
 * A gmail account is needed. The address must be set in the .property file in KIBRARY_HOME.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class DataRequestor extends Operation {

    private final Property property;
    /**
     * Path for the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Path of the output folder.
     */
    private Path outPath;

    private String[] networks;
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

    private boolean send;

    private Set<GlobalCMTID> requestedEvents;
    private String dateString = GadgetAid.getTemporaryString();

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
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Network names for request, listed using spaces, must be set.");
            pw.println("##  Wildcards (*, ?) and virtual networks are allowed.");
            pw.println("##  Note that it will make a request for all stations in the networks.");
            pw.println("#networks II IU _US-All");
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
            pw.println("##(boolean) Whether to actually send the emails. (false)");
            pw.println("#send ");
        }
        System.err.println(outPath + " is created.");
    }

    public DataRequestor(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        networks = property.parseStringArray("networks", null);

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

        send = property.parseBoolean("send", "false");
    }

    @Override
    public void run() throws IOException {
        requestedEvents = listEvents();
        if (!DatasetAid.checkNum(requestedEvents.size(), "event", "events")) {
            return;
        }
        System.out.println("Label contains \"" + dateString + "\"");

        outPath = DatasetAid.createOutputFolder(workPath, "request", folderTag, appendFolderDate, dateString);

        requestedEvents.forEach(event -> output(createBreakFastMail(event)));

        Path sentPath = outPath.resolve("sent");
        if (send) try {
            System.err.println("Sending requests in 5 sec.");
            System.err.println("Sent mails will be in " + sentPath);
            Thread.sleep(1000 * 5);
        } catch (Exception e2) {
        }
        requestedEvents.forEach(event -> {
            BreakFastMail m = createBreakFastMail(event);
            try {
                Path out = output(m);
                if (!send) return;
                Files.createDirectories(sentPath);
                Files.move(out, sentPath.resolve(out.getFileName()));
                System.err.println("Sending a request for " + event);
                m.sendIris();
                Thread.sleep(300 * 1000);
            } catch (Exception e) {
                System.err.println(m.getLabel() + " was not sent");
                e.printStackTrace();
            }
        });
    }

    private Path output(BreakFastMail mail) {
        Path out = outPath.resolve(mail.getLabel() + ".mail");
        try {
            Files.write(out, Arrays.asList(mail.getLines()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * write a break fast mail for the input id
     *
     * @param id of {@link GlobalCMTID}
     * @return BreakFastMail for the id
     */
    public BreakFastMail createBreakFastMail(GlobalCMTID event) {
        Channel[] channels = Channel.listChannels(networks, event, ChronoUnit.MINUTES, headAdjustment, ChronoUnit.MINUTES,
                footAdjustment);
        return new BreakFastMail(event + "." + dateString, channels);
    }

    private Set<GlobalCMTID> listEvents() {
        GlobalCMTSearch search = new GlobalCMTSearch(startDate, endDate);
        search.setMwRange(mwRange);
        search.setDepthRange(depthRange);
        search.setLatitudeRange(latitudeRange);
        search.setLongitudeRange(longitudeRange);
        return search.search();
    }

}
