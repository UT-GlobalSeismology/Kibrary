package io.github.kensuke1984.kibrary.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.FastMath;

/**
 * Some calculation Utilities.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class MathAid {

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
     * Transforms a value to a String.
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
     * The left side is padded with spaces and the right with "0"s.
     *
     * @param value (double) The value to turn into a String
     * @param nInteger (int) The number of digits for the integer part, including the minus sign but excluding the decimal point.
     * @param nDecimal (int) The number of digits for the decimal part, excluding the decimal point.
     * @param headLetter (String) The letter to pad at the head (i.e. " ", "0", ...)
     * @return (String) The padded String form of the value
     *
     * @since 2021/11/26
     * @author otsuru
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
     * @since 2022/2/4
     * @author otsuru
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
     * Rounds value to n effective digits.
     *
     * @param value (double) The value to be rounded
     * @param n (int) The number of effective digits
     * @return (double) The rounded value which has n effective digits
     */
    public static double roundToEffective(double value, int n) {
        if (n < 1)
            throw new RuntimeException("invalid input n");

        final long log10 = (long) Math.floor(Math.log10(Math.abs(value)));
        final double power10 = FastMath.pow(10, log10 - n + 1);
        return Math.round(value / power10) * power10;
    }

    /**
     * @param v1
     * @param v2
     * @param eps
     * @return
     * @author anselme
     */
    public static boolean equalWithinEpsilon(double v1, double v2, double eps) {
        if (Math.abs(v1 - v2) > eps)
            return false;
        else
            return true;
    }

    /**
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
