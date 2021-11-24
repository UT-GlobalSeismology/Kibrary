package io.github.kensuke1984.kibrary.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.FastMath;

public class MathUtils {

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
     * @param n     number of decimal places
     * @param value to fix
     * @return string for fixed value
     */
    public static String fixDecimalPlaces(int n, double value) {
        double factor = Math.pow(10, n);
        double fixedValue = Math.round(value * factor) / factor;
        int integerPart = (int) Math.floor(fixedValue);
        int decimalPlaces = (int) Math.round((fixedValue - integerPart) * factor);
        if (n == 0)
            return String.valueOf(integerPart);
        return integerPart + "." + StringUtils.leftPad(Integer.toString(decimalPlaces), n, "0");
    }

    /**
     * @param n the effective digit
     * @param d value to change
     * @return changed value which effective digit is n
     */
    public static double toSignificantFigure(int n, double d) {
        if (n < 1)
            throw new RuntimeException("invalid input n");

        final long log10 = (long) Math.floor(Math.log10(Math.abs(d)));
        final double power10 = FastMath.pow(10, log10 - n + 1);
        return Math.round(d / power10) * power10;
    }

    /**
     * Changes an input double value to a string. The value is rounded to have n
     * decimal places.
     *
     * @param n the number of decimal places (Note that if decimal is 0, this
     *          value will be ignored)
     * @param d to be changed
     * @return String with d expressing .
     */
    public static String toStringWithD(int n, double d) {
        int intValue = (int) d;
        double decimal = d - intValue;
        decimal *= Math.pow(10, n);
        int decimalInt = (int) Math.round(decimal);
        return decimalInt == 0 ? String.valueOf(intValue) : intValue + "d" + decimalInt;
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


}
