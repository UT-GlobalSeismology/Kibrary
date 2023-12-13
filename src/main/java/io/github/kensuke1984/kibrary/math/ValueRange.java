package io.github.kensuke1984.kibrary.math;

/**
 * A range of values.
 * Generally, the lower limit is included and the upper limit is excluded, i.e. [lowerValue:upperValue).
 * (This is so that values are not double-counted in consecutive ranges.)
 * However, when the upper limit is the maximum allowed value, it is included, i.e. [lowerValue:upperValue].
 * (This is so that the maximum values, such as 90 in latitudes, can be included in one of the ranges.)
 *
 * @author otsuru
 * @since 2023/12/13
 */
public class ValueRange {

    double lowerValue;
    double upperValue;
    /**
     * Maximum of allowed value.
     */
    double maxValue;
    /**
     * Whether the maximum value is set.
     */
    boolean hasMaximum = false;

    /**
     * Construct a value range. Must satisfy lowerValue &lt; upperValue.
     * Note that the value range excludes the upper limit.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerValue (double) Value that is to be lower limit of range.
     * @param upperValue (double) Value that is to be upper limit of range.
     */
    public ValueRange(String valueName, double lowerValue, double upperValue) {
        if (lowerValue >= upperValue)
            throw new IllegalArgumentException(valueName + " range [" + lowerValue + ":" + upperValue + ") is invalid.");
        this.lowerValue = lowerValue;
        this.upperValue = upperValue;
    }

    /**
     * Construct a value range. Must satisfy lowerValue &lt; upperValue.
     * Note that the value range excludes the upper limit.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerValue (double) Value that is to be lower limit of range.
     * @param upperValue (double) Value that is to be upper limit of range.
     * @param minValue (double) Minimum value acceptable.
     */
    public ValueRange(String valueName, double lowerValue, double upperValue, double minValue) {
        if (lowerValue < minValue || lowerValue >= upperValue)
            throw new IllegalArgumentException(valueName + " range [" + lowerValue + ":" + upperValue + ") is invalid.");
        this.lowerValue = lowerValue;
        this.upperValue = upperValue;
    }

    /**
     * Construct a value range. Must satisfy lowerValue &lt; upperValue.
     * Note that the value range excludes the upper limit, except when upperValue is the maximum allowed value.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerValue (double) Value that is to be lower limit of range.
     * @param upperValue (double) Value that is to be upper limit of range.
     * @param minValue (double) Minimum value acceptable.
     * @param maxValue (double) Maximum value acceptable.
     */
    public ValueRange(String valueName, double lowerValue, double upperValue, double minValue, double maxValue) {
        if (lowerValue < minValue || lowerValue >= upperValue || maxValue < upperValue)
            throw new IllegalArgumentException(valueName + " range [" + lowerValue + ":" + upperValue + ") is invalid.");
        this.lowerValue = lowerValue;
        this.upperValue = upperValue;
        this.maxValue = maxValue;
        this.hasMaximum = true;
    }

    /**
     * Check if a value is within the specified range.
     * Lower limit is included; upper limit is excluded.
     * However, if the upper limit is the maximum value allowed, upper limit is included.
     * @param checkValue (double) Value to check.
     * @return (boolean) Whether the value to check is within the specified range.
     */
    public boolean check(double checkValue) {
        // true in [lowerValue:upperValue).
        if (lowerValue <= checkValue && checkValue < upperValue) return true;
        // if maximum exists and upperValue == maximum, true in [lowerValue:upperValue]
        if (hasMaximum && upperValue == maxValue && checkValue == upperValue) return true;
        // otherwise
        return false;
    }
}
