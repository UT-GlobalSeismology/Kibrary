package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Class for building d vector in Am=d.
 * <p>
 * This class is substantially <b>IMMUTABLE</b>.
 * Caution: {@link RealVector} is not immutable, so don't hand it over without deep-copying!
 *
 * @author otsuru
 * @since 2022/7/5 recreated inversion.Dvector
 */
public final class DVectorBuilder {
    private static final double START_TIME_DELAY_LIMIT = 15.0;

    /**
     * 観測波形の波形情報
     */
    private final BasicID[] obsIDs;
    /**
     * 観測波形のベクトル（各IDに対するタイムウインドウ）
     */
    private final RealVector[] obsVecs;
    /**
     * Synthetic
     */
    private final BasicID[] synIDs;
     /**
     * Vector syn
     */
    private final RealVector[] synVecs;
    /**
     * Number of data points
     */
    private int npts;
    /**
     * Number of timewindows
     */
    private final int nTimeWindow;
    /**
     * それぞれのタイムウインドウが,全体の中の何点目から始まるか
     */
    private final int[] startPoints;


    public DVectorBuilder(BasicID[] basicIDs) {
        // check if IDs are valid
        if (!check(basicIDs)) throw new RuntimeException("Input IDs do not have waveform data.");

        // sort observed and synthetic
        BasicIDPairUp pairer = new BasicIDPairUp(basicIDs);
        obsIDs = pairer.getObsList().toArray(new BasicID[0]);
        synIDs = pairer.getSynList().toArray(new BasicID[0]);
        nTimeWindow = synIDs.length;

        obsVecs = new RealVector[nTimeWindow];
        synVecs = new RealVector[nTimeWindow];
        startPoints = new int[nTimeWindow];
        System.err.println(" " + MathAid.switchSingularPlural(nTimeWindow, "timewindow is", "timewindows are") + " used");

        npts = read();
    }

    private int read() {
        int npts = 0;
        int start = 0;

        for (int i = 0; i < nTimeWindow; i++) {
            startPoints[i] = start;
            int nptsI = obsIDs[i].getNpts();
            npts += nptsI;
            start += nptsI;

            // read waveforms
            obsVecs[i] = new ArrayRealVector(obsIDs[i].getData(), false);
            synVecs[i] = new ArrayRealVector(synIDs[i].getData(), false);

            if (Math.abs(obsIDs[i].getStartTime() - synIDs[i].getStartTime()) >= START_TIME_DELAY_LIMIT)
                throw new RuntimeException("Start time mismatch: " + obsIDs[i] + " " + synIDs[i]);
            if (Double.isNaN(obsVecs[i].getLInfNorm()) || obsVecs[i].getLInfNorm() == 0)
                throw new RuntimeException("Obs is 0 or NaN: " + obsIDs[i] + " " + obsVecs[i].getLInfNorm());
        }
        return npts;
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
     * Look for the index for the input ID.
     * If the input is obs, the search is for obs, while if the input is syn or partial, the search is in syn.
     *
     * @param id {@link BasicID}
     * @return index for the ID. -1 if no ID found.
     */
    public int whichTimewindow(BasicID id) {
        BasicID[] ids = id.getWaveformType() == WaveformType.OBS ? obsIDs : synIDs;
        return IntStream.range(0, ids.length).filter(i -> BasicID.isPair(id, ids[i])).findAny().orElse(-1);
    }

    /**
     * Conposes a full vector from smaller ones corresponding to the timewindows set in this class.
     * Every vector must have the same length as the corresponding timewindow.
     *
     * @param vectors to combine
     * @return combined vectors
     */
    public RealVector compose(RealVector[] vectors) {
        if (vectors.length != nTimeWindow)
            throw new IllegalArgumentException("the number of input vectors is invalid");
        for (int i = 0; i < nTimeWindow; i++)
            if (vectors[i].getDimension() != obsVecs[i].getDimension())
                throw new IllegalArgumentException("input vector is invalid");

        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++)
            v.setSubVector(startPoints[i], vectors[i]);
        return v;
    }

    /**
     * Decomposes a full vector to smaller ones corresponding to the timewindows set in this class.
     * Error occurs if the input is invalid.
     * @param vector (RealVector) Full vector to separate
     * @return (RealVector[]) Separated vectors for each time window.
     */
    public RealVector[] decompose(RealVector vector) {
        if (vector.getDimension() != npts)
            throw new IllegalArgumentException("The length of input vector " + vector.getDimension() + " is invalid, should be " + npts);
        RealVector[] vectors = new RealVector[nTimeWindow];
        Arrays.setAll(vectors, i -> vector.getSubVector(startPoints[i], obsVecs[i].getDimension()));
        return vectors;
    }

    /**
     * Builds and returns the d vector.
     * It will be weighed as Wd = [weight diagonal matrix](obsVector - synVector)
     * @param weighting (Weighting)
     * @return (RealVector) Wd
     */
    public RealVector buildWithWeight(Weighting weighting) {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            // [(obs - syn) * weight] for each element point inside timewindow i
            RealVector vi = obsVecs[i].subtract(synVecs[i]).ebeMultiply(weighting.get(i));
            v.setSubVector(startPoints[i], vi);
        }
        return v;
    }

    /**
     * Builds and returns the full synthetic vector
     * @return (RealVector) syn
     */
    public RealVector fullSynVec() {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], synVecs[i]);
        }
        return v;
    }

    /**
     * Builds and returns the full synthetic vector, weighted
     * @param weighting (Weighting)
     * @return (RealVector) W * syn
     */
    public RealVector fullSynVecWithWeight(Weighting weighting) {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], synVecs[i].ebeMultiply(weighting.get(i)));
        }
        return v;
    }

    /**
     * Builds and returns the full observed vector
     * @return (RealVector) obs
     */
    public RealVector fullObsVec() {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], obsVecs[i]);
        }
        return v;
    }

    /**
     * Builds and returns the full observed vector, weighted
     * @param weighting (Weighting)
     * @return (RealVector) W * obs
     */
    public RealVector fullObsVecWithWeight(Weighting weighting) {
        RealVector v = new ArrayRealVector(npts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], obsVecs[i].ebeMultiply(weighting.get(i)));
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

    public int[] nptsArray() {
        return IntStream.range(0, nTimeWindow).map(i -> obsIDs[i].getNpts()).toArray();
    }

    /**
     * @return (int) number of points of whole vector
     */
    public int getNpts() {
        return npts;
    }

    /**
     * @return (int) number of timewindows (= number of BasicIDs) included
     */
    public int getNTimeWindow() {
        return nTimeWindow;
    }

    public BasicID getObsID(int i) {
        return obsIDs[i];
    }

    public BasicID getSynID(int i) {
        return synIDs[i];
    }

    public RealVector getObsVec(int i) {
        return obsVecs[i].copy();
    }

    public RealVector getSynVec(int i) {
        return synVecs[i].copy();
    }

    /**
     * @param i (int) index of timewindow
     * @return (int) the index of start point where the i th timewindow starts
     */
    public int getStartPoint(int i) {
        return startPoints[i];
    }

}
