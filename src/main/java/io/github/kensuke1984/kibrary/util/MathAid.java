package io.github.kensuke1984.kibrary.util;

import java.time.LocalDate;

import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.FastMath;

/**
 * Some calculation Utilities.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class MathAid {
    private MathAid() {}

    /**
     * @param variance variance
     * @param n        Number of independent data
     * @param k        Degree of freedom
     * @return aic
     */
    public static double computeAIC(double variance, int n, int k) {
        final double log2pi = Math.log(2 * Math.PI);
        return n * (log2pi + Math.log(variance) + 1) + 2 * k + 2;
    }

    /**
     * Compute the normalized variance of residual waveform
     * @param d (RealVector) Residual waveform
     * @param obs (RealVector) Observed waveform
     * @return (double) normalized variance
     */
    public static double computeVariance(RealVector d, RealVector obs) {
        return d.dotProduct(d) / obs.dotProduct(obs);
    }

    /**
     * Division of two integers, but round up when not divisible.
     * @param dividend (int) a in a/b
     * @param divisor (int) b in a/b
     * @return (int) a/b, rounded up
     *
     * @author otsuru
     * @since 2023/1/15
     */
    public static int divideUp(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    /**
     * Rounds value to n effective digits.
     *
     * @param value (double) The value to be rounded
     * @param n (int) The number of effective digits
     * @return (double) The rounded value which has n effective digits
     */
    public static double roundToEffective(double value, int n) {
        if (n < 1)
            throw new IllegalArgumentException("invalid input n");

        final long log10 = (long) Math.floor(Math.log10(Math.abs(value)));
        final double power10 = FastMath.pow(10, log10 - n + 1);
        return Math.round(value / power10) * power10;
    }

    /**
     * Transforms a value (double) to a String.
     * This method exports integer values without ".0" (which is always left in integer values when simply changing double to String).
     *
     * @param value (int) The value to turn into a String.
     * @return (String) Simple String form of the value.
     *
     * @author otsuru
     * @since 2023/1/15
     */
    public static String simplestString(double value) {
        if (Math.floor(value) == value) {
            return String.valueOf((int) value);
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Transforms a value (double) to a String.
     * This method exports integer values without ".0" (which is always left in integer values when simply changing double to String).
     * The decimal point can be changed to a specified letter.
     *
     * @param value (int) The value to turn into a String.
     * @param decimalLetter (String) The letter to use instead of the decimal point.
     * @return (String) Simple String form of the value.
     */
    public static String simplestString(double value, String decimalLetter) {
        return simplestString(value).replace(".", decimalLetter);
    }

    /**
     * Transforms a value (int) to a padded String.
     * The left side is padded with either " " or "0".
     * If the integer part has more digits than desired, the whole integer is returned.
     *
     * @param value (int) The value to turn into a String.
     * @param nInteger (int) The number of digits for the integer part, including the minus sign. Must be positive.
     * @param leftZeroPad (boolean) Whether to pad the left side with "0". Otherwise, " ".
     * @return (String) The padded String form of the value.
     *
     * @author otsuru
     * @since 2022/2/4
     */
    public static String padToString(int value, int nInteger, boolean leftZeroPad) {
        if (nInteger <= 0) throw new IllegalArgumentException("nInteger must be positive.");

        String format = (leftZeroPad ? "%0" : "%") + nInteger + "d";
        return String.format(format, value);
    }

    /**
     * Transforms a value (double) to a padded String.
     * The left side is padded with either " " or "0", and the right with "0".
     * Even if the integer part has more digits than desired, the whole integer is used.
     * When the decimal part has more digits than desired, it will be rounded.
     *
     * @param value (double) The value to turn into a String.
     * @param nInteger (int) The number of digits for the integer part, including the minus sign but excluding the decimal point. Must be positive.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point. Must be non-negative.
     * @param leftZeroPad (boolean) Whether to pad the left side with "0". Otherwise, " ".
     * @return (String) The padded String form of the value.
     *
     * @author otsuru
     * @since 2021/11/26
     */
    public static String padToString(double value, int nInteger, int nDecimal, boolean leftZeroPad) {
        if (nInteger <= 0) throw new IllegalArgumentException("nInteger must be positive.");
        if (nDecimal < 0) throw new IllegalArgumentException("nDecimal must be non-negative.");

        if (nDecimal == 0) {
            int intValue = (int) Math.round(value);
            return padToString(intValue, nInteger, leftZeroPad);

        } else {
            String format = (leftZeroPad ? "%0" : "%") + (nInteger + 1 + nDecimal) + "." + nDecimal + "f";
            return String.format(format, value);
        }
    }

    /**
     * Transforms a value (double) to a padded String.
     * The left side is padded with either " " or "0", and the right with "0".
     * Even if the integer part has more digits than desired, the whole integer is used.
     * When the decimal part has more digits than desired, it will be rounded.
     * The decimal point can be changed to a specified letter.
     *
     * @param value (double) The value to turn into a String.
     * @param nInteger (int) The number of digits for the integer part, including the minus sign but excluding the decimal point. Must be positive.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point. Must be non-negative.
     * @param leftZeroPad (boolean) Whether to pad the left side with "0". Otherwise, " ".
     * @param decimalLetter (String) The letter to use instead of the decimal point.
     * @return (String) The padded String form of the value.
     *
     * @author otsuru
     * @since 2023/6/1
     */
    public static String padToString(double value, int nInteger, int nDecimal, boolean leftZeroPad, String decimalLetter) {
        return padToString(value, nInteger, nDecimal, leftZeroPad).replace(".", decimalLetter);
    }

    /**
     * Transforms a value (double) to a String.
     * The right side is padded with "0"s.
     * When the decimal part has more digits than desired, it will be rounded.
     *
     * @param value (double) The value to turn into a String.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point. Must be non-negative.
     * @return (String) The String form of the value.
     */
    public static String roundToString(double value, int nDecimal) {
        return padToString(value, 1, nDecimal, false);
    }

    /**
     * Transforms a value (double) to a String.
     * The right side is padded with "0"s.
     * When the decimal part has more digits than desired, it will be rounded.
     * The decimal point can be changed to a specified letter.
     *
     * @param value (double) The value to turn into a String.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point. Must be non-negative.
     * @param decimalLetter (String) The letter to use instead of the decimal point.
     * @return (String) The String form of the value.
     *
     * @author otsuru
     * @since 2023/6/1
     */
    public static String roundToString(double value, int nDecimal, String decimalLetter) {
        return padToString(value, 1, nDecimal, false, decimalLetter);
    }

    /**
     * Turns a positive number into an ordinal number String (i.e. 1st, 2nd, ...)
     * @param n (int) Number to get the ordinal of
     * @return (String)
     *
     * @author otsuru
     * @since 2022/4/24
     */
    public static String ordinalNumber(int n) {
        if (n < 0) throw new IllegalArgumentException("Input n must be positive");

        // always "th" when the digit in the tens place is 1
        if (n % 100 / 10 == 1) return  n + "th";
        else if (n % 10 == 1) return n + "st";
        else if (n % 10 == 2) return n + "nd";
        else if (n % 10 == 3) return n + "rd";
        else return n + "th";
    }

    /**
     * Switches the wording to use based on whether a value is singular or plural.
     * For counting objects (file/files) or changing verbs (is/are).
     * @param n (int) Number
     * @param singularCase (String) Words to append when the number is singular
     * @param pluralCase (String) Words to append when the number is plural
     * @return (String) Number followd by appended words
     *
     * @author otsuru
     * @since 2022/4/24
     */
    public static String switchSingularPlural(int n, String singularCase, String pluralCase) {
        if (n == 1) {
            return n + " " + singularCase;
        } else {
            return n + " " + pluralCase;
        }
    }

    /**
     * Check if a value range is valid (i.e. first value &lt;= second value).
     * @param valueName (String) Name of variable that is being checked.
     * @param lowerValue (double) Value that is supposed to be lower limit of range.
     * @param upperValue (double) Value that is supposed to be upper limit of range.
     *
     * @author otsuru
     * @since 2023/12/4
     */
    public static void checkRangeValidity(String valueName, double lowerValue, double upperValue) {
        if (lowerValue > upperValue)
            throw new IllegalArgumentException(valueName + " range [" + lowerValue + ", " + upperValue + "] is invalid.");
    }
    /**
     * Check if a date range is valid (i.e. first value &lt;= second value).
     * @param startDate (LocalDate) Value that is supposed to be lower limit of range.
     * @param endDate (LocalDate) Value that is supposed to be upper limit of range.
     *
     * @author otsuru
     * @since 2023/12/4
     */
    public static void checkDateRangeValidity(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate))
            throw new IllegalArgumentException("Date range [" + startDate + ", " + endDate + "] is invalid.");
    }

    /**
     * Check if an angle is within a specified range.
     * @param angle [0:360)
     * @param lower [-360:upper)
     * @param upper (lower:360]
     * @return (boolean) true if "angle" is within the range set by "lower" and "upper"
     */
    public static boolean checkAngleRange(double angle, double lower, double upper) {
        if (angle < 0 || 360 <= angle || lower < -360 || upper < lower || 360 < upper) {
            throw new IllegalArgumentException("The input angles " + angle + "," + lower + "," + upper + " are invalid.");
        }

        // In the following, the third part is for the case of angle==0
        if ((lower <= angle && angle <= upper) || (lower+360 <= angle && angle <= upper+360) || (lower-360 <= angle && angle <= upper-360)) {
            return true;
        } else {
            return false;
        }
    }

}
