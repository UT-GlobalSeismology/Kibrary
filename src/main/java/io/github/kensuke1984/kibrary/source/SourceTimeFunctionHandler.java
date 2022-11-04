package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Class to create {@link SourceTimeFunction}s based on various settings.
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

        if (catalogPath != null) {
            readCatalog(catalogPath);
        }
        if (userSTFPath != null) {
            readUserSourceTimeFunctions(userSTFPath, events);
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

    private void readUserSourceTimeFunctions(Path inPath, Set<GlobalCMTID> events) throws IOException {
        userSourceTimeFunctions = new HashMap<>(events.size());
        for (GlobalCMTID event : events)
            userSourceTimeFunctions
                    .put(event, SourceTimeFunction.readSourceTimeFunction(inPath.resolve(event + ".stf")));
    }

    public Map<GlobalCMTID, SourceTimeFunction> createSourceTimeFunctionMap(int np, double tlen, double samplingHz) {
        if (userSTFPath != null) {
            return userSourceTimeFunctions;

        } else {
            Map<GlobalCMTID, SourceTimeFunction> stfMap = new HashMap<>();

            for (GlobalCMTID event : events) {
                stfMap.put(event, createSourceTimeFunction(np, tlen, samplingHz, event));
            }
            return stfMap;
        }
    }

    public SourceTimeFunction createSourceTimeFunction(int np, double tlen, double samplingHz, GlobalCMTID event) {
        double halfDuration = event.getEventData().getHalfDuration();

        if (userSTFPath != null) {
            SourceTimeFunction tmp = userSourceTimeFunctions.get(event);
            if (tmp == null) {
                System.err.println("! Source time function for " + event + " not found, using triangular instead.");
                tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
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
                return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(event)) {
                    String[] ss = sourceTimeFunctionCatalog.get(event).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case ASYMMETRIC_TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(event)) {
                    String[] ss = sourceTimeFunctionCatalog.get(event).split("\\s+");
                    double halfDuration1 = Double.parseDouble(ss[1]);
                    double halfDuration2 = Double.parseDouble(ss[2]);
                    return SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration1, halfDuration2);
                } else {
                    System.err.println("! Catalog data for " + event + " not found, using triangular instead.");
                    return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
                }
            default:
                throw new RuntimeException("Invalid source time function type.");
            }
        }
    }


}
