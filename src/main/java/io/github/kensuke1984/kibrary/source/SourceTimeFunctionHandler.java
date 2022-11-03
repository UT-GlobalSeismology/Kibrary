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
    private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;
    private Map<GlobalCMTID, String> sourceTimeFunctionCatalog;

    public SourceTimeFunctionHandler(SourceTimeFunctionType type, Path catalogPath, Path userSTFPath, Set<GlobalCMTID> ids) throws IOException {
        this.type = type;
        this.userSTFPath = userSTFPath;
        this.catalogPath = catalogPath;

        if (catalogPath != null) {
            readCatalog(catalogPath);
        }
        if (userSTFPath != null) {
            readUserSourceTimeFunctions(userSTFPath, ids);
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

    private void readUserSourceTimeFunctions(Path inPath, Set<GlobalCMTID> ids) throws IOException {
        userSourceTimeFunctions = new HashMap<>(ids.size());
        for (GlobalCMTID id : ids)
            userSourceTimeFunctions
                    .put(id, SourceTimeFunction.readSourceTimeFunction(inPath.resolve(id + ".stf")));
    }

    public SourceTimeFunction createSourceTimeFunction(int np, double tlen, double samplingHz, GlobalCMTID id) {
        double halfDuration = id.getEventData().getHalfDuration();

        if (userSTFPath != null) {
            SourceTimeFunction tmp = userSourceTimeFunctions.get(id);
            if (tmp == null) {
                System.err.println("! Source time function for " + id + " not found, using triangular instead.");
                tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            }
            return tmp;

        } else {
            // look up STF type in GCMT catalog when type is AUTO
            SourceTimeFunctionType individualType = type;
            if (type == SourceTimeFunctionType.AUTO) {
                individualType = id.getEventData().getSTFType();
            }

            // create source time function
            switch (individualType) {
            case NONE:
                return null;
            case BOXCAR:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case ASYMMETRIC_TRIANGLE:
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    double halfDuration1 = Double.parseDouble(ss[1]);
                    double halfDuration2 = Double.parseDouble(ss[2]);
                    return SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration1, halfDuration2);
                } else {
                    System.err.println("! Catalog data for " + id + " not found, using triangular instead.");
                    return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
                }
            default:
                throw new RuntimeException("Invalid source time function type.");
            }
        }
    }


}
