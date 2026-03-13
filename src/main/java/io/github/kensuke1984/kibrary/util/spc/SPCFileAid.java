package io.github.kensuke1984.kibrary.util.spc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Utilities for collecting SPC files.
 *
 * @author otsuru
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class SPCFileAid {
    private SPCFileAid() {}

    /**
     * @param path ({@link Path}) Folder in which to look for {@link FormattedSPCFileName}s.
     * @return (Set of {@link SPCFileName}) SPC files in the folder.
     * @throws IOException
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

    /**
     * Compute npts = tlen * samplingHz, ensuring it is a power of 2.
     * @param tlen (double) Time length [s].
     * @param samplingHz (double) Sampling frequency [Hz].
     * @return (int) Number of data points in time domain.
     */
    public static int findNpts(double tlen, double samplingHz) {
        // npts = tlen * samplingHz must be a power of 2.
        if (!MathAid.isInteger(tlen * samplingHz)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        int npts = (int) MathAid.roundForPrecision(tlen * samplingHz);
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        return npts;
    }

    public static enum UsableSPCMode {
        SH,
        PSV,
        BOTH
    }

}
