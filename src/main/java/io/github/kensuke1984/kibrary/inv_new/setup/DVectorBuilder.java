package io.github.kensuke1984.kibrary.inv_new.setup;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Class for building d vector in Am=d.
 *
 * @author otsuru
 * @since 2022/7/5 recreated inversion.Dvector
 */
public class DVectorBuilder {
    private static final double START_TIME_DELAY_LIMIT = 15.0;

    /**
     * 観測波形の波形情報
     */
    private BasicID[] obsIDs;
    /**
     * 観測波形のベクトル（各IDに対するタイムウインドウ）
     */
    private RealVector[] obsVec;
    /**
     * Synthetic
     */
    private BasicID[] synIDs;
     /**
     * Vector syn
     */
    private RealVector[] synVec;
    /**
     * Number of data points
     */
    private int npts;
    /**
     * Number of timewindows
     */
    private int nTimeWindow;
    /**
     * それぞれのタイムウインドウが,全体の中の何点目から始まるか
     */
    private int[] startPoints;


    public DVectorBuilder(BasicID[] basicIDs) {
        // check if IDs are valid
        if (!check(basicIDs)) throw new RuntimeException("Input IDs do not have waveform data.");

        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
        obsIDs = pairer.getObsList().toArray(new BasicID[0]);
        synIDs = pairer.getSynList().toArray(new BasicID[0]);
        nTimeWindow = synIDs.length;

        obsVec = new RealVector[nTimeWindow];
        synVec = new RealVector[nTimeWindow];
        startPoints = new int[nTimeWindow];
        System.err.println(nTimeWindow + " timewindows are used");

        read();
    }

    private void read() {
        this.npts = 0;
        int start = 0;

        for (int i = 0; i < nTimeWindow; i++) {
            startPoints[i] = start;
            int npts = obsIDs[i].getNpts();
            this.npts += npts;
            start += npts;

            // read waveforms
            obsVec[i] = new ArrayRealVector(obsIDs[i].getData(), false);
            synVec[i] = new ArrayRealVector(synIDs[i].getData(), false);

            if (Math.abs(obsIDs[i].getStartTime() - synIDs[i].getStartTime()) >= START_TIME_DELAY_LIMIT)
                throw new RuntimeException("Start time mismatch: " + obsIDs[i] + " " + synIDs[i]);
            if (obsVec[i].getLInfNorm() == 0 || Double.isNaN(obsVec[i].getLInfNorm()))
                throw new RuntimeException("Obs is 0 or NaN: " + obsIDs[i] + " " + obsVec[i].getLInfNorm());

        }
    }

    /**
     * Check if all basicIDs include waveform data
     * @param ids for check
     * @return if all the ids have waveform data.
     */
    private static boolean check(BasicID[] ids) {
        return Arrays.stream(ids).parallel().allMatch(BasicID::containsData);
    }

    /**
     * Decides whether two IDs (BasicID and/or PartialID) are pairs.
     * They are regarded as same if component, npts, sampling Hz, start time, max & min
     * period, observer, globalCMTID are same.
     * This method ignores whether the input IDs are observed or synthetic. TODO start time
     *
     * @param id0 {@link BasicID}
     * @param id1 {@link BasicID}
     * @return if the IDs are same
     */
    private static boolean isPair(BasicID id0, BasicID id1) {
        boolean res = false;
        if (id0.getPhases() == null && id1.getPhases() == null) // for compatibility with old format of BasicID
            res = id0.getObserver().equals(id1.getObserver()) && id0.getGlobalCMTID().equals(id1.getGlobalCMTID())
                    && id0.getSacComponent() == id1.getSacComponent() && id0.getNpts() == id1.getNpts()
                    && id0.getSamplingHz() == id1.getSamplingHz() && Math.abs(id0.getStartTime() - id1.getStartTime()) < 20.
                    && id0.getMaxPeriod() == id1.getMaxPeriod() && id0.getMinPeriod() == id1.getMinPeriod();
        else {
            res = id0.getObserver().equals(id1.getObserver()) && id0.getGlobalCMTID().equals(id1.getGlobalCMTID())
                && id0.getSacComponent() == id1.getSacComponent()
                && id0.getSamplingHz() == id1.getSamplingHz() && new Phases(id0.getPhases()).equals(new Phases(id1.getPhases()))
                && id0.getMaxPeriod() == id1.getMaxPeriod() && id0.getMinPeriod() == id1.getMinPeriod();
        }
        return res;
    }

    /**
     * Look for the index for the input ID.
     * If the input is obs, the search is for obs, while if the input is syn or partial, the search is in syn.
     *
     * @param id {@link BasicID}
     * @return index for the ID. -1 if no ID found.
     */
    int whichTimewindow(BasicID id) {
        BasicID[] ids = id.getWaveformType() == WaveformType.OBS ? obsIDs : synIDs;
        return IntStream.range(0, ids.length).filter(i -> isPair(id, ids[i])).findAny().orElse(-1);
    }

    /**
     * Every vector must have the same length as the corresponding timewindow.
     *
     * @param vectors to combine
     * @return combined vectors
     */
    private RealVector combine(RealVector[] vectors) {
        if (vectors.length != nTimeWindow)
            throw new RuntimeException("the number of input vectors is invalid");
        for (int i = 0; i < nTimeWindow; i++)
            if (vectors[i].getDimension() != obsVec[i].getDimension())
                throw new RuntimeException("input vector is invalid");

        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++)
            v.setSubVector(startPoints[i], vectors[i]);

        return v;
    }

    /**
     * Calculate Wd = [weight diagonal matrix](obsVector - synVector)
     * @param weighting
     * @return
     */
    public RealVector buildWithWeight(Weighting weighting) {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            // [(obs - syn) * weight] for each element point inside timewindow i
            RealVector vi = obsVec[i].subtract(synVec[i]).ebeMultiply(weighting.get(i));
            v.setSubVector(startPoints[i], vi);
        }
        return v;
    }

    /**
     * @param i (int)
     * @return (int) number of points inside i-th timewindow
     */
    public int nptsOfWindow(int i) {
        return obsIDs[i].getNpts();
    }

    /**
     * @return (int) number of points of whole vector
     */
    public int getNpts() {
        return npts;
    }

    public int getNTimeWindow() {
        return nTimeWindow;
    }

    public RealVector getObsVec(int i) {
        return obsVec[i];
    }

    public RealVector getSynVec(int i) {
        return synVec[i];
    }

    /**
     * @param i index of timewindow
     * @return the index of start point where the i th timewindow starts
     */
    public int getStartPoint(int i) {
        return startPoints[i];
    }

}
