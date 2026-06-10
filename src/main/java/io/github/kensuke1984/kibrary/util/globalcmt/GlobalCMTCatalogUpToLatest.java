package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.FileAid;

/**
 * Updating the catalog of Gglobal CMT solutions.
 * <p>
 * The specified version of the catalog will be downloaded if it does not already exist.
 * The active version of the catalog will be set to the one specified.
 * <p>
 * Available versions of catalogs can be checked at
 * <a href="https://www.globalcmt.org/CMTfiles.html">https://www.globalcmt.org/CMTfiles.html</a>.
 *
 * @author kataoka
 * @since 2026/3/23
 */

public final class GlobalCMTCatalogUpToLatest {
    private GlobalCMTCatalogUpToLatest() {}

    /**
     * Path to the directory where individual NDK files are saved.
     */
    private static final Path catalogDirectoryPath = Environment.KIBRARY_SHARE.resolve("eachMonth");

    /**
     * option flag for catalog version of "AllEvents"
     */
    private static final String versionOption = "v1";

    /**
     * optin flag for first year of "monthly" catalog
     */
    private static final String firstYearOption = "v2";

    /**
     * option flag for last year of "monthly" catalog
     */
    private static final String lastYearOption = "v3";

    /**
     * Upadate the catalog of global CMT solutions until 2025
     * @param args Options.
     * @throws IOExpetion if any
     */
    public static void main(String[] args) throws IOException{
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

        options.addOption(Option.builder(versionOption).longOpt("version").hasArg().argName("mmmYY").required()
                .desc("The month and year the version of the catalog is up to, "
                        + "with mmm as the first three letters of the name of the month (lower case), "
                        + "and YY as the lower two digits of the year.").build());
        options.addOption(Option.builder(firstYearOption).longOpt("firstYear").hasArg().argName("YYYY").required()
                .desc("The firstyear of catalog in monthly, with YYYY as the four digits of the year.").build()
                );
        options.addOption(Option.builder(lastYearOption).longOpt("lastYear").hasArg().argName("YYYY").required()
                .desc("The lastyear of catalog in monthly, with YYYY as the four digits of the year.").build()
                );
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        switchCatalog(cmdLine.getOptionValue(versionOption), cmdLine.getOptionValue(firstYearOption), cmdLine.getOptionValue(lastYearOption));
    }

    private static void switchCatalog(String version, String firstYear, String lastYear) throws IOException {
        //~Download ndk files from "all_events"~//
        String catalogNameofAllEvents = "jan76_" + version + ".ndk";
        String catalogURLofAllEvents = "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/" + catalogNameofAllEvents;
        downloadCatalog(catalogNameofAllEvents, catalogURLofAllEvents);

        //~Download ndk files from "monthly"~//
        for (int eventYear = Integer.parseInt(firstYear); eventYear <= Integer.parseInt(lastYear); eventYear++) {
            String eventYearURL =
                    "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/NEW_MONTHLY/" + eventYear +  "/";
            System.out.println("Start" + eventYear);

            URL url = new URL(eventYearURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            reader.close();
            connection.disconnect();

            String htmlString = html.toString();
            //find and get ndk link
            Pattern pattern = Pattern.compile("href=\'([^\']+\\.ndk)\'");
            Matcher matcher = pattern.matcher(htmlString);
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String ndkFile = matcher.group(1);
                String fullURL = eventYearURL + ndkFile;
                downloadCatalog(ndkFile, fullURL);
            }
            if (!found) {
                System.err.println("No ndk files found for " + eventYear);
            }
            System.out.println("Finished " + eventYear);
        }
        mergeCatalog(lastYear);
    }

    //~Method to merge all ndk files into one
    private static void mergeCatalog(String lastYear) throws IOException{
        String yy = lastYear.substring(2);
        String MergedCatalogName = "jan76_dec" + yy + ".ndk";
        Path MergedCatalogPath = Environment.KIBRARY_SHARE.resolve(MergedCatalogName);
        List<String> monthOrder = Arrays.asList(
                "jan", "feb", "mar", "apr", "may", "jun",
                "jul", "aug", "sep", "oct", "nov", "dec"
            );
        // get only monthly catalog
        List<Path> monthlyFiles = Files.list(catalogDirectoryPath)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".ndk")
                            && name.length() == 9;
                })
                .sorted((p1, p2) -> {
                    String f1 = p1.getFileName().toString();
                    String f2 = p2.getFileName().toString();
                    String month1 = f1.substring(0, 3);
                    String month2 = f2.substring(0, 3);
                    int year1 = Integer.parseInt(f1.substring(3, 5));
                    int year2 = Integer.parseInt(f2.substring(3, 5));
                    if (year1 != year2) {
                        return Integer.compare(year1, year2);
                    }
                    return Integer.compare(
                        monthOrder.indexOf(month1),
                        monthOrder.indexOf(month2)
                    );
                })
                .collect(Collectors.toList());
        try (BufferedWriter writer = Files.newBufferedWriter(MergedCatalogPath)){
            //~Write ndk files from "all_Events"~//
            Path oldCatalog = catalogDirectoryPath.resolve("jan76_dec20.ndk");
            for(String line : Files.readAllLines(oldCatalog)) {
                writer.write(line);
                writer.newLine();
            }
            //~Write ndk files from "monthly"~//
            for (Path file : monthlyFiles) {
                for (String line : Files.readAllLines(file)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

       //~Activate (change target of symbolic link)~//
       // check whether the symbolic link itself exists, regardless of the existence of its target
        if (Files.exists(GlobalCMTCatalog.CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
            // delete the symbolic link, not its target
            Files.delete(GlobalCMTCatalog.CATALOG_PATH);
        }
        Files.createSymbolicLink(GlobalCMTCatalog.CATALOG_PATH, MergedCatalogPath);
        System.err.println("The referenced catalog is set to " + MergedCatalogName);
    }

    //~Method to download GCMT catalog~//
    private static void downloadCatalog(String catalogName, String catalogURL) throws IOException{
        Path catalogPath = catalogDirectoryPath.resolve(catalogName);
        if (Files.exists(catalogPath)) {
            System.err.println("Catalog " + catalogName + " already exists; skipping download.");
        } else {
            System.err.println("Downloading catalog " + catalogName + " ...");
            try {
                FileAid.download(new URL(catalogURL), catalogPath, false);
            } catch (IOException e) {
                if (Files.exists(catalogPath)) {
                    // delete the trash that may be made
                    Files.delete(catalogPath);
                }
                // If download fails, IOException will be thrown here. Symbolic link will not be changed.
                throw e;
            }
        }
      //~Fix errors in downloaded catalog~//
        fixCatalog(catalogPath);
    }

    private static void fixCatalog(Path catalogPath) throws IOException {
        List<String> lines = Files.readAllLines(catalogPath);
        if (lines.size() % 5 != 0) throw new IllegalStateException(catalogPath + " is broken or invalid.");

        for (int n = 0; n < lines.size() / 5; n++) {

            //~fix errors where a space between 2 numbers is missing~//
            String centroidLine = lines.get(n * 5 + 2);
            if (centroidLine.split("\\s+")[1].split("\\.").length == 3) {
                // get position of first decimal
                int firstDecimal = centroidLine.indexOf(".");
                // add a space 2 letters after the decimal
                StringBuilder fixer = new StringBuilder(centroidLine);
                fixer.insert(firstDecimal + 2, " ");
                // overwrite the line
                lines.set(n * 5 + 2, fixer.toString());
            }

            //~fix errors where "60.0" seconds exists~//
            String hypocenterLine = lines.get(n * 5);
            String[] timeStrings = hypocenterLine.split("\\s+")[2].split(":");
            if (Precision.equals(Double.parseDouble(timeStrings[2]), 60.0, 0.001)) {
                // calculate correct time
                LocalTime time = LocalTime.parse(timeStrings[0] + ":" + timeStrings[1] + ":00");
                time = time.plusMinutes(1);
                String fixedTimeString = time.format(DateTimeFormatter.ISO_LOCAL_TIME);
                // get position of first colon
                int firstColon = hypocenterLine.indexOf(":");
                // fix time in line
                StringBuilder fixer = new StringBuilder(hypocenterLine);
                fixer.replace(firstColon - 2, firstColon + 6, fixedTimeString);
                // overwrite the line
                lines.set(n * 5, fixer.toString());
            }

            //~fix errors where space after TRIHD is missing~//
            String halfdurationLine = lines.get(n * 5 + 1);
            if (halfdurationLine.contains("TRIHD:") && !halfdurationLine.contains("TRIHD: ")) {
                // get position of half duration
                int indexHD = halfdurationLine.indexOf("TRIHD:");
                //add a space
                StringBuilder fixer = new StringBuilder(halfdurationLine);
                fixer.insert(indexHD + 6, " ");
                // overwrite the line
                lines.set(n * 5 + 1, fixer.toString());
            }
            if (halfdurationLine.contains("BOXHD:") && !halfdurationLine.contains("BOXHD: ")) {
                // get position of half duration
                int indexHD = halfdurationLine.indexOf("BOXHD:");
                StringBuilder fixer = new StringBuilder(halfdurationLine);
                //add a space
                fixer.insert(indexHD + 6, " ");
                // overwrite the line
                lines.set(n * 5 + 1, fixer.toString());
            }

        }

        // overwrite existing file
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(catalogPath, StandardOpenOption.TRUNCATE_EXISTING))) {
            lines.forEach(pw::println);
        }
    }

}
