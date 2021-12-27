package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.GadgetAid;

public class MergeWindow {

	public static void main(String[] args) throws IOException {
		Path infoPath = Paths.get(args[0]);
		Path infoPath2 = Paths.get(args[1]);
		
		Set<TimewindowData> windows = TimewindowDataFile.read(infoPath);
		Set<TimewindowData> windows2 = TimewindowDataFile.read(infoPath2);
		
		Set<TimewindowData> outWindows = new HashSet<>();
		
		for (TimewindowData window : windows)
			outWindows.add(window);
		for (TimewindowData window : windows2)
			outWindows.add(window);
		

		Path outputPath = Paths.get("timewindow" + GadgetAid.getTemporaryString() + ".dat");
		TimewindowDataFile.write(outWindows, outputPath);
	}

}
