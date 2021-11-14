package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.Earth;
import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.FullPosition;
import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class testTimewindowSet {

	public static void main(String[] args) {
		Set<TimewindowData> timewindows = getTestwindows();
		Path outputPath = Paths.get("testTimewindow.inf");
		
		try {
			TimewindowDataFile.write(timewindows, outputPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static Set<TimewindowData> getTestwindows() {
		GlobalCMTID id = new GlobalCMTID("200503070717A");
		Observer station = new Observer("340A", new HorizontalPosition(31.41670036315918, -93.88960266113281), "TA");
		
		FullPosition loc = id.getEvent().getCmtLocation();
		double distance = loc.getEpicentralDistance(station.getPosition()) * 180. / Math.PI ;
		
//		System.out.println((Earth.EARTH_RADIUS - loc.getR()) + " " + distance);
		
		Set<TimewindowData> timewindows = new HashSet<>();
		TimewindowData window1 = new TimewindowData(415., 495., station, id, SACComponent.T, new Phase[] {Phase.S});
		TimewindowData window2 = new TimewindowData(816., 896., station, id, SACComponent.T, new Phase[] {Phase.ScS});
		TimewindowData window3 = new TimewindowData(1732., 1812., station, id, SACComponent.T, new Phase[] {Phase.create("ScSScS")});
		
		timewindows.add(window1);
		timewindows.add(window2);
		timewindows.add(window3);
		return timewindows;
	}
	
}
