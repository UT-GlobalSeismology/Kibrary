package io.github.kensuke1984.kibrary.util.earth;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

/**
 * Data of various 1D Earth structures.
 * <p>
 * Structures shall be defined as combinations of polynomial functions.
 * The radii used as the variable x in polynomial functions should be normalized to the planet radius.
 *
 * @author otsuru
 * @since 2022/2/5 extracted some parts of PolynomialStructure
 * @version 2022/6/17 recreated & renamed from PolynomialStructureData to DefaultStructure
 */
public class DefaultStructure {
    private DefaultStructure() {}

    /**
     * Transversely isotropic (TI) PREM by Dziewonski &amp; Anderson 1981.
     * The ocean is excluded from the model. The crust is extended to the surface instead.
     */
    public static final PolynomialStructure PREM = initialAnisoPREM();
    /**
     * Isotropic PREM by Dziewonski &amp; Anderson 1981.
     * The ocean is excluded from the model. The crust is extended to the surface instead.
     */
    public static final PolynomialStructure IPREM = initialIsoPREM();
    /**
     * IASP91 by Kennett &amp; Engdahl 1991.
     * It has no density model, so the density of PREM is used instead.
     */
    public static final PolynomialStructure IASP91 = initialIASP91();
    /**
     * A version of IASP91 modified to make the structure smoother.
     * See Borgeaud et al. 2016.
     */
    public static final PolynomialStructure MIASP91 = initialMIASP91();
    /**
     * AK135 by Kennett <i>et al.</i> 1995.
     */
    public static final PolynomialStructure AK135 = initialAK135();

    /**
     * Homogeneous earth structure used for test purposes
     */
    public static final PolynomialStructure HOMOGEN = homogeneous();


    private static PolynomialStructure initialAnisoPREM() {
        int nZone = 12;
        int nCoreZone = 2;
        double[] rMin = new double[]{0, 1221.5, 3480, 3630, 5600, 5701, 5771, 5971, 6151, 6291, 6346.6, 6356};
        double[] rMax = new double[]{1221.5, 3480, 3630, 5600, 5701, 5771, 5971, 6151, 6291, 6346.6, 6356, 6371};
        double[][] rho = new double[][]{{13.0885, 0, -8.8381, 0}, {12.5815, -1.2638, -3.6426, -5.5281},
                {7.9565, -6.4761, 5.5283, -3.0807}, {7.9565, -6.4761, 5.5283, -3.0807}, {7.9565, -6.4761, 5.5283, -3.0807},
                {5.3197, -1.4836, 0, 0}, {11.2494, -8.0298, 0, 0}, {7.1089, -3.8045, 0, 0},
                {2.691, 0.6924, 0, 0}, {2.691, 0.6924, 0, 0}, {2.9, 0, 0, 0}, {2.6, 0, 0, 0}};
        double[][] vpv = new double[][]{{11.2622, 0, -6.364, 0}, {11.0487, -4.0362, 4.8023, -13.5732},
                {15.3891, -5.3181, 5.5242, -2.5514}, {24.952, -40.4673, 51.4832, -26.6419}, {29.2766, -23.6027, 5.5242, -2.5514},
                {19.0957, -9.8672, 0, 0}, {39.7027, -32.6166, 0, 0}, {20.3926, -12.2569, 0, 0},
                {0.8317, 7.218, 0, 0}, {0.8317, 7.218, 0, 0}, {6.8, 0, 0, 0}, {5.8, 0, 0, 0}};
        double[][] vph = new double[][]{{11.2622, 0, -6.364, 0}, {11.0487, -4.0362, 4.8023, -13.5732},
                {15.3891, -5.3181, 5.5242, -2.5514}, {24.952, -40.4673, 51.4832, -26.6419}, {29.2766, -23.6027, 5.5242, -2.5514},
                {19.0957, -9.8672, 0, 0}, {39.7027, -32.6166, 0, 0}, {20.3926, -12.2569, 0, 0},
                {3.5908, 4.6172, 0, 0}, {3.5908, 4.6172, 0, 0}, {6.8, 0, 0, 0}, {5.8, 0, 0, 0}};
        double[][] vsv = new double[][]{{3.6678, 0, -4.4475, 0}, {0, 0, 0, 0},
                {6.9254, 1.4672, -2.0834, 0.9783}, {11.1671, -13.7818, 17.4575, -9.2777}, {22.3459, -17.2473, -2.0834, 0.9783},
                {9.9839, -4.9324, 0, 0}, {22.3512, -18.5856, 0, 0}, {8.9496, -4.4597, 0, 0},
                {5.8582, -1.4678, 0, 0}, {5.8582, -1.4678, 0, 0}, {3.9, 0, 0, 0}, {3.2, 0, 0, 0}};
        double[][] vsh = new double[][]{{3.6678, 0, -4.4475, 0}, {0, 0, 0, 0},
                {6.9254, 1.4672, -2.0834, 0.9783}, {11.1671, -13.7818, 17.4575, -9.2777}, {22.3459, -17.2473, -2.0834, 0.9783},
                {9.9839, -4.9324, 0, 0}, {22.3512, -18.5856, 0, 0}, {8.9496, -4.4597, 0, 0},
                {-1.0839, 5.7176, 0, 0}, {-1.0839, 5.7176, 0, 0}, {3.9, 0, 0, 0}, {3.2, 0, 0, 0}};
        double[][] eta = new double[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0},
                {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {3.3687, -2.4778, 0, 0}, {3.3687, -2.4778, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        double[] qMu = new double[]{84.6, -1, 312, 312, 312, 143, 143, 143, 80, 600, 600, 600};
        double[] qKappa = new double[]{1327.7, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823};
        return new PolynomialStructure(nZone, nCoreZone, rMin, rMax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa, true);
    }

    private static PolynomialStructure initialIsoPREM() {
        PolynomialStructure prem = initialAnisoPREM();
        PolynomialFunction[] vp = prem.getVpv();
        PolynomialFunction[] vs = prem.getVsv();
        PolynomialFunction[] eta = prem.getEta();

        PolynomialFunction funcVp = new PolynomialFunction(new double[]{4.1875, 3.9382, 0, 0});
        PolynomialFunction funcVs = new PolynomialFunction(new double[]{2.1519, 2.3481, 0, 0});
        PolynomialFunction funcEta = new PolynomialFunction(new double[]{1, 0, 0, 0});
        for (int i = 8; i <= 9; i++) {
            vp[i] = funcVp;
            vs[i] = funcVs;
            eta[i] = funcEta;
        }

        return new PolynomialStructure(prem.getNZone(), prem.getNCoreZone(), prem.getRmin(), prem.getRmax(),
                prem.getRho(), vp, vp, vs, vs, eta, prem.getQMu(), prem.getQKappa(), true);
    }

    private static PolynomialStructure initialIASP91() {
        int nZone = 11;
        int nCoreZone = 2;
        double[] rMin = new double[]{0, 1217.1, 3482, 3631, 5611, 5711, 5961, 6161, 6251, 6336, 6351};
        double[] rMax = new double[]{1217.1, 3482, 3631, 5611, 5711, 5961, 6161, 6251, 6336, 6351, 6371};
        double[][] rho = new double[][]{{13.0885, 0, -8.8381, 0}, {12.5815, -1.2638, -3.6426, -5.5281},
                {7.9565, -6.4761, 5.5283, -3.0807}, {7.9565, -6.4761, 5.5283, -3.0807}, {7.9565, -6.4761, 5.5283, -3.0807},
                {11.2494, -8.0298, 0, 0}, {7.1089, -3.8045, 0, 0},
                {2.691, 0.6924, 0, 0}, {2.691, 0.6924, 0, 0}, {2.9, 0, 0, 0}, {2.6, 0, 0, 0}};
        double[][] vp = new double[][]{{11.24094, 0, -4.09689, 0}, {10.03904, 3.75665, -13.67046, 0},
                {14.49470, -1.47089, 0, 0}, {25.1486, -41.1538, 51.9932, -26.6083}, {25.96984, -16.93412, 0, 0},
                {29.38896, -21.40656, 0, 0}, {30.78765, -23.25415, 0, 0},
                {25.41389, -17.69722, 0, 0}, {8.78541, -0.74953, 0, 0}, {6.5, 0, 0, 0}, {5.8, 0, 0, 0}};
        double[][] vs = new double[][]{{3.56454, 0, -3.45241, 0}, {0, 0, 0, 0},
                {8.16616, -1.58206, 0, 0}, {12.9303, -21.259, 27.8988, -14.108}, {20.7689, -16.53147, 0, 0},
                {17.70732, -13.50652, 0, 0}, {15.24213, -11.08552, 0, 0},
                {5.7502, -1.2742, 0, 0}, {6.706231, -2.248585, 0, 0}, {3.75, 0, 0, 0}, {3.36, 0, 0, 0}};
        double[][] eta = new double[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0},
                {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        double[] qMu = new double[]{84.6, -1, 312, 312, 312, 143, 143, 80, 600, 600, 600};
        double[] qKappa = new double[]{1327.7, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823};
        return new PolynomialStructure(nZone, nCoreZone, rMin, rMax, rho, vp, vp, vs, vs, eta, qMu, qKappa, true);
    }

    private static PolynomialStructure initialMIASP91() {
        int nZone = 12;
        int nCoreZone = 2;
        double[] rMin = new double[]{0, 1221.5, 3480, 3630, 5610, 5641, 5781, 5891, 5971, 6030.9, 6160, 6281};
        double[] rMax = new double[]{1221.5, 3480, 3630, 5610, 5641, 5781, 5891, 5971, 6030.9, 6160, 6281, 6371};
        double[][] rho = new double[][]{{13.0885, 0, -8.8381, 0}, {12.5815, -1.2638, -3.6426, -5.5281},
                {7.2586, -3.1016, 0, 0}, {7.9469, -6.4376, 5.4773, -3.0584}, {7.8896, -3.9208, 0, 0},
                {22.3146, -20.2128, 0, 0}, {14.743076955227242, -11.868712364945988, 0, 0}, {14.743076955227242, -11.868712364945988, 0, 0},
                {14.743076955227242, -11.868712364945988, 0, 0}, {8.1973, -4.9538, 0, 0}, {6.1900, -2.8776, 0, 0}, {5.6768, -2.3570, 0, 0}};
        double[][] vp = new double[][]{{11.2622, 0, -6.3640, 0}, {11.0487, -4.0362, 4.8023, -13.5732},
            {14.4729, -1.4327, 0, 0}, {25.0591, -40.7952, 51.5188, -26.4007}, {25.8698, -16.8211, 0, 0},
            {51.6956, -45.9896, 0, 0}, {29.3890, -21.4066, 0, 0}, {44.0573, -37.2711, 0, 0},
            {44.1294, -37.3480, 0, 0}, {30.7797, -23.2453, 0, 0}, {21.4868, -13.6332, 0, 0}, {8.5458, -0.5058, 0, 0}};
        double[][] vs = new double[][]{{3.6678, 0, -4.4475, 0}, {0, 0, 0, 0},
            {8.14951, -1.5525, 0, 0}, {12.90771, -21.1679, 27.7784, -14.0554}, {20.53961, -16.2723, 0, 0},
            {33.51471, -30.9268, 0, 0}, {17.70751, -13.5065, 0, 0}, {24.97041, -21.3617, 0, 0},
            {25.00361, -21.3971, 0, 0}, {15.34491, -11.1936, 0, 0}, {6.21621, -1.7514, 0, 0}, {5.85131, -1.3812, 0, 0}};
        double[][] eta = new double[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0},
                {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        double[] qMu = new double[]{84.6, -1.0, 312.0, 312.0, 312.0, 312.0, 143.0, 143.0, 143.0, 143.0, 80.0, 600.0};
        double[] qKappa = new double[]{1327.7, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0, 57823.0};
        return new PolynomialStructure(nZone, nCoreZone, rMin, rMax, rho, vp, vp, vs, vs, eta, qMu, qKappa, true);
    }

    private static PolynomialStructure initialAK135() {
        int nzone = 11;
        int nCoreZone = 2;
        double[] rMin = new double[]{0, 1217.5, 3479.5, 3631, 5611, 5711, 5961, 6161, 6251, 6336, 6351};
        double[] rMax = new double[]{1217.5, 3479.5, 3631, 5611, 5711, 5961, 6161, 6251, 6336, 6351, 6371};
        double[][] rho = new double[][]{{13.01224, -0.00072, -8.448571, 0}, {12.27867, 1.206494, -10.135214, 0},
                {5.520665, -0.172417, 0, 0}, {9.404821, -14.092113, 17.721033, -9.221153}, {10.084566, -6.409226, 0, 0},
                {11.384761, -8.109009, 0, 0}, {11.916663, -8.811093, 0, 0}, {9.878741, -6.703708, 0, 0},
                {3.573402, -0.277326, 0, 0}, {2.7142, 0, 0, 0}, {2.449, 0, 0, 0}};
        double[][] vp = new double[][]{{11.261692, 0.028794, -6.627846, 0}, {10.118851, 3.457774, -13.434875, 0},
                {13.908244, -0.45417, 0, 0}, {24.138794, -37.097655, 46.631994, -24.272115}, {25.969838, -16.934118, 0, 0},
                {29.38896, -21.40656, 0, 0}, {30.78765, -23.25415, 0, 0}, {25.413889, -17.697222, 0, 0},
                {8.785412, -0.749529, 0, 0}, {6.5, 0, 0, 0}, {5.8, 0, 0, 0}};
        double[][] vs = new double[][]{{3.667865, -0.001345, -4.440915, 0}, {0, 0, 0, 0},
                {8.018341, -1.349895, 0, 0}, {12.213901, -18.573085, 24.557329, -12.728015}, {20.208945, -15.895645, 0, 0},
                {17.71732, -13.50652, 0, 0}, {15.212335, -11.053685, 0, 0}, {5.7502, -1.2742, 0, 0},
                {5.970824, -1.499059, 0, 0}, {3.85, 0, 0, 0}, {3.46, 0, 0, 0}};
        double[][] eta = new double[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0},
                {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        double[] qMu = new double[]{84.6, -1, 312, 312, 312, 143, 143, 80, 600, 600, 600};
        double[] qKappa = new double[]{1327.7, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823, 57823};
        return new PolynomialStructure(nzone, nCoreZone, rMin, rMax, rho, vp, vp, vs, vs, eta, qMu, qKappa, true);
    }

    /**
     * Homogeneous earth structure used for test purposes
     */
    private static PolynomialStructure homogeneous() {
        double eps = 1e-10;
        int nZone = 3;
        int nCoreZone = 2;
        double[] rMin = new double[]{0, 1221.5, 3480.0};
        double[] rMax = new double[]{1221.5, 3480.0, 6371};
        double[][] rho = new double[][]{{10.0, 0.0, 0.0, 0.0}, {10.0, 0.0, 0.0, 0.0}, {10.0, 0.0, 0.0, 0.0}};
        double[][] vpv = new double[][]{{eps, 17., 0.0, 0.0}, {0., 17., 0.0, 0.0}, {0., 17., 0.0, 0.0}};
        double[][] vph = new double[][]{{eps, 17.51, 0.0, 0.0}, {0., 17.51, 0.0, 0.0}, {0., 17.51, 0.0, 0.0}};
        // TODO 2021.4.5 changed to vsh=vsv=0 in outer-core. Check if comparison still good
        double[][] vsv = new double[][]{{eps, 10., 0.0, 0.0}, {0., 0.0, 0.0, 0.0}, {0., 10., 0.0, 0.0}};
        double[][] vsh = new double[][]{{eps, 10.3, 0.0, 0.0}, {0., 0.0, 0.0, 0.0}, {0., 10.3, 0.0, 0.0}};
        double[][] eta = new double[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        double[] qMu = new double[]{84.6, -1, 600};
        double[] qKappa = new double[]{1327.7, 57823, 57823};
        return new PolynomialStructure(nZone, nCoreZone, rMin, rMax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa, true);
    }


}
