package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.elastic.ElasticMedium;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * 1D structure of a planet.
 * To be used as input for softwares of <i>Direct Solution Method</i> (DSM)<br>
 * <p>
 * Structures shall be defined as combinations of polynomial functions or constants, each defined within a layer.
 * The arrays containing structure information must be ordered from radius=0 to radius=planetRadius.
 * <p>
 * In each layer, 6 variables (RHO, Vpv, Vph, Vsv, Vsh, ETA) are defined using a degree-4 polynomial function,
 * and 2 variables (Qkappa, Qmu) are defined as constants.
 * <p>
 * Every depth is written in <b>radius</b>.
 * The radii used as the variable x in polynomial functions should be normalized to the planet radius.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * Caution that member arrays are mutable, so they must be cloned in constructors and getters.
 * ({@link PolynomialFunction} is supposedly immutable, so they do not have to be cloned.)
 * <p>
 * When you try to get values on the radius of boundaries, you will get one in the
 * isShallower layer, i.e., the layer which has the radius as rmin.
 *
 * @author Kensuke Konishi, anselme
 * @since a long time ago
 * @version 2022/2/10 moved from package dsmsetup into util.earth
 * @version 2022/6/15 recreated this file to make this class actually immutable
 */
public final class PolynomialStructure {

    /**
     * The margin to decide whether two radii are the same value
     */
    private static final double R_EPSILON = 1e-6;

    /**
     * true if default structure, false if user-defined structure
     */
    private final boolean isDefault;
    /**
     * the number of layers
     */
    private final int nZone;
    /**
     * Number of zones of cores.
     */
    private final int nCoreZone;
    private final double[] rMin;
    private final double[] rMax;
    private final PolynomialFunction[] rho;
    private final PolynomialFunction[] vpv;
    private final PolynomialFunction[] vph;
    private final PolynomialFunction[] vsv;
    private final PolynomialFunction[] vsh;
    private final PolynomialFunction[] eta;
    private final double[] qMu;
    private final double[] qKappa;

    /**
     * Set up a {@link PolynomialStructure} instance by either reading a {@link PolynomialStructureFile} or from a structure name.
     * @param structurePath (Path) A {@link PolynomialStructureFile}. Should be null when 'structureName' is to be referenced.
     * @param structureName (String) A structure name. May be null when 'structurePath' is to be referenced.
     * @return ({@link PolynomialStructure}) Set structure.
     * @throws IOException
     *
     * @author otsuru
     * @since 2023/4/5
     */
    public static PolynomialStructure setupFromFileOrName(Path structurePath, String structureName) throws IOException {
        PolynomialStructure structure = null;
        if (structurePath != null) {
            structure = PolynomialStructureFile.read(structurePath);
        } else {
            structure = PolynomialStructure.of(structureName);
        }
        return structure;
    }

    /**
     * Get a default structure.
     * @param modelName (String) Name of a default structure.
     * @return ({@link PolynomialStructure}) Structure.
     *
     * @author otsuru
     * @since 2022/2/5 moved from inside run() in SyntheticDSMSetup
     */
    public static PolynomialStructure of(String modelName) {
        PolynomialStructure ps = null;

        switch (modelName) {
        case "PREM":
            System.err.println("Using PREM");
            ps = DefaultStructure.PREM;
            break;
        case "IPREM":
            System.err.println("Using IPREM");
            ps = DefaultStructure.IPREM;
            break;
        case "IASP91":
            System.err.println("Using IASP91");
            ps = DefaultStructure.IASP91;
            break;
        case "MIASP91":
            System.err.println("Using MIASP91");
            ps = DefaultStructure.MIASP91;
            break;
        case "AK135":
            System.err.println("Using AK135");
            ps = DefaultStructure.AK135;
            break;
        case "HOMOGEN":
            System.err.println("Using HOMOGEN");
            ps = DefaultStructure.HOMOGEN;
            break;
        default:
            throw new RuntimeException("Model not implemented yet");
        }

        return ps;
    }

    /**
     * Constructor for creating user-defined structures.
     * @param nZone
     * @param rMin
     * @param rMax
     * @param rho
     * @param vpv
     * @param vph
     * @param vsv
     * @param vsh
     * @param eta
     * @param qMu
     * @param qKappa
     */
    public PolynomialStructure(int nZone, int nCoreZone, double[] rMin, double[] rMax, PolynomialFunction[] rho,
            PolynomialFunction[] vpv, PolynomialFunction[] vph, PolynomialFunction[] vsv, PolynomialFunction[] vsh,
            PolynomialFunction[] eta, double[] qMu, double[] qKappa) {
        this(nZone, nCoreZone, rMin, rMax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa, false);
    }

    /**
     * Constructor for creating default structures in {@link DefaultStructure}.
     * @param nZone
     * @param rMin
     * @param rMax
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
    PolynomialStructure(int nZone, int nCoreZone, double[] rMin, double[] rMax, PolynomialFunction[] rho,
            PolynomialFunction[] vpv, PolynomialFunction[] vph, PolynomialFunction[] vsv, PolynomialFunction[] vsh,
            PolynomialFunction[] eta, double[] qMu, double[] qKappa, boolean isDefault) {
        this.nZone = nZone;
        this.nCoreZone = nCoreZone;
        this.rMin = rMin.clone();
        this.rMax = rMax.clone();
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
     * Constructor for creating default structures in {@link DefaultStructure}.
     * @param nZone
     * @param rMin
     * @param rMax
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
    PolynomialStructure(int nZone, int nCoreZone, double[] rMin, double[] rMax, double[][] rho, double[][] vpv,
            double[][] vph, double[][] vsv, double[][] vsh, double[][] eta, double[] qMu,
            double[] qKappa, boolean isDefault) {
        this.nZone = nZone;
        this.nCoreZone = nCoreZone;
        this.rMin = rMin.clone();
        this.rMax = rMax.clone();

        this.rho = new PolynomialFunction[nZone];
        this.vpv = new PolynomialFunction[nZone];
        this.vph = new PolynomialFunction[nZone];
        this.vsv = new PolynomialFunction[nZone];
        this.vsh = new PolynomialFunction[nZone];
        this.eta = new PolynomialFunction[nZone];
        for (int i = 0; i < nZone; i++) {
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

    /**
     * Add boundaries at the input radii.
     * If there is already a boundary at r then nothing will be done.
     *
     * @param boundaries (double...) Radii for boundaries. Values smaller than 0 or bigger than planet radius will be ignored.
     * @return ({@link PolynomialStructure}) A new structure which has additional layers split at input boundaries,
     *              or current structure itself if all radii already exist in current structure.
     */
    public PolynomialStructure withBoundaries(double... boundaries) {
        return withBoundaries(false, boundaries);
    }

    /**
     * Add boundaries at the input radii.
     * If there is already a boundary at r then nothing will be done.
     *
     * @param isDefault (boolean) Whether to create this as a default structure (to be used in {@link DefaultStructure}).
     * @param boundaries (double...) Radii for boundaries. Values smaller than 0 or bigger than planet radius will be ignored.
     * @return ({@link PolynomialStructure}) A new structure which has additional layers split at input boundaries,
     *              or current structure itself if all radii already exist in current structure.
     */
    PolynomialStructure withBoundaries(boolean isDefault, double... boundaries) {
        double[] addBoundaries = Arrays.stream(boundaries)
                .filter(d -> 0 < d && d < rMax[nZone - 1] && Arrays.binarySearch(rMin, d) < 0).distinct().sorted()
                .toArray();
        if (addBoundaries.length == 0)
            return this;

        int nZoneNew = nZone + addBoundaries.length;
        int nCoreZoneNew = nCoreZone + (int) Arrays.stream(addBoundaries).filter(r -> r < rMin[nCoreZone]).count();
        double[] rMinNew = DoubleStream.concat(Arrays.stream(rMin), Arrays.stream(addBoundaries)).sorted().toArray();
        double[] rMaxNew = DoubleStream.concat(Arrays.stream(rMax), Arrays.stream(addBoundaries)).sorted().toArray();

        PolynomialFunction[] rhoNew = new PolynomialFunction[nZoneNew];
        PolynomialFunction[] vpvNew = new PolynomialFunction[nZoneNew];
        PolynomialFunction[] vphNew = new PolynomialFunction[nZoneNew];
        PolynomialFunction[] vsvNew = new PolynomialFunction[nZoneNew];
        PolynomialFunction[] vshNew = new PolynomialFunction[nZoneNew];
        PolynomialFunction[] etaNew = new PolynomialFunction[nZoneNew];
        double[] qMuNew = new double[nZoneNew];
        double[] qKappaNew = new double[nZoneNew];

        for (int iZoneNew = 0; iZoneNew < nZoneNew; iZoneNew++) {
            double rMin = rMinNew[iZoneNew];
            // izone in this for rmin
            int iZoneOld = zoneOf(rMin);
            // copy. PolynomialFunction is immutable so instances do not have to be recreated.
            rhoNew[iZoneNew] = rho[iZoneOld];
            vpvNew[iZoneNew] = vpv[iZoneOld];
            vphNew[iZoneNew] = vph[iZoneOld];
            vsvNew[iZoneNew] = vsv[iZoneOld];
            vshNew[iZoneNew] = vsh[iZoneOld];
            etaNew[iZoneNew] = eta[iZoneOld];
            qMuNew[iZoneNew] = qMu[iZoneOld];
            qKappaNew[iZoneNew] = qKappa[iZoneOld];
        }

        return new PolynomialStructure(nZoneNew, nCoreZoneNew, rMinNew, rMaxNew,
                rhoNew, vpvNew, vphNew, vsvNew, vshNew, etaNew, qMuNew, qKappaNew, isDefault);
    }

    /**
     * Add perturbation of a certain parameter to an arbitrary layer.
     * This returns a new instance; the original instance is unchanged.
     *
     * @param r1 (double) Lower radius of layer to add perturbation to.
     * @param r2 (double) Upper radius of layer to add perturbation to.
     * @param variable (VariableType) The parameter to perburb.
     * @param percent (double) Size of perturbation [%].
     * @return ({@link PolynomialStructure}) A new structure with added perturbations.
     */
    public PolynomialStructure withPerturbation(double r1, double r2, VariableType variable, double percent) {
        // look up whether r1 and r2 are existing boundaries or not
        boolean foundR1 = false;
        boolean foundR2 = false;
        for (double r : rMin) {
            if (Precision.equals(r1, r, R_EPSILON))
                foundR1 = true;
            if (Precision.equals(r2, r, R_EPSILON))
                foundR2 = true;
        }

        // add r1 and r2 as boundaries if they were not already
        PolynomialStructure originalStructure = this;
        if (!foundR1)
            originalStructure = originalStructure.withBoundaries(r1);
        if (!foundR2)
            originalStructure = originalStructure.withBoundaries(r2);

        // get values of structureNew
        int nZoneNew = originalStructure.getNZone();
        int nCoreZoneNew = originalStructure.getNCoreZone();
        double[] rMinNew = originalStructure.getRmin();
        double[] rMaxNew = originalStructure.getRmax();
        PolynomialFunction[] rhoNew = originalStructure.getRho();
        PolynomialFunction[] vpvNew = originalStructure.getVpv();
        PolynomialFunction[] vphNew = originalStructure.getVph();
        PolynomialFunction[] vsvNew = originalStructure.getVsv();
        PolynomialFunction[] vshNew = originalStructure.getVsh();
        PolynomialFunction[] etaNew = originalStructure.getEta();
        double[] qMuNew = originalStructure.getQMu();
        double[] qKappaNew = originalStructure.getQKappa();

        // decide factor
        double factor = 1.0 + percent / 100.0;

        // multiply the functions of the corresponding zones
        int iZoneR1 = originalStructure.zoneOf(r1);
        int iZoneR2 = originalStructure.zoneOf(r2);
        for (int iZone = iZoneR1; iZone < iZoneR2; iZone++) {
            switch(variable) {
            case RHO:
                rhoNew[iZone] = multiplyCoefficient(rhoNew[iZone], factor);
                break;
            case Vp:
                vpvNew[iZone] = multiplyCoefficient(vpvNew[iZone], factor);
                vphNew[iZone] = multiplyCoefficient(vphNew[iZone], factor);
                break;
            case Vpv:
                vpvNew[iZone] = multiplyCoefficient(vpvNew[iZone], factor);
                break;
            case Vph:
                vphNew[iZone] = multiplyCoefficient(vphNew[iZone], factor);
                break;
            case Vs:
                vsvNew[iZone] = multiplyCoefficient(vsvNew[iZone], factor);
                vshNew[iZone] = multiplyCoefficient(vshNew[iZone], factor);
                break;
            case Vsv:
                vsvNew[iZone] = multiplyCoefficient(vsvNew[iZone], factor);
                break;
            case Vsh:
                vshNew[iZone] = multiplyCoefficient(vshNew[iZone], factor);
                break;
            case ETA:
                etaNew[iZone] = multiplyCoefficient(etaNew[iZone], factor);
                break;
            case Qmu:
                qMuNew[iZone] = qMuNew[iZone] * factor;
                break;
            case Qkappa:
                qKappaNew[iZone] = qKappaNew[iZone] * factor;
                break;
            default:
                throw new IllegalArgumentException("Illegal variable type: " + variable);
            }
        }

        return new PolynomialStructure(nZoneNew, nCoreZoneNew, rMinNew, rMaxNew,
                rhoNew, vpvNew, vphNew, vsvNew, vshNew, etaNew, qMuNew, qKappaNew);
    }

    private PolynomialFunction multiplyCoefficient(PolynomialFunction function, double factor) {
        double[] coefficients = Arrays.stream(function.getCoefficients()).map(r -> MathAid.roundForPrecision(r * factor)).toArray();
        return new PolynomialFunction(coefficients);
    }

    /**
     * Find the number of the zone which includes the given radius.
     * Note that the zone will be rmin &le; r &lt; rmax, except when r = planetRadius.
     * @param r (double) Radius [km]; [0, rmax].
     * @return (int) The number of the zone which includes r.
     */
    public int zoneOf(double r) {
        if (r == rMax[nZone - 1])
            return nZone - 1;
        return IntStream.range(0, nZone).filter(i -> rMin[i] <= r && r < rMax[i]).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input r:" + r + "is invalid."));
    }

    /**
     * Calculate normalized radius x = r / planetRadius
     *
     * @param r (double) Radius [km].
     * @return (double) A value x to the input r for polynomial functions.
     */
    private double xFor(double r) {
        return r / planetRadius();
    }

    public double planetRadius() {
        return rMax[nZone - 1];
    }

    /**
     * Get elastic medium at a given radius.
     * @param r
     * @return
     *
     * @author otsuru
     * @since 2022/4/11
     */
    public ElasticMedium mediumAt(double r) {
        ElasticMedium medium = new ElasticMedium();
        medium.set(VariableType.RHO, rho[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.Vpv, vpv[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.Vph, vph[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.Vsv, vsv[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.Vsh, vsh[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.ETA, eta[zoneOf(r)].value(xFor(r)));
        medium.set(VariableType.Qmu, qMu[zoneOf(r)]);
        medium.set(VariableType.Qkappa, qKappa[zoneOf(r)]);
        return medium;
    }

    /**
     * Get value of a specified parameter at a given radius.
     * @param variable (VariableType) the type of variable to obtain. Only RHO, Vpv, Vph, Vsv, Vsh, ETA, Qmu, Qkappa are allowed.
     * @param r (double) radius [km]
     * @return (double) value of the parameter at the given radius
     *
     * @author otsuru
     * @since 2022/4/11
     */
    public double getAtRadius(VariableType variable, double r) {
        switch(variable) {
        case RHO:
            return rho[zoneOf(r)].value(xFor(r));
        case Vpv:
            return vpv[zoneOf(r)].value(xFor(r));
        case Vph:
            return vph[zoneOf(r)].value(xFor(r));
        case Vsv:
            return vsv[zoneOf(r)].value(xFor(r));
        case Vsh:
            return vsh[zoneOf(r)].value(xFor(r));
        case ETA:
            return eta[zoneOf(r)].value(xFor(r));
        case Qmu:
            return qMu[zoneOf(r)];
        case Qkappa:
            return qKappa[zoneOf(r)];
        default:
//            return getMediumAt(r).get(type);
            throw new IllegalArgumentException("Illegal parameter type");
        }
    }

    /**
     * Export structure for use in PSV input files of DSM.
     * @return (String[]) Lines to be printed, including comment lines starting with the letter 'c'.
     */
    public String[] toPSVlines() {
        String[] outString = new String[6 * (nZone) + 7];
        outString[0] = String.valueOf(nZone) + " nzone";
        outString[1] = "c  - Radius (km) -    --- Density (g/cm^3) ---";
        outString[2] = "c                     ---   Vpv     (km/s) ---";
        outString[3] = "c                     ---   Vph     (km/s) ---";
        outString[4] = "c                     ---   Vsv     (km/s) ---";
        outString[5] = "c                     ---   Vsh     (km/s) ---";
        outString[6] = "c                     ---   eta     (ND  ) ---             - Qmu -  - Qkappa -";
        for (int i = 0; i < nZone; i++) {
            outString[6 * i + 7] = rMin[i] + " " + rMax[i] + " " + stringFor(rho[i]);
            outString[6 * i + 8] = "          " + stringFor(vpv[i]);
            outString[6 * i + 9] = "          " + stringFor(vph[i]);
            outString[6 * i + 10] = "          " + stringFor(vsv[i]);
            outString[6 * i + 11] = "          " + stringFor(vsh[i]);
            outString[6 * i + 12] = "          " + stringFor(eta[i]) + " " + qMu[i] + " " + qKappa[i];
        }
        return outString;
    }

    /**
     * Export structure for use in SH input files of DSM. Zones inside the core will not be printed.
     * @return (String[]) Lines to be printed, including comment lines starting with the letter 'c'.
     */
    public String[] toSHlines() {
        int zone = nZone - nCoreZone;
        String[] outString = new String[3 * zone + 4];
        outString[0] = zone + " nzone";
        outString[1] = "c  - Radius (km) -    --- Density (g/cm^3) ---";
        outString[2] = "c                     ---   Vsv     (km/s) ---";
        outString[3] = "c                     ---   Vsh     (km/s) ---          - Qmu -";
        for (int i = nCoreZone; i < nZone; i++) {
            outString[3 * (i - nCoreZone) + 4] = rMin[i] + " " + rMax[i] + " " + stringFor(rho[i]);
            outString[3 * (i - nCoreZone) + 5] = "          " + stringFor(vsv[i]);
            outString[3 * (i - nCoreZone) + 6] = "          " + stringFor(vsh[i]) + " " + qMu[i];
        }
        return outString;
    }

    /**
     * change String line from coefficients a + bx + cx**2 + dx**3 >>>>> a b c d
     *
     * @param pf polynomial function for a layer
     * @return string in a form of this
     */
    static String stringFor(PolynomialFunction pf) {
        return Arrays.stream(Arrays.copyOf(pf.getCoefficients(), 4)).mapToObj(Double::toString)
                .collect(Collectors.joining(" "));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + nCoreZone;
        result = prime * result + Arrays.hashCode(eta);
        result = prime * result + nZone;
        result = prime * result + Arrays.hashCode(qKappa);
        result = prime * result + Arrays.hashCode(qMu);
        result = prime * result + Arrays.hashCode(rho);
        result = prime * result + Arrays.hashCode(rMax);
        result = prime * result + Arrays.hashCode(rMin);
        result = prime * result + Arrays.hashCode(vph);
        result = prime * result + Arrays.hashCode(vpv);
        result = prime * result + Arrays.hashCode(vsh);
        result = prime * result + Arrays.hashCode(vsv);
        return result;
    }

    /**
     * Whether two structures are the same.
     * They are considered same when all parameters, except for isDefault, are completely the same.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PolynomialStructure other = (PolynomialStructure) obj;
        if (nCoreZone != other.nCoreZone)
            return false;
        if (!Arrays.equals(eta, other.eta))
            return false;
        if (nZone != other.nZone)
            return false;
        if (!Arrays.equals(qKappa, other.qKappa))
            return false;
        if (!Arrays.equals(qMu, other.qMu))
            return false;
        if (!Arrays.equals(rho, other.rho))
            return false;
        if (!Arrays.equals(rMax, other.rMax))
            return false;
        if (!Arrays.equals(rMin, other.rMin))
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

    public int getNZone() {
        return nZone;
    }

    public int getNCoreZone() {
        return nCoreZone;
    }

    public double[] getRmin() {
        return rMin.clone();
    }

    public double[] getRmax() {
        return rMax.clone();
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

    public double[] getQMu() {
        return qMu.clone();
    }

    public double[] getQKappa() {
        return qKappa.clone();
    }

    /**
     * @return true if default structure (already implemented), false if user-defined structure
     */
    public boolean isDefault() {
        return isDefault;
    }

}
