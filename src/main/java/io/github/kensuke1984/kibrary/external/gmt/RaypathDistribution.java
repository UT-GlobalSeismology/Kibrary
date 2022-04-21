package io.github.kensuke1984.kibrary.external.gmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceReader;
import io.github.kensuke1984.kibrary.external.TauPPierceReader.Info;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.EventCluster;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverInformationFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * This is like pathDrawer.pl The pathDrawer compute raypath coordinate. But
 * this class uses raypath by GMT.
 * <p>
 * event and station are necessary.
 * <p>
 * <b>Assume that there are no stations with the same name but different
 * networks in an event</b>
 *
 * @author Kensuke Konishi
 * @version 0.1.2
 * @author anselme add methods to draw raypaths inside D''
 */
public class RaypathDistribution extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * components for path
     */
    private Set<SACComponent> components;

    /**
     * draw Path Mode; 0: don't draw, 1: quick draw, 2: detailed draw
     */
    protected int drawsPathMode;
    private Set<GlobalCMTID> events;
    /**
     * draw points of partial TODO
     */
    // protected boolean drawsPoint;

    private Set<Observer> observers;
    private Set<TimewindowData> timeWindowInformationFile;
    private Path stationPath;
    private Path eventPath;
    private Path eventCSVPath;
    private Path raypathPath;
    private Path turningPointPath;
    private Path psPath;
    private Path gmtPath;
    private Path eventClusterPath;
    private String model;
    private double pierceDepth;
    Map<GlobalCMTID, Integer> eventClusterMap;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents of data to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Integer if you want to draw raypath (0: don't draw, 1: quick draw, 2: detailed draw) (0)");
            pw.println("#drawsPathMode");
            pw.println("##StationInformationFile a file containing station information; must be set");
            pw.println("#stationInformationPath station.inf");
            pw.println("##Path of a time window information file.");
            pw.println("##If it exists, draw raypaths in the file");
            pw.println("#timeWindowInformationPath");
            pw.println("#model");
            pw.println("#pierceDepth");
            pw.println("#eventClusterPath");
        }
        System.err.println(outPath + " is created.");
    }

    public RaypathDistribution(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        drawsPathMode = property.parseInt("drawsPathMode", "0");
        if (property.containsKey("timeWindowInformationPath")) {
            Path timewindowPath = property.parsePath("timeWindowInformationPath", null, true, workPath);
            timeWindowInformationFile = TimewindowDataFile.read(timewindowPath);
        }
        Path stationPath = property.parsePath("stationInformationPath", null, true, workPath);
        if (timeWindowInformationFile == null) observers = ObserverInformationFile.read(stationPath);
        else observers = timeWindowInformationFile.stream().map(tw -> tw.getObserver())
                .collect(Collectors.toSet());

        pierceDepth = property.parseDouble("pierceDepth", "400");
        model = property.parseString("model", "prem");
        if (property.containsKey("eventClusterPath")) eventClusterPath = property.parsePath("eventClusterPath", null, true, workPath);
        else eventClusterPath = null;
    }


    private void setName() {
        String date = GadgetAid.getTemporaryString();
        stationPath = workPath.resolve("rdStation" + date + ".inf");
        eventPath = workPath.resolve("rdEvent" + date + ".inf");
        raypathPath = workPath.resolve("rdRaypath" + date + ".inf");
        turningPointPath = workPath.resolve("rdTurningPoint" + date + ".inf");
        psPath = workPath.resolve("rd" + date + ".eps");
        gmtPath = workPath.resolve("rd" + date + ".sh");
        eventCSVPath = workPath.resolve("rdEvent" + date + ".csv");
    }

    private void outputEvent() throws IOException {
        List<String> lines = new ArrayList<>();
        for (GlobalCMTID id : events) {
            FullPosition loc = id.getEvent().getCmtLocation();
            double latitude = loc.getLatitude();
            double longitude = loc.getLongitude();
            longitude = 0 <= longitude ? longitude : longitude + 360;
            lines.add(id + " " + latitude + " " + longitude + " " + loc.getR());
        }
        Files.write(eventPath, lines);
    }

    private void outputEventCSV() throws IOException {
        List<String> lines = new ArrayList<>();
        for (GlobalCMTID id : events) {
            FullPosition loc = id.getEvent().getCmtLocation();
            double latitude = loc.getLatitude();
            double longitude = loc.getLongitude();
            double depth = 6371. - loc.getR();
            double mw = id.getEvent().getCmt().getMw();
            double duration = id.getEvent().getHalfDuration() * 2;
            longitude = 0 <= longitude ? longitude : longitude + 360;
            lines.add(id + "," + latitude + "," + longitude + "," + depth + "," + mw + "," + duration);
        }
        Files.write(eventCSVPath, lines);
    }

    private void outputStation() throws IOException {
        List<String> lines = observers.stream().map(station -> station + " " + station.getPosition())
                .collect(Collectors.toList());
        if (!lines.isEmpty())
            Files.write(stationPath, lines);
    }

    @Override
    public void run() throws IOException {
        setName();
        if (timeWindowInformationFile == null)
            events = DatasetAid.globalCMTIDSet(workPath);
        else
            events = timeWindowInformationFile.stream().map(tw -> tw.getGlobalCMTID())
                .collect(Collectors.toSet());

        if (eventClusterPath != null) {
            eventClusterMap = new HashMap<GlobalCMTID, Integer>();
            EventCluster.readClusterFile(eventClusterPath).forEach(c -> eventClusterMap.put(c.getID(), c.getIndex()));
        }

        outputEvent();
        outputStation();
        outputEventCSV();
        switch (drawsPathMode) {
        case 1:
            outputRaypath();
            break;
        case 2:
            outputRaypathInside(pierceDepth);
            outputTurningPoint();
            break;
        default:
            break;
        }

        outputGMT();
    }

    /**
     * @param name Sacfile
     * @return if the path of Sacfile should be drawn
     */
    private boolean inTimeWindow(SACFileName name) {
        return timeWindowInformationFile == null ? true
                : timeWindowInformationFile.stream()
                        .anyMatch(tw -> tw.getComponent() == name.getComponent()
                                && tw.getGlobalCMTID().equals(name.getGlobalCMTID())
                                && tw.getObserver().getStation().equals(name.getStationCode()));
    }

    private void outputRaypath() throws IOException {
        List<String> lines = DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
            try {
                return eventDir.sacFileSet().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
                .map(name -> {
                    try {
                        return name.readHeader();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .map(header -> header.getSACString(SACHeaderEnum.KSTNM) + " " + header.getSACString(SACHeaderEnum.KEVNM)
                        + " " + header.getEventLocation() + " " + Observer.of(header).getPosition())
                .collect(Collectors.toList());

        Files.write(raypathPath, lines);
    }

    private void outputTurningPoint() throws IOException {
        Phase phasetmp = Phase.ScS;
        if (components.size() == 1 && components.contains(SACComponent.Z))
            phasetmp = Phase.PcP;
        final Phase phase = phasetmp;

        List<String> lines = new ArrayList<>();
        DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
            try {
                return eventDir.sacFileSet().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
                .map(name -> {
                    try {
                        return name.readHeader();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .forEach(headerData -> {
                    FullPosition eventLocation = headerData.getEventLocation();
                    HorizontalPosition stationPosition = headerData.getObserver().getPosition();
                    Info info = TauPPierceReader.getPierceInfo(eventLocation, stationPosition, model, phase)
                        .get(0);
                    FullPosition turningPoint = info.getTurningPoint();
                    lines.add(String.format("%.2f %.2f %.2f"
                            , turningPoint.getLongitude()
                            , turningPoint.getLatitude()
                            , turningPoint.getR()));
                });

        Files.write(turningPointPath, lines);
    }

    private void outputRaypathInside(double pierceDepth) throws IOException {
        List<String> lines = new ArrayList<>();

        Phase phasetmp = Phase.ScS;
        if (components.size() == 1 && components.contains(SACComponent.Z))
            phasetmp = Phase.PcP;
        final Phase phase = phasetmp;

        DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
            try {
                return eventDir.sacFileSet().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
                .map(name -> {
                    try {
                        return name.readHeader();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .forEach(headerData -> {
                    FullPosition eventLocation = headerData.getEventLocation();
                    HorizontalPosition stationPosition = headerData.getObserver().getPosition();
                    List<Info> infoList = TauPPierceReader.getPierceInfo(eventLocation, stationPosition, model, pierceDepth, phase);
                    Info info = null;
                    if (infoList.size() > 0) {
                        info = infoList.get(0);
                        FullPosition enterPoint = info.getEnterPoint();
                        FullPosition leavePoint = info.getLeavePoint();
                        if (eventClusterPath != null)
                            lines.add(String.format("%.2f %.2f %.2f %.2f cluster%d"
                                , enterPoint.getLatitude()
                                , enterPoint.getLongitude()
                                , leavePoint.getLatitude()
                                , leavePoint.getLongitude()
                                , eventClusterMap.get(headerData.getGlobalCMTID())
                                ));
                        else
                            lines.add(String.format("%.2f %.2f %.2f %.2f"
                                    , enterPoint.getLatitude()
                                    , enterPoint.getLongitude()
                                    , leavePoint.getLatitude()
                                    , leavePoint.getLongitude()
                                    ));
                    }
                });

        Path outpath = workPath.resolve("raypathInside.inf");
        Files.write(outpath, lines);
    }

    private void outputRaypathInside_divide() throws IOException {
        List<String> lines_western = new ArrayList<>();
        List<String> lines_central = new ArrayList<>();
        List<String> lines_eastern = new ArrayList<>();

        DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
            try {
                return eventDir.sacFileSet().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
                .map(name -> {
                    try {
                        return name.readHeader();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .forEach(headerData -> {
                    FullPosition eventLocation = headerData.getEventLocation();
                    HorizontalPosition stationPosition = headerData.getObserver().getPosition();
                    List<Info> infoList = TauPPierceReader.getPierceInfo(eventLocation, stationPosition, "prem", Phase.ScS);
                    Info info = null;
                    if (infoList.size() > 0) {
                        info = TauPPierceReader.getPierceInfo(eventLocation, stationPosition, "prem", Phase.ScS)
                        .get(0);
                        FullPosition enterDpp = info.getEnterPoint();
                        FullPosition leaveDpp = info.getLeavePoint();
                        if (stationPosition.getLongitude() >= -130 && stationPosition.getLongitude() <= -110)
                            lines_western.add(String.format("%.2f %.2f %.2f %.2f"
                                , enterDpp.getLatitude()
                                , enterDpp.getLongitude()
                                , leaveDpp.getLatitude()
                                , leaveDpp.getLongitude()
                                ));
                        else if (stationPosition.getLongitude() > -110 && stationPosition.getLongitude() <= -90)
                            lines_central.add(String.format("%.2f %.2f %.2f %.2f"
                                    , enterDpp.getLatitude()
                                    , enterDpp.getLongitude()
                                    , leaveDpp.getLatitude()
                                    , leaveDpp.getLongitude()
                                    ));
                        else if (stationPosition.getLongitude() > -90 && stationPosition.getLongitude() <= -70)
                            lines_eastern.add(String.format("%.2f %.2f %.2f %.2f"
                                    , enterDpp.getLatitude()
                                    , enterDpp.getLongitude()
                                    , leaveDpp.getLatitude()
                                    , leaveDpp.getLongitude()
                                    ));
                    }
                });

        Path path_western = workPath.resolve("raypathInside_western.inf");
        Path path_central = workPath.resolve("raypathInside_central.inf");
        Path path_eastern = workPath.resolve("raypathInside_eastern.inf");
        Files.write(path_western, lines_western);
        Files.write(path_central, lines_central);
        Files.write(path_eastern, lines_eastern);
    }

    private GMTMap createBaseMap() {

        double minimumEventLatitude = events.stream().mapToDouble(id -> id.getEvent().getCmtLocation().getLatitude()).min()
                .getAsDouble();
        double maximumEventLatitude = events.stream().mapToDouble(id -> id.getEvent().getCmtLocation().getLatitude()).max()
                .getAsDouble();

        double minimumEventLongitude = events.stream().mapToDouble(e -> e.getEvent().getCmtLocation().getLongitude())
                .map(d -> 0 <= d ? d : d + 360).min().getAsDouble();
        double maximumEventLongitude = events.stream().mapToDouble(e -> e.getEvent().getCmtLocation().getLongitude())
                .map(d -> 0 <= d ? d : d + 360).max().getAsDouble();

        double minimumStationLatitude = observers.stream().mapToDouble(s -> s.getPosition().getLatitude()).min()
                .orElse(minimumEventLatitude);
        double maximumStationLatitude = observers.stream().mapToDouble(s -> s.getPosition().getLatitude()).max()
                .orElse(maximumEventLatitude);

        double minimumStationLongitude = observers.stream().mapToDouble(s -> s.getPosition().getLongitude())
                .map(d -> 0 <= d ? d : d + 360).min().orElse(minimumEventLongitude);
        double maximumStationLongitude = observers.stream().mapToDouble(s -> s.getPosition().getLongitude())
                .map(d -> 0 <= d ? d : d + 360).max().orElse(maximumEventLongitude);

        int minLatitude = (int) Math
                .round(minimumEventLatitude < minimumStationLatitude ? minimumEventLatitude : minimumStationLatitude)
                / 5 * 5 - 10;
        int maxLatitude = (int) Math
                .round(maximumEventLatitude < maximumStationLatitude ? maximumStationLatitude : maximumEventLatitude)
                / 5 * 5 + 10;
        int minLongitude = (int) Math.round(
                minimumEventLongitude < minimumStationLongitude ? minimumEventLongitude : minimumStationLongitude) / 5
                * 5 - 10;
        int maxLongitude = (int) Math.round(
                maximumEventLongitude < maximumStationLongitude ? maximumStationLongitude : maximumEventLongitude) / 5
                * 5 + 10;
        if (minLatitude < -90)
            minLatitude = -90;
        if (90 < maxLatitude)
            maxLatitude = 90;
        return new GMTMap("MAP", minLongitude, maxLongitude, minLatitude, maxLatitude);
    }

    private void outputGMT() throws IOException {
        GMTMap gmtmap = createBaseMap();
        List<String> gmtCMD = new ArrayList<>();
        gmtCMD.add("#!/bin/sh");
        gmtCMD.add("psname=\"" + psPath + "\"");
        gmtCMD.add(gmtmap.psStart());
        if (drawsPathMode == 1) {
            gmtCMD.add("while  read line");
            gmtCMD.add("do");
            gmtCMD.add("echo $line |awk '{print $3, $4, \"\\n\", $6, $7}' | \\");
            gmtCMD.add("psxy -: -J -R -O -P -K  -W0.25,grey,.@100 >>$psname");
            gmtCMD.add("done < " + raypathPath);
            // draw over the path
        }

        gmtCMD.add(GMTMap.psCoast());
        gmtCMD.add("awk '{print $2, $3}' " + eventPath + " | psxy -V -: -J -R -O -P -Sa0.3 -G255/0/0 -W1  -K "
                + " >> $psname");
        gmtCMD.add("awk '{print $2, $3}' " + stationPath + " |psxy -V -: -J -R -K -O -P -Si0.3 -G0/0/255 -W1 "
                + " >> $psname");
        gmtCMD.add(gmtmap.psEnd());
        gmtCMD.add("#eps2eps $psname .$psname && mv .$psname $psname");
        Files.write(gmtPath, gmtCMD);
        gmtPath.toFile().setExecutable(true);
    }

}
