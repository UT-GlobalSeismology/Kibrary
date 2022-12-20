package io.github.kensuke1984.kibrary.source;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

/**
 * Moment Tensor<br>
 * Global CMT -
 * 1:M<sub>rr</sub> , 2:M<sub>tt</sub> , 3:M<sub>pp</sub> , 4:M<sub>rt</sub> , 5:M<sub>rp</sub> , 6: M<sub>tp</sub> <br>
 * DSM -
 * 1:M<sub>rr</sub> , 2:M<sub>rt</sub> , 3:M<sub>rp</sub> , 4:M<sub>tt</sub> , 5:M<sub>tp</sub> , 6:M<sub>pp</sub>
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author Kensuke Konishi
 * @since version 0.0.6.3
 */
public class MomentTensor {

    /**
     * the value used for exponential in DSM input files
     */
    private static final int DSM_EXP = 25;

    private final double mrrCoefficient;
    private final double mttCoefficient;
    private final double mppCoefficient;
    private final double mrtCoefficient;
    private final double mrpCoefficient;
    private final double mtpCoefficient;
    private final int mtExponent;
    private final double mw;

    /**
     * The order is same as Global CMT project.
     *
     * @param mrrCoefficient (double) Mrr
     * @param mttCoefficient (double) Mtt
     * @param mppCoefficient (double) Mpp
     * @param mrtCoefficient (double) Mrt
     * @param mrpCoefficient (double) Mrp
     * @param mtpCoefficient (double) Mtp
     * @param mtExponent (int) exponential number for the preceding values
     * @param mw    Mw
     */
    public MomentTensor(double mrrCoefficient, double mttCoefficient, double mppCoefficient,
            double mrtCoefficient, double mrpCoefficient, double mtpCoefficient, int mtExponent, double mw) {
        this.mrrCoefficient = mrrCoefficient;
        this.mttCoefficient = mttCoefficient;
        this.mppCoefficient = mppCoefficient;
        this.mrtCoefficient = mrtCoefficient;
        this.mrpCoefficient = mrpCoefficient;
        this.mtpCoefficient = mtpCoefficient;
        this.mtExponent = mtExponent;
        this.mw = mw;
    }

    /**
     * Convert value in dyne*cm to N*m.
     * 10<sup>5</sup> dyne = N, 100 cm = 1 m
     * @param dynecm (double) value in dyne*cm
     * @return (double) value converted to N*m
     */
    public static double convertToNm(double dynecm) {
        return dynecm / 100000 / 100;
    }

    /**
     * Convert scalar moment to moment magnitude.
     * Note that the scalar moment must be given in N*m (not dyne*cm).
     *
     * @param m0 (double) scalar moment M<sub>0</sub> [N*m]
     * @return (double) moment magnitude M<sub>w</sub> [no unit]
     */
    public static double toMw(double m0) {
        double mw = (FastMath.log10(m0) - 9.1) / 1.5;
        return Precision.round(mw, 1);
    }

    /**
     * Convert moment magnitude to scalar moment.
     * @param mw (double) moment magnitude M<sub>w</sub> [no unit]
     * @return (double) scalar moment M<sub>0</sub> [N*m]
     */
    public static final double toM0(double mw) {
        return FastMath.pow(10, 1.5 * mw + 9.1);
    }

    /**
     * Coefficient for Mrr value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mrr coefficient
     */
    public double getMrrCoefficient() {
        return mrrCoefficient;
    }

    /**
     * Coefficient for Mtt value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mtt coefficient
     */
    public double getMttCoefficient() {
        return mttCoefficient;
    }

    /**
     * Coefficient for Mpp value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mpp coefficient
     */
    public double getMppCoefficient() {
        return mppCoefficient;
    }

    /**
     * Coefficient for Mrt value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mrt coefficient
     */
    public double getMrtCoefficient() {
        return mrtCoefficient;
    }

    /**
     * Coefficient for Mrp value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mrp coefficient
     */
    public double getMrpCoefficient() {
        return mrpCoefficient;
    }

    /**
     * Coefficient for Mtp value. This must be multiplied by the exponent part (10^exponent) to get the value in dyne*cm.
     * @return (double) Mtp coefficient
     */
    public double getMtpCoefficient() {
        return mtpCoefficient;
    }

    /**
     * Get the value used for exponential in global CMT expression.
     * By multiplying 10^(this value) to the coefficients, the parameters will be expressed in dyne*cm.
     * @return (int) the value used for exponential in global CMT expression
     */
    public int getMtExponent() {
        return mtExponent;
    }

    /**
     * @return (double) Moment magnitude [no unit]
     */
    public double getMw() {
        return mw;
    }

    /**
     * DSM情報ファイルに書く形式のモーメントテンソルを返す
     *
     * @return moment tensor in the order used in DSM
     */
    public double[] getDSMmt() {
        double[] dsmMT = new double[6];
        double factor = FastMath.pow(10, mtExponent - DSM_EXP);
        dsmMT[0] = Precision.round(mrrCoefficient * factor, 5);
        dsmMT[1] = Precision.round(mrtCoefficient * factor, 5);
        dsmMT[2] = Precision.round(mrpCoefficient * factor, 5);
        dsmMT[3] = Precision.round(mttCoefficient * factor, 5);
        dsmMT[4] = Precision.round(mtpCoefficient * factor, 5);
        dsmMT[5] = Precision.round(mppCoefficient * factor, 5);
        return dsmMT;
    }

    @Override
    public String toString() {
        return "Moment Tensor (in Global CMT project order): Expo=" + mtExponent
                + " " + mrrCoefficient + " " + mttCoefficient + " " + mppCoefficient
                + " " + mrtCoefficient + " " + mrpCoefficient + " " + mtpCoefficient;
    }

}
