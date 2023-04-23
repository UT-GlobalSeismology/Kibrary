package io.github.kensuke1984.kibrary.visual.plot;

import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;

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

}
