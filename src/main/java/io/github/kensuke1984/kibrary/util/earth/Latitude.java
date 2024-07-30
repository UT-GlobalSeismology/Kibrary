package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Latitude [-90:90].
 * The value is rounded off to the 4th decimal place.
 *<p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 */
final class Latitude implements Comparable<Latitude> {

    /**
     * The number of decimal places to round off the latitude value.
     */
    static final int DECIMALS = 4;

    /**
     * Geographic latitude [deg]. [-90:90]
     */
    private final double geographicLatitude;
    /**
     * Geocentric latitude [rad]. [-&pi;/2:&pi;/2]
     */
    private final double geocentricLatitudeRad;
    /**
     * Geocentric colatitude &theta; in spherical coordinates [rad]. [0:&pi;]
     */
    private final double theta;

    /**
     * Method to convert a (double) latitude value to a (double) theta value.
     * @param theta [rad] spherical coordinates [0:&pi;]
     * @return geographic latitude [deg]
     */
    static double valueForTheta(double theta) {
        if (theta < 0 || Math.PI < theta) throw new IllegalArgumentException(
                "Invalid theta (must be in [0:pi]): " + theta + " @" +
                        Thread.currentThread().getStackTrace()[1].getMethodName());

        double geocentric = 0.5 * Math.PI - theta;
        return FastMath.toDegrees(Earth.toGeographicLatitude(geocentric));
    }


    /**
     * Construct from geographic latitude. The input must be within [-90:90].
     * @param geographicLatitude (double) Geographic latitude [deg]. [-90:90]
     */
    Latitude(double geographicLatitude) {
        if (!withinValidRange(geographicLatitude)) throw new IllegalArgumentException(
                "The input latitude: " + geographicLatitude + " is invalid (must be in [-90:90]).");

        this.geographicLatitude = Precision.round(geographicLatitude, DECIMALS);
        geocentricLatitudeRad = Earth.toGeocentricLatitude(FastMath.toRadians(this.geographicLatitude));
        theta = 0.5 * Math.PI - geocentricLatitudeRad;
    }

    /**
     * Check if input value is within [-90:90].
     *
     * @param latitude (double) Input value [deg].
     * @return (boolean) Whether the latitude is valid.
     */
    private static boolean withinValidRange(double latitude) {
        return -90 <= latitude && latitude <= 90;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
//		long temp;
//		temp = Double.doubleToLongBits(geographicLatitude);
//		result = prime * result + (int) (temp ^ (temp >>> 32));
        int temp = (int) (geographicLatitude);
        result = prime * result + temp;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Latitude other = (Latitude) obj;

        return Precision.equals(geographicLatitude, other.geographicLatitude,  Math.pow(10, -DECIMALS)/2);
    }

    @Override
    public int compareTo(Latitude o) {
        return Double.compare(geographicLatitude, o.geographicLatitude);
    }

    /**
     * Geographic latitude [deg]. [-90:90]
     * @return (double) Geographic latitude [deg].
     */
    double getLatitude() {
        return geographicLatitude;
    }

    /**
     * Geocentric latitude [rad]. [-&pi;/2:&pi;/2]
     * @return (double) Geocentric latitude [rad].
     */
    double getGeocentricLatitudeRad() {
        return geocentricLatitudeRad;
    }

    /**
     * Geocentric colatitude in spherical coordinate &theta; [rad]. [0:&pi;]
     * @return (double) Geocentric colatitude &theta; [rad].
     */
    double getTheta() {
        return theta;
    }

    /**
     * Print String so that all latitudes will have uniform number of digits.
     * Total number of digits is the sum of:
     * <ul>
     * <li>1 (the minus sign)</li>
     * <li>2 (the integer part; 0~90)</li>
     * <li>1 (the period)</li>
     * <li>{@value #DECIMALS} (the decimal part)</li>
     * <ul>
     */
    @Override
    public String toString() {
        return MathAid.padToString(geographicLatitude, 3, DECIMALS, false);
    }

    /**
     * Turn the latitude value into a short String code.
     * 1 letter ("P" for positive or "M" for negative) followed by 2 + {@value #DECIMALS} digits.
     * @return (String) Code for this latitude.
     */
    public String toCode() {
        String sign;
        if (geographicLatitude >= 0) sign = "P";
        else sign = "M";

        double absolute = Math.abs(geographicLatitude);
        int number = (int) Math.round(absolute * Math.pow(10, DECIMALS));

        return sign + MathAid.padToString(number, 2 + DECIMALS, true);
    }
}
