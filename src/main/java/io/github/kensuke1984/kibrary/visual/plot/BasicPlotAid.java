package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.SeismicPhase;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

class BasicPlotAid {
    static final GnuplotLineAppearance UNSHIFTED_APPEARANCE = new GnuplotLineAppearance(2, GnuplotColorName.gray, 1);
    static final GnuplotLineAppearance SHIFTED_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
    static final GnuplotLineAppearance RED_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);
    static final GnuplotLineAppearance GREEN_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.web_green, 1);
    static final GnuplotLineAppearance BLUE_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.web_blue, 1);
    static final GnuplotLineAppearance RESIDUAL_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.skyblue, 1);

    static final GnuplotLineAppearance ZERO_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.light_gray, 1);
    static final GnuplotLineAppearance USE_PHASE_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.turquoise, 1);
    static final GnuplotLineAppearance AVOID_PHASE_APPEARANCE = new GnuplotLineAppearance(1, GnuplotColorName.violet, 1);

    static GnuplotLineAppearance switchObservedAppearance(int num) {
        switch(num) {
        case 1: return UNSHIFTED_APPEARANCE;
        case 2: return SHIFTED_APPEARANCE;
        default: throw new IllegalArgumentException("Undefined style number for observed: " + num);
        }
    }
    static GnuplotLineAppearance switchSyntheticAppearance(int num) {
        switch(num) {
        case 1: return RED_APPEARANCE;
        case 2: return GREEN_APPEARANCE;
        case 3: return BLUE_APPEARANCE;
        default: throw new IllegalArgumentException("Undefined style number for synthetic: " + num);
        }
    }
    static GnuplotLineAppearance switchResidualAppearance(int num) {
        switch(num) {
        case 1: return RESIDUAL_APPEARANCE;
        default: throw new IllegalArgumentException("Undefined style number for residual: " + num);
        }
    }

    static enum AmpStyle {
        obsEach,
        synEach,
        obsMean,
        synMean
    }

    static double selectAmp(AmpStyle style, double ampScale, double obsEachMax, double synEachMax, double obsMeanMax, double synMeanMax) {
        switch (style) {
        case obsEach:
            return obsEachMax / ampScale;
        case synEach:
            return synEachMax / ampScale;
        case obsMean:
            return obsMeanMax / ampScale;
        case synMean:
            return synMeanMax / ampScale;
        default:
            throw new IllegalArgumentException("Input AmpStyle is unknown.");
        }
    }

    static void plotTravelTimeCurve(TauP_Time timeTool, String[] displayPhases, String[] alignPhases, double reductionSlowness,
            double startDistance, double endDistance,
            String fileTag, String dateStr, Path eventPath, SACComponent component, GnuplotFile gnuplot) throws IOException, TauModelException {
        // set names of all phases to display, and the phase to align if it is specified
        timeTool.setPhaseNames(displayPhases);
        if (alignPhases != null) {
            for (String phase : alignPhases) timeTool.appendPhaseName(phase);
        }

        // compute travel times
        List<SeismicPhase> phaseList = timeTool.getSeismicPhases();
        List<String> alignPhaseNameList = Arrays.asList(alignPhases);
        List<SeismicPhase> alignPhaseList = phaseList.stream().filter(phase -> alignPhaseNameList.contains(phase.getName())).collect(Collectors.toList());

        // output file and add curve for each phase
        for (SeismicPhase phase : phaseList) {
            if(!phase.hasArrivals()) {
                continue;
            }

            double[] dist = phase.getDist();
            double[] time = phase.getTime();

            String phaseName = phase.getName();
            String curveFileName = DatasetAid.generateOutputFileName("curve", fileTag, dateStr, "_" + component + "_" + phaseName + ".txt");
            Path curvePath = eventPath.resolve(curveFileName);
            boolean wrotePhaseLabel = false;

            // output file and add curve
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(curvePath))) {
                for(int i = 0; i < dist.length; i++) {
                    double distance = Math.toDegrees(dist[i]);
                    double arrivalTime = time[i];

                    // calculate time to reduce
                    double reduceTime;
                    if (alignPhases != null) {
                        Arrival relativeArrival = SeismicPhase.getEarliestArrival(alignPhaseList, distance);
                        if (relativeArrival == null) {
                            // no relative arrival at this dist, skip
                            continue;
                        }
                        reduceTime = relativeArrival.getTime();
                    } else {
                        reduceTime = reductionSlowness * distance;
                    }

                    pw.println(distance + " " + (arrivalTime - reduceTime));

                    // add label at first appearance
                    if (wrotePhaseLabel == false && startDistance < distance && distance < endDistance) {
                        gnuplot.addLabel(phaseName, "first", arrivalTime - reduceTime, distance, GnuplotColorName.turquoise);
                        wrotePhaseLabel = true;
                    }
                }
            }
            gnuplot.addLine(curveFileName, 2, 1, USE_PHASE_APPEARANCE, "");
        }
    }

}
