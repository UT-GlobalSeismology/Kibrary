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
 * Spectrum file written by DSM. Binary format.
 * <p>
 * Note that the data values in spectrum files are displacement velocity in the frequency domain, with unit [km].
 * <p>
 * Each file can contain information for multiple perturbation radii, but only for a single horizontal pixel.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class SPCFile implements SPCFileAccess {

    private final SPCFileName spcFileName;
    private SPCType spcFileType;
    private int nbody;
    private double[] bodyR;
    private List<SPCBody> spcBodies;

    private String sourceID;
    /**
     * Position of the source.
     */
    private FullPosition sourcePosition;
    private String receiverID;
    /**
     * Horizontal position of the receiver. Does not include depth information.
     */
    private HorizontalPosition receiverPosition;
    private double tlen;
    private int np;
    private double omegai;
    /**
     * Number of elements in each spcBody.
     */
    private int nElement;

    public SPCFile(SPCFileName spcFileName) {
        this.spcFileName = spcFileName;
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
        SPCFile specFile = new SPCFile(spcFileName);
        specFile.sourceID = spcFileName.getSourceID();
        specFile.receiverID = spcFileName.getReceiverID();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(spcFileName)))) {
            // read header PF
            // tlen
            double tlen = dis.readDouble();
            specFile.tlen = tlen;
            // np
            int np = dis.readInt();
            specFile.np = np;
            // nbody
            int nbody = dis.readInt();
            specFile.nbody = nbody;
            // ncomponents
            int typeNumber = dis.readInt();
            if (typeNumber == 0) {  // isotropic 1D partial
                specFile.spcFileType = spcFileName.getFileType();
            } else {  // synthetic, FP, or BP
                specFile.spcFileType = SPCType.ofNumber(typeNumber);
            }
            specFile.nElement = specFile.spcFileType.getNElement();

            specFile.spcBodies = new ArrayList<>(nbody);
            for (int i = 0; i < nbody; i++)
                specFile.spcBodies.add(new SPCBody(specFile.nElement, np));

            //~data part
            specFile.omegai = dis.readDouble();

            // read receiver position
            if (receiverPosition == null) {
                specFile.receiverPosition = new HorizontalPosition(dis.readDouble(), dis.readDouble());
            } else {
                dis.readDouble();
                dis.readDouble();
                specFile.receiverPosition = receiverPosition;
            }

            // read source position
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
                specFile.sourcePosition = new FullPosition(dis.readDouble(), dis.readDouble(), 0); // TODO radius is incorrect
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

            //~read body
            int hasZero = 0;
            for (int i = 0; i < np + hasZero; i++) {
                for (SPCBody body : specFile.spcBodies) {
                    Complex[] u = new Complex[specFile.nElement];
                    int ip = dis.readInt();
                    // Added this to account for SPC files with and without data for ip=0.  2024/4/17 otsuru
                    if (ip == 0) hasZero = 1;

                    if (specFile.spcFileType.equals(SPCType.PBSHCAT)) {
                        for (int k = 0; k < specFile.nElement; k++) {
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
                    } else if (specFile.spcFileType.equals(SPCType.PBPSVCAT)) {
                        for (int k = 0; k < specFile.nElement; k++) {
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
                    } else if (specFile.spcFileType.equals(SPCType.PFSHCAT) ) {
                        for (int k = 0; k < specFile.nElement; k++) {
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
                    } else if (specFile.spcFileType.equals(SPCType.PFPSVCAT) ) {
                        for (int k = 0; k < specFile.nElement; k++) {
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
                    } else {
                        for (int k = 0; k < specFile.nElement; k++) {
                            u[k] = new Complex(dis.readDouble(), dis.readDouble());
                        }
                    }

                    try {
                        body.setValues(ip, u);
                    } catch (Exception e) {
                        System.err.println(spcFileName);
                        throw e;
                    }
                }
            }
        }
        return specFile;
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
            SPCBody body = SPCBody.interpolate(bp1.spcBodies.get(ibody), bp2.spcBodies.get(ibody), bp3.spcBodies.get(ibody), dh);
            bp.spcBodies.set(ibody, body);
        }

        return bp;
    }

    @Override
    public void setSpcBody(int i, SPCBody body) {
        spcBodies.set(i, body.copy());
    }

    @Override
    public int nbody() {
        return nbody;
    }

    @Override
    public double[] getBodyR() {
        return bodyR.clone();
    }

    @Override
    public List<SPCBody> getSpcBodyList() {
        return Collections.unmodifiableList(spcBodies);
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

    /**
     * (PB, PF, UB, UF) Return perturbation point code
     * <p>
     * (else) Return obsever code
     */
    @Override
    public String getReceiverID() {
        return receiverID;
    }

    @Override
    public HorizontalPosition getReceiverPosition() {
        return receiverPosition;
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
    public SPCType getSpcFileType() {
        return spcFileType;
    }

    @Override
    public SPCFileName getSpcFileName() {
        return spcFileName;
    }

}
