package io.github.kensuke1984.kibrary.selection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;


/**
 * File containing information of data selection. Ascii-format.
 *
 *
 *
 */
public class DataSelectionInformationFile {

    public static void write(List<DataSelectionInformation> infoList, Path outpath) throws IOException {
        PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outpath,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND));

        writer.println("#station, network, lat, lon, event, component, start time, end time, phases, max ratio, min ratio, abs ratio, variance, cc, SN ratio");
        for (DataSelectionInformation info : infoList)
            writer.println(info);

        writer.close();
    }

    public static List<DataSelectionInformation> read(Path infoPath) throws IOException {
        List<DataSelectionInformation> infoList = new ArrayList<>();

        Files.readAllLines(infoPath).stream().forEach(line -> {
            String[] s = line.split("\\s+");
            Observer observer = new Observer(s[0], s[1], new HorizontalPosition(Double.parseDouble(s[2]), Double.parseDouble(s[3])));
            Phase[] phases = Stream.of(s[8].split(",")).map(string -> Phase.create(string)).toArray(Phase[]::new);

            TimewindowData timewindow = new TimewindowData(Double.parseDouble(s[6]), Double.parseDouble(s[7]), observer,
                    new GlobalCMTID(s[4]), SACComponent.valueOf(s[5]), phases);

            DataSelectionInformation info = new DataSelectionInformation(timewindow, Double.parseDouble(s[12]),
                    Double.parseDouble(s[13]), Double.parseDouble(s[9]), Double.parseDouble(s[10]),
                    Double.parseDouble(s[11]), Double.parseDouble(s[14]));

            infoList.add(info);
        });

        return infoList;
    }

    public static void main(String[] args) throws IOException {
        Path infoPath = Paths.get(args[0]);
        read(infoPath).stream().forEach(info -> {
            System.out.println(info);
        });
    }

    public static void outputHistograms(Path rootpath, List<DataSelectionInformation> infoList) throws IOException {
        double dVar = 0.1;
        double dCC = 0.1;
        double dRatio = 0.1;
        double maxVar = 5.;
        double maxCC = 1.;
        double maxRatio = 5.;
        int nVar = (int) (maxVar / dVar) + 1;
        int nCC = (int) (2 * maxCC / dCC) + 1;
        int nRatio = (int) (maxRatio / dRatio) + 1;
        int[] vars = new int[nVar];
        int[] ccs = new int[nCC];
        int[] ratios = new int[nRatio];
        Path varPath = rootpath.resolve("histogram_variance" + GadgetUtils.getTemporaryString() + ".dat");
        Path corPath = rootpath.resolve("histogram_cc" + GadgetUtils.getTemporaryString() + ".dat");
        Path ratioPath = rootpath.resolve("histogram_ratio" + GadgetUtils.getTemporaryString() + ".dat");

        for (DataSelectionInformation info : infoList) {
            if (info.getVariance() > maxVar
                 || info.getCC() > maxCC
                 || info.getAbsRatio() > maxRatio)
                continue;
            int iVar = (int) (info.getVariance() / dVar);
            int iCC = (int) ((info.getCC() + 1.) / dCC);
            int iRatio = (int) (info.getAbsRatio() / dRatio);
            vars[iVar] += 1;
            ccs[iCC] += 1;
            ratios[iRatio] += 1;
        }

        PrintWriter writer = new PrintWriter(Files.newBufferedWriter(varPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        for (int i = 0; i < nVar; i++)
            writer.println(i * dVar + " " + vars[i]);
        writer.close();

        writer = new PrintWriter(Files.newBufferedWriter(corPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        for (int i = 0; i < nCC; i++)
            writer.println((i * dCC - 1) + " " + ccs[i]);
        writer.close();

        writer = new PrintWriter(Files.newBufferedWriter(ratioPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        for (int i = 0; i < nRatio; i++)
            writer.println(i * dRatio + " " + ratios[i]);
        writer.close();
    }

//	public static void outputEventInfo(Path rootpath, List<DataSelectionInformation> infoList) throws IOException {
//		infoList.
//	}
}
