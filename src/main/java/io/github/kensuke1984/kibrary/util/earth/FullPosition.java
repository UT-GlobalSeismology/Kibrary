package io.github.kensuke1984.kibrary.util.earth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.geometry.RThetaPhi;
import io.github.kensuke1984.kibrary.math.geometry.XYZ;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * <p>
 * 3D position on Earth.
 * <p>
 * Latitude (-180, 180) Longitude（-90, 90）Radius [0, &infin;)
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * <p>
 * The radius is rounded off to the 6th decimal place.
 *
 * @author Kensuke Konishi
 * @author anselme add methods used for BP/FP catalog
 */
public final class FullPosition extends HorizontalPosition {

    /**
     * the number of decimal places to round off the radius value
     */
    private static final int RADIUS_PRECISION = 6;
    /**
     * margin to decide whether two radii are the same value
     */
    public static final double RADIUS_EPSILON = Math.pow(10, -RADIUS_PRECISION) / 2;
    /**
     * [0, &infin;) radius [km]
     */
    private final double radius;

    /**
     * @param latitude  [deg] geographic latitude
     * @param longitude [deg] longitude
     * @param radius [km] radius
     */
    public FullPosition(double latitude, double longitude, double radius) {
        super(latitude, longitude);
        this.radius = Precision.round(radius, RADIUS_PRECISION);
    }

    /**
     * @param latitude  [deg] geographic latitude
     * @param longitude [deg] longitude
     * @param depth [km] depth
     * @return new instance
     */
    public static FullPosition constructByDepth(double latitude, double longitude, double depth) {
        return new FullPosition(latitude, longitude, Precision.round(Earth.EARTH_RADIUS - depth, RADIUS_PRECISION)); //TODO: consider ellipticity of Earth
    }

    /**
     * Checks whether this position is inside a given coordinate range.
     * Lower limit is included; upper limit is excluded.
     * @param minLatitude (double) [-90:maxLatitude)
     * @param maxLatitude (double) (minLatitude:90]
     * @param minLongitude (double) [-180:maxLongitude)
     * @param maxLongitude (double) (minLongitude:360]
     * @param minRadius (double) [0:maxRadius)
     * @param maxRadius (double) (minRadius:)
     * @return (boolean) Whether this position is inside the given range.
     *
     * @author otsuru
     * @since 2022/10/11
     * @deprecated
     */
    public boolean isInRange(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude,
            double minRadius, double maxRadius) {
        if (minRadius < 0 || minRadius > maxRadius) {
            throw new IllegalArgumentException("The input radius range: " + minRadius + ", " + maxRadius + " is invalid.");
        }

        // radius
        if (radius < minRadius || maxRadius <= radius) return false;

        // latitude and longitude
        if (isInRange(minLatitude, maxLatitude, minLongitude, maxLongitude) == false) return false;

        return true;
    }
    /**
     * Checks whether this position is inside a given coordinate range.
     * Lower limit is included; upper limit is excluded.
     * However, upper latitude limit is included when it is 90 (if maximum latitude is correctly set as 90).
     * @param latitudeRange ({@link LinearRange}) Latitude range [deg].
     * @param longitudeRange ({@link CircularRange}) Longitude range [deg].
     * @param radiusRange ({@link LinearRange}) Radius range [km].
     * @return (boolean) Whether this position is inside the given range.
     *
     * @author otsuru
     * @since 2022/10/11
     */
    public boolean isInRange(LinearRange latitudeRange, CircularRange longitudeRange, LinearRange radiusRange) {
        if (isInRange(latitudeRange, longitudeRange) && radiusRange.check(radius)) return true;
        else return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
//      long temp;
//      temp = Double.doubleToLongBits(R);
//      result = prime * result + (int) (temp ^ (temp >>> 32));
//      int temp = (int) (R / eps / 10);
        int temp = (int) radius;
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
        // if horizontal position is different, false
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        FullPosition other = (FullPosition) obj;
        return Precision.equals(radius, other.radius, Math.pow(10, -RADIUS_PRECISION)/2);
    }

    /**
     * Sorting order is latitude &rarr; longitude &rarr; radius.
     */
    @Override
    public int compareTo(HorizontalPosition o) {
        int horizontalCompare = super.compareTo(o);
        if (horizontalCompare != 0 || !(o instanceof FullPosition)) return horizontalCompare;
        return Double.compare(radius, ((FullPosition) o).radius);
    }

    /**
     * @return [km] radius (not depth)
     */
    public double getR() {
        return radius;
    }

    /**
     * @return [km] depth (not radius)
     */
    public double getDepth() {
        return Precision.round(Earth.EARTH_RADIUS - radius, RADIUS_PRECISION); //TODO: consider ellipticity of Earth
    }

    /**
     * @return {@link RThetaPhi} of this
     */
    public RThetaPhi toRThetaPhi() {
        return new RThetaPhi(radius, getTheta(), getPhi());
    }

    /**
     * Cartesian coordinate
     *
     * @return {@link XYZ} of this
     */
    public XYZ toXYZ() {
        return RThetaPhi.toCartesian(radius, getTheta(), getPhi());
    }

    /**
     * used for FP/BP catalog
     * @return
     * @author anselme
     */
    public XYZ toXYZGeographical() {
        double theta = Math.toRadians(90. - getLatitude());
        return RThetaPhi.toCartesian(radius, theta, getPhi());
    }

    /**
     * @return
     * @author anselme
     */
    public HorizontalPosition toHorizontalPosition() {
        return new HorizontalPosition(getLatitude(), getLongitude());
    }

    /**
     * Compute straight-line distance to another position.
     * @param location {@link FullPosition} to compute distance with
     * @return [km] one-line distance from the location
     */
    public double computeStraightDistance(FullPosition location) {
        return location.toXYZ().getDistance(toXYZ());
    }

    /**
     * @param location
     * @return
     * @author anselme
     */
    public double computeStraightDistanceGeographical(FullPosition location) {
        return location.toXYZGeographical().getDistance(toXYZGeographical());
    }

    /**
     * @param locations to be sorted.
     * @return locations in the order of the distance from this.
     */
    public FullPosition[] findNearestPosition(FullPosition[] locations) {
        FullPosition[] newLocations = locations.clone();
        Arrays.parallelSort(newLocations, Comparator.comparingDouble(this::computeStraightDistance));
        return newLocations;
    }

    /**
     * @param locations
     * @param maxSearchRange
     * @return
     * @author anselme
     */
    public FullPosition[] findNearestPosition(FullPosition[] locations, double maxSearchRange) {
        FullPosition[] newLocations = Arrays.stream(locations).parallel().filter(loc -> {
    //		System.out.println(loc + " " + this.toString() + " " + this.getDistance(loc));
            return Math.abs(this.radius - loc.getR()) < maxSearchRange;
        }).collect(Collectors.toList()).toArray(new FullPosition[0]);
    //	System.out.println(newLocations.length);
        Arrays.parallelSort(newLocations, Comparator.comparingDouble(this::computeStraightDistance));
        return newLocations;
    }

    /**
     * @param locations
     * @return
     * @author anselme
     */
    public FullPosition[] findNearestHorizontalPosition(FullPosition[] locations) {
        FullPosition[] newLocations = Arrays.stream(locations)
                .filter(loc -> loc.getR() == this.getR())
                .collect(Collectors.toList())
                .toArray(new FullPosition[0]);
        Arrays.sort(newLocations, Comparator.comparingDouble(this::computeStraightDistance));
        return newLocations;
    }

    /**
     * Print padded String so that all positions will have uniform number of digits.
     */
    @Override
    public String toString() {
        return super.toString() + " " + MathAid.padToString(radius, 4, RADIUS_PRECISION, false);
    }

    /**
     * Print padded String so that all positions will have uniform number of digits.
     * Can be printed in range [0:360) instead of [-180:180).
     *
     * @param crossDateLine (boolean) Whether to use longitude range [0:360) instead of [-180:180).
     * @return (String) Padded string of longitude
     *
     * @author otsuru
     * @since 2023/3/10
     */
    public String toString(boolean crossDateLine) {
        return super.toString(crossDateLine) + " " + MathAid.padToString(radius, 4, RADIUS_PRECISION, false);
    }

}
