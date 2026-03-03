package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/**
 * Calculation of U<sub>j</sub> &rho;</sub> &eta;<sub>ji</sub> in
 * Geller &amp; Hara (1993)
 *
 * @author Rei Sato
 * @since 2022/01/10
 */
public class TensorCalculationURhoE {

    /**
     * Uj = u[j][(np)]
     */
    private Complex[][] u = new Complex[3][];

    /**
     * i に対して都度計算するので iは引数に取らない Eta ji = eta （[i]）[j][(np)]
     */
    private Complex[][] eta = new Complex[3][];

    private SPCBody fp;
    private SPCBody bp;

    private int np;

    /**
     * bpのテンソル座標軸をfpの軸に合わせるための角度
     */
    private double angle;

    private double tlen;

    /**
     * input cに対するテンソル積の和を計算する
     *
     * @param fp     forward propagation spc file
     * @param bp     back propagation spc file
     * @param factor どう重み付けするか
     * @param angle
     */
    TensorCalculationURhoE(SPCBody fp, SPCBody bp, double angle, double tlen) {
        this.fp = fp;
        this.bp = bp;
        np = fp.getNp();
        this.angle = angle;
        this.tlen = tlen;
    }

    /**
     * Uj Rho Ejiのi成分の計算
     *
     * @param i (0: Z 1:R 2:T)
     * @return {@link Complex}[NP] i成分を返す
     */
    public Complex[] calc(int i) {
        Complex[] partial = new Complex[np + 1];
        Arrays.fill(partial, Complex.ZERO);

        for (int j = 0; j < 3; j++) {
            SPCTensorComponent irs = SPCTensorComponent.valueOf9Component(i + 1, j + 1);
            eta[j] = bp.getSpcElement(irs).getValueInFrequencyDomain();
        }

        eta = rotateEta(eta);

        for (int j = 0; j < 3; j++) {
            u[j] = fp.getSpcElement(j).getValueInFrequencyDomain();
            addPartial(partial, calcCrossCorrelation(u[j], eta[j]));
        }
        return partial;
    }

    /**
     * back propagateのローカル座標をforwardのものにあわせる
     *
     * @param eta eta[3][NP+1]
     * @param r
     * @return ETAji（back propagation） をテンソルのZ軸中心に {@link #angle} 回す
     */
    private Complex[][] rotateEta(Complex[][] eta) {

        // double angle = this.angle+Math.toRadians(195);
        /*
         * テンソル（eta）をangleだけ回転させ新しいテンソル(reta)を返す。
         *
         * reta = forwardMatrix eta backmatrix
         *
         * 中間値として neweta = forwardmatrix eta
         *
         * reta = neweta backmatrix
         */
        // angle= 0;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);

        // 回転行列 前から
        double[][] forwardMatrix = new double[][] { { 1, 0, 0 }, { 0, cosine, sine }, { 0, -sine, cosine } };

        // 回転行列 後ろから
        double[][] backMatrix = new double[][] { { 1, 0, 0 }, { 0, cosine, -sine }, { 0, sine, cosine } };

        Complex[][] newETA = new Complex[3][np + 1];

        for (int ip = 0; ip < np + 1; ip++)
            for (int j = 0; j < 3; j++) {
                    newETA[j][ip] = Complex.ZERO;
                    for (int k = 0; k < 3; k++)
                        newETA[j][ip] = newETA[j][ip].add(eta[k][ip].multiply(forwardMatrix[j][k]));
            }

        Complex[][] rETA = new Complex[3][np + 1];

        for (int ip = 0; ip < np + 1; ip++)
            for (int j = 0; j < 3; j++) {
                    rETA[j][ip] = Complex.ZERO;
                    for (int k = 0; k < 3; k++)
                        rETA[j][ip] = rETA[j][ip].add(newETA[k][ip].multiply(backMatrix[k][j]));
                }

        return newETA;
    }

    /**
     * uとEtaの計算をする（積） cross correlation
     *
     * @param u
     * @param eta
     * @return c[i] = u[i]* eta[i]
     */
    private Complex[] calcCrossCorrelation(Complex[] u, Complex[] eta) {
        Complex[] c = new Complex[np + 1];
        for (int i = 0; i < np +1; i++)
            c[i] = u[i].multiply(eta[i]).multiply(FastMath.pow(2.0 * Math.PI * i / tlen, 2));
//        Arrays.setAll(c, i -> u[i].multiply(eta[i]));
//        Arrays.parallelSetAll(c, i -> u[i].multiply(eta[i]));
        return c;
    }

    /**
     *
     * add URhoE to partial
     *
     * @param partial
     * @param ure
     */
    private void addPartial(Complex[] partial, Complex[] ure) {
        for (int i = 0; i < np + 1; i++)
            partial[i] = partial[i].add(ure[i]);
        return;
    }

}
