package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

/**
 * Data for 1 depth layer (= 1 perturbation point) in {@link SPCFile}.
 * <p>
 * ista に対応する
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class SPCBody {

    /**
     * Number of steps in frequency domain.
     */
    private final int np;

    private final int nElement;
    private SPCElement[] spcElements;

    /**
     * @param nElement (int) The number of elements.
     * @param np (int) The number of steps in frequency domain.
     */
    SPCBody(int nElement, int np) {
        this.nElement = nElement;
        this.np = np;
        spcElements = IntStream.range(0, nElement).mapToObj(i -> new SPCElement(np)).toArray(SPCElement[]::new);
    }

    /**
     * Set spectrum values for all elements for a single &omega; value.
     * @param ip (int) Step number in frequency domain.
     * @param u (Complex...) Spectrum values for all elements. u[i] is for the i-th element.
     */
    void setValues(int ip, Complex... u) {
        if (u.length != nElement) throw new IllegalStateException("The number of elements is wrong.");
        for (int i = 0; i < nElement; i++)
            spcElements[i].setValue(ip, u[i]);
    }

    /**
     * @return DEEP copy of this
     */
    SPCBody copy() {
        SPCBody s = new SPCBody(nElement, np);
        s.spcElements = new SPCElement[nElement];
        Arrays.setAll(s.spcElements, i -> spcElements[i].copy());
        return s;
    }

    /**
     * Interpolation for catalog
     * @param anotherBody
     * @param unitDistance
     * @return
     * @author anselme
     */
    public SPCBody interpolate(SPCBody anotherBody, double unitDistance) {
        if (unitDistance < 0 || unitDistance > 1)
            throw new IllegalArgumentException("Unit distance must be between 0-1: " + unitDistance);
        SPCBody s = this.copy();
        if (np != anotherBody.getNp()) throw new IllegalStateException("np is not equal.");
        if (nElement != anotherBody.getNElement()) throw new IllegalStateException("Number of elements is different.");

        for (int j = 0; j < nElement; j++) {
            SPCElement comp1 = s.spcElements[j];
            SPCElement comp2 = anotherBody.spcElements[j];
            comp1.mapMultiply(1. - unitDistance);
            comp2.mapMultiply(unitDistance);
            comp1.addElement(comp2);
            s.spcElements[j] = comp1;
        }

        return s;
    }

    /**
     * Interpolate wave field by using Lagrange interpolation
     * @param body1
     * @param body2
     * @param body3
     * @param dh
     * @return
     * @author anselme
     */
    //TODO Compare with another interpolation method
    public static SPCBody interpolate(SPCBody body1, SPCBody body2, SPCBody body3, double[] dh) {
        SPCBody s = body1.copy();
//		double c1 = 1 - dh[0] + dh[0]*dh[1]/2.;
//		double c2 = dh[0] - dh[0]*dh[1];
//		double c3 = dh[0]*dh[1]/2.;
        double c1 = dh[1]*dh[2] / 2.;
        double c2 = -dh[0]*dh[2];
        double c3 = dh[0]*dh[1] / 2.;

        for (int j = 0; j < body1.nElement; j++) {
            SPCElement comp1 = body1.spcElements[j].copy();
            SPCElement comp2 = body2.spcElements[j].copy();
            SPCElement comp3 = body3.spcElements[j].copy();

            comp1.mapMultiply(c1);
            comp2.mapMultiply(c2);
            comp3.mapMultiply(c3);
            comp1.addElement(comp2);
            comp1.addElement(comp3);

            s.spcElements[j] = comp1;
        }

        return s;
    }

    /**
     * @param body1
     * @param body2
     * @param body3
     * @param dh
     * @return
     * @author anselme
     */
    public static SPCBody interpolate_backward(SPCBody body1, SPCBody body2, SPCBody body3, double[] dh) {
        SPCBody s = body1.copy();
//		double c1 = 1 - dh[0] + dh[0]*dh[1]/2.;
//		double c2 = dh[0] - dh[0]*dh[1];
//		double c3 = dh[0]*dh[1]/2.;
        double c1 = -dh[1]*dh[2];
        double c2 = dh[0]*dh[2] / 2.;
        double c3 = dh[0]*dh[1] / 2.;

        for (int j = 0; j < body1.nElement; j++) {
            SPCElement element1 = body1.spcElements[j];
            SPCElement element2 = body2.spcElements[j];
            SPCElement element3 = body3.spcElements[j];

            element1.mapMultiply(c1);
            element2.mapMultiply(c2);
            element3.mapMultiply(c3);
            element1.addElement(element2);
            element1.addElement(element3);

            s.spcElements[j] = element1;
        }

        return s;
    }

    /**
     * Add the spectrum values in the frequency domain of another {@link SPCBody}.
     * @param anotherBody ({@link SPCBody}) The instance to add to this instance.
     */
    public void addBody(SPCBody anotherBody) {
        if (np != anotherBody.getNp()) throw new IllegalStateException("np is not equal.");
        if (nElement != anotherBody.getNElement()) throw new IllegalStateException("Number of elements is different.");

        for (int j = 0; j < nElement; j++)
            spcElements[j].addElement(anotherBody.spcElements[j]);
    }

    /**
     * Apply ramped source time function. To be conducted before {@link #toTimeDomain(int)}.
     * @param sourceTimeFunction ({@link SourceTimeFunction}) Source time function to be applied on all elements.
     */
    public void applySourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        Arrays.stream(spcElements).forEach(element -> element.applySourceTimeFunction(sourceTimeFunction));
    }

    /**
     * Convert the data in frequency domain to time domain for all elements using FFT.
     * @param npts (int) Number of data points in time domain.
     */
    public void toTimeDomain(int npts) {
        Arrays.stream(spcElements).forEach(element -> element.toTimeDomain(npts));
    }

    /**
     * Multiply exp(&omega;<sub>I</sub>t) to all elements to account for the artificial damping introduced in DSM.
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param omegaI (double) &omega;<sub>i</sub>.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    public void applyGrowingExponential(double omegaI, double samplingHz) {
        Arrays.stream(spcElements).forEach(element -> element.applyGrowingExponential(omegaI, samplingHz));
    }

    /**
     * Correct the amplitude of time series, converting the unit from [km] to [m/s].
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    public void amplitudeCorrection(double samplingHz) {
        Arrays.stream(spcElements).forEach(element -> element.amplitudeCorrection(samplingHz));
    }

    /**
     * Differentiate the data for all elements (in frequency domain) by time.
     * To be conducted before {@link #toTimeDomain(int)}.
     * @param tlen (double) Time length [s].
     */
    public void differentiate(double tlen) {
        Arrays.stream(spcElements).forEach(element -> element.differentiate(tlen));
    }

    public int getNp() {
        return np;
    }

    public int getNElement() {
        return nElement;
    }

    /**
     * @return ({@link SPCElement}[]) All the {@link SPCElement}s in this.
     */
    public SPCElement[] getSpcElements() {
        return spcElements;
    }

    /**
     * Get element for specified tensor component.
     * @param tensorComponent ({@link SPCTensorComponent}) Tensor component.
     * @return ({@link SPCElement}) Element for the tensor component.
     */
    public SPCElement getSpcElement(SPCTensorComponent tensorComponent) {
        return spcElements[tensorComponent.valueOf() - 1];
    }

    public SPCElement getSpcElement(SACComponent sacComponent) {
        return spcElements[sacComponent.valueOf() - 1];
    }

    public SPCElement getSpcElement(int n) {
        return spcElements[n];
    }

    /**
     * Get the displacement velociy time series for a certain component.
     * @param component ({@link SACComponent}) Component to retreive data for.
     * @return (double[]) Data for the specified component in time domain [m/s].
     */
    public double[] getTimeseries(SACComponent component) {
        return spcElements[component.valueOf() - 1].getTimeseries();
    }

}
