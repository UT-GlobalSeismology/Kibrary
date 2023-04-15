package io.github.kensuke1984.kibrary.fusion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Operation that creates new {@link PartialID}s for fused voxels in {@link FusionDesign}
 * by averaging partial waveforms of existing {@link PartialID}s.
 * The average is taken considering the volume of each voxel.
 *
 * @author otsuru
 * @since 2022/8/10
 */
public class PartialsFuser extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;

    /**
     * path of partial waveform folder
     */
    private Path partialPath;
    /**
     * Path of a {@link FusionInformationFile}
     */
    private Path fusionPath;

    /**
     * The design of the fusion of unknown parameters
     */
    private FusionDesign fusionDesign;

    List<PartialID> inputPartialIDs;
    List<PartialID> fusedPartialIDs = Collections.synchronizedList(new ArrayList<>());
    /**
     * Number of processed parameters
     */
    private AtomicInteger nProcessedParam = new AtomicInteger();

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##Path of a partial waveform folder, must be set");
            pw.println("#partialPath partial");
            pw.println("##Path of a fusion information file, must be set");
            pw.println("#fusionPath fusion.inf");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsFuser(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        partialPath = property.parsePath("partialPath", null, true, workPath);
        fusionPath = property.parsePath("fusionPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {

        // read input
        inputPartialIDs = PartialIDFile.read(partialPath, true);
        fusionDesign = FusionInformationFile.read(fusionPath);

        // work for each fused parameter
        ExecutorService es = ThreadAid.createFixedThreadPool();
        int nTotalParam = fusionDesign.getFusedParameters().size();
        for (int i = 0; i < nTotalParam; i++) {
            UnknownParameter fusedParam = fusionDesign.getFusedParameters().get(i);
            List<UnknownParameter> originalParams = fusionDesign.getOriginalParameters().get(i);

            es.execute(process(fusedParam, originalParams));
        }
        es.shutdown();
        System.err.println("Fusing parameters ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + Math.ceil(100.0 * nProcessedParam.get() / nTotalParam) + "% of parameters done");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all parameters.");

        // collect fused IDs and the original IDs that are not fused
        List<PartialID> newPartialIDs = inputPartialIDs.stream().filter(id -> !isFused(id)).collect(Collectors.toList());
        newPartialIDs.addAll(fusedPartialIDs);

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "partial", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        PartialIDFile.write(newPartialIDs, outPath);
    }

    private Runnable process(UnknownParameter fusedParam, List<UnknownParameter> originalParams) {
        return () -> {
            try {
                // collect partialIDs that are for these originalParams
                List<PartialID> originalPartialIDs = new ArrayList<>();
                for (PartialID id : inputPartialIDs) {
                    for (UnknownParameter originalParam : originalParams) {
                        if (id.getPartialType().equals(originalParam.getPartialType()) && id.getVoxelPosition().equals(originalParam.getPosition())) {
                            originalPartialIDs.add(id);
                            break;
                        }
                    }
                }

                // pair up partialIDs and fuse into a new one
                // this process is repeated while removing used IDs out of the list
                while (originalPartialIDs.size() > 0) {
                    // get the first ID in list
                    PartialID id0 = originalPartialIDs.get(0);
                    // list to add all pair IDs
                    List<PartialID> pairPartialIDs = new ArrayList<>();
                    pairPartialIDs.add(id0);
                    // add all pair IDs to the list
                    for (int k = 1; k < originalPartialIDs.size(); k++) {
                        PartialID idK = originalPartialIDs.get(k);
                        if (BasicID.isPair(id0, idK)) {
                            pairPartialIDs.add(idK);
                        }
                    }
                    // fuse partialID for this fusedParam
                    fusedPartialIDs.add(fuse(pairPartialIDs, originalParams, fusedParam));
                    // remove used IDs from the collected IDs
                    for (PartialID id : pairPartialIDs) {
                        originalPartialIDs.remove(id);
                    }
                }
            } catch (Exception e) {
                System.err.println("!!! Error on " + fusedParam);
                e.printStackTrace();
            } finally {
                nProcessedParam.incrementAndGet();
            }
        };
    }

    private boolean isFused(PartialID id) {
        List<List<UnknownParameter>> originalParamsList = fusionDesign.getOriginalParameters();
        for (List<UnknownParameter> originalParams : originalParamsList) {
            for (UnknownParameter originalParam : originalParams) {
                if (id.getPartialType().equals(originalParam.getPartialType()) && id.getVoxelPosition().equals(originalParam.getPosition())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a new {@link PartialID} based on input {@link PartialID}s.
     * The average waveform, taken considering the volume of voxels, is computed.
     *
     * @param ids
     * @param fusedParam
     * @return
     */
    private PartialID fuse(List<PartialID> ids, List<UnknownParameter> originalParams, UnknownParameter fusedParam) {
        // add waveforms
        RealVector sumVector = null;
        double volumeTotal = 0;
        for (PartialID id : ids) {
            // find volume of corresponding voxel
            UnknownParameter originalParam = originalParams.stream()
                    .filter(param -> id.getPartialType().equals(param.getPartialType()) && id.getVoxelPosition().equals(param.getPosition()))
                    .findFirst().get();
            double volume = originalParam.getSize();
            volumeTotal += volume;
            // add the waveform, multiplied by volume
            RealVector vector = new ArrayRealVector(id.getData());
            sumVector = (sumVector == null) ? vector.mapMultiply(volume) : sumVector.add(vector.mapMultiply(volume));
        }
        // compute average waveform
        double[] averageWaveform = sumVector.mapDivide(volumeTotal).toArray();

        // create forged ID
        PartialID id0 = ids.get(0);
        PartialID fusedID = new PartialID(id0.getObserver(), id0.getGlobalCMTID(), id0.getSacComponent(),
                id0.getSamplingHz(), id0.getStartTime(), id0.getNpts(), id0.getMinPeriod(), id0.getMaxPeriod(),
                id0.getPhases(), id0.isConvolved(),
                fusedParam.getPosition(), fusedParam.getPartialType(), averageWaveform);
        return fusedID;
    }
}
