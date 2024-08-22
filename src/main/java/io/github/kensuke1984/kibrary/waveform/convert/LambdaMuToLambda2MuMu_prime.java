package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;

public class LambdaMuToLambda2MuMu_prime {

    public static void main(String[] args) throws IOException {

//		for (PartialID partialMU : partialsMU) {
//			for (PartialID par : tmpPartialsLambda) {
//				if (par.getGlobalCMTID().equals(partialMU.getGlobalCMTID())
//					&& par.getStation().equals(partialMU.getStation())
//					&& par.getPerturbationLocation().equals(partialMU.getPerturbationLocation())
//					&& par.getSacComponent().equals(partialMU.getSacComponent())
//					&& new Phases(par.getPhases()).equals(new Phases(partialMU.getPhases()))) {
//						partialsLambda.add(par);
//
//						PartialID parKappa = partialMU;
//						double[] muData = partialMU.getData();
//						double[] lambdaData = par.getData();
//						double[] kappaData = new double[muData.length];
//						for (int i = 0; i < muData.length; i++)
//							kappaData[i] = lambdaData[i] + 2. * muData[i];
//
//						parKappa = parKappa.setData(kappaData);
//						partialsLambda2MU.add(parKappa);
//
//						break;
//				}
//			}
//		}
/*
        // fast version
        for (int i = 0; i < partialsMU.size(); i++) {
            PartialID partialMU = partialsMU.get(indexOrderedMU[i]);
            PartialID partialLambda = partialsLambda.get(indexOrderedLambda[i]);
            if (!(partialLambda.getGlobalCMTID().equals(partialMU.getGlobalCMTID())
                    && partialLambda.getObserver().equals(partialMU.getObserver())
                    && partialLambda.getVoxelPosition().equals(partialMU.getVoxelPosition())
                    && partialLambda.getSacComponent().equals(partialMU.getSacComponent())
                    && new Phases(partialLambda.getPhases()).equals(new Phases(partialMU.getPhases())))) {
                System.out.println(partialMU + " ::: " + partialLambda);
                throw new RuntimeException("Partials order differ");
            }

            SACComponent component = partialLambda.getSacComponent();

            double[] muPrimeData = partialMU.getData();
            double[] lambdaData = partialLambda.getData();
            if (component.equals(SACComponent.Z)) {
                for (int j = 0; j < lambdaData.length; j++)
                    lambdaData[j] = lambdaData[j] + .5 * muPrimeData[j];
                Arrays.fill(muPrimeData, 0.);
            }
            else if (component.equals(SACComponent.T)) {
                Arrays.fill(lambdaData, 0.);
            }

            PartialID parMuPrime = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData);

            PartialID parLambda2mu = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.LAMBDA2MU, partialLambda.getVoxelPosition(), lambdaData);

            partialsMUPrime.add(parMuPrime);
            partialsLambda2mu.add(parLambda2mu);
        }
*/

    }

}
