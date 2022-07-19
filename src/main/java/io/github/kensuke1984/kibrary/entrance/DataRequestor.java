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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;

/**
 * This Operation class makes breqfast mails to request data and sends them.
 * Requests are made for events that satisfy specifications, and for stations included in networks specified by the user.
 * A gmail account is needed. The address must be set in the .property file in KIBRARY_HOME.
 *
 * @author Kensuke Konishi
 */
public class DataRequestor extends Operation {

    private final Property property;
    /**
     * Path for the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    // private String label;
    // private String[] alternateMedia;
    private String[] networks;
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
    private boolean send;

    private Set<GlobalCMTID> requestedEvents;
    private String dateStr = GadgetAid.getTemporaryString();

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Network names for request, listed using spaces, must be defined");
            pw.println("## Wildcards (*, ?) and virtual networks are allowed.");
            pw.println("## Note that it will make a request for all stations in the networks.");
            pw.println("#networks II IU _US-All");
            pw.println("##Adjustment at the head [min], must be integer and defined");
            pw.println("#headAdjustment -10");
            pw.println("##Adjustment at the foot [min], must be integer and defined");
            pw.println("#footAdjustment 120");
            pw.println("##########The following parameters are for seismic events to be searched for.");
            pw.println("##Start date yyyy-mm-dd, must be defined");
            pw.println("#startDate 1990-01-01");
            pw.println("##End date yyyy-mm-dd, must be defined");
            pw.println("#endDate 2019-12-31");
            pw.println("##Lower limit of Mw (5.5)");
            pw.println("#lowerMw ");
            pw.println("##Upper limit of Mw (7.3)");
            pw.println("#upperMw ");
            pw.println("##Shallower limit of DEPTH (100)");
            pw.println("#lowerDepth ");
            pw.println("##Deeper limit of DEPTH (700)");
            pw.println("#upperDepth ");
            pw.println("#Lower limit of latitude [deg] [-90:upperLatitude) (-90)");
            pw.println("#lowerLatitude ");
            pw.println("##Upper limit of latitude [deg] (lowerLatitude:90] (90)");
            pw.println("#upperLatitude ");
            pw.println("##Lower limit of longitude [deg] [-180:upperLongitude) (-180)");
            pw.println("#lowerLongitude ");
            pw.println("##Upper limit of longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperLongitude ");
            pw.println("##(boolean) Whether to actually send the emails (false)");
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
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        networks = property.parseStringArray("networks", null);

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

        send = property.parseBoolean("send", "false");
    }
/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("networks"))
            throw new IllegalArgumentException("No information about networks");
        if (!property.containsKey("headAdjustment"))
            throw new IllegalArgumentException("No information about the head adjustment");
        if (!property.containsKey("footAdjustment"))
            throw new IllegalArgumentException("No information about the foot adjustment");
        if (!property.containsKey("startDate"))
            throw new IllegalArgumentException("No information about the start date");
        if (!property.containsKey("endDate"))
            throw new IllegalArgumentException("No information about the end date");
        if (!property.containsKey("lowerMw")) property.setProperty("lowerMw", "5.5");
        if (!property.containsKey("upperMw")) property.setProperty("upperMw", "7.3");
        if (!property.containsKey("lowerDepth")) property.setProperty("lowerDepth", "100");
        if (!property.containsKey("upperDepth")) property.setProperty("upperDepth", "700");
        if (!property.containsKey("lowerLatitude")) property.setProperty("lowerLatitude", "-90");
        if (!property.containsKey("upperLatitude")) property.setProperty("upperLatitude", "90");
        if (!property.containsKey("lowerLongitude")) property.setProperty("lowerLongitude", "-180");
        if (!property.containsKey("upperLongitude")) property.setProperty("upperLongitude", "180");
        if (!property.containsKey("send")) property.setProperty("send", "false");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        networks = property.getProperty("networks").split("\\s+");
        headAdjustment = Integer.parseInt(property.getProperty("headAdjustment"));
        footAdjustment = Integer.parseInt(property.getProperty("footAdjustment"));

        startDate = LocalDate.parse(property.getProperty("startDate"));
        endDate = LocalDate.parse(property.getProperty("endDate"));
        if (startDate.isAfter(endDate))
            throw new IllegalArgumentException("Date range " + startDate + " , " + endDate + " is invalid.");
        lowerMw = Double.parseDouble(property.getProperty("lowerMw"));
        upperMw = Double.parseDouble(property.getProperty("upperMw"));
        if (lowerMw > upperMw)
            throw new IllegalArgumentException("Magnitude range " + lowerMw + " , " + upperMw + " is invalid.");
        lowerDepth = Double.parseDouble(property.getProperty("lowerDepth"));
        upperDepth = Double.parseDouble(property.getProperty("upperDepth"));
        if (lowerDepth > upperDepth)
            throw new IllegalArgumentException("Depth range " + lowerDepth + " , " + upperDepth + " is invalid.");
        lowerLatitude = Double.parseDouble(property.getProperty("lowerLatitude"));
        upperLatitude = Double.parseDouble(property.getProperty("upperLatitude"));
        if (lowerLatitude < -90 || lowerLatitude > upperLatitude || 90 < upperLatitude)
            throw new IllegalArgumentException("Latitude range " + lowerLatitude + " , " + upperLatitude + " is invalid.");
        lowerLongitude = Double.parseDouble(property.getProperty("lowerLongitude"));
        upperLongitude = Double.parseDouble(property.getProperty("upperLongitude"));
        if (lowerLongitude < -180 || lowerLongitude > upperLongitude || 360 < upperLongitude)
            throw new IllegalArgumentException("Longitude range " + lowerLongitude + " , " + upperLongitude + " is invalid.");

        send = Boolean.parseBoolean(property.getProperty("send"));
    }
*/

    @Override
    public void run() throws IOException {
        requestedEvents = listEvents();
        if (!DatasetAid.checkNum(requestedEvents.size(), "event", "events")) {
            return;
        }
        System.out.println("Label contains \"" + dateStr + "\"");

        outPath = DatasetAid.createOutputFolder(workPath, "request", tag, dateStr);

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
        return new BreakFastMail(event + "." + dateStr, channels);
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
