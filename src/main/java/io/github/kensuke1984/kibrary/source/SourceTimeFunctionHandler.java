package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Class to create {@link SourceTimeFunction}s based on various settings.
 *
 * @author otsuru
 * @since 2022/11/3
 */
public class SourceTimeFunctionHandler {

    private final SourceTimeFunctionType type;
    private final Path userSTFPath;
    private final Path catalogPath;
    private Set<GlobalCMTID> events;
    private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;
    private Map<GlobalCMTID, String> sourceTimeFunctionCatalog;

    public SourceTimeFunctionHandler(SourceTimeFunctionType type, Path catalogPath, Path userSTFPath, Set<GlobalCMTID> events) throws IOException {
        this.type = type;
        this.userSTFPath = userSTFPath;
        this.catalogPath = catalogPath;
        this.events = events;

        if (userSTFPath != null) {
            readUserSourceTimeFunctions(userSTFPath);
            System.err.println("Using user-defined STFs.");
        } else {
            if (catalogPath != null) {
                readCatalog(catalogPath);
            }
            System.err.println("STF type: " + type);
        }
    }

    private void readCatalog(Path inputPath) throws IOException {
        System.err.println("STF catalogue: " + inputPath);
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        while(reader.hasNext()) {
            String line = reader.next();
            String[] parts = line.split("\\s+");
            sourceTimeFunctionCatalog.put(new GlobalCMTID(parts[0]), line);
        }
    }

    private void readUserSourceTimeFunctions(Path inPath) throws IOException {
        // collect STF files
        Set<Path> stfPaths;
        // CAUTION: Files.list() must be in try-with-resources.
        try (Stream<Path> stream = Files.list(inPath)) {
            stfPaths = stream.filter(dir -> dir.getFileName().toString().endsWith(".stf")).collect(Collectors.toSet());
        }

        userSourceTimeFunctions = new HashMap<>();
        for (Path stfPath: stfPaths) {
            GlobalCMTID event = new GlobalCMTID(stfPath.getFileName().toString().split("\\.")[0]);
            userSourceTimeFunctions.put(event, SourceTimeFunction.read(stfPath));
        }
    }

    public Map<GlobalCMTID, SourceTimeFunction> createSourceTimeFunctionMap(int np, double tlen) {
        if (userSTFPath != null) {
            return userSourceTimeFunctions;

        } else {
            Map<GlobalCMTID, SourceTimeFunction> stfMap = new HashMap<>();

            for (GlobalCMTID event : events) {
                stfMap.put(event, createSourceTimeFunction(np, tlen, event));
            }
            return stfMap;
        }
    }

    public SourceTimeFunction createSourceTimeFunction(int np, double tlen, GlobalCMTID event) {
        double halfDuration = event.getEventData().getHalfDuration();

        if (userSTFPath != null) {
            SourceTimeFunction tmp = userSourceTimeFunctions.get(event);
            if (tmp == null) {
                System.err.println("! Source time function for " + event + " not found, using triangular instead.");
                tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, halfDuration);
            } else if (tmp.getNp() != np || !Precision.equals(tmp.getTlen(), tlen)) {
                System.err.println("! Input np " + tmp.getNp() + " and tlen " + tmp.getTlen() + " do not match requirements, using triangular instead.");
                tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, halfDuration);
            }
            return tmp;

        } else {
            // look up STF type in GCMT catalog when type is AUTO
            SourceTimeFunctionType individualType = type;
            if (type == SourceTimeFunctionType.AUTO) {
                individualType = event.getEventData().getSTFType();
            }

            // create source time function
            switch (individualType) {
            case NONE:
                return null;
            case BOXCAR:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(event)) {
                    String[] ss = sourceTimeFunctionCatalog.get(event).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, halfDuration);
            case TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(event)) {
                    String[] ss = sourceTimeFunctionCatalog.get(event).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, halfDuration);
            case ASYMMETRIC_TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(event)) {
                    String[] ss = sourceTimeFunctionCatalog.get(event).split("\\s+");
                    double halfDuration1 = Double.parseDouble(ss[1]);
                    double halfDuration2 = Double.parseDouble(ss[2]);
                    return SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, halfDuration1, halfDuration2);
                } else {
                    System.err.println("! Catalog data for " + event + " not found, using triangular instead.");
                    return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, halfDuration);
                }
            default:
                throw new RuntimeException("Invalid source time function type.");
            }
        }
    }

}
