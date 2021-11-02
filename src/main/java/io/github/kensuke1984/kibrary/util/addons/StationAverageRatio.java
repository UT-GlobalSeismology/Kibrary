package io.github.kensuke1984.kibrary.util.addons;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.datacorrection.StaticCorrectionData;
import io.github.kensuke1984.kibrary.datacorrection.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StationAverageRatio {

	public static void main(String[] args) throws IOException {
		if (args.length != 2)
			System.err.println("StaticCorrectionPath timewindowPath");
		Path staticCorrectionPath = Paths.get(args[0]);
		Path timewindowPath = Paths.get(args[1]);
		
		Set<StaticCorrectionData> corrections = StaticCorrectionDataFile.read(staticCorrectionPath);
		Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowPath);

		Map<Observer, Double> stationAverages = new HashMap<Observer, Double>();
		Map<Observer, Integer> stationCount = new HashMap<Observer, Integer>();
		
		for (TimewindowData tw : timewindows) {
//			if (!tw.getGlobalCMTID().equals(new GlobalCMTID("200608250044A")))
//				continue;
			
			boolean contin = true;
			for (Phase p : tw.getPhases()) {
				if (p.equals(Phase.S) || p.equals(Phase.s))
					contin = false;
			}
//			Phases phases = new Phases(tw.getPhases());
//			if (phases.equals(new Phases(new Phase[] {Phase.S})) || phases.equals(new Phases(new Phase[] {Phase.s})))
//				contin = false;
			
			if (contin)
				continue;
			
			StaticCorrectionData correction = corrections.stream().filter(corr -> corr.getGlobalCMTID().equals(tw.getGlobalCMTID()) 
					&& corr.getObserver().equals(tw.getObserver())
					&& corr.getComponent().equals(tw.getComponent())
					&& corr.getSynStartTime() == tw.getStartTime())
					.findAny().get();
			
			Observer sta = correction.getObserver();
			Double ratio = correction.getAmplitudeRatio();
			if (stationAverages.containsKey(sta)) {
				ratio = ratio + stationAverages.get(sta);
				stationAverages.replace(sta, ratio);
				stationCount.replace(sta, stationCount.get(sta) + 1);
			}
			else {
				stationAverages.put(sta, ratio);
				stationCount.put(sta, 1);
			}
		}
		
		Path outpathP = Paths.get("stationAverageRatio_greater.inf");
		Path outpathM = Paths.get("stationAverageRatio_smaller.inf");
		Files.deleteIfExists(outpathP);
		Files.createFile(outpathP);
		Files.deleteIfExists(outpathM);
		Files.createFile(outpathM);
		for (Observer sta : stationCount.keySet()) {
			double ratio = stationAverages.get(sta) / stationCount.get(sta);
			if (ratio >= 1)
				Files.write(outpathP, (sta.getStation() + " " + sta.getNetwork() + " " + sta.getPosition() + " " + ratio + "\n").getBytes(), StandardOpenOption.APPEND);
			else
				Files.write(outpathM, (sta.getStation() + " " + sta.getNetwork() + " " + sta.getPosition() + " " + ratio + "\n").getBytes(), StandardOpenOption.APPEND);
		}
	}

}
