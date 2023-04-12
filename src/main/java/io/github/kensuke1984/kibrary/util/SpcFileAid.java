package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;

/**
 * Utilities for collecting SPC files.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class SpcFileAid {
    private SpcFileAid() {}

    /**
     * @param path {@link Path} to look for {@link FormattedSPCFileName} in
     * @return set of {@link SPCFileName} in the dir
     * @throws IOException if an I/O error occurs
     */
    public static Set<SPCFileName> collectSpcFileName(Path path) throws IOException {
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toSet());
        }
    }

    public static List<SPCFileName> collectOrderedSpcFileNamePFPB(Path path, SPCMode spcMode) throws IOException {
        List<SPCFileName> fileNameList;
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            fileNameList = stream.filter(p -> (p.getFileName().toString().endsWith("PF..." + spcMode + ".spc")
                            || p.getFileName().toString().endsWith("PB..." + spcMode + ".spc")))
                    .sorted(Comparator.comparing(filePath -> filePath.getFileName().toString()))
                    .filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toList());
        }
        if (fileNameList.get(fileNameList.size() - 1).getReceiverID().equals("XY" + fileNameList.size()) == false) {
            throw new IllegalStateException("Error when collecting SPC files in " + path);
        }
        return fileNameList;
    }

    public static List<SPCFileName> collectOrderedSpcFileNameUFUB(Path path, SPCMode spcMode) throws IOException {
        List<SPCFileName> fileNameList;
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(path)) {
            fileNameList = stream.filter(p -> (p.getFileName().toString().endsWith("UF..." + spcMode + ".spc")
                            || p.getFileName().toString().endsWith("UB..." + spcMode + ".spc")))
                    .sorted(Comparator.comparing(filePath -> filePath.getFileName().toString()))
                    .filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toList());
        }
        if (fileNameList.get(fileNameList.size() - 1).getReceiverID().equals("XY" + fileNameList.size()) == false) {
            throw new IllegalStateException("Error when collecting SPC files in " + path);
        }
        return fileNameList;
    }

    public static enum UsableSPCMode {
        SH,
        PSV,
        BOTH
    }

}
