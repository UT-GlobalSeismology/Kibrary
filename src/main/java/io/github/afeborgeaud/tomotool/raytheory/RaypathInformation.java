package io.github.afeborgeaud.tomotool.raytheory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

public class RaypathInformation {

    private Observer observer;

    private GlobalCMTID event;

    //TODO
    public static void main(String[] args) {
        Path timewindowPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/timewindow_SScS_60deg.dat");
        try {
            List<RaypathInformation> raypaths = readRaypathFromTimewindows(timewindowPath);
            Path outpath = Paths.get("/home/anselme/Dropbox/noise_correlation/EQs/raypaths_from_earthquakes.txt");
            writeRaypathInformation(raypaths, outpath);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public RaypathInformation(Observer observer, GlobalCMTID event) {
        this.observer = observer;
        this.event = event;
    }

    public double getDistanceDegree() {
        return Math.toDegrees(event.getEventData().getCmtLocation().computeEpicentralDistance(observer.getPosition()));
    }

    public double getAzimuthDegree() {
        return Math.toDegrees(event.getEventData().getCmtLocation().computeAzimuth(observer.getPosition()));
    }

    public FullPosition getCmtLocation() {
        return event.getEventData().getCmtLocation();
    }

    public HorizontalPosition getObserverPosition() {
        return observer.getPosition();
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getEventData() {
        return event;
    }

    /**
     * @param timewindowPath
     * @return RaypathInformation about observers and events which are contaioned in timewindow file
     * @throws IOException
     */
    public static List<RaypathInformation> readRaypathFromTimewindows(Path timewindowPath) throws IOException {
        return TimewindowDataFile.read(timewindowPath).stream()
            .map(window -> new RaypathInformation(window.getObserver(), window.getGlobalCMTID()))
            .collect(Collectors.toList());
    }

    /**
     * Read raypath information file
     * The format of Information file should be
     * (GCMTID, station name, network name, latitude of the station, longitude of the station)
     *
     * @param path to information file
     * @return RaypathInformation about observers and events which are contaioned in information file
     * @throws IOException
     *
     */
    public static List<RaypathInformation> readRaypathInformation(Path path) throws IOException {
        return Files.readAllLines(path).stream().map(line -> {
            String[] ss = line.split("\\s+");
            GlobalCMTID event = new GlobalCMTID(ss[0]);
            Observer observer = new Observer(ss[1].trim(), ss[2].trim(),
                new HorizontalPosition(Double.parseDouble(ss[3]), Double.parseDouble(ss[4])));
            return new RaypathInformation(observer, event);
        }).collect(Collectors.toList());
    }

    /**
     * Write event names, obsever names, observer positions on the output file
     *
     * @param list of raypath informations
     * @param outpath
     * @throws IOException
     */
    public static void writeRaypathInformation(List<RaypathInformation> rays, Path outpath) throws IOException {
        PrintWriter pw = new PrintWriter(outpath.toFile());
        rays.stream().forEach(r -> pw.println(r.getEventData() + " " +
                r.getObserver().toString() + " " + r.getObserver().getPosition()));
        pw.close();
    }

    @Override
    public String toString() {
        return observer.toString() + " " + event;
    }
}
