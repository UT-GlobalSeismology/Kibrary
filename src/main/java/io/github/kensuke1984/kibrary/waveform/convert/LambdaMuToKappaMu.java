package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;


public class LambdaMuToKappaMu {

    public static void main(String[] args) throws IOException {

        // slow version
//		for (PartialID partialMU : partialsMU) {
//		partialsMU.stream().parallel().forEach(partialMU -> {
//			for (PartialID partialLambda : partialsLambda) {
//				if (partialLambda.getGlobalCMTID().equals(partialMU.getGlobalCMTID())
//					&& partialLambda.getStation().equals(partialMU.getStation())
//					&& partialLambda.getPerturbationLocation().equals(partialMU.getPerturbationLocation())
//					&& partialLambda.getSacComponent().equals(partialMU.getSacComponent())
//					&& new Phases(partialLambda.getPhases()).equals(new Phases(partialMU.getPhases()))) {
//
//						double[] muData = partialMU.getData();
//						double[] lambdaData = partialLambda.getData();
//						double[] muPrimeData = new double[muData.length];
//						for (int j = 0; j < muData.length; j++)
//							muPrimeData[j] = muData[j] - 2./3. * lambdaData[j];
//
//						PartialID parMuPrime = new PartialID(partialLambda.getStation(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
//								partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
//								partialLambda.getPhases(), partialLambda.getStartByte(), partialLambda.isConvolute(), partialLambda.getPerturbationLocation()
//								, PartialType.MU, muPrimeData);
//
//						PartialID parKappa = new PartialID(partialLambda.getStation(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
//								partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
//								partialLambda.getPhases(), partialLambda.getStartByte(), partialLambda.isConvolute(), partialLambda.getPerturbationLocation()
//								, PartialType.KAPPA, lambdaData);
//
//						partialsMUPrime.add(parMuPrime);
//						partialsKappa.add(parKappa);
//
//						System.out.println(parKappa + " "  + parKappa.TYPE);
//						System.out.println(parMuPrime + " "  + parMuPrime.TYPE);
//
//						break;
//				}
//			}
//		});
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

                double[] muData = partialMU.getData();
                double[] lambdaData = partialLambda.getData();
                double[] muPrimeData = new double[muData.length];
                for (int j = 0; j < muData.length; j++)
                    muPrimeData[j] = muData[j] - 2./3. * lambdaData[j];

                PartialID parMuPrime = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                        partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                        partialLambda.getPhases(), partialLambda.isConvolved(),
                        ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData);

                PartialID parKappa = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                        partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                        partialLambda.getPhases(), partialLambda.isConvolved(),
                        ParameterType.VOXEL, VariableType.KAPPA, partialLambda.getVoxelPosition(), lambdaData);

                partialsMUPrime.add(parMuPrime);
                partialsKappa.add(parKappa);
        }
*/

    }

}
