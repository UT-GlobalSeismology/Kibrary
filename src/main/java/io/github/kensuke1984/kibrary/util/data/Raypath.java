package io.github.kensuke1984.kibrary.util.data;


import io.github.kensuke1984.anisotime.ComputationalMesh;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.anisotime.RaypathCatalog;
import io.github.kensuke1984.anisotime.VelocityStructure;
import io.github.kensuke1984.kibrary.external.TauPPierceReader;
import io.github.kensuke1984.kibrary.external.TauPPierceReader.Info;
import io.github.kensuke1984.kibrary.math.geometry.RThetaPhi;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Raypath between a source at {@link #sourcePosition} and a receiver at
 * {@link #receiverPosition} <br>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 */
public class Raypath {

    /**
     * source-to-receiver(station) Azimuth [rad] 震源から観測点をみた方位角
     */
    protected final double azimuth;
    protected final double backAzimuth;
    /**
     * epicentral distance [rad]
     */
    protected final double epicentralDistance;
    /**
     * {@link FullPosition} of a seismic source
     */
    private final FullPosition sourcePosition;
    /**
     * {@link HorizontalPosition} of a seismic station
     */
    private final HorizontalPosition receiverPosition;

    /**
     * whether attempt to calculate turning and piercing points has been made
     */
    private boolean calculatedPiercePoints;
    /**
     * whether attempt to calculate turning point has been made
     */
    private boolean calculatedTurningPoint;
    /**
     * the bottoming point of raypath
     */
    private FullPosition turnPosition;
    /**
     * the point the ray pierces in through a certain depth
     */
    private FullPosition enterPosition;
    /**
     * the point the ray pierces out through a certain depth
     */
    private FullPosition leavePosition;


    /**
     * Create a raypath for the source and station.
     *
     * @param source  {@link FullPosition} of a source
     * @param receiver {@link HorizontalPosition} of a receiver
     */
    public Raypath(FullPosition source, HorizontalPosition receiver) {
        sourcePosition = source;
        receiverPosition = receiver;
        azimuth = source.calculateAzimuth(receiver);
        epicentralDistance = Earth.getEpicentralDistance(source, receiver);
        backAzimuth = source.calculateBackAzimuth(receiver);
    }

    /**
     * Calculate turning point on the raypath.
     * @param model
     * @param phase
     * @return (boolean) true if calculation succeeded
     */
    public boolean calculateTurningPoint(String model, Phase phase) {
        Info info = TauPPierceReader.getTurningInfo(sourcePosition, receiverPosition, model, phase);
        calculatedTurningPoint = true;

        if (info != null) {
            turnPosition = info.getTurningPoint();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Calculate turning point and pierce points on the raypath.
     * @param model
     * @param phase
     * @param pierceDepth
     * @return (boolean) true if calculation succeeded
     */
    public boolean calculatePiercePoints(String model, Phase phase, double pierceDepth) {
        Info info = TauPPierceReader.getPierceInfo(sourcePosition, receiverPosition, model, pierceDepth, phase);
        calculatedPiercePoints = true;
        calculatedTurningPoint = true;

        if (info != null) {
            enterPosition = info.getEnterPoint();
            turnPosition = info.getTurningPoint();
            leavePosition = info.getLeavePoint();
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return MAY BE NULL if turning point does not exist
     *
     * @author otsuru
     * @since 2022/4/22
     */
    public FullPosition getTurningPoint() {
        if (calculatedTurningPoint) return turnPosition;
        else throw new IllegalStateException("Turning point is not yet calculated");
    }
    public FullPosition getEnterPoint() {
        if (calculatedPiercePoints) return enterPosition;
        else throw new IllegalStateException("Pierce points are not yet calculated");
    }
    public FullPosition getLeavePoint() {
        if (calculatedPiercePoints) return leavePosition;
        else throw new IllegalStateException("Pierce points are not yet calculated");
    }

    /**
     * Calculate the azimuth of the raypath at the turning point.
     * The turning point must be already calculated.
     * @return (double) azimuth at turning point
     */
    public double calculateMidAzimuth() {
        if (calculatedTurningPoint) return turnPosition.calculateAzimuth(receiverPosition);
        else throw new IllegalStateException("Turning point is not yet calculated");
    }

    /**
     * 震源から観測点に向けての震央距離thetaでの座標
     * @param theta [rad]
     * @return {@link HorizontalPosition} on the raypath where the epicentral distance from the source is theta.
     */
    public HorizontalPosition positionOnRaypathAt(double theta) {
        return RThetaPhi.toCartesian(Earth.EARTH_RADIUS, theta, 0).rotateaboutZ(Math.PI - azimuth)
                .rotateaboutY(sourcePosition.getTheta()).rotateaboutZ(sourcePosition.getPhi()).toLocation();
    }

    /**
     * ある点を震源、観測点に与えた時に、 震源を北極に持って行って観測点をさらに標準時線に持っていった時のある点の座標
     *
     * @param position {@link HorizontalPosition} of target
     * @return relative position when the source is shifted to the north pole
     * and station is on the Standard meridian
     */
    public HorizontalPosition moveToNorthPole(HorizontalPosition position) {
        return position.toXYZ(Earth.EARTH_RADIUS).rotateaboutZ(-sourcePosition.getPhi())
                .rotateaboutY(-sourcePosition.getTheta()).rotateaboutZ(-Math.PI + azimuth).toLocation();
    }

    /**
     * 震源を北極に持って行って観測点をさらに標準時線に持っていった時に、ある点を仮定する。 その後震源、観測点を本来の位置に戻した時の、ある点の座標
     *
     * @param position {@link HorizontalPosition} of target
     * @return relative position when the source is shifted from the north pole
     * and station is from the Standard meridian
     */
    public HorizontalPosition moveFromNorthPole(HorizontalPosition position) {
        return position.toXYZ(Earth.EARTH_RADIUS).rotateaboutZ(Math.PI - azimuth)
                .rotateaboutY(sourcePosition.getTheta()).rotateaboutZ(sourcePosition.getPhi()).toLocation();
    }

    /**
     * Compensation is the raypath extension of the input phase to the surface
     * at the source side.
     *
     * @param phase     target phase to be extended
     * @param structure in which a raypath travels
     * @return [rad] the delta of the extednded ray path
     */
    public double computeCompensatedEpicentralDistance(Phase phase, VelocityStructure structure) {
        io.github.kensuke1984.anisotime.Raypath[] rays = toANISOtime(phase, structure);
        if (rays.length == 0) throw new RuntimeException("No raypath");
        if (1 < rays.length) throw new RuntimeException("multiples");
        return rays[0].computeDelta(phase, structure.earthRadius());
    }

    /**
     * @param phase     target phase
     * @param structure to compute raypath
     * @return Raypath which phase travels this raypath
     */
    public io.github.kensuke1984.anisotime.Raypath[] toANISOtime(Phase phase, VelocityStructure structure) {
        return RaypathCatalog.computeCatalog(structure, ComputationalMesh.simple(structure), 10)
                .searchPath(phase, sourcePosition.getR(), epicentralDistance, false);
    }

    /**
     * @return {@link FullPosition} of the seismic source on the raypath
     */
    public FullPosition getSource() {
        return sourcePosition;
    }

    /**
     * @return {@link HorizontalPosition} of the seismic station on the raypath
     */
    public HorizontalPosition getReceiver() {
        return receiverPosition;
    }

    /**
     * @return epicentral distance of this raypath [rad]
     */
    public double getEpicentralDistance() {
        return epicentralDistance;
    }

    /**
     * @return azimuth [rad]
     */
    public double getAzimuth() {
        return azimuth;
    }

    /**
     * @return back azimuth [rad]
     */
    public double getBackAzimuth() {
        return backAzimuth;
    }

    public boolean hasCalculatedTurningPoint() {
        return calculatedTurningPoint;
    }

    public boolean hasCalculatedPiercePoints() {
        return calculatedPiercePoints;
    }
}
