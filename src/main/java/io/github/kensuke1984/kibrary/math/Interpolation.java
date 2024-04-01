package io.github.kensuke1984.kibrary.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Methods concerning interpolation of values on a line or surface.
 *
 * @author ?
 * @since a long time ago
 */
public class Interpolation {

    private static final int GRID_PRECISION = 4;

    public static double threePointInterpolation(double x, double[] xi, double[] yi) {
        double[] h = new double[3];
        for (int i = 0; i < 3; i++)
            h[i] = x - xi[i];
        double h01 = xi[1] - xi[0];
        double h02 = xi[2] - xi[0];
        double h12 = xi[2] - xi[1];

        double phi0 = h[1] * h[2] / (h01 * h02);
        double phi1 = -h[0] * h[2] / (h01 * h12);
        double phi2 = h[0] * h[1] / (h02 * h12);

        return yi[0] * phi0 + yi[1] * phi1 + yi[2] * phi2;
    }

    public static double linear(double x, double[] xi, double[] yi) {
        return yi[0] + (yi[1] - yi[0]) * (x - xi[0]) / (xi[1] - xi[0]);
    }

    /**
     * @param originalMap (Map of {@link FullPosition}, Double) Map data to be interpolated.
     * @param sampleLongitudes (double[]) Longitudes at which to interpolate.
     * @param longitudeMargin (double) The margin to append at the western and eastern ends of the region [deg]
     *         (including edges of voxel gaps).
     *          Also used to recognize voxel gaps in the longitude direction.
     * @param mosaic (boolean) Whether to create a mosaic-style map. When false, a smooth map will be created.
     * @return (LinkedHashMap of {@link FullPosition} to Double) Interpolated map data.
     *
     * @author otsuru
     * @since 2023/3/24
     */
    public static Map<FullPosition, Double> inEachWestEastLine(Map<FullPosition, Double> originalMap, double[] sampleLongitudes,
            double longitudeMarginDeg, boolean mosaic) {
        // This is created as LinkedHashMap to preserve the order of grid points
        Map<FullPosition, Double> interpolatedMap = new LinkedHashMap<>();

        Set<FullPosition> allPositions = originalMap.keySet();
        boolean crossDateLine = HorizontalPosition.crossesDateLine(allPositions);
        double[] radii = allPositions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();
        double[] latitudes = allPositions.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();

        for (double radius : radii) {
            for (double latitude : latitudes) {
                List<FullPosition> inLinePositions = allPositions.stream()
                        .filter(pos -> Precision.equals(pos.getLatitude(), latitude, FullPosition.LATITUDE_EPSILON)
                                && Precision.equals(pos.getR(), radius, FullPosition.RADIUS_EPSILON))
                        .sorted(Comparator.comparing(pos -> pos.getLongitude(crossDateLine)))
                        .collect(Collectors.toList());

                // pack data values at original points along this latitude in a Trace (x is the longitude direction here)
                double[] x = inLinePositions.stream().mapToDouble(position -> position.getLongitude(crossDateLine)).toArray();
                double[] y = inLinePositions.stream().mapToDouble(position -> originalMap.get(position)).toArray();
                Trace originalTrace = new Trace(x, y);

                // split the trace at gaps
                List<Trace> splitTraces = splitTraceAtGaps(originalTrace, longitudeMarginDeg);
                // interpolate each of the split traces and store the results
                List<Trace> interpolatedTraces = splitTraces.stream()
                        .map(trace -> interpolateTraceAtPoints(trace, sampleLongitudes, longitudeMarginDeg, mosaic)).collect(Collectors.toList());
                for (Trace interpolatedTrace : interpolatedTraces) {
                    for (int i = 0; i < interpolatedTrace.getLength(); i++) {
                        double longitude = interpolatedTrace.getXAt(i);
                        double value = interpolatedTrace.getYAt(i);
                        FullPosition position = new FullPosition(latitude, longitude, radius);
                        interpolatedMap.put(position, value);
                    }
                }
            }
        }
        return interpolatedMap;
    }

    /**
     * Interpolation on each horizontal 2-D surface.
     * This method supposes that
     * <ul>
     * <li> data points are given on several distinct radii </li>
     * <li> data points are aligned, equally spaced, on several distinct latitudes </li>
     * <li> longitudes of the points are equally spaced along each latitude </li>
     * </ul>
     *
     * In mosaic mode, a two-step nearest-neighbor interpolation is done: first in the longitude direction, then in the latitude.
     * In smooth interpolation, a two-step cubic interpolation is done: first in the longitude direction, then in the latitude.
     *
     * @param originalMap (Map of {@link FullPosition}, Double) Map data to be interpolated.
     * @param gridInterval (double) Grid spacing to be used in output map.
     * @param latitudeMargin (double) The margin to append at the northern and southern ends of the region (including edges of voxel gaps).
     * @param latitudeInKm (boolean) Whether the above value is given in [km] or [deg].
     * @param longitudeMargin (double) The margin to append at the western and eastern ends of the region (including edges of voxel gaps).
     *          Also used to recognize voxel gaps in the longitude direction.
     * @param longitudeInKm (boolean) Whether the above value is given in [km] or [deg].
     * @param mosaic (boolean) Whether to create a mosaic-style map. When false, a smooth map will be created.
     * @return (LinkedHashMap of {@link FullPosition} to Double) Interpolated map data.
     *
     * @author otsuru
     * @since 2023/3/4
     */
    public static Map<FullPosition, Double> inEachMapLayer(Map<FullPosition, Double> originalMap, double gridInterval,
            double latitudeMargin, boolean latitudeInKm, double longitudeMargin, boolean longitudeInKm, boolean mosaic) {
        // This is created as LinkedHashMap to preserve the order of grid points
        Map<FullPosition, Double> interpolatedMap = new LinkedHashMap<>();

        Set<FullPosition> allPositions = originalMap.keySet();
        boolean crossDateLine = HorizontalPosition.crossesDateLine(allPositions);
        double[] radii = allPositions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        for (double radius : radii) {
            Set<FullPosition> inLayerPositions = allPositions.stream()
                    .filter(pos -> Precision.equals(pos.getR(), radius, FullPosition.RADIUS_EPSILON)).collect(Collectors.toSet());
            double[] latitudes = inLayerPositions.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();

            // a Map to store interpolated Traces at each latitude
            Map<Double, List<Trace>> eachLatitudeTraces = new HashMap<>();

            //~interpolate along each latitude containing data points
            for (double latitude : latitudes) {
                List<FullPosition> inLatitudePositions = inLayerPositions.stream()
                        .filter(pos -> Precision.equals(pos.getLatitude(), latitude, FullPosition.LATITUDE_EPSILON))
                        .sorted(Comparator.comparing(pos -> pos.getLongitude(crossDateLine)))
                        .collect(Collectors.toList());

                // pack data values at original points along this latitude in a Trace (x is the longitude direction here)
                double[] x = inLatitudePositions.stream().mapToDouble(position -> position.getLongitude(crossDateLine)).toArray();
                double[] y = inLatitudePositions.stream().mapToDouble(position -> originalMap.get(position)).toArray();
                Trace originalTrace = new Trace(x, y);

                // split the trace at gaps
                double smallCircleRadius = radius * Math.cos(Math.toRadians(latitude));
                double longitudeMarginDeg = longitudeInKm ? Math.toDegrees(longitudeMargin / smallCircleRadius) : longitudeMargin;
                List<Trace> splitTraces = splitTraceAtGaps(originalTrace, longitudeMarginDeg);
                // interpolate each of the split traces and store the results
                List<Trace> interpolatedTraces = splitTraces.stream()
                        .map(trace -> interpolateTraceOnGrid(trace, gridInterval, longitudeMarginDeg, mosaic)).collect(Collectors.toList());
                eachLatitudeTraces.put(latitude, interpolatedTraces);
            }

            //~interpolate along each grid longitude (meridian)
            double minLongitude = eachLatitudeTraces.values().stream().flatMap(List::stream)
                    .mapToDouble(trace -> trace.getMinX()).min().getAsDouble();
            double maxLongitude = eachLatitudeTraces.values().stream().flatMap(List::stream)
                    .mapToDouble(trace -> trace.getMaxX()).max().getAsDouble();
            int nGridLongitudes = (int) Math.round((maxLongitude - minLongitude) / gridInterval) + 1;
            for (int i = 0; i < nGridLongitudes; i++) {
                double longitude = Precision.round(minLongitude + i * gridInterval, GRID_PRECISION);

                // extract indices of latitudes with values defined on this longitude
                int[] indicesWithValue = IntStream.range(0, latitudes.length)
                        .filter(j -> hasValueInTraceList(longitude, eachLatitudeTraces.get(latitudes[j]))).sorted().toArray();
                if (indicesWithValue.length == 0) {
                    System.err.println("!! No index for longitude " + longitude);
                    System.err.println("    Did you set the margins properly?");
                    continue;
                }
                // split into groups of consequtive latitudes
                List<int[]> indexGroups = splitIndexGroups(indicesWithValue);
                for (int[] indexArray : indexGroups) {
                    double[] xs = Arrays.stream(indexArray).mapToDouble(j -> latitudes[j]).toArray();
                    // pack data values at original latitudes along this meridian in a Trace (x is the latitude direction here)
                    double[] ys = Arrays.stream(xs).map(latitude -> findValueInTraceList(longitude, eachLatitudeTraces.get(latitude))).toArray();
                    Trace discreteTrace = new Trace(xs, ys);

                    // interpolate the Trace and resample at all grid points
                    double latitudeMarginDeg = latitudeInKm ? Math.toDegrees(latitudeMargin / radius) : latitudeMargin;
                    Trace interpolatedTrace = interpolateTraceOnGrid(discreteTrace, gridInterval, latitudeMarginDeg, mosaic);
                    for (int j = 0; j < interpolatedTrace.getLength(); j++) {
                        interpolatedMap.put(new FullPosition(interpolatedTrace.getXAt(j), longitude, radius), interpolatedTrace.getYAt(j));
                    }
                }
            }
        }

        return interpolatedMap;
    }

    private static boolean hasValueInTraceList(double x, List<Trace> traceList) {
        for (Trace trace : traceList) {
            if (trace.getMinX() <= x && x <= trace.getMaxX()) return true;
        }
        return false;
    }
    private static double findValueInTraceList(double x, List<Trace> traceList) {
        for (Trace trace : traceList) {
            if (trace.getMinX() <= x && x <= trace.getMaxX()) {
                return trace.getYAt(trace.findNearestXIndex(x));
            }
        }
        throw new IllegalArgumentException("Value not found for x=" + x + " in Trace list.");
    }

    private static List<Trace> splitTraceAtGaps(Trace trace, double margin) {
        List<Trace> traceList = new ArrayList<>();
        int iStart = 0;
        // from i=1, check if [x(i-1),x(i)] is much larger than margin*2
        for (int i = 1; i < trace.getLength(); i++) {
            if (trace.getXAt(i) - trace.getXAt(i - 1) > margin * 2.5) {
                traceList.add(trace.subTrace(iStart, i));
                iStart = i;
            }
        }
        // add last trace
        traceList.add(trace.subTrace(iStart, trace.getLength()));
        return traceList;
    }
    private static List<int[]> splitIndexGroups(int[] numbers) {
        List<int[]> groupList = new ArrayList<>();
        // first k of current group
        int kStart = 0;
        // number that corresponds to the last k
        int lastNumber = numbers[0];
        // we have set the values for k=0 above, so start from k=1
        for (int k = 1; k < numbers.length; k++) {
            if (numbers[k] != lastNumber + 1) {
                // add the last group to groupList
                groupList.add(Arrays.copyOfRange(numbers, kStart, k));
                // set the next group starting from k
                kStart = k;
            }
            lastNumber = numbers[k];
        }
        // add last group
        groupList.add(Arrays.copyOfRange(numbers, kStart, numbers.length));
        return groupList;
    }

    /**
     * Interpolates a given 1-D plot at all points that are within the domain and are multiples of the specified grid interval.
     * The domain is taken with a margin on either side, as [xMin-margin:xMax+margin].
     * In mosaic mode, nearest-neighbor interpolation is done; otherwise, cubic interpolation.
     *
     * @param originalTrace ({@link Trace}) Original 1-D plot.
     * @param gridInterval (double) Grid interval at which to interpolate.
     * @param margin (double) The length of margin to add at either end of the domain.
     * @param mosaic (boolean) Whether to interpolate as mosaic (nearest-neighbor). Otherwise, smooth (cubic) interpolation.
     * @return ({@link Trace}) Interpolated 1-D plot, where x values are multiples of gridInterval.
     *
     * @author otsuru
     * @since 2023/3/4
     */
    public static Trace interpolateTraceOnGrid(Trace originalTrace, double gridInterval, double margin, boolean mosaic) {
        // set the x range so that the margin is added to either end
        double startX = MathAid.ceil((originalTrace.getMinX() - margin) / gridInterval) * gridInterval;
        double endX = MathAid.floor((originalTrace.getMaxX() + margin) / gridInterval) * gridInterval;
        int nGridXs = (int) Math.round((endX - startX) / gridInterval) + 1;
        // array of sample points at which to interpolate
        double[] xs = new double[nGridXs];
        for (int i = 0; i < nGridXs; i++) {
            xs[i] = Precision.round(startX + i * gridInterval, GRID_PRECISION);
        }
        return interpolateTraceAtPoints(originalTrace, xs, margin, mosaic);
    }

    /**
     * Interpolates a given 1-D plot at a set of specified points.
     * The domain is taken with a margin on either side, as [xMin-margin:xMax+margin].
     * If a specified point is outside this domain, it will not be used.
     * In mosaic mode, nearest-neighbor interpolation is done; otherwise, cubic interpolation.
     *
     * @param originalTrace ({@link Trace}) Original 1-D plot.
     * @param samplePoints (double[]) Points at which to interpolate.
     * @param margin (double) The length of margin to add at either end of the domain.
     * @param mosaic (boolean) Whether to interpolate as mosaic (nearest-neighbor). Otherwise, smooth (cubic) interpolation.
     * @return ({@link Trace}) Interpolated 1-D plot, where x values are those included in samplePoints and within the domain.
     *
     * @author otsuru
     * @since 2023/3/24
     */
    private static Trace interpolateTraceAtPoints(Trace originalTrace, double[] samplePoints, double margin, boolean mosaic) {
        int lastIndex = originalTrace.getLength() - 1;
        // set the x range so that the margin is added to either end
        double startX = originalTrace.getMinX() - margin;
        double endX = originalTrace.getMaxX() + margin;
        // extract sample points that are within the domain
        double[] xs = Arrays.stream(samplePoints).filter(x -> startX <= x && x <= endX).toArray();
        double[] ys = new double[xs.length];

        if (mosaic) {
            // use value at nearest index
            for (int i = 0; i < xs.length; i++) {
                ys[i] = originalTrace.getYAt(originalTrace.findNearestXIndex(xs[i]));
            }

        } else {
            // for each interval, including both ends, which is 1 more than the number of points on the Trace (k=-1 is added)
            for (int k = -1; k < originalTrace.getLength(); k++) {
                PolynomialFunction interpolatedFunc = cubic(originalTrace.getY(), k);
                // the actual x values at the ends of the segment, instead of [0:1]
                // At either end of the domain, margin*2 is appended so that only one side of the flipping mirror is used.
                double xLower = (k == -1) ? originalTrace.getXAt(0) - margin * 2 : originalTrace.getXAt(k);
                double xUpper = (k == lastIndex) ? originalTrace.getXAt(lastIndex) + margin * 2 : originalTrace.getXAt(k + 1);

                // compute y values for points that are within this segment
                for (int i = 0; i < xs.length; i++) {
                    if (xLower <= xs[i] && xs[i] < xUpper) {
                        double normalizedX = (xs[i] - xLower) / (xUpper - xLower);
                        ys[i] = interpolatedFunc.value(normalizedX);
                    }
                }
            }
        }
        return new Trace(xs, ys);
    }

    /**
     * Interpolates a given 1-D plot at a specified point.
     * The domain is taken with a margin on either side, as [xMin-margin:xMax+margin].
     * In mosaic mode, nearest-neighbor interpolation is done; otherwise, cubic interpolation.
     *
     * @param originalTrace ({@link Trace}) Original 1-D plot.
     * @param samplePoint (double) Point at which to interpolate. Must be in domain; otherwise, Exception is thrown.
     * @param margin (double) The length of margin to add at either end of the domain.
     * @param mosaic (boolean) Whether to interpolate as mosaic (nearest-neighbor). Otherwise, smooth (cubic) interpolation.
     * @return (double) Interpolated value.
     *
     * @author otsuru
     * @since 2023/3/25
     */
    public static double interpolateTraceAtPoint(Trace originalTrace, double samplePoint, double margin, boolean mosaic) {
        int lastIndex = originalTrace.getLength() - 1;
        // set the x range so that the margin is added to either end
        double startX = originalTrace.getMinX() - margin;
        double endX = originalTrace.getMaxX() + margin;
        if (samplePoint < startX || endX < samplePoint) throw new IllegalArgumentException("samplePoint " + samplePoint + "out of range.");

        if (mosaic) {
            return originalTrace.getYAt(originalTrace.findNearestXIndex(samplePoint));

        } else {
            // find which interval samplePoint is in
            int interval = -1;
            for (int k = 0; k < originalTrace.getLength(); k++) {
                if (samplePoint >= originalTrace.getXAt(k)) interval = k;
            }

            // the actual x values at the ends of the segment, instead of [0:1]
            // At either end of the domain, margin*2 is appended so that only one side of the flipping mirror is used.
            double xLower = (interval == -1) ? originalTrace.getXAt(0) - margin * 2 : originalTrace.getXAt(interval);
            double xUpper = (interval == lastIndex) ? originalTrace.getXAt(lastIndex) + margin * 2 : originalTrace.getXAt(interval + 1);

            // interpolate at samplePoint
            double normalizedX = (samplePoint - xLower) / (xUpper - xLower);
            PolynomialFunction interpolatedFunc = cubic(originalTrace.getY(), interval);
            return interpolatedFunc.value(normalizedX);
        }
    }

    /**
     * Computes cubic interpolation for the segment of the specified index, given a list of values (y_0, y_1, y_2, ...).
     * When specifying index 5, for example, the range [x_5:x_6] will be interpolated,
     * but supposing the x values in that segment are in [0:1] (thus, x_4=-1, x_5=0, x_6=1, and x_7=2).
     * <p>
     * Both ends will be set as a flipped mirror, i.e. f(-1)=-f(0) and f'(-1)=f'(0), f(last+1)=-f(last) and f'(last+1)=f'(last).
     * With indices -1 and lastIndex, the interpolated function to the left and right of the input range can be obtained.
     *
     * @param values (double[]) Data values y_0, y_1, y_2, ...
     * @param iSegment (int) Index of segment to interpolate. [-1:lastIndex]
     * @return (PolynomialFunction) Cubic function.
     *
     * @author otsuru
     * @since 2023/3/8
     */
    private static PolynomialFunction cubic(double[] values, int iSegment) {
        int lastIndex = values.length - 1;
        if (iSegment < -1 || lastIndex < iSegment)
            throw new IllegalArgumentException("iSegment " + iSegment + " out of bounds [-1:" + lastIndex + "]");

        double f0;
        double fPrime0;
        double f1;
        double fPrime1;

        if (values.length == 1) {
            f0 = (iSegment == -1) ? -values[0] : values[0];
            f1 = (iSegment == -1) ? values[0] : -values[0];
            fPrime0 = 0;
            fPrime1 = 0;
        } else if (iSegment == -1) {
            // set left side as a flipped mirror, i.e. f(-1)=-f(0) and f'(-1)=f'(0)
            f0 = -values[0];
            fPrime0 = (values[1] + values[0]) / 2;
            f1 = values[0];
            fPrime1 = (values[1] + values[0]) / 2;
        } else if (iSegment == lastIndex) {
            // set right side as a flipped mirror, i.e. f(last+1)=-f(last) and f'(last+1)=f'(last)
            f0 = values[lastIndex];
            fPrime0 = (-values[lastIndex] - values[lastIndex - 1]) / 2;
            f1 = -values[lastIndex];
            fPrime1 = (-values[lastIndex] - values[lastIndex - 1]) / 2;
        } else {
            if (iSegment == 0) {
                // the left end is a flipped mirror, i.e. f(-1)=-f(0)
                f0 = values[0];
                fPrime0 = (values[1] + values[0]) / 2;
            } else {
                f0 = values[iSegment];
                fPrime0 = (values[iSegment + 1] - values[iSegment - 1]) / 2;
            }
            if (iSegment == lastIndex - 1) {
                // the right end is a flipped mirror, i.e. f(last+1)=-f(last)
                f1 = values[lastIndex];
                fPrime1 = (-values[lastIndex] - values[lastIndex - 1]) / 2;
            } else {
                f1 = values[iSegment + 1];
                fPrime1 = (values[iSegment + 2] - values[iSegment]) / 2;
            }
        }
        double a = 2 * f0 - 2 * f1 + fPrime0 + fPrime1;
        double b = -3 * f0 + 3 * f1 - 2 * fPrime0 - fPrime1;
        double c = fPrime0;
        double d = f0;
        double[] coeffs = {d, c, b, a};
        return new PolynomialFunction(coeffs);
    }

}
