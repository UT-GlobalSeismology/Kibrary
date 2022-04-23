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

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.EventCluster;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.data.Raypath;
import io.github.kensuke1984.kibrary.util.data.RaypathListFile;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

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
public class RaypathMapper extends Operation {

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

    private Path datasetPath;
    private Path timewindowPath;
    private Path basicIDPath;

    /**
     * draw Path Mode; 0: don't draw, 1: quick draw, 2: detailed draw
     */
    protected int drawsPathMode;

    private Set<GlobalCMTID> events;
    private Set<Observer> observers;
    private Set<Raypath> raypaths;

    private Phase piercePhase;
    private double pierceDepth;
    private String model;



    /**
     * draw points of partial TODO
     */
    // protected boolean drawsPoint;

    private Set<TimewindowData> timeWindowInformationFile;

    private Path eventPath;
    private Path observerPath;
    private Path raypathPath;
    private Path turningPointPath;
    private Path psPath;
    private Path gmtPath;
    private Path eventClusterPath;

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
            pw.println("##(int) Whether you want to draw raypath (0: don't draw, 1: quick draw, 2: detailed draw) (0)");
            pw.println("#drawsPathMode ");
            pw.println("##########For input, one of the following must be set. When many are set, the first one will be used.");
            pw.println("##Path of a root folder containing dataset");
            pw.println("#datasetPath .");
            pw.println("##Path of a timewindow data file");
            pw.println("#timeindowPath timewindow.dat");
            pw.println("##Path of a basic ID file");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##########Other settings:");
            pw.println("#Phase to calculate pierce points for (ScS)");
            pw.println("#piercePhase ");
            pw.println("#(double) Depth to calculate pierce points for [km] (2491)");
            pw.println("#pierceDepth ");
            pw.println("#(String) Name of model to use for calculating pierce points (prem)");
            pw.println("#model ");
            pw.println("#eventClusterPath");
        }
        System.err.println(outPath + " is created.");
    }

    public RaypathMapper(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        drawsPathMode = property.parseInt("drawsPathMode", "0");

        if (property.containsKey("datasetPath")) {
            datasetPath = property.parsePath("datasetPath", null, true, workPath);
        } else if (property.containsKey("timewindowPath")) {
            timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        } else if (property.containsKey("basicIDPath")) {
            basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        } else {
            throw new IllegalArgumentException("A folder or file for input must be set.");
        }

        piercePhase = Phase.create(property.parseString("piercePhase", "ScS"));
        pierceDepth = property.parseDouble("pierceDepth", "2491");
        model = property.parseString("model", "prem");

        if (property.containsKey("eventClusterPath")) eventClusterPath = property.parsePath("eventClusterPath", null, true, workPath);
        else eventClusterPath = null;

        setName();
    }

    private void setName() {
        String dateStr = GadgetAid.getTemporaryString();
        eventPath = workPath.resolve("rdEvent" + dateStr + ".lst");
        observerPath = workPath.resolve("rdObserver" + dateStr + ".lst");
        raypathPath = workPath.resolve("rdRaypath" + dateStr + ".inf");
        turningPointPath = workPath.resolve("rdTurningPoint" + dateStr + ".inf");
        psPath = workPath.resolve("rd" + dateStr + ".eps");
        gmtPath = workPath.resolve("rd" + dateStr + ".sh");
    }

    @Override
    public void run() throws IOException {

        if (datasetPath != null) {
            Set<SACHeaderAccess> validSacHeaderSet =  DatasetAid.sacFileNameSet(datasetPath)
                    .stream().filter(sacname -> sacname.isOBS() && components.contains(sacname.getComponent()))
                    .map(sacname -> sacname.readHeaderWithNullOnFailure()).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            events = validSacHeaderSet.stream().map(sac -> sac.getGlobalCMTID()).collect(Collectors.toSet());
            observers = validSacHeaderSet.stream().map(sac -> sac.getObserver()).collect(Collectors.toSet());
            raypaths = validSacHeaderSet.stream().map(Raypath::new).collect(Collectors.toSet());
        } else if (timewindowPath != null) {
            Set<TimewindowData> validTimewindowSet = TimewindowDataFile.read(timewindowPath)
                    .stream().filter(window -> components.contains(window.getComponent()))
                    .collect(Collectors.toSet());
            events = validTimewindowSet.stream().map(id -> id.getGlobalCMTID()).collect(Collectors.toSet());
            observers = validTimewindowSet.stream().map(id -> id.getObserver()).collect(Collectors.toSet());
            raypaths = validTimewindowSet.stream().map(Raypath::new).collect(Collectors.toSet());
        } else if (basicIDPath != null) {
            BasicID[] basicIDs = BasicIDFile.read(basicIDPath);
            Set<BasicID> validBasicIDSet = Arrays.stream(basicIDs)
                    .filter(basicID -> basicID.getWaveformType().equals(WaveformType.OBS) && components.contains(basicID.getSacComponent()))
                    .collect(Collectors.toSet());
            events = validBasicIDSet.stream().map(id -> id.getGlobalCMTID()).collect(Collectors.toSet());
            observers = validBasicIDSet.stream().map(id -> id.getObserver()).collect(Collectors.toSet());
            raypaths = validBasicIDSet.stream().map(Raypath::new).collect(Collectors.toSet());
        } else {
            throw new IllegalStateException("Input folder or file not set");
        }


        if (eventClusterPath != null) {
            eventClusterMap = new HashMap<GlobalCMTID, Integer>();
            EventCluster.readClusterFile(eventClusterPath).forEach(c -> eventClusterMap.put(c.getID(), c.getIndex()));
        }

        EventListFile.write(events, eventPath);
        ObserverListFile.write(observers, observerPath);
        RaypathListFile.write(raypaths, raypathPath);

        switch (drawsPathMode) {
        case 1:
            break;
        case 2:
            //outputRaypathInside(pierceDepth);
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

    private void outputTurningPoint() throws IOException {

        List<String> lines = new ArrayList<>();
        raypaths.forEach(ray -> {
            ray.calculateTurningPoint(model, piercePhase);
            FullPosition p = ray.getTurningPoint();
            if(p != null)
                lines.add(p.toString());
        });
        Files.write(turningPointPath, lines);


    }
//
//    private void outputRaypathInside(double pierceDepth) throws IOException {
//        List<String> lines = new ArrayList<>();
//
//        Phase phasetmp = Phase.ScS;
//        if (components.size() == 1 && components.contains(SACComponent.Z))
//            phasetmp = Phase.PcP;
//        final Phase phase = phasetmp;
//
//        DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
//            try {
//                return eventDir.sacFileSet().stream();
//            } catch (Exception e) {
//                return Stream.empty();
//            }
//        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
//                .map(name -> {
//                    try {
//                        return name.readHeader();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        return null;
//                    }
//                }).filter(Objects::nonNull)
//                .forEach(headerData -> {
//                    FullPosition eventLocation = headerData.getEventLocation();
//                    HorizontalPosition stationPosition = headerData.getObserver().getPosition();
//                    List<Info> infoList = TauPPierceReader.getPierceInfo(eventLocation, stationPosition, model, pierceDepth, phase);
//                    Info info = null;
//                    if (infoList.size() > 0) {
//                        info = infoList.get(0);
//                        FullPosition enterPoint = info.getEnterPoint();
//                        FullPosition leavePoint = info.getLeavePoint();
//                        if (eventClusterPath != null)
//                            lines.add(String.format("%.2f %.2f %.2f %.2f cluster%d"
//                                , enterPoint.getLatitude()
//                                , enterPoint.getLongitude()
//                                , leavePoint.getLatitude()
//                                , leavePoint.getLongitude()
//                                , eventClusterMap.get(headerData.getGlobalCMTID())
//                                ));
//                        else
//                            lines.add(String.format("%.2f %.2f %.2f %.2f"
//                                    , enterPoint.getLatitude()
//                                    , enterPoint.getLongitude()
//                                    , leavePoint.getLatitude()
//                                    , leavePoint.getLongitude()
//                                    ));
//                    }
//                });
//
//        Path outpath = workPath.resolve("raypathInside.inf");
//        Files.write(outpath, lines);
//    }
//
//    private void outputRaypathInside_divide() throws IOException {
//        List<String> lines_western = new ArrayList<>();
//        List<String> lines_central = new ArrayList<>();
//        List<String> lines_eastern = new ArrayList<>();
//
//        DatasetAid.eventFolderSet(workPath).stream().flatMap(eventDir -> {
//            try {
//                return eventDir.sacFileSet().stream();
//            } catch (Exception e) {
//                return Stream.empty();
//            }
//        }).filter(name -> name.isOBS() && components.contains(name.getComponent())).filter(this::inTimeWindow)
//                .map(name -> {
//                    try {
//                        return name.readHeader();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        return null;
//                    }
//                }).filter(Objects::nonNull)
//                .forEach(headerData -> {
//                    FullPosition eventLocation = headerData.getEventLocation();
//                    HorizontalPosition stationPosition = headerData.getObserver().getPosition();
//                    List<Info> infoList = TauPPierceReader.getTurningInfo(eventLocation, stationPosition, "prem", Phase.ScS);
//                    Info info = null;
//                    if (infoList.size() > 0) {
//                        info = TauPPierceReader.getTurningInfo(eventLocation, stationPosition, "prem", Phase.ScS)
//                        .get(0);
//                        FullPosition enterDpp = info.getEnterPoint();
//                        FullPosition leaveDpp = info.getLeavePoint();
//                        if (stationPosition.getLongitude() >= -130 && stationPosition.getLongitude() <= -110)
//                            lines_western.add(String.format("%.2f %.2f %.2f %.2f"
//                                , enterDpp.getLatitude()
//                                , enterDpp.getLongitude()
//                                , leaveDpp.getLatitude()
//                                , leaveDpp.getLongitude()
//                                ));
//                        else if (stationPosition.getLongitude() > -110 && stationPosition.getLongitude() <= -90)
//                            lines_central.add(String.format("%.2f %.2f %.2f %.2f"
//                                    , enterDpp.getLatitude()
//                                    , enterDpp.getLongitude()
//                                    , leaveDpp.getLatitude()
//                                    , leaveDpp.getLongitude()
//                                    ));
//                        else if (stationPosition.getLongitude() > -90 && stationPosition.getLongitude() <= -70)
//                            lines_eastern.add(String.format("%.2f %.2f %.2f %.2f"
//                                    , enterDpp.getLatitude()
//                                    , enterDpp.getLongitude()
//                                    , leaveDpp.getLatitude()
//                                    , leaveDpp.getLongitude()
//                                    ));
//                    }
//                });
//
//        Path path_western = workPath.resolve("raypathInside_western.inf");
//        Path path_central = workPath.resolve("raypathInside_central.inf");
//        Path path_eastern = workPath.resolve("raypathInside_eastern.inf");
//        Files.write(path_western, lines_western);
//        Files.write(path_central, lines_central);
//        Files.write(path_eastern, lines_eastern);
//    }

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
        return new GMTMap("MAP", minLatitude, maxLatitude, minLongitude, maxLongitude);
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
        gmtCMD.add("awk '{print $2, $3}' " + observerPath + " |psxy -V -: -J -R -K -O -P -Si0.3 -G0/0/255 -W1 "
                + " >> $psname");
        gmtCMD.add(gmtmap.psEnd());
        gmtCMD.add("#eps2eps $psname .$psname && mv .$psname $psname");
        Files.write(gmtPath, gmtCMD);
        gmtPath.toFile().setExecutable(true);
    }

}
