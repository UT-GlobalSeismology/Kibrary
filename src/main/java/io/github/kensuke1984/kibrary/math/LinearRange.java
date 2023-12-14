package io.github.kensuke1984.kibrary.math;

/**
 * A range of values.
 * Generally, the lower limit is included and the upper limit is excluded, i.e. [lowerLimit:upperLimit).
 * (This is so that values are not double-counted in consecutive ranges.)
 * However, when the upper limit is the maximum allowed value, it is included, i.e. [lowerLimit:upperLimit].
 * (This is so that the maximum values, such as 90 in latitudes, can be included in one of the ranges.)
 *
 * @author otsuru
 * @since 2023/12/13
 */
public class LinearRange {

    private double lowerLimit;
    private double upperLimit;
    /**
     * Maximum of allowed value.
     */
    private double maximum;
    /**
     * Whether the maximum value is set.
     */
    private boolean hasMaximum = false;

    /**
     * Construct a value range. Must satisfy lowerLimit &lt; upperLimit.
     * Note that the value range excludes the upper limit.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerLimit (double) Value that is to be lower limit of range.
     * @param upperLimit (double) Value that is to be upper limit of range.
     */
    public LinearRange(String valueName, double lowerLimit, double upperLimit) {
        if (lowerLimit >= upperLimit)
            throw new IllegalArgumentException(valueName + " range [" + lowerLimit + ":" + upperLimit + ") is invalid.");
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
    }

    /**
     * Construct a value range. Must satisfy lowerLimit &lt; upperLimit.
     * Note that the value range excludes the upper limit.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerLimit (double) Value that is to be lower limit of range.
     * @param upperLimit (double) Value that is to be upper limit of range.
     * @param minimum (double) Minimum value acceptable.
     */
    public LinearRange(String valueName, double lowerLimit, double upperLimit, double minimum) {
        if (lowerLimit < minimum || lowerLimit >= upperLimit)
            throw new IllegalArgumentException(valueName + " range [" + lowerLimit + ":" + upperLimit + ") is invalid.");
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
    }

    /**
     * Construct a value range. Must satisfy lowerLimit &lt; upperLimit.
     * Note that the value range excludes the upper limit, except when it is the maximum allowed value.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerLimit (double) Value that is to be lower limit of range.
     * @param upperLimit (double) Value that is to be upper limit of range.
     * @param minimum (double) Minimum value acceptable.
     * @param maximum (double) Maximum value acceptable.
     */
    public LinearRange(String valueName, double lowerLimit, double upperLimit, double minimum, double maximum) {
        if (lowerLimit < minimum || lowerLimit >= upperLimit || maximum < upperLimit)
            throw new IllegalArgumentException(valueName + " range [" + lowerLimit + ":" + upperLimit + ") is invalid.");
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.maximum = maximum;
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
        // true in [lowerLimit:upperLimit).
        if (lowerLimit <= checkValue && checkValue < upperLimit) return true;
        // if maximum exists and upperLimit == maximum, true in [lowerLimit:upperLimit]
        if (hasMaximum && upperLimit == maximum && checkValue == upperLimit) return true;
        // otherwise
        return false;
    }
}
