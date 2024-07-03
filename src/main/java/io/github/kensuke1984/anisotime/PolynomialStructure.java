package io.github.kensuke1984.anisotime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.LinearEquation;

/**
 * Polynomial structure.
 * Outer-core must have a value of Q<sub>&mu;</sub> =-1
 *
 * @author Kensuke Konishi, Anselme Borgeaud
 * @version 0.1.3
 */
public class PolynomialStructure implements VelocityStructure {

    /**
     * Transversely isotropic (TI) PREM by Dziewonski &amp; Anderson 1981
     */
    public static final PolynomialStructure PREM =
            new PolynomialStructure(io.github.kensuke1984.kibrary.util.earth.PolynomialStructure.of("PREM"));
    /**
     * isotropic PREM by Dziewonski &amp; Anderson 1981
     */
    public static final PolynomialStructure ISO_PREM =
            new PolynomialStructure(io.github.kensuke1984.kibrary.util.earth.PolynomialStructure.of("IPREM"));
    /**
     * AK135 by Kennett <i>et al</i>. (1995)
     */
    public static final PolynomialStructure AK135 =
            new PolynomialStructure(io.github.kensuke1984.kibrary.util.earth.PolynomialStructure.of("AK135"));
    /**
     * Homogeneous structure used for test purposes
     */
    public static final PolynomialStructure HOMOGEN =
            new PolynomialStructure(io.github.kensuke1984.kibrary.util.earth.PolynomialStructure.of("HOMOGEN"));
    /**
     * 2019/12/7
     */
    private static final long serialVersionUID = -7292410325252292009L;

    private final io.github.kensuke1984.kibrary.util.earth.PolynomialStructure STRUCTURE;
    /*
     * -radius x this is only for computations for bouncing points.
     */
    private final PolynomialFunction RADIUS_SUBTRACTION;

    public PolynomialStructure(io.github.kensuke1984.kibrary.util.earth.PolynomialStructure structure) {
        STRUCTURE = checkBoundaries(structure);
        if (!STRUCTURE.isDefault() && !checkStructure())
            throw new RuntimeException(
                    "The structure must have strictly positive velocity and density,"
                    + "except for vsh=vsv=0 in the outer-core.");
        RADIUS_SUBTRACTION = new PolynomialFunction(new double[]{0, -earthRadius()});
    }

    private io.github.kensuke1984.kibrary.util.earth.PolynomialStructure checkBoundaries(
            io.github.kensuke1984.kibrary.util.earth.PolynomialStructure structure) {
        double[] dBoundaries = IntStream.range(1, structure.getNZone()).mapToDouble(i -> structure.getRmin()[i])
                .filter(r -> isDBoundary(r, structure)).toArray();
        io.github.kensuke1984.kibrary.util.earth.PolynomialStructure newStructure = structure;
        double earthRadius = structure.getRmax()[structure.getNZone() - 1];
        for (double boundary : dBoundaries) {
            newStructure = newStructure.replaceDBoundary(boundary, D_BOUNDARY_ZONE);
//            newStructure = newStructure.withBoundaries(boundary - D_BOUNDARY_ZONE, boundary + D_BOUNDARY_ZONE);
//            newStructure = newStructure.mergeLayer(newStructure.zoneOf(boundary));
//            int izone = newStructure.zoneOf(boundary);
//            //rho
//            newStructure = newStructure.setFunction(VariableType.RHO, izone,
//                    computeReplacement(boundary, earthRadius, newStructure.getRho()[izone],
//                            newStructure.getRho()[izone + 1]));
//            //Vpv
//            newStructure = newStructure.setFunction(VariableType.Vpv, izone,
//                    computeReplacement(boundary, earthRadius, newStructure.getVpv()[izone],
//                            newStructure.getVpv()[izone + 1]));
//            //Vph
//            newStructure = newStructure.setFunction(VariableType.Vph, izone,
//                    computeReplacement(boundary, earthRadius, newStructure.getVph()[izone],
//                            newStructure.getVph()[izone + 1]));
//            //Vsv
//            newStructure = newStructure.setFunction(VariableType.Vsv, izone,
//                    computeReplacement(boundary, earthRadius, newStructure.getVsv()[izone],
//                            newStructure.getVsv()[izone + 1]));
//            //Vsh
//            newStructure = newStructure.setFunction(VariableType.Vsh, izone,
//                    computeReplacement(boundary, earthRadius, newStructure.getVsh()[izone],
//                            newStructure.getVsh()[izone + 1]));
        }
        return newStructure;
    }

    /**
     * Check if vpv, vph, vsv, vsh, and rho are strictly positive over all depths,
     * excepts for the outer core for vsh and vsv, where it checks if vsh=vsv=0
     * @return true if check passed, else false
     */
    private boolean checkStructure() {
        if (IntStream.range(0, (int) earthRadius()).filter(r -> r >= coreMantleBoundary() && r < innerCoreBoundary())
                .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vsh, r)).anyMatch(v -> v <= 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius()).filter(r -> r < coreMantleBoundary() && r > innerCoreBoundary())
                    .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vsh, r)).anyMatch(v -> v != 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius()).filter(r -> r >= coreMantleBoundary() && r < innerCoreBoundary())
                    .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vsv, r)).anyMatch(v -> v <= 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius()).filter(r -> r < coreMantleBoundary() && r > innerCoreBoundary())
                .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vsv, r)).anyMatch(v -> v != 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius())
                    .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vph, r)).anyMatch(v -> v <= 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius())
                    .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.Vpv, r)).anyMatch(v -> v <= 0 )) return false;
        else if (IntStream.range(0, (int) earthRadius())
                    .mapToDouble(r -> STRUCTURE.getAtRadius(VariableType.RHO, r)).anyMatch(v -> v <= 0 )) return false;
        return true;
    }

    /**
     * TODO
     *
     * @param boundary      [km] radius of boundary
     * @param earthRadius   [km] earth radius
     * @param lowerFunction polynomial function at the layer beneath the boundary
     * @param upperFunction polynomial function at the layer above the boundary
     * @return function to be replaced with the upper one
     */
    private PolynomialFunction computeReplacement(double boundary, double earthRadius, PolynomialFunction lowerFunction,
                                                  PolynomialFunction upperFunction) {
        double xLower = (boundary - D_BOUNDARY_ZONE) / earthRadius;
        //double xBoundary = boundary / earthRadius;
        //double boundaryValue = lowerFunction.value(xBoundary);
        double lowerValue = lowerFunction.value(xLower);
        double xUpper = (boundary + D_BOUNDARY_ZONE) / earthRadius;
        double upperValue = upperFunction.value(xUpper);
        double a = (upperValue - lowerValue) / (xUpper - xLower);
        double b = upperValue - a * xUpper;
        return new PolynomialFunction(new double[]{b, a});
    }

    /**
     * @param r         radius to be checked
     * @param structure structure to be checked
     * @return Whether the boundary is D boundary.
     * When the functions (of velocities and density) in the upper and lower boundaries are identical, this method returns false.
     */
    private static boolean isDBoundary(double r,
                                       io.github.kensuke1984.kibrary.util.earth.PolynomialStructure structure) {
        double rPlus = r + ComputationalMesh.EPS;
        double rMinus = r - ComputationalMesh.EPS;
        int upperZone = structure.zoneOf(rPlus);
        int lowerZone = structure.zoneOf(rMinus);
        // check whether the functions are identical.
        Function<VariableType, Boolean> compare =
                variable -> structure.getFunctionIn(variable, upperZone).equals(structure.getFunctionIn(variable, lowerZone));
        //Function<IntFunction<PolynomialFunction>, Boolean> compare =
        //        ic -> ic.apply(upperZone).equals(ic.apply(lowerZone));
        if (compare.apply(VariableType.Vph) && compare.apply(VariableType.Vpv) &&
                compare.apply(VariableType.Vsh) && compare.apply(VariableType.Vsv) &&
                compare.apply(VariableType.RHO)) return false;
        // check whether the boundary is D boundary
        double criterion = 1 - MAXIMUM_RATIO_OF_D_BOUNDARY / 100;
        ToDoubleFunction<DoubleUnaryOperator> toRatio = compute -> {
            double ratio = compute.applyAsDouble(rPlus) / compute.applyAsDouble(rMinus);
            return ratio < 1 ? ratio : 1 / ratio;
        };
        Function<VariableType, DoubleUnaryOperator> getOperater =
                ti -> a -> structure.mediumAt(a).get(ti);
        return !(toRatio.applyAsDouble(getOperater.apply(VariableType.A)) < criterion ||
                toRatio.applyAsDouble(getOperater.apply(VariableType.C)) < criterion ||
                toRatio.applyAsDouble(getOperater.apply(VariableType.F)) < criterion ||
                toRatio.applyAsDouble(getOperater.apply(VariableType.L)) < criterion ||
                toRatio.applyAsDouble(getOperater.apply(VariableType.N)) < criterion ||
                toRatio.applyAsDouble(getOperater.apply(VariableType.RHO)) < criterion);
    }

    /**
     * index of D-Boundary for rmin
     */
    private int[] rMinIndexOfDBoundary;

    public PolynomialStructure(Path path) throws IOException {
        this(io.github.kensuke1984.kibrary.util.earth.PolynomialStructureFile.read(path));
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((STRUCTURE == null) ? 0 : STRUCTURE.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PolynomialStructure other = (PolynomialStructure) obj;
        if (STRUCTURE == null) return other.STRUCTURE == null;
        else return STRUCTURE.equals(other.STRUCTURE);
    }

    /**
     * @param i  zone number for the search
     * @param eq equation to solve
     * @return [km] turningR in the i-th zone or -1 if no valid R in the i-th zone
     */
    private double findTurningR(int i, LinearEquation eq) {
        double minR = STRUCTURE.getRmin()[i];
        double maxR = STRUCTURE.getRmax()[i];
        int ansType = eq.Discriminant();
        Complex[] answer = eq.compute();
        if (ansType == 1) {
            double radius = answer[0].getReal() * earthRadius();
            return minR <= radius && radius < maxR ? radius : -1;
        }

        if (ansType < 19) return -1;

        if (ansType == 20 || ansType == 28 || ansType == 29 || ansType == 30) {
            double radius = answer[0].getReal() * earthRadius();
            return minR <= radius && radius < maxR ? radius : -1;
        }

        return Arrays.stream(answer).map(a -> a.getReal() * earthRadius()).sorted(Comparator.reverseOrder())
                .filter(x -> minR <= x && x < maxR).findFirst().orElse(-1d);
    }

    @Override
    public double earthRadius() {
        return STRUCTURE.getRmax()[STRUCTURE.getNZone() - 1];
    }

    @Override
    public double innerCoreBoundary() {
        return STRUCTURE.getRmin()[
                IntStream.range(0, STRUCTURE.getNZone()).filter(i -> STRUCTURE.getQMu()[i] < 0).min().getAsInt()];
    }

    @Override
    public double coreMantleBoundary() {
        return STRUCTURE.getRmax()[
                IntStream.range(0, STRUCTURE.getNZone()).filter(i -> STRUCTURE.getQMu()[i] < 0).max().getAsInt()];

    }

    @Override
    public double[] velocityBoundaries() {
        double[] boundaries = new double[STRUCTURE.getNZone() + 1];
        for (int i = 0; i < boundaries.length - 1; i++)
            boundaries[i] = STRUCTURE.getRmin()[i];
        boundaries[boundaries.length - 1] = earthRadius();
        return boundaries;
    }

    @Override
    public double pTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; -1 < i; i--) {
            PolynomialFunction pvr = STRUCTURE.getVph()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (coreMantleBoundary() <= r) return r;
        }
        return Double.NaN;
    }

    @Override
    public double iTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; -1 < i; i--) {
            if (innerCoreBoundary() < STRUCTURE.getRmin()[i]) continue;
            PolynomialFunction pvr = STRUCTURE.getVph()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (0 <= r && r <= innerCoreBoundary()) return r;
        }
        return Double.NaN;
    }

    @Override
    public double svTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; i > -1; i--) {
            PolynomialFunction pvr = STRUCTURE.getVsv()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (coreMantleBoundary() <= r) return r;
        }
        return Double.NaN;
    }

    @Override
    public double shTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; -1 < i; i--) {
            PolynomialFunction pvr = STRUCTURE.getVsh()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (coreMantleBoundary() <= r) return r;
        }
        return Double.NaN;
    }

    @Override
    public double jvTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; i > -1; i--) {
            if (innerCoreBoundary() < STRUCTURE.getRmin()[i]) continue;
            PolynomialFunction pvr = STRUCTURE.getVsv()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (0 <= r && r <= innerCoreBoundary()) return r;
        }
        return Double.NaN;
    }

    @Override
    public double jhTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; -1 < i; i--) {
            PolynomialFunction pvr = STRUCTURE.getVsh()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (0 <= r && r <= innerCoreBoundary()) return r;
        }
        return Double.NaN;
    }

    @Override
    public double kTurningR(double p) {
        PolynomialFunction pFunction = new PolynomialFunction(new double[]{p});
        for (int i = STRUCTURE.getNZone() - 1; -1 < i; i--) {
            if (coreMantleBoundary() < STRUCTURE.getRmin()[i]) continue;
            PolynomialFunction pvr = STRUCTURE.getVph()[i].multiply(pFunction).add(RADIUS_SUBTRACTION); // pv-r=0
            LinearEquation eq = new LinearEquation(pvr);
            double r = findTurningR(i, eq);
            if (innerCoreBoundary() < r && r < coreMantleBoundary()) return r;
        }
        return Double.NaN;
    }

    @Override
    public double getRho(double r) {
        return STRUCTURE.getAtRadius(VariableType.RHO, r);
    }

    @Override
    public double getA(double r) {
        return STRUCTURE.mediumAt(r).get(VariableType.A);
    }

    @Override
    public double getC(double r) {
        return STRUCTURE.mediumAt(r).get(VariableType.C);
    }

    @Override
    public double getF(double r) {
        return STRUCTURE.mediumAt(r).get(VariableType.F);
    }

    @Override
    public double getL(double r) {
        return STRUCTURE.mediumAt(r).get(VariableType.L);
    }

    @Override
    public double getN(double r) {
        return STRUCTURE.mediumAt(r).get(VariableType.N);
    }

}
