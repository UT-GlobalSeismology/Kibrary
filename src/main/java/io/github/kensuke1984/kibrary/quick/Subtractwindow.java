package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.GadgetUtils;

public class Subtractwindow {

	public static void main(String[] args) throws IOException {
		Path infoPath = Paths.get(args[0]);
		Path infoToSubtractPath = Paths.get(args[1]);
		
		Set<TimewindowData> windows = TimewindowDataFile.read(infoPath);
		Set<TimewindowData> windowsToSubtract = TimewindowDataFile.read(infoToSubtractPath);
		
		Set<TimewindowData> outWindows = new HashSet<>();
		
		for (TimewindowData window : windows) {
			if (!windowsToSubtract.contains(window))
				outWindows.add(window);
		}

		Path outputPath = Paths.get("timewindow" + GadgetUtils.getTemporaryString() + ".dat");
		TimewindowDataFile.write(outWindows, outputPath);
	}

}
