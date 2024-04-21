package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;

public class LambdaMuToGR {

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
            double mu = structure.computeMu(r);
            double R = structure.getVshAt(r) / structure.getVphAt(r);

            double[] muData = partialMU.getData();
            double[] lambdaData = partialLambda.getData();
            double[] gData = new double[muData.length];
            double[] rData = new double[muData.length];
            for (int j = 0; j < muData.length; j++) {
                gData[i] = muData[i];
                rData[i] = -mu/(2 * Math.pow(R, 1.5)) * lambdaData[i];
            }

            PartialID parG = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.Vs, partialLambda.getVoxelPosition(), gData);

            PartialID parR = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.R, partialLambda.getVoxelPosition(), rData);

            partialsG.add(parG);
            partialsR.add(parR);
        }
*/
    }

}
