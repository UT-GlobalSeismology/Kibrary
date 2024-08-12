package io.github.kensuke1984.kibrary.dsmsetup;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.ArithmeticUtils;

/**
 * Header part of a file for DSM.
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @version 2021/11/18 Renamed from dsminformation.DSMheader to dsmsetup.DSMInputHeader.
 */
class DSMInputHeader {

    /**
     * Time length [s].
     */
    private final double tlen;

    /**
     * Number of steps in frequency domain, must be a power of 2.
     */
    private final int np;

    /**
     * The default value is 0.
     */
    private final int imin;

    /**
     * The default value is set as 'np'.
     */
    private final int imax;

    /**
     * re; relative error (See GT95 eq. 6.2). The default value is 1e-2.
     */
    private final double relativeError;

    /**
     * ratc; ampratio to use in grid cut-off. 1e-10 is recommended. The default value is 1e-10.
     */
    private final double ratc;

    /**
     * ratl; ampratio to use in l-cutoff. The default value is 1e-5.
     */
    private final double ratl;

    /**
     * Wrap-around attenuation for OMEGAI. The default value is 1e-2.
     */
    private final double artificialDamping;

    /**
     * Header info of DSM.
     * re = 1.e-2; ratc = 1.e-10; ratl = 1.e-5; artificialDamping = 1.e-2; imin = 0; imax = NP.
     *
     * @param tlen must be 2<sup>n</sup>/10 where n is an integer
     * @param np   must be 2<sup>n</sup> where n is an integer
     */
    DSMInputHeader(double tlen, int np) {
        this(tlen, np, 0, np, 1.e-2, 1.e-10, 1.e-5, 1.e-2);
    }

    DSMInputHeader(double tlen, int np, int imin, int imax, double relativeError, double ratc, double ratl,
              double artificialDamping) {
        if (!ArithmeticUtils.isPowerOfTwo(np) || !validTlen(tlen))
            throw new IllegalArgumentException("Input TLEN:" + tlen + " or NP:" + np + " is invalid");
        this.tlen = tlen;
        this.np = np;
        this.imin = imin;
        this.imax = imax;
        this.relativeError = relativeError;
        this.ratc = ratc;
        this.ratl = ratl;
        this.artificialDamping = artificialDamping;
    }

    /**
     * Checks whether a given value is valid for tlen, which must be 2<sup>n</sup>/10.
     * @param tlen (double) Value of tlen to be checked.
     * @return (boolean) Whether the given value is valid.
     */
    private static boolean validTlen(double tlen) {
        long tlen10 = Math.round(10 * tlen);
        return ArithmeticUtils.isPowerOfTwo(tlen10) && tlen10 / 10.0 == tlen;
    }

    public double getTlen() {
        return tlen;
    }

    public int getNp() {
        return np;
    }

    public int getImin() {
        return imin;
    }

    public int getImax() {
        return imax;
    }

    /**
     * @return relative error
     */
    public double getRe() {
        return relativeError;
    }

    /**
     * @return ratc
     */
    public double getRatc() {
        return ratc;
    }

    public double getRatl() {
        return ratl;
    }

    public double getArtificialDamping() {
        return artificialDamping;
    }

    String[] outputDSMHeader() {
        List<String> outputLines = new ArrayList<>();
        outputLines.add("c parameters for the periodic range");
        outputLines.add(tlen + " " + np + "  tlen (s), np");
        outputLines.add("c relative error (see GT95 eq. 6.2)");
        outputLines.add(relativeError + "  re");
        outputLines.add("c ampratio to use in grid cut-off (1.d-10 is recommended)");
        outputLines.add(ratc + "  ratc");
        outputLines.add("c ampratio to use in l-cutoff (see KTG04 fig.8)");
        outputLines.add(ratl + "  ratl");
        outputLines.add("c artificial damping for wrap-around (see GO94 5.1)");
        outputLines.add(artificialDamping + "  adamp");
        outputLines.add("c frequency index range");
        outputLines.add(imin + " " + imax + "  imin, imax");

        return outputLines.toArray(new String[11]);
    }

}
