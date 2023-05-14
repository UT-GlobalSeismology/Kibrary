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

import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.selection.DataFeature;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Class to decide weighting to be applied to A matrix and d vector in Am=d.
 * To be applied as the W matrix in WAm=Wd.
 * It is assumed to be a diagonal matrix, so the diagonal components are stored as a vector.
 * A weighting value is set for each timewindow.
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
    private boolean balanceDistance;
    private boolean balanceAzimuth;
    private boolean balanceGeometry;
    private boolean balanceComponent;
    private double factorForZComponent;
    private double factorForRComponent;
    private double factorForTComponent;

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

            //TODO
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

        //TODO
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

        //~compute weight for each timewindow
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            double weighting = 1.0;

            // multiply reciprocal of amplitude
            if (amplitudeReciprocal) weighting /= dVector.getObsVec(i).getLInfNorm();

            // balance component and multiply factor for each component
            SACComponent component = dVector.getObsID(i).getSacComponent();
            switch (component) {
            case Z:
                weighting *= factorForZComponent;
                if (balanceComponent) weighting /= (numZ / (numZ + numR + numT));
                break;
            case R:
                weighting *= factorForRComponent;
                if (balanceComponent) weighting /= (numR / (numZ + numR + numT));
                break;
            case T:
                weighting *= factorForTComponent;
                if (balanceComponent) weighting /= (numT / (numZ + numR + numT));
                break;
            }


            //TODO
            System.err.println(dVector.getObsID(i));
            System.err.println(" " + dVector.getObsVec(i).getLInfNorm() + " " + weighting);

            // create vector with the value 'weighting' for the whole timewindow
            double[] ws = new double[dVector.getObsVec(i).getDimension()];
            for (int j = 0; j < ws.length; j++) {
                ws[j] = weighting;
            }
            weightingVectors[i] = new ArrayRealVector(ws);
        }

        return weightingVectors;
    }
}
