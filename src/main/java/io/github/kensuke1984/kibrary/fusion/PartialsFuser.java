package io.github.kensuke1984.kibrary.fusion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Operation that creates new {@link PartialID}s for fused voxels in {@link FusionDesign}
 * by averaging partial waveforms of existing {@link PartialID}s.
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
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

    /**
     * path of partial ID file
     */
    private Path partialIDPath;
    /**
     * path of partial data
     */
    private Path partialPath;
    /**
     * Path of a {@link FusionInformationFile}
     */
    private Path fusionPath;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
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
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        fusionPath = property.parsePath("fusionPath", null, true, workPath);

    }

    @Override
    public void run() throws IOException {
        List<PartialID> forgedPartialIDs = new ArrayList<>();

        // read input
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
        FusionDesign fusionDesign = FusionInformationFile.read(fusionPath);

        // work for each fused parameter
        for (int i = 0; i < fusionDesign.getFusedParameters().size(); i++) {
            UnknownParameter fusedParam = fusionDesign.getFusedParameters().get(i);
            List<UnknownParameter> originalParams = fusionDesign.getOriginalParameters().get(i);

            // collect partialIDs that are for these originalParams
            List<PartialID> originalPartialIDs = new ArrayList<>();
            for (PartialID id : partialIDs) {
                for (UnknownParameter originalParam : originalParams) {
                    if (id.getPartialType().equals(originalParam.getPartialType()) && id.getVoxelPosition().equals(originalParam.getPosition())) {
                        originalPartialIDs.add(id);
                        break;
                    }
                }
            }

            // pair up partialIDs and forge into a new one
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
                // forge partialID for this fusedParam
                forgedPartialIDs.add(forge(pairPartialIDs, fusedParam));
                // remove used IDs from the collected IDs
                for (PartialID id : pairPartialIDs) {
                    originalPartialIDs.remove(id);
                }
            }
        }

        // add forged IDs into the array of all original IDs
        List<PartialID> newPartialIDs = Stream.concat(Arrays.stream(partialIDs), forgedPartialIDs.stream()).collect(Collectors.toList());

        // output
        String dateStr = GadgetAid.getTemporaryString();
        Path idPath = workPath.resolve(DatasetAid.generateOutputFileName("partialID", fileTag, dateStr, ".dat"));
        Path wavePath = workPath.resolve(DatasetAid.generateOutputFileName("partial", fileTag, dateStr, ".dat"));
        PartialIDFile.write(newPartialIDs, idPath, wavePath);
    }

    /**
     * Creates a new {@link PartialID} based on input {@link PartialID}s.
     * TODO Is taking a simple average OK?
     *
     * @param ids
     * @param fusedParam
     * @return
     */
    private PartialID forge(List<PartialID> ids, UnknownParameter fusedParam) {
        // add waveforms
        RealVector sumVector = null;
        for (PartialID id : ids) {
            RealVector vector = new ArrayRealVector(id.getData());
            sumVector = (sumVector == null) ? vector : sumVector.add(vector);
        }
        // compute average waveform
        double[] averageWaveform = sumVector.mapDivide(ids.size()).toArray();

        // create forged ID
        PartialID id0 = ids.get(0);
        PartialID forgedID = new PartialID(id0.getObserver(), id0.getGlobalCMTID(), id0.getSacComponent(),
                id0.getSamplingHz(), id0.getStartTime(), id0.getNpts(), id0.getMinPeriod(), id0.getMaxPeriod(),
                id0.getPhases(), id0.getStartByte(), id0.isConvolved(),
                fusedParam.getPosition(), fusedParam.getPartialType(), averageWaveform);
        return forgedID;
    }
}
