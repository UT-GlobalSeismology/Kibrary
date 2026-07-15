package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;
import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.FileAid;

/**
 * Updating the catalog of Global CMT solutions.
 * <p>
 * The specified version of the catalog will be downloaded if it does not already exist.
 * Recent monthly catalogs can be downloaded and merged into the master catalog.
 * The active version of the catalog will be set to the one specified.
 * <p>
 * Available versions of catalogs can be checked at
 * <a href="https://www.globalcmt.org/CMTfiles.html">https://www.globalcmt.org/CMTfiles.html</a>.
 *
 * @since 2021/8/25
 * @author otsuru
 *
 * @version 2026/7/15 Replaced with contents of GlobalCMTCatalogUpToLatest, which was created based on GlobalCMTCatalogUpdate.
 * @author kataoka
 */
public final class GlobalCMTCatalogUpdate {
    private GlobalCMTCatalogUpdate() {}

    /**
     * Path to the directory where individual NDK files are saved.
     */
    private static final Path CATALOG_DIRECTORY_PATH = Environment.KIBRARY_SHARE.resolve("eachMonth");

    /**
     * option flag for catalog version of "AllEvents"
     */
    private static final String VERSION_OPTION = "v";

    /**
     * option flag for last month of "monthly" catalog
     */
    private static final String LAST_MONTH_OPTION = "M";

    /**
     * Upadate the catalog of Global CMT solutions.
     * @param args (String[]) Options.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return (Options) Options that can be specified by user.
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder(VERSION_OPTION).longOpt("version").hasArg().argName("mmmYY").required()
                .desc("The month and year the version of the catalog is up to, "
                        + "with mmm as the first three letters of the name of the month (lower case), "
                        + "and YY as the lower two digits of the year.").build());
        options.addOption(Option.builder(LAST_MONTH_OPTION).longOpt("lastMonth").hasArg().argName("mmmYY")
                .desc("The last month and year of monthly catalog to use, "
                        + "with mmm as the first three letters of the name of the month (lower case), "
                        + "and YY as the lower two digits of the year.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine (CommandLine) Options specified by user.
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        switchCatalog(cmdLine.getOptionValue(VERSION_OPTION), cmdLine.getOptionValue(LAST_MONTH_OPTION));
    }

    private static void switchCatalog(String version, String lastMonth) throws IOException {
        Path targetCatalogPath;
        String targetCatalogName;
        Files.createDirectories(CATALOG_DIRECTORY_PATH);

        // convert options to YearMonth objects
        YearMonth allEventsMonth = parseVersion(version);

        // download ndk files from "all events"
        String catalogNameofAllEvents = "jan76_" + version + ".ndk";
        String catalogURLofAllEvents = "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/" + catalogNameofAllEvents;
        downloadCatalog(catalogNameofAllEvents, catalogURLofAllEvents);

        if (lastMonth == null) {
            targetCatalogPath = CATALOG_DIRECTORY_PATH.resolve(catalogNameofAllEvents);
            targetCatalogName = catalogNameofAllEvents;
        } else {
            // check whether "monthlyMonth" is after "allEventsMonth"
            YearMonth monthlyMonth = parseVersion(lastMonth);
            if (!monthlyMonth.isAfter(allEventsMonth)) {
                throw new IllegalArgumentException("The last month of monthly catalog must be after the version of AllEvents catalog.");
            }
            // download ndk files from "monthly"
            DateTimeFormatter catalogMonthFormatter = DateTimeFormatter.ofPattern("MMMyy", Locale.ENGLISH);
            YearMonth firstMonth = allEventsMonth.plusMonths(1);
            for (YearMonth eventMonth = firstMonth; !eventMonth.isAfter(monthlyMonth); eventMonth = eventMonth.plusMonths(1)) {
                String eventURL =
                        "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/NEW_MONTHLY/" + eventMonth.getYear() + "/";
                String ndkFile = eventMonth.format(catalogMonthFormatter).toLowerCase(Locale.ENGLISH) + ".ndk";
                String fullURL = eventURL + ndkFile;
                downloadCatalog(ndkFile, fullURL);
            }

            //~Variables for the merged catalog~//
            targetCatalogName = "jan76_" + lastMonth + ".ndk";
            targetCatalogPath = Environment.KIBRARY_SHARE.resolve(targetCatalogName);
            //~Merge all ndk files into one~//
            mergeCatalog(CATALOG_DIRECTORY_PATH.resolve(catalogNameofAllEvents), targetCatalogPath, targetCatalogName, allEventsMonth, monthlyMonth);
        }

        //~Activate (change target of symbolic link)~//
        // check whether the symbolic link itself exists, regardless of the existence of its target
        if (Files.exists(GlobalCMTCatalog.CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
            // delete the symbolic link, not its target
            Files.delete(GlobalCMTCatalog.CATALOG_PATH);
        }
        Files.createSymbolicLink(GlobalCMTCatalog.CATALOG_PATH, targetCatalogPath);
        System.err.println("The referenced catalog is set to " + targetCatalogName);
    }

    //~Method to convert a string in mmmYY to a YearMonth object ~//
    private static YearMonth parseVersion(String value) {
        if (!value.matches("[a-z]{3}\\d{2}")) {
            throw new IllegalArgumentException("Invalid catalog month: " + value + " Expected format: mmmYY.");
        }
        String monthString = value.substring(0, 3);
        int year = 2000 + Integer.parseInt(value.substring(3, 5));
        DateTimeFormatter inputCatalogMonthFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM").toFormatter(Locale.ENGLISH);

        try {
            Month month = Month.from(inputCatalogMonthFormatter.parse(monthString));
            return YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid month: " + monthString, e);
        }
    }

    //~Method to download GCMT catalog~//
    private static void downloadCatalog(String catalogName, String catalogURL) throws IOException {
        Path catalogPath = CATALOG_DIRECTORY_PATH.resolve(catalogName);
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

    //~Method to merge all ndk files into one
    private static void mergeCatalog(Path oldCatalog, Path mergedCatalogPath, String mergedCatalogName,
            YearMonth allEventMonth, YearMonth monthlyMonth) throws IOException {
        DateTimeFormatter catalogMonthFormatter = DateTimeFormatter.ofPattern("MMMyy", Locale.ENGLISH);
        try (BufferedWriter writer = Files.newBufferedWriter(mergedCatalogPath)) {
            //~Write ndk files from "all events"~//
            for (String line : Files.readAllLines(oldCatalog)) {
                writer.write(line);
                writer.newLine();
            }
            //~Write ndk files from "monthly"~//
            YearMonth firstMonth = allEventMonth.plusMonths(1);
            for (YearMonth eventMonth = firstMonth; !eventMonth.isAfter(monthlyMonth); eventMonth = eventMonth.plusMonths(1)) {
                String ndkFile = eventMonth.format(catalogMonthFormatter).toLowerCase(Locale.ENGLISH) + ".ndk";
                Path monthlyCatalogPath = CATALOG_DIRECTORY_PATH.resolve(ndkFile);

                for (String line : Files.readAllLines(monthlyCatalogPath)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            System.err.println("All catalogs are merged to " + mergedCatalogName);
        }
    }

    //~Method to fix catalog if there are any errors
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
                // add a space
                StringBuilder fixer = new StringBuilder(halfdurationLine);
                fixer.insert(indexHD + 6, " ");
                // overwrite the line
                lines.set(n * 5 + 1, fixer.toString());
            }
            if (halfdurationLine.contains("BOXHD:") && !halfdurationLine.contains("BOXHD: ")) {
                // get position of half duration
                int indexHD = halfdurationLine.indexOf("BOXHD:");
                StringBuilder fixer = new StringBuilder(halfdurationLine);
                // add a space
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
