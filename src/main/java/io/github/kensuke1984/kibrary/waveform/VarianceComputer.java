package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.Weighting;
import io.github.kensuke1984.kibrary.util.MathAid;

public class VarianceComputer {

    /**
     * Exports data files in ascii format.
     *
     * @param args [option]
     * <ul>
     * <li> [-i IDFile] : exports ID file in standard output</li>
     * <li> [-w IDFile WaveformFile] : exports waveforms in event directories under current path</li>
     * </ul>
     * You must specify one or the other.
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
        //input
        options.addOption(Option.builder("i").longOpt("id").hasArg().argName("basicIDFile").required()
                .desc("Input basic ID file").build());
        options.addOption(Option.builder("w").longOpt("waveform").hasArg().argName("basicFile").required()
                .desc("Input basic waveform file").build());
        options.addOption(Option.builder("t").longOpt("type").hasArg().argName("weightingType").required()
                .desc("Weighting type, from {LOWERUPPERMANTLE,RECIPROCAL,TAKEUCHIKOBAYASHI,IDENTITY,FINAL}").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        BasicID[] basicIDs = BasicIDFile.read(Paths.get(cmdLine.getOptionValue("i")), Paths.get(cmdLine.getOptionValue("w")));
        WeightingType weightingType = WeightingType.valueOf(cmdLine.getOptionValue("t"));

        // set DVector
        System.err.println("Setting data for d vector");
        DVectorBuilder dVectorBuilder = new DVectorBuilder(basicIDs);

        // set weighting
        System.err.println("Setting weighting of type " + weightingType);
        Weighting weighting = new Weighting(dVectorBuilder, weightingType, null);

        // assemble d
        System.err.println("Assembling d vector");
        RealVector d = dVectorBuilder.buildWithWeight(weighting);

        // compute variance
        RealVector obs = dVectorBuilder.fullObsVecWithWeight(weighting);
        double normalizedVariance = MathAid.computeVariance(d, obs);
        System.err.println("Normalized variance is " + normalizedVariance);

    }
}
