package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowData;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowDataFile;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Computes variance of {@link BasicIDFile}s, with the specified weighting.
 *
 * @author otsuru
 * @since 2022/7/22
 */
public class VarianceComputer {

    /**
     * Computes normalized variance of residual waveforms in a basic waveform folder.
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
        //input
        options.addOption(Option.builder("b").longOpt("basic").hasArg().argName("basicFolder").required()
                .desc("Path of basic waveform folder.").build());
        OptionGroup inputOption = new OptionGroup();
        inputOption.setRequired(true);
        inputOption.addOption(Option.builder("w").longOpt("weighting").hasArg().argName("weightingFile")
                .desc("Path of weighting properties file.").build());
        inputOption.addOption(Option.builder("I").longOpt("identity")
                .desc("Use IDENTITY weighting.").build());
        options.addOptionGroup(inputOption);
        options.addOption(Option.builder("p").longOpt("improvement").hasArg().argName("improvementWindowFile")
                .desc("Path of improvement window file, if it is to be used.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        List<BasicID> basicIDs = BasicIDFile.read(Paths.get(cmdLine.getOptionValue("b")), true);

        // decide weighting
        WeightingHandler weightingHandler;
        if (cmdLine.hasOption("w")) {
            weightingHandler = new WeightingHandler(Paths.get(cmdLine.getOptionValue("w")));
        } else if (cmdLine.hasOption("I")) {
            weightingHandler = WeightingHandler.IDENTITY;
        } else {
            throw new IllegalArgumentException("Either -w or -I must be specified.");
        }

        // cut out improvement windows if the file is given
        if (cmdLine.hasOption("p")) {
            Set<TimeWindowData> improvementWindowSet = TimeWindowDataFile.read(Paths.get(cmdLine.getOptionValue("p")));
            System.err.println("Cutting out improvement window from waveform data");
            basicIDs = cutOutImprovementWindows(basicIDs, improvementWindowSet);
        }

        List<GlobalCMTID> events = basicIDs.stream().map(id -> id.getGlobalCMTID()).distinct().sorted().collect(Collectors.toList());
        for(GlobalCMTID event:events) {
            List<BasicID> basicIDsForEvent = basicIDs.stream().filter(id -> id.getGlobalCMTID().equals(event)).collect(Collectors.toList());

            // set DVector
            DVectorBuilder dVectorBuilder = new DVectorBuilder(basicIDsForEvent, false);

            // set weighting
            RealVector[] weighting = weightingHandler.weightWaveforms(dVectorBuilder);

            // assemble d
            RealVector d = dVectorBuilder.buildWithWeight(weighting);

            // compute variance
            RealVector obs = dVectorBuilder.fullObsVecWithWeight(weighting);
            double normalizedVariance = MathAid.computeVariance(d, obs);
            System.err.println( event + " : " + normalizedVariance + " (" + basicIDsForEvent.size() + " waveforms)");
        }
        // set DVector
        System.err.println("Setting data for d vector");
        DVectorBuilder dVectorBuilder = new DVectorBuilder(basicIDs, true);

        // set weighting
        System.err.println("Setting weighting");
        RealVector[] weighting = weightingHandler.weightWaveforms(dVectorBuilder);

        // assemble d
        System.err.println("Assembling d vector");
        RealVector d = dVectorBuilder.buildWithWeight(weighting);

        // compute variance
        RealVector obs = dVectorBuilder.fullObsVecWithWeight(weighting);
        double normalizedVariance = MathAid.computeVariance(d, obs);
        System.err.println("Npts of whole waveform is " + obs.getDimension());
        System.err.println("Normalized variance for all events is " + normalizedVariance);
    }

    /**
     * Cut out the parts included in improvement windows from input basicIDs.
     * BasicIDs will be split into several parts if multiple improvement windows exist for one ID.
     * @param basicIDs (List of {@link BasicID}) Input basicIDs
     * @param improvementWindowSet (Set of {@link TimeWindowData}) Improvement windows
     * @return (List of {@link BasicID}) Cut out basicIDs. {@link BasicID#startByte} is not set.
     */
    private static List<BasicID> cutOutImprovementWindows(List<BasicID> basicIDs, Set<TimeWindowData> improvementWindowSet) {
        List<BasicID> cutOutBasicIDs = new ArrayList<>();

        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs,true);
        List<BasicID> obsIDs = pairer.getObsList();
        List<BasicID> synIDs = pairer.getSynList();

        // cut out basicIDs
        for (int i = 0; i < obsIDs.size(); i++) {
            BasicID obsID = obsIDs.get(i);
            BasicID synID = synIDs.get(i);

            // Time frame of synthetic waveform must be compared, since it is the correct one when time shift is applied.
            // All windows are worked for in case the improvement window is split into several parts.
            Set<TimeWindowData> improvementWindows = synID.findAllOverlappingWindows(improvementWindowSet);
            if (improvementWindows.size() == 0) {
                System.err.println(" No matching improvement window: " + synID.toDataEntry());
            }
            for (TimeWindowData improvementWindow : improvementWindows) {
                // Time frame of synthetic waveform must be used, since it is the correct one when time shift is applied.
                double[] cutX = synID.toTrace().cutWindow(improvementWindow).getX();
                double startTime = cutX[0];
                double endTime = cutX[cutX.length - 1];
                int npts = cutX.length;
                // observed waveform must be shifted before cutting
                double[] cutObsY = obsID.toTrace().withXAs(synID.toTrace().getX()).cutWindow(startTime, endTime).getY();
                double[] cutSynY = synID.toTrace().cutWindow(startTime, endTime).getY();

                // add new synID
                cutOutBasicIDs.add(new BasicID(synID.getWaveformType(), synID.getSamplingHz(), startTime, npts,
                        synID.getObserver(), synID.getGlobalCMTID(), synID.getSacComponent(),
                        synID.getMinPeriod(), synID.getMaxPeriod(), improvementWindow.getPhases(),
                        synID.isConvolved(), cutSynY));
                // add new obsID
                // Time shift is the same as before cutting.
                double shift = obsID.getStartTime() - synID.getStartTime();
                cutOutBasicIDs.add(new BasicID(obsID.getWaveformType(), obsID.getSamplingHz(), startTime + shift, npts,
                        obsID.getObserver(), obsID.getGlobalCMTID(), obsID.getSacComponent(),
                        obsID.getMinPeriod(), obsID.getMaxPeriod(), improvementWindow.getPhases(),
                        obsID.isConvolved(), cutObsY));
            }

        }
        return cutOutBasicIDs;
    }

}
