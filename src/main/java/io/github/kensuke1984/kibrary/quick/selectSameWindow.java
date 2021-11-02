package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.Utilities;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class selectSameWindow {

	public static void main(String[] args) throws IOException {
		Path targetwindowPath = Paths.get(args[0]);
		Path otherwindowPath = Paths.get(args[1]);
		
		Set<TimewindowData> targetwindows = TimewindowDataFile.read(targetwindowPath);
		Set<TimewindowData> otherwindows = TimewindowDataFile.read(otherwindowPath);
		
		Set<TimewindowData> selectedwindow = new HashSet<>();
		
		for (TimewindowData window : targetwindows) {
			List<TimewindowData> tmplist = otherwindows.stream().parallel().filter(tw -> tw.getGlobalCMTID().equals(window.getGlobalCMTID())
					&& tw.getObserver().equals(window.getObserver())).collect(Collectors.toList());
			if (tmplist.size() == 1)
				selectedwindow.add(tmplist.get(0));
		}
		
		Path outpath = Paths.get("timewindow" + Utilities.getTemporaryString() + ".dat");
		TimewindowDataFile.write(selectedwindow, outpath);
		
	}

}
