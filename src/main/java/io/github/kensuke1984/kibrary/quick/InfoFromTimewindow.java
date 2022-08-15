package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InfoFromTimewindow {

	public static void main(String[] args) throws IOException {
		Path timewindowpath = Paths.get(args[0]);
		String tmpString = GadgetAid.getTemporaryString();
		Path stationFile = Paths.get("station" + tmpString + ".inf");
		Path eventFile = Paths.get("event" + tmpString + ".inf");
		
		Set<TimewindowData> timewindows = TimewindowDataFile.read(timewindowpath);
		
		Files.deleteIfExists(stationFile);
		Files.createFile(stationFile);
		
		Files.deleteIfExists(eventFile);
		Files.createFile(eventFile);
		
		Set<Observer> usedStation = new HashSet<>();
		Map<GlobalCMTID, Integer> nTransverseMap = new HashMap<>();
		for (TimewindowData timewindow : timewindows) {
			GlobalCMTID event = timewindow.getGlobalCMTID();
			Integer itmp = new Integer(1);
			if (nTransverseMap.containsKey(event)) {
				itmp = nTransverseMap.get(event) + 1;
			}
			nTransverseMap.put(event, itmp);
			
			Observer sta = timewindow.getObserver();
			usedStation.add(sta);
			
			System.out.println((6371. - event.getEventData().getCmtLocation().getR()) + " " + Math.toDegrees(event.getEventData().getCmtLocation().computeEpicentralDistance(sta.getPosition())));
		}
		
		for (Observer sta : usedStation)
			Files.write(stationFile, (sta.getStation() + " " + sta.getNetwork() + " " + sta.getPosition()+"\n").getBytes(), StandardOpenOption.APPEND);
		
		for (GlobalCMTID id : nTransverseMap.keySet()) {
			System.out.println(id + " " + nTransverseMap.get(id));
			double depth = 6371 - id.getEventData().getCmtLocation().getR();
			double mw = id.getEventData().getCmt().getMw();
			double duration = id.getEventData().getHalfDuration() * 2;
			Files.write(eventFile, (id + " " + id.getEventData().getCmtLocation() + " " + mw + " " + duration + "\n").getBytes(), StandardOpenOption.APPEND);
		}
	}

}
