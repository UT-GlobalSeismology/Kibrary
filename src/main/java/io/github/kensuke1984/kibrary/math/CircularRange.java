package io.github.kensuke1984.kibrary.math;

/**
 * A range of angles on a circle [deg].
 * Comparisons are done after normalizing all angle values into range [0:360).
 * The lower limit is included and the upper limit is excluded, i.e. [lowerLimit:upperLimit).
 *
 * @author otsuru
 * @since 2023/12/13
 */
public class CircularRange {

    /**
     * Lower limit, converted into range [0:360).
     */
    double lowerLimit;
    /**
     * Upper limit, converted into range [0:360).
     */
    double upperLimit;

    /**
     * Construct a value range.
     * Note that the value range excludes the upper limit.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerLimit (double) Value that is to be lower limit of range [deg].
     * @param upperLimit (double) Value that is to be upper limit of range [deg].
     */
    public CircularRange(String valueName, double lowerLimit, double upperLimit) {
        // convert all angles to range [0:360)
        this.lowerLimit = lowerLimit - Math.floor(lowerLimit / 360) * 360;
        this.upperLimit = upperLimit - Math.floor(upperLimit / 360) * 360;
    }

    /**
     * Construct a value range.
     * Note that the value range excludes the upper limit.
     * A range of acceptable values can be set for the sake of safety.
     * @param valueName (String) Name of variable that is being checked. First letter should be capitalized.
     * @param lowerLimit (double) Value that is to be lower limit of range [deg].
     * @param upperLimit (double) Value that is to be upper limit of range [deg].
     * @param minimum (double) Minimum value acceptable [deg].
     * @param maximum (double) Maximum value acceptable [deg].
     */
    public CircularRange(String valueName, double lowerLimit, double upperLimit, double minimum, double maximum) {
        if (lowerLimit < minimum || maximum < lowerLimit || upperLimit < minimum || maximum < upperLimit)
            throw new IllegalArgumentException(valueName + " range [" + lowerLimit + ":" + upperLimit + ") is invalid.");
        // convert all angles to range [0:360)
        this.lowerLimit = lowerLimit - Math.floor(lowerLimit / 360) * 360;
        this.upperLimit = upperLimit - Math.floor(upperLimit / 360) * 360;
    }

    /**
     * Check if an angle is within the specified range.
     * Comparison is done after normalizing all angle values into range [0:360).
     * Lower limit is included; upper limit is excluded.
     * @param checkValue (double) Angle to check [deg].
     * @return (boolean) Whether the angle to check is within the specified range.
     */
    public boolean check(double checkValue) {
        // convert angle to range [0:360)
        double convertedValue = checkValue - Math.floor(checkValue / 360) * 360;

        if (upperLimit <= lowerLimit) {
            // Accept values in [0:upperValue),[lowerValue:360].
            // When lowerValue == upperValue (this happens for [-180:180), [0 & 360), etc.), everything is accepted.
            if (convertedValue < upperLimit || lowerLimit <= convertedValue) return true;
            else return false;
        } else {
            // Accept values in [lowerValue:upperValue).
            if (convertedValue < lowerLimit || upperLimit <= convertedValue) return false;
            else return true;
        }
    }

}
