package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JOptionPane;

import org.apache.commons.io.input.CloseShieldInputStream;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.FileAid;

/**
 * Catalog of global CMT solutions.
 * <p>
 * The catalog contains list of events
 * from <b>1976 January - 2017 September</b>.
 * TODO add the latest catalogs
 *
 * @author Kensuke Konishi
 * @version 0.1.8
 */
public final class GlobalCMTCatalog {

    /**
     * The (symbolic link of) catalog to be referenced.
     * This is set as package private so that {@link GlobalCMTCatalogUpdate} can access this.
     */
    static final Path CATALOG_PATH = Environment.KIBRARY_SHARE.resolve("globalcmt.catalog");

    private static final Set<NDK> NDKs;

    static {
        // if an activated catalog does not exist, download the default catalog
        if (!Files.exists(CATALOG_PATH)) downloadCatalog();

        // read the activated catalog
        Set<NDK> readNDKSet = readCatalog(CATALOG_PATH, true);
        // if the activated catalog cannot be read, ask for another catalog
        if (null == readNDKSet) readNDKSet = readCatalog(selectCatalogFile(), false);

        // set NDK set
        NDKs = Collections.unmodifiableSet(readNDKSet);
    }

    private static void downloadCatalog() {
        Path defaultPath = Environment.KIBRARY_SHARE.resolve("globalcmt_default.catalog");

        try {
            // download default catalog if it does not already exist; otherwise, skip download
            if (!Files.exists(defaultPath)) {
                System.err.println("Downloading default catalog ...");
                FileAid.download(new URL("https://bit.ly/3bl0Ly9"), defaultPath, false);
            }

            //~set symbolic link~//
            // check whether the symbolic link itself exists, regardless of the existence of its target
            if(Files.exists(CATALOG_PATH, LinkOption.NOFOLLOW_LINKS)) {
                // delete symbolic link if it exists
                Files.delete(CATALOG_PATH);
            }
            Files.createSymbolicLink(CATALOG_PATH, defaultPath);
            System.err.println("Default catalog is activated.");
        } catch (Exception e) {
            // even if download fails, there is a chance that another catalog can be selected, so suppress exception here
            e.printStackTrace();
        }
    }

    private static Path selectCatalogFile() {
        Path catalogFile;
        String path = System.getProperty("user.dir");
        do {
            try {
                path = JOptionPane.showInputDialog("A catalog filename?", path);
            } catch (Exception e) {
                System.err.println("A catalog filename?");
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(CloseShieldInputStream.wrap(System.in)))) {
                    path = br.readLine().trim();
                    if (!path.startsWith("/")) path = System.getProperty("user.dir") + "/" + path;
                } catch (Exception e2) {
                    e2.printStackTrace();
                    throw new RuntimeException("No catalog.");
                }
            } finally {
                catalogFile = Paths.get(path);
            }
        } while (!Files.exists(catalogFile));
        return catalogFile;
    }

    /**
     * Reads catalog file and returns NDKs inside.
     *
     * @param catalogPath path of a catalog
     * @param allowNull (boolean) whether null is allowed to be returned
     * @return NDKs in catalogFile
     */
    static Set<NDK> readCatalog(Path catalogPath, boolean allowNull) {
        try {
            List<String> lines = Files.readAllLines(catalogPath);
            if (lines.size() % 5 != 0) throw new Exception(catalogPath + " is broken or invalid.");
            return IntStream.range(0, lines.size() / 5).mapToObj(
                    i -> NDK.read(lines.get(i * 5), lines.get(i * 5 + 1), lines.get(i * 5 + 2), lines.get(i * 5 + 3),
                            lines.get(i * 5 + 4))).collect(Collectors.toSet());
        } catch (NullPointerException e) {
            if (allowNull) {
                return null;
            }
            else {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            if (allowNull) {
                e.printStackTrace();
                return null;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * @param id for the NDK
     * @return NDK of the input id
     */
    static NDK getNDK(GlobalCMTID id) {
        return NDKs.parallelStream().filter(ndk -> ndk.getGlobalCMTID().equals(id)).findAny()
                .orElseThrow(() -> new RuntimeException("No information for " + id));
    }

    /**
     * @return <b>(Unmodifiable)</b>Set of all NDKs
     */
    static Set<NDK> allNDK() {
        return NDKs;
    }

    public static Path getCatalogPath() {
        return CATALOG_PATH;
    }

}
