package io.github.kensuke1984.kibrary.stacking;

import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import org.apache.commons.math3.linear.RealVector;

/**
 * Stacking by the peak-to-peak average arrival time and amplitude.
 *
 * @author Kensuke Konishi
 * @version 0.0.1.1
 */
public class PeakStack implements Stack {

    private double delta;

    public PeakStack() {
        delta = 0.05;
    }

    @Override
    public Trace stack(String stationName, GlobalCMTID globalCMTID, SACComponent component, WaveformType type,
                       Trace trace) {
        RealVector x = trace.getXVector();
        for (int i = 1; i < x.getDimension(); i++) {
            double interval = x.getEntry(i) - x.getEntry(i - 1);
            double gap = Math.abs(interval - delta);
            if (10e-10 < gap) throw new RuntimeException("Input Trace has invalid x interval.");
        }
        RealVector y = trace.getYVector();
        double peakX = (trace.getXforMaxYValue() + trace.getXforMinYValue()) / 2;
        double peakY = (trace.getMaxY() - trace.getMinY()) / 2;
        x = x.mapSubtract(peakX);
        y = y.mapDivide(peakY);
        return new Trace(x.toArray(), y.toArray());
    }

}
