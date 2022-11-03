package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

public class SourceTimeFunctionHandler {

    private final int type;
    private final Path userSTFPath;
    private final Path catalogPath;
    private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;
    private Map<GlobalCMTID, String> sourceTimeFunctionCatalog;

    public SourceTimeFunctionHandler(int type, Path catalogPath, Path userSTFPath, Set<GlobalCMTID> ids) throws IOException {
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

    public SourceTimeFunction getSourceTimeFunction(int np, double tlen, double samplingHz, GlobalCMTID id) {
        double halfDuration = id.getEventData().getHalfDuration();

        if (userSTFPath != null) {
            SourceTimeFunction tmp = userSourceTimeFunctions.get(id);
            if (tmp == null) {
                System.err.println("! Source time function for " + id + " not found, using triangular instead.");
                tmp = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            }
            return tmp;

        } else {
            switch (type) {
            case 0:
                // none
                return null;
            case 1:
                // boxcar
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case 2:
                // triangle
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    halfDuration = Double.parseDouble(ss[1]);
                }
                return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
            case 3:
                // asymmetric triangle
                if (catalogPath != null && sourceTimeFunctionCatalog.containsKey(id)) {
                    String[] ss = sourceTimeFunctionCatalog.get(id).split("\\s+");
                    double halfDuration1 = Double.parseDouble(ss[1]);
                    double halfDuration2 = Double.parseDouble(ss[2]);
                    return SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, samplingHz, halfDuration1, halfDuration2);
                } else {
                    System.err.println("! Catalog data for " + id + " not found, using triangular instead.");
                    return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, samplingHz, halfDuration);
                }
            case 4:
                // auto

            default:
                throw new RuntimeException("Integer for source time function is invalid.");
            }
        }
    }


}
