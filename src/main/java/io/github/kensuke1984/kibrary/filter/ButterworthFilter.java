package io.github.kensuke1984.kibrary.filter;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Masanori Saito recursive digital filter <br>
 * 斎藤正徳 漸化式ディジタルフィルタ<br>
 * |B<sub>n</sub>(&sigma;)|<sup>2</sup>=1/(1+&sigma;<sup>2n</sup>)<br>
 * Saito (1978) Default design is for zero-phase filter.
 * <p>
 * If you want to create a causal one, use {@link #setBackward(boolean)} and set
 * true.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public abstract class ButterworthFilter {

    /**
     * A<sub>p</sub> 透過係数 permissible parameter <br>
     * 透過域の振幅は (1+A<sub>p</sub><sup>2</sup>)<sup>-1</sup> 以上 <br>
     * amplitude ratio in permissible region is over (1+A<sub>p</sub>
     * <sup>2</sup>)<sup>-1</sup>
     */
    double ap;
    /**
     * &sigma;<sub>s</sub>/&sigma;<sub>p</sub>
     */
    double sigmaSoverSigmaP;
    /**
     * G<sub>0</sub> in eq. 1.3 Saito
     */
    double g;
    int n;
    double c;
    /**
     * A<sub>s</sub> 遮断係数 shutting parameter <br>
     * 遮断域の振幅は (1+A<sub>s</sub><sup>2</sup>)<sup>-1</sup> 以下 <br>
     * amplitude ratio in shutting region is over (1+A<sub>s</sub><sup>2</sup>)
     * <sup>-1</sup>
     */
    double as;
    /**
     * True:zero phase, False:causal
     */
    boolean backward = true;
    double[] b1;
    double[] b2;

    ButterworthFilter() {
    }

    /**
     * Compute a value of Saito 1.7. <br>
     * H<sub>j</sub> = (a<sub>1j</sub> +(a<sub>2j</sub>+1) cos &omega; -i(a
     * <sub>2j</sub>-1)sin&omega;)<br>
     * /(b<sub>2j</sub>+1) cos &omega; -i(b<sub>2j</sub>-1)sin&omega;) <br>
     * If you want to compute a compute Frequency responce, you have to multiply
     * all of compute frequency for each filter(j) and consider a vaue of G.
     *
     * @param omega &omega;: 2&pi;f&Delta;t
     * @return frequency responce for an input &omega;
     */
    public abstract Complex getFrequencyResponse(double omega);

    public double getAp() {
        return ap;
    }

    public double getAs() {
        return as;
    }

    /**
     * @param data data to be applied the filter
     * @return arrays after applied the filter
     */
    public abstract Complex[] applyFilter(Complex[] data);

    /**
     * @param data data to be applied the filter
     * @return arrays after applied the filter
     */
    public double[] applyFilter(double[] data) {
        Complex[] cdata = new Complex[data.length];
        for (int i = 0; i < cdata.length; i++)
            cdata[i] = Complex.valueOf(data[i]);

        cdata = applyFilter(cdata);

        double[] newData = new double[data.length];
        for (int i = 0; i < data.length; i++)
            newData[i] = cdata[i].getReal();

        return newData;
    }

    /**
     * true: zero phase, false: causal
     * @param backward if true, backword filtering is done
     */
    public void setBackward(boolean backward) {
        this.backward = backward;
    }
    /**
     * true: causal, false: zero-phase
     * @param causal (boolean) if false, backword filtering is done
     * @author otsuru
     * @since 2022/7/12
     */
    public void setCausal(boolean causal) {
        this.backward = !causal;
    }

    void setN() {
        n = (int) MathAid.ceil(FastMath.log(as / ap) / FastMath.log(sigmaSoverSigmaP));
    }

    abstract void setSigmaSoverSigmaP();

    /**
     * computes {@link #sigmaSoverSigmaP} from an input n
     *
     * @param n a parameter for the filter. see Saito
     */
    void nToSigmaSoverSigmaP(int n) {
        sigmaSoverSigmaP = FastMath.exp(FastMath.log(as / ap) / n);
        // double n = Math.log(as/ap)/Math.log(sigmaSvsSigmaP);
    }

}
