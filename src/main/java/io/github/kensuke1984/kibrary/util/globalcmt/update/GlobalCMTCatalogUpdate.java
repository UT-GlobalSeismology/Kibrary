package io.github.kensuke1984.kibrary.util.globalcmt.update;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.Utilities;

/**
 * Updating the catalog of global CMT solutions.
 * <p>
 * The specified version of the catalog will be downloaded if it does not already exist.
 * The active version of the catalog will be set to the one specified.
 *
 * @author Keisuke Otsuru
 * @version 0.0.1
 */
public final class GlobalCMTCatalogUpdate {

    private final static Path SHARE_DIR_PATH = Environment.KIBRARY_HOME.resolve("share");
    private final static Path CATALOG_PATH = Environment.KIBRARY_HOME.resolve("share/globalcmt.catalog"); //globalcmt.catalog linacmt.catalog synthetics.catalog NDK_no_rm200503211243A NDK_CMT_20170807.catalog
    //TODO get path from GCMTCatalog

    private GlobalCMTCatalogUpdate() {
    }

    private static void downloadCatalog(String update) throws IOException {
        String catalogName = "jan76_" + update + ".ndk";
        Path catalogPath = SHARE_DIR_PATH.resolve(catalogName);

        // Download
        if (Files.exists(catalogPath)) {
            System.err.println("Catalog " + catalogName + " already exists.");
        }
        else {
            System.err.println("Downloading catalog " + catalogName + " ...");

            String catalogUrl = "https://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/" + catalogName;
            Utilities.download(new URL(catalogUrl), catalogPath, false);
        }

        // Activate
        if(Files.exists(CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
            // checks whether the symbolic link itself exists, regardless of the existence of its target
            Files.delete(CATALOG_PATH);
        }
        Files.createSymbolicLink(CATALOG_PATH, catalogPath);

        System.err.println("Catalog is set to " + catalogName);

    }

//    private static void linkCatalog() throws IOException {
//        Path backupPath = SHARE_DIR_PATH.resolve("backup");
//        Files.createDirectories(backupPath);

//        if (Files.exists(CATALOG_PATH)) {
//           Utilities.moveToDirectory(CATALOG_PATH, backupPath, true, StandardCopyOption.REPLACE_EXISTING);
//        }
//    }

    /**
     * @param args [month and year of update]<br>
     *             Should take the form mmmYY,
     *             where mmm is the first three letters of the name of the month,
     *             and YY is the lower two digits of the year.
     * @throws IOException if any
     */
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException(
                "Usage:[month and year of update] Should take the form mmmYY, where mmm is the first three letters of the name of the month, and YY is the lower two digits of the year.");
        try {
            downloadCatalog(args[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("Catalog update finished :)");
    }
}

