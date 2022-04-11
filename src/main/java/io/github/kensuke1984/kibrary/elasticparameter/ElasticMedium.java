package io.github.kensuke1984.kibrary.elasticparameter;

import java.util.HashMap;
import java.util.Map;

import io.github.kensuke1984.kibrary.util.earth.ParameterType;

/**
 * Class for computing unknown parameters when some parameters are already known.
 *
 * @author otsuru
 * @since 2022//4/11
 */
public class ElasticMedium {

    private Map<ParameterType, Double> parameterMap = new HashMap<>();
    private boolean isAddingIsotropicModuli;
    private boolean isAddingIsotropicVelocities;

    public ElasticMedium() {
    }

    public void set(ParameterType type, double value) {
        if (!isFineAddition(type)) throw new IllegalArgumentException(type + " cannot be added");

        if (ParameterType.isIsotropicModulus(type)) isAddingIsotropicModuli = true;
        else if (ParameterType.isIsotropicVelocity(type)) isAddingIsotropicVelocities = true;

        parameterMap.put(type, value);
        calculateWhenAdding(type);
    }

    public double get(ParameterType type) {
        if (!isDefined(type)) throw new IllegalArgumentException("Not enough information is given to define " + type);
        return parameterMap.get(type);
    }

    private boolean isFineAddition(ParameterType type) {
        if (isDefined(type)) return false;
        if (ParameterType.isIsotropicModulus(type) && isAddingIsotropicVelocities) return false;
        if (ParameterType.isIsotropicVelocity(type) && isAddingIsotropicModuli) return false;
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
        switch(type) {
        case RHO:
            convertToIsotropicModuli();
            findIsotropicModuli();
            convertToIsotropicVelocities();
            break;
        case Vp:
        case Vs:
        case Vb:
            convertToIsotropicModuli();
            //fall through
        case LAMBDAplus2MU:
        case LAMBDA:
        case MU:
        case KAPPA:
            findIsotropicModuli();
            convertToIsotropicVelocities();
            break;

        default:
            throw new IllegalArgumentException("Illegal parameter type");
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
