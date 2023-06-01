package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Catalog of global CMT solutions.
 * <p>
 * The catalog can be updated using {@link GlobalCMTCatalogUpdate}.
 * Virtual events can be added to the custom catalog file using {@link }. TODO
 * <p>
 * When no catalog can be found, a default catalog that contains a list of events
 * from <b>1976 January - 2017 September</b> is downloaded.
 *
 * @author Kensuke Konishi
 * @since version 0.1.8
 */
public final class GlobalCMTCatalog {
    private GlobalCMTCatalog() {}

    /**
     * The (symbolic link of) catalog to be referenced.
     * This is set as package private so that {@link GlobalCMTCatalogUpdate} can access this.
     */
    static final Path CATALOG_PATH = Environment.KIBRARY_SHARE.resolve("globalcmt.catalog");
    static final Path CUSTOM_CATALOG_PATH = Environment.KIBRARY_SHARE.resolve("custom.catalog");

    private static final Set<NDK> NDKs;

    static {
        // if neither an activated catalog nor a custom catalog exists, download the default catalog
        if (!Files.exists(CATALOG_PATH) && !Files.exists(CUSTOM_CATALOG_PATH)) downloadCatalog();

        // read the activated catalog and/or custom catalog
        Set<NDK> readNDKSet = new HashSet<>();
        if (Files.exists(CATALOG_PATH)) {
            readNDKSet.addAll(readCatalog(CATALOG_PATH, false));
        }
        if (Files.exists(CUSTOM_CATALOG_PATH)) {
            readNDKSet.addAll(readCatalog(CUSTOM_CATALOG_PATH, false));
        }
        // if the catalogs cannot be read, ask for another catalog
        if (readNDKSet.size() == 0) readNDKSet = readCatalog(selectCatalogFile(), false);

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
        Path catalogPath;
        try {
            String pathString = "";
            do {
                pathString = GadgetAid.readInputDialogOrLine("A catalog filename?", pathString);
                catalogPath = Paths.get(pathString);
            } while (!Files.exists(catalogPath) || Files.isDirectory(catalogPath));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("No catalog");
        }
        return catalogPath;
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
            if (lines.size() % 5 != 0) throw new IllegalStateException(catalogPath + " is broken or invalid.");
            return IntStream.range(0, lines.size() / 5).mapToObj(
                    i -> NDK.constructFromLines(lines.get(i * 5), lines.get(i * 5 + 1), lines.get(i * 5 + 2), lines.get(i * 5 + 3),
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
     * Add an NDK to the custom catalog file.
     * @param ndk
     */
    static void addInCustom(NDK ndk) throws IOException {
        String[] lines = ndk.toLines();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(CUSTOM_CATALOG_PATH,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            for (String line: lines) {
                pw.println(line);
            }
        }
    }

    static boolean contains(GlobalCMTID id) {
        return NDKs.parallelStream().anyMatch(ndk -> ndk.getGlobalCMTID().equals(id));
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
    static Set<NDK> allNDKs() {
        return NDKs;
    }

}
