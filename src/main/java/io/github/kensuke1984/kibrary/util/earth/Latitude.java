package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Latitude [-90, 90].
 * The value is rounded off to the 4th decimal place.
 *<p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 */
class Latitude implements Comparable<Latitude> {

    /**
     * the number of decimal places to round off the latitude value
     */
    private static final int PRECISION = 4;

    /**
     * [-90, 90] geographic latitude [deg]
     */
    private double geographicLatitude;
    /**
     * [-&pi;/2, &pi;/2] geocentric latitude [rad]
     */
    private double geocentricLatitude;
    /**
     * [0, &pi;] &theta; in spherical coordinates [rad]
     */
    private double theta;

    /**
     * Method to convert a (double) latitude value to a (double) theta value.
     * @param theta [rad] spherical coordinates [0, &pi;]
     * @return geographic latitude [deg]
     */
    static double toLatitude(double theta) {
        if (theta < 0 || Math.PI < theta) throw new IllegalArgumentException(
                "Invalid theta (must be[0, pi]): " + theta + " @" +
                        Thread.currentThread().getStackTrace()[1].getMethodName());

        double geocentric = 0.5 * Math.PI - theta;
        return FastMath.toDegrees(Earth.toGeographical(geocentric));
    }


    /**
     * Creates a Latitude instance. The input must be within [-90, 90].
     * @param geographicLatitude [deg] [-90, 90]
     */
    Latitude(double geographicLatitude) {
        if (!checkLatitude(geographicLatitude)) throw new IllegalArgumentException(
                "The input latitude: " + geographicLatitude + " is invalid (must be [-90, 90]).");

        this.geographicLatitude = Precision.round(geographicLatitude, PRECISION);
        geocentricLatitude = Earth.toGeocentric(FastMath.toRadians(geographicLatitude));
        theta = 0.5 * Math.PI - geocentricLatitude;
    }

    /**
     * check if the latitude is within [-90, 90].
     *
     * @param latitude [deg]
     * @return if the latitude is valid
     */
    private static boolean checkLatitude(double latitude) {
        return -90 <= latitude && latitude <= 90;
    }

    @Override
    public int compareTo(Latitude o) {
        return Double.compare(geographicLatitude, o.geographicLatitude);
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

    /**
     *@author anselme compare within eps
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Latitude other = (Latitude) obj;

        return MathAid.equalWithinEpsilon(geographicLatitude, other.geographicLatitude,  Math.pow(10, -PRECISION)/2);
    }

    /**
     * @return geographic latitude [deg]
     */
    public double getLatitude() {
        return geographicLatitude;
    }

    /**
     * @return geocentric latitude [rad]
     */
    public double getGeocentricLatitude() {
        return geocentricLatitude;
    }

    /**
     * @return theta [radian]
     */
    public double getTheta() {
        return theta;
    }

    /**
     * Print String so that all latitudes will have uniform number of digits.
     * Total number of digits is the sum of:
     * <ul>
     * <li>1 (the minus sign)</li>
     * <li>2 (the integer part; 0~90)</li>
     * <li>1 (the period)</li>
     * <li>{@value #PRECISION} (the decimal part)</li>
     * <ul>
     */
    @Override
    public String toString() {
        return MathAid.padToString(geographicLatitude, 3, PRECISION, " ");
    }

    /**
     * Turn the latitude value into a short String code.
     * 1 letter ("P" for positive or "M" for negative) followed by 2 + {@value #PRECISION} digits.
     * @return (String) code
     */
    public String toCode() {
        String sign;
        if (geographicLatitude >= 0) sign = "P";
        else sign = "M";

        double absolute = Math.abs(geographicLatitude);
        int number = (int) Math.round(absolute * Math.pow(10, PRECISION));

        return sign + MathAid.padToString(number, 2 + PRECISION, "0");
    }
}
