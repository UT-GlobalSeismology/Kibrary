package io.github.kensuke1984.kibrary.waveform.convert;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.FrequencyRange;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure_old;
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

import org.apache.commons.math3.linear.ArrayRealVector;

public class MuToVs {
	
	public static void main(String[] args) throws IOException {
		Path partialIDPath = Paths.get(args[0]);
		Path partialPath = Paths.get(args[1]);
		
		PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);
		
		List<PartialID> partialsMU = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.MU3D)).collect(Collectors.toList());
		
		System.out.println(partialsMU.size());
		
		PolynomialStructure_old structure = PolynomialStructure_old.PREM;
		
		List<PartialID> partialsVs = partialsMU.stream().map(p -> {
			double r = p.getVoxelPosition().getR();
			double[] vsData = new ArrayRealVector(p.getData()).mapMultiply(2 * structure.getRhoAt(r) * structure.getVshAt(r)).toArray();
			
			PartialID parVs = new PartialID(p.getObserver(), p.getGlobalCMTID(), p.getSacComponent(), p.getSamplingHz(),
					p.getStartTime(), p.getNpts(), p.getMinPeriod(), p.getMaxPeriod(),
					p.getPhases(), p.getStartByte(), p.isConvolved(), p.getVoxelPosition()
					, PartialType.VS3D, vsData);
			return parVs;
		}).collect(Collectors.toList());
		
		
		String tmpString = GadgetAid.getTemporaryString();
		Path outID = Paths.get("partialID" + tmpString +".dat");
		Path out = Paths.get("partial" + tmpString + ".dat");
		
		Set<Observer> stationSet = partialsMU.stream().map(p -> p.getObserver()).collect(Collectors.toSet());
		Set<GlobalCMTID> globalCMTIDSet = partialsMU.stream().map(p -> p.getGlobalCMTID()).collect(Collectors.toSet());
		
		Set<Phase> phaseSet = new HashSet<>();
		partialsMU.stream().map(p -> new Phases(p.getPhases())).collect(Collectors.toSet()).forEach(p -> p.toSet().forEach(pp -> phaseSet.add(pp)));
		Phase[] phases = phaseSet.stream().collect(Collectors.toList()).toArray(new Phase[0]);
		
		List<double[]> periodRangeList = partialsMU.stream().map(p -> new FrequencyRange(1./p.getMaxPeriod(), 1./p.getMinPeriod())).distinct().map(f -> f.toPeriodRange()).collect(Collectors.toList());
		double[][] periodRanges = new double[periodRangeList.size()][];
		for (int i = 0; i < periodRangeList.size(); i++) {
			periodRanges[i] = periodRangeList.get(i);
		}
		
		Set<FullPosition> locationSet = partialsMU.stream().map(p -> p.getVoxelPosition()).collect(Collectors.toSet());
		
		WaveformDataWriter writer = new WaveformDataWriter(outID, out, stationSet, globalCMTIDSet, periodRanges, phases, locationSet);
		
		for (PartialID partial : partialsVs)
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
