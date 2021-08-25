package io.github.kensuke1984.kibrary.util.globalcmt.update;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.Utilities;

/**
 * Updating the catalog of global CMT solutions.
 * <p>
 * The old version of the catalog will be moved to a backup directory before downloading the new one.
 *
 * @author Keisuke Otsuru
 * @version 0.1.8
 */
public final class GlobalCMTCatalogUpdate {

	private final static Path SHARE_DIR_PATH = Environment.KIBRARY_HOME.resolve("share");
    private final static Path CATALOG_PATH = Environment.KIBRARY_HOME.resolve("share/globalcmt.catalog"); //globalcmt.catalog linacmt.catalog synthetics.catalog NDK_no_rm200503211243A NDK_CMT_20170807.catalog
    //TODO get path from GCMTCatalog

    private GlobalCMTCatalogUpdate() {
    }

    private static void downloadCatalog() throws IOException {
        Utilities.download(new URL("http://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/jan76_dec20.ndk"), CATALOG_PATH, false);
    }

    private static void backupCatalog() throws IOException {
        Path backupPath = SHARE_DIR_PATH.resolve("backup");
        Files.createDirectories(backupPath);

        if (Files.exists(CATALOG_PATH)) {
            Utilities.moveToDirectory(CATALOG_PATH, backupPath, true);
        }
    }

    public static void main(String[] args) {
    	try {
    		backupCatalog();
    		downloadCatalog();
    	} catch (IOException e) {
            e.printStackTrace();
        }

    	System.err.println("finished");
    }
}

