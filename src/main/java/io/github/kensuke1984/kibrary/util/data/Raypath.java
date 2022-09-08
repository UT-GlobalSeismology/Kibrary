package io.github.kensuke1984.kibrary.util.data;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Raypath between a source and a receiver.
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2022/9/8 Modified almost whole file.
 */
public class Raypath {

    /**
     * Name of phase
     */
    private final String phase;
    /**
     * Number of points along the raypath (source, pierce points, turning points, receiver)
     */
    private final int nPoint;
    /**
     * Epicentral distances from the source to points along the raypath [deg]
     */
    private final double[] distancesDeg;
    /**
     * List of points along the raypath
     */
    private final List<FullPosition> positions;

    /**
     * @param phase
     * @param distancesDeg (double[]) Epicentral distances from the source to points along the raypath [deg]
     * @param positions (List of FullPosition) List of points along the raypath
     */
    public Raypath(String phase, double[] distancesDeg, List<FullPosition> positions) {
        if (distancesDeg.length != positions.size()) throw new IllegalArgumentException("number of distances and positions should match");
        this.nPoint = distancesDeg.length;

        this.phase = phase;
        this.distancesDeg = distancesDeg;
        this.positions = positions;
    }

    /**
     * Create a raypath from the source to the receiver.
     *
     * @param phase (String) Name of phase
     * @param source  {@link FullPosition} of a source
     * @param receiver {@link HorizontalPosition} of a receiver
     */
    public Raypath(String phase, FullPosition source, HorizontalPosition receiver) {
        this.phase = phase;
        this.nPoint = 2;

        this.distancesDeg = new double[2];
        distancesDeg[0] = 0;
        distancesDeg[1] = source.computeEpicentralDistanceDeg(receiver);

        this.positions = new ArrayList<>();
        positions.add(source);
        positions.add(receiver.toFullPosition(Earth.EARTH_RADIUS));
    }

    /**
     * Clips all raypath segments that are within a specified layer.
     * @param lowerRadius (double) lower bound of layer [km]
     * @param upperRadius (double) upper bound of layer [km]
     * @return (List of Raypath)
     */
    public List<Raypath> clipInsideLayer(double lowerRadius, double upperRadius) {
        List<Raypath> clippedRaypaths = new ArrayList<>();

        int startIndex = -1;
        // if start point is inside layer, set it to startIndex
        if (lowerRadius < positions.get(0).getR() && positions.get(0).getR() < upperRadius) {
            startIndex = 0;
        }
        for (int i = 0; i < nPoint; i++) {
            if (startIndex < 0 && (Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)
                    || Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON))) {
                // when raypath comes to a border, remember that index
                startIndex = i;
            } else if (startIndex >= 0 && (Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)
                    || Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON))) {
                // do nothing if raypath is still at the borders
            } else if (startIndex >= 0 && (positions.get(i).getR() < lowerRadius || upperRadius < positions.get(i).getR())) {
                // once the raypath goes away from layer, clip from startIndex to the previous index
                if (i - 1 > startIndex) clippedRaypaths.add(clip(startIndex, i - 1));
                startIndex = -1;
            }
        }
        // if end point is still within layer, add the final clip
        if (startIndex >= 0 && (nPoint - 1 > startIndex)) {
            clippedRaypaths.add(clip(startIndex, nPoint - 1));
        }

        return clippedRaypaths;
    }

    /**
     * Clips all raypath segments that are outside a specified layer.
     * @param lowerRadius (double) lower bound of layer [km]
     * @param upperRadius (double) upper bound of layer [km]
     * @return (List of Raypath)
     */
    public List<Raypath> clipOutsideLayer(double lowerRadius, double upperRadius) {
        List<Raypath> clippedRaypaths = new ArrayList<>();

        int startBelowIndex = -1;
        int startAboveIndex = -1;
        // if start point is outside layer, set it to startIndex
        if (positions.get(0).getR() < lowerRadius) {
            startBelowIndex = 0;
        } else if (upperRadius < positions.get(0).getR()) {
            startAboveIndex = 0;
        }
        for (int i = 0; i < nPoint; i++) {
            if (startBelowIndex < 0 && Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)) {
                // when raypath comes down to lower border, remember that index
                startBelowIndex = i;
            } else if (startBelowIndex >= 0 && Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)) {
                // do nothing if raypath is still at the lower border
            } else if (startBelowIndex >= 0 && positions.get(i).getR() > lowerRadius) {
                // once the raypath goes above the lower border, clip from startBelowIndex to the previous index
                if (i - 1 > startBelowIndex) clippedRaypaths.add(clip(startBelowIndex, i - 1));
                startBelowIndex = -1;
            }
            if (startAboveIndex < 0 && Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON)) {
                // when raypath comes up to upper border, remember that index
                startAboveIndex = i;
            } else if (startAboveIndex >= 0 && Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON)) {
                // do nothing if raypath is still at the upper border
            } else if (startAboveIndex >= 0 && positions.get(i).getR() < upperRadius) {
                // once the raypath goes below the upper border, clip from startAboveIndex to the previous index
                if (i - 1 > startAboveIndex) clippedRaypaths.add(clip(startAboveIndex, i - 1));
                startAboveIndex = -1;
            }
        }
        // if end point is still outside layer, add the final clip
        if (startBelowIndex >= 0 && (nPoint - 1 > startBelowIndex)) {
            clippedRaypaths.add(clip(startBelowIndex, nPoint - 1));
        } else if (startAboveIndex >= 0 && (nPoint - 1 > startAboveIndex)) {
            clippedRaypaths.add(clip(startAboveIndex, nPoint - 1));
        }

        return clippedRaypaths;
    }

    private Raypath clip(int start, int end) {
        if (end - start <= 0) throw new IllegalArgumentException("Raypath must include at least 1 segment");
        double[] clippedDistances = new double[end - start + 1];
        List<FullPosition> clippedPositions = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            clippedDistances[i] = distancesDeg[i] - distancesDeg[start];
            clippedPositions.add(positions.get(i));
        }
        return new Raypath(phase, clippedDistances, clippedPositions);
    }

    /**
     * Finds the bottom turning point of the given index.
     * @param index (int) Which bottom turning point to look for (0:first, 1:second, ...)
     * @return (FullPosition) Position of bottom turning point, or null if it does not exist
     */
    public FullPosition findTurningPoint(int index) {
        List<FullPosition> turningPoints = findTurningPoints();
        if (index < 0 || index >= turningPoints.size()) return null;
        return turningPoints.get(index);
    }

    /**
     * Finds all bottom turning points.
     * @return (List of FullPosition) Positions of bottom turning points
     */
    public List<FullPosition> findTurningPoints() {
        List<FullPosition> turningPoints = new ArrayList<>();
        for (int i = 1; i < nPoint - 1; i++) {
            if (positions.get(i).getR() <= positions.get(i - 1).getR() && positions.get(i).getR() <= positions.get(i + 1).getR()) {
                turningPoints.add(positions.get(i));
            }
        }
        return turningPoints;
    }

    /**
     * Finds the ceil bouncing point of the given index.
     * @param index (int) Which ceil bouncing point to look for (0:first, 1:second, ...)
     * @return (FullPosition) Position of ceil bouncing point, or null if it does not exist
     */
    public FullPosition findCeilBouncingPoint(int index) {
        List<FullPosition> ceilBouncingPoints = findCeilBouncingPoints();
        if (index < 0 || index >= ceilBouncingPoints.size()) return null;
        return ceilBouncingPoints.get(index);
    }

    /**
     * Finds all ceil bouncing points.
     * @return (List of FullPosition) Positions of ceil bouncing points
     */
    public List<FullPosition> findCeilBouncingPoints() {
        List<FullPosition> ceilBouncingPoints = new ArrayList<>();
        for (int i = 1; i < nPoint - 1; i++) {
            if (positions.get(i).getR() >= positions.get(i - 1).getR() && positions.get(i).getR() >= positions.get(i + 1).getR()) {
                ceilBouncingPoints.add(positions.get(i));
            }
        }
        return ceilBouncingPoints;
    }

    /**
     * Computes the bottom turning point azimuth of the given index.
     * @param index (int) Which bottom turning point to compute for (0:first, 1:second, ...)
     * @return (double) Azimuth at bottom turning point [deg]
     */
    public double computeTurningAzimuthDeg(int index) {
        FullPosition turningPoint = findTurningPoint(index);
        if (turningPoint == null)
            throw new ArrayIndexOutOfBoundsException("Bottom turning point " + index + " does not exist.");
        return turningPoint.computeAzimuthDeg(getReceiver());
    }

    /**
     * @return epicentral distance of this full raypath [deg]
     */
    public double getEpicentralDistanceDeg() {
        return distancesDeg[nPoint - 1];
    }

    /**
     * @return azimuth at source [deg]
     */
    public double getAzimuthDeg() {
        return getSource().computeAzimuthDeg(getReceiver());
    }

    /**
     * @return back azimuth at receiver [deg]
     */
    public double getBackAzimuthDeg() {
        return getSource().computeBackAzimuthDeg(getReceiver());
    }

    /**
     * @return (FullPosition) The first point on this raypath
     */
    public FullPosition getSource() {
        return positions.get(0);
    }

    /**
     * @return (FullPosition) The last point on this raypath
     */
    public FullPosition getReceiver() {
        return positions.get(nPoint - 1);
    }

}
