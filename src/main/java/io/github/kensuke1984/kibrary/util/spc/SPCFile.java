package io.github.kensuke1984.kibrary.util.spc;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Spectrum file by DSM.
 *
 * @version 0.1.2
 * @author Kensuke Konishi
 * @author anselme add content for BP/FP catalog
 */
public class SPCFile implements SPCFileAccess {

    private String sourceID;
    private String receiverID;
    private SPCFileName spcFileName;
    private double tlen;
    private int np;

    /**
     * 震源の位置
     */
    private FullPosition sourcePosition;
    /**
     * 観測点の位置 深さの情報は含まない
     */
    private HorizontalPosition receiverPosition;
    private double omegai;
    private List<SPCBody> spcBody;
    private int nbody;
    private int nComponent;
    private double[] bodyR;
    private SPCType spcFileType;

    public SPCFile(SPCFileName spcFileName) {
        this.spcFileName = spcFileName;
    }

    /**
     * Interpolation for BP/FP catalog
     * @param bp1
     * @param bp2
     * @param bp3
     * @param dh
     * @return
     * @author anselme
     */
    public static SPCFile interpolate(SPCFile bp1, SPCFile bp2, SPCFile bp3, double[] dh) {
        SPCFile bp = bp1;
        for (int ibody = 0; ibody < bp1.nbody; ibody++) {
            SPCBody body = SPCBody.interpolate(bp1.spcBody.get(ibody), bp2.spcBody.get(ibody), bp3.spcBody.get(ibody), dh);
            bp.spcBody.set(ibody, body);
        }

        return bp;
    }

    /**
     * If the named file does not exist, is a directory rather than a regular
     * file, or for some other reason cannot be opened for reading then a
     * {@link IOException} is thrown.
     *
     * @param spcFileName must exist.
     * @return Spectrum of the spcFileName
     * @throws IOException If the spcFileName does not exist, or an I/O error occurs
     * @author Kensuke Konishi
     * @author anselme add content for BP/FP catalog
     * @author rei add content for UB/UF catalog
     */
    public static final SPCFile getInstance(SPCFileName spcFileName, double phi, HorizontalPosition receiverPosition
            , FullPosition sourcePosition) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(spcFileName)))) {
            SPCFile specFile = new SPCFile(spcFileName);
            specFile.sourceID = spcFileName.getSourceID();
            specFile.receiverID = spcFileName.getReceiverID();
            // read header PF
            // tlen
            double tlen = dis.readDouble();
            // np
            int np = dis.readInt();
            // nbody
            int nbody = dis.readInt();
            // ncomponents
            int ncomp = dis.readInt();
            //System.err.println(np + " " + nbody + " " + ncomp);

            switch (ncomp) {
            case 0: // isotropic 1D partial par2 (lambda)
                specFile.spcFileType = spcFileName.getFileType();
                specFile.nComponent = 3;
                break;
            case 3: // normal synthetic
                specFile.nComponent = 3;
                specFile.spcFileType = SPCType.SYNTHETIC;
                break;
            case 4:// forward propagation dislocation field. 4 is an identifier. The actual number of component is 3 (3 non-zero component).
                specFile.nComponent = 3;
                specFile.spcFileType = SPCType.UF;
                break;
            case 5:// back propagation dialocation filed. 5 is an identifier. The actual number of component is 9 (9 non-zero component).
                specFile.nComponent = 9;
                specFile.spcFileType = SPCType.UB;
                break;
            case 7: // back propagation PSV catalog. 7 is an identifier. The actual number of component is 27 (27 non-zero component).
//				System.out.println("PBPSVCAT");
                specFile.nComponent = 27;
                specFile.spcFileType = SPCType.PBPSVCAT;
                break;
            case 8: // back propagation SH catalog. 8 is an identifier. The actual number of component is 27 (18 non-zero component).
                specFile.nComponent = 27;
                specFile.spcFileType = SPCType.PBSHCAT;
                break;
            case 9: // forward propagation (strain field)
                specFile.nComponent = 9;
                specFile.spcFileType = SPCType.PF;
                break;
            case 10: // forward propagation SH catalog. 10 is an identifier. The actual number of component is 9.
                specFile.nComponent = 9;
                specFile.spcFileType = SPCType.PFSHCAT;
                break;
//            case 11: // Optimized forward propagation SH catalog. 11 is an identifier. The actual number of component is 9.
//                specFile.nComponent = 9;
//                specFile.spcFileType = SPCType.PFSHO;
//                break;
            case 12: // forward propagation SH catalog. 10 is an identifier. The actual number of component is 9.
                specFile.nComponent = 9;
                specFile.spcFileType = SPCType.PFPSVCAT;
                break;
            case 27: // back propagation (strain field)
                specFile.nComponent = 27;
                specFile.spcFileType = SPCType.PB;
                break;
            default:
                throw new RuntimeException("component can be only 3(synthetic), 4(uf), 5(ub), 7(bppsvcat), 8(bpshcat), 9(fp), 10(fpshcat), or 27(bp) right now");
            }

            //System.out.println(nbody);
            specFile.nbody = nbody;
            specFile.np = np;
            specFile.tlen = tlen;

            specFile.spcBody = new ArrayList<>(nbody);
            for (int i = 0; i < nbody; i++)
                specFile.spcBody.add(new SPCBody(specFile.nComponent, np));

            // data part
            specFile.omegai = dis.readDouble();

            if (receiverPosition == null)
                specFile.receiverPosition = new HorizontalPosition(dis.readDouble(), dis.readDouble());
            else {
                dis.readDouble();
                dis.readDouble();
                specFile.receiverPosition = receiverPosition;
            }

            //
            switch (specFile.spcFileType) {
            case RHO1D:
            case LAMBDA1D:
            case MU1D:
            case A1D:
            case C1D:
            case F1D:
            case L1D:
            case N1D:
            case SYNTHETIC:
                specFile.sourcePosition = new FullPosition(dis.readDouble(), dis.readDouble(), dis.readDouble());
                break;
            case PBSHCAT:
            case PBPSVCAT:
                if (sourcePosition == null)
                    specFile.sourcePosition = new FullPosition(dis.readDouble(), dis.readDouble(), 0);
                else {
                    dis.readDouble();
                    dis.readDouble();
                    if (sourcePosition.getR() != Earth.EARTH_RADIUS)
                        throw new RuntimeException("Error: BP source depth should be 0. " + sourcePosition.getR() + " " + Earth.EARTH_RADIUS);
                    specFile.sourcePosition = sourcePosition;
                }
                break;
            case UF:
            case PF:
//            case PFSHO:
//                specFile.sourceLocation = new FullPosition(dis.readDouble(), dis.readDouble(), dis.readDouble());
//                break;
            case PFSHCAT:
            case PFPSVCAT:
                if (sourcePosition == null)
                    specFile.sourcePosition = new FullPosition(dis.readDouble(), dis.readDouble(), dis.readDouble());
                else {
                    dis.readDouble();
                    dis.readDouble();
                    dis.readDouble();
                    specFile.sourcePosition = sourcePosition;
                }
                break;
            case UB:
            case PB:
                specFile.sourcePosition = new FullPosition(dis.readDouble(), dis.readDouble(), 0); // TODO
                break;
            default:
                throw new RuntimeException("Unexpected");
            }

            specFile.bodyR = new double[nbody];
            if (specFile.spcFileType != SPCType.SYNTHETIC)
                for (int i = 0; i < nbody; i++)
                    specFile.bodyR[i] = dis.readDouble();

            double cosphi = FastMath.cos(phi);
            double sinphi = FastMath.sin(phi);
            double cos2phi = FastMath.cos(2 * phi);
            double sin2phi = FastMath.sin(2 * phi);

            // read body
            for (int i = 0; i < np + 1; i++)
                for (SPCBody body : specFile.spcBody) {
                    Complex[] u = new Complex[specFile.nComponent];
                    int ip = dis.readInt();

                    if (specFile.spcFileType.equals(SPCType.PBSHCAT)) {
                        for (int k = 0; k < specFile.nComponent; k++) {
                            if (SPCTensorComponent.isBPSHCATzero(k+1))
                                u[k] = Complex.ZERO;
                            else {
                                double tmpReal_m1 = dis.readDouble();
                                double tmpImag_m1 = dis.readDouble();
                                double tmpReal_p1 = dis.readDouble();
                                double tmpImag_p1 = dis.readDouble();

//								System.out.println(k + " " + tmpReal_m1 + " " + tmpReal_p1 + " " + tmpImag_m1 + " " + tmpImag_p1);

//								double cosphi = FastMath.cos(phi);
//								double sinphi = FastMath.sin(phi);

                                double tmpReal = tmpReal_m1*cosphi + tmpImag_m1*sinphi
                                        + tmpReal_p1*cosphi - tmpImag_p1*sinphi;
                                double tmpImag = -tmpReal_m1*sinphi + tmpImag_m1*cosphi
                                        + tmpReal_p1*sinphi + tmpImag_p1*cosphi;

                                u[k] = new Complex(tmpReal, tmpImag);
                            }
                        }
                      //TODO
                      //  if (observerName.equals("XY100"))
                      //  if (ip == 512) {
                      //      System.err.println(spcFileName.toString());
                      //      System.err.println(specFile.observerID + " " + phi);
                      //      for (int k = 0; k < specFile.nComponent; k++) {
                      //          System.err.println(u[k].getReal() + " " + u[k].getImaginary());
                      //      }
                      //  }
                    }
                    else if (specFile.spcFileType.equals(SPCType.PBPSVCAT)) {
                        for (int k = 0; k < specFile.nComponent; k++) {
                            double tmpReal_m1 = dis.readDouble();
                            double tmpImag_m1 = dis.readDouble();
                            double tmpReal_m0 = dis.readDouble();
                            double tmpImag_m0 = dis.readDouble();
                            double tmpReal_p1 = dis.readDouble();
                            double tmpImag_p1 = dis.readDouble();

//								System.out.println(k + " " + tmpReal_m1 + " " + tmpReal_p1 + " " + tmpReal_m0 +  " " + tmpImag_m0 + " " + tmpImag_m1 + " " + tmpImag_p1);

//								double cosphi = FastMath.cos(phi);
//								double sinphi = FastMath.sin(phi);

                            double tmpReal = tmpReal_m0 + tmpReal_m1*cosphi + tmpImag_m1*sinphi
                                    + tmpReal_p1*cosphi - tmpImag_p1*sinphi;
                            double tmpImag = tmpImag_m0 + -tmpReal_m1*sinphi + tmpImag_m1*cosphi
                                    + tmpReal_p1*sinphi + tmpImag_p1*cosphi;

                            u[k] = new Complex(tmpReal, tmpImag);
                        }
                    }
                    else if (specFile.spcFileType.equals(SPCType.PFSHCAT) ) {
                        for (int k = 0; k < specFile.nComponent; k++) {
                            double tmpReal_m2 = dis.readDouble();
                            double tmpImag_m2 = dis.readDouble();
                            double tmpReal_m1 = dis.readDouble();
                            double tmpImag_m1 = dis.readDouble();
                            double tmpReal_p1 = dis.readDouble();
                            double tmpImag_p1 = dis.readDouble();
                            double tmpReal_p2 = dis.readDouble();
                            double tmpImag_p2 = dis.readDouble();

                            double tmpReal = tmpReal_m2*cos2phi + tmpImag_m2*sin2phi
                                    + tmpReal_m1*cosphi + tmpImag_m1*sinphi
                                    + tmpReal_p1*cosphi - tmpImag_p1*sinphi
                                    + tmpReal_p2*cos2phi - tmpImag_p2*sin2phi;
                            double tmpImag = -tmpReal_m2*sin2phi + tmpImag_m2*cos2phi
                                    - tmpReal_m1*sinphi + tmpImag_m1*cosphi
                                    + tmpReal_p1*sinphi + tmpImag_p1*cosphi
                                    + tmpReal_p2*sin2phi + tmpImag_p2*cos2phi;

                            u[k] = new Complex(tmpReal, tmpImag);
                        }
                    }
                    else if (specFile.spcFileType.equals(SPCType.PFPSVCAT) ) {
                        for (int k = 0; k < specFile.nComponent; k++) {
                            double tmpReal_m2 = dis.readDouble();
                            double tmpImag_m2 = dis.readDouble();
                            double tmpReal_m1 = dis.readDouble();
                            double tmpImag_m1 = dis.readDouble();
                            double tmpReal_m0 = dis.readDouble();
                            double tmpImag_m0 = dis.readDouble();
                            double tmpReal_p1 = dis.readDouble();
                            double tmpImag_p1 = dis.readDouble();
                            double tmpReal_p2 = dis.readDouble();
                            double tmpImag_p2 = dis.readDouble();

                            double tmpReal = tmpReal_m0 + tmpReal_m2*cos2phi + tmpImag_m2*sin2phi
                                    + tmpReal_m1*cosphi + tmpImag_m1*sinphi
                                    + tmpReal_p1*cosphi - tmpImag_p1*sinphi
                                    + tmpReal_p2*cos2phi - tmpImag_p2*sin2phi;
                            double tmpImag = tmpImag_m0 - tmpReal_m2*sin2phi + tmpImag_m2*cos2phi
                                    - tmpReal_m1*sinphi + tmpImag_m1*cosphi
                                    + tmpReal_p1*sinphi + tmpImag_p1*cosphi
                                    + tmpReal_p2*sin2phi + tmpImag_p2*cos2phi;

                            u[k] = new Complex(tmpReal, tmpImag);
                        }
                    }
//                    else if (specFile.spcFileType.equals(SPCType.PFSHO)) {
//                        for (int k = 0; k < specFile.nComponent; k++) {
//                            if (SPCTensorComponent.isFPSHzero(k+1))
//                                u[k] = Complex.ZERO;
//                            else
//                                u[k] = new Complex(dis.readDouble(), dis.readDouble());
//                        }
//                    }
                    else {
                        for (int k = 0; k < specFile.nComponent; k++) {
                            u[k] = new Complex(dis.readDouble(), dis.readDouble());
                        }
                    }
                    body.add(ip, u);
                }
            return specFile;
        }
    }

    /**
     * @param spcFileName
     * @param phi
     * @return
     * @throws IOException
     * @author anselme
     */
    public static final SPCFile getInstance(SPCFileName spcFileName, double phi) throws IOException {
        return getInstance(spcFileName, phi, null, null);
    }

    /**
     * @param spcFileName
     * @return
     * @throws IOException
     * @author anselme
     */
    public static final SPCFile getInstance(SPCFileName spcFileName) throws IOException {
        return getInstance(spcFileName, 0., null, null);
    }

    /**
     * (PB, PF, UB, UF) Return perturbation point code
     * <p>
     * (else) Return obsever code
     */
    @Override
    public String getReceiverID() {
        return receiverID;
    }

    /**
     * (PB, UB) Return observer code (source of back propagate wave)
     * <p>
     * (else) Return source ID
     */
    @Override
    public String getSourceID() {
        return sourceID;
    }

    @Override
    public FullPosition getSourcePosition() {
        return sourcePosition;
    }

    @Override
    public HorizontalPosition getReceiverPosition() {
        return receiverPosition;
    }

    public SPCFileName getSpcFileName() {
        return spcFileName;
    }

    @Override
    public double tlen() {
        return tlen;
    }

    @Override
    public int np() {
        return np;
    }

    @Override
    public double omegai() {
        return omegai;
    }

    @Override
    public List<SPCBody> getSpcBodyList() {
        return Collections.unmodifiableList(spcBody);
    }

    @Override
    public int nbody() {
        return nbody;
    }

    @Override
    public SPCType getSpcFileType() {
        return spcFileType;
    }

    @Override
    public double[] getBodyR() {
        return bodyR.clone();
    }

    @Override
    public void setSpcBody(int i, SPCBody body) {
        spcBody.set(i, body.copy());
    }
}
