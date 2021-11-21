package io.github.kensuke1984.kibrary.waveform.convert;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
import io.github.kensuke1984.kibrary.util.addons.FrequencyRange;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;
import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

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

public class LambdaMuToGR {
	
	public static void main(String[] args) throws IOException {
		Path partialIDPath = Paths.get(args[0]);
		Path partialPath = Paths.get(args[1]);
		
		PolynomialStructure structure = PolynomialStructure.AK135;
		
		System.out.println("Using " + structure);
		
		PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);
		
		List<PartialID> partialsLambda = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.LAMBDA)).collect(Collectors.toList());
		List<PartialID> partialsMU = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.MU)).collect(Collectors.toList());
		
		System.out.println(partialsMU.size());
		
		List<PartialID> partialsG = new ArrayList<>();
		List<PartialID> partialsR = new ArrayList<>();
		
		final FullPosition[] locations = partialsMU.stream().map(p -> p.getPerturbationLocation()).distinct().collect(Collectors.toList()).toArray(new FullPosition[0]);
		final PartialID[] partialsOrder = partialsMU.stream().parallel().filter(p -> p.getPerturbationLocation().equals(locations[0])).collect(Collectors.toList()).toArray(new PartialID[0]);
		
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
		
		for (int i = 0; i < partialsMU.size(); i++) {
			PartialID partialMU = partialsMU.get(indexOrderedMU[i]);
			PartialID partialLambda = partialsLambda.get(indexOrderedLambda[i]);
			if (!(partialLambda.getGlobalCMTID().equals(partialMU.getGlobalCMTID())
					&& partialLambda.getObserver().equals(partialMU.getObserver())
					&& partialLambda.getPerturbationLocation().equals(partialMU.getPerturbationLocation())
					&& partialLambda.getSacComponent().equals(partialMU.getSacComponent())
					&& new Phases(partialLambda.getPhases()).equals(new Phases(partialMU.getPhases())))) {
				System.out.println(partialMU + " ::: " + partialLambda);
				throw new RuntimeException("Partials order differ");
			}
			
			double r = partialMU.getPerturbationLocation().getR();
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
					partialLambda.getPhases(), partialLambda.getStartByte(), partialLambda.isConvolute(), partialLambda.getPerturbationLocation()
					, PartialType.MU, gData);
			
			PartialID parR = new PartialID(partialLambda.getObserver(), partialLambda.getGlobalCMTID(), partialLambda.getSacComponent(), partialLambda.getSamplingHz(),
					partialLambda.getStartTime(), partialLambda.getNpts(), partialLambda.getMinPeriod(), partialLambda.getMaxPeriod(),
					partialLambda.getPhases(), partialLambda.getStartByte(), partialLambda.isConvolute(), partialLambda.getPerturbationLocation()
					, PartialType.R, rData);
			
			partialsG.add(parG);
			partialsR.add(parR);
		}
		
		String tmpString = GadgetUtils.getTemporaryString();
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
		
		for (PartialID partial : partialsG)
			writer.addPartialID(partial);
		
		for (PartialID partial : partialsR)
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
			if (partial.getPerturbationLocation().equals(locations[i])) {
				return i;
			}
		}
		return -1;
	}
	
}
