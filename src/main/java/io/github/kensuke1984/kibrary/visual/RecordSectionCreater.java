package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

public class RecordSectionCreater {

    public static void main(String[] args) throws IOException {
        Path basicIDPath = Paths.get(args[0]);
        SACComponent component = null;
        switch(args[1]) {
        case "Z":
            component = SACComponent.getComponent(1);
            break;
        case "R":
            component = SACComponent.getComponent(2);
            break;
        case "T":
            component = SACComponent.getComponent(3);
            break;
        default:
            System.err.println("Component is illegal");
            return;
        }

        BasicID[] ids = BasicIDFile.read(basicIDPath);

        Path workPath = Paths.get(".");
        Set<EventFolder> eventDirs = DatasetUtils.eventFolderSet(workPath);

        for (EventFolder eventDir : eventDirs) {
            BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID()))
                    .sorted(Comparator.comparing(BasicID::getObserver)).collect(Collectors.toList()).toArray(new BasicID[0]);
            createRecordSection(eventDir, useIds, component);
        }
    }

    private static void createRecordSection(EventFolder eventDir, BasicID[] ids, SACComponent component) throws IOException {
        List<BasicID> obsList = new ArrayList<>();
        List<BasicID> synList = new ArrayList<>();
        BasicIDFile.pairUp(ids, obsList, synList);

    }
}
