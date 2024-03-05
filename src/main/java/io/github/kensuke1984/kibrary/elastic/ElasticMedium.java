package io.github.kensuke1984.kibrary.elastic;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that holds a set of elastic parameters for of a block of homogenious isotropic or transversely isotropic (TI) medium.
 * Unknown parameters that can be computed from given parameters are automatically computed.
 * <p>
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
 * <p>
 * CAUTION, this class is <b>NOT IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2022//4/11
 */
public class ElasticMedium implements Cloneable {

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
            if (isDefined(ParameterType.LAMBDA2MU)) return false;
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
        case LAMBDA2MU:
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
        //Voigt approximation
        if (isDefined(VariableType.L) && isDefined(VariableType.N)) {
            if (isDefined(VariableType.A) && isDefined(VariableType.C) && isDefined(VariableType.F)) {
                double a = variableMap.get(VariableType.A);
                double c = variableMap.get(VariableType.C);
                double f = variableMap.get(VariableType.F);
                double l = variableMap.get(VariableType.L);
                double n = variableMap.get(VariableType.N);
                double kappa = (4 * a + c + 4 * f - 4 * n) / 9;
                double mu = (a + c - 2 * f + 5 * n + 6 * l) / 15;
                variableMap.put(VariableType.KAPPA, kappa);
                variableMap.put(VariableType.MU, mu);
            } else {
                //Only for MU (not for kappa)
                double l = variableMap.get(VariableType.L);
                double n = variableMap.get(VariableType.N);
                double mu = (2 * l + n) / 3;
                variableMap.put(VariableType.MU, mu);
            }
        }
    }

    //VRH (Voigt-Reuss-Hill) approximantion
    //TODO check accuracy
    private void tiToIsotropicByVRH() {
        if (isDefined(VariableType.L) && isDefined(VariableType.N) && isDefined(VariableType.A) && isDefined(VariableType.C) && isDefined(VariableType.F)) {
            double a = variableMap.get(VariableType.A);
            double c = variableMap.get(VariableType.C);
            double f = variableMap.get(VariableType.F);
            double l = variableMap.get(VariableType.L);
            double n = variableMap.get(VariableType.N);
            //Voigt approximation
            double kv = (4 * a + c + 4 * f - 4 * n) / 9;
            double mv = (a + c - 2 * f + 5 * n + 6 * l) / 15;
            //Reuss approximation
            double kr = 1 / ((a * c - f * f + 2 * a * n - 2 * n * n) / 2 / (a * c * n - c * n * n - f * f * n) + 2 * (2 / l + 1 / n));
            double mr = 15 / ((3 * a * c - 3 * f * f + 4 * a * n + 4 * f * n - 2 * c * n - n * n) / (a * c * n - c * n * n - f * f * n) + 3 * (2 / l + 1 / n));
            //Hill approximation
            double kappa = (kv + kr) / 2;
            double mu = (mv + mr)/ 2;
            variableMap.put(VariableType.KAPPA, kappa);
            variableMap.put(VariableType.MU, mu);
        }
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
        if (isDefined(VariableType.LAMBDA)) numDefined++;
        if (isDefined(VariableType.MU)) numDefined++;
        if (isDefined(VariableType.KAPPA)) numDefined++;
        if (isDefined(VariableType.G)) numDefined++;
        if (numDefined != 2) {
            return;
        }

        if (isDefined(VariableType.G) && isDefined(VariableType.MU)) {
            throw new IllegalArgumentException("Variable types G & MU are insufficient to convert parameters");
        }
        else if (isDefined(VariableType.G) && isDefined(VariableType.LAMBDA)) {
            double g = variableMap.get(VariableType.G);
            double lambda = variableMap.get(VariableType.LAMBDA);
            double mu = g;
            double kappa = lambda + 2./3. * g;
            variableMap.put(VariableType.MU, mu);
            variableMap.put(VariableType.KAPPA, kappa);
        } else if (isDefined(VariableType.G) && isDefined(VariableType.KAPPA)) {
            double g = variableMap.get(VariableType.G);
            double kappa = variableMap.get(VariableType.KAPPA);
            double mu = g;
            double lambda = kappa - 2./3. * g;
            variableMap.put(VariableType.LAMBDA, lambda);
            variableMap.put(VariableType.MU, mu);
        } else if (isDefined(VariableType.LAMBDA) && isDefined(VariableType.MU)) {
            double lambda = variableMap.get(VariableType.LAMBDA);
            double mu = variableMap.get(VariableType.MU);
            double g = mu;
            double kappa = lambda + 2./3. * mu;
            variableMap.put(VariableType.G, g);
            variableMap.put(VariableType.KAPPA, kappa);
        } else if (isDefined(VariableType.LAMBDA) && isDefined(VariableType.KAPPA)) {
            double lambda = variableMap.get(VariableType.LAMBDA);
            double kappa = variableMap.get(VariableType.KAPPA);
            double mu = (kappa - lambda) * 3./2.;
            double g = mu;
            variableMap.put(VariableType.G, g);
            variableMap.put(VariableType.MU, mu);
        } else if (isDefined(VariableType.MU) && isDefined(VariableType.KAPPA)) {
            double mu = variableMap.get(VariableType.MU);
            double kappa = variableMap.get(VariableType.KAPPA);
            double lambda = kappa - 2./3. * mu;
            double g = mu;
            variableMap.put(VariableType.G, g);
            variableMap.put(VariableType.LAMBDA, lambda);
        }
    }

    private void convertToIsotropicModuli() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.Vs) == true && isDefined(VariableType.MU) == false) {
            double vs = variableMap.get(VariableType.Vs);
            double mu = rho * vs * vs;
            variableMap.put(VariableType.MU, mu);
        }
        if (isDefined(VariableType.Vs) == true && isDefined(VariableType.G) == false) {
            double vs = variableMap.get(VariableType.G);
            double g = rho * vs * vs;
            variableMap.put(VariableType.G, g);
        }
        if (isDefined(VariableType.Vb) == true && isDefined(VariableType.KAPPA) == false) {
            double vb = variableMap.get(VariableType.Vb);
            double kappa = rho * vb * vb;
            variableMap.put(VariableType.KAPPA, kappa);
        }
        if (isDefined(VariableType.Vp) == true && isDefined(VariableType.LAMBDA) == false) {
            if (isDefined(VariableType.MU) == true) {
                double vp = variableMap.get(VariableType.Vp);
                double mu = variableMap.get(VariableType.MU);
                double lambda = rho * vp * vp - 2. * mu;
                variableMap.put(VariableType.LAMBDA, lambda);
            }
            else if (isDefined(VariableType.G) == true) {
                double vp = variableMap.get(VariableType.Vp);
                double g = variableMap.get(VariableType.G);
                double lambda = rho * vp * vp - 2. * g;
                variableMap.put(VariableType.LAMBDA, lambda);
            }
            else if (isDefined(VariableType.KAPPA) == true) {
                double vp = variableMap.get(VariableType.Vp);
                double kappa = variableMap.get(VariableType.KAPPA);
                double lambda = 1.5 * kappa - 0.5 * rho * vp * vp;
                variableMap.put(VariableType.LAMBDA, lambda);
            }
            else
                throw new IllegalArgumentException("Variable type is insufficient to compute LAMBDA");
        }
    }

    private void convertToIsotropicVelocities() {
        if (!isDefined(VariableType.RHO)) return;
        double rho = variableMap.get(VariableType.RHO);

        if (isDefined(VariableType.MU) == true && isDefined(VariableType.Vs) == false) {
            double mu = variableMap.get(VariableType.MU);
            double vs = Math.sqrt(mu/rho);
            variableMap.put(VariableType.Vs, vs);
        }
        if (isDefined(VariableType.G) == true && isDefined(VariableType.Vs) == false) {
            double g = variableMap.get(VariableType.G);
            double vs = Math.sqrt(g/rho);
            variableMap.put(VariableType.Vs, vs);
        }
        if (isDefined(VariableType.KAPPA) == true && isDefined(VariableType.Vb) == false) {
            double kappa = variableMap.get(VariableType.KAPPA);
            double vb = Math.sqrt(kappa/rho);
            variableMap.put(VariableType.Vb, vb);
        }
        if (isDefined(VariableType.LAMBDA) == true && isDefined(VariableType.Vp) == false) {
            if (isDefined(VariableType.MU) == true) {
                double lambda = variableMap.get(VariableType.LAMBDA);
                double mu = variableMap.get(VariableType.MU);
                double vp = Math.sqrt((lambda  + 2. * mu)/rho);
                variableMap.put(VariableType.Vp, vp);
            }
            else if (isDefined(VariableType.G) == true) {
                double lambda = variableMap.get(VariableType.LAMBDA);
                double g = variableMap.get(VariableType.G);
                double vp = Math.sqrt((lambda  + 2. * g)/rho);
                variableMap.put(VariableType.Vp, vp);
            }
            else if (isDefined(VariableType.KAPPA) == true) {
                double lambda = variableMap.get(VariableType.LAMBDA);
                double kappa = variableMap.get(VariableType.KAPPA);
                double vp = Math.sqrt((3. * kappa - 2. * lambda)/rho);
                variableMap.put(VariableType.Vp, vp);
            }
        }
    }

    @Override
    public ElasticMedium clone() {
        try {
            ElasticMedium newInstance = (ElasticMedium) super.clone();
            newInstance.variableMap = new HashMap<>(this.variableMap);
            return newInstance;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isDefined(VariableType type) {
        return variableMap.containsKey(type);
    }
}
