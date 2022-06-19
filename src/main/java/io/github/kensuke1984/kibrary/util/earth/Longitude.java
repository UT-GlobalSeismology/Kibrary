package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Longitude [-180, 180).
 * The value is rounded off to the 4th decimal place.
 * <p>
 * The input can be in range [-180, 360).
 * If you input 200, then the value is considered to be -160.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 */
final class Longitude implements Comparable<Longitude> {

    /**
     * the number of decimal places to round off the longitude value
     */
    private static final int PRECISION = 4;

    /**
     * [-180, 180) geographic longitude [deg]
     */
    private final double longitude;

    /**
     * [-&pi;, &pi;) &phi; in spherical coordinates [rad]
     */
    private final double phi;

    /**
     * @param longitude [deg] [-180, 360)
     */
    Longitude(double longitude) {
        if (!withinValidRange(longitude)) throw new IllegalArgumentException(
                "The input longitude: " + longitude + " is invalid (must be [-180, 360)).");

        if (180 <= longitude) {
            this.longitude = Precision.round(longitude - 360, PRECISION);
        } else {
            this.longitude = Precision.round(longitude, PRECISION);
        }
        phi = FastMath.toRadians(this.longitude);
    }

    /**
     * check if the longitude is within [-180, 360)
     *
     * @param longitude [deg]
     * @return if the longitude is valid
     */
    private static boolean withinValidRange(double longitude) {
        return -180 <= longitude && longitude < 360;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
//		long temp;
//		temp = Double.doubleToLongBits(longitude);
//		result = prime * result + (int) (temp ^ (temp >>> 32));
        int temp = (int) longitude;
        result = prime * result + temp;
        return result;
    }

    /**
     *@author anselme equals within epsilon
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Longitude other = (Longitude) obj;

        return MathAid.equalWithinEpsilon(longitude, other.longitude, Math.pow(10, -PRECISION)/2);
    }

    @Override
    public int compareTo(Longitude o) {
        return Double.compare(longitude, o.longitude);
    }

    /**
     * [-180, 180)
     *
     * @return longitude [deg]
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * [-&pi;, &pi;)
     *
     * @return &phi; [rad]
     */
    public double getPhi() {
        return phi;
    }

    /**
     * Print String so that all longitudes will have uniform number of digits.
     * Total number of digits is the sum of:
     * <ul>
     * <li>1 (the minus sign)</li>
     * <li>3 (the integer part; 0~360)</li>
     * <li>1 (the period)</li>
     * <li>{@value #PRECISION} (the decimal part)</li>
     * <ul>
     */
    @Override
    public String toString() {
        return MathAid.padToString(longitude, 4, PRECISION, " ");
    }

    /**
     * Turn the longitude value into a short String code.
     * 1 letter ("N" for -100 and under, "M" for under 0, "P" for under 100, "Q" for 100 and above)
     * followed by 2 + {@value #PRECISION} digits.
     * @return (String) code
     */
    public String toCode() {
        String letter;
        double absolute = Math.abs(longitude);
        if (longitude <= -100) {  // -180 ~ -100
            letter = "N";
            absolute -= 100;
        }
        else if (longitude < 0) {  // -100 ~ 0
            letter = "M";
        }
        else if (longitude < 100) {  // 0 ~ 100
            letter = "P";
        }
        else {  // 100 ~ 180
            letter = "Q";
            absolute -= 100;
        }

        int number = (int) Math.round(absolute * Math.pow(10, PRECISION));

        return letter + MathAid.padToString(number, 2 + PRECISION, "0");
    }

}
