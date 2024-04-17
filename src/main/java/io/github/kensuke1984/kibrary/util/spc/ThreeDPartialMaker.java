package io.github.kensuke1984.kibrary.util.spc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.util.SpcFileAid;
import io.github.kensuke1984.kibrary.util.earth.DefaultStructure;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure_old;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Create a partial derivative waveform from one forward propagation and one backward propagation.
 * <p>
 * U<sub>j,q</sub> C<sub>jqrs</sub> &eta;<sub>ri,s</sub>
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class ThreeDPartialMaker {

    /**
     * forward propagation
     */
    private final SPCFileAccess fp;
    private final SPCFileAccess fp2;
    private final SPCFileAccess fp3;
    /**
     * back propagation
     */
    private final SPCFileAccess bp;
    private final SPCFileAccess bp2;
    private final SPCFileAccess bp3;
    /**
     * Sampling frequency [Hz].
     */
    private final double samplingHz;
    /**
     * Number of data points in time domain.
     */
    private final int npts;

    /**
     * distances for interpolation
     */
    double[] dh;
    double[] dhFP;
    /**
     * 摂動点における bpからのテンソルをfpのテンソルに合わせるために回転させる角度
     */
    private double angleForTensor;
    /**
     *
     * 偏微分波形成分を震源観測点大円上の座標に合わせる角度
     */
    private double angleForVector;
    private FujiConversion fujiConversion;
    private SourceTimeFunction sourceTimeFunction;
    Set<Double> ignoreBodyR;

     /**
     * 用いたいspcファイルたちと ヘッダーに加えたい情報
     *
     * @param fp a spc file for forward propagation
     * @param bp a spc file for back propagation
     */
    public ThreeDPartialMaker(SPCFileAccess fp, SPCFileAccess bp, double samplingHz) {
        ignoreBodyR = new HashSet<>();
        if (!isGoodPair(fp, bp))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.err::println);
        this.fp = fp;
        this.bp = bp;
        this.bp2 = null;
        this.bp3 = null;
        this.fp2 = null;
        this.fp3 = null;
        this.dh = null;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }

    /**
     * Used for BP/FP catalog
     * @param fp
     * @param bp1
     * @param bp2
     * @param bp3
     * @param dh
     * @author anselme
     */
    public ThreeDPartialMaker(SPCFileAccess fp, SPCFileAccess bp1, SPCFileAccess bp2, SPCFileAccess bp3, double[] dh, double samplingHz) {
        ignoreBodyR = new HashSet<>();
        if (!isGoodPair(fp, bp1))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp, bp2))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp, bp3))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.err::println);
        this.fp = fp;
        this.fp2 = null;
        this.fp3 = null;
        this.bp = bp1;
        this.bp2 = bp2;
        this.bp3 = bp3;
        this.dh = dh;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }

    /**
     * Used for BP/FP catalog
     * @param fpSH
     * @param fpPSV
     * @param bp1SH
     * @param bp1PSV
     * @param bp2SH
     * @param bp2PSV
     * @param bp3SH
     * @param bp3PSV
     * @param dh
     * @author anselme
     */
    public ThreeDPartialMaker(SPCFileAccess fpSH, SPCFileAccess fpPSV, SPCFileAccess bp1SH,
            SPCFileAccess bp1PSV, SPCFileAccess bp2SH, SPCFileAccess bp2PSV, SPCFileAccess bp3SH, SPCFileAccess bp3PSV, double[] dh, double samplingHz) {
        ignoreBodyR = new HashSet<>();
        if (!isGoodPair(fpSH, bp1SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fpSH, bp2SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fpSH, bp3SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.out::println);

        this.fp = fpSH;
        this.bp = bp1SH;
        this.bp2 = bp2SH;
        this.bp3 = bp3SH;
//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        for (int i = 0; i < fpSH.nbody(); i++) {
            SPCBody body = fpPSV.getSpcBodyList().get(i).copy();
            this.fp.getSpcBodyList().get(i).addBody(body);
        }

//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        for (int i = 0; i < bp1SH.nbody(); i++) {
            SPCBody body = bp1PSV.getSpcBodyList().get(i).copy();
            this.bp.getSpcBodyList().get(i).addBody(body);

            SPCBody body2 = bp2PSV.getSpcBodyList().get(i).copy();
            this.bp2.getSpcBodyList().get(i).addBody(body2);

            SPCBody body3 = bp3PSV.getSpcBodyList().get(i).copy();
            this.bp3.getSpcBodyList().get(i).addBody(body3);
        }
//		System.out.println("PSV and SH added");

        this.fp2 = null;
        this.fp3 = null;
        this.dh = dh;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }


    /**
     * Used for BP/FP catalog
     * @param fp1
     * @param fp2
     * @param fp3
     * @param bp1
     * @param bp2
     * @param bp3
     * @param dhBP
     * @param dhFP
     * @author anselme
     */
    public ThreeDPartialMaker(SPCFileAccess fp1, SPCFileAccess fp2, SPCFileAccess fp3, SPCFileAccess bp1, SPCFileAccess bp2, SPCFileAccess bp3, double[] dhBP, double[] dhFP, double samplingHz) {
        ignoreBodyR = new HashSet<>();
        if (!isGoodPair(fp1, bp1))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp2, bp2))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp3, bp3))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.out::println);
        this.fp = fp1;
        this.fp2 = fp2;
        this.fp3 = fp3;
        this.bp = bp1;
        this.bp2 = bp2;
        this.bp3 = bp3;
        this.dh = dhBP;
        this.dhFP = dhFP;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }


    /**
     * Used for BP/FP catalog
     * @param fp1PSV
     * @param fp1SH
     * @param fp2PSV
     * @param fp2SH
     * @param fp3PSV
     * @param fp3SH
     * @param bp1PSV
     * @param bp1SH
     * @param bp2PSV
     * @param bp2SH
     * @param bp3PSV
     * @param bp3SH
     * @param dhBP
     * @param dhFP
     * @author anselme
     */
    public ThreeDPartialMaker(SPCFileAccess fp1PSV, SPCFileAccess fp1SH, SPCFileAccess fp2PSV,  SPCFileAccess fp2SH, SPCFileAccess fp3PSV, SPCFileAccess fp3SH,
            SPCFileAccess bp1PSV, SPCFileAccess bp1SH, SPCFileAccess bp2PSV, SPCFileAccess bp2SH, SPCFileAccess bp3PSV, SPCFileAccess bp3SH, double[] dhBP, double[] dhFP, double samplingHz) {
        ignoreBodyR = new HashSet<>();
        if (!isGoodPair(fp1SH, bp1SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp2SH, bp2SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        if (!isGoodPair(fp3SH, bp3SH))
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.out::println);

        this.fp = fp1PSV;
        this.fp2 = fp2PSV;
        this.fp3 = fp3PSV;
        this.bp = bp1PSV;
        this.bp2 = bp2PSV;
        this.bp3 = bp3PSV;
//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        for (int i = 0; i < fp1PSV.nbody(); i++) {
            SPCBody body = fp1SH.getSpcBodyList().get(i).copy();
            this.fp.getSpcBodyList().get(i).addBody(body);

            SPCBody body2 = fp2SH.getSpcBodyList().get(i).copy();
            this.fp2.getSpcBodyList().get(i).addBody(body2);

            SPCBody body3 = fp3SH.getSpcBodyList().get(i).copy();
            this.fp3.getSpcBodyList().get(i).addBody(body3);
        }

//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        for (int i = 0; i < bp1PSV.nbody(); i++) {
            SPCBody body = bp1SH.getSpcBodyList().get(i).copy();
            this.bp.getSpcBodyList().get(i).addBody(body);

            SPCBody body2 = bp2SH.getSpcBodyList().get(i).copy();
            this.bp2.getSpcBodyList().get(i).addBody(body2);

            SPCBody body3 = bp3SH.getSpcBodyList().get(i).copy();
            this.bp3.getSpcBodyList().get(i).addBody(body3);
        }

        this.dh = dhBP;
        this.dhFP = dhFP;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }

    /**
     * Used for BP/FP catalog
     * @param fpSH
     * @param fpPSV
     * @param bpSH
     * @param bpPSV
     * @author anselme
     */
    public ThreeDPartialMaker(SPCFileAccess fpSH, SPCFileAccess fpPSV, SPCFileAccess bpSH, SPCFileAccess bpPSV, double samplingHz) {
        ignoreBodyR = new HashSet<>();

        this.fp = fpPSV;
        this.bp = bpPSV;

//		System.out.println(fpSH.getSpcFileName() + " " + fpPSV.getSpcFileName() + " " + bpSH.getSpcFileName() + " " + bpPSV.getSpcFileName());

//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);

        for (int i = 0; i < fpPSV.nbody(); i++) {
            SPCBody body = fpSH.getSpcBodyList().get(i).copy();
            this.fp.getSpcBodyList().get(i).addBody(body);
        }

//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);

        for (int i = 0; i < bpPSV.nbody(); i++) {
            SPCBody body = bpSH.getSpcBodyList().get(i).copy();
            this.bp.getSpcBodyList().get(i).addBody(body);
        }
//		System.out.println("PSV and SH added");

        if (!isGoodPair(fp, bp)) //isGoodPair
            throw new RuntimeException("An input pair of forward and backward propagation is invalid.");
        ignoreBodyR.forEach(System.err::println);

        this.bp2 = null;
        this.bp3 = null;
        this.fp2 = null;
        this.fp3 = null;
        this.dh = null;
        this.samplingHz = samplingHz;
        npts = SpcFileAid.findNpts(bp.tlen(), samplingHz);
        setAngles();
    }

    /**
     * Check whether the pair of fp and bp is valid for making partials.
     *
     * @param fp forward propagation
     * @param bp backward propagation
     * @return (boolean) Whether the pair of fp and bp is valid for making partials.
     */
    private static boolean isGoodPair(SPCFileAccess fp, SPCFileAccess bp) {
        boolean validity = true;

        if (fp.nbody() != bp.nbody()) {
            System.err.println("nbodies are different. fp, bp: " + fp.nbody() + " ," + bp.nbody());
            validity = false;
        }
        if (validity) {
            double[] fpR = fp.getBodyR();
            double[] bpR = bp.getBodyR();
            validity = Arrays.equals(fpR, bpR);
            if (!validity) {
                System.err.println("the depths are invalid(different) as below  fp : bp");
                for (int i = 0; i < fpR.length; i++)
                    System.err.println(fpR[i] + " : " + bpR[i]);
            }
        }
        if (fp.omegai() != bp.omegai()) {
            System.err.println("Omegais are different. fp, bp: " + fp.omegai() + ", " + bp.omegai());
            validity = false;
        }
        if (fp.np() != bp.np()) {
            System.err.println("nps are different. fp, bp: " + fp.np() + ", " + bp.np());
            validity = false;
        }
        if (fp.tlen() != bp.tlen()) {
            System.err.println("tlens are different. fp, bp: " + fp.tlen() + " ," + bp.tlen());
            validity = false;
        }

        // check if voxel IDs are same
//        if (!(fp.getReceiverID().equals(bp.getReceiverID()))) {
//            System.err.println(
//                    "Perturbation points are different fp, bp: " + fp.getReceiverID() + " ," + bp.getReceiverID());
//            validity = false;
//        }

        // check if voxel positions are same
        if (!fp.getReceiverPosition().equals(bp.getReceiverPosition())) {
            System.err.print("Voxel positions are different: ");
            System.err.println("(" + fp.getReceiverPosition().getLatitude() + ", "
                    + fp.getReceiverPosition().getLongitude() + "), (" + bp.getReceiverPosition().getLatitude() + ", "
                    + bp.getReceiverPosition().getLongitude() + ")");
            validity = false;
        }

        return validity;
    }

    //TODO what is this for? Is this needed for FP catalog?
    // If this is unneeded, delete. If this is needed, merge with isGoodPermissive() above. 2023/8/27 otsuru
    /**
     * @param fp
     * @param bp
     * @return
     * @author anselme
     */
    private boolean isGoodPairPermissive(SPCFileAccess fp, SPCFileAccess bp) {
        boolean validity = true;
        if (Math.abs(fp.nbody() - bp.nbody()) > 2) {
            System.err.println("nbodies are different by more than 2. fp, bp: " + fp.nbody() + " ," + bp.nbody());
            validity = false;
        }
        if (validity) {
            double[] fpR = fp.getBodyR();
            double[] bpR = bp.getBodyR();
            for (int i = 0; i < fpR.length; i++) {
                double fpRi = fpR[i];
                boolean isInBpR = false;
                for (int j = 0; j < bpR.length; j++) {
                    if (fpRi == bpR[j]) {
                        isInBpR = true;
                        break;
                    }
                }
                if (!isInBpR)
                    ignoreBodyR.add(fpRi);
            }

//			Set<Double> fpRSet = Arrays.stream(fpR).boxed().collect(Collectors.toSet());
//			Set<Double> bpRSet = Arrays.stream(bpR).boxed().collect(Collectors.toSet());
//			Set<Double> bpRSet_copy = new HashSet<>(bpRSet);
//			bpRSet.removeAll(fpRSet);
//			fpRSet.removeAll(bpRSet_copy);
//			ignoreBodyR.addAll(bpRSet);
//			ignoreBodyR.addAll(fpRSet);
//			ignoreBodyR.forEach(r -> System.out.println(r));

            validity = ignoreBodyR.size() <= 2;
            if (!validity) {
                System.err.println("the depths are invalid (different) as below  fp : bp");
                for (int i = 0; i < fpR.length; i++)
                    System.err.println(fpR[i] + " : " + bpR[i]);
            }
        }
        if (fp.omegai() != bp.omegai()) {
            System.err.println("Omegais are different. fp, bp: " + fp.omegai() + ", " + bp.omegai());
            validity = false;
        }

        if (fp.np() != bp.np()) {
            System.err.println("nps are different. fp, bp: " + fp.np() + ", " + bp.np());
            validity = false;
        }
        // tlen
        if (fp.tlen() != bp.tlen()) {
            System.err.println("tlens are different. fp, bp: " + fp.tlen() + " ," + bp.tlen());
            validity = false;
        }
        // check if voxel IDs are same
        if (!(fp.getReceiverID().equals(bp.getReceiverID()))) {
            System.err.println(
                    "Perturbation points are different. fp, bp: " + fp.getReceiverID() + " ," + bp.getReceiverID());
            validity = false;
        }

        // check if voxel positions are same
        if (!fp.getReceiverPosition().equals(bp.getReceiverPosition())) {
            System.err.println("perturbation point Positions are different.");
            System.err.println("perturbation point of fp, bp are" + "(" + fp.getReceiverPosition().getLatitude() + ", "
                    + fp.getReceiverPosition().getLongitude() + "), (" + bp.getReceiverPosition().getLatitude() + ", "
                    + bp.getReceiverPosition().getLongitude() + ")");
            validity = false;
        }
        return validity;
    }

    /**
     * Create a {@link SPCFileAccess} from a forward propagation and a
     * backward propagation.
     *
     * @param type {@link PartialType}
     * @return partial spectrum file
     */
    public SPCFileAccess toSpectrum(PartialType type) {
        SPCFileName spcFileName = bp.getSpcFileName();
        double tlen = bp.tlen();
        int np = bp.np();
        int nbody = bp.nbody();
        double omegai = bp.omegai();
        HorizontalPosition observerPosition = bp.getSourcePosition();
        String observerID = bp.getSourceID();
        FullPosition sourceLocation = fp.getSourcePosition();
        String sourceID = fp.getSourceID();
        double[] bodyR = bp.getBodyR();
        List<SPCBody> spcBodyList = new ArrayList<>(nbody);
        for (int ibody = 0; ibody < nbody; ibody++) {
            TensorCalculationUCE tensorcalc = new TensorCalculationUCE(fp.getSpcBodyList().get(ibody),
                    bp.getSpcBodyList().get(ibody), type.getWeightingFactor(), angleForTensor, true);
            // tensorcalc.setBP(angleBP);
            // tensorcalc.setFP(angleFP);
             System.out.println("angleForTensor " + angleForTensor);
            Complex[] partialZ = tensorcalc.calc(0); // frequency domain Z
            Complex[] partial1 = tensorcalc.calc(1); // R
            Complex[] partial2 = tensorcalc.calc(2); // T

            Complex[] partialR = rotatePartial(partial1, partial2, SACComponent.R);
            Complex[] partialT = rotatePartial(partial1, partial2, SACComponent.T);
            SPCBody body = new SPCBody(3, np);
            for (int ip = 0; ip < bp.np() + 1; ip++)
                body.setValues(ip, partialZ[ip], partialR[ip], partialT[ip]);
            spcBodyList.add(body);
        }
        return new SPCFileAccess() {

            @Override
            public double tlen() {
                return tlen;
            }

            @Override
            public double omegai() {
                return omegai;
            }

            @Override
            public int np() {
                return np;
            }

            @Override
            public int nbody() {
                return nbody;
            }

            @Override
            public SPCType getSpcFileType() {
                return type.toSpcFileType();
            }

            @Override
            public List<SPCBody> getSpcBodyList() {
                return spcBodyList;
            }

            @Override
            public FullPosition getSourcePosition() {
                return sourceLocation;
            }

            @Override
            public String getSourceID() {
                return sourceID;
            }

            @Override
            public HorizontalPosition getReceiverPosition() {
                return observerPosition;
            }

            @Override
            public String getReceiverID() {
                return observerID;
            }

            @Override
            public double[] getBodyR() {
                return bodyR;
            }

            @Override
            public SPCFileName getSpcFileName() {
                return spcFileName;
            }

            @Override
            public void setSpcBody(int i, SPCBody body) {
//				spcBody.set(i, body); TODO
            }
        };
    }

    /**
     * ibody番目のボディ（深さ）に対する摂動の Partial derivatives のiに対する成分 ETAri,s の i
     *
     * @param component {@link SACComponent}
     * @param iBody     index for SacBody
     * @param type      {@link PartialType}
     * @return Ui(t) u[t] 時間領域
     * @author Kensuke Konishi
     * @author anselme return array of zero for partials whose radius is too close to the BP or FP source
     */
    public double[] createPartial(SACComponent component, int iBody, PartialType type) {
        // return array of zero for partials whose radius is too close to the BP or FP source
        double bpR = bp.getBodyR()[iBody];
        double fpR = fp.getBodyR()[iBody];
        if (fpR != bpR)
            throw new RuntimeException("Unexpected: fp and bp rBody differ " + fpR + " " + bpR);

        long t1i = System.currentTimeMillis();
        Complex[] partial_frequency = type == PartialType.Q3D ? computeQpartial(component, iBody)
                : computeTensorCulculus(component, iBody, iBody, type);
        long t1f = System.currentTimeMillis();
        System.out.println("Tensor multiplication finished in " + (t1f - t1i)*1e-3 + " s");

        if (null != sourceTimeFunction)
            partial_frequency = sourceTimeFunction.convolve(partial_frequency);
        //test tapper
//		partial_frequency = rightTapper(partial_frequency); //TODO
        long t2i = System.currentTimeMillis();
        Complex[] partial_time = SpcFileAid.convertToTimeDomain(partial_frequency, fp.np(), npts, samplingHz, fp.omegai());
        double[] partialdouble = new double[npts];
        for (int j = 0; j < npts; j++)
            partialdouble[j] = partial_time[j].getReal();
        long t2f = System.currentTimeMillis();
        System.out.println("iFFT finished in " + (t2f - t2i)*1e-3 + " s");
        return partialdouble;
    }

    /**
     * return array of zero for partials whose radius is too close to the BP or FP source
     * @param component
     * @param iBody
     * @param type
     * @return
     * @author anselme
     */
    public double[] createPartialSerial(SACComponent component, int iBody, PartialType type) {
        // return array of zero for partials whose radius is too close to the BP or FP source
        // fp, bp is SPCFileAccess
        double bpR = bp.getBodyR()[iBody];
        double fpR = fp.getBodyR()[iBody];
        if (fpR != bpR)
            throw new RuntimeException("Unexpected: fp and bp rBody differ " + fpR + " " + bpR);

//        long t1i = System.currentTimeMillis();
        Complex[] partial_frequency = type == PartialType.Q3D ? computeQpartial(component, iBody)
                : computeTensorCulculusSerial(component, iBody, iBody, type);
//        long t1f = System.currentTimeMillis();
//		System.out.println("Tensor multiplication finished in " + (t1f - t1i)*1e-3 + " s");

        if (null != sourceTimeFunction)
            partial_frequency = sourceTimeFunction.convolveSerial(partial_frequency);

        //test tapper
        partial_frequency = rightTapper(partial_frequency); //TODO

//        long t2i = System.currentTimeMillis();
        Complex[] partial_time = SpcFileAid.convertToTimeDomain(partial_frequency, fp.np(), npts, samplingHz, fp.omegai());
        double[] partialdouble = new double[npts];
        for (int j = 0; j < npts; j++)
            partialdouble[j] = partial_time[j].getReal();
//        long t2f = System.currentTimeMillis();
//		System.out.println("iFFt finished in " + (t2f - t2i)*1e-3 + " s");
//		Arrays.stream(partial_time).mapToDouble(Complex::abs).toArray();
        return partialdouble;
    }

    /**
     * return array of zero for partials whose radius is too close to the BP or FP source
     * @param component
     * @param iBody
     * @param type
     * @return
     * @author anselme
     */
    public Complex[] createPartialFrequencySerial(SACComponent component, int iBody, PartialType type) {
        double bpR = bp.getBodyR()[iBody];
        double fpR = fp.getBodyR()[iBody];
        if (fpR != bpR)
            throw new RuntimeException("Unexpected: fp and bp rBody differ " + fpR + " " + bpR);

        long t1i = System.currentTimeMillis();
        Complex[] partial_frequency = type == PartialType.Q3D ? computeQpartial(component, iBody)
                : computeTensorCulculusSerial(component, iBody, iBody, type);
        long t1f = System.currentTimeMillis();
//		System.out.println("Tensor multiplication finished in " + (t1f - t1i)*1e-3 + " s");

        if (null != sourceTimeFunction)
            partial_frequency = sourceTimeFunction.convolveSerial(partial_frequency);

        //test tapper
        partial_frequency = rightTapper(partial_frequency); //TODO

        return partial_frequency;
    }

    /**
     * The structure is used for computation Q
     *
     * @param structure {@link PolynomialStructure_old}
     */
    public void setStructure(PolynomialStructure structure) {
        fujiConversion = new FujiConversion(structure);
    }

    private Complex[] computeQpartial(SACComponent component, int iBody) {
        if (fujiConversion == null)
            fujiConversion = new FujiConversion(DefaultStructure.PREM);
        SPCFileAccess qspec = fujiConversion.convert(toSpectrum(PartialType.MU3D));
        return qspec.getSpcBodyList().get(iBody).getSpcElement(component).getValueInFrequencyDomain();

    }

    /**
     * compute tensor culculus of u Cijkl eta
     *
     * @param component {@link SACComponent}
     * @param iBody     index for sacbody
     * @param type      {@link PartialType}
     * @return uCe
     */
    private Complex[] computeTensorCulculus(SACComponent component, int iBody, PartialType type) {
        TensorCalculationUCE tensorcalc = new TensorCalculationUCE(fp.getSpcBodyList().get(iBody),
                bp.getSpcBodyList().get(iBody), type.getWeightingFactor(), angleForTensor, true);
        return component == SACComponent.Z ? tensorcalc.calc(0)
                : rotatePartial(tensorcalc.calc(1), tensorcalc.calc(2), component);
    }

    /**
     * Used for BP/FP catalog
     * @param component
     * @param iBodyBp
     * @param iBodyFp
     * @param type
     * @return
     * @author anselme
     */
    private Complex[] computeTensorCulculus(SACComponent component, int iBodyBp, int iBodyFp, PartialType type) {
        SPCBody bpBody = null;
        SPCBody fpBody = null;
        if (bp2 == null) {
            bpBody = bp.getSpcBodyList().get(iBodyBp);
            fpBody = fp.getSpcBodyList().get(iBodyFp);
//			System.err.println("No interpolation performed");
//			System.out.println("DEBUG BP noInterp: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
        }
        else if (fp2 == null) {
            bpBody = SPCBody.interpolate(bp.getSpcBodyList().get(iBodyBp)
                    , bp2.getSpcBodyList().get(iBodyBp), bp3.getSpcBodyList().get(iBodyBp), dh);
            fpBody = fp.getSpcBodyList().get(iBodyFp);
//			System.out.println("DEBUG BP: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
        }
        else {
            bpBody = SPCBody.interpolate(bp.getSpcBodyList().get(iBodyBp)
                    , bp2.getSpcBodyList().get(iBodyBp), bp3.getSpcBodyList().get(iBodyBp), dh);
            fpBody = SPCBody.interpolate(fp.getSpcBodyList().get(iBodyFp)
                    , fp2.getSpcBodyList().get(iBodyFp), fp3.getSpcBodyList().get(iBodyFp), dhFP);
//			System.out.println("DEBUG BP: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
//			System.out.println("DEBUG FP: " +  fpBody.getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        }
//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        TensorCalculationUCE tensorcalc = new TensorCalculationUCE(fpBody,
                bpBody, type.getWeightingFactor(), angleForTensor, true);
        return component == SACComponent.Z ? tensorcalc.calc(0)
                : rotatePartial(tensorcalc.calc(1), tensorcalc.calc(2), component);
    }

    /**
     * @param component
     * @param iBodyBp
     * @param iBodyFp
     * @param type
     * @return
     * @author anselme
     */
    private Complex[] computeTensorCulculusSerial(SACComponent component, int iBodyBp, int iBodyFp, PartialType type) {
        SPCBody bpBody = null;
        SPCBody fpBody = null;
        if (bp2 == null) {
            bpBody = bp.getSpcBodyList().get(iBodyBp);
            fpBody = fp.getSpcBodyList().get(iBodyFp);
//			System.err.println("No interpolation performed");
//			System.out.println("DEBUG BP noInterp: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
        }
        else if (fp2 == null) {
            bpBody = SPCBody.interpolate(bp.getSpcBodyList().get(iBodyBp)
                    , bp2.getSpcBodyList().get(iBodyBp), bp3.getSpcBodyList().get(iBodyBp), dh);
            fpBody = fp.getSpcBodyList().get(iBodyFp);

            //TODO
           // if (fp.getObserverID().equals("XY100") && iBodyBp == 0 && iBodyFp == 0) {
           //     System.err.println(component + " " + type);
           //     for (int k = 0; k < bpBody.getNumberOfComponent(); k++)
           //         System.err.println("DEBUG BP: " +  bpBody.getSpcComponent(k).getValueInFrequencyDomain()[512]);
           //     System.err.println();
           // }
//			System.out.println("DEBUG BP: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
        }
        else {
            bpBody = SPCBody.interpolate(bp.getSpcBodyList().get(iBodyBp)
                    , bp2.getSpcBodyList().get(iBodyBp), bp3.getSpcBodyList().get(iBodyBp), dh);
            fpBody = SPCBody.interpolate(fp.getSpcBodyList().get(iBodyFp)
                    , fp2.getSpcBodyList().get(iBodyFp), fp3.getSpcBodyList().get(iBodyFp), dhFP);
//			System.out.println("DEBUG BP: " +  bpBody.getSpcComponents()[20].getValueInFrequencyDomain()[10]);
//			System.out.println("DEBUG FP: " +  fpBody.getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        }
//		System.out.println(fp.getSpcBodyList().get(0).getSpcComponents()[8].getValueInFrequencyDomain()[10]);
        if (type.isDensity()) {
            double tlen = bp.tlen();
            TensorCalculationURhoE tensorcalc = new TensorCalculationURhoE(fpBody, bpBody, angleForTensor, tlen);
            return component == SACComponent.Z ? tensorcalc.calc(0)
                    : rotatePartial(tensorcalc.calc(1), tensorcalc.calc(2), component);
        }
        else {
            TensorCalculationUCE tensorcalc = new TensorCalculationUCE(fpBody,
                    bpBody, type.getWeightingFactor(), angleForTensor, false);
            return component == SACComponent.Z ? tensorcalc.calc(0)
                    : rotatePartial(tensorcalc.calc(1), tensorcalc.calc(2), component);
        }
    }

    /**
     * @param sourceTimeFunction ({@link SourceTimeFunction}) Source time function to use. Set this null when none is to be applied.
     */
    public void setSourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        this.sourceTimeFunction = sourceTimeFunction;
    }

    /**
     * 周波数領域のデータにしか使えない
     *
     * @param partial1  partial in local cartesian
     * @param partial2  partial in local cartesian
     * @param component R, T 震源 観測点の乗る大円上
     * @return 回転させてできたi成分の偏微分波形
     */
    private Complex[] rotatePartial(Complex[] partial1, Complex[] partial2, SACComponent component) {
        Complex[] partial = new Complex[fp.np() + 1];

        double cosine = FastMath.cos(angleForVector);
        double sine = FastMath.sin(angleForVector);

        switch (component) {
        case R:
            for (int j = 0; j < fp.np() + 1; j++)
                partial[j] = new Complex(
                        cosine * partial1[j].getReal()
                                + sine * partial2[j].getReal(),
                        cosine * partial1[j].getImaginary()
                                + sine * partial2[j].getImaginary());
            return partial;
        case T:
            for (int j = 0; j < fp.np() + 1; j++)
                partial[j] = new Complex(
                        -sine * partial1[j].getReal()
                                + cosine * partial2[j].getReal(),
                        -sine * partial1[j].getImaginary()
                                + cosine * partial2[j].getImaginary());
            return partial;
        default:
            System.out.println(Thread.currentThread().getStackTrace()[1].getMethodName());
            System.out.println("an input component is invalid");
            return null;
        }

    }

    /**
     * 座標軸回転に必要な角度の計算 Z軸を中心に angleForTensor：
     * bpの（ローカル座標）を回してfpのテンソルに合わせる（Zは一致しているため） angleForVector 得られたｆiに対する応答を回転する
     * （北極に持って行って、東西南北ベースに力を入れているのでそれを大円内に戻す）
     */
    private void setAngles() {
        HorizontalPosition event = fp.getSourcePosition();
        HorizontalPosition station = bp.getSourcePosition();
        HorizontalPosition point = bp.getReceiverPosition();
        angleForTensor = Earth.computeAzimuthRad(point, station) - Earth.computeAzimuthRad(point, event);

        angleForVector = 2 * Math.PI - Earth.computeAzimuthRad(station, event);

//		System.out.println(event + " " + station + " " + point);
//		System.out.println(angleForTensor*180/Math.PI + " " + angleForVector*180/Math.PI);
    }

    /**
     * @param complex
     * @return
     * @author anselme
     */
    private Complex[] rightTapper(Complex[] complex) {
        Complex[] tappered = complex.clone();
        int l = complex.length;
        int n = l / 5;

        for (int i = 0; i < n; i++) {
//			tappered[i + l - n] = tappered[i + l - n].multiply(FastMath.cos(Math.PI / (2 * (n - 1)) * i));
            tappered[i + l - n] = tappered[i + l - n].multiply(1. - (double) i / (n - 1.));
        }

        return tappered;
    }

}
