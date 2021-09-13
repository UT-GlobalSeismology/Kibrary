package io.github.kensuke1984.kibrary.datarequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.Set;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;

/**
 * It makes a data requesting mail.
 *
 * @author Kensuke Konishi
 * @version 0.1.4
 */
public class DataLobby implements Operation {

    private String dataselectURL = "http://service.iris.edu/fdsnws/dataselect/1/query?";

    private Path workPath;
    // private String label;
    // private String[] alternateMedia;
    private String networks;
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

    private Set<GlobalCMTID> requestedIDs;
//    private boolean send;
    private Properties property;
    private String date = Utilities.getTemporaryString();


    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Paths.get(DataLobby.class.getName() + Utilities.getTemporaryString() + ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan DataLobby");
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath");
            pw.println("##Network names for request, listed using commas, must be defined");
            pw.println("##Note that it will make a request for all stations in the networks.");
            pw.println("#networks II,IU");
            pw.println("##Adjustment at the head [min], must be integer and defined");
            pw.println("#headAdjustment -10");
            pw.println("##Adjustment at the foot [min], must be integer and defined");
            pw.println("#footAdjustment 120");
            pw.println("##Starting date yyyy-mm-dd, must be defined");
            pw.println("#startDate 1990-01-01");
            pw.println("##End date yyyy-mm-dd, must be defined");
            pw.println("#endDate 2019-12-31");
            pw.println("##Lower limit of Mw (5.5)");
            pw.println("#lowerMw");
            pw.println("##Upper limit of Mw (6.5)");
            pw.println("#upperMw");
            pw.println("##Shallower limit of DEPTH (100)");
            pw.println("#lowerDepth");
            pw.println("##Deeper limit of DEPTH (700)");
            pw.println("#upperDepth");
            pw.println("##The following geometrical filter is for seismic events.");
            pw.println("##Lower limit of latitude [deg] [-90:upperLatitude) (-90)");
            pw.println("#lowerLatitude");
            pw.println("##Upper limit of latitude [deg] (lowerLatitude:90] (90)");
            pw.println("#upperLatitude");
            pw.println("##Lower limit of longitude [deg] [-180:upperLongitude) (-180)");
            pw.println("#lowerLongitude");
            pw.println("##Upper limit of longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperLongitude");
//            pw.println("##If you want to send emails, then set it true (false)");
//            pw.println("#send");
        }
        System.err.println(outPath + " is created.");
    }

    public DataLobby(Properties property) {
        this.property = (Properties) property.clone();
        set();
    }

    private void set() {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        networks = property.getProperty("networks"); //.split("\\s+");
        headAdjustment = Integer.parseInt(property.getProperty("headAdjustment"));
        footAdjustment = Integer.parseInt(property.getProperty("footAdjustment"));

        startDate = LocalDate.parse(property.getProperty("startDate"));
        endDate = LocalDate.parse(property.getProperty("endDate"));
        lowerMw = Double.parseDouble(property.getProperty("lowerMw"));
        upperMw = Double.parseDouble(property.getProperty("upperMw"));
        lowerDepth = Double.parseDouble(property.getProperty("lowerDepth"));
        upperDepth = Double.parseDouble(property.getProperty("upperDepth"));
        lowerLatitude = Double.parseDouble(property.getProperty("lowerLatitude"));
        upperLatitude = Double.parseDouble(property.getProperty("upperLatitude"));
        lowerLongitude = Double.parseDouble(property.getProperty("lowerLongitude"));
        upperLongitude = Double.parseDouble(property.getProperty("upperLongitude"));
//        send = Boolean.parseBoolean(property.getProperty("send"));
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("networks")) throw new RuntimeException("No information about networks");
        if (!property.containsKey("footAdjustment"))
            throw new RuntimeException("No information about the foot adjustment");
        if (!property.containsKey("headAdjustment"))
            throw new RuntimeException("No information about the head adjustment");
        if (!property.containsKey("startDate")) throw new RuntimeException("No information about the start date");
        if (!property.containsKey("endDate")) throw new RuntimeException("No information about the end date");
        if (!property.containsKey("lowerMw")) property.setProperty("lowerMw", "5.5");
        if (!property.containsKey("upperMw")) property.setProperty("upperMw", "6.5");
        if (!property.containsKey("lowerDepth")) property.setProperty("lowerDepth", "100");
        if (!property.containsKey("upperDepth")) property.setProperty("upperDepth", "700");
        if (!property.containsKey("lowerLatitude")) property.setProperty("lowerLatitude", "-90");
        if (!property.containsKey("upperLatitude")) property.setProperty("upperLatitude", "90");
        if (!property.containsKey("lowerLongitude")) property.setProperty("lowerLongitude", "-180");
        if (!property.containsKey("upperLongitude")) property.setProperty("upperLongitude", "180");
//        if (!property.containsKey("send")) property.setProperty("send", "false");
    }


    /**
     * @param args Request Mode: [parameter file name]
     * @throws Exception file name
     */
    public static void main(String[] args) throws Exception {
        DataLobby dl = new DataLobby(Property.parse(args));
        long startT = System.nanoTime();
        System.err.println(DataLobby.class.getName() + " is going");
        dl.run();
        System.err.println(
                DataLobby.class.getName() + " finished in " + Utilities.toTimeString(System.nanoTime() - startT));

    }

    @Override
    public void run() throws IOException {
        System.err.println("Working directory is " + workPath);
        if (!Files.exists(workPath)) throw new NoSuchFileException(workPath.toString());
        Path outPath = workPath.resolve("dl" + Utilities.getTemporaryString());
        Files.createDirectories(outPath);
        System.err.println("Output directory is " + outPath);

        requestedIDs = listIDs();
        System.out.println(requestedIDs.size() + " events are found.");
        System.out.println("Label contains \"" + date + "\""); //TODO erase
        requestedIDs.forEach(id -> {
            try {
                EventFolder eventDir = new EventFolder(outPath.resolve(id.toString()));
                if (!eventDir.mkdirs()) throw new RuntimeException("Can't create " + eventDir);

                String mseedFile = id + "." + date + ".mseed";
                Path mseedPath = eventDir.toPath().resolve(mseedFile); // 出力パスの指定

                System.err.println(id + " : downloading mseed ...");
                downloadMseed(id, mseedPath);

                System.err.println(id + " : extracting mseed ...");
                mseed2sac(mseedFile, eventDir.toPath());


            } catch (Exception e) {
                System.err.println("Download for " + id + " failed.");
                e.printStackTrace();
            }
        });
        System.err.println("Download finished.");


/*
        requestedIDs.forEach(id -> output(createBreakFastMail(id)));
        Path sent = workPath.resolve("sent" + Utilities.getTemporaryString());
        if (send) try {
            System.err.println("Sending requests in 5 sec.");
            System.err.println("Sent mails will be in " + sent);
            Thread.sleep(1000 * 5);
        } catch (Exception e2) {
        }
        requestedIDs.forEach(id -> {
            BreakFastMail m = createBreakFastMail(id);
            try {
                Path out = output(m);
                if (!send) return;
                Files.createDirectories(sent);
                Files.move(out, sent.resolve(out.getFileName()));
                System.err.println("Sending a request for " + id);
                m.sendIris();
                Thread.sleep(300 * 1000);
            } catch (Exception e) {
                System.err.println(m.getLabel() + " was not sent");
                e.printStackTrace();
            }
        });
*/
    }

    private Set<GlobalCMTID> listIDs() {
        GlobalCMTSearch search = new GlobalCMTSearch(startDate, endDate);
        search.setLatitudeRange(lowerLatitude, upperLatitude);
        search.setLongitudeRange(lowerLongitude, upperLongitude);
        search.setMwRange(lowerMw, upperMw);
        search.setDepthRange(lowerDepth, upperDepth);
        return search.search();
    }

    private void downloadMseed(GlobalCMTID id, Path mseedPath) throws IOException {
        LocalDateTime cmtTime = id.getEvent().getCMTTime();
        LocalDateTime startTime = cmtTime.plus(headAdjustment, ChronoUnit.MINUTES);
        LocalDateTime endTime = cmtTime.plus(footAdjustment, ChronoUnit.MINUTES);

        String url_string = dataselectURL + "net=" + networks + "&sta=*&loc=*&cha=BH?&starttime=" + toLine(startTime) +
                "&endtime=" + toLine(endTime) + "&format=miniseed&nodata=404";
        URL url = new URL(url_string);
        long size = 0L;

        size = Files.copy(url.openStream(), mseedPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
        System.out.println("Downloaded : " + id + " - " + size + " bytes");
    }

    private String toLine(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private boolean mseed2sac(String mseedFile, Path outputPath) throws IOException {
        String command = "mseed2sac " + mseedFile;
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); //  runevalresp in MseedSAC.javaを参考にした

        pb.directory(new File(outputPath.toString()).getAbsoluteFile());
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


/*
    private Path output(BreakFastMail mail) {
        Path out = workPath.resolve(mail.getLabel() + ".mail");
        try {
            Files.write(out, Arrays.asList(mail.getLines()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }
*/
    /**
     * write a break fast mail for the input id
     *
     * @param id of {@link GlobalCMTID}
     * @return BreakFastMail for the id
     */
/*    public BreakFastMail createBreakFastMail(GlobalCMTID id) {
        Channel[] channels = Channel.listChannels(networks, id, ChronoUnit.MINUTES, headAdjustment, ChronoUnit.MINUTES,
                footAdjustment);
        return new BreakFastMail(id + "." + date, channels);
    }
*/

    @Override
    public Path getWorkPath() {
        return workPath;
    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

}
