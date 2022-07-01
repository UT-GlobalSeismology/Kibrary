package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.data.Raypath;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * This is like pathDrawer.pl The pathDrawer compute raypath coordinate. But
 * this class uses raypath by GMT.
 * <p>
 * event and station are necessary.
 * <p>
 * <b>Assume that there are no stations with the same name but different
 * networks in an event</b>
 *
 * TODO: pierce calculation may not work for phases other than ScS (see {@link Raypath})
 *
 * @author Kensuke Konishi
 * @version 0.1.2
 * @author anselme add methods to draw raypaths inside D''
 */
public class RaypathMapper extends Operation {

    /**
     * The interval of deciding map size
     */
    private static final int INTERVAL = 5;
    /**
     * How much space to provide at the rim of the map
     */
    private static final int MAP_RIM = 5;

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
     * Path of the output folder
     */
    private Path outPath;

    private Path reusePath;
    private Path dataEntryPath;
    private Path voxelPath;

    private boolean cutAtPiercePoint;
    private Phase piercePhase;
    private double pierceDepth;
    private String model;

    private int colorMode;
    private static final int BIN_DISTANCE = 1;
    private static final int BIN_AZIMUTH = 2;
    private static final int BIN_BACKAZIMUTH = 3;
    private static final int BIN_MIDAZIMUTH = 4;
    private Path colorBinPath;
    private boolean drawOutsides;
    private Path outsideColorBinPath;
    private String mapRegion;
    private String legendJustification;

    private String dateStr;
    private ColorBinInformationFile colorBin;
    private ColorBinInformationFile outsideColorBin;

    private String eventFileName;
    private String observerFileName;
    private String raypathFileName;
    private String insideFileName;
    private String outsideFileName;
    private String turningPointFileName;
    private String perturbationFileName;
    private String gmtFileName;
    private String psFileName;

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
            pw.println("##To reuse raypath data that have already been exported, set the folder containing them");
            pw.println("#reusePath raypathMap");
            pw.println("##########The following is valid when reusePath is not set.");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents of data to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry file, must be set if reusePath is not set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##########To plot perturbation points, set one of the following.");
            pw.println("##Path of a voxel information file");
            pw.println("#voxelPath voxel.inf");
            pw.println("##########Overall settings");
            pw.println("##(boolean) Whether to cut raypaths at piercing points (true)");
            pw.println("#cutAtPiercePoint ");
            pw.println("##########The following settings are valid when reusePath is false and cutAtPiercePoint is true.");
            pw.println("##Phase to calculate pierce points for (ScS)");
            pw.println("#piercePhase ");
            pw.println("##(double) Depth to calculate pierce points for [km] (2491)");
            pw.println("#pierceDepth ");
            pw.println("##(String) Name of model to use for calculating pierce points (prem)");
            pw.println("#model ");
            pw.println("##########Settings for mapping");
            pw.println("##Mode of coloring of raypaths {0: single color, 1: bin by distance, 2: bin by azimuth,");
            pw.println("## 3: bin by back azimuth, 4: bin by turning-point-azimuth} (0)");
            pw.println("#colorMode ");
            pw.println("##Path of color bin file, must be set if colorMode is not 0");
            pw.println("#colorBinPath ");
            pw.println("##(boolean) Whether to draw the raypaths outside the pierce points (false)");
            pw.println("#drawOutsides ");
            pw.println("##Path of color bin file for the outside segments, must be set if colorMode is not 0 and drawOutsides is true");
            pw.println("#outsideColorBinPath ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax, range lon:[-180,180] lat:[-90,90]");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##The position of the legend, when colorMode>0, from {TL, TR, BL, BR, none} (BR)");
            pw.println("#legendJustification ");
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

        if (property.containsKey("reusePath")) {
            reusePath = property.parsePath("reusePath", null, true, workPath);
        } else if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        } else {
            throw new IllegalArgumentException("A folder or file for input must be set.");
        }
        if (property.containsKey("voxelPath")) {
            voxelPath = property.parsePath("voxelPath", null, true, workPath);
        }

        cutAtPiercePoint = property.parseBoolean("cutAtPiercePoint", "true");
        piercePhase = Phase.create(property.parseString("piercePhase", "ScS"));
        pierceDepth = property.parseDouble("pierceDepth", "2491");
        model = property.parseString("model", "prem");

        colorMode = property.parseInt("colorMode", "0");
        if (colorMode > 0)
            colorBinPath = property.parsePath("colorBinPath", null, true, workPath);
        drawOutsides = property.parseBoolean("drawOutsides", "false");
        if (colorMode > 0 && drawOutsides == true)
            outsideColorBinPath = property.parsePath("outsideColorBinPath", null, true, workPath);
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        legendJustification = property.parseString("legendJustification", "BR");

        setName();
    }

    private void setName() {
        dateStr = GadgetAid.getTemporaryString();
        eventFileName = "event.lst";
        observerFileName = "observer.lst";
        raypathFileName = "raypath.lst";
        insideFileName = "raypathInside.lst";
        outsideFileName = "raypathOutside.lst";
        turningPointFileName = "turningPoint.lst";
        perturbationFileName = "perturbation.lst";
        gmtFileName = "raypathMap" + dateStr + ".sh";
        psFileName = "raypathMap" + dateStr + ".eps";
    }

    @Override
    public void run() throws IOException {

        if (reusePath != null) {
            checkReusePath();
        } else {
            readAndOutput();
        }

        if (colorBinPath != null) colorBin = new ColorBinInformationFile(colorBinPath);
        if (outsideColorBinPath != null) outsideColorBin = new ColorBinInformationFile(outsideColorBinPath);

        outputGMT();
    }

    private void checkReusePath() {
        if (!Files.exists(reusePath.resolve(eventFileName)) || !Files.exists(reusePath.resolve(observerFileName))){
            throw new IllegalStateException(reusePath + " is missing files.");
        }
        if (cutAtPiercePoint) {
            if (!Files.exists(reusePath.resolve(turningPointFileName)) || !Files.exists(reusePath.resolve(insideFileName))
                    || !Files.exists(reusePath.resolve(outsideFileName))){
                throw new IllegalStateException(reusePath + " is missing files.");
            }
        } else {
            if (!Files.exists(reusePath.resolve(raypathFileName))) {
                throw new IllegalStateException(reusePath + " is missing files.");
            }
        }

        outPath = reusePath;
        System.err.println("Reusing " + reusePath);
    }

    private void readAndOutput() throws IOException {
        Set<GlobalCMTID> events;
        Set<Observer> observers;
        Set<Raypath> raypaths;

        if (dataEntryPath != null) {
            Set<DataEntry> validEntrySet = DataEntryListFile.readAsSet(dataEntryPath)
                    .stream().filter(entry -> components.contains(entry.getComponent()))
                    .collect(Collectors.toSet());
            events = validEntrySet.stream().map(entry -> entry.getEvent()).collect(Collectors.toSet());
            observers = validEntrySet.stream().map(entry -> entry.getObserver()).collect(Collectors.toSet());
            raypaths = validEntrySet.stream().map(DataEntry::toRaypath).collect(Collectors.toSet());
        } else {
            throw new IllegalStateException("Input folder or file not set");
        }

        DatasetAid.checkNum(raypaths.size(), "raypath", "raypaths");

        outPath = DatasetAid.createOutputFolder(workPath, "raypathMap", tag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        EventListFile.write(events, outPath.resolve(eventFileName));
        ObserverListFile.write(observers, outPath.resolve(observerFileName));

        if (cutAtPiercePoint) {
            outputRaypathSegments(raypaths);
        } else {
            outputRaypaths(raypaths);
        }

        if (voxelPath != null) {
            HorizontalPosition[] perturbationPositions = new VoxelInformationFile(voxelPath).getHorizontalPositions();
            List<String> perturbationLines = Stream.of(perturbationPositions).map(HorizontalPosition::toString).collect(Collectors.toList());
            Files.write(outPath.resolve(perturbationFileName), perturbationLines);
        }

    }

    private void outputRaypaths(Set<Raypath> raypaths) throws IOException {
        List<String> raypathLines = new ArrayList<>();

        raypaths.forEach(ray -> {
            raypathLines.add(lineFor(ray.getSource(), ray.getReceiver(), ray));
        });
        Files.write(outPath.resolve(raypathFileName), raypathLines);
    }

    private void outputRaypathSegments(Set<Raypath> raypaths) throws IOException {
        List<String> turningPointLines = new ArrayList<>();
        List<String> insideLines = new ArrayList<>();
        List<String> outsideLines = new ArrayList<>();

        System.err.println("Calculating pierce points ...");

        raypaths.forEach(ray -> {
            if (ray.calculatePiercePoints(model, piercePhase, pierceDepth)) {
                turningPointLines.add(ray.getTurningPoint().toHorizontalPosition().toString());
                insideLines.add(lineFor(ray.getEnterPoint(), ray.getLeavePoint(), ray));
                outsideLines.add(lineFor(ray.getSource(), ray.getEnterPoint(), ray));
                outsideLines.add(lineFor(ray.getLeavePoint(), ray.getReceiver(), ray));
            } else {
                System.err.println("Pierce point calculation for " + ray + "failed.");
            }
        });

        System.err.println("Calculation of raypath segments for " + turningPointLines.size() + " raypaths succeeded.");

        Files.write(outPath.resolve(turningPointFileName), turningPointLines);
        Files.write(outPath.resolve(insideFileName), insideLines);
        Files.write(outPath.resolve(outsideFileName), outsideLines);
    }

    /**
     * Output line: lat1 lon1 lat2 lon2 dist azimuth
     * @param point1
     * @param point2
     * @param raypath
     * @return
     */
    private static String lineFor(FullPosition point1, HorizontalPosition point2, Raypath raypath) {
        // turn point2 into HorizontalPosition if it is FullPosition
        HorizontalPosition point2h = point2;
        if (point2 instanceof FullPosition) point2h = ((FullPosition) point2).toHorizontalPosition();

        // only output latitude and longitude, not radius, because point2 may be HorizontalPosition or FullPosition
        return point1.toHorizontalPosition() + " " + point2h + " "
                + Math.round(FastMath.toDegrees(raypath.getEpicentralDistance())) + " "
                + Math.round(FastMath.toDegrees(raypath.getAzimuth())) + " "
                + Math.round(FastMath.toDegrees(raypath.getBackAzimuth())) + " "
                + Math.round(FastMath.toDegrees(raypath.calculateMidAzimuth()));
    }

    private void outputGMT() throws IOException {
        Path gmtPath = outPath.resolve(gmtFileName);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(gmtPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("# GMT options");
            pw.println("gmt set COLOR_MODEL RGB");
            pw.println("gmt set PS_MEDIA 1100x1100");
            pw.println("gmt set PS_PAGE_ORIENTATION landscape");
            pw.println("gmt set MAP_DEFAULT_PEN black");
            pw.println("gmt set MAP_TITLE_OFFSET 1p");
            pw.println("gmt set FONT 20p");
            pw.println("gmt set FONT_LABEL 15p,Helvetica,black");
            pw.println("gmt set FONT_ANNOT_PRIMARY 25p");
            pw.println("");
            pw.println("# parameters for gmt pscoast");
            pw.println("R='-R" + decideMapRegion() + "'");
            pw.println("J='-JQ20'");
            pw.println("B='-Ba30 -BWeSn'");
            pw.println("C='-Ggray -Wthinnest,gray20'");
            pw.println("");
            pw.println("psname=\"" + psFileName + "\"");
            pw.println("");
            pw.println("gmt pscoast -K $R $J $B $C -P  > $psname");
            pw.println("");
            pw.println("#------- Events & Observers");
            pw.println("awk '{print $2, $3}' " + eventFileName + " | gmt psxy -: -J -R -O -P -Sa0.3 -G255/156/0 -Wthinnest -K  >> $psname");
            pw.println("awk '{print $3, $4}' " + observerFileName + " | gmt psxy -: -J -R -O -P -Si0.3 -G71/187/243 -Wthinnest -K >> $psname");
            pw.println("");

            pw.println("#------- Raypath");

            // raypathOutside
            if (cutAtPiercePoint && drawOutsides) {
                pw.println("while read line");
                pw.println("do");
                if (colorMode > 0) {
                    int nSections = outsideColorBin.getNSections();
                    int binColumn;
                    switch (colorMode) {
                    case BIN_DISTANCE: binColumn = 5; break;
                    case BIN_AZIMUTH: binColumn = 6; break;
                    case BIN_BACKAZIMUTH: binColumn = 7; break;
                    case BIN_MIDAZIMUTH: binColumn = 8; break;
                    default: throw new IllegalArgumentException("colorMode out of range");
                    }

                    if (nSections == 1) {
                        pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                        pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest," + outsideColorBin.getColorFor(0) + " >> $psname");
                    } else {
                        pw.println("  valueForBin=$(echo $line | awk '{print $" + binColumn + "}')");
                        for (int i = 0; i < nSections; i++) {
                            if (i == 0)
                                pw.println("  if [ $valueForBin -lt " + outsideColorBin.getValueFor(i) + " ]; then");
                            else if (i < nSections - 1)
                                pw.println("  elif [ $valueForBin -lt " + outsideColorBin.getValueFor(i) + " ]; then");
                            else
                                pw.println("  else");
                            pw.println("    echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                            pw.println("    gmt psxy -: -J -R -O -P -K -Wthinnest," + outsideColorBin.getColorFor(i) + " >> $psname");
                        }
                        pw.println("  fi");
                    }
                } else {
                    pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                    pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest,lavender >> $psname");
                }
                pw.println("done < " + outsideFileName);
                pw.println("");
            }

            // raypathInside
            pw.println("while read line");
            pw.println("do");
            if (colorMode > 0) {
                int nSections = colorBin.getNSections();
                int binColumn = (colorMode == BIN_DISTANCE ? 5 : 6);
                if (nSections == 1) {
                    pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                    pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest," + colorBin.getColorFor(0) + " >> $psname");
                } else {
                    pw.println("  valueForBin=$(echo $line | awk '{print $" + binColumn + "}')");
                    for (int i = 0; i < nSections; i++) {
                        if (i == 0)
                            pw.println("  if [ $valueForBin -lt " + colorBin.getValueFor(i) + " ]; then");
                        else if (i < nSections - 1)
                            pw.println("  elif [ $valueForBin -lt " + colorBin.getValueFor(i) + " ]; then");
                        else
                            pw.println("  else");
                        pw.println("    echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                        pw.println("    gmt psxy -: -J -R -O -P -K -Wthinnest," + colorBin.getColorFor(i) + " >> $psname");
                    }
                    pw.println("  fi");
                }
            } else {
                pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest,red >> $psname");
            }
            if (cutAtPiercePoint) {
                pw.println("done < " + insideFileName);
            } else {
                pw.println("done < " + raypathFileName);
            }
            pw.println("");

            if (cutAtPiercePoint) {
                pw.println("awk '{print $1, $2}' " + turningPointFileName + " | gmt psxy -: -J -R -O -Sx0.3 -Wthinnest,black -K >> $psname");
                pw.println("");
            }

            if (voxelPath != null) {
                pw.println("#------- Perturbation");
                pw.println("gmt psxy " + perturbationFileName + " -: -J -R -O -P -Sc0.2 -G0/255/0 -Wthinnest -K >> $psname");
                pw.println("");
            }

            if (colorMode > 0 && legendJustification.equals("none") == false) {
                pw.println("#------- Legend");
                pw.println("gmt pslegend -Dj" + legendJustification + "+w6c -F+g#FFFFFF+p1p,black -J -R -O -K << END >> $psname");
                int nSections = colorBin.getNSections();
                for (int i = 0; i < nSections; i++) {
                    if (i == 0) {
                        pw.println("S 1c - 1c - 0.4p," + colorBin.getColorFor(i) + " 2c ~" + colorBin.getValueFor(i));
                    } else if (i < nSections - 1) {
                        pw.println("S 1c - 1c - 0.4p," + colorBin.getColorFor(i) + " 2c " + colorBin.getValueFor(i - 1) + "~" + colorBin.getValueFor(i));
                    } else {
                        pw.println("S 1c - 1c - 0.4p," + colorBin.getColorFor(i) + " 2c " + colorBin.getValueFor(i - 1) + "~");
                    }
                }
                pw.println("END");
            }

            pw.println("#------- Finalize");
            pw.println("gmt pstext $J $R -O -N -F+jLM+f30p,Helvetica,black << END >> $psname");
            pw.println("END");
            pw.println("");
            pw.println("gmt psconvert $psname -A -Tf -Qg4 -E100");
            pw.println("gmt psconvert $psname -A -Tg -Qg4 -E100");
            pw.println("");
            pw.println("#-------- clear");
            pw.println("rm -rf color.cpt tmp.grd comp.grd $OUTFILE gmt.conf gmt.history");
        }
        gmtPath.toFile().setExecutable(true);

    }

    private String decideMapRegion() throws IOException {
        if (mapRegion != null) {
            return mapRegion;
        } else {
            double latMin, latMax, lonMin, lonMax;
            Set<GlobalCMTID> events = EventListFile.read(outPath.resolve(eventFileName));
            Set<Observer> observers = ObserverListFile.read(outPath.resolve(observerFileName));

            // set one position as an initial value
            HorizontalPosition pos0 = events.iterator().next().getEventData().getCmtLocation();
            latMin = latMax = pos0.getLatitude();
            lonMin = lonMax = pos0.getLongitude();

            for (GlobalCMTID event : events) {
                HorizontalPosition pos = event.getEventData().getCmtLocation();
                if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
                if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
                if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
                if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
            }

            for (Observer observer : observers) {
                HorizontalPosition pos = observer.getPosition();
                if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
                if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
                if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
                if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
            }

            // expand the region a bit more
            latMin = Math.floor(latMin / INTERVAL) * INTERVAL - MAP_RIM;
            latMax = Math.ceil(latMax / INTERVAL) * INTERVAL + MAP_RIM;
            lonMin = Math.floor(lonMin / INTERVAL) * INTERVAL - MAP_RIM;
            lonMax = Math.ceil(lonMax / INTERVAL) * INTERVAL + MAP_RIM;
            // space for legend
            if (colorMode > 0) {
                if (legendJustification.equals("TL") || legendJustification.equals("BL")) {
                    lonMin -= 40;
                } else if (legendJustification.equals("TR") || legendJustification.equals("BR")) {
                    lonMax += 40;
                }
            }

            return (int) lonMin + "/" + (int) lonMax + "/" + (int) latMin + "/" + (int) latMax;
        }
    }

}
