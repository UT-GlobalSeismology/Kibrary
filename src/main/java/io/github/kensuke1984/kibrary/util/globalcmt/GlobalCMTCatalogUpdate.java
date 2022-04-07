package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Updating the catalog of global CMT solutions.
 * <p>
 * The specified version of the catalog will be downloaded if it does not already exist.
 * The active version of the catalog will be set to the one specified.
 * <p>
 * Available versions of catalogs can be checked at
 * <a href="https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/">https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/</a>.
 *
 * @author Keisuke Otsuru
 * @since 2021/8/25
 */
public final class GlobalCMTCatalogUpdate {

    /**
     * @param args [month and year of update]<br>
     *             Should take the form mmmYY,
     *             where mmm is the first three letters of the name of the month,
     *             and YY is the lower two digits of the year.
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println("-----");
            usage().forEach(System.err::println);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return usage
     */
    public static List<String> usage() {
        List<String> usageList = new ArrayList<>();
        usageList.add("Usage: mmmYY");
        usageList.add("  mmmYY : month and year the version of the catalog is up to,");
        usageList.add("    where mmm is the first three letters of the name of the month (lower case),");
        usageList.add("    and YY is the lower two digits of the year.");
        return usageList;
    }

    /**
     * To be called from {@link Summon}.
     * @param args
     * @throws IOException
     */
    public static void run(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Wrong number of arguments");
        switchCatalog(args[0]);
    }

    private static void switchCatalog(String version) throws IOException {
        String catalogName = "jan76_" + version + ".ndk";
        Path catalogPath = Environment.KIBRARY_SHARE.resolve(catalogName);

        //~Download~//
        if (Files.exists(catalogPath)) {
            System.err.println("Catalog " + catalogName + " already exists; skipping download.");
        } else {
            System.err.println("Downloading catalog " + catalogName + " ...");

            String catalogUrl = "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/" + catalogName;
            try {
                FileAid.download(new URL(catalogUrl), catalogPath, false);
            } catch(IOException e) {
                if(Files.exists(catalogPath)) {
                    // delete the trash that may be made
                    Files.delete(catalogPath);
                }
                // If download fails, IOException will be thrown here. Symbolic link will not be changed.
                throw e;
            }
        }

        //~Fix errors in downloaded catalog~//
        fixCatalog(catalogPath);

        //~Activate (change target of symbolic link)~//
        // check whether the symbolic link itself exists, regardless of the existence of its target
        if(Files.exists(GlobalCMTCatalog.CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
            // delete the symbolic link, not its target
            Files.delete(GlobalCMTCatalog.CATALOG_PATH);
        }
        Files.createSymbolicLink(GlobalCMTCatalog.CATALOG_PATH, catalogPath);

        System.err.println("The referenced catalog is set to " + catalogName);

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
            if (MathAid.equalWithinEpsilon(Double.parseDouble(timeStrings[2]), 60.0, 0.001)) {
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
        }

        // overwrite existing file
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(catalogPath, StandardOpenOption.TRUNCATE_EXISTING))) {
            lines.forEach(pw::println);
        }
    }

}

