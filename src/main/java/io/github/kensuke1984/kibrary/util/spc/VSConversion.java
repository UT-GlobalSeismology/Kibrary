/**
 *
 */
package io.github.kensuke1984.kibrary.util.spc;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.elastic.ElasticMedium;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.DefaultStructure;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Conversion of a partial derivative<br>
 * for &mu;<sub>0</sub> to Q<sub>&mu;</sub>(&ne;q) following Fuji <i>et al</i>.
 * (2010)
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public final class VSConversion {

    private PolynomialStructure structure;

    /**
     * @param structure
     *            structure
     */
    public VSConversion(PolynomialStructure structure) {
        this.structure = structure == null ? DefaultStructure.PREM : structure;
    }

    /**
     * Conversion with respect to PREM
     */
    public VSConversion() {
        this(null);
    }

    public SPCFileAccess convert(SPCFileAccess spectrum) {
        if (spectrum.getSpcFileType() != SPCType.MU1D)
            throw new RuntimeException();

        final int nbody = spectrum.nbody();
        final int np = spectrum.np();
        final double tlen = spectrum.tlen();
        List<SPCBody> spcBodyList = new ArrayList<>(nbody);
        SPCFileName spcFileName = spectrum.getSpcFileName();

        // data part
        double omegai = spectrum.omegai();
        HorizontalPosition observerPosition = spectrum.getReceiverPosition();
        String observerID = spectrum.getReceiverID();
        FullPosition sourceLocation = spectrum.getSourcePosition();
        String sourceID = spectrum.getSourceID();
        double[] bodyR = spectrum.getBodyR();
        for (int i = 0; i < spectrum.nbody(); i++) {
            double r = bodyR[i];
            ElasticMedium medium = structure.mediumAt(r);
            double fact = 2. * medium.get(VariableType.RHO) * medium.get(VariableType.Vsh);
            SPCBody body = spectrum.getSpcBodyList().get(i);
            SPCBody newBody = new SPCBody(3, np);
            for (int ip = 0; ip < np + 1; ip++) {
                Complex[] uIm = new Complex[body.getNumberOfComponent()];
                for (int iComponent = 0; iComponent < body.getNumberOfComponent(); iComponent++) {
                    Complex u = body.getSpcComponent(SACComponent.getComponent(iComponent + 1))
                            .getValueInFrequencyDomain()[ip];
                    uIm[iComponent] = u.multiply(fact);
                }
                newBody.add(ip, uIm);
            }
            spcBodyList.add(newBody);
        }
        SPCFileAccess dsmoutput = new SPCFileAccess() {

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
                return SPCType.VS1D;
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
//				spcBody.set(i, body); //TODO
            }
        };

        return dsmoutput;
    }

}
