package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.Arrays;
import java.util.List;
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
     * Observed IDs.
     */
    private final BasicID[] obsIDs;
    /**
     * Vectors of observed waveforms.
     */
    private final RealVector[] obsVecs;
    /**
     * Synthetic IDs.
     */
    private final BasicID[] synIDs;
     /**
     * Vectors of synthetic waveforms.
     */
    private final RealVector[] synVecs;
    /**
     * Number of data points.
     */
    private final int totalNpts;
    /**
     * Number of independent data in the whole vector.
     */
    private final double numIndependent;
    /**
     * Number of timewindows.
     */
    private final int nTimeWindow;
    /**
     * Indices of the points that each timewindow starts at.
     */
    private final int[] startPoints;

    public DVectorBuilder(BasicID[] basicIDs) {
        this(Arrays.asList(basicIDs));
    }

    public DVectorBuilder(List<BasicID> basicIDs) {
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

        totalNpts = read();
        numIndependent = computeNumIndependent();
    }

    private int read() {
        int currentNpts = 0;
        for (int i = 0; i < nTimeWindow; i++) {
            startPoints[i] = currentNpts;
            currentNpts += obsIDs[i].getNpts();

            // read waveforms
            obsVecs[i] = new ArrayRealVector(obsIDs[i].getData(), false);
            synVecs[i] = new ArrayRealVector(synIDs[i].getData(), false);

            if (Math.abs(obsIDs[i].getStartTime() - synIDs[i].getStartTime()) >= START_TIME_DELAY_LIMIT)
                throw new RuntimeException("Start time mismatch: " + obsIDs[i] + " " + synIDs[i]);
            if (Double.isNaN(obsVecs[i].getLInfNorm()) || obsVecs[i].getLInfNorm() == 0)
                throw new RuntimeException("Obs is 0 or NaN: " + obsIDs[i] + " " + obsVecs[i].getLInfNorm());
        }
        return currentNpts;
    }

    /**
     * Compute the number of independent data in the whole data vector,
     * considering the lower period of passband and the sampling frequency.
     * The redundancy parameter is not considered here.
     * @return (double) Number of independent data in the whole vector.
     *
     * @author otsuru
     * @since 2024/4/2
     */
    private double computeNumIndependent() {
        double currentNumIndependent = 0.0;
        for (int i = 0; i < nTimeWindow; i++) {
            int npts = obsIDs[i].getNpts();
            double minPeriod = obsIDs[i].getMinPeriod();
            double samplingHz = obsIDs[i].getSamplingHz();
            // (total number of points) = (lower period of passband / sampling period) * (number of independent data)
            currentNumIndependent += npts / minPeriod / samplingHz;
        }
        return currentNumIndependent;
    }

    /**
     * Check if all basicIDs include waveform data.
     * @param ids (List of {@link BasicID}) IDs to check.
     * @return (boolean) Whether all the ids have waveform data.
     */
    private static boolean check(List<BasicID> ids) {
        return ids.stream().parallel().allMatch(BasicID::containsData);
    }

    /**
     * Look for the timewindow that the input ID corresponds to.
     * If the input is obs, the search is done for obs, while if the input is syn or partial, the search is done for syn.
     *
     * @param id ({@link BasicID}) ID to search for.
     * @return (int) Index for the ID. -1 if no ID is found.
     */
    public int whichTimewindow(BasicID id) {
        BasicID[] ids = id.getWaveformType() == WaveformType.OBS ? obsIDs : synIDs;
        return IntStream.range(0, ids.length).filter(i -> BasicID.isPair(id, ids[i])).findAny().orElse(-1);
    }

    /**
     * Conposes a full vector from smaller ones corresponding to the timewindows set in this class.
     * Every vector must have the same length as the corresponding timewindow.
     *
     * @param vectors (RealVector[]) Vectors to combine.
     * @return (RealVector) Combined vector.
     */
    public RealVector compose(RealVector[] vectors) {
        if (vectors.length != nTimeWindow)
            throw new IllegalArgumentException("the number of input vectors is invalid");
        for (int i = 0; i < nTimeWindow; i++)
            if (vectors[i].getDimension() != obsVecs[i].getDimension())
                throw new IllegalArgumentException("input vector is invalid");

        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++)
            v.setSubVector(startPoints[i], vectors[i]);
        return v;
    }

    /**
     * Decomposes a full vector to smaller ones corresponding to the timewindows set in this class.
     * Error occurs if the input is invalid.
     * @param vector (RealVector) Full vector to separate.
     * @return (RealVector[]) Separated vectors for each time window.
     */
    public RealVector[] decompose(RealVector vector) {
        if (vector.getDimension() != totalNpts)
            throw new IllegalArgumentException("The length of input vector " + vector.getDimension() + " is invalid, should be " + totalNpts);
        RealVector[] vectors = new RealVector[nTimeWindow];
        Arrays.setAll(vectors, i -> vector.getSubVector(startPoints[i], obsVecs[i].getDimension()));
        return vectors;
    }

    /**
     * Builds and returns the d vector.
     * It will be weighed as Wd = [weight diagonal matrix](obsVector - synVector).
     * @param weighting (Weighting)
     * @return (RealVector) Wd
     */
    public RealVector buildWithWeight(RealVector[] weighting) {
        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++) {
            // [(obs - syn) * weight] for each element point inside timewindow i
            RealVector vi = obsVecs[i].subtract(synVecs[i]).ebeMultiply(weighting[i]);
            v.setSubVector(startPoints[i], vi);
        }
        return v;
    }

    /**
     * Builds and returns the full synthetic vector.
     * @return (RealVector) syn
     */
    public RealVector fullSynVec() {
        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], synVecs[i]);
        }
        return v;
    }

    /**
     * Builds and returns the full synthetic vector, weighted.
     * @param weighting (Weighting)
     * @return (RealVector) W * syn
     */
    public RealVector fullSynVecWithWeight(RealVector[] weighting) {
        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], synVecs[i].ebeMultiply(weighting[i]));
        }
        return v;
    }

    /**
     * Builds and returns the full observed vector.
     * @return (RealVector) obs
     */
    public RealVector fullObsVec() {
        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], obsVecs[i]);
        }
        return v;
    }

    /**
     * Builds and returns the full observed vector, weighted.
     * @param weighting (Weighting)
     * @return (RealVector) W * obs
     */
    public RealVector fullObsVecWithWeight(RealVector[] weighting) {
        RealVector v = new ArrayRealVector(totalNpts);
        for (int i = 0; i < nTimeWindow; i++) {
            v.setSubVector(startPoints[i], obsVecs[i].ebeMultiply(weighting[i]));
        }
        return v;
    }

    /**
     * @param i (int) Index of timewindow.
     * @return (int) Number of points inside i-th timewindow.
     */
    public int nptsOfWindow(int i) {
        return obsIDs[i].getNpts();
    }

    public int[] nptsArray() {
        return IntStream.range(0, nTimeWindow).map(i -> obsIDs[i].getNpts()).toArray();
    }

    /**
     * @return (int) Total number of points of whole vector.
     */
    public int getTotalNpts() {
        return totalNpts;
    }

    /**
     * @return (double) Number of independent data in the whole vector.
     */
    public double getNumIndependent() {
        return numIndependent;
    }

    /**
     * @return (int) Number of timewindows (= number of BasicIDs) included.
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
     * @param i (int) Index of timewindow.
     * @return (int) Index of point where the i-th timewindow starts.
     */
    public int getStartPoint(int i) {
        return startPoints[i];
    }

}
