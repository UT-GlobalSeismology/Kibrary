package io.github.kensuke1984.kibrary.waveform.addons;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.inversion.addons.HorizontalParameterMapping;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
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
import org.apache.commons.math3.linear.RealVector;


public class CombineParameters {

	public static void main(String[] args) {
		Path partialIDPath = Paths.get(args[0]);
		Path partialPath = Paths.get(args[1]);
		Path unknownPath = Paths.get(args[2]);
		Path horizontalMappingPath = Paths.get(args[3]);
		
		String tmpString = GadgetAid.getTemporaryString();
		Path outIDPath = Paths.get("partialID" + tmpString + ".dat");
		Path outPath = Paths.get("partial" + tmpString + ".dat");
		
		try {
			PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);
			UnknownParameter[] originalUnknowns = UnknownParameterFile.read(unknownPath).toArray(new UnknownParameter[0]);
			
			HorizontalParameterMapping mapping = new HorizontalParameterMapping(originalUnknowns, horizontalMappingPath);
			
			UnknownParameter[] newUnknowns = mapping.getUnknowns();
			
			int nNew = mapping.getNnew();
			
			Set<Observer> stationSet = Stream.of(partials).parallel().map(par -> par.getObserver()).collect(Collectors.toSet());
			Set<GlobalCMTID> globalCMTIDSet = Stream.of(partials).parallel().map(par -> par.getGlobalCMTID()).collect(Collectors.toSet());
			
			double[][] periodRanges = new double[][] {{partials[0].getMinPeriod(), partials[0].getMaxPeriod()}};
			Set<FullPosition> perturbationPoints = Stream.of(newUnknowns).parallel().map(u -> u.getPosition()).collect(Collectors.toSet());
			
//			Phase[] phases = new Phase[] {Phase.ScS, Phase.S};
//			Phase[] phases = new Phase[] {Phase.PcP, Phase.P};
			
			Set<Phase> phaseSet = new HashSet<>();
			Stream.of(partials).parallel().map(par -> new Phases(par.getPhases())).distinct().forEach(ps -> {
				for (Phase p : ps.toSet())
					phaseSet.add(p);
			});
			Phase[] phases = phaseSet.toArray(new Phase[phaseSet.size()]);
			System.out.print("Found phases ");
			for (Phase p : phases)
				System.out.print(p + " ");
			System.out.println();
			
			WaveformDataWriter writer = new WaveformDataWriter(outIDPath, outPath, stationSet, globalCMTIDSet, periodRanges, phases, perturbationPoints);
			
			globalCMTIDSet.stream().forEach(event -> {
				List<PartialID> eventPartials = Stream.of(partials).filter(par -> par.getGlobalCMTID().equals(event)).collect(Collectors.toList());
				stationSet.stream().forEach(station -> {
					List<PartialID> stationPartials = eventPartials.stream().filter(par -> par.getObserver().equals(station)).collect(Collectors.toList());
					if (stationPartials.size() > 0) {
						IntStream.range(0, nNew).forEach(inew -> {
							int[] iOriginals = mapping.getiNewToOriginal(inew);
							PartialID refID = stationPartials.get(0);
							RealVector dataVector = new ArrayRealVector(refID.getNpts());
							
							System.out.println("---> " + newUnknowns[inew]);
							
							for (int iOriginal : iOriginals) {
								UnknownParameter unknown = originalUnknowns[iOriginal];
								System.out.println(unknown);
								
//								double weight = unknown.getWeighting();
								
								double weight = 1.;
								
								List<PartialID> tmpIDList = stationPartials.stream().parallel().filter(par -> par.getPerturbationLocation().equals(unknown.getPosition())
										&& par.getPartialType().equals(unknown.getPartialType())).collect(Collectors.toList());
								if (tmpIDList.size() != 1)
									throw new RuntimeException("Found more than one partialID " + tmpIDList.size());
								PartialID tmpID = tmpIDList.get(0);
								
								dataVector = dataVector.add(new ArrayRealVector(tmpID.getData()).mapMultiply(weight));
								
//								System.out.println(unknown.getWeighting() + " "  + dataVector.getNorm());
							}
							
							PartialID tmpPartial = new PartialID(refID.getObserver(), refID.getGlobalCMTID(), refID.getSacComponent(), refID.getSamplingHz()
									, refID.getStartTime(), refID.getNpts(), refID.getMinPeriod(), refID.getMaxPeriod(), refID.getPhases(), refID.getStartByte()
									, refID.isConvolute(), newUnknowns[inew].getPosition(), newUnknowns[inew].getPartialType(), dataVector.toArray());
							try {
								writer.addPartialID(tmpPartial);
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
					}
				});
			});
				
			writer.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		

	}

}
