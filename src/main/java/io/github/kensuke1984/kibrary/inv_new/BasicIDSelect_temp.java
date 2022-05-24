package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

public class BasicIDSelect_temp {

    public static void main(String[] args) throws IOException {
        Path workPath = Paths.get(".");
        Path srcID = workPath.resolve(args[0]);
        BasicID[] basicIDs = BasicIDFile.read(srcID);

        Set<BasicID> newSet = new HashSet<>();

        for (BasicID basic : basicIDs) {
            if (basic.getObserver().getPosition().getLatitude() < -10) {
                newSet.add(basic);
            }
        }

        // extract set of observers, events, periods, and phases
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        newSet.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            boolean add = true;
            for (double[] periods : periodSet) {
                if (id.getMinPeriod() == periods[0] && id.getMaxPeriod() == periods[1])
                    add = false;
            }
            if (add)
                periodSet.add(new double[] {id.getMinPeriod(), id.getMaxPeriod()});
            for (Phase phase : id.getPhases())
                phaseSet.add(phase);
        });
        double[][] periodRanges = new double[periodSet.size()][];
        int j = 0;
        for (double[] periods : periodSet)
            periodRanges[j++] = periods;
        Phase[] phases = phaseSet.toArray(new Phase[phaseSet.size()]);

        String dateStr = GadgetAid.getTemporaryString();
        Path outputIDPath = workPath.resolve(DatasetAid.generateOutputFileName("basicID", "south", dateStr, ".dat"));
        Path outputWavePath = workPath.resolve(DatasetAid.generateOutputFileName("basic", "south", dateStr, ".dat"));

        System.err.println("Outputting in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath, observerSet, eventSet, periodRanges, phases)) {
            newSet.forEach(id -> {
                try {
                    wdw.addBasicID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

    }
}
