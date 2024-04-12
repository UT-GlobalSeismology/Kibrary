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
    /**
     * Number of data points in time domain.
     */
    private int nptsInTimeDomain;

    private final int nComponent;
    private SPCComponent[] spcComponents;

    /**
     * @param nComponent the number of components
     * @param np         the number of steps in frequency domain
     */
    SPCBody(int nComponent, int np) {
        this.nComponent = nComponent;
        this.np = np;
        spcComponents = IntStream.range(0, nComponent).mapToObj(i -> new SPCComponent(np)).toArray(SPCComponent[]::new);
    }

    /**
     * Set spectrum values for all components for a single &omega; value.
     * @param ip (int) Step number in frequency domain.
     * @param u (Complex...) Spectrum values for all components. u[i] is for the i-th component.
     */
    void setValues(int ip, Complex... u) {
        if (u.length != nComponent) throw new IllegalStateException("The number of components is wrong.");
        for (int i = 0; i < nComponent; i++)
            spcComponents[i].setValue(ip, u[i]);
    }

    /**
     * @return DEEP copy of this
     */
    SPCBody copy() {
        SPCBody s = new SPCBody(nComponent, np);
        s.nptsInTimeDomain = nptsInTimeDomain;
        s.spcComponents = new SPCComponent[nComponent];
        Arrays.setAll(s.spcComponents, i -> spcComponents[i].copy());
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
            throw new RuntimeException("Error: unit distance should be between 0-1 " + unitDistance);
        SPCBody s = this.copy();
        if (nComponent != anotherBody.getNp())
            throw new RuntimeException("Error: Size of body is not equal!");
        else if (nComponent != anotherBody.getNumberOfComponent())
            throw new RuntimeException("Error: The numbers of each component are different.");

        for (int j = 0; j < nComponent; j++) {
            SPCComponent comp1 = s.spcComponents[j];
            SPCComponent comp2 = anotherBody.spcComponents[j];
            comp1.mapMultiply(1. - unitDistance);
            comp2.mapMultiply(unitDistance);
            comp1.addComponent(comp2);
            s.spcComponents[j] = comp1;
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

        for (int j = 0; j < body1.nComponent; j++) {
            SPCComponent comp1 = body1.spcComponents[j].copy();
            SPCComponent comp2 = body2.spcComponents[j].copy();
            SPCComponent comp3 = body3.spcComponents[j].copy();

            comp1.mapMultiply(c1);
            comp2.mapMultiply(c2);
            comp3.mapMultiply(c3);
            comp1.addComponent(comp2);
            comp1.addComponent(comp3);

            s.spcComponents[j] = comp1;
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

        for (int j = 0; j < body1.nComponent; j++) {
            SPCComponent comp1 = body1.spcComponents[j];
            SPCComponent comp2 = body2.spcComponents[j];
            SPCComponent comp3 = body3.spcComponents[j];

            comp1.mapMultiply(c1);
            comp2.mapMultiply(c2);
            comp3.mapMultiply(c3);
            comp1.addComponent(comp2);
            comp1.addComponent(comp3);

            s.spcComponents[j] = comp1;
        }

        return s;
    }

    /**
     * frequency domain をsamplingFrequencyでtime-domain tlen(s)にもってくるスムージング値を探す
     *
     */
    public int findLsmooth(double tlen, double samplingFrequency) {
        int tmpNp = Integer.highestOneBit(np);
        if (tmpNp < np)
            tmpNp *= 2;

        int lsmooth = (int) (0.5 * tlen * samplingFrequency / np);
        int i = Integer.highestOneBit(lsmooth);
        if (i < lsmooth)
            i *= 2;
        lsmooth = i;

        return lsmooth;
    }

    /**
     * Add the spectrum values in the frequency domain of another {@link SPCBody}.
     * @param anotherBody ({@link SPCBody}) The instance to add to this instance.
     */
    public void addBody(SPCBody anotherBody) {
        if (np != anotherBody.getNp()) throw new RuntimeException("Error: Size of body is not equal!");
        else if (nComponent != anotherBody.getNumberOfComponent())
            throw new RuntimeException("Error: The numbers of each component are different.");

        for (int j = 0; j < nComponent; j++)
            spcComponents[j].addComponent(anotherBody.spcComponents[j]);
    }

    /**
     * Apply ramped source time function. To be conducted before {@link #toTimeDomain(int)}.
     * @param sourceTimeFunction will be applied on all components.
     */
    public void applySourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        Arrays.stream(spcComponents).forEach(component -> component.applySourceTimeFunction(sourceTimeFunction));
    }

    /**
     * Converts all the components to time domain.
     * @param lsmooth lsmooth
     */
    public void toTimeDomain(int lsmooth) {
        Arrays.stream(spcComponents).forEach(component -> component.toTimeDomain(lsmooth));
    }

    /**
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param tlen time length
     */
    public void amplitudeCorrection(double tlen) {
        Arrays.stream(spcComponents).forEach(component -> component.amplitudeCorrection(tlen));
    }

    /**
     * To be conducted after {@link #toTimeDomain(int)}.
     * @param omegaI &omega;<sub>i</sub>
     * @param tlen   time length
     */
    public void applyGrowingExponential(double omegaI, double tlen) {
        Arrays.stream(spcComponents).forEach(component -> component.applyGrowingExponential(omegaI, tlen));
    }

    /**
     * すべてのコンポーネントに対し時間微分する。 before toTime
     *
     * @param tlen time length
     */
    public void differentiate(double tlen) {
        Arrays.stream(spcComponents).forEach(component -> component.differentiate(tlen));
    }

    public int getNp() {
        return np;
    }

    public int getNPTSinTimeDomain() {
        return nptsInTimeDomain;
    }

    public int getNumberOfComponent() {
        return nComponent;
    }

    /**
     * @return SPCComponent[] all the {@link SPCComponent} in this
     */
    public SPCComponent[] getSpcComponents() {
        return spcComponents;
    }

    /**
     * 引数で指定されたテンソル成分に対するコンポーネントを返す
     *
     * @param tensor SPCTensorComponent
     * @return SPCComponent for the tensor
     */
    public SPCComponent getSpcComponent(SPCTensorComponent tensor) {
        return spcComponents[tensor.valueOf() - 1];
    }

    public SPCComponent getSpcComponent(SACComponent sacComponent) {
        return spcComponents[sacComponent.valueOf() - 1];
    }

    public SPCComponent getSpcComponent(int n) {
        return spcComponents[n];
    }

    /**
     * @param component {@link SACComponent}
     * @return the data of i th component time_domain
     */
    public double[] getTimeseries(SACComponent component) {
        return spcComponents[component.valueOf() - 1].getTimeseries();
    }

}
