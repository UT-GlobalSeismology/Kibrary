package io.github.kensuke1984.kibrary.inversion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Class to weigh waveforms.
 *
 * @author otsuru
 * @since 2023/5/13
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
    private double factorForRComponent;
    private double factorForTComponent;
    private double factorForZComponent;


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
            pw.println("##(boolean) Whether to multiply reciprocal of amplitude (false)");
            pw.println("#amplitudeReciprocal ");

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

        //TODO
    }

    public RealVector[] weighWaveforms(DVectorBuilder dVector) {
        RealVector[] weightingVectors = new ArrayRealVector[dVector.getNTimeWindow()];

        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            double weighting = 1.0;

            SACComponent component = dVector.getObsID(i).getSacComponent();
//            BasicID obsID = dVector.getObsID(i);
//            BasicID synID = dVector.getSynID(i);

            if (amplitudeReciprocal) weighting /= dVector.getObsVec(i).getLInfNorm();

            switch (component) {
            case R:
                weighting *= factorForRComponent;
                break;
            case T:
                weighting *= factorForTComponent;
                break;
            case Z:
                weighting *= factorForZComponent;
                break;
            }


            //TODO

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
