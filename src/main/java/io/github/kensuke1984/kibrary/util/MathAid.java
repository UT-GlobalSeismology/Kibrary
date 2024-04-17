package io.github.kensuke1984.kibrary.util;

import java.time.LocalDate;

import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

/**
 * Some calculation utilities.
 *
 * @author otsuru
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class MathAid {
    private MathAid() {}

    /**
     * The number of decimal places to decide if 0.00...01 = 0, 0.9999... = 1, etc.
     */
    public static final int PRECISION_DECIMALS = 10;
    /**
     * The margin to decide if 0.00...01 = 0, 0.9999... = 1, etc.
     */
    public static final double PRECISION_EPSILON = Math.pow(10, -PRECISION_DECIMALS);

    /**
     * Compute AIC.
     * @param variance (double) Variance.
     * @param n (int) Number of independent data.
     * @param k (int) Degree of freedom.
     * @return (double) AIC value.
     */
    public static double computeAIC(double variance, int n, int k) {
        final double log2pi = Math.log(2 * Math.PI);
        return n * (log2pi + Math.log(variance) + 1) + 2 * k + 2;
    }

    /**
     * Compute the normalized variance of residual waveform.
     * @param d (RealVector) Residual waveform.
     * @param obs (RealVector) Observed waveform.
     * @return (double) Normalized variance.
     */
    public static double computeVariance(RealVector d, RealVector obs) {
        return d.dotProduct(d) / obs.dotProduct(obs);
    }

    /**
     * Division of two integers, but round up when not divisible.
     * @param dividend (int) a in a/b.
     * @param divisor (int) b in a/b.
     * @return (int) a/b, rounded up.
     *
     * @author otsuru
     * @since 2023/1/15
     */
    public static int divideUp(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    /**
     * Check if a value is integer.
     * @param value (double) Value to check.
     * @return (boolean) Whether the value is integer.
     *
     * @author otsuru
     * @since 2024/4/6
     */
    public static boolean isInteger(double value) {
        // compare the integer part with the value rounded to get rid of the error
        return Math.floor(value) == Precision.round(value, PRECISION_DECIMALS);
    }

    /**
     * Check if a value is a terminating decimal.
     * @param value (double) Value to check.
     * @return (boolean) Whether the value is a terminating decimal.
     *
     * @author otsuru
     * @since 2024/4/10
     */
    public static boolean isTerminatingDecimal(double value) {
        return Precision.round(value, PRECISION_DECIMALS) == Precision.round(value, PRECISION_DECIMALS + 2);
    }

    /**
     * Rounds value to n effective digits.
     *
     * @param value (double) The value to be rounded.
     * @param n (int) The number of effective digits.
     * @return (double) The rounded value which has n effective digits.
     */
    public static double roundToEffective(double value, int n) {
        if (n < 1)
            throw new IllegalArgumentException("invalid input n");

        final long log10 = (long) MathAid.floor(Math.log10(Math.abs(value)));
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
     * @param value (double) The value to turn into a String.
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

        // total number of digits: (integer digits) + (1 for decimal) + (decimal digits)
        int digits = nInteger + (nDecimal == 0 ? 0 : 1 + nDecimal);

        String format = (leftZeroPad ? "%0" : "%") + digits + "." + nDecimal + "f";
        return String.format(format, value);
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
     * Turns a positive number into an ordinal number String (i.e. 1st, 2nd, ...).
     * @param n (int) Number to get the ordinal of.
     * @return (String) Ordinal number.
     *
     * @author otsuru
     * @since 2022/4/24
     */
    public static String ordinalNumber(int n) {
        if (n < 0) throw new IllegalArgumentException("Input n must be positive.");

        // always "th" when the digit in the tens place is 1
        if (n % 100 / 10 == 1) return  n + "th";
        // otherwise, switch by digit in the ones place
        else if (n % 10 == 1) return n + "st";
        else if (n % 10 == 2) return n + "nd";
        else if (n % 10 == 3) return n + "rd";
        else return n + "th";
    }

    /**
     * Switches the wording to use based on whether a value is singular or plural.
     * For counting objects (file/files) or changing verbs (is/are).
     * @param n (int) Number.
     * @param singularCase (String) Words to append when the number is singular.
     * @param pluralCase (String) Words to append when the number is plural.
     * @return (String) Number followd by appended words.
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
     * Check if a date range is valid (i.e. first value &lt;= second value).
     * Note that the date range includes the end date.
     * @param startDate (LocalDate) Date that is supposed to be start of range.
     * @param endDate (LocalDate) Date that is supposed to be end of range.
     *
     * @author otsuru
     * @since 2023/12/4
     */
    public static void checkDateRangeValidity(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate))
            throw new IllegalArgumentException("Date range [" + startDate + ":" + endDate + "] is invalid.");
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
        return Math.floor(Precision.round(value, PRECISION_DECIMALS));
    }

    /**
     * Same as Math.ceil(), but consider precision, fixing 1.00...01 to 1.
     * @param value (double) Input value.
     * @return (double) Rounded result.
     *
     * @author otsuru
     * @since 2023/12/14
     */
    public static double ceil(double value) {
        return Math.ceil(Precision.round(value, PRECISION_DECIMALS));
    }

    /**
     * Round a value to git rid of computation error (ex. fixing 0.9999... to 1 or fixing 1.00...01 to 1).
     * @param value (double) Input value.
     * @return (double) Rounded result.
     *
     * @author otsuru
     * @since 2024/4/6
     */
    public static double roundForPrecision(double value) {
        return Precision.round(value, PRECISION_DECIMALS);
    }

}
