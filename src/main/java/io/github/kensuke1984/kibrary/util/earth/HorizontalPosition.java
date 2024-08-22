package io.github.kensuke1984.kibrary.util.earth;

import java.util.Collection;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.geometry.Ellipse;
import io.github.kensuke1984.kibrary.math.geometry.Point2D;
import io.github.kensuke1984.kibrary.math.geometry.RThetaPhi;
import io.github.kensuke1984.kibrary.math.geometry.XYZ;

/**
 * <p>
 * 2D Position on Earth (specified by latitude and longitude).
 * <p>
 * This class is <b>almost IMMUTABLE</b> (expect that it is not a final class).
 * Classes that extend this class must be <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class HorizontalPosition implements Comparable<HorizontalPosition> {

    /**
     * Margin to decide whether two latitudes are the same value.
     */
    public static final double LATITUDE_EPSILON = FastMath.pow(10, -Latitude.DECIMALS) / 2;
    /**
     * Margin to decide whether two longitudes are the same value.
     */
    public static final double LONGITUDE_EPSILON = FastMath.pow(10, -Longitude.DECIMALS) / 2;

    private final Latitude latitude;
    private final Longitude longitude;

    /**
     * Find the latitude interval of a given set of positions.
     * The latitudes must be equally spaced.
     * @param positions (Collection of {@link HorizontalPosition}) Input positions
     * @return (double) interval
     */
    public static double findLatitudeInterval(Collection<? extends HorizontalPosition> positions) {
        HorizontalPosition pos0 = positions.iterator().next();
        return positions.stream().mapToDouble(pos -> Math.abs(pos.getLatitude() - pos0.getLatitude())).distinct()
                .filter(diff -> !Precision.equals(diff, 0, LATITUDE_EPSILON)).min().getAsDouble();
    }

    /**
     * Judges whether a set of positions crosses the date line and not the prime meridian.
     * If the positions cross both the prime meridian and the date line, returns false.
     * @param positions (Collection of {@link HorizontalPosition}) Input positions
     * @return (boolean) Whether the positions cross only the date line
     *
     * @author otsuru
     * @since 2023/3/9
     */
    public static boolean crossesDateLine(Collection<? extends HorizontalPosition> positions) {
        double[] longitudes = positions.stream().mapToDouble(HorizontalPosition::getLongitude).distinct().sorted().toArray();
        if (longitudes.length <= 1) return false;

        double largestGap = longitudes[0] + 360 - longitudes[longitudes.length - 1];
        double gapStartLongitude = longitudes[longitudes.length - 1];
        double gapEndLongitude = longitudes[0];
        for (int i = 1; i < longitudes.length; i++) {
            if (longitudes[i] - longitudes[i - 1] > largestGap) {
                largestGap = longitudes[i] - longitudes[i - 1];
                gapStartLongitude = longitudes[i - 1];
                gapEndLongitude = longitudes[i];
            }
        }

        // Return true when start of gap is in western hemisphere and end of gap is in eastern hemisphere,
        //   thus the gap crosses the prime meridian but not the date line.
        //   This is when the set of positions crosses the date line and not the prime meridian.
        if (gapStartLongitude <= 0 && 0 <= gapEndLongitude) return true;
        // Otherwise, false. (Either the positions are clustered on one hemisphere, or crosses the prime meridian.)
        else return false;
    }

    public static double latitudeForTheta(double theta) {
        return Latitude.valueForTheta(theta);
    }

    /**
     * Construct from geographic latitude and longitude.
     * @param latitude (double) Geographic latitude [deg]. [-90:90]
     * @param longitude (double) Longitude [deg]. [-180:360]
     */
    public HorizontalPosition(double latitude, double longitude) {
        this.latitude = new Latitude(latitude);
        this.longitude = new Longitude(longitude);
    }

    /**
     * Checks whether this position is inside a given coordinate range.
     * Lower limit is included; upper limit is excluded.
     * However, upper latitude limit is included when it is 90 (if maximum latitude is correctly set as 90).
     * @param latitudeRange ({@link LinearRange}) Latitude range [deg].
     * @param longitudeRange ({@link CircularRange}) Longitude range [deg].
     * @return (boolean) Whether this position is inside the given range.
     *
     * @author otsuru
     * @since 2021/11/21
     */
    public boolean isInRange(LinearRange latitudeRange, CircularRange longitudeRange) {
        if (latitudeRange.check(latitude.getLatitude()) && longitudeRange.check(longitude.getLongitude())) return true;
        else return false;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + latitude.hashCode();
        result = prime * result + longitude.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        HorizontalPosition other = (HorizontalPosition) obj;
        return latitude.equals(other.latitude) && longitude.equals(other.longitude);
    }

    /**
     * Sorting order is latitude &rarr; longitude.
     */
    @Override
    public int compareTo(HorizontalPosition o) {
        int lat = latitude.compareTo(o.latitude);
        return lat != 0 ? lat : longitude.compareTo(o.longitude);
    }

    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute azimuth with.
     * @return (double) Azimuth [rad] from this to the given position [0:2pi).
     */
    public double computeAzimuthRad(HorizontalPosition position) {
        return Earth.computeAzimuthRad(this, position);
    }
    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute azimuth with.
     * @return (double) Azimuth [deg] from this to the given position [0:360).
     */
    public double computeAzimuthDeg(HorizontalPosition position) {
        return Math.toDegrees(Earth.computeAzimuthRad(this, position));
    }

    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute back azimuth with.
     * @return (double) Back azimuth [rad] from the given position to this position [0:2pi).
     */
    public double computeBackAzimuthRad(HorizontalPosition position) {
        return Earth.computeBackAzimuthRad(this, position);
    }
    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute back azimuth with.
     * @return (double) Back azimuth [deg] from the given position to this position [0:360).
     */
    public double computeBackAzimuthDeg(HorizontalPosition position) {
        return Math.toDegrees(Earth.computeBackAzimuthRad(this, position));
    }

    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute epicentral distance to.
     * @return (double) Epicentral distance [rad] between this and the given position [0:pi].
     */
    public double computeEpicentralDistanceRad(HorizontalPosition horizontalPosition) {
        return Earth.computeEpicentralDistanceRad(horizontalPosition, this);
    }
    /**
     * @param horizontalPosition ({@link HorizontalPosition}) Position to compute epicentral distance to.
     * @return (double) Epicentral distance [deg] between this and horizontalPosition [0:180].
     */
    public double computeEpicentralDistanceDeg(HorizontalPosition horizontalPosition) {
        return Math.toDegrees(Earth.computeEpicentralDistanceRad(horizontalPosition, this));
    }

    public double computeGeographicalAzimuthRad(HorizontalPosition position) {
        return Earth.computeGeographicalAzimuthRad(this, position);
    }

    public double computeGeographicalDistanceRad(HorizontalPosition horizontalPosition) {
        return Earth.computeGeographicalDistanceRad(horizontalPosition, this);
    }

    /**
     * 元点loc0と入力locとの大円上の中点を求める 半径は考慮しない locとloc0のなす震央距離を⊿
     * loc0を北極に持って行ったときのlocの経度をphi1 とすると、点(r, ⊿/2, 0)
     * をｚ軸周りにphi１回転して、loc0を北極から元の位置に戻す作業をすればいい
     *
     * @param position {@link HorizontalPosition} of target
     * @return {@link HorizontalPosition} of the center between the position and
     * this
     */
    public HorizontalPosition computeMidpoint(HorizontalPosition position) {
        double delta = computeEpicentralDistanceRad(position); // locとthis との震央距離
        // System.out.println("delta: " + delta);
        // theta = ⊿/2の zx平面上の点
        XYZ midXYZ = new RThetaPhi(1, delta * 0.5, 0).toCartesian();
        // locの点
        XYZ locXYZ = position.toXYZ(Earth.EARTH_RADIUS);
        // thisをzx面上に戻したときのloc
        locXYZ = locXYZ.rotateaboutZ(-1 * getPhi());
        // loc0を北極に
        locXYZ = locXYZ.rotateaboutY(-1 * getTheta());
        RThetaPhi locRTP = locXYZ.toSphericalCoordinate();
        // その時の phi1
        double phi1 = locRTP.getPhi();
        // System.out.println("phi1 " + phi1);
        midXYZ = midXYZ.rotateaboutZ(phi1);
        midXYZ = midXYZ.rotateaboutY(getTheta());
        midXYZ = midXYZ.rotateaboutZ(getPhi());
        RThetaPhi midRTP = midXYZ.toSphericalCoordinate();
        // System.out.println(midRTP);
        return new HorizontalPosition(Latitude.valueForTheta(midRTP.getTheta()), Math.toDegrees(midRTP.getPhi()));
        // System.out.println(midLoc);
    }

    /**
     * d = 2・N・ψ
     * <p>
     * ここに， 地点1の緯度φ1，経度λ1，地点2の緯度φ2，経度λ2のときの直交座標地を それぞれ （x1，y1，z1），（x2，y2，z2）
     * とすると2地点間の直距離 Rn は
     * <p>
     * A = 6378140m（地球赤道半径），B = 6356755m（地球極半径），ｅ**2 = (A**2 - B**2)/A**2 e：離心率
     * <p>
     * N1 = A/sqrt(1 - e**2・sin**2φ1)， N_2 = A/sqrt(1 - e2・sin**2φ2) x1 =
     * N_1・cosφ1cosλ_1， x2 = N_2・cosφ_2cosλ_2 y1 = N_1・cosφ1sinλ_1， y2 =
     * N_2・cosφ_2sinλ_2 z1 = N_1・(1 - e**2)sinφ_1， z2 = N_2・(1 - e**2)sinφ_2
     * <p>
     * 中心から2地点を見込んだ中心角の半分の角 ψ は
     * <p>
     * ψ = sin-1( (Rn/2)/N )， N = (N1 + N2)/2
     * <p>
     * ただし，計算に使用した緯度・経度はラジアンに変換。標高は無視して計算。 TODO ずれる
     *
     * @param position {@link HorizontalPosition} of target
     * @return 大円上での距離 精度はそこまでよくない
     */
    public double computePath(HorizontalPosition position) {
        double distance;
        Ellipse e = new Ellipse(Earth.EQUATORIAL_RADIUS, Earth.POLAR_RADIUS);

        double r0 = e.toR(0.5 * Math.PI - getTheta());
        double r1 = e.toR(0.5 * Math.PI - position.getTheta());

        // System.out.println(a + " " + Earth.EQUATORIAL_RADIUS);

        XYZ xyz0 = toXYZ(r0);
        XYZ xyz = position.toXYZ(r1);
        // System.out.println(xyz0+" \n"+xyz);
        double r = xyz.getDistance(xyz0);

        double n1 = Earth.EQUATORIAL_RADIUS / Math.sqrt(1 -
                Earth.E * Earth.E * Math.sin(Math.toRadians(getLatitude())) *
                        Math.sin(Math.toRadians(getLatitude())));
        // System.out.println(n1 + " " + N1);
        double n2 = Earth.EQUATORIAL_RADIUS / Math.sqrt(1 -
                Earth.E * Earth.E * Math.sin(Math.toRadians(position.getLatitude())) *
                        Math.sin(Math.toRadians(position.getLatitude())));
        double n = (n1 + n2) / 2;
        double kai = FastMath.asin(r / 2 / n);
        distance = 2 * kai * n;
        return distance;
    }

    /**
     * Computes the position of a point that can be reached by travelling
     * at a certain azimuth for a certain distance from the current position.
     * @param azimuthDeg (double) Azimuth of direction to head from this position [deg].
     * @param distanceDeg (double) Epicentral distance to travel [deg].
     * @return (HorizontalPosition) Point located at distance from self along azimuth.
     * @author anselme
     */
    public HorizontalPosition pointAlongAzimuth(double azimuthDeg, double distanceDeg) {
        double azimuth = Math.toRadians(azimuthDeg);
        double distance = Math.toRadians(distanceDeg);

        // colatitude of current position
        double thetaO = getTheta();
        // cosine of colatitude of result position, from spherical law of cosines
        double cosThetaP = Math.cos(distance) * Math.cos(thetaO)
                + Math.sin(distance) * Math.sin(thetaO) * Math.cos(azimuth);
        // colatitude of result position
        double thetaP = FastMath.acos(cosThetaP);

        // cosine of longitude difference, from spherical law of cosines
        double cosDPhi = (Math.cos(distance) - Math.cos(thetaO) * Math.cos(thetaP)) / (Math.sin(thetaO) * Math.sin(thetaP));
        // sine of longitude difference, from spherical law of sines
        double sinDPhi = Math.sin(distance) * Math.sin(azimuth) / Math.sin(thetaP);
        // longitude of result position
        double phiP = getPhi() + FastMath.atan2(sinDPhi, cosDPhi);

        // set result position
        double lat = Latitude.valueForTheta(thetaP);
        double lon = Math.toDegrees(phiP);
        if (lon < -180) lon += 360;
        if (lon > 180) lon -= 360;
        return new HorizontalPosition(lat, lon);
    }

    /**
     * Geographic latitude [deg]. [-90:90]
     * @return (double) Geographic latitude [deg].
     */
    public double getLatitude() {
        return latitude.getLatitude();
    }

    /**
     * Geocentric latitude [rad]. [-&pi;/2:&pi;/2]
     * @return (double) Geocentric latitude [rad].
     */
    public double getGeocentricLatitudeRad() {
        return latitude.getGeocentricLatitudeRad();
    }

    /**
     * Geocentric colatitude in spherical coordinate &theta; [rad]. [0:&pi;]
     * @return (double) Geocentric colatitude &theta; [rad].
     */
    public double getTheta() {
        return latitude.getTheta();
    }

    /**
     * Longitude [deg] in range [-180:180).
     * @return (double) Longitude [deg].
     */
    public double getLongitude() {
        return longitude.getLongitude();
    }

    /**
     * Longitude [deg], either in range [0:360) or [-180:180).
     * @param crossDateLine (boolean) Whether to use range [0:360). Otherwise, [-180:180).
     * @return (double) Longitude [deg].
     *
     * @author otsuru
     * @since 2023/4/30
     */
    public double getLongitude(boolean crossDateLine) {
        return longitude.getLongitude(crossDateLine);
    }

    /**
     * Longitude in spherical coordinate &phi; [rad] in range [-&pi;:&pi;).
     * @return (double) Longitude &phi; [rad].
     */
    public double getPhi() {
        return longitude.getPhi();
    }

    /**
     * @param r (double) Radius [km].
     * @return ({@link FullPosition}) {@link FullPosition} newly created with the input radius (deep copy).
     */
    public FullPosition toFullPosition(double r) {
        return new FullPosition(latitude.getLatitude(), longitude.getLongitude(), r);
    }

    /**
     * @return Point2D of this
     */
    public Point2D toPoint2D() {
        return new Point2D(getLongitude(), getLatitude());
    }

    /**
     * @param r radius
     * @return {@link XYZ} at radius ｒ
     */
    public XYZ toXYZ(double r) {
        return RThetaPhi.toCartesian(r, getTheta(), getPhi());
    }

    /**
     * Print padded String so that all positions will have uniform number of digits.
     */
    @Override
    public String toString() {
        return latitude.toString() + " " + longitude.toString();
    }

    /**
     * Print padded String so that all positions will have uniform number of digits.
     * Can be printed in range [0:360) instead of [-180:180).
     *
     * @param crossDateLine (boolean) Whether to use longitude range [0:360) instead of [-180:180).
     * @return (String) Padded string of longitude.
     *
     * @author otsuru
     * @since 2023/3/10
     */
    public String toString(boolean crossDateLine) {
        return latitude.toString() + " " + longitude.toString(crossDateLine);
    }

    /**
     * Turn the position value into a short String code with 14 letters.
     * Begins with latitude: 1 letter ("P" for positive or "M" for negative)
     * followed by 2 + {@value Latitude#DECIMALS} digits,
     * then longitude: 1 letter ("N" for -100 and under, "M" for under 0, "P" for under 100, "Q" for 100 and above)
     * followed by 2 + {@value Longitude#DECIMALS} digits.
     * @return (String) Code for this position.
     */
    public String toCode() {
        return latitude.toCode() + longitude.toCode();
    }

}
