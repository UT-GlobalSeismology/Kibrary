package io.github.kensuke1984.kibrary.timewindow;

import org.apache.commons.math3.util.Precision;

/**
 * Time window instance, with starting and ending time.
 * <b>They are rounded off to {@value #DECIMALS} decimal places.</b>
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class Timewindow implements Comparable<Timewindow> {

    /**
     * The number of decimal places to round off the time values.
     */
    public static final int DECIMALS = 2;
    /**
     * The maximum number of digits of the integer part of a typical startTime or endTime.
     */
    public static final int TYPICAL_MAX_INTEGER_DIGITS = 4;
    /**
     * Margin to decide whether two timewindows have the same startTime and/or endTime.
     */
    public static final double TIME_EPSILON = 0.1;
    /**
     * Margin of startTime to decide whether two timewindows are pairs, taking into account the timeshifts due to corrections.
     */
    public static final double TIME_SHIFT_MAX = 20;

    /**
     * Start time of the window [s].
     */
    protected final double startTime;
    /**
     * End time of the window [s].
     */
    protected final double endTime;

    /**
     * Construct time window instance.
     * startTime must be less than endTime.
     * @param startTime (double) Start time of the window [s].
     * @param endTime (double) End time of the window [s].
     */
    public Timewindow(double startTime, double endTime) {
        if (endTime < startTime)
            throw new IllegalArgumentException("startTime: " + startTime + " endTime: " + endTime + " are invalid");
        this.startTime = Precision.round(startTime, DECIMALS);
        this.endTime = Precision.round(endTime, DECIMALS);
    }

    /**
     * Check if a given time window overlaps with this time window.
     * @param timeWindow ({@link Timewindow}) Time window to check.
     * @return (boolean) Whether the time windows overlap.
     */
    boolean overlaps(Timewindow timeWindow) {
        return timeWindow.startTime <= endTime && startTime <= timeWindow.endTime;
    }

    /**
     * Creates a new instance with this time window merged with the input time window.
     * If the two windows do not overlap, then the interval between them is also included.
     * @param timeWindow ({@link Timewindow}) Time window to merge.
     * @return ({@link Timewindow}) Merged time window.
     */
    Timewindow merge(Timewindow timeWindow) {
        double newStart = startTime < timeWindow.startTime ? startTime : timeWindow.startTime;
        double newEnd = timeWindow.endTime < endTime ? endTime : timeWindow.endTime;
        return new Timewindow(newStart, newEnd);
    }

    /**
     * Shift time window (in positive direction) by specified time.
     * @param shift (double) Time to shift time window [s].
     * @return ({@link Timewindow}) Shifted time window.
     */
    public Timewindow shift(double shift) {
        return new Timewindow(startTime + shift, endTime + shift);
    }

    @Override
    public int compareTo(Timewindow o) {
        int c = Double.compare(startTime, o.startTime);
        return c != 0 ? c : Double.compare(endTime, o.endTime);
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(endTime);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(startTime);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Timewindow other = (Timewindow) obj;
        return Double.doubleToLongBits(endTime) == Double.doubleToLongBits(other.endTime) &&
                Double.doubleToLongBits(startTime) == Double.doubleToLongBits(other.startTime);
    }

    public double getStartTime() {
        return startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public double getLength() {
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return startTime + " " + endTime;
    }

}
