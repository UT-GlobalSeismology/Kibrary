package io.github.kensuke1984.kibrary.elasticparameter;

import java.util.HashMap;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.earth.ParameterType;

/**
 * Class for computing unknown parameters of isotropic and transversely isotropic (TI) media
 * when some parameters are already known.
 *<p>
 * Relationships between parameters are as follows:
 * <ul>
 * <li> A = &rho;V<sub>PH</sub><sup>2</sup> </li>
 * <li> C = &rho;V<sub>PV</sub><sup>2</sup> </li>
 * <li> F = &eta;(A-2*L) </li>
 * <li> L = &rho;V<sub>SV</sub><sup>2</sup> </li>
 * <li> N = &rho;V<sub>SH</sub><sup>2</sup> </li>
 * <li> &xi; = N/L </li>
 * <li> Vs = (2L+N)/(3&rho;)  (effective isotropic shear wave velocity)</li> //TODO changed?
 * <li> &mu; = Vs * Vs * &rho; </li>
 * <li> &lambda; = Vp * Vp * &rho; - 2 &mu; </li>
 * <li> &kappa; = &lambda; + 2/3 &mu; (bulk modulus) </li>
 * <li> Vb = sqrt(&kappa; / &rho;) </li>
 * </ul>
 *
 * @author otsuru
 * @since 2022//4/11
 */
public class ElasticMedium {

    private Map<ParameterType, Double> parameterMap = new HashMap<>();
    private boolean isAddingIsotropicModuli;
    private boolean isAddingIsotropicVelocities;
    private boolean isAddingTIModuli;
    private boolean isAddingTIVelocities;

    public ElasticMedium() {
    }

    public void set(ParameterType type, double value) {
        if (!isFineAddition(type)) throw new IllegalArgumentException(type + " cannot be added");

        if (ParameterType.isIsotropicModulus(type)) isAddingIsotropicModuli = true;
        else if (ParameterType.isIsotropicVelocity(type)) isAddingIsotropicVelocities = true;
        else if (ParameterType.isTIModulus(type)) isAddingTIModuli = true;
        else if (ParameterType.isTIVelocity(type)) isAddingTIVelocities = true;

        parameterMap.put(type, value);
        calculateWhenAdding(type);
    }

    public double get(ParameterType type) {
        if (!isDefined(type)) throw new IllegalArgumentException("Not enough information is given to define " + type);
        return parameterMap.get(type);
    }

    private boolean isFineAddition(ParameterType type) {
        // not allowed to modify a parameter that is already defined
        if (isDefined(type)) return false;
        // when nothing has been started, anything is OK
        if (!isAddingIsotropicModuli && !isAddingIsotropicVelocities && !isAddingTIModuli && !isAddingTIVelocities) return true;
        // when some group has been started to be added, only its group can be added
        if (ParameterType.isIsotropicModulus(type) && !isAddingIsotropicModuli) return false;
        if (ParameterType.isIsotropicVelocity(type) && !isAddingIsotropicVelocities) return false;
        if (ParameterType.isTIModulus(type) && !isAddingTIModuli) return false;
        if (ParameterType.isTIVelocity(type) && !isAddingTIVelocities) return false;
        // others (rho, Q) are OK
        return true;

/*        switch(type) {
        case Vp:
            if (isDefined(ParameterType.LAMBDAplus2MU)) return false;
            else if (isDefined(ParameterType.Vs) && isDefined(ParameterType.Vb)) return false;
            else return true;
        case Vs:
            if (isDefined(ParameterType.MU)) return false;
            else if (isDefined(ParameterType.Vp) && isDefined(ParameterType.Vb)) return false;
            else return true;
        case Vb:
            if (isDefined(ParameterType.KAPPA)) return false;
            else if (isDefined(ParameterType.Vp) && isDefined(ParameterType.Vb)) return false;
            else return true;
        case LAMBDAplus2MU:
            return isDefined(ParameterType.Vp) ? false : true;
        case MU:
            return isDefined(ParameterType.Vs) ? false : true;
        case KAPPA:
            return isDefined(ParameterType.Vb) ? false : true;
        default:
            return true;
        }
*/
    }

    private void calculateWhenAdding(ParameterType type) {
        if (type == ParameterType.RHO) {
            convertToTIModuli();
            findTIModuli();
            convertToTIVelocities();

            tiToIsotropic();

            convertToIsotropicModuli();
            findIsotropicModuli();
            convertToIsotropicVelocities();

        } else if (ParameterType.isIsotropicModulus(type)) {
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (ParameterType.isIsotropicVelocity(type)) {
            convertToIsotropicModuli();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (ParameterType.isTIModulus(type)) {
            findTIModuli();
            convertToTIVelocities();
            tiToIsotropic();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (ParameterType.isTIVelocity(type)) {
            convertToTIModuli();
            findTIModuli();
            tiToIsotropic();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        }
        // else : nothing has to be calculated
    }

    private void tiToIsotropic() {
        if (isDefined(ParameterType.L) && isDefined(ParameterType.N)) {
            double l = parameterMap.get(ParameterType.L);
            double n = parameterMap.get(ParameterType.N);
            double mu = (2 * l + n) / 3;
            parameterMap.put(ParameterType.MU, mu);
        }
        //TODO how to calculate LAMBDA?
    }

    private void findTIModuli() {
        int numDefined = 0;
        if (isDefined(ParameterType.L)) numDefined++;
        if (isDefined(ParameterType.N)) numDefined++;
        if (isDefined(ParameterType.XI)) numDefined++;
        if (numDefined != 2) {
            return;
        }

        if (isDefined(ParameterType.L) && isDefined(ParameterType.N)) {
            double l = parameterMap.get(ParameterType.L);
            double n = parameterMap.get(ParameterType.N);
            double xi = n / l;
            parameterMap.put(ParameterType.XI, xi);
        } else if (isDefined(ParameterType.L) && isDefined(ParameterType.XI)) {
            double l = parameterMap.get(ParameterType.L);
            double xi = parameterMap.get(ParameterType.XI);
            double n = xi * l;
            parameterMap.put(ParameterType.N, n);
        } else if (isDefined(ParameterType.N) && isDefined(ParameterType.XI)) {
            double n = parameterMap.get(ParameterType.N);
            double xi = parameterMap.get(ParameterType.XI);
            double l = n / xi;
            parameterMap.put(ParameterType.L, l);
        }
    }

    private void convertToTIVelocities() {
        if (!isDefined(ParameterType.RHO)) return;
        double rho = parameterMap.get(ParameterType.RHO);

        if (isDefined(ParameterType.A) == true && isDefined(ParameterType.Vph) == false) {
            double a = parameterMap.get(ParameterType.A);
            double v = Math.sqrt(a / rho);
            parameterMap.put(ParameterType.Vph, v);
        }
        if (isDefined(ParameterType.C) == true && isDefined(ParameterType.Vpv) == false) {
            double c = parameterMap.get(ParameterType.C);
            double v = Math.sqrt(c / rho);
            parameterMap.put(ParameterType.Vpv, v);
        }
        if (isDefined(ParameterType.L) == true && isDefined(ParameterType.Vsv) == false) {
            double l = parameterMap.get(ParameterType.L);
            double v = Math.sqrt(l / rho);
            parameterMap.put(ParameterType.Vsv, v);
        }
        if (isDefined(ParameterType.N) == true && isDefined(ParameterType.Vsh) == false) {
            double n = parameterMap.get(ParameterType.N);
            double v = Math.sqrt(n / rho);
            parameterMap.put(ParameterType.Vsh, v);
        }
        if (isDefined(ParameterType.F) == true && isDefined(ParameterType.A) == true
                && isDefined(ParameterType.L) == true && isDefined(ParameterType.ETA) == false) {
            double a = parameterMap.get(ParameterType.A);
            double f = parameterMap.get(ParameterType.F);
            double l = parameterMap.get(ParameterType.L);
            double eta = f / (a - 2 * l);
            parameterMap.put(ParameterType.ETA, eta);
        }
    }
    private void convertToTIModuli() {
        if (!isDefined(ParameterType.RHO)) return;
        double rho = parameterMap.get(ParameterType.RHO);

        if (isDefined(ParameterType.Vph) == true && isDefined(ParameterType.A) == false) {
            double v = parameterMap.get(ParameterType.Vph);
            double a = rho * v * v;
            parameterMap.put(ParameterType.A, a);
        }
        if (isDefined(ParameterType.Vpv) == true && isDefined(ParameterType.C) == false) {
            double v = parameterMap.get(ParameterType.Vpv);
            double c = rho * v * v;
            parameterMap.put(ParameterType.C, c);
        }
        if (isDefined(ParameterType.Vsv) == true && isDefined(ParameterType.L) == false) {
            double v = parameterMap.get(ParameterType.Vsv);
            double l = rho * v * v;
            parameterMap.put(ParameterType.L, l);
        }
        if (isDefined(ParameterType.Vsh) == true && isDefined(ParameterType.N) == false) {
            double v = parameterMap.get(ParameterType.Vsh);
            double n = rho * v * v;
            parameterMap.put(ParameterType.N, n);
        }
        if (isDefined(ParameterType.ETA) == true && isDefined(ParameterType.A) == true
                && isDefined(ParameterType.L) == true && isDefined(ParameterType.F) == false) {
            double eta = parameterMap.get(ParameterType.ETA);
            double a = parameterMap.get(ParameterType.A);
            double l = parameterMap.get(ParameterType.L);
            double f = eta * (a - 2 * l);
            parameterMap.put(ParameterType.F, f);
        }
    }

    private void findIsotropicModuli() {
        int numDefined = 0;
        if (isDefined(ParameterType.LAMBDAplus2MU)) numDefined++;
        if (isDefined(ParameterType.LAMBDA)) numDefined++;
        if (isDefined(ParameterType.MU)) numDefined++;
        if (isDefined(ParameterType.KAPPA)) numDefined++;
        if (numDefined != 2) {
            return;
        }

        if (isDefined(ParameterType.LAMBDAplus2MU) && isDefined(ParameterType.LAMBDA)) {
            double lambdaPlus2Mu = parameterMap.get(ParameterType.LAMBDAplus2MU);
            double lambda = parameterMap.get(ParameterType.LAMBDA);
            double mu = (lambdaPlus2Mu - lambda) / 2.;
            double kappa = lambda + 2./3. * mu;
            parameterMap.put(ParameterType.MU, mu);
            parameterMap.put(ParameterType.KAPPA, kappa);
        } else if (isDefined(ParameterType.LAMBDAplus2MU) && isDefined(ParameterType.MU)) {
            double lambdaPlus2Mu = parameterMap.get(ParameterType.LAMBDAplus2MU);
            double mu = parameterMap.get(ParameterType.MU);
            double lambda = lambdaPlus2Mu - 2. * mu;
            double kappa = lambda + 2./3. * mu;
            parameterMap.put(ParameterType.LAMBDA, lambda);
            parameterMap.put(ParameterType.KAPPA, kappa);
        } else if (isDefined(ParameterType.LAMBDAplus2MU) && isDefined(ParameterType.KAPPA)) {
            double lambdaPlus2Mu = parameterMap.get(ParameterType.LAMBDAplus2MU);
            double kappa = parameterMap.get(ParameterType.KAPPA);
            double mu = (lambdaPlus2Mu - kappa) * 3./4.;
            double lambda = lambdaPlus2Mu - 2. * mu;
            parameterMap.put(ParameterType.LAMBDA, lambda);
            parameterMap.put(ParameterType.MU, mu);
        } else if (isDefined(ParameterType.LAMBDA) && isDefined(ParameterType.MU)) {
            double lambda = parameterMap.get(ParameterType.LAMBDA);
            double mu = parameterMap.get(ParameterType.MU);
            double lambdaPlus2Mu = lambda + 2. * mu;
            double kappa = lambda + 2./3. * mu;
            parameterMap.put(ParameterType.LAMBDAplus2MU, lambdaPlus2Mu);
            parameterMap.put(ParameterType.KAPPA, kappa);
        } else if (isDefined(ParameterType.LAMBDA) && isDefined(ParameterType.KAPPA)) {
            double lambda = parameterMap.get(ParameterType.LAMBDA);
            double kappa = parameterMap.get(ParameterType.KAPPA);
            double mu = (kappa - lambda) * 3./2.;
            double lambdaPlus2Mu = lambda + 2. * mu;
            parameterMap.put(ParameterType.LAMBDAplus2MU, lambdaPlus2Mu);
            parameterMap.put(ParameterType.MU, mu);
        } else if (isDefined(ParameterType.MU) && isDefined(ParameterType.KAPPA)) {
            double mu = parameterMap.get(ParameterType.MU);
            double kappa = parameterMap.get(ParameterType.KAPPA);
            double lambda = kappa - 2./3. * mu;
            double lambdaPlus2Mu = lambda + 2. * mu;
            parameterMap.put(ParameterType.LAMBDAplus2MU, lambdaPlus2Mu);
            parameterMap.put(ParameterType.LAMBDA, lambda);
        }
    }

    private void convertToIsotropicModuli() {
        if (!isDefined(ParameterType.RHO)) return;
        double rho = parameterMap.get(ParameterType.RHO);

        if (isDefined(ParameterType.Vp) == true && isDefined(ParameterType.LAMBDAplus2MU) == false) {
            double vp = parameterMap.get(ParameterType.Vp);
            double lambdaPlus2Mu = rho * vp * vp;
            parameterMap.put(ParameterType.LAMBDAplus2MU, lambdaPlus2Mu);
        }
        if (isDefined(ParameterType.Vs) == true && isDefined(ParameterType.MU) == false) {
            double vs = parameterMap.get(ParameterType.Vs);
            double mu = rho * vs * vs;
            parameterMap.put(ParameterType.MU, mu);
        }
        if (isDefined(ParameterType.Vb) == true && isDefined(ParameterType.KAPPA) == false) {
            double vb = parameterMap.get(ParameterType.Vb);
            double kappa = rho * vb * vb;
            parameterMap.put(ParameterType.KAPPA, kappa);
        }
    }

    private void convertToIsotropicVelocities() {
        if (!isDefined(ParameterType.RHO)) return;
        double rho = parameterMap.get(ParameterType.RHO);

        if (isDefined(ParameterType.LAMBDAplus2MU) == true && isDefined(ParameterType.Vp) == false) {
            double lambdaPlus2Mu = parameterMap.get(ParameterType.LAMBDAplus2MU);
            double vp = Math.sqrt(lambdaPlus2Mu/rho);
            parameterMap.put(ParameterType.Vp, vp);
        }
        if (isDefined(ParameterType.MU) == true && isDefined(ParameterType.Vs) == false) {
            double mu = parameterMap.get(ParameterType.MU);
            double vs = Math.sqrt(mu/rho);
            parameterMap.put(ParameterType.Vs, vs);
        }
        if (isDefined(ParameterType.KAPPA) == true && isDefined(ParameterType.Vb) == false) {
            double kappa = parameterMap.get(ParameterType.KAPPA);
            double vb = Math.sqrt(kappa/rho);
            parameterMap.put(ParameterType.Vb, vb);
        }
    }

    public boolean isDefined(ParameterType type) {
        return parameterMap.containsKey(type);
    }
}
