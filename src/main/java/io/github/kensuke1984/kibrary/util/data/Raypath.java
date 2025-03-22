package io.github.kensuke1984.kibrary.util.data;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Raypath between a source and a receiver, stored as a list of positions along the raypath.
 * Multiple points along the raypath can be stored (e.g., pierce points, turning points).
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2022/9/8 Modified almost whole file.
 */
public final class Raypath {

    /**
     * Name of phase.
     */
    private final String phaseName;
    /**
     * Number of points along the raypath (source, pierce points, turning points, receiver).
     */
    private final int nPoint;
    /**
     * Epicentral distances from the source to points along the raypath [deg].
     */
    private final double[] distancesDeg;
    /**
     * List of points along the raypath.
     */
    private final List<FullPosition> positions;

    /**
     * @param phaseName (String) Name of phase of this raypath.
     * @param distancesDeg (double[]) Epicentral distances from the source to points along the raypath [deg].
     * @param positions (List of {@link FullPosition}) List of points along the raypath.
     */
    public Raypath(String phaseName, double[] distancesDeg, List<FullPosition> positions) {
        if (distancesDeg.length != positions.size()) throw new IllegalArgumentException("Number of distances and positions should match.");
        this.nPoint = distancesDeg.length;

        this.phaseName = phaseName;
        this.distancesDeg = distancesDeg;
        this.positions = positions;
    }

    /**
     * Create a raypath from the source to the receiver, with no points in between.
     *
     * @param phaseName (String) Name of phase of this raypath.
     * @param sourcePosition ({@link FullPosition}) Position of source.
     * @param receiverPosition ({@link HorizontalPosition}) Position of receiver.
     */
    public Raypath(String phaseName, FullPosition sourcePosition, HorizontalPosition receiverPosition) {
        this.phaseName = phaseName;
        this.nPoint = 2;

        distancesDeg = new double[2];
        distancesDeg[0] = 0;
        distancesDeg[1] = sourcePosition.computeEpicentralDistanceDeg(receiverPosition);

        positions = new ArrayList<>();
        positions.add(sourcePosition);
        positions.add(receiverPosition.toFullPosition(Earth.EARTH_RADIUS));
    }

    /**
     * Clips all raypath segments that are within a specified layer.
     * @param lowerRadius (double) Lower bound of layer [km].
     * @param upperRadius (double) Upper bound of layer [km].
     * @return (List of {@link Raypath}) Clipped raypaths.
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
                if (i - 1 > startIndex) clippedRaypaths.add(clip(startIndex, i));
                startIndex = -1;
            }
        }
        // if end point is still within layer, add the final clip
        if (startIndex >= 0 && (nPoint - 1 > startIndex)) {
            clippedRaypaths.add(clip(startIndex, nPoint));
        }

        return clippedRaypaths;
    }

    /**
     * Clips all raypath segments that are outside a specified layer.
     * @param lowerRadius (double) Lower bound of layer [km].
     * @param upperRadius (double) Upper bound of layer [km].
     * @return (List of {@link Raypath}) Clipped raypaths.
     */
    public List<Raypath> clipOutsideLayer(double lowerRadius, double upperRadius) {
        List<Raypath> clippedRaypaths = new ArrayList<>();

        int startBelowIndex = -1;
        int startAboveIndex = -1;
        boolean valid = false;
        // if start point is outside layer, set it to startIndex
        if (positions.get(0).getR() < lowerRadius) {
            startBelowIndex = 0;
            valid = true;
        } else if (upperRadius < positions.get(0).getR()) {
            startAboveIndex = 0;
            valid = true;
        }
        for (int i = 0; i < nPoint; i++) {
            if (startBelowIndex < 0 && Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)) {
                // when raypath comes down to lower border, remember that index
                startBelowIndex = i;
            } else if (startBelowIndex >= 0 && Precision.equals(positions.get(i).getR(), lowerRadius, FullPosition.RADIUS_EPSILON)) {
                // do nothing if raypath is still at the lower border
                // This is here to exclude cases where the radius becomes slightly larger than lowerRadius due to precision effects.
            } else if (startBelowIndex >= 0 && positions.get(i).getR() < lowerRadius) {
                // set valid when raypath goes under the lower border
                valid = true;
            } else if (startBelowIndex >= 0 && positions.get(i).getR() > lowerRadius) {
                // once the raypath goes above the lower border, clip from startBelowIndex to the previous index
                if ((i - 1 > startBelowIndex) && valid) clippedRaypaths.add(clip(startBelowIndex, i));
                startBelowIndex = -1;
                valid = false;
            }
            if (startAboveIndex < 0 && Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON)) {
                // when raypath comes up to upper border, remember that index
                startAboveIndex = i;
            } else if (startAboveIndex >= 0 && Precision.equals(positions.get(i).getR(), upperRadius, FullPosition.RADIUS_EPSILON)) {
                // do nothing if raypath is still at the upper border
                // This is here to exclude cases where the radius becomes slightly smaller than upperRadius due to precision effects.
            } else if (startAboveIndex >= 0 && positions.get(i).getR() > upperRadius) {
                // set valid when raypath goes above the upper border
                valid = true;
            } else if (startAboveIndex >= 0 && positions.get(i).getR() < upperRadius) {
                // once the raypath goes below the upper border, clip from startAboveIndex to the previous index
                if ((i - 1 > startAboveIndex) && valid) clippedRaypaths.add(clip(startAboveIndex, i));
                startAboveIndex = -1;
                valid = false;
            }
        }
        // if end point is still outside layer, add the final clip
        if (startBelowIndex >= 0 && (nPoint - 1 > startBelowIndex) && valid) {
            clippedRaypaths.add(clip(startBelowIndex, nPoint));
        } else if (startAboveIndex >= 0 && (nPoint - 1 > startAboveIndex) && valid) {
            clippedRaypaths.add(clip(startAboveIndex, nPoint));
        }

        return clippedRaypaths;
    }

    /**
     * Clip raypath using indices of positions.
     * @param from (int) Index of position to start clipping from (includes this index).
     * @param to (int) Index of position to clip up to (does not include this index).
     * @return ({@link Raypath}) Clipped raypath.
     */
    private Raypath clip(int from, int to) {
        if (to - from <= 1) throw new IllegalArgumentException("Raypath must include at least 1 segment");
        double[] clippedDistances = new double[to - from];
        for (int i = from; i < to; i++) {
            clippedDistances[i - from] = distancesDeg[i] - distancesDeg[from];
        }
        List<FullPosition> clippedPositions = positions.subList(from, to);
        return new Raypath(phaseName, clippedDistances, clippedPositions);
    }

    /**
     * Finds the bottom turning point of the given index.
     * @param index (int) Which bottom turning point to look for (0:first, 1:second, ...).
     * @param includeStrictTurn (boolean) Whether to include points that are strict bottom turning points.
     * @param includeDiffCenter (boolean) Whether to include mid-points of diffraction leg.
     * @param includeDiffStart (boolean) Whether to include points where diffraction starts.
     * @param includeDiffEnd (boolean) Whether to include points where diffraction ends.
     * @return ({@link FullPosition}) Position of bottom turning point, or null if it does not exist.
     */
    public FullPosition findTurningPoint(int index, boolean includeStrictTurn, boolean includeDiffCenter, boolean includeDiffStart, boolean includeDiffEnd) {
        List<FullPosition> turningPoints = findTurningPoints(includeStrictTurn, includeDiffCenter, includeDiffStart, includeDiffEnd);
        if (index < 0 || index >= turningPoints.size()) return null;
        return turningPoints.get(index);
    }

    /**
     * Finds all bottom turning points.
     * @param includeStrictTurn (boolean) Whether to include points that are strict bottom turning points.
     * @param includeDiffCenter (boolean) Whether to include mid-points of diffraction leg.
     * @param includeDiffStart (boolean) Whether to include points where diffraction starts.
     * @param includeDiffEnd (boolean) Whether to include points where diffraction ends.
     * @return (List of {@link FullPosition}) Positions of bottom turning points.
     */
    public List<FullPosition> findTurningPoints(boolean includeStrictTurn, boolean includeDiffCenter, boolean includeDiffStart, boolean includeDiffEnd) {
        List<FullPosition> turningPoints = new ArrayList<>();

        for (int i = 1; i < nPoint - 1; i++) {
            FullPosition position = positions.get(i);
            double rad = position.getR();

            if (includeStrictTurn) {
                if (rad < positions.get(i - 1).getR() && rad < positions.get(i + 1).getR()) {
                    turningPoints.add(position);
                }
            }
            if (includeDiffStart) {
                if (rad != positions.get(i - 1).getR() && rad == positions.get(i + 1).getR()) {
                    turningPoints.add(position);
                }
            }
            if (includeDiffEnd) {
                if (rad == positions.get(i - 1).getR() && rad != positions.get(i + 1).getR()) {
                    turningPoints.add(position);
                }
            }
            // This is at the end to list positions in order.
            if (includeDiffCenter) {
                if (rad == positions.get(i + 1).getR()) {
                    HorizontalPosition centerPosition = Earth.computeMidpoint(position, positions.get(i + 1));
                    turningPoints.add(centerPosition.toFullPosition(rad));
                }
            }
        }
        return turningPoints;
    }

    /**
     * Finds the ceil bouncing point of the given index.
     * @param index (int) Which ceil bouncing point to look for (0:first, 1:second, ...).
     * @return ({@link FullPosition}) Position of ceil bouncing point, or null if it does not exist.
     */
    public FullPosition findCeilBouncingPoint(int index) {
        List<FullPosition> ceilBouncingPoints = findCeilBouncingPoints();
        if (index < 0 || index >= ceilBouncingPoints.size()) return null;
        return ceilBouncingPoints.get(index);
    }

    /**
     * Finds all ceil bouncing points.
     * @return (List of {@link FullPosition}) Positions of ceil bouncing points.
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
     * Computes the bottom turning point azimuth of the given index. Center points of diffraction are also considered.
     * @param index (int) Which bottom turning point to compute for (0:first, 1:second, ...).
     * @return (double) Azimuth at bottom turning point [deg].
     */
    public double computeTurningAzimuthDeg(int index) {
        FullPosition turningPoint = findTurningPoint(index, true, true, false, false);
        if (turningPoint == null)
            throw new ArrayIndexOutOfBoundsException("Bottom turning point " + index + " does not exist.");
        return turningPoint.computeAzimuthDeg(getReceiver());
    }

    /**
     * @return (double) Epicentral distance of this full raypath [deg].
     */
    public double getEpicentralDistanceDeg() {
        return distancesDeg[nPoint - 1];
    }

    /**
     * @return (double) Azimuth at source [deg].
     */
    public double getAzimuthDeg() {
        return getSource().computeAzimuthDeg(getReceiver());
    }

    /**
     * @return (double) Back azimuth at receiver [deg].
     */
    public double getBackAzimuthDeg() {
        return getSource().computeBackAzimuthDeg(getReceiver());
    }

    /**
     * @return ({@link FullPosition}) The first point on this raypath.
     */
    public FullPosition getSource() {
        return positions.get(0);
    }

    /**
     * @return ({@link FullPosition}) The last point on this raypath.
     */
    public FullPosition getReceiver() {
        return positions.get(nPoint - 1);
    }

    /**
     * @return (String) The name of phase of this raypath.
     */
    public String getPhaseName() {
        return phaseName;
    }
}
