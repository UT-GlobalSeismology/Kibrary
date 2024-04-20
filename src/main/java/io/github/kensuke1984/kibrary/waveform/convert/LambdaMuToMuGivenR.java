package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;

public class LambdaMuToMuGivenR {

    public static void main(String[] args) throws IOException {
/*
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

            double r = partialMU.getVoxelPosition().getR();
            double depth = 6371. - r;
            double Rsp1 = rKRDH16.get(depth);
            double Rsp2 = rMLBD00.get(depth);
            double Rsp3 = rKK01ah.get(depth);
            double Rsp4 = rKK01ahae.get(depth);

            double vp = structure.getVphAt(r);
            double vs = structure.getVshAt(r);

            if (vs == 0)
                throw new RuntimeException("Unexpected zero Vs");
            if (Rsp1 == 0)
                throw new RuntimeException("Unexpected zero Rsp1");

            double[] muData = partialMU.getData();
            double[] lambdaData = partialLambda.getData();
            double[] muPrimeData1 = new double[muData.length];
            double[] muPrimeData2 = new double[muData.length];
            double[] muPrimeData3 = new double[muData.length];
            double[] muPrimeData4 = new double[muData.length];
            for (int j = 0; j < muData.length; j++) {
                muPrimeData1[j] = (muData[j] - 2 * lambdaData[j]) + 1./Rsp1*vp*vp/vs/vs * lambdaData[j];
                muPrimeData2[j] = (muData[j] - 2 * lambdaData[j]) + 1./Rsp2*vp*vp/vs/vs * lambdaData[j];
                muPrimeData3[j] = (muData[j] - 2 * lambdaData[j]) + 1./Rsp3*vp*vp/vs/vs * lambdaData[j];
                muPrimeData4[j] = (muData[j] - 2 * lambdaData[j]) + 1./Rsp4*vp*vp/vs/vs * lambdaData[j];
            }

            PartialID parMuPrime1 = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData1);
            PartialID parMuPrime2 = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData2);
            PartialID parMuPrime3 = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData3);
            PartialID parMuPrime4 = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData4);

            writer1.addPartialID(parMuPrime1);
            writer2.addPartialID(parMuPrime2);
            writer3.addPartialID(parMuPrime3);
            writer4.addPartialID(parMuPrime4);
        }
*/
    }

}
