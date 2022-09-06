package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;

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
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(SPCFileName::isFormatted).map(FormattedSPCFileName::new).collect(Collectors.toSet());
        }
    }

    /**
     * @param path
     * @return
     * @throws IOException
     * @author anselme
     */
    public static List<SPCFileName> collectOrderedSpcFileName(Path path) throws IOException {
        List<SPCFileName> list = new ArrayList<>();
        List<Path> paths = Files.list(path).filter(p -> p.getFileName().toString().endsWith(".spc")).collect(Collectors.toList());
        int n = paths.size();
        int ndigits = 1;
        String formatter = null;
        if (n < 10) {
            ndigits = 1;
            formatter = "XY%d";
        }
        else if (n < 1e2) {
            ndigits = 2;
            formatter = "XY%02d";
        }
        else if (n < 1e3) {
            ndigits = 3;
            formatter = "XY%03d";
        }
        else if (n < 1e4) {
            ndigits = 4;
            formatter = "XY%04d";
        }
        else if (n < 1e5) {
            ndigits = 5;
            formatter = "XY%05d";
        }
        else
            throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

        String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
        Path root = paths.get(0).getParent();

        for (int i = 1; i <= n; i++) {
            String filename = String.format(formatter + template, i);
            list.add(new FormattedSPCFileName(root.resolve(filename)));
        }
        return list;
    }

    /**
     * @param path
     * @return
     * @throws IOException
     * @author anselme
     */
    public static List<SPCFileName> collectOrderedSHSpcFileName(Path path) throws IOException {
        List<SPCFileName> list = new ArrayList<>();
        List<Path> paths = Files.list(path).filter(p -> (p.getFileName().toString().endsWith("PB...SH.spc") || p.getFileName().toString().endsWith("PF...SH.spc"))).collect(Collectors.toList());
        int n = paths.size();
        int ndigits = 1;
        String formatter = null;
        if (n < 10) {
            ndigits = 1;
            formatter = "XY%d";
        }
        else if (n < 1e2) {
            ndigits = 2;
            formatter = "XY%02d";
        }
        else if (n < 1e3) {
            ndigits = 3;
            formatter = "XY%03d";
        }
        else if (n < 1e4) {
            ndigits = 4;
            formatter = "XY%04d";
        }
        else if (n < 1e5) {
            ndigits = 5;
            formatter = "XY%05d";
        }
        else
            throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

        String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
        Path root = paths.get(0).getParent();

        for (int i = 1; i <= n; i++) {
            String filename = String.format(formatter + template, i);
            list.add(new FormattedSPCFileName(root.resolve(filename)));
        }
        return list;
    }

    /**
     * @param path
     * @return
     * @throws IOException
     * @author rei
     */
    public static List<SPCFileName> collectOrderedUFUBSHSpcFileName(Path path) throws IOException {
        List<SPCFileName> list = new ArrayList<>();
        List<Path> paths = Files.list(path).filter(p -> (p.getFileName().toString().endsWith("UB...SH.spc")) || p.getFileName().toString().endsWith("UF...SH.spc")).collect(Collectors.toList());
        int n = paths.size();
        int ndigits = 1;
        String formatter = null;
        if (n < 10) {
            ndigits = 1;
            formatter = "XY%d";
        }
        else if (n < 1e2) {
            ndigits = 2;
            formatter = "XY%02d";
        }
        else if (n < 1e3) {
            ndigits = 3;
            formatter = "XY%03d";
        }
        else if (n < 1e4) {
            ndigits = 4;
            formatter = "XY%04d";
        }
        else if (n < 1e5) {
            ndigits = 5;
            formatter = "XY%05d";
        }
        else
            throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

        String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
        Path root = paths.get(0).getParent();

        for (int i = 1; i <= n; i++) {
            String filename = String.format(formatter + template, i);
            list.add(new FormattedSPCFileName(root.resolve(filename)));
        }
        return list;
    }

    /**
     * @param fpPath
     * @param model
     * @return
     * @throws IOException
     * @author anselme
     */
    public static Map<GlobalCMTID, List<SPCFileName>> collectMapOfOrderedSHFpFileName(Path fpPath, String model) throws IOException {
        Map<GlobalCMTID, List<SPCFileName>> fpfileMap = new HashMap<>();
        Set<EventFolder> events = DatasetAid.eventFolderSet(fpPath);
        for (EventFolder event : events) {
            Path path = fpPath.resolve(event.getGlobalCMTID().toString()).resolve(model);
            List<SPCFileName> list = new ArrayList<>();
            List<Path> paths = Files.list(path).filter(p -> p.getFileName().toString().endsWith("SH.spc")).collect(Collectors.toList());
            int n = paths.size();

            if (n == 0)
                continue;

            int ndigits = 1;
            String formatter = null;
            if (n < 10) {
                ndigits = 1;
                formatter = "XY%d";
            }
            else if (n < 1e2) {
                ndigits = 2;
                formatter = "XY%02d";
            }
            else if (n < 1e3) {
                ndigits = 3;
                formatter = "XY%03d";
            }
            else if (n < 1e4) {
                ndigits = 4;
                formatter = "XY%04d";
            }
            else if (n < 1e5) {
                ndigits = 5;
                formatter = "XY%05d";
            }
            else
                throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

            String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
            Path root = paths.get(0).getParent();

            for (int i = 1; i <= n; i++) {
                String filename = String.format(formatter + template, i);
                list.add(new FormattedSPCFileName(root.resolve(filename)));
            }

            fpfileMap.put(event.getGlobalCMTID(), list);
        }

        return fpfileMap;
    }

    /**
     * @param path
     * @return
     * @throws IOException
     * @author anselme
     */
    public static List<SPCFileName> collectOrderedPSVSpcFileName(Path path) throws IOException {
        List<SPCFileName> list = new ArrayList<>();
        List<Path> paths = Files.list(path).filter(p -> (p.getFileName().toString().endsWith("PB...PSV.spc") || p.getFileName().toString().endsWith("PF...PSV.spc"))).collect(Collectors.toList());
        int n = paths.size();
        int ndigits = 1;
        String formatter = null;
        if (n < 10) {
            ndigits = 1;
            formatter = "XY%d";
        }
        else if (n < 1e2) {
            ndigits = 2;
            formatter = "XY%02d";
        }
        else if (n < 1e3) {
            ndigits = 3;
            formatter = "XY%03d";
        }
        else if (n < 1e4) {
            ndigits = 4;
            formatter = "XY%04d";
        }
        else if (n < 1e5) {
            ndigits = 5;
            formatter = "XY%05d";
        }
        else
            throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

        String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
        Path root = paths.get(0).getParent();

        for (int i = 1; i <= n; i++) {
            String filename = String.format(formatter + template, i);
            list.add(new FormattedSPCFileName(root.resolve(filename)));
        }
        return list;
    }

    /**
     * @param path
     * @return
     * @throws IOException
     * @author rei
     */
    public static List<SPCFileName> collectOrderedUFUBPSVSpcFileName(Path path) throws IOException {
        List<SPCFileName> list = new ArrayList<>();
        List<Path> paths = Files.list(path).filter(p -> (p.getFileName().toString().endsWith("UB...PSV.spc")) || p.getFileName().toString().endsWith("UF...PSV.spc")).collect(Collectors.toList());
        int n = paths.size();
        int ndigits = 1;
        String formatter = null;
        if (n < 10) {
            ndigits = 1;
            formatter = "XY%d";
        }
        else if (n < 1e2) {
            ndigits = 2;
            formatter = "XY%02d";
        }
        else if (n < 1e3) {
            ndigits = 3;
            formatter = "XY%03d";
        }
        else if (n < 1e4) {
            ndigits = 4;
            formatter = "XY%04d";
        }
        else if (n < 1e5) {
            ndigits = 5;
            formatter = "XY%05d";
        }
        else
            throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

        String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
        Path root = paths.get(0).getParent();

        for (int i = 1; i <= n; i++) {
            String filename = String.format(formatter + template, i);
            list.add(new FormattedSPCFileName(root.resolve(filename)));
        }
        return list;
    }

    /**
     * @param fpPath
     * @param model
     * @return
     * @throws IOException
     * @author anselme
     */
    public static Map<GlobalCMTID, List<SPCFileName>> collectMapOfOrderedPSVFpFileName(Path fpPath, String model) throws IOException {
        Map<GlobalCMTID, List<SPCFileName>> fpfileMap = new HashMap<>();
        Set<EventFolder> events = DatasetAid.eventFolderSet(fpPath);
        for (EventFolder event : events) {
            Path path = fpPath.resolve(event.getGlobalCMTID().toString()).resolve(model);
            List<SPCFileName> list = new ArrayList<>();
            List<Path> paths = Files.list(path).filter(p -> p.getFileName().toString().endsWith("PSV.spc")).collect(Collectors.toList());
            int n = paths.size();

            if (n == 0)
                continue;

            int ndigits = 1;
            String formatter = null;
            if (n < 10) {
                ndigits = 1;
                formatter = "XY%d";
            }
            else if (n < 1e2) {
                ndigits = 2;
                formatter = "XY%02d";
            }
            else if (n < 1e3) {
                ndigits = 3;
                formatter = "XY%03d";
            }
            else if (n < 1e4) {
                ndigits = 4;
                formatter = "XY%04d";
            }
            else if (n < 1e5) {
                ndigits = 5;
                formatter = "XY%05d";
            }
            else
                throw new RuntimeException("Error: case for 1e6 or more perturbation points not implemented");

            String template = paths.get(0).getFileName().toString().substring(ndigits + 2);
            Path root = paths.get(0).getParent();

            for (int i = 1; i <= n; i++) {
                String filename = String.format(formatter + template, i);
                list.add(new FormattedSPCFileName(root.resolve(filename)));
            }

            fpfileMap.put(event.getGlobalCMTID(), list);
        }

        return fpfileMap;
    }

}
