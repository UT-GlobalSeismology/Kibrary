package io.github.kensuke1984.kibrary.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * Utility for an event folder.
 * <p>
 * Class File is Comparable, so this class is also Comparable.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class EventFolder extends File {

    private static final long serialVersionUID = 8698976273645876402L;

    private GlobalCMTID eventID;

    public EventFolder(File parent, String child) {
        super(parent, child);
        eventID = new GlobalCMTID(getName());
    }

    public EventFolder(String parent, String child) {
        super(parent, child);
        eventID = new GlobalCMTID(getName());
    }

    public EventFolder(String pathname) {
        super(pathname);
        eventID = new GlobalCMTID(getName());
    }

    public EventFolder(Path path) {
        this(path.toString());
    }

    /**
     * @return {@link GlobalCMTID} of this
     */
    public GlobalCMTID getGlobalCMTID() {
        return eventID;
    }

    @Override
    public String toString() {
        return eventID.toString();
    }

    /**
     * Collect SAC files in this folder.
     * @return (Set of {@link SACFileName}) All SAC file names in this folder, including observed, synthetic, and partial derivatives.
     * @throws IOException
     */
    public Set<SACFileName> sacFileSet() throws IOException {
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(toPath())) {
            return stream.filter(SACFileName::isSacFileName).map(SACFileName::new).collect(Collectors.toSet());
        }
    }

}
