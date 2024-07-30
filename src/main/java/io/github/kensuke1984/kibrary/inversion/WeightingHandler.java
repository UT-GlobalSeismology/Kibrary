package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;

/**
 * Class to decide weighting to be applied to A matrix and d vector in Am=d.
 * To be applied as the W matrix in WAm=Wd.
 * It is assumed to be a diagonal matrix, so the diagonal components are stored as a vector.
 * A weighting time-series is set for each timewindow.
 *
 * CAUTION: {@link RealVector} is not immutable, so be careful when handing it over to other methods without deep-copying!
 *
 * @author otsuru
 * @since 2023/5/13 created based on inversion.setup.Weighting, which was created based on part of inversion.Dvector
 */
public class WeightingHandler {

    /**
     * {@link WeightingHandler} created with default settings, thus complete IDENTITY.
     */
    public static final WeightingHandler IDENTITY = new WeightingHandler();

    /**
     * Maximum number of {@link EntryWeightListFile}s that can be handled.
     */
    private static final int MAX_INPUT = 5;

    private WaveformType reciprocalType;
    private SACComponent standardComponent;
    private boolean balanceComponent;
    private double factorForZComponent;
    private double factorForRComponent;
    private double factorForTComponent;
    private boolean balanceGeometry;

    private List<DataFeature> dataFeatures; // TODO apply

    private List<Map<DataEntry, Double>> weightMaps = new ArrayList<>();

    /**
     * Create default properties file.
     * @param args Options.
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();
        // output
        options.addOption(Option.builder("T").longOpt("tag").hasArg().argName("fileTag")
                .desc("A tag to include in output file name.").build());
        options.addOption(Option.builder("O").longOpt("omitDate")
                .desc("Omit date string in output file name.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String fileTag = cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : null;
        boolean appendFileDate = !cmdLine.hasOption("O");
        Path outputPath = DatasetAid.generateOutputFilePath(Paths.get(""), "weighting", fileTag, appendFileDate, null, ".properties");
        writeDefaultPropertiesFile(outputPath);
    }

    private static void writeDefaultPropertiesFile(Path outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("##Type of waveform, from {OBS,SYN}, when to multiply reciprocal of its amplitude.");
            pw.println("#reciprocalType OBS");
            pw.println("##Use amplitude of a specific component when multiplying reciprocal, from {Z, R, T}.");
            pw.println("#standardComponent ");
            pw.println("##(boolean) Whether to weigh the number of timewindows of each component. (false)");
            pw.println("#balanceComponent true");
            pw.println("##(double) Factor to multiply to Z component. (1.0)");
            pw.println("#factorForZComponent ");
            pw.println("##(double) Factor to multiply to R component. (1.0)");
            pw.println("#factorForRComponent ");
            pw.println("##(double) Factor to multiply to T component. (1.0)");
            pw.println("#factorForTComponent ");
            pw.println("##(boolean) Whether to balance event & observer positions. (false)");
            pw.println("#balanceGeometry ");
            pw.println("##########From here on, list up paths of entry weight list files to use.");
            pw.println("########## Up to " + MAX_INPUT + " files can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " file.");
                pw.println("#weightPath" + i + " entryWeight.lst");
            }
        }
        System.err.println(outPath + " is created.");
    }

    /**
     * Set up from a propery file.
     * @param propertyPath (Path) Input property file.
     * @throws IOException
     */
    public WeightingHandler(Path propertyPath) throws IOException {
        Property property = new Property();
        property.readFrom(propertyPath);
        // get path of folder including this property file
        Path parentPath = (propertyPath.getParent() != null) ? propertyPath.getParent() : Paths.get("");
        // set up parameters
        set(property, parentPath);

        System.err.print("Weighting: ");
        if (reciprocalType != null) {
            if (standardComponent != null) System.err.print("reciprocal_" + reciprocalType + "_" + standardComponent + ", ");
            else System.err.print("reciprocal_" + reciprocalType + ", ");
        }
        if (balanceComponent) System.err.print("balanceComponent, ");
        System.err.print("factorZ=" + factorForZComponent + ", ");
        System.err.print("factorR=" + factorForRComponent + ", ");
        System.err.print("factorT=" + factorForTComponent + ", ");
        System.err.println(MathAid.switchSingularPlural(weightMaps.size(), "weight file.",  "weight files."));
    }

    /**
     * Create with default settings, thus weight is complete IDENTITY.
     */
    private WeightingHandler() {
        try {
            set(new Property(), Paths.get(""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void set(Property property, Path parentPath) throws IOException {
        if (property.containsKey("reciprocalType"))
            reciprocalType = WaveformType.valueOf(property.parseString("reciprocalType", null));
        else if (property.parseBoolean("amplitudeReciprocal", "false") == true)     // TODO delete (this is temporarily here for backward compatibility)
            reciprocalType = WaveformType.OBS;                                      // TODO delete (this is temporarily here for backward compatibility)
        if (property.containsKey("standardComponent"))
            standardComponent = SACComponent.valueOf(property.parseString("standardComponent", null));

        balanceComponent = property.parseBoolean("balanceComponent", "false");
        factorForZComponent = property.parseDouble("factorForZComponent", "1.0");
        factorForRComponent = property.parseDouble("factorForRComponent", "1.0");
        factorForTComponent = property.parseDouble("factorForTComponent", "1.0");
        balanceGeometry = property.parseBoolean("balanceGeometry", "false");

        for (int i = 1; i <= MAX_INPUT; i++) {
            String weightKey = "weightPath" + i;
            if (property.containsKey(weightKey)) {
                Path weightPath = property.parsePath(weightKey, null, true, parentPath);
                Map<DataEntry, Double> weightMap = EntryWeightListFile.read(weightPath);
                weightMaps.add(weightMap);
            }
        }
    }

    /**
     * Decide weighting for a d vector.
     * Caution: {@link RealVector} is not immutable, so be careful when handing it over to other methods without deep-copying!
     *
     * @param dVector
     * @return
     */
    public RealVector[] weightWaveforms(DVectorBuilder dVector) {
        RealVector[] weightingVectors = new ArrayRealVector[dVector.getNTimeWindow()];

        // count number of timewindows for each component
        int numZ = 0, numR = 0, numT = 0;
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            switch (dVector.getObsID(i).getSacComponent()) {
            case Z: numZ++; break;
            case R: numR++; break;
            case T: numT++; break;
            }
        }

        //~compute weight for each timewindow
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            double weighting = 1.0;

            // multiply reciprocal of amplitude
            // Square root is not taken for this because partial derivatives in A^T should also be weighted.
            if (reciprocalType == WaveformType.OBS) weighting /= findObsAmplitude(dVector, i, standardComponent);
            else if (reciprocalType == WaveformType.SYN) weighting /= findSynAmplitude(dVector, i, standardComponent);

            // balance component and multiply factor for each component
            // Take square root because weighting matrix W will be multiplied twice, as tAWWAm=tAWWd
            SACComponent component = dVector.getObsID(i).getSacComponent();
            switch (component) {
            case Z:
                weighting *= Math.sqrt(factorForZComponent);
                if (balanceComponent) weighting /= Math.sqrt(numZ / (numZ + numR + numT));
                break;
            case R:
                weighting *= Math.sqrt(factorForRComponent);
                if (balanceComponent) weighting /= Math.sqrt(numR / (numZ + numR + numT));
                break;
            case T:
                weighting *= Math.sqrt(factorForTComponent);
                if (balanceComponent) weighting /= Math.sqrt(numT / (numZ + numR + numT));
                break;
            }

            // balance event & observer positions
            if (balanceGeometry) {
                weighting /= computeGeometryWeight(dVector.getObsID(i), dVector);
            }

            // multiply values specified in weight files
            for (int k = 0; k < weightMaps.size(); k++) {
                DataEntry entry = dVector.getObsID(i).toDataEntry();
                // Take square root because weighting matrix W will be multiplied twice, as tAWWAm=tAWWd
                weighting *= Math.sqrt(weightMaps.get(k).get(entry));
            }

            //~create vector with the value 'weighting' for the whole timewindow
            double[] weightingArray = new double[dVector.getObsVec(i).getDimension()];
            for (int j = 0; j < weightingArray.length; j++) {
                weightingArray[j] = weighting;
            }
            weightingVectors[i] = new ArrayRealVector(weightingArray);
        }

        return weightingVectors;
    }

    private double findObsAmplitude(DVectorBuilder dVector, int i, SACComponent standardComponent) {
        BasicID obsID = dVector.getObsID(i);
        if (standardComponent == null || obsID.getSacComponent().equals(standardComponent)) {
            return dVector.getObsVec(i).getLInfNorm();
        } else {
            DataEntry standardEntry = new DataEntry(obsID.getGlobalCMTID(), obsID.getObserver(), standardComponent);
            for (int j = 0; j < dVector.getNTimeWindow(); j++) {
                if (dVector.getObsID(j).toDataEntry().equals(standardEntry)) {
                    return dVector.getObsVec(j).getLInfNorm();
                }
            }
            throw new IllegalStateException("Pair basicID not found for: " + obsID.toDataEntry());
        }
    }

    private double findSynAmplitude(DVectorBuilder dVector, int i, SACComponent standardComponent) {
        BasicID synID = dVector.getSynID(i);
        if (standardComponent == null || synID.getSacComponent().equals(standardComponent)) {
            return dVector.getSynVec(i).getLInfNorm();
        } else {
            DataEntry standardEntry = new DataEntry(synID.getGlobalCMTID(), synID.getObserver(), standardComponent);
            for (int j = 0; j < dVector.getNTimeWindow(); j++) {
                if (dVector.getSynID(j).toDataEntry().equals(standardEntry)) {
                    return dVector.getSynVec(j).getLInfNorm();
                }
            }
            throw new IllegalStateException("Pair basicID not found for: " + synID.toDataEntry());
        }
    }

    private double computeGeometryWeight(BasicID basicID, DVectorBuilder dVector) {
        HorizontalPosition eventPosition = basicID.getGlobalCMTID().getEventData().getCmtPosition();
        HorizontalPosition observerPosition = basicID.getObserver().getPosition();

        // count number of basicIDs with similar event & observer positions
        int numClose = 0;
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            BasicID otherID = dVector.getObsID(i);
            if (otherID.getSacComponent().equals(basicID.getSacComponent())
                    && otherID.getGlobalCMTID().getEventData().getCmtPosition().computeEpicentralDistanceDeg(eventPosition) < 2.5
                    && otherID.getObserver().getPosition().computeEpicentralDistanceDeg(observerPosition) < 2.5) {
                numClose++;
            }
        }
        // Take square root here because weighting matrix W will be multiplied twice, as tAWWAm=tAWWd
        return Math.sqrt(numClose);
    }
}
