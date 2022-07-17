package io.github.kensuke1984.kibrary.elasticparameter;

import java.util.HashMap;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.earth.VariableType;

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

    private Map<VariableType, Double> variableMap = new HashMap<>();
    private boolean isAddingIsotropicModuli;
    private boolean isAddingIsotropicVelocities;
    private boolean isAddingTIModuli;
    private boolean isAddingTIVelocities;

    public ElasticMedium() {
    }

    public void set(VariableType type, double value) {
        if (!isFineAddition(type)) throw new IllegalArgumentException(type + " cannot be added");

        if (VariableType.isIsotropicModulus(type)) isAddingIsotropicModuli = true;
        else if (VariableType.isIsotropicVelocity(type)) isAddingIsotropicVelocities = true;
        else if (VariableType.isTIModulus(type)) isAddingTIModuli = true;
        else if (VariableType.isTIVelocity(type)) isAddingTIVelocities = true;

        variableMap.put(type, value);
        calculateWhenAdding(type);
    }

    public double get(VariableType type) {
        if (!isDefined(type)) throw new IllegalArgumentException("Not enough information is given to define " + type);
        return variableMap.get(type);
    }

    private boolean isFineAddition(VariableType type) {
        // not allowed to modify a parameter that is already defined
        if (isDefined(type)) return false;
        // when nothing has been started, anything is OK
        if (!isAddingIsotropicModuli && !isAddingIsotropicVelocities && !isAddingTIModuli && !isAddingTIVelocities) return true;
        // when some group has been started to be added, only its group can be added
        if (VariableType.isIsotropicModulus(type) && !isAddingIsotropicModuli) return false;
        if (VariableType.isIsotropicVelocity(type) && !isAddingIsotropicVelocities) return false;
        if (VariableType.isTIModulus(type) && !isAddingTIModuli) return false;
        if (VariableType.isTIVelocity(type) && !isAddingTIVelocities) return false;
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

    private void calculateWhenAdding(VariableType type) {
        if (type == VariableType.RHO) {
            convertToTIModuli();
            findTIModuli();
            convertToTIVelocities();

            tiToIsotropic();

            convertToIsotropicModuli();
            findIsotropicModuli();
            convertToIsotropicVelocities();

        } else if (VariableType.isIsotropicModulus(type)) {
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (VariableType.isIsotropicVelocity(type)) {
            convertToIsotropicModuli();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (VariableType.isTIModulus(type)) {
            findTIModuli();
            convertToTIVelocities();
            tiToIsotropic();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        } else if (VariableType.isTIVelocity(type)) {
            convertToTIModuli();
            findTIModuli();
            tiToIsotropic();
            findIsotropicModuli();
            convertToIsotropicVelocities();
        }
        // else : nothing has to be calculated
    }

    private void tiToIsotropic() {
        if (isDefined(VariableType.L) && isDefined(VariableType.N)) {
            double l = variableMap.get(VariableType.L);
            double n = variableMap.get(VariableType.N);
            double mu = (2 * l + n) / 3;
            variableMap.put(VariableType.MU, mu);
        }
        //TODO how to calculate LAMBDA?
    }

    private void findTIModuli() {
        int numDefined = 0;
        if (isDefined(VariableType.L)) numDefined++;
        if (isDefined(VariableType.N)) numDefined++;
        if (isDefined(VariableType.XI)) numDefined++;
        if (numDefined != 2) {
            return;
        }

        if (isDefined(VariableType.L) && isDefined(VariableType.N)) {
            double l = variableMap.get(VariableType.L);
            double n = variableMap.get(VariableType.N);
            double xi = n / l;
            variableMap.put(VariableType.XI, xi);
        } else if (isDefined(VariableType.L) && isDefined(VariableType.XI)) {
            double l = variableMap.get(VariableType.L);
            double xi = variableMap.get(VariableType.XI);
            double n = xi * l;
            variableMap.put(VariableType.N, n);
        } else if (isDefined(VariableType.N) && isDefined(VariableType.XI)) {
            double n = variableMap.get(VariableType.N);
            double xi = variableMap.get(VariableType.XI);
            double l = n / xi;
            variableMap.put(VariableType.L, l);
        }
    }

    private void convertToTIVelocities() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.A) == true && isDefined(VariableType.Vph) == false) {
            double a = variableMap.get(VariableType.A);
            double v = Math.sqrt(a / rho);
            variableMap.put(VariableType.Vph, v);
        }
        if (isDefined(VariableType.C) == true && isDefined(VariableType.Vpv) == false) {
            double c = variableMap.get(VariableType.C);
            double v = Math.sqrt(c / rho);
            variableMap.put(VariableType.Vpv, v);
        }
        if (isDefined(VariableType.L) == true && isDefined(VariableType.Vsv) == false) {
            double l = variableMap.get(VariableType.L);
            double v = Math.sqrt(l / rho);
            variableMap.put(VariableType.Vsv, v);
        }
        if (isDefined(VariableType.N) == true && isDefined(VariableType.Vsh) == false) {
            double n = variableMap.get(VariableType.N);
            double v = Math.sqrt(n / rho);
            variableMap.put(VariableType.Vsh, v);
        }
        if (isDefined(VariableType.F) == true && isDefined(VariableType.A) == true
                && isDefined(VariableType.L) == true && isDefined(VariableType.ETA) == false) {
            double a = variableMap.get(VariableType.A);
            double f = variableMap.get(VariableType.F);
            double l = variableMap.get(VariableType.L);
            double eta = f / (a - 2 * l);
            variableMap.put(VariableType.ETA, eta);
        }
    }
    private void convertToTIModuli() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.Vph) == true && isDefined(VariableType.A) == false) {
            double v = variableMap.get(VariableType.Vph);
            double a = rho * v * v;
            variableMap.put(VariableType.A, a);
        }
        if (isDefined(VariableType.Vpv) == true && isDefined(VariableType.C) == false) {
            double v = variableMap.get(VariableType.Vpv);
            double c = rho * v * v;
            variableMap.put(VariableType.C, c);
        }
        if (isDefined(VariableType.Vsv) == true && isDefined(VariableType.L) == false) {
            double v = variableMap.get(VariableType.Vsv);
            double l = rho * v * v;
            variableMap.put(VariableType.L, l);
        }
        if (isDefined(VariableType.Vsh) == true && isDefined(VariableType.N) == false) {
            double v = variableMap.get(VariableType.Vsh);
            double n = rho * v * v;
            variableMap.put(VariableType.N, n);
        }
        if (isDefined(VariableType.ETA) == true && isDefined(VariableType.A) == true
                && isDefined(VariableType.L) == true && isDefined(VariableType.F) == false) {
            double eta = variableMap.get(VariableType.ETA);
            double a = variableMap.get(VariableType.A);
            double l = variableMap.get(VariableType.L);
            double f = eta * (a - 2 * l);
            variableMap.put(VariableType.F, f);
        }
    }

    private void findIsotropicModuli() {
        int numDefined = 0;
        if (isDefined(VariableType.LAMBDAplus2MU)) numDefined++;
        if (isDefined(VariableType.LAMBDA)) numDefined++;
        if (isDefined(VariableType.MU)) numDefined++;
        if (isDefined(VariableType.KAPPA)) numDefined++;
        if (numDefined != 2) {
            return;
        }

        if (isDefined(VariableType.LAMBDAplus2MU) && isDefined(VariableType.LAMBDA)) {
            double lambdaPlus2Mu = variableMap.get(VariableType.LAMBDAplus2MU);
            double lambda = variableMap.get(VariableType.LAMBDA);
            double mu = (lambdaPlus2Mu - lambda) / 2.;
            double kappa = lambda + 2./3. * mu;
            variableMap.put(VariableType.MU, mu);
            variableMap.put(VariableType.KAPPA, kappa);
        } else if (isDefined(VariableType.LAMBDAplus2MU) && isDefined(VariableType.MU)) {
            double lambdaPlus2Mu = variableMap.get(VariableType.LAMBDAplus2MU);
            double mu = variableMap.get(VariableType.MU);
            double lambda = lambdaPlus2Mu - 2. * mu;
            double kappa = lambda + 2./3. * mu;
            variableMap.put(VariableType.LAMBDA, lambda);
            variableMap.put(VariableType.KAPPA, kappa);
        } else if (isDefined(VariableType.LAMBDAplus2MU) && isDefined(VariableType.KAPPA)) {
            double lambdaPlus2Mu = variableMap.get(VariableType.LAMBDAplus2MU);
            double kappa = variableMap.get(VariableType.KAPPA);
            double mu = (lambdaPlus2Mu - kappa) * 3./4.;
            double lambda = lambdaPlus2Mu - 2. * mu;
            variableMap.put(VariableType.LAMBDA, lambda);
            variableMap.put(VariableType.MU, mu);
        } else if (isDefined(VariableType.LAMBDA) && isDefined(VariableType.MU)) {
            double lambda = variableMap.get(VariableType.LAMBDA);
            double mu = variableMap.get(VariableType.MU);
            double lambdaPlus2Mu = lambda + 2. * mu;
            double kappa = lambda + 2./3. * mu;
            variableMap.put(VariableType.LAMBDAplus2MU, lambdaPlus2Mu);
            variableMap.put(VariableType.KAPPA, kappa);
        } else if (isDefined(VariableType.LAMBDA) && isDefined(VariableType.KAPPA)) {
            double lambda = variableMap.get(VariableType.LAMBDA);
            double kappa = variableMap.get(VariableType.KAPPA);
            double mu = (kappa - lambda) * 3./2.;
            double lambdaPlus2Mu = lambda + 2. * mu;
            variableMap.put(VariableType.LAMBDAplus2MU, lambdaPlus2Mu);
            variableMap.put(VariableType.MU, mu);
        } else if (isDefined(VariableType.MU) && isDefined(VariableType.KAPPA)) {
            double mu = variableMap.get(VariableType.MU);
            double kappa = variableMap.get(VariableType.KAPPA);
            double lambda = kappa - 2./3. * mu;
            double lambdaPlus2Mu = lambda + 2. * mu;
            variableMap.put(VariableType.LAMBDAplus2MU, lambdaPlus2Mu);
            variableMap.put(VariableType.LAMBDA, lambda);
        }
    }

    private void convertToIsotropicModuli() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.Vp) == true && isDefined(VariableType.LAMBDAplus2MU) == false) {
            double vp = variableMap.get(VariableType.Vp);
            double lambdaPlus2Mu = rho * vp * vp;
            variableMap.put(VariableType.LAMBDAplus2MU, lambdaPlus2Mu);
        }
        if (isDefined(VariableType.Vs) == true && isDefined(VariableType.MU) == false) {
            double vs = variableMap.get(VariableType.Vs);
            double mu = rho * vs * vs;
            variableMap.put(VariableType.MU, mu);
        }
        if (isDefined(VariableType.Vb) == true && isDefined(VariableType.KAPPA) == false) {
            double vb = variableMap.get(VariableType.Vb);
            double kappa = rho * vb * vb;
            variableMap.put(VariableType.KAPPA, kappa);
        }
    }

    private void convertToIsotropicVelocities() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.LAMBDAplus2MU) == true && isDefined(VariableType.Vp) == false) {
            double lambdaPlus2Mu = variableMap.get(VariableType.LAMBDAplus2MU);
            double vp = Math.sqrt(lambdaPlus2Mu/rho);
            variableMap.put(VariableType.Vp, vp);
        }
        if (isDefined(VariableType.MU) == true && isDefined(VariableType.Vs) == false) {
            double mu = variableMap.get(VariableType.MU);
            double vs = Math.sqrt(mu/rho);
            variableMap.put(VariableType.Vs, vs);
        }
        if (isDefined(VariableType.KAPPA) == true && isDefined(VariableType.Vb) == false) {
            double kappa = variableMap.get(VariableType.KAPPA);
            double vb = Math.sqrt(kappa/rho);
            variableMap.put(VariableType.Vb, vb);
        }
    }

    public boolean isDefined(VariableType type) {
        return variableMap.containsKey(type);
    }
}
