package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.FileAid;

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

    }

    private static void downloadCatalog(String update) throws IOException {
        String catalogName = "jan76_" + update + ".ndk";
        Path catalogPath = Environment.KIBRARY_SHARE.resolve(catalogName);

        //~Download~//
        if (Files.exists(catalogPath)) {
            System.err.println("Catalog " + catalogName + " already exists; skipping download.");
        }
        else {
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

        //~Activate (change target of symbolic link)~//
        // check whether the symbolic link itself exists, regardless of the existence of its target
        if(Files.exists(GlobalCMTCatalog.CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
            // delete the symbolic link, not its target
            Files.delete(GlobalCMTCatalog.CATALOG_PATH);
        }
        Files.createSymbolicLink(GlobalCMTCatalog.CATALOG_PATH, catalogPath);

        System.err.println("The referenced catalog is set to " + catalogName);

    }

}

