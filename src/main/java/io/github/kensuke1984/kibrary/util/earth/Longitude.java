package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Longitude [-180, 180).
 * The value is rounded off to the 4th decimal place.
 * <p>
 * The input can be in range [-180, 360].
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
    static final int PRECISION = 4;

    /**
     * [-180:180) geographic longitude [deg]
     */
    private final double longitude;

    /**
     * [-&pi;:&pi;) &phi; in spherical coordinates [rad]
     */
    private final double phi;

    /**
     * @param longitude (double) Longitude [deg]; [-180:360].
     */
    Longitude(double longitude) {
        // switch to range [-180:180) after rounding
        double fixedLongitude = fix(Precision.round(longitude, PRECISION));
        // round again to eliminate computation error
        this.longitude = Precision.round(fixedLongitude, PRECISION);
        // [deg] to [rad]
        phi = FastMath.toRadians(this.longitude);
    }

    /**
     * Fix longitude from range [-180:360] to range [-180:180).
     * @param longitude (double) Longitude [deg] in [-180:360].
     * @return (double) Longitude [deg] in [-180:180).
     *
     * @author otsuru
     * @since 2023/12/4
     */
    static double fix(double longitude) {
        if (longitude < -180 || 360 < longitude)
            throw new IllegalArgumentException("The input longitude " + longitude + " is invalid (must be in [-180:360)).");
        return (180 <= longitude) ? longitude - 360 : longitude;
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

        return Precision.equals(longitude, other.longitude, Math.pow(10, -PRECISION)/2);
    }

    @Override
    public int compareTo(Longitude o) {
        return Double.compare(longitude, o.longitude);
    }

    /**
     * Geographic longitude in range [-180, 180).
     * @return (double) longitude [deg]
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Geographic longitude, either in range [0:360) or [-180, 180).
     * @param crossDateLine (boolean) Whether to use range [0:360). Otherwise, [-180, 180).
     * @return (double) longitude [deg]
     *
     * @author otsuru
     * @since 2023/4/30
     */
    public double getLongitude(boolean crossDateLine) {
        return (crossDateLine && longitude < 0) ? longitude + 360 : longitude;
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
     * Print padded String so that all longitudes will have uniform number of digits.
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
        return MathAid.padToString(longitude, 4, PRECISION, false);
    }

    /**
     * Print padded String so that all longitudes will have uniform number of digits.
     * Can be printed in range [0:360) instead of [-180:180).
     * Total number of digits is the sum of:
     * <ul>
     * <li>1 (the minus sign)</li>
     * <li>3 (the integer part; 0~360)</li>
     * <li>1 (the period)</li>
     * <li>{@value #PRECISION} (the decimal part)</li>
     * <ul>
     *
     * @param crossDateLine (boolean) Whether to use range [0:360) instead of [-180:180).
     * @return (String) Padded string of longitude
     *
     * @author otsuru
     * @since 2023/3/10
     */
    public String toString(boolean crossDateLine) {
        double correctedLongitude = getLongitude(crossDateLine);
        return MathAid.padToString(correctedLongitude, 4, PRECISION, false);
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

        return letter + MathAid.padToString(number, 2 + PRECISION, true);
    }

}
