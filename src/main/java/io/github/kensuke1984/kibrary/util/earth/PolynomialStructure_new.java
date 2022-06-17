package io.github.kensuke1984.kibrary.util.earth;

import java.util.Arrays;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

/**
 * 1D structure of the Earth.
 * To be used as input for softwares of <i>Direct Solution Method</i> (DSM)<br>
 * <p>
 * Every depth is written in <b>radius</b>.<br>
 * <p>
 * This class is <b>IMMUTABLE</b> <br>.
 * Caution that member arrays are mutable, so they must be cloned in constructors and getters.
 * ({@link PolynomialFunction} is supposedly immutable, so they do not have to be cloned.)
 * <p>
 * When you try to get values on radius of boundaries, you will get one in the
 * isShallower layer, i.e., the layer which has the radius as rmin.
 *
 * @author Kensuke Konishi, anselme
 * @since version 0.2.10
 * @version 2022/2/10 moved from dsmsetup
 * @version 2022/6/15 recreated
 */
public final class PolynomialStructure_new {

    /**
     * true if default structure. False if user-defined structure
     */
    private final boolean isDefault;
    /**
     * the number of layers
     */
    private final int nzone;
    /**
     * Number of zones of cores.
     */
    private final int coreZone = 2; // TODO
    private final double[] rmin;
    private final double[] rmax;
    private final PolynomialFunction[] rho;
    private final PolynomialFunction[] vpv;
    private final PolynomialFunction[] vph;
    private final PolynomialFunction[] vsv;
    private final PolynomialFunction[] vsh;
    private final PolynomialFunction[] eta;
    private final double[] qMu;
    private final double[] qKappa;

    /**
     * Constructor for creating user-defined structures.
     * @param nzone
     * @param rmin
     * @param rmax
     * @param rho
     * @param vpv
     * @param vph
     * @param vsv
     * @param vsh
     * @param eta
     * @param qMu
     * @param qKappa
     */
    public PolynomialStructure_new(int nzone, double[] rmin, double[] rmax, PolynomialFunction[] rho,
            PolynomialFunction[] vpv, PolynomialFunction[] vph, PolynomialFunction[] vsv, PolynomialFunction[] vsh,
            PolynomialFunction[] eta, double[] qMu, double[] qKappa) {
        this(nzone, rmin, rmax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa, false);
    }

    /**
     * Constructor for creating default structures in {@link PolynomialStructureData}.
     * @param nzone
     * @param rmin
     * @param rmax
     * @param rho
     * @param vpv
     * @param vph
     * @param vsv
     * @param vsh
     * @param eta
     * @param qMu
     * @param qKappa
     * @param isDefault
     */
    PolynomialStructure_new(int nzone, double[] rmin, double[] rmax, PolynomialFunction[] rho,
            PolynomialFunction[] vpv, PolynomialFunction[] vph, PolynomialFunction[] vsv, PolynomialFunction[] vsh,
            PolynomialFunction[] eta, double[] qMu, double[] qKappa, boolean isDefault) {
        this.nzone = nzone;
        this.rmin = rmin.clone();
        this.rmax = rmax.clone();
        this.rho = rho.clone();
        this.vpv = vpv.clone();
        this.vph = vph.clone();
        this.vsv = vsv.clone();
        this.vsh = vsh.clone();
        this.eta = eta.clone();
        this.qMu = qMu.clone();
        this.qKappa = qKappa.clone();
        this.isDefault = isDefault;
    }

    /**
     * Constructor for creating default structures in {@link PolynomialStructureData}.
     * @param nzone
     * @param rmin
     * @param rmax
     * @param rho
     * @param vpv
     * @param vph
     * @param vsv
     * @param vsh
     * @param eta
     * @param qMu
     * @param qKappa
     * @param isDefault
     */
    PolynomialStructure_new(int nzone, double[] rmin, double[] rmax, double[][] rho, double[][] vpv,
            double[][] vph, double[][] vsv, double[][] vsh, double[][] eta, double[] qMu,
            double[] qKappa, boolean isDefault) {
        this.nzone = nzone;
        this.rmin = rmin.clone();
        this.rmax = rmax.clone();

        this.rho = new PolynomialFunction[nzone];
        this.vpv = new PolynomialFunction[nzone];
        this.vph = new PolynomialFunction[nzone];
        this.vsv = new PolynomialFunction[nzone];
        this.vsh = new PolynomialFunction[nzone];
        this.eta = new PolynomialFunction[nzone];
        for (int i = 0; i < nzone; i++) {
            this.rho[i] = new PolynomialFunction(rho[i]);
            this.vpv[i] = new PolynomialFunction(vpv[i]);
            this.vph[i] = new PolynomialFunction(vph[i]);
            this.vsv[i] = new PolynomialFunction(vsv[i]);
            this.vsh[i] = new PolynomialFunction(vsh[i]);
            this.eta[i] = new PolynomialFunction(eta[i]);
        }

        this.qMu = qMu.clone();
        this.qKappa = qKappa.clone();
        this.isDefault = isDefault;

    }









    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + coreZone;
        result = prime * result + Arrays.hashCode(eta);
        result = prime * result + nzone;
        result = prime * result + Arrays.hashCode(qKappa);
        result = prime * result + Arrays.hashCode(qMu);
        result = prime * result + Arrays.hashCode(rho);
        result = prime * result + Arrays.hashCode(rmax);
        result = prime * result + Arrays.hashCode(rmin);
        result = prime * result + Arrays.hashCode(vph);
        result = prime * result + Arrays.hashCode(vpv);
        result = prime * result + Arrays.hashCode(vsh);
        result = prime * result + Arrays.hashCode(vsv);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PolynomialStructure_new other = (PolynomialStructure_new) obj;
        if (coreZone != other.coreZone)
            return false;
        if (!Arrays.equals(eta, other.eta))
            return false;
        if (nzone != other.nzone)
            return false;
        if (!Arrays.equals(qKappa, other.qKappa))
            return false;
        if (!Arrays.equals(qMu, other.qMu))
            return false;
        if (!Arrays.equals(rho, other.rho))
            return false;
        if (!Arrays.equals(rmax, other.rmax))
            return false;
        if (!Arrays.equals(rmin, other.rmin))
            return false;
        if (!Arrays.equals(vph, other.vph))
            return false;
        if (!Arrays.equals(vpv, other.vpv))
            return false;
        if (!Arrays.equals(vsh, other.vsh))
            return false;
        if (!Arrays.equals(vsv, other.vsv))
            return false;
        return true;
    }

    public int getNzone() {
        return nzone;
    }

    public int getCoreZone() {
        return coreZone;
    }

    public double[] getRmin() {
        return rmin.clone();
    }

    public double[] getRmax() {
        return rmax.clone();
    }

    public PolynomialFunction[] getRho() {
        return rho.clone();
    }

    public PolynomialFunction[] getVpv() {
        return vpv.clone();
    }

    public PolynomialFunction[] getVph() {
        return vph.clone();
    }

    public PolynomialFunction[] getVsv() {
        return vsv.clone();
    }

    public PolynomialFunction[] getVsh() {
        return vsh.clone();
    }

    public PolynomialFunction[] getEta() {
        return eta.clone();
    }

    public double[] getqMu() {
        return qMu.clone();
    }

    public double[] getqKappa() {
        return qKappa.clone();
    }

    /**
     * @return true if default structure (already implemented), false if user-defined structure
     */
    public boolean isDefault() {
        return isDefault;
    }

}
