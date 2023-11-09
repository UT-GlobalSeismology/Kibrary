package io.github.kensuke1984.kibrary.math;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Utility for a function y = f(x)
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * </p>
 * TODO sorted
 *
 * @author Kensuke Konishi
 */
public final class Trace {

    private final double[] xArray;
    private final double[] yArray;
    private final RealVector xVector;
    private final RealVector yVector;
    /**
     * Index of upward convex, ordered by the absolute values of the convex.
     * <p>
     * Upward convex is defined as
     * 0 &lt; (y(x[i])-y(x[i-1]))*(y(x[i])-y(x[i+1])) and y[i-1] &lt; y[i]
     */
    private final int[] indexOfUpwardConvex;
    /**
     * Index of downward convex, ordered by the absolute values of the convex.
     * <p>
     * Downward convex is defined as
     * 0 &lt; (y(x[i])-y(x[i-1])) * (y(x[i]) - y(x[i+1])) and y[i] &lt; y[i-1]
     */
    private final int[] indexOfDownwardConvex;
    /**
     * Index of a peak, ordered by the absolute values of the convex.
     * <p>
     * Peak is defined as
     * 0 &lt; (y(x[i])-y(x[i-1]))*(y(x[i])-y(x[i+1]))
     */
    private final int[] indexOfPeak;

    /**
     * Create trace from arrays of x and y by deep copy.
     * @param x (double[]) Array for x
     * @param y (double[]) Array for y
     */
    public Trace(double[] x, double[] y) {
        if (x.length != y.length) throw new IllegalArgumentException("Input arrays have different lengths");
        xArray = x.clone();
        yArray = y.clone();
        xVector = new ArrayRealVector(x, false);
        yVector = new ArrayRealVector(y, false);
        indexOfDownwardConvex = IntStream.range(1, xArray.length - 1)
                .filter(i -> yArray[i] < yArray[i - 1] && 0 < (yArray[i + 1] - yArray[i]) * (yArray[i - 1] - yArray[i])).boxed()
                .sorted(Comparator.comparingDouble(o -> -yArray[o] * yArray[o])).mapToInt(i -> i).toArray();
        indexOfUpwardConvex = IntStream.range(1, xArray.length - 1)
                .filter(i -> yArray[i - 1] < yArray[i] && 0 < (yArray[i + 1] - yArray[i]) * (yArray[i - 1] - yArray[i])).boxed()
                .sorted(Comparator.comparingDouble(o -> -yArray[o] * yArray[o])).mapToInt(i -> i).toArray();
        indexOfPeak = IntStream.range(1, xArray.length - 1).filter(i -> 0 < (yArray[i + 1] - yArray[i]) * (yArray[i - 1] - yArray[i])).boxed()
                .sorted(Comparator.comparingDouble(o -> -yArray[o] * yArray[o])).mapToInt(i -> i).toArray();
    }

    /**
     * Read trace from file.
     * @param path ({@link Path}) The file you want to read
     * @param xColumn (int) The column containing x (with first column as 0)
     * @param yColumn (int) The column containing y (with first column as 0)
     * @return ({@link Trace}) Trace made from the file
     * @throws IOException if any
     */
    public static Trace read(Path path, int xColumn, int yColumn) throws IOException {
        InformationFileReader reader = new InformationFileReader(path, true);
        int n = reader.getNumLines();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            if (!reader.hasNext()) throw new IllegalStateException("Failed to read enough rows");
            String[] parts = reader.next().split("\\s+");
            x[i] = Double.parseDouble(parts[xColumn]);
            y[i] = Double.parseDouble(parts[yColumn]);
        }
        return new Trace(x, y);
    }

    /**
     * Read trace from file.
     * x is read from the first column and y from the second column.
     * @param path ({@link Path}) The file you want to read
     * @return ({@link Trace}) Trace made from the file
     * @throws IOException if any
     */
    public static Trace read(Path path) throws IOException {
        return read(path, 0, 1);
    }

    /**
     * Writes X and Y.
     * Each line has X<sub>i</sub> and Y<sub>i</sub>
     *
     * @param path    of the write file
     * @param options if any
     * @throws IOException if any
     */
    public void write(Path path, OpenOption... options) throws IOException {
        List<String> outLines = new ArrayList<>(xArray.length);
        for (int i = 0; i < xArray.length; i++) {
            outLines.add(xArray[i] + " " + yArray[i]);
        }
        Files.write(path, outLines, options);
    }

    /**
     * 最も相関の高い位置を探す 探し方は、短い方をずらしていく 同じ長さだと探さない。
     *
     * @param base    array
     * @param compare array
     * @return compareを何ポイントずらすか 0だと先頭から
     */
    public static int findBestShift(double[] base, double[] compare) {
        double[] shorter;
        double[] longer;
        if (base.length == compare.length) return 0;
        if (base.length < compare.length) {
            shorter = base;
            longer = compare;
        } else {
            shorter = compare;
            longer = base;
        }
        int gap = longer.length - shorter.length;
        int bestShift = 0;
        double bestCorrelation = 0;
        for (int shift = 0; shift < gap + 1; shift++) {
            double[] partY = new double[shorter.length];
            System.arraycopy(longer, shift, partY, 0, shorter.length);
            RealVector partYVec = new ArrayRealVector(partY);
            RealVector shorterVec = new ArrayRealVector(shorter);
            double correlation = partYVec.dotProduct(shorterVec) / partYVec.getNorm() / shorterVec.getNorm();
            if (bestCorrelation < correlation) {
                bestCorrelation = correlation;
                bestShift = shift;
            }
            // System.out.println(correlation);
        }

        return compare.length < base.length ? bestShift : -bestShift;
    }

    public static int findBestShiftParallel(double[] base, double[] compare) {
        double[] shorter;
        double[] longer;
        if (base.length == compare.length) return 0;
        if (base.length < compare.length) {
            shorter = base;
            longer = compare;
        } else {
            shorter = compare;
            longer = base;
        }
        int gap = longer.length - shorter.length;
        int bestShift = 0;
        double bestCorrelation = 0;
//        IntStream.range(0, gap + 1).parallel()
        for (int shift = 0; shift < gap + 1; shift++) {
            double[] partY = new double[shorter.length];
            System.arraycopy(longer, shift, partY, 0, shorter.length);
            RealVector partYVec = new ArrayRealVector(partY);
            RealVector shorterVec = new ArrayRealVector(shorter);
            double correlation = partYVec.dotProduct(shorterVec) / partYVec.getNorm() / shorterVec.getNorm();
            if (bestCorrelation < correlation) {
                bestCorrelation = correlation;
                bestShift = shift;
            }
        }

        return compare.length < base.length ? bestShift : -bestShift;
    }

    /**
     * Assume the interval of x is same as that of this.
     *
     * @param trace which length must be shorter than this.
     * @return the shift value x0 in x direction for best correlation.
     * @author anselme RealVector.getNorm() returns a square-rooted norm
     */
    public double findBestShift(Trace trace) {
        int gapLength = xArray.length - trace.getLength();
        if (gapLength <= 0) throw new IllegalArgumentException("Input trace must be shorter.");
        double corMax = -1;
        double compY2 = trace.yVector.getNorm();
        double shift = 0;
        for (int i = 0; i <= gapLength; i++) {
            double cor = 0;
            double y2 = 0;
            for (int j = 0; j < trace.getLength(); j++) {
                cor += yArray[i + j] * trace.yArray[j];
                y2 += yArray[i + j] * yArray[i + j];
            }
//            cor /= y2 * compY2;
            cor /= Math.sqrt(y2) * compY2;
            if (corMax < cor) {
                shift = xArray[i] - trace.xArray[0];
                corMax = cor;
            }
        }
        return shift;
    }

    /**
     * Assume the interval of x is same as that of this.
     *
     * @param trace which length must be shorter than this.
     * @return the shift value x0 in x direction for best correlation.
     */
    public double findBestShiftParallel(Trace trace) {
        int gapLength = xArray.length - trace.getLength();
        if (gapLength <= 0) throw new IllegalArgumentException("Input trace must be shorter.");
        double compY2 = trace.yVector.getNorm();
        double[] shifts = new double[gapLength + 1];
        double[] cors = new double[gapLength + 1];
        IntStream.range(0, gapLength + 1).parallel().forEach(i -> {
            double cor = 0;
            double y2 = 0;
            for (int j = 0; j < trace.getLength(); j++) {
                cor += yArray[i + j] * trace.yArray[j];
                y2 += yArray[i + j] * yArray[i + j];
            }
            cor /= Math.sqrt(y2) * compY2;
            shifts[i] = xArray[i] - trace.xArray[0];
            cors[i] = cor;
        });
        return shifts[new ArrayRealVector(cors).getMaxIndex()];
    }

    public double findBestShiftConsiderAmplitude(Trace trace) {
        int gapLength = xArray.length - trace.getLength();
        if (gapLength <= 0) throw new IllegalArgumentException("Input trace must be shorter.");
        double corMax = -1;
        double compY2 = trace.yVector.getNorm();
        double compMax = trace.yVector.getLInfNorm();
        double shift = 0;
        for (int i = 0; i <= gapLength; i++) {
            double cor = 0;
            double y2 = 0;
            double max = Double.MIN_VALUE;
            for (int j = 0; j < trace.getLength(); j++) {
                cor += yArray[i + j] * trace.yArray[j];
                y2 += yArray[i + j] * yArray[i + j];
                if (Math.abs(yArray[j+i]) > max)
                    max = Math.abs(yArray[j+i]);
            }
            cor /= y2 * compY2;
            cor *= 2 * Math.abs(compMax - max) / (compMax + max);
            if (corMax < cor) {
                shift = xArray[i] - trace.xArray[0];
                corMax = cor;
            }
        }
        return shift;
    }

    public double findBestL1Shift(Trace trace) {
        int gapLength = xArray.length - trace.getLength();
        if (gapLength <= 0) throw new IllegalArgumentException("Input trace must be shorter.");
        double l1min = Double.MAX_VALUE;
        double compY2 = trace.yVector.getNorm();
        double shift = 0;
        for (int i = 0; i <= gapLength; i++) {
            double l1 = 0;
            double y2 = 0;
            for (int j = 0; j < trace.getLength(); j++) {
                l1 += Math.abs(yArray[i + j] - trace.yArray[j]);
//                y2 += Y[i + j] * Y[i + j];
            }
//            cor /= y2 * compY2;
            if (l1 < l1min) {
                shift = xArray[i] - trace.xArray[0];
                l1min = l1;
            }
        }
        return shift;
    }

    /**
     * f(x) &rarr; f(x-shift) Shifts "shift" in the direction of x axis. If you
     * want to change like below: <br>
     * x:(3, 4, 5) &rarr; (0, 1, 2) <br>
     * f(3) &rarr; f'(0)
     * then the value 'shift' should be -3
     *
     * @param shift (double) Value of shift
     * @return ({@link Trace}) f (x - shift), the values in y are deep copied.
     */
    public Trace shiftX(double shift) {
        return new Trace(Arrays.stream(xArray).map(d -> d + shift).toArray(), yArray);
    }

    /**
     * Replace x values with new ones. The original Trace is not changed.
     * @param xNew (double[]) The x values to replace with.
     * @return ({@link Trace}) New trace with x values replaced.
     * @since 2022/12/12
     * @author otsuru
     */
    public Trace withXAs(double[] xNew) {
        return new Trace(xNew, yArray);
    }

    /**
     * Cut out the part of this Trace in the specified range.
     * @param iStart (int) Start index of the range to be copied, inclusive
     * @param iEnd (int) End index of the range to be copied, EXCLUSIVE
     * @return ({@link Trace}) Cut out Trace
     *
     * @author otsuru
     * @since 2023/3/11
     */
    public Trace subTrace(int iStart, int iEnd) {
        return new Trace(Arrays.copyOfRange(xArray, iStart, iEnd), Arrays.copyOfRange(yArray, iStart, iEnd));
    }

    /**
     * Cut out while resampling the part of this Trace in the specified range.
     * @param iStart (int) Start index of the range to be copied, inclusive
     * @param step (int) Interval in which to resample
     * @param npts (int) Number of points that the resampled trace should include
     * @return ({@link Trace}) Cut out and resampled Trace
     *
     * @author otsuru
     * @since 2023/3/19
     */
    public Trace resampleByStep(int iStart, int step, int npts) {
        double[] sampledX = IntStream.range(0, npts).parallel().mapToDouble(i -> xArray[iStart + i * step]).toArray();
        double[] sampledY = IntStream.range(0, npts).parallel().mapToDouble(i -> yArray[iStart + i * step]).toArray();
        return new Trace(sampledX, sampledY);
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given time range.
     * The start and end points will each be the points with x values closest to xStart and xEnd.
     * The input time range does not have to be completely included in the time range of the Trace;
     * in that case, only the overlapping part will be returned.
     * The original Trace is not changed.
     * @param xStart (int) Start x of window (closest point will be chosen)
     * @param xEnd (int) End x of window (closest point will be chosen; inclusive)
     * @return ({@link Trace}) New trace that is cut out around the time range (deep copy)
     */
    public Trace cutWindow(double xStart, double xEnd) {
        int iStart = findNearestXIndex(xStart);
        int iEnd = findNearestXIndex(xEnd);
        return subTrace(iStart, iEnd + 1);
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given timewindow.
     * The start and end points will each be the points with x values closest to those of the timewindow.
     * The timewindow does not have to be completely included in the time range of the Trace;
     * in that case, only the overlapping part will be returned.
     * The original Trace is not changed.
     * @param timewindow ({@link Timewindow}) Timewindow of cut range
     * @return ({@link Trace}) New trace that is cut out around the timewindow (deep copy)
     */
    public Trace cutWindow(Timewindow timewindow) {
        return cutWindow(timewindow.getStartTime(), timewindow.getEndTime());
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given time range.
     * The start point will be the point with an x value closest to xStart.
     * The number of points will be decided by rounding (xEnd-xStart)*samplingHz, so that it is not affected by time shifts.
     * The input time range MUST be completely included in the time range of the Trace.
     * The original Trace is not changed.
     * @param xStart (int) Start x of window (closest point will be chosen)
     * @param xEnd (int) End x of window (used to decide number of points)
     * @param samplingHz (double) Sampling rate of this trace (used to decide number of points)
     * @return ({@link Trace}) New trace that is cut out around the time range (deep copy)
     *
     * @author otsuru
     * @since 2023/3/19
     */
    public Trace cutWindow(double xStart, double xEnd, double samplingHz) {
        if (xStart < getMinX() || getMaxX() < xEnd)
            throw new IllegalArgumentException("Specified time range exceeds x range.");
        int iStart = findNearestXIndex(xStart);
        // Here, npts is rounded (and not rounded down) because a point close to the edge of the time range shall be used.
        int npts = (int) Math.round((xEnd - xStart) * samplingHz) + 1;
        return subTrace(iStart, iStart + npts);
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given timewindow.
     * The start point will be the point with an x value closest to xStart.
     * The number of points will be decided by rounding (xEnd-xStart)*samplingHz+1, so that it is not affected by time shifts.
     * The input timewindow MUST be completely included in the time range of the Trace.
     * The original Trace is not changed.
     * @param timewindow ({@link Timewindow}) Timewindow of cut range
     * @param samplingHz (double) Sampling rate of this trace (used to decide number of points)
     * @return ({@link Trace}) New trace that is cut out around the timewindow (deep copy)
     *
     * @author otsuru
     * @since 2023/3/19
     */
    public Trace cutWindow(Timewindow timewindow, double samplingHz) {
        return cutWindow(timewindow.getStartTime(), timewindow.getEndTime(), samplingHz);
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given time range, and resample at a lower sampling rate.
     * The start point will be the point with an x value closest to xStart.
     * The number of points will be decided by rounding down (xEnd-xStart)*finalSamplingHz+1, so that it is not affected by time shifts.
     * The input time range MUST be completely included in the time range of the Trace.
     * The original Trace is not changed.
     * @param xStart (int) Start x of window (closest point will be chosen)
     * @param xEnd (int) End x of window (used to decide number of points)
     * @param originalSamplingHz (double) Sampling rate of this trace
     * @param finalSamplingHz (double) Sampling rate to resample the trace
     * @return ({@link Trace}) New trace that is cut out around the time range and resampled (deep copy)
     *
     * @author otsuru
     * @since 2023/3/19
     */
    public Trace resampleInWindow(double xStart, double xEnd, double originalSamplingHz, double finalSamplingHz) {
        if (xStart < getMinX() || getMaxX() < xEnd)
            throw new IllegalArgumentException("Specified time range exceeds x range.");
        int iStart = findNearestXIndex(xStart);
        // Here, npts is rounded down because a point far outside the time range should not be used.
        int npts = (int) MathAid.floor((xEnd - xStart) * finalSamplingHz) + 1;
        int step = (int) Math.round(originalSamplingHz / finalSamplingHz);
        return resampleByStep(iStart, step, npts);
    }

    /**
     * Cut out part of the Trace (with x as the time) that corresponds to the given timewindow, and resample at a lower sampling rate.
     * The start point will be the point with an x value closest to xStart.
     * The number of points will be decided by rounding (xEnd-xStart)*finalSamplingHz, so that it is not affected by time shifts.
     * The input timewindow MUST be completely included in the time range of the Trace.
     * The original Trace is not changed.
     * @param timewindow ({@link Timewindow}) Timewindow of cut range
     * @param originalSamplingHz (double) Sampling rate of this trace
     * @param finalSamplingHz (double) Sampling rate to resample the trace
     * @return ({@link Trace}) New trace that is cut out around the timewindow and resampled (deep copy)
     *
     * @author otsuru
     * @since 2023/3/19
     */
    public Trace resampleInWindow(Timewindow timewindow, double originalSamplingHz, double finalSamplingHz) {
        return resampleInWindow(timewindow.getStartTime(), timewindow.getEndTime(), originalSamplingHz, finalSamplingHz);
    }

    /**
     * Truncate the trace to a given length.
     * The original Trace is not changed.
     * @param length (int) Length to truncate the trace
     * @return ({@link Trace}) New trace that is truncated (deep copy)
     */
    public Trace truncateToLength(int length) {
        return new Trace(Arrays.copyOfRange(xArray, 0, length), Arrays.copyOfRange(yArray, 0, length));
    }

    /**
     * x in this and trace must be same. i.e. all the x elements must be same
     *
     * @param trace to be added
     * @return new Trace after the addition
     */
    public Trace add(Trace trace) {
        if (!Arrays.equals(xArray, trace.xArray)) throw new IllegalArgumentException("Trace to be added has different X axis.");
        return new Trace(xArray, yVector.add(trace.yVector).toArray());
    }

    /**
     * @param d to be multiplied
     * @return Trace which y is multiplied d
     */
    public Trace multiply(double d) {
        return new Trace(xArray, yVector.mapMultiply(d).toArray());
    }

    public double correlation(Trace trace) {
        return yVector.dotProduct(trace.yVector) / (yVector.getNorm() * trace.yVector.getNorm());
    }

    /**
     * @return the average value of y
     */
    public double average() {
        return Arrays.stream(yArray).average().getAsDouble();
    }

    /**
     * 1/n&times;&Sigma;(y<sub>i</sub> - ymean)<sup>2</sup>
     *
     * @return standard deviation of y
     */
    public double standardDeviation() {
        double average = average();
        return Arrays.stream(yArray).map(d -> d - average).map(d -> d * d).sum() / yArray.length;
    }

    public Trace removeTrend() {
        double mean = 0;
        for (double y : yArray)
            mean += y;
        mean /= yArray.length;
        return new Trace(xArray, yVector.mapSubtract(mean).toArray());
    }

    /**
     * fit X, Y in this to  y<sub>j</sub> = &sum;<sub>i</sub> (a<sub>i</sub> f<sub>i</sub> (x<sub>j</sub>))
     * by the least-square method.
     *
     * @param operators functions f<sub>i</sub>(x)
     * @return a<sub>i</sub>
     */
    public double[] fit(DoubleUnaryOperator... operators) {
        int n = operators.length;
        if (xArray.length < n + 1) throw new IllegalArgumentException("Too many operators input.");
        if (n == 0) throw new IllegalArgumentException("Invalid use");
        RealMatrix matrix = new Array2DRowRealMatrix(getLength(), n); // {a_ij} fj(xi)
        for (int i = 0; i < matrix.getRowDimension(); i++)
            for (int j = 0; j < matrix.getColumnDimension(); j++)
                matrix.setEntry(i, j, operators[j].applyAsDouble(xArray[i]));
        RealVector bb = matrix.transpose().operate(new ArrayRealVector(yArray, false));
        matrix = matrix.transpose().multiply(matrix);
        return new LUDecomposition(matrix).getSolver().solve(bb).toArray();
    }

    /**
     * Interpolates a value at <i>x = c</i>, only if the time series has no point at <i>x = c</i>,
     * assuming that nearest <i>n+1</i> points are on an <i>n</i> th order function.
     * f(x) = &Sigma; a<sub>i</sub> x<sup>i</sup>
     *
     * @param n degree of function for interpolation.
     *          If <i>n = 0</i>, then the value y at the closest point returns.
     * @param c point for the value. If the value c exists in the time series (x),
     *          no interpolation is performed.
     * @return interpolated y = f(c) = &Sigma; a<sub>i</sub> c<sup>i</sup>
     */
    public double interpolateValue(int n, double c) {
        if (xArray.length < n + 1) throw new IllegalArgumentException("n is too big");
        if (n < 0) throw new IllegalArgumentException("n is invalid");

        int[] j = nearPoints(n + 1, c);
        if (n == 0) return yArray[j[0]];

        double[] xi = Arrays.stream(j).parallel().mapToDouble(i -> xArray[i]).toArray();

        // (1, c, c**2...)
        RealVector cx = new ArrayRealVector(n + 1);

        RealMatrix matrix = new Array2DRowRealMatrix(n + 1, n + 1);
//      {a_i}
        RealVector bb = new ArrayRealVector(n + 1);
        for (int i = 0; i < n + 1; i++) {
            cx.setEntry(i, Math.pow(c, i));
            for (int k = 0; k < n + 1; k++)
                matrix.setEntry(i, k, Math.pow(xi[i], k));
            bb.setEntry(i, yArray[j[i]]);
        }
        return cx.dotProduct(new LUDecomposition(matrix).getSolver().solve(bb));
    }

    /**
     * @param n must be a natural number
     * @param x x
     * @return index of n points closest to the value x
     */
    private int[] nearPoints(int n, double x) {
        if (n <= 0 || xArray.length < n) throw new IllegalArgumentException("n is invalid");
        int[] xi = new int[n];
        double[] res = new double[n];
        Arrays.fill(res, -1);
        for (int i = 0; i < xArray.length; i++) {
            double residual = Math.abs(xArray[i] - x);
            for (int j = 0; j < n; j++)
                if (res[j] < 0 || residual <= res[j]) {
                    for (int k = n - 1; j < k; k--) {
                        res[k] = res[k - 1];
                        xi[k] = xi[k - 1];
                    }
                    res[j] = residual;
                    xi[j] = i;
                    break;
                }
        }
        return xi;
    }

    /**
     * @param target value of x to look for the nearest x value to
     * @return the index of the closest x to the target
     */
    public int findNearestXIndex(double target) {
        return nearPoints(1, target)[0];
    }

    /**
     * @param target value of x to look for the nearest x value to
     * @return the closest x to the target
     */
    public double findNearestX(double target) {
        return xArray[findNearestXIndex(target)];
    }

    /**
     * @return DEEP copy of x
     */
    public double[] getX() {
        return xArray.clone();
    }

    /**
     * @return DEEP copy of y
     */
    public double[] getY() {
        return yArray.clone();
    }

    /**
     * @return ({@link RealVector}) DEEP copy of x
     */
    public RealVector getXVector() {
        return xVector.copy();
    }

    /**
     * @return ({@link RealVector}) DEEP copy of y
     */
    public RealVector getYVector() {
        return yVector.copy();
    }

    /**
     * @param i index for <i>x</i> [0, length -1]
     * @return x[i], second at the ith point
     */
    public double getXAt(int i) {
        return xArray[i];
    }

    /**
     * @param i index (NOT time) for y [0, length -1]
     * @return y[i], amplitude at the ith point (X[i] sec)
     */
    public double getYAt(int i) {
        return yArray[i];
    }

    /**
     * @return the number of elements
     */
    public int getLength() {
        return xArray.length;
    }

    /**
     * @return minimum value of x
     */
    public double getMinX() {
        return xVector.getMinValue();
    }

    /**
     * @return maximum value of x
     */
    public double getMaxX() {
        return xVector.getMaxValue();
    }

    /**
     * @return x which gives minimum y
     */
    public double getXforMinYValue() {
        return xArray[yVector.getMinIndex()];
    }

    /**
     * @return x which gives maximum y
     */
    public double getXforMaxYValue() {
        return xArray[yVector.getMaxIndex()];
    }

    /**
     * @return minimum value of y
     */
    public double getMinY() {
        return yVector.getMinValue();
    }

    /**
     * @return maximum value of y
     */
    public double getMaxY() {
        return yVector.getMaxValue();
    }

    /**
     * @return index of maximal and minimal points. The order follows the absolute values of the points.
     */
    public int[] getIndexOfPeak() {
        return indexOfPeak.clone();
    }

    /**
     * @return index of minimal points. The order follows the absolute values of the points.
     */
    public int[] getIndexOfDownwardConvex() {
        return indexOfDownwardConvex.clone();
    }

    /**
     * @return index of maximal points. The order follows the absolute values of the points.
     */
    public int[] getIndexOfUpwardConvex() {
        return indexOfUpwardConvex.clone();
    }

    /**
     * compute n th polynomial functions for the trace
     *
     * @param n degree of polynomial
     * @return n th {@link PolynomialFunction} fitted to this
     */
    public PolynomialFunction toPolynomial(int n) {
        if (xArray.length <= n) throw new IllegalArgumentException("n is too big");
        if (n < 0) throw new IllegalArgumentException("n must be positive..(at least)");

        // (1,X,X**2,....)
        RealMatrix a = new Array2DRowRealMatrix(xArray.length, n + 1);
        for (int j = 0; j < xArray.length; j++)
            for (int i = 0; i <= n; i++)
                a.setEntry(j, i, Math.pow(xArray[j], i));
        RealMatrix at = a.transpose();
        a = at.multiply(a);
        RealVector b = at.operate(yVector);
        RealVector coef = new LUDecomposition(a).getSolver().solve(b);
        return new PolynomialFunction(coef.toArray());
    }

    public int[] robustPeakFinder() {
        int lag = 300;
        double threshold = 3.5;
        double influence = 0.5;

        double[] signals = new double[getLength()];
        double[] filteredY = new double[getLength()];
        double[] avgFilter = new double[getLength()];
        double[] stdFilter = new double[getLength()];

        for (int i = 0; i < lag; i++)
            filteredY[i] = yArray[i];
        avgFilter[lag - 1] = mean(yVector.getSubVector(0, lag).toArray());
        stdFilter[lag - 1] = std(yVector.getSubVector(0, lag).toArray());

        for (int i = lag; i < yArray.length; i++) {
//			System.out.println(y[i] + " " + Math.abs(y[i] - avgFilter[i-1]) + " " + threshold * stdFilter[i-1]);
             if (Math.abs(yArray[i] - avgFilter[i-1]) > threshold * stdFilter[i-1]) {
                if (yArray[i] > avgFilter[i-1])
                    signals[i] = 1;
                else
                  signals[i] = -1;
                filteredY[i] = influence * yArray[i] + (1-influence) * filteredY[i-1];
             }
             else {
                signals[i] = 0;
                filteredY[i] = yArray[i];
             }
             double[] cutFiltered = Arrays.copyOfRange(filteredY, i - lag, i + 1);
             avgFilter[i] = mean(cutFiltered);
             stdFilter[i] = std(cutFiltered);
        }

        List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < signals.length - 1; i++) {
            if (signals[i] == 0 && Math.abs(signals[i + 1]) == 1)
                indicesList.add(i + 1);
        }

        int[] indices = new int[indicesList.size()];
        for (int i = 0; i < indices.length; i++)
            indices[i] = indicesList.get(i);

        return indices;
    }

    private static double mean(double[] y) {
        double sum = 0;
        for (double yy : y)
            sum += yy;
        return sum / y.length;
    }

    private static double std(double[] y) {
        double std = 0;
        double mean = mean(y);
        for (double yy : y)
            std += (yy - mean) * (yy - mean);
        return Math.sqrt(std / y.length);
    }

}
