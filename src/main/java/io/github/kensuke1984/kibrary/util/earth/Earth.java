package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.geometry.Ellipse;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Earth utility.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public final class Earth {
    private Earth() {}

    /**
     * Earth radius [km].
     */
    public final static double EARTH_RADIUS = 6371.0;
    /**
     * Equatorial radius [km]. a
     */
    public final static double EQUATORIAL_RADIUS = 6378.137;
    /**
     * (1st) Flattening. f = 1 - b/a
     */
    public final static double FLATTENING = 1.0 / 298.257223563;
    /**
     * Polar radius [km]. b
     */
    public final static double POLAR_RADIUS = (1.0 - FLATTENING) * EQUATORIAL_RADIUS;  // 6356.752314140356;
    /**
     * (1st) Eccentricity. e = sqrt(1 - b^2/a^2) = sqrt(f(2-f))
     */
    public final static double E = Math.sqrt(FLATTENING * (2.0 - FLATTENING));  // 0.08181919104281514;
    /**
     * 3rd flattening. n = f/(2-f)
     */
    public final static double N = FLATTENING / (2.0 - FLATTENING);  // 0.0016792443125758178;

    /**
     * Transform a geographic latitude to geocentric latitude.
     * @param geographicLatitude (double) Geographic latitude [rad]. [-&pi;/2:&pi;/2]
     * @return (double) Geocentric latitude [rad]. [-&pi;/2:&pi;/2]
     */
    static double geographicToGeocentric(double geographicLatitude) {
        if (0.5 * Math.PI < Math.abs(geographicLatitude))
            throw new IllegalArgumentException("Geographic latitude must be in [-pi/2:pi/2]: " + geographicLatitude);
        return Math.atan(Math.tan(geographicLatitude) * (1.0 - E * E));
    }

    /**
     * Transform a geographic latitude to reduced latitude.
     * @param geographicLatitude (double) Geographic latitude [rad]. [-&pi;/2:&pi;/2]
     * @return (double) Reduced latitude [rad]. [-&pi;/2:&pi;/2]
     */
    static double geographicToReduced(double geographicLatitude) {
        if (0.5 * Math.PI < Math.abs(geographicLatitude))
            throw new IllegalArgumentException("Geographic latitude must be in [-pi/2:pi/2]: " + geographicLatitude);
        return Math.atan(Math.tan(geographicLatitude) * Math.sqrt(1.0 - E * E));
    }

    /**
     * Transform a geocentric latitude to geographic latitude.
     * @param geocentricLatitude (double) Geocentric latitude [rad]. [-&pi;/2:&pi;/2]
     * @return (double) Geographic latitude [rad]. [-&pi;/2:&pi;/2]
     */
    static double geocentricToGeographic(double geocentricLatitude) {
        if (0.5 * Math.PI < Math.abs(geocentricLatitude))
            throw new IllegalArgumentException("Geocentric latitude must be in [-pi/2:pi/2]: " + geocentricLatitude);
        return Math.atan(Math.tan(geocentricLatitude) / (1 - E * E));
    }

    /**
     * Compute the radius of a position on the surface considering ellipse.
     * @param position ({@link HorizontalPosition}) Horizontal position to compute for.
     * @return (double) Radius of the position considering ellipse [km].
     */
    public static double computeRadiusOnSurface(HorizontalPosition position) {
        double psi = position.getGeocentricLatitudeRad();
        // r^2 [(cos(psi)/a)^2 + (sin(psi)/b)^2] = 1
        return 1 / Math.sqrt(Math.cos(psi) * Math.cos(psi) / EQUATORIAL_RADIUS / EQUATORIAL_RADIUS
                + Math.sin(psi) * Math.sin(psi) / POLAR_RADIUS / POLAR_RADIUS);
    }

    /**
     * Compute distance between points on the same meridian with specified lower and upper latitudes, considering ellipse.
     * @param lowerLatitude (double) Lower geographic latitude [deg]. [-90:upperLatitude)
     * @param upperLatitude (double) Upper geographic latitude [deg]. (lowerLatitude:90]
     * @return (double) Length of meridional part on the surface [km].
     */
    public static double computeMeridianLength(double lowerLatitude, double upperLatitude) {
        LinearRange.checkValidity("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        if (0.0 <= lowerLatitude) return computeMeridianLength(upperLatitude) - computeMeridianLength(lowerLatitude);
        else if (upperLatitude < 0.0) return computeMeridianLength(lowerLatitude) - computeMeridianLength(upperLatitude);
        else return computeMeridianLength(lowerLatitude) + computeMeridianLength(upperLatitude);
    }

    /**
     * Compute a distance along a meridian between the equator and an input latitude, considering ellipse.
     * @param latitude (double) Geographic latitude [deg]. [-90:90]
     * @return (double) Distance along the meridian between the equator and latitude [km].
     */
    private static double computeMeridianLength(double latitude) {
        if (latitude < -90.0 || 90.0 < latitude)
            throw new IllegalArgumentException("Input latitude: " + latitude + " is invalid.");
        return getSUMforMeridionalParts(latitude) * EQUATORIAL_RADIUS;
    }

    /**
     * 第三扁平率を用いて子午線弧の近似値を求める際のべき級数の和（長軸によらない部分）
     * ある長軸aにおける弧の長さを求めるには aをかけなければならない
     * @param latitude [-90:90] geographical latitude
     * @return 長軸１ｋｍの時の 赤道から入力された緯度までの子午線弧の長さ
     */
    private static double getSUMforMeridionalParts(double latitude) {
        if (latitude < -90 || 90 < latitude)
            throw new IllegalArgumentException("Input latitude: " + latitude + " is invalid.");
        double s = 0; // length
        // double N=0;
        double n2 = N * N;
        double n3 = N * n2;
        double n4 = n2 * n2;
        double phi = Math.toRadians(latitude);
        if (phi < 0) phi *= -1;

        s += (1 + n2 / 4 + n4 / 64) * phi;
        s += -1.5 * (N - n3 / 8) * Math.sin(phi * 2);
        s += 15 / 16.0 * (n2 - n4 / 4) * Math.sin(4 * phi);
        s += -35 / 48.0 * n3 * Math.sin(6 * phi);
        s += 315 / 512.0 * n4 * Math.sin(8 * phi);
        s /= 1 + N;
        return s;
    }

    /**
     * Compute the length of semi-major axis of ellipse, similar to the Earth, on which the position exists.
     * @param position ({@link FullPosition}) Position to compute for.
     * @return (double) Length of semi-major axis [km] of ellipse, similar to Earth, on which the position exists.
     */
    public static double computeSemiMajorAxis(FullPosition position) {
        if (position.getR() == 0) {
            System.err.println("Position has no radius information; the semi-major axis for the surface is returned.");
            return EQUATORIAL_RADIUS;
        }
        double r2 = position.getR() * position.getR();
        double psi = position.getGeocentricLatitudeRad();
        // sqrt( x^2 + y^2/(b/a)^2 ) = a
        return Math.sqrt(r2 * Math.cos(psi) * Math.cos(psi) + r2 * Math.sin(psi) * Math.sin(psi) / (1 - E * E));
    }

    /**
     * Compute volume within an input radius, latitude, and longitude range.
     * Note that, for instance, the range is [point-0.5*dX : point+0.5*dX].
     * @param point ({@link FullPosition}) Center position.
     * @param dr (double) Radius range [km].
     * @param dLatitude (double) Latitude range [deg] in geographical latitude.
     * @param dLongitude (double) Longitude range [deg].
     * @return (double) Volume [km<sup>3</sup>].
     */
    public static double computeVolume(FullPosition point, double dr, double dLatitude, double dLongitude) {
        // compute semi-major axis (= radius on expanded sphere)
        double r = point.getR();
        if (r <= 0) throw new IllegalArgumentException("Radius must be positive: " + r);
        double startA = computeSemiMajorAxis(point.toFullPosition(r - 0.5 * dr));
        double endA = computeSemiMajorAxis(point.toFullPosition(r + 0.5 * dr));
        // compute theta (= colatitude for reduced latitude)
        double latitude = point.getLatitude();
        double startTheta = 0.5 * Math.PI - geographicToReduced(Math.toRadians(latitude + 0.5 * dLatitude));
        double endTheta = 0.5 * Math.PI - geographicToReduced(Math.toRadians(latitude - 0.5 * dLatitude));
        // volume of expanded sphere
        double volume = (FastMath.pow(endA, 3) - FastMath.pow(startA, 3)) * (Math.cos(startTheta) - Math.cos(endTheta))
                * Math.toRadians(dLongitude) / 3.0;
        return volume * (1 - FLATTENING);
    }

    /**
     * @param startA (double) Lower major axis [km]. [0:endA)
     * @param endA (double) Upper major axis [km]. (startA:)
     * @param startLatitude (double) Lower geographic latitude [deg]. [-90:endLatitude)
     * @param endLatitude (double) Upper geographic latitude [deg]. (startLatitude:90]
     * @return (double) Area of cross section inside specified major axis and latitude range.
     */
    public static double computeCrossSection(double startA, double endA, double startLatitude, double endLatitude) {
        LinearRange.checkValidity("Major axis", startA, endA, 0.0);
        LinearRange.checkValidity("Latitude", startLatitude, endLatitude, -90.0, 90.0);

        Ellipse el0 = new Ellipse(startA, startA - startA * FLATTENING);
        Ellipse el1 = new Ellipse(endA, endA - endA * FLATTENING);
        double startPsi = geographicToGeocentric(Math.toRadians(startLatitude));
        double endPsi = geographicToGeocentric(Math.toRadians(endLatitude));
        if (startPsi < 0) {
            startPsi += Math.PI;
            endPsi += Math.PI;
        }
        double s0 = el0.computeEllipticalSectorArea(startPsi, endPsi);
        double s1 = el1.computeEllipticalSectorArea(startPsi, endPsi);
        return s1 - s0;
    }

    /**
     * Compute geographical distance between two positions on a sphere.
     * @param pos1 ({@link HorizontalPosition}) Position of point 1.
     * @param pos2 ({@link HorizontalPosition}) Position of point 2.
     * @return (double) Geographical distance between the two positions [rad]. [0:pi]
     * @author anselme
     */
    public static double computeGeographicalDistanceRad(HorizontalPosition pos1, HorizontalPosition pos2) {
        // convert to colatitude [rad]
        double theta1 = Math.toRadians(90.0 - pos1.getLatitude());
        double theta2 = Math.toRadians(90.0 - pos2.getLatitude());
        double deltaPhi = pos1.getPhi() - pos2.getPhi();
        return computeDistance(theta1, theta2, deltaPhi);
    }

    /**
     * Compute epicentral distance between two positions on a sphere.
     * @param pos1 ({@link HorizontalPosition}) Position of point 1.
     * @param pos2 ({@link HorizontalPosition}) Position of point 2.
     * @return (double) Epicentral distance between the two positions [rad]. [0:pi]
     */
    public static double computeEpicentralDistanceRad(HorizontalPosition pos1, HorizontalPosition pos2) {
        double theta1 = pos1.getTheta();
        double theta2 = pos2.getTheta();
        double deltaPhi = pos1.getPhi() - pos2.getPhi();
        return computeDistance(theta1, theta2, deltaPhi);
    }

    private static double computeDistance(double theta1, double theta2, double deltaPhi) {
        double cosAlpha = Math.sin(theta1) * Math.sin(theta2) * Math.cos(deltaPhi)
                + Math.cos(theta1) * Math.cos(theta2);
        if (1.0 < cosAlpha) cosAlpha = 1.0;
        else if (cosAlpha < -1.0) cosAlpha = -1.0;
        return FastMath.acos(cosAlpha);
    }

    /**
     * Compute geographical azimuth from source to receiver on a sphere.
     * @param sourcePos ({@link HorizontalPosition}) Position of source.
     * @param receiverPos ({@link HorizontalPosition}) Position of receiver.
     * @return (double) Geographical azimuth of the receiver from the source [rad]. [0:2pi)
     * @author anselme
     */
    public static double computeGeographicalAzimuthRad(HorizontalPosition sourcePos, HorizontalPosition receiverPos) {
        // convert to colatitude [rad]
        double thetaS = Math.toRadians(90.0 - sourcePos.getLatitude());
        double thetaR = Math.toRadians(90.0 - receiverPos.getLatitude());
        double deltaPhi = -sourcePos.getPhi() + receiverPos.getPhi();
        return computeAzimuth(thetaS, thetaR, deltaPhi);
    }

    /**
     * Compute azimuth from source to receiver on a sphere.
     * @param sourcePos ({@link HorizontalPosition}) Position of source.
     * @param receiverPos ({@link HorizontalPosition}) Position of receiver.
     * @return (double) Azimuth of the receiver from the source [rad]. [0:2pi)
     */
    public static double computeAzimuthRad(HorizontalPosition sourcePos, HorizontalPosition receiverPos) {
        double thetaS = sourcePos.getTheta();
        double thetaR = receiverPos.getTheta();
        double deltaPhi = -sourcePos.getPhi() + receiverPos.getPhi();
        return computeAzimuth(thetaS, thetaR, deltaPhi);
    }

    private static double computeAzimuth(double thetaS, double thetaR, double deltaPhi) {
        double sinDistance = Math.sin(computeDistance(thetaS, thetaR, deltaPhi));

        if (Precision.equals(sinDistance, 0.0, MathAid.PRECISION_EPSILON)) {
            // Set azimuth as 0 when source and receiver are at same position or at antipodes.
            return 0.0;

        } else {
            double cos = (Math.cos(thetaR) * Math.sin(thetaS)
                    - Math.sin(thetaR) * Math.cos(thetaS) * Math.cos(deltaPhi)) / sinDistance;
            if (1.0 < cos) cos = 1.0;
            else if (cos < -1.0) cos = -1.0;
            double sin = Math.sin(thetaR) * Math.sin(deltaPhi) / sinDistance;
            double az = FastMath.acos(cos);
            return 0.0 <= sin ? az : -az + 2.0 * Math.PI;
        }
    }

    /**
     * Compute azimuth from receiver to source on a sphere.
     * @param sourcePos ({@link HorizontalPosition}) Position of source.
     * @param receiverPos ({@link HorizontalPosition}) Position of receiver.
     * @return (double) Back azimuth of the receiver from the source [rad]. [0:2pi)
     */
    public static double computeBackAzimuthRad(HorizontalPosition sourcePos, HorizontalPosition receiverPos) {
        return computeAzimuthRad(receiverPos, sourcePos);
    }

}
