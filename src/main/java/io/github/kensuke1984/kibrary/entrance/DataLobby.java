package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
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
 * See also {@link EventDataPreparer}.
 *
 * @since 2021/09/13
 * @author otsuru
 */
public class DataLobby implements Operation {

    private final Properties property;
    /**
     * Path for the work folder
     */
    private Path workPath;

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


    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Property.generatePath(DataLobby.class);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan DataLobby");
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##Datacenter to send request, chosen from [IRIS, ORFEUS] (IRIS)");
            pw.println("#datacenter");
            pw.println("##Network names for request, listed using commas, must be defined");
            pw.println("##Wildcards (*, ?) are allowed. Virtual networks are currently not supported.");
            pw.println("##Note that it will make a request for all stations in the networks.");
            pw.println("#networks II,IU");
            pw.println("##Channels to be requested, listed using commas, from [BH?,HH?,BL?,HL?] (BH?)");
            pw.println("#channels BH?,HH?,BL?,HL?");
            pw.println("##Adjustment at the head [min], must be integer and defined");
            pw.println("#headAdjustment -10");
            pw.println("##Adjustment at the foot [min], must be integer and defined");
            pw.println("#footAdjustment 120");
            pw.println("##The following parameters are for seismic events to be searched for.");
            pw.println("##Start date yyyy-mm-dd, must be defined");
            pw.println("#startDate 1990-01-01");
            pw.println("##End date yyyy-mm-dd, must be defined");
            pw.println("#endDate 2019-12-31");
            pw.println("##Lower limit of Mw (5.5)");
            pw.println("#lowerMw");
            pw.println("##Upper limit of Mw (7.3)");
            pw.println("#upperMw");
            pw.println("##Shallower limit of DEPTH (100)");
            pw.println("#lowerDepth");
            pw.println("##Deeper limit of DEPTH (700)");
            pw.println("#upperDepth");
            pw.println("##Lower limit of latitude [deg] [-90:upperLatitude) (-90)");
            pw.println("#lowerLatitude");
            pw.println("##Upper limit of latitude [deg] (lowerLatitude:90] (90)");
            pw.println("#upperLatitude");
            pw.println("##Lower limit of longitude [deg] [-180:upperLongitude) (-180)");
            pw.println("#lowerLongitude");
            pw.println("##Upper limit of longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperLongitude");
        }
        System.err.println(outPath + " is created.");
    }

    public DataLobby(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("datacenter")) property.setProperty("datacenter", "IRIS");
        if (!property.containsKey("networks"))
            throw new IllegalArgumentException("No information about networks");
        if (!property.containsKey("channels")) property.setProperty("channels", "BH?");
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
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        datacenter = property.getProperty("datacenter");
        networks = property.getProperty("networks"); //.split("\\s+");
        channels = property.getProperty("channels"); //.split("\\s+");
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
    }

    /**
     * @param args Request Mode: [parameter file name]
     * @throws Exception file name
     */
    public static void main(String[] args) throws IOException {
        DataLobby dl = new DataLobby(Property.parse(args));
        long startTime = System.nanoTime();
        System.err.println(DataLobby.class.getName() + " is operating.");
        dl.run();
        System.err.println(DataLobby.class.getName() + " finished in " +
                GadgetUtils.toTimeString(System.nanoTime() - startTime));
    }

    @Override
    public void run() throws IOException {
        requestedEvents = listEvents();
        int n_total = requestedEvents.size();
        if (!DatasetUtils.checkEventNum(n_total)) {
            return;
        }

        Path outPath = workPath.resolve("dl" + GadgetUtils.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output folder is " + outPath);

        final AtomicInteger n = new AtomicInteger();
        requestedEvents.stream().sorted().forEach(event -> {
            try {
                n.incrementAndGet();
                System.err.println("Downloading files for " + event + " (# " + n + " of " + n_total + ") ...");

                // create event folder
                EventFolder ef = new EventFolder(outPath.resolve(event.toString()));
                if (!ef.mkdirs()) throw new IOException("Can't create " + ef);

                // download by EventDataPreparer
                EventDataPreparer edp = new EventDataPreparer(ef);
                String mseedFileName = event + "." + GadgetUtils.getTemporaryString() + ".mseed";
                if (!edp.downloadMseed(datacenter, networks, channels, headAdjustment, footAdjustment, mseedFileName)) {
                    System.err.println("!! Data not found for " + event + ", skipping.");
                    return;
                }

            } catch (IOException e) {
                // Here, suppress exceptions for events that failed, and move on to the next event.
                System.err.println("!! Download for " + event + " failed.");
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

    @Override
    public Path getWorkPath() {
        return workPath;
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

}
