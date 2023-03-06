package io.github.kensuke1984.kibrary.math;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Methods concerning interpolation of values on a line or surface.
 *
 * @since a long time ago
 */
public class Interpolation {

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
     * @param originalMap (Map of {@link FullPosition}, Double) Map data to be interpolated
     * @param gridInterval (double) Grid spacing to be used in output map
     * @param mosaic (boolean) Whether to create a mosaic-style map. When false, a smooth map will be created.
     * @return (LinkedHashMap of {@link FullPosition}, Double) Interpolated map data
     *
     * @author otsuru
     * @since 2023/3/4
     */
    public static Map<FullPosition, Double> inEachMapLayer(Map<FullPosition, Double> originalMap, double gridInterval, boolean mosaic) {
        // This is created as LinkedHashMap to preserve the order of grid points
        Map<FullPosition, Double> interpolatedMap = new LinkedHashMap<>();

        Set<FullPosition> allPositions = originalMap.keySet();
        double[] radii = allPositions.stream().mapToDouble(pos -> pos.getR()).distinct().sorted().toArray();

        for (double radius : radii) {
            Set<FullPosition> inLayerPositions = allPositions.stream()
                    .filter(pos -> Precision.equals(pos.getR(), radius, FullPosition.RADIUS_EPSILON)).collect(Collectors.toSet());
            double[] latitudes = inLayerPositions.stream().mapToDouble(pos -> pos.getLatitude()).distinct().sorted().toArray();

            // a Map to store interpolated Traces at each latitude
            Map<Double, Trace> eachLatitudeTraces = new HashMap<>();

            //~interpolate along each latitude containing data points
            for (double latitude : latitudes) {
                List<FullPosition> inLatitudePositions = inLayerPositions.stream()
                        .filter(pos -> Precision.equals(pos.getLatitude(), latitude, FullPosition.LATITUDE_EPSILON))
                        .sorted().collect(Collectors.toList());

                // pack data values at original points along this latitude in a Trace (x is the longitude direction here)
                double[] x = inLatitudePositions.stream().mapToDouble(position -> position.getLongitude()).toArray();
                double[] y = inLatitudePositions.stream().mapToDouble(position -> originalMap.get(position)).toArray();
                Trace originalTrace = new Trace(x, y);

                // interpolate the Trace and store it
                eachLatitudeTraces.put(latitude, interpolate(originalTrace, gridInterval, mosaic));
            }

            //~interpolate along each grid longitude (meridian)
            double minLongitude = eachLatitudeTraces.values().stream().mapToDouble(trace -> trace.getMinX()).min().getAsDouble();
            double maxLongitude = eachLatitudeTraces.values().stream().mapToDouble(trace -> trace.getMaxX()).max().getAsDouble();
//            double startLongitude = Math.ceil(minLongitude / gridInterval) * gridInterval;
//            double endLongitude = Math.floor(maxLongitude / gridInterval) * gridInterval;
            int nGridLongitudes = (int) Math.round((maxLongitude - minLongitude) / gridInterval) + 1;
            for (int i = 0; i < nGridLongitudes; i++) {
                double longitude = minLongitude + i * gridInterval;

                // pack data values at original latitudes along this meridian in a Trace (x is the latitude direction here)
                double[] x = Arrays.stream(latitudes).filter(latitude -> {
                    Trace latitudeTrace = eachLatitudeTraces.get(latitude);
                    return (latitudeTrace.getMinX() <= longitude && longitude <= latitudeTrace.getMaxX());
                }).sorted().toArray();
                double[] y = Arrays.stream(x).map(latitude -> {
                    Trace latitudeTrace = eachLatitudeTraces.get(latitude);
                    return latitudeTrace.getYAt(latitudeTrace.findNearestXIndex(longitude));
                }).toArray();
                Trace discreteTrace = new Trace(x, y);

                // interpolate the Trace and resample at all grid points
                Trace interpolatedTrace = interpolate(discreteTrace, gridInterval, mosaic);
                for (int j = 0; j < interpolatedTrace.getLength(); j++) {
                    interpolatedMap.put(new FullPosition(interpolatedTrace.getXAt(j), longitude, radius), interpolatedTrace.getYAt(j));
                }
            }
        }

        return interpolatedMap;
    }

    private static Trace interpolate(Trace originalTrace, double gridInterval, boolean mosaic) {
        double startLongitude = Math.ceil(originalTrace.getMinX() / gridInterval) * gridInterval;
        double endLongitude = Math.floor(originalTrace.getMaxX() / gridInterval) * gridInterval;
        int nGridLongitudes = (int) Math.round((endLongitude - startLongitude) / gridInterval) + 1;
        double[] x = new double[nGridLongitudes];
        double[] y = new double[nGridLongitudes];
        for (int i = 0; i < nGridLongitudes; i++) {
            x[i] = startLongitude + i * gridInterval;
        }

        if (mosaic) {
            // use value at nearest index
            for (int i = 0; i < nGridLongitudes; i++) {
                y[i] = originalTrace.getYAt(originalTrace.findNearestXIndex(x[i]));
            }
        } else {
            // for each interval, which is 1 less than the number of points on the Trace
            for (int k = 0; k < originalTrace.getLength() - 1; k++) {

            }
        }
        return new Trace(x, y);
    }

}
