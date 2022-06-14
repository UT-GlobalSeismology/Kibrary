package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.dsmsetup.TransverselyIsotropicParameter;
import io.github.kensuke1984.kibrary.elasticparameter.ElasticMedium;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.data.Trace;

/**
 * 1D structure of the Earth.
 * To be used as input for softwares of <i>Direct Solution Method</i> (DSM)<br>
 * <p>
 * Every depth is written in <b>radius</b>.<br>
 * <p>
 * This class is <b>IMMUTABLE</b> <br>
 * <p>
 * When you try to get values on radius of boundaries, you will get one in the
 * isShallower layer, i.e., the layer which has the radius as rmin.
 *
 * @author Kensuke Konishi, anselme
 * @since version 0.2.10
 * @version 2022/2/10 moved from dsmsetup
 */
public class PolynomialStructure implements Serializable {

    /**
     * 2020/4/18
     */
    private static final long serialVersionUID = 387354455950454238L;

    /**
     * transversely isotropic (TI) PREM by Dziewonski &amp; Anderson 1981
     */
    public static final PolynomialStructure PREM = PolynomialStructureData.initialAnisoPREM();
    /**
     * isotropic PREM by Dziewonski &amp; Anderson 1981
     */
    public static final PolynomialStructure ISO_PREM = PolynomialStructureData.initialIsoPREM();
    /**
     * AK135 by Kennett <i>et al</i>. (1995)
     */
    public static final PolynomialStructure AK135 = PolynomialStructureData.initialAK135();

    /**
     * Homogeneous earth structure used for test purposes
     */
    public static final PolynomialStructure HOMOGEN = PolynomialStructureData.homogeneous();
    public static final PolynomialStructure MIASP91 = PolynomialStructureData.initialMIASP91();
    public static final PolynomialStructure TBL50 = PolynomialStructureData.initialTBL50();
    public static final PolynomialStructure TNASNA = PolynomialStructureData.initialTNASNA();
    public static final PolynomialStructure AK135_elastic = PolynomialStructureData.initialAK135_elastic();
    public static final PolynomialStructure MAK135 = PolynomialStructureData.initialMAK135();
    public static final PolynomialStructure PREM_PRIME = PolynomialStructureData.initialPREM_PRIME();

    /**
     * true if default structure. False if user-defined structure
     */
    private boolean isDefault = true;
    /**
     * the number of layers
     */
    private int nzone;
    /**
     * Number of zones of cores.
     */
    private int coreZone = 2; // TODO
    private double[] rmin;
    private double[] rmax;
    private PolynomialFunction[] rho;
    private PolynomialFunction[] vpv;
    private PolynomialFunction[] vph;
    private PolynomialFunction[] vsv;
    private PolynomialFunction[] vsh;
    private PolynomialFunction[] eta;
    private double[] qMu;
    private double[] qKappa;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: model file.");
        Path path = Paths.get(args[0]);
        PolynomialStructure ps = new PolynomialStructure(path);
        ps.printValues(1);
    }

    private void printValues(double deltaR) {
        System.out.println("#r \u03C1 Vpv Vph Vsv Vsh \u03B7 Q\u03BC Q\u03BA");
        for (int izone = 0; izone < nzone; izone++) {
            for (double r = rmin[izone]; r < rmax[izone]; r += deltaR)
                GadgetAid.println(r, getRhoAt(r), getVpvAt(r), getVphAt(r), getVsvAt(r), getVshAt(r), getEtaAt(r),
                        getQmuAt(r), getQkappaAt(r));
            double r1 = rmax[izone];
            GadgetAid.println(r1, getRhoAt(r1), getVpvAt(r1), getVphAt(r1), getVsvAt(r1), getVshAt(r1), getEtaAt(r1),
                    getQmuAt(r1), getQkappaAt(r1));
        }
    }

    /**
     * @param modelName
     * @return
     *
     * @author otsuru
     * @since 2022/2/5 moved from inside run() in SyntheticDSMSetup
     */
    public static PolynomialStructure of(String modelName) {
        PolynomialStructure ps = null;

        // PREM_3600_RHO_3 : PREM is a 3% rho (density) discontinuity at radius 3600 km
        if (!modelName.contains("/") && modelName.contains("_")) {
            System.err.println("Using " + modelName + ". Adding perturbations");
            String[] ss = modelName.split("_");
            modelName = ss[0];
            String[] range = ss[1].split("-");
            double r1 = Double.parseDouble(range[0]);
            double r2 = Double.parseDouble(range[1]);
            Map<String, Double> quantityPercentMap = new HashMap<>();
            for (int i = 2; i < ss.length; i++) {
                String[] quantity_percent = ss[i].split("-");
                double percent = quantity_percent[1].startsWith("M") ? -1 * Double.parseDouble(quantity_percent[1].substring(1)) / 100.
                        : Double.parseDouble(quantity_percent[1]) / 100.;
                quantityPercentMap.put(quantity_percent[0], percent);
            }
            if (modelName.equals("MIASP91")) {
                ps = PolynomialStructure.MIASP91;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else if (modelName.equals("PREM")) {
                ps = PolynomialStructure.PREM;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else if (modelName.equals("AK135")) {
                ps = PolynomialStructure.AK135;
                for (String quantity : quantityPercentMap.keySet()) {
                    System.err.println("Adding " + quantity + " " + quantityPercentMap.get(quantity)*100 + "% discontinuity");
                    if (quantity.equals("RHO"))
                        ps = ps.addRhoDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                    else if (quantity.equals("VS"))
                        ps = ps.addVsDiscontinuity(r1, r2, quantityPercentMap.get(quantity));
                }
            }
            else
                throw new RuntimeException("Model not implemented yet");
        }
        else {
            switch (modelName) {
            case "PREM":
                System.err.println("Using PREM");
                ps = PolynomialStructure.PREM;
                break;
            case "AK135":
                System.err.println("Using AK135");
                ps = PolynomialStructure.AK135;
                break;
            case "AK135-ELASTIC":
                System.err.println("Using AK135 elastic");
                ps = PolynomialStructure.AK135_elastic;
                break;
            case "MIASP91":
                System.err.println("Using MIASP91");
                ps = PolynomialStructure.MIASP91;
                break;
            case "IPREM":
                System.err.println("Using IPREM");
                ps = PolynomialStructure.ISO_PREM;
                break;
            case "TNASNA":
                System.err.println("Using TNASNA");
                ps = PolynomialStructure.TNASNA;
                break;
            case "TBL50":
                System.err.println("Using TBL50");
                ps = PolynomialStructure.TBL50;
                break;
            case "MAK135":
                System.err.println("Using MAK135");
                ps = PolynomialStructure.MAK135;
                break;
            default:
                throw new RuntimeException("Model not implemented yet");
            }
        }

        return ps;
    }

    private PolynomialStructure() {
    }

    /**
     * @param structurePath {@link Path} of a
     * @throws IOException if an I/O error occurs. A structure file (structurePath) must
     *                     exist.
     */
    public PolynomialStructure(Path structurePath) throws IOException {
        readStructureFile(structurePath);
    }

    static PolynomialStructure set(int nzone, double[] rmin, double[] rmax, double[][] rho, double[][] vpv,
                                           double[][] vph, double[][] vsv, double[][] vsh, double[][] eta, double[] qMu,
                                           double[] qKappa) {
        PolynomialStructure structure = new PolynomialStructure();
        structure.nzone = nzone;
        structure.rmin = rmin;
        structure.rmax = rmax;

        structure.rho = new PolynomialFunction[nzone];
        structure.vpv = new PolynomialFunction[nzone];
        structure.vph = new PolynomialFunction[nzone];
        structure.vsv = new PolynomialFunction[nzone];
        structure.vsh = new PolynomialFunction[nzone];
        structure.eta = new PolynomialFunction[nzone];
        structure.qMu = qMu;
        structure.qKappa = qKappa;

        for (int i = 0; i < nzone; i++) {
            structure.rho[i] = new PolynomialFunction(rho[i]);
            structure.vpv[i] = new PolynomialFunction(vpv[i]);
            structure.vph[i] = new PolynomialFunction(vph[i]);
            structure.vsv[i] = new PolynomialFunction(vsv[i]);
            structure.vsh[i] = new PolynomialFunction(vsh[i]);
            structure.eta[i] = new PolynomialFunction(eta[i]);
        }

        return structure;
    }

    @Override
    public PolynomialStructure clone() {
        PolynomialStructure ps = new PolynomialStructure();
        ps.nzone = this.nzone;
        ps.rmin = this.rmin;
        ps.rmax = this.rmax;
        ps.rho = this.rho;
        ps.vpv = this.vpv;
        ps.vph = this.vph;
        ps.vsv = this.vsv;
        ps.vsh = this.vsh;
        ps.eta = this.eta;
        ps.qMu = this.qMu;
        ps.qKappa = this.qKappa;
        return ps;
    }

    private PolynomialStructure deepCopy() {
        PolynomialStructure structure = new PolynomialStructure();
        structure.nzone = nzone;
        structure.coreZone = coreZone;
        structure.rmin = rmin.clone();
        structure.rmax = rmax.clone();
        structure.rho = rho.clone();
        structure.vpv = vpv.clone();
        structure.vph = vph.clone();
        structure.vsv = vsv.clone();
        structure.vsh = vsh.clone();
        structure.eta = eta.clone();
        structure.qMu = qMu.clone();
        structure.qKappa = qKappa.clone();
        return structure;
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
        PolynomialStructure other = (PolynomialStructure) obj;
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

    /**
     * nzoneにしたがって、半径情報や速度情報を初期化する
     */
    private void initialize() {
        // System.out.println("Initializing polinomial structure"
        // + " components by nzone " + nzone);
        if (nzone < 1)
            throw new IllegalStateException("nzone is invalid.");
        rmin = new double[nzone];
        rmax = new double[nzone];
        rho = new PolynomialFunction[nzone];
        vpv = new PolynomialFunction[nzone];
        vph = new PolynomialFunction[nzone];
        vsv = new PolynomialFunction[nzone];
        vsh = new PolynomialFunction[nzone];
        eta = new PolynomialFunction[nzone];
        qMu = new double[nzone];
        qKappa = new double[nzone];
    }

    /**
     * Add boundaries at the input radii.
     * if there is already a boundary at r then nothing will be done.
     *
     * @param radii radii for boundaries. Values smaller than 0 or bigger than
     *              earth radius will be ignored
     * @return a new structure which have additional layers at the input
     * boundaries or this if there all the radiuses already exist in
     * this
     */
    public PolynomialStructure addBoundaries(double... boundaries) {
        PolynomialStructure ps = new PolynomialStructure();
        double[] addBoundaries = Arrays.stream(boundaries)
                .filter(d -> 0 < d && d < rmax[nzone - 1] && Arrays.binarySearch(rmin, d) < 0).distinct().sorted()
                .toArray();
        if (addBoundaries.length == 0)
            return this;
        ps.nzone = nzone + addBoundaries.length;
        ps.initialize();
        ps.rmin = DoubleStream.concat(Arrays.stream(rmin), Arrays.stream(addBoundaries)).sorted().toArray();
        ps.rmax = DoubleStream.concat(Arrays.stream(rmax), Arrays.stream(addBoundaries)).sorted().toArray();
        ps.coreZone += Arrays.stream(addBoundaries).filter(r -> r < rmin[coreZone]).count();

        for (int iZone = 0; iZone < ps.nzone; iZone++) {
            double rmin = ps.rmin[iZone];
            // izone in this for rmin
            int oldIZone = zoneOf(rmin);
            // copy
            ps.qMu[iZone] = qMu[oldIZone];
            ps.qKappa[iZone] = qKappa[oldIZone];
            ps.rho[iZone] = rho[oldIZone];
            ps.vpv[iZone] = vpv[oldIZone];
            ps.vph[iZone] = vph[oldIZone];
            ps.vsv[iZone] = vsv[oldIZone];
            ps.vsh[iZone] = vsh[oldIZone];
            ps.eta[iZone] = eta[oldIZone];
        }
        return ps;
    }

    public PolynomialStructure addRhoDiscontinuity(double r1, double r2, double percent) {
        PolynomialStructure ps = this.clone();
        boolean foundR1 = false;
        boolean foundR2 = false;
        for (double r : ps.rmin) {
            if (Math.abs(r1-r) < 1e-6)
                foundR1 = true;
            if (Math.abs(r2-r) < 1e-6)
                foundR2 = true;
        }
        if (!foundR1)
            ps = ps.addBoundaries(r1);
        if (!foundR2)
            ps = ps.addBoundaries(r2);
        int izoneR1 = ps.zoneOf(r1);
        int izoneR2 = ps.zoneOf(r2);
        double value = ps.getRhoAt(r2);
        double increment = value * percent;
        PolynomialFunction p0 = new PolynomialFunction(new double[] {increment});
        for (int izone = izoneR1; izone < izoneR2; izone++)
            ps.rho[izone] = ps.rho[izone].add(p0);
        return ps;
    }

    public PolynomialStructure addVsDiscontinuity(double r1, double r2, double percent) {
        PolynomialStructure ps = this.clone();
        boolean foundR1 = false;
        boolean foundR2 = false;
        for (double r : ps.rmin) {
            if (Math.abs(r1-r) < 1e-6)
                foundR1 = true;
            if (Math.abs(r2-r) < 1e-6)
                foundR2 = true;
        }
        if (!foundR1)
            ps = ps.addBoundaries(r1);
        if (!foundR2)
            ps = ps.addBoundaries(r2);
        int izoneR1 = ps.zoneOf(r1);
        int izoneR2 = ps.zoneOf(r2);
        double value = ps.computeVs(r2);
        double increment = value * percent;
        PolynomialFunction p0 = new PolynomialFunction(new double[] {increment});
        for (int izone = izoneR1; izone < izoneR2; izone++) {
            ps.vsv[izone] = ps.vsv[izone].add(p0);
            ps.vsh[izone] = ps.vsh[izone].add(p0);
        }
        return ps;
    }

    /**
     * x = r / earth radius
     *
     * @param r [km] radius
     * @return a value x to the input r for polynomial functions
     */
    private double toX(double r) {
        return r / rmax[nzone - 1];
    }

    /**
     * @param r [km] radius [0, rmax]
     * @return the number of the zone which includes r. Note that the zone will
     * be rmin &le; r &lt; rmax except r = earth radius
     */
    public int zoneOf(double r) {
        if (r == rmax[nzone - 1])
            return nzone - 1;
        return IntStream.range(0, nzone).filter(i -> rmin[i] <= r && r < rmax[i]).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input r:" + r + "is invalid."));
    }

    /**
     * The numbers of radii and values must be same.
     *
     * @param n      order of polynomial function. 0: constant, 1:linear function, 2:quadratic, 3: cubic
     * @param radii  [km] radius
     * @param values values
     * @return PolynomialFunction for the input
     */
    public PolynomialFunction createFunction(int n, double[] radii, double[] values) {
        if (radii.length != values.length)
            throw new IllegalArgumentException("radii and values must have a same number of points.");
        if (radii.length <= n) throw new IllegalArgumentException("The number of input points must be over n.");
        double[] x = Arrays.stream(radii).map(this::toX).toArray();
        Trace trace = new Trace(x, values);
        return trace.toPolynomial(n);
    }

    /**
     * i-th layer is merged to (i-1)th layer.
     * in other words, ith boundary is gone.
     * values in the i-th layer becomes those of the (i-1)th layer.
     * So far you can not merge a layer beneath CMB.
     *
     * @param i index of a layer which disappears after the merge
     * @return slimmed Structure
     */
    public PolynomialStructure mergeLayer(int i) {
        if (i <= coreZone) throw new IllegalArgumentException("Cannot merge layers in the core.");
        if (nzone - 2 < i) throw new IllegalArgumentException("Input i must be less than " + (nzone - 1));
        UnaryOperator<double[]> one =
                old -> IntStream.range(0, nzone).filter(j -> j != i).mapToDouble(j -> old[j]).toArray();
        Function<PolynomialFunction[], double[][]> two =
                old -> IntStream.range(0, nzone).filter(j -> j != i).mapToObj(j -> old[j].getCoefficients())
                        .toArray(double[][]::new);
        double[] rmin = one.apply(this.rmin);
        double[] rmax = one.apply(this.rmax);
        rmax[i - 1] = this.rmax[i];
        double[][] rho = two.apply(this.rho);
        double[][] vpv = two.apply(this.vpv);
        double[][] vph = two.apply(this.vph);
        double[][] vsv = two.apply(this.vsv);
        double[][] vsh = two.apply(this.vsh);
        double[][] eta = two.apply(this.eta);
        double[] qMu = one.apply(this.qMu);
        double[] qKappa = one.apply(this.qKappa);
        return set(nzone - 1, rmin, rmax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa);
    }

    /**
     * structureLines must not have comment lines and must have only structure
     * lines. structureLines must have been trimmed.
     *
     * @param structureLines lines for a structure
     */
    private void readLines(String[] structureLines) {
        nzone = Integer.parseInt(structureLines[0].split("\\s+")[0]);
        if (structureLines.length != (nzone * 6 + 1))
            throw new IllegalArgumentException("Invalid lines");
        initialize();
        for (int i = 0; i < nzone; i++) {
            String[] rangeRhoParts = structureLines[i * 6 + 1].split("\\s+");
            String[] vpvParts = structureLines[i * 6 + 2].split("\\s+");
            String[] vphParts = structureLines[i * 6 + 3].split("\\s+");
            String[] vsvParts = structureLines[i * 6 + 4].split("\\s+");
            String[] vshParts = structureLines[i * 6 + 5].split("\\s+");
            String[] etaParts = structureLines[i * 6 + 6].split("\\s+");
            rmin[i] = Double.parseDouble(rangeRhoParts[0]);
            rmax[i] = Double.parseDouble(rangeRhoParts[1]);
            double[] rho = new double[4];
            double[] vpv = new double[4];
            double[] vph = new double[4];
            double[] vsv = new double[4];
            double[] vsh = new double[4];
            double[] eta = new double[4];
            for (int j = 0; j < 4; j++) {
                rho[j] = Double.parseDouble(rangeRhoParts[j + 2]);
                vpv[j] = Double.parseDouble(vpvParts[j]);
                vph[j] = Double.parseDouble(vphParts[j]);
                vsv[j] = Double.parseDouble(vsvParts[j]);
                vsh[j] = Double.parseDouble(vshParts[j]);
                eta[j] = Double.parseDouble(etaParts[j]);
            }
            this.rho[i] = new PolynomialFunction(rho);
            this.vpv[i] = new PolynomialFunction(vpv);
            this.vph[i] = new PolynomialFunction(vph);
            this.vsv[i] = new PolynomialFunction(vsv);
            this.vsh[i] = new PolynomialFunction(vsh);
            this.eta[i] = new PolynomialFunction(eta);
            qMu[i] = Double.parseDouble(etaParts[4]);
            qKappa[i] = Double.parseDouble(etaParts[5]);
        }
    }

    /**
     * Read a structure file
     *
     * @param structurePath {@link Path} of a structureFile
     */
    private void readStructureFile(Path structurePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(structurePath, false);
        readLines(reader.getNonCommentLines());
        isDefault = false;
        System.err.println(structurePath + " read in.");
    }

    public void readStructureFile(List<String> lines) throws IOException {
        InformationFileReader reader = new InformationFileReader(lines, false);
        readLines(reader.getNonCommentLines());
        isDefault = false;
    }

    public String[] toPSVlines() {
        String[] outString = new String[6 * (nzone) + 7];
        outString[0] = String.valueOf(nzone) + " nzone";
        outString[1] = "c  - Radius(km) -     --- Density (g/cm^3)---";
        outString[2] = "c                     ---   Vpv     (km/s) ---";
        outString[3] = "c                     ---   Vph     (km/s) ---";
        outString[4] = "c                     ---   Vsv     (km/s) ---";
        outString[5] = "c                     ---   Vsh     (km/s) ---";
        outString[6] = "c                     ---   eta     (ND  ) ---             - Qmu -  - Qkappa -";
        for (int i = 0; i < nzone; i++) {
            outString[6 * i + 7] = rmin[i] + " " + rmax[i] + " " + toLine(rho[i]);
            outString[6 * i + 8] = toLine(vpv[i]);
            outString[6 * i + 9] = toLine(vph[i]);
            outString[6 * i + 10] = toLine(vsv[i]);
            outString[6 * i + 11] = toLine(vsh[i]);
            outString[6 * i + 12] = toLine(eta[i]) + " " + qMu[i] + " " + qKappa[i];
        }
        return outString;
    }

    public String[] toSHlines() {
        int zone = nzone - coreZone;
        String[] outString = new String[3 * zone + 4];
        outString[0] = zone + " nzone";
        outString[1] = "c  --- Radius(km) ---  --- Density (g/cm^3) ---";
        outString[2] = "c                      ---   Vsv     (km/s) ---";
        outString[3] = "c                      ---   Vsh     (km/s) ---      - Qmu -";
        for (int i = coreZone; i < nzone; i++) {
            outString[3 * (i - coreZone) + 4] = rmin[i] + " " + rmax[i] + " " + toLine(rho[i]);
            outString[3 * (i - coreZone) + 5] = toLine(vsv[i]);
            outString[3 * (i - coreZone) + 6] = toLine(vsh[i]) + " " + qMu[i];
        }
        return outString;
    }

    /**
     * change String line from coefficients a + bx + cx**2 >>>>> a b c 0
     *
     * @param pf polynomial function for a layer
     * @return string in a form of this
     */
    private static String toLine(PolynomialFunction pf) {
        return Arrays.stream(Arrays.copyOf(pf.getCoefficients(), 4)).mapToObj(Double::toString)
                .collect(Collectors.joining(" "));
    }

    /**
     * @param outPath {@link Path} of an write file.
     * @param options for writing
     * @throws IOException if any
     */
    public void writePSV(Path outPath, OpenOption... options) throws IOException {
        Files.write(outPath, Arrays.asList(toPSVlines()), options);
    }

    /**
     * @param outPath {@link Path} of an write file.
     * @param options for write
     * @throws IOException if any
     */
    public void writeSH(Path outPath, OpenOption... options) throws IOException {
        Files.write(outPath, Arrays.asList(toSHlines()), options);
    }

    /**
     *
     * @param outPath {@link Path} of an output file
     * @throws IOException
     */
    public void writeVelocity(Path outPath) throws IOException {
        Files.deleteIfExists(outPath);
        Files.createFile(outPath);
        for (int i = 0; i < 10000; i++) {
            double r = i * 6371./10000.;
            double vsh = getVshAt(r);
            double vsv = getVsvAt(r);
            double vph = getVphAt(r);
            double vpv = getVpvAt(r);
            Files.write(outPath, (r + " " + vpv + " " + vph + " " + vsv + " " + vsh + " " + "\n").getBytes(), StandardOpenOption.APPEND);
        }
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for &rho; in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setRho(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.rho[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>pv</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVpv(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vpv[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>ph</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVph(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vph[izone] = polynomialFunction;
        return str;
    }

     /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>sv</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVsv(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vsv[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>sh</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVsh(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vsh[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>pv</sub> and V<sub>ph</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVp(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vph[izone] = polynomialFunction;
        str.vpv[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for V<sub>sv</sub> and V<sub>sh</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setVs(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.vsh[izone] = polynomialFunction;
        str.vsv[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone              index of the target zone
     * @param polynomialFunction replace the function for &eta; in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setEta(int izone, PolynomialFunction polynomialFunction) {
        PolynomialStructure str = deepCopy();
        str.eta[izone] = polynomialFunction;
        return str;
    }

    /**
     * @param izone index of the target zone
     * @param qMu   replace q<sub>&mu;</sub> in the ith zone to it
     * @return new structure
     */
    public PolynomialStructure setQMu(int izone, double qMu) {
        PolynomialStructure str = deepCopy();
        str.qMu[izone] = qMu;
        return str;
    }

    /**
     * A = &rho;V<sub>PH</sub><sup>2</sup>
     *
     * @param r [km] radius
     * @return the parameter A under TI approx.
      * @deprecated use {@link #getMediumAt(double)}
    */
    public double computeA(double r) {
        double vph = getVphAt(r);
        return getRhoAt(r) * vph * vph;
    }

    /**
     * C = &rho;V<sub>PV</sub><sup>2</sup>
     *
     * @param r [km] radius
     * @return the parameter C under TI approximation.
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeC(double r) {
        double vpv = getVpvAt(r);
        return getRhoAt(r) * vpv * vpv;
    }

    /**
     * F = &eta;(A-2*L)
     *
     * @param r [km]
     * @return the parameter F under TI approx.
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeF(double r) {
        return computeEta(r) * (computeA(r) - 2 * computeL(r));
    }

    /**
     * L = &rho;V<sub>SV</sub><sup>2</sup>
     *
     * @param r [km]
     * @return the parameter L under TI approx.
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeL(double r) {
        double vsv = getVsvAt(r);
        return getRhoAt(r) * vsv * vsv;
    }

    /**
     * N = &rho;V<sub>SH</sub><sup>2</sup>
     *
     * @param r [km]
     * @return the parameter N under TI approx.
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeN(double r) {
        double v = getVshAt(r);
        return getRhoAt(r) * v * v;
    }

    public double computeEta(double r) {
        return eta[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r
     *            [km] radius
     * @return &xi; (N/L)
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeXi(double r) {
        return computeN(r) / computeL(r);
    }

    /**
     * @param r [km] radius
     * @return &mu; computed by Vs * Vs * &rho;
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeMu(double r) {
        double v = computeVs(r);
        return v * v * getRhoAt(r);
    }

    /**
     * @param r
     * @return &kappa; bulk modulus
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeKappa(double r) {
        return computeLambda(r) + 2./3. * computeMu(r);
    }

    /**
     * @param r [km] radius
     * @return &lambda; computed by Vs * Vs * &rho;
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeLambda(double r) {
        double v = getVphAt(r);
        return getRhoAt(r) * v * v - 2 * computeMu(r);
    }

    /**
     * (2L+N)/(3&rho;)
     *
     * @param r
     *            [km] radius
     * @return effective isotropic shear wave velocity
     * @deprecated use {@link #getMediumAt(double)}
     */
    public double computeVs(double r) {
        return Math.sqrt((2 * computeL(r) + computeN(r)) / 3 / getRhoAt(r));
    }

    /**
     * Get elastic medium at a given radius.
     * @param r
     * @return
     *
     * @author otsuru
     * @since 2022/4/11
     */
    public ElasticMedium getMediumAt(double r) {
        ElasticMedium medium = new ElasticMedium();
        medium.set(ParameterType.RHO, rho[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.Vpv, vpv[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.Vph, vph[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.Vsv, vsv[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.Vsh, vsh[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.ETA, eta[zoneOf(r)].value(toX(r)));
        medium.set(ParameterType.Qmu, qMu[zoneOf(r)]);
        medium.set(ParameterType.Qkappa, qKappa[zoneOf(r)]);
        return medium;
    }

    /**
     * Get value of various parameters.
     * They are calculated as follows:
     * <ul>
     * <li> A = &rho;V<sub>PH</sub><sup>2</sup> </li>
     * <li> C = &rho;V<sub>PV</sub><sup>2</sup> </li>
     * <li> F = &eta;(A-2*L) </li>
     * <li> L = &rho;V<sub>SV</sub><sup>2</sup> </li>
     * <li> N = &rho;V<sub>SH</sub><sup>2</sup> </li>
     * <li> &xi; = N/L </li>
     * <li> Vs = (2L+N)/(3&rho;)  (effective isotropic shear wave velocity)</li>
     * <li> &mu; = Vs * Vs * &rho; </li>
     * <li> &lambda; = Vp * Vp * &rho; - 2 &mu; </li>
     * <li> &kappa; = &lambda; + 2/3 &mu; (bulk modulus) </li>
     * <li> Vb = sqrt(&kappa; / &rho;) </li>
     * </ul>
     * @param type (ParameterType) the type of parameter to obtain
     * @param r (double) radius [km]
     * @return
     *
     * @author otsuru
     * @since 2022/4/11
     */
    public double getAtRadius(ParameterType type, double r) {
        switch(type) {
        case RHO:
            return rho[zoneOf(r)].value(toX(r));
        // TI
        case Vpv:
            return vpv[zoneOf(r)].value(toX(r));
        case Vph:
            return vph[zoneOf(r)].value(toX(r));
        case Vsv:
            return vsv[zoneOf(r)].value(toX(r));
        case Vsh:
            return vsh[zoneOf(r)].value(toX(r));
        case ETA:
            return eta[zoneOf(r)].value(toX(r));
/*        case A:
            double vph = getAtRadius(ParameterType.Vph, r);
            return getAtRadius(ParameterType.RHO, r) * vph * vph;
        case C:
            double vpv = getAtRadius(ParameterType.Vpv, r);
            return getAtRadius(ParameterType.RHO, r) * vpv * vpv;
        case F:
            return getAtRadius(ParameterType.ETA, r) * (getAtRadius(ParameterType.A, r) - 2 * getAtRadius(ParameterType.L, r));
        case L:
            double vsv = getAtRadius(ParameterType.Vsv, r);
            return getAtRadius(ParameterType.RHO, r) * vsv * vsv;
        case N:
            double vsh = getAtRadius(ParameterType.Vsh, r);
            return getAtRadius(ParameterType.RHO, r) * vsh * vsh;
        case XI:
            return getAtRadius(ParameterType.N, r) / getAtRadius(ParameterType.L, r);
        // iso
        case Vp:
            return getAtRadius(ParameterType.Vph, r); //TODO is this OK?
        case Vs:
            return Math.sqrt((2 * getAtRadius(ParameterType.L, r) + getAtRadius(ParameterType.N, r)) / 3 / getAtRadius(ParameterType.RHO, r)); //TODO where does this come from?
        case Vb:
            return Math.sqrt(getAtRadius(ParameterType.KAPPA, r) / getAtRadius(ParameterType.RHO, r));
        case LAMBDA:
            return getAtRadius(ParameterType.LAMBDA2MU, r) - 2 * getAtRadius(ParameterType.MU, r);
        case MU:
            double vs = getAtRadius(ParameterType.Vs, r);
            return getAtRadius(ParameterType.RHO, r) * vs * vs;
        case LAMBDA2MU:
            double vp = getAtRadius(ParameterType.Vp, r);
            return getAtRadius(ParameterType.RHO, r) * vp * vp;
        case KAPPA:
            return getAtRadius(ParameterType.LAMBDA, r) + 2./3. * getAtRadius(ParameterType.MU, r);
*/
        // Q
        case Qmu:
            return qMu[zoneOf(r)];
        case Qkappa:
            return qKappa[zoneOf(r)];
        default:
            return getMediumAt(r).get(type);
//            throw new IllegalArgumentException("Illegal parameter type");
        }
    }

    public double getTransverselyIsotropicValue(TransverselyIsotropicParameter ti, double r) {
        switch (ti) {
        case A:
            return computeA(r);
        case C:
            return computeC(r);
        case ETA:
            return computeEta(r);
        case F:
            return computeF(r);
        case L:
            return computeL(r);
        case N:
            return computeN(r);
        default:
            throw new RuntimeException();
        }

    }

    /**
     * @return number of zones
     */
    public int getNzone() {
        return nzone;
    }

    /**
     * @return Number of core zones.
     */
    public int getCoreZone() {
        return coreZone;
    }

    /**
     * @param izone index of the zone
     * @return minimum radius of the zone
     */
    public double getRMinOf(int izone) {
        return rmin[izone];
    }

    /**
     * @param izone index of a zone
     * @return maximum radius of the zone
     */
    public double getRMaxOf(int izone) {
        return rmax[izone];
    }

    /**
     * @param izone index of a zone
     * @return polynomial function for &rho; of the zone
     */
    public PolynomialFunction getRhoOf(int izone) {
        return rho[izone];
    }

    /**
     * @param izone index of a zone
     * @return polynomial function for V<sub>PV</sub> of the zone
     */
    public PolynomialFunction getVpvOf(int izone) {
        return vpv[izone];
    }

    /**
     * @param izone index of a zone
     * @return polynomial function for V<sub>PH</sub> of the zone
     */
    public PolynomialFunction getVphOf(int izone) {
        return vph[izone];
    }

    /**
     * @param izone index of a zone
     * @return polynomial function for V<sub>SV</sub> of the zone
     */
    public PolynomialFunction getVsvOf(int izone) {
        return vsv[izone];
    }

    /**
     * @param izone index of a zone
     * @return polynomial function for V<sub>SH</sub> of the zone
     */
    public PolynomialFunction getVshOf(int izone) {
        return vsh[izone];
    }

    /**
     * @param i index of a zone
     * @return Q<sub>&mu;</sub> of the zone
     */
    public double getQMuOf(int i) {
        return qMu[i];
    }

    /**
     * @param r [km] radius
     * @return &rho; at the radius r
     */
    public double getRhoAt(double r) {
        return rho[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r km] radius
     * @return V<sub>PV</sub> at the radius r
     */
    public double getVpvAt(double r) {
        return vpv[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r [km] radius
     * @return V<sub>PH</sub> at the radius r
     */
    public double getVphAt(double r) {
        return vph[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r [km] radius
     * @return V<sub>SV</sub> at the radius r
     */
    public double getVsvAt(double r) {
        return vsv[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r [km] radius
     * @return V<sub>SH</sub> at the radius r
     */
    public double getVshAt(double r) {
        return vsh[zoneOf(r)].value(toX(r));
    }

    public double getVbAt(double r) {
        double vsh = getVshAt(r);
        double vph = getVphAt(r);
        return Math.sqrt(vph * vph - 4./3. * vsh * vsh);
    }

    /**
     * @param r [km] radius
     * @return &eta; at the radius r
     */
    public double getEtaAt(double r) {
        return eta[zoneOf(r)].value(toX(r));
    }

    /**
     * @param r [km] radius
     * @return Q<sub>&mu;</sub> at the radius r
     */
    public double getQmuAt(double r) {
        return qMu[zoneOf(r)];
    }

    /**
     * @param r [km] radius
     * @return Q<sub>&kappa;</sub> at the radius r
     */
    public double getQkappaAt(double r) {
        return qKappa[zoneOf(r)];
    }

    /**
     * @return true if default structure (already implemented), false if user-defined structure
     */
    public boolean isDefault() {
        return isDefault;
    }
}
