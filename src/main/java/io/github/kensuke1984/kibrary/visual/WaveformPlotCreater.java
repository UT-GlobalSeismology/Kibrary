package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

public class WaveformPlotCreater {

    public static void main(String[] args) throws IOException {
        BasicID[] ids = BasicIDFile.read(Paths.get(args[1]));

        Path workPath = Paths.get(".");
        Set<EventFolder> eventDirs = DatasetUtils.eventFolderSet(workPath);

        for (EventFolder eventDir : eventDirs) {
            BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID()))
                    .collect(Collectors.toList()).toArray(new BasicID[0]);
            createPlot(eventDir, useIds);
        }

    }

    private static void createPlot(EventFolder eventDir, BasicID[] ids) throws IOException {
        List<BasicID> obsList = new ArrayList<>();
        List<BasicID> synList = new ArrayList<>();
        BasicIDFile.pairUp(ids, obsList, synList);

        GnuplotFile gnuplot = new GnuplotFile(eventDir.toPath().resolve(eventDir.toString() + ".plt"));
        GnuplotLineAppearance obsAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(2, GnuplotColorName.red, 1);

        gnuplot.setOutput("pdf", eventDir.toString() + ".pdf", 21, 29.7, true);
        gnuplot.setKey(true, true, "top right");

        gnuplot.setXlabel("time");
        gnuplot.setYlabel("value");
        //gnuplot.setTitle("Test");

        int i = 0;
        for (BasicID obsID : obsList) {
            String filename = obsID.getObserver() + "." + obsID.getGlobalCMTID() + "." + obsID.getSacComponent() + ".txt";
            gnuplot.addLabel(obsID.getObserver().getPaddedInfoString(), "graph", 0, 0.95);
            gnuplot.addLabel(eventDir.toString(), "graph", 0, 0.85);
            gnuplot.addLine(filename, 1, 2, obsAppearance, "observed");
            gnuplot.addLine(filename, 1, 3, synAppearance, "synthetic");

            i++;
            if(i%10 == 0) {
                gnuplot.nextPage();
            } else {
                gnuplot.nextField();
            }
        }
        while(i % 10 != 0) {
            i++;
            gnuplot.nextField();
        }

        gnuplot.write();
        if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
    }
}
