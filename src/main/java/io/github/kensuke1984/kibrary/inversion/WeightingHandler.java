package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
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
    public static WeightingHandler IDENTITY = new WeightingHandler(new Property());

    private boolean amplitudeReciprocal;
    private boolean balanceComponent;
    private double factorForZComponent;
    private double factorForRComponent;
    private double factorForTComponent;
    private boolean balancePhase; //TODO Phases containing "p", "P", or "K" are now judged as PSV phase
    private boolean balanceDistance; // TODO apply
    private boolean balanceAzimuth; // TODO apply
    private boolean balanceGeometry;

    private List<DataFeature> dataFeatures; // TODO apply

    /**
     * Create default properties file.
     * @param args [option]
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
        options.addOption(Option.builder("t").longOpt("tag").hasArg().argName("tag")
                .desc("A tag to include in output file name.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;
        Path outputPath = Paths.get(DatasetAid.generateOutputFileName("weighting", tag, GadgetAid.getTemporaryString(), ".properties"));
        writeDefaultPropertiesFile(outputPath);
    }

    public static void writeDefaultPropertiesFile(Path outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("##(boolean) Whether to multiply reciprocal of amplitude. (false)");
            pw.println("#amplitudeReciprocal ");
            pw.println("##(boolean) Whether to weigh the number of timewindows of each component. (false)");
            pw.println("#balanceComponent ");
            pw.println("##(double) Factor to multiply to Z component. (1.0)");
            pw.println("#factorForZComponent ");
            pw.println("##(double) Factor to multiply to R component. (1.0)");
            pw.println("#factorForRComponent ");
            pw.println("##(double) Factor to multiply to T component. (1.0)");
            pw.println("#factorForTComponent ");
            pw.println("##(boolean) Whether to weigh the number of timewindows of SH & PSV pahses. (false)");
            pw.println("#balancePhase ");
//            pw.println("##(boolean) Whether to balance epicentral distances. (false)");
//            pw.println("#balanceDistance ");
//            pw.println("##(boolean) Whether to balance azimuths. (false)");
//            pw.println("#balanceAzimuth ");
            pw.println("##(boolean) Whether to balance event & observer positions. (false)");
            pw.println("#balanceGeometry ");
        }
        System.err.println(outPath + " is created.");
    }

    public WeightingHandler(Path propertyPath) throws IOException {
        Property property = new Property();
        property.load(Files.newBufferedReader(propertyPath));
        set(property);
    }

    private WeightingHandler(Property property) {
        set(property);
    }

    private void set(Property property) {
        amplitudeReciprocal = property.parseBoolean("amplitudeReciprocal", "false");
        balanceComponent = property.parseBoolean("balanceComponent", "false");
        factorForZComponent = property.parseDouble("factorForZComponent", "1.0");
        factorForRComponent = property.parseDouble("factorForRComponent", "1.0");
        factorForTComponent = property.parseDouble("factorForTComponent", "1.0");
        balancePhase = property.parseBoolean("balancePhase", "false");
        balanceDistance = property.parseBoolean("balanceDistance", "false");
        balanceAzimuth = property.parseBoolean("balanceAzimuth", "false");
        balanceGeometry = property.parseBoolean("balanceGeometry", "false");
    }

    /**
     * Decide weighting for a d vector.
     * Caution: {@link RealVector} is not immutable, so be careful when handing it over to other methods without deep-copying!
     *
     * @param dVector
     * @return
     */
    public RealVector[] weighWaveforms(DVectorBuilder dVector) {
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

        int numSH = 0, numPSV =0;
        if (balancePhase) {
            for (int i = 0; i < dVector.getNTimeWindow(); i++) {
                boolean isPSV = false, isSH = false;
                Phase[] phases = dVector.getObsID(i).getPhases();
                for (Phase phase : phases) {
                    if (phase.isPSV()) isPSV = true;
                    else isSH = true;
                }
                if (isPSV) numPSV++;
                if (isSH) numSH++;
            }
        }

        //~compute weight for each timewindow
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            double weighting = 1.0;

            // multiply reciprocal of amplitude
            if (amplitudeReciprocal) weighting /= dVector.getObsVec(i).getLInfNorm();

            // balance component and multiply factor for each component
            // Take square root for number of each component because weighting matrix W will be multiplied twice, as tAWWAm=tAWWd
            SACComponent component = dVector.getObsID(i).getSacComponent();
            switch (component) {
            case Z:
                weighting *= factorForZComponent;
                if (balanceComponent) weighting /= Math.sqrt((double) numZ / (numZ + numR + numT));
                break;
            case R:
                weighting *= factorForRComponent;
                if (balanceComponent) weighting /= Math.sqrt((double) numR / (numZ + numR + numT));
                break;
            case T:
                weighting *= factorForTComponent;
                if (balanceComponent) weighting /= Math.sqrt((double) numT / (numZ + numR + numT));
                break;
            }

            if (balancePhase) {
                boolean isPSV = false, isSH = false;
                Phase[] phases = dVector.getObsID(i).getPhases();
                for (Phase phase : phases) {
                    if (phase.isPSV()) isPSV = true;
                    else isSH = true;
                }
                if (isPSV) weighting /= Math.sqrt((double) numPSV / (numSH + numPSV));
                if (isSH) weighting /= Math.sqrt((double) numSH / (numSH + numPSV));
            }

            // balance event & observer positions
            if (balanceGeometry) {
                weighting /= computeGeometryWeight(dVector.getObsID(i), dVector);
            }

            //TODO
//            if (i % 500 == 0) {
//                System.err.println(dVector.getObsID(i));
//                System.err.println(" " + weighting);
//            }

            //~create vector with the value 'weighting' for the whole timewindow
            double[] ws = new double[dVector.getObsVec(i).getDimension()];
            for (int j = 0; j < ws.length; j++) {
                ws[j] = weighting;
            }
            weightingVectors[i] = new ArrayRealVector(ws);
        }

        return weightingVectors;
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
