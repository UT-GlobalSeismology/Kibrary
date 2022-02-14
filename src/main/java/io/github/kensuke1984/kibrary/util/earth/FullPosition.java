package io.github.kensuke1984.kibrary.util.earth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.math.geometry.RThetaPhi;
import io.github.kensuke1984.kibrary.math.geometry.XYZ;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * <p>
 * 3D position on Earth.
 * <p>
 * Latitude (-180, 180) Longitude（-90, 90）Radius [0, &infin;)
 * <p>
 * <b>This class is IMMUTABLE</b>.
 * <p>
 * The radius is rounded off to the 6th decimal place.
 *
 * @author Kensuke Konishi
 * @author anselme add methods used for BP/FP catalog
 */
public class FullPosition extends HorizontalPosition {

    /**
     * the number of decimal places to round off the radius value
     */
    private static final int R_PRECISION = 6;
    /**
     * [0, &infin;) radius [km]
     */
    private final double R;

    /**
     * @param latitude  [deg] geographical latitude
     * @param longitude [deg] longitude
     * @param r         [km] radius
     */
    public FullPosition(double latitude, double longitude, double r) {
        super(latitude, longitude);
        R = Precision.round(r, R_PRECISION);
//        R = r;
    }

    public static double toLatitude(double theta) {
        return Latitude.toLatitude(theta);
    }

    /**
     * Sorting order is latitude &rarr; longitude &rarr; radius.
     */
    @Override
    public int compareTo(HorizontalPosition o) {
        int horizontalCompare = super.compareTo(o);
        if (horizontalCompare != 0 || !(o instanceof FullPosition)) return horizontalCompare;
        return Double.compare(R, ((FullPosition) o).R);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
//      long temp;
//      temp = Double.doubleToLongBits(R);
//      result = prime * result + (int) (temp ^ (temp >>> 32));
//      int temp = (int) (R / eps / 10);
        int temp = (int) R;
        result = prime * result + temp;
        return result;
    }

    /**
     *
     * @author anselme equals within epsilon
     * @return true only when the other object is also FullPosition and latitude, longitude, radius are all equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        FullPosition other = (FullPosition) obj;
//        return Double.doubleToLongBits(R) == Double.doubleToLongBits(other.R);
        return MathAid.equalWithinEpsilon(R, other.R, Math.pow(10, -R_PRECISION)/2);
    }

    /**
     * @return [km] radius (not depth)
     */
    public double getR() {
        return R;
    }

    /**
     * @return {@link RThetaPhi} of this
     */
    public RThetaPhi getRThetaPhi() {
        return new RThetaPhi(R, getTheta(), getPhi());
    }

    /**
     * Cartesian coordinate
     *
     * @return {@link XYZ} of this
     */
    public XYZ toXYZ() {
        return RThetaPhi.toCartesian(R, getTheta(), getPhi());
    }

    /**
     * used for FP/BP catalog
     * @return
     * @author anselme
     */
    public XYZ toXYZGeographical() {
        double theta = Math.toRadians(90. - getLatitude());
        return RThetaPhi.toCartesian(R, theta, getPhi());
    }

    /**
     * @return
     * @author anselme
     */
    public HorizontalPosition toHorizontalPosition() {
        return new HorizontalPosition(getLatitude(), getLongitude());
    }

    /**
     * @param location {@link FullPosition} to compute distance with
     * @return [km] one-line distance from the location
     */
    public double getDistance(FullPosition location) {
        return location.toXYZ().getDistance(toXYZ());
    }

    /**
     * @param location
     * @return
     * @author anselme
     */
    public double getDistanceGeographical(FullPosition location) {
        return location.toXYZGeographical().getDistance(toXYZGeographical());
    }

    /**
     * @param locations to be sorted.
     * @return locations in the order of the distance from this.
     */
    public FullPosition[] getNearestLocation(FullPosition[] locations) {
        FullPosition[] newLocations = locations.clone();
        Arrays.parallelSort(newLocations, Comparator.comparingDouble(this::getDistance));
        return newLocations;
    }

    /**
     * @param locations
     * @param maxSearchRange
     * @return
     * @author anselme
     */
    public FullPosition[] getNearestLocation(FullPosition[] locations, double maxSearchRange) {
        FullPosition[] newLocations = Arrays.stream(locations).parallel().filter(loc -> {
    //		System.out.println(loc + " " + this.toString() + " " + this.getDistance(loc));
            return Math.abs(this.R - loc.getR()) < maxSearchRange;
        }).collect(Collectors.toList()).toArray(new FullPosition[0]);
    //	System.out.println(newLocations.length);
        Arrays.parallelSort(newLocations, Comparator.comparingDouble(this::getDistance));
        return newLocations;
    }

    /**
     * @param locations
     * @return
     * @author anselme
     */
    public FullPosition[] getNearestHorizontalLocation(FullPosition[] locations) {
        FullPosition[] newLocations = Arrays.stream(locations)
                .filter(loc -> loc.getR() == this.getR())
                .collect(Collectors.toList())
                .toArray(new FullPosition[0]);
        Arrays.sort(newLocations, Comparator.comparingDouble(this::getDistance));
        return newLocations;
    }

    @Override
    public String toString() {
        return super.toString() + " " + MathAid.padToString(R, 4, R_PRECISION);
    }

}
