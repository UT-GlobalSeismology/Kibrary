package io.github.kensuke1984.kibrary.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

/**
 * Some calculation Utilities.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class MathAid {
    private MathAid() {}

    /**
     * The number of decimal places to decide if 0.9999... = 1.
     */
    private static final int PRECISION_DIGIT = 10;

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
     * When simply changing double to String, a ".0" is always left in integer values.
     * This method exports integer values without ".0".
     * @param value (double) Value to turn into String
     * @return (String) Simple String form of the value
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
     * Transforms a value to a String.
     * The right side is padded with "0"s.
     *
     * @param value (double) The value to turn into a String
     * @param n (int) The number of decimal places
     * @return (String) The String form of the value
     */
    public static String roundToString(double value, int n) {
        double factor = Math.pow(10, n);
        double fixedValue = Math.round(value * factor) / factor;
        int integerPart = (int) Math.floor(fixedValue);
        int decimalPlaces = (int) Math.round((fixedValue - integerPart) * factor);
        if (n == 0)
            return String.valueOf(integerPart);
        return integerPart + "." + StringUtils.leftPad(Integer.toString(decimalPlaces), n, "0");
    }

    /**
     * Changes an input double value to a string. The letter "d" is used instead of a decimal ".".
     * The value is rounded to have n decimal places.
     *
     * @param value (double) The value to be changed into String
     * @param n (int) The number of decimal places (Note that if decimal is 0, this value will be ignored)
     * @return (String) The String form of the value with "d" expressing "."
     */
    public static String roundToStringWithD(double value, int n) {
        int intValue = (int) value;
        double decimal = value - intValue;
        decimal *= Math.pow(10, n);
        int decimalInt = (int) Math.round(decimal);
        return decimalInt == 0 ? String.valueOf(intValue) : intValue + "d" + decimalInt;
    }

    /**
     * Transforms a value (double) to a padded String.
     * The left side is padded with the specified letter and the right with "0"s.
     *
     * @param value (double) The value to turn into a String
     * @param nInteger (int) The number of digits for the integer part, including the minus sign but excluding the decimal point.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point.
     * @param headLetter (String) The letter to pad at the head (i.e. " ", "0", ...)
     * @return (String) The padded String form of the value
     *
     * @author otsuru
     * @since 2021/11/26
     */
    public static String padToString(double value, int nInteger, int nDecimal, String headLetter) {
        String format;
        if (headLetter == " ")
            format = "%" + (nInteger + 1 + nDecimal) + "." + nDecimal + "f";
        else
            format = "%" + headLetter + (nInteger + 1 + nDecimal) + "." + nDecimal + "f";
        return String.format(format, value);
    }
    /**
     * Transforms a value (int) to a padded String.
     * The left side is padded with the specified letter.
     *
     * @param value (int) The value to turn into a String
     * @param nInteger (int) The number of digits for the integer part, including the minus sign.
     * @param headLetter (String) The letter to pad at the head (i.e. " ", "0", ...)
     * @return (String) The padded String form of the value
     *
     * @author otsuru
     * @since 2022/2/4
     */
    public static String padToString(int value, int nInteger, String headLetter) {
        String format;
        if (headLetter == " ")
            format = "%" + nInteger + "d";
        else
            format = "%" + headLetter + nInteger + "d";
        return String.format(format, value);
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
     * Same as Math.floor(), but consider precision, fixing 0.9999... to 1.
     * @param value (double) Input value.
     * @return (double) Rounded result.
     *
     * @author otsuru
     * @since 2023/11/8
     */
    public static double floor(double value) {
        return Math.floor(Precision.round(value, PRECISION_DIGIT));
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
