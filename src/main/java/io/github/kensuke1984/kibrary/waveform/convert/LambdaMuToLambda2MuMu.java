package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.FrequencyRange;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;
import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

/**
 * Convert partial derivatives for LAMBDA and MU to derivatives for P- and S-velocity moduli, M and G
 * @author anselme
 */
public class LambdaMuToLambda2MuMu {

    /**
     * @param args: args[0]: path of partialID file; args[1]: path of partial file
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Path partialIDPath = Paths.get(args[0]);
        Path partialPath = Paths.get(args[1]);

        PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);

        List<PartialID> partialsLambda = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.LAMBDA3D)).collect(Collectors.toList());
        List<PartialID> partialsMU = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.MU3D)).collect(Collectors.toList());

        System.out.println(partialsMU.size());

        List<PartialID> partialsMUPrime = new ArrayList<>();
        List<PartialID> partialsLambda2mu = new ArrayList<>();

        final FullPosition[] locations = partialsMU.stream().map(p -> p.getVoxelPosition()).distinct().collect(Collectors.toList()).toArray(new FullPosition[0]);
        final PartialID[] partialsOrder = partialsMU.stream().parallel().filter(p -> p.getVoxelPosition().equals(locations[0])).collect(Collectors.toList()).toArray(new PartialID[0]);

        int[] indexOrderedMU = new int[partialsMU.size()];
        int[] indexOrderedLambda = new int[partialsMU.size()];

        IntStream.range(0, partialsMU.size()).parallel().forEach(i -> {
            int index = whichTimewindow(partialsMU.get(i), partialsOrder) * locations.length + whichUnknown(partialsMU.get(i), locations);
            indexOrderedMU[index] = i;
        });
        IntStream.range(0, partialsLambda.size()).parallel().forEach(i -> {
            int index = whichTimewindow(partialsLambda.get(i), partialsOrder) * locations.length + whichUnknown(partialsLambda.get(i), locations);
            indexOrderedLambda[index] = i;
        });

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
                muPrimeData[j] = muData[j] - 2. * lambdaData[j];

            PartialID parMuPrime = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.MU, partialLambda.getVoxelPosition(), muPrimeData);

            PartialID parLambda2mu = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
                    partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
                    partialLambda.getPhases(), partialLambda.isConvolved(),
                    ParameterType.VOXEL, VariableType.G, partialLambda.getVoxelPosition(), lambdaData);

            partialsMUPrime.add(parMuPrime);
            partialsLambda2mu.add(parLambda2mu);
        }

        String tmpString = GadgetAid.getTemporaryString();
        Path outID = Paths.get("partialID" + tmpString +".dat");
        Path out = Paths.get("partial" + tmpString + ".dat");

        Set<Observer> stationSet = partialsMU.stream().map(p -> p.getObserver()).collect(Collectors.toSet());
        Set<GlobalCMTID> globalCMTIDSet = partialsMU.stream().map(p -> p.getGlobalCMTID()).collect(Collectors.toSet());

//		double[][] periodRanges = new double[][] {{1./0.136, 100.}};
//		Phase[] phases = new Phase[] {Phase.P, Phase.PcP};

//		double[][] periodRanges = new double[][] {{1./0.08, 100.}};
//		Phase[] phases = new Phase[] {Phase.S, Phase.ScS};

        Set<Phase> phaseSet = new HashSet<>();
        Stream.of(partialsOrder).map(p -> new Phases(p.getPhases())).collect(Collectors.toSet()).forEach(p -> p.toSet().forEach(pp -> phaseSet.add(pp)));
        Phase[] phases = phaseSet.stream().collect(Collectors.toList()).toArray(new Phase[0]);

        List<double[]> periodRangeList = Stream.of(partialsOrder).map(p -> new FrequencyRange(1./p.getMaxPeriod(), 1./p.getMinPeriod())).distinct().map(f -> f.toPeriodRange()).collect(Collectors.toList());
        double[][] periodRanges = new double[periodRangeList.size()][];
        for (int i = 0; i < periodRangeList.size(); i++) {
            periodRanges[i] = periodRangeList.get(i);
        }

        Set<FullPosition> locationSet = Stream.of(locations).collect(Collectors.toSet());

        WaveformDataWriter writer = new WaveformDataWriter(outID, out, stationSet, globalCMTIDSet, periodRanges, phases, locationSet);

        for (PartialID partial : partialsMUPrime)
            writer.addPartialID(partial);

        for (PartialID partial : partialsLambda2mu)
            writer.addPartialID(partial);

        writer.close();
    }

    private static int whichTimewindow(PartialID partial, PartialID[] partialsOrder) {
        for (int i = 0; i < partialsOrder.length; i++) {
            PartialID par = partialsOrder[i];
            if (partial.getGlobalCMTID().equals(par.getGlobalCMTID())
                    && partial.getObserver().equals(par.getObserver())
                    && partial.getSacComponent().equals(par.getSacComponent())
                    && new Phases(partial.getPhases()).equals(new Phases(par.getPhases()))
                    && Math.abs(partial.getStartTime() - par.getStartTime()) < 1.01) {
                return i;
            }
        }
        return -1;
    }

    private static int whichUnknown(PartialID partial, FullPosition[] locations) {
        for (int i = 0; i < locations.length; i++) {
            if (partial.getVoxelPosition().equals(locations[i])) {
                return i;
            }
        }
        return -1;
    }

}
