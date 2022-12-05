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

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.EventListFile;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.ObserverListFile;
import io.github.kensuke1984.kibrary.util.data.Raypath;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Operation to draw map of raypaths, events, observers, and voxel points.
 * <p>
 * In cutAtPiercePoint mode, pierce points are computed using TauP and exported in list files.
 * By reusing the output folder, these pierce point files can be reused to save the time to compute them again.
 * <p>
 * This class uses GMT to draw raypaths.
 * Raypaths can be classified in different colors based on phase or binned by epicentral distance, azimuth at the event,
 * azimuth at the observer (backazimuth), and azimuth at the turning point.
 * See also {@link ColorBinInformationFile}.
 * When coloring by phase, the color bin information file shall be "0 / color0 / 1 / color1 / 2 ...",
 * with the order of phases as set in piercePhases.
 * <p>
 * By reusing the output folder, computation of pierce points can be omitted.
 * When you want to change the raypaths that are mapped, do not reuse the output folder.
 *
 * @author Kensuke Konishi
 * @since version 0.1.2
 * @version 2022/4/24 moved and renamed from external.gmt.RaypathDistribution
 */
public class RaypathMapper extends Operation {

    // color modes, corresponding to the numbers written in the property file
    private static final int COLOR_BY_PHASE = 1;
    private static final int BIN_DISTANCE = 2;
    private static final int BIN_AZIMUTH = 3;
    private static final int BIN_BACKAZIMUTH = 4;
    private static final int BIN_MIDAZIMUTH = 5;

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
     * Path of an output foler to reuse, if reusing any
     */
    private Path reusePath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * components for path
     */
    private Set<SACComponent> components;
    /**
     * Path of the output folder
     */
    private Path outPath;

    private Path dataEntryPath;
    private Path voxelPath;

    private boolean cutAtPiercePoint;
    private String[] piercePhases;
    private double lowerPierceRadius;
    private double upperPierceRadius;
    private String structureName;

    private int colorMode;
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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents of data to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a data entry file, must be set if reusePath is not set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##########To plot perturbation points, set the following.");
            pw.println("##Path of a voxel information file");
            pw.println("#voxelPath voxel.inf");
            pw.println("##########Overall settings");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to cut raypaths at piercing points (true)");
            pw.println("#cutAtPiercePoint ");
            pw.println("##########The following settings are valid when reusePath is false and cutAtPiercePoint is true.");
            pw.println("##Phases to compute pierce points for, listed using spaces (ScS)");
            pw.println("#piercePhases ");
            pw.println("##(double) Lower radius to compute pierce points for [km] (3480)");
            pw.println("#lowerPierceRadius ");
            pw.println("##(double) Upper radius to compute pierce points for [km] (3880)");
            pw.println("#upperPierceRadius ");
            pw.println("##(String) Name of structure to use for calculating pierce points (prem)");
            pw.println("#structureName ");
            pw.println("##########Settings for mapping");
            pw.println("##Mode of coloring of raypaths {0: single color, 1: color by phase, 2: bin by distance, 3: bin by azimuth,");
            pw.println("## 4: bin by back azimuth, 5: bin by turning-point-azimuth} (0)");
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
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
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
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        cutAtPiercePoint = property.parseBoolean("cutAtPiercePoint", "true");
        piercePhases = property.parseStringArray("piercePhases", "ScS");
        lowerPierceRadius = property.parseDouble("lowerPierceRadius", "3480");
        upperPierceRadius = property.parseDouble("upperPierceRadius", "3880");
        structureName = property.parseString("structureName", "prem");

        colorMode = property.parseInt("colorMode", "0");
        if (colorMode > 0)
            colorBinPath = property.parsePath("colorBinPath", null, true, workPath);
        drawOutsides = property.parseBoolean("drawOutsides", "false");
        if (colorMode > 0 && drawOutsides == true)
            outsideColorBinPath = property.parsePath("outsideColorBinPath", null, true, workPath);
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        legendJustification = property.parseString("legendJustification", "BR");

        // error prevention
        if (cutAtPiercePoint == false && colorMode == BIN_MIDAZIMUTH)
            throw new IllegalArgumentException("Cannot compute midazimuth without cutAtPiercePoint");

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
        gmtFileName = DatasetAid.generateOutputFileName("raypathMap", fileTag, dateStr, ".sh");
        psFileName = DatasetAid.generateOutputFileName("raypathMap", fileTag, dateStr, ".eps");
    }

    @Override
    public void run() throws IOException {

        if (reusePath != null) {
            checkReusePath();
        } else if (dataEntryPath != null){
            readAndOutput();
        } else {
            throw new IllegalStateException("Input folder or file not set");
        }

        if (voxelPath != null) {
            List<HorizontalPosition> voxelPositions = new VoxelInformationFile(voxelPath).getHorizontalPositions();
            List<String> perturbationLines = voxelPositions.stream().map(HorizontalPosition::toString).collect(Collectors.toList());
            Files.write(outPath.resolve(perturbationFileName), perturbationLines);
        }

        if (colorBinPath != null) colorBin = new ColorBinInformationFile(colorBinPath);
        if (outsideColorBinPath != null) outsideColorBin = new ColorBinInformationFile(outsideColorBinPath);

        outputGMT();
        System.err.println("After this finishes, please run " + outPath.resolve(gmtFileName));
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
        Set<DataEntry> validEntrySet = DataEntryListFile.readAsSet(dataEntryPath)
                .stream().filter(entry -> components.contains(entry.getComponent()))
                .collect(Collectors.toSet());

        Set<GlobalCMTID> events = validEntrySet.stream().map(entry -> entry.getEvent()).collect(Collectors.toSet());
        Set<Observer> observers = validEntrySet.stream().map(entry -> entry.getObserver()).collect(Collectors.toSet());

        outPath = DatasetAid.createOutputFolder(workPath, "raypathMap", folderTag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        EventListFile.write(events, outPath.resolve(eventFileName));
        ObserverListFile.write(observers, outPath.resolve(observerFileName));

        if (cutAtPiercePoint) {
            outputRaypathSegments(validEntrySet);
        } else {
            outputRaypaths(validEntrySet);
        }
    }

    private void outputRaypaths(Set<DataEntry> validEntrySet) throws IOException {
        // here, only a single raypath from source to receiver is needed for each event-observer pair,
        //  so use any one phase just for the sake of setting a phase name
        String piercePhase = piercePhases[0];
        Set<Raypath> raypaths = validEntrySet.stream().map(entry -> entry.toRaypath(piercePhase)).collect(Collectors.toSet());

        List<String> raypathLines = new ArrayList<>();
        raypaths.forEach(ray -> {
            raypathLines.add(lineFor(ray, ray));
        });
        Files.write(outPath.resolve(raypathFileName), raypathLines);
    }

    private void outputRaypathSegments(Set<DataEntry> validEntrySet) throws IOException {
        TauPPierceWrapper pierceTool = null;
        try {
            double[] pierceRadii = {lowerPierceRadius, upperPierceRadius};
            pierceTool = new TauPPierceWrapper(structureName, piercePhases, pierceRadii);
            pierceTool.compute(validEntrySet);
        } catch (TauModelException e) {
            throw new RuntimeException(e);
        }

        List<Raypath> allRaypaths = pierceTool.getAll();
        List<String> turningPointLines = new ArrayList<>();
        List<String> insideLines = new ArrayList<>();
        List<String> outsideLines = new ArrayList<>();
        for (Raypath raypath : allRaypaths) {
            // add all raypath segments inside layer
            List<Raypath> insideSegments = raypath.clipInsideLayer(lowerPierceRadius, upperPierceRadius);
            insideSegments.forEach(segment -> insideLines.add(lineFor(raypath, segment)));
            // add all bottom turning points inside layer
            insideSegments.stream().flatMap(segment -> segment.findTurningPoints().stream())
                    .forEach(pos -> turningPointLines.add(pos.toHorizontalPosition().toString()));
            // add all raypath segments outside layer
            List<Raypath> outsideSegments = raypath.clipOutsideLayer(lowerPierceRadius, upperPierceRadius);
            outsideSegments.forEach(segment -> outsideLines.add(lineFor(raypath, segment)));
        }

        System.err.println("Computation of raypath segments for " + allRaypaths.size() + " raypaths succeeded.");

        Files.write(outPath.resolve(turningPointFileName), turningPointLines);
        Files.write(outPath.resolve(insideFileName), insideLines);
        Files.write(outPath.resolve(outsideFileName), outsideLines);
    }

    /**
     * Creates output line for a raypath segment.
     * Output line: lat1 lon1 lat2 lon2 dist azimuth backAzimuth (turningAzimuth)
     *
     * @param raypath (Raypath) The whole raypath
     * @param raypathSegment (Raypath) The raypath segment
     * @return (String) Output line for the raypath segment
     */
    private String lineFor(Raypath raypath, Raypath raypathSegment) {
        // create output line
        // Only output latitude and longitude, without radius.
        // Phase is converted to its index number.
        // Math.floor is used because intervals will be set by integer, as "int_i <= val < int_{i+1}"
        String line = raypathSegment.getSource().toHorizontalPosition() + " "
                + raypathSegment.getReceiver().toHorizontalPosition() + " "
                + Arrays.asList(piercePhases).indexOf(raypathSegment.getPhaseName()) + " "
                + (int) Math.floor(raypath.getEpicentralDistanceDeg()) + " "
                + (int) Math.floor(raypath.getAzimuthDeg()) + " "
                + (int) Math.floor(raypath.getBackAzimuthDeg());
        // Turning point azimuth can be obtained only when turning point has been computed for.
        // The first turning point on the raypath is used.
        if (raypath.findTurningPoint(0) != null) {
            line = line + " " + (int) Math.floor(raypath.computeTurningAzimuthDeg(0));
        }
        return line;
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
            pw.println("gmt set FONT 25p");
            pw.println("gmt set FONT_LABEL 15p,Helvetica,black");
            pw.println("gmt set FONT_ANNOT_PRIMARY 25p");
            pw.println("");
            pw.println("# parameters for gmt pscoast");
            pw.println("R='-R" + decideMapRegion() + "'");
            pw.println("J='-JQ20'");
            pw.println("B='-Ba30 -BWeSn'");
            pw.println("C='-Ggray -Wthinnest,gray20'");
            pw.println("");
            pw.println("outputps=\"" + psFileName + "\"");
            pw.println("");
            pw.println("gmt pscoast -K $R $J $B $C -P  > $outputps");
            pw.println("");
            pw.println("#------- Events & Observers");
            pw.println("awk '{print $2, $3}' " + eventFileName + " | gmt psxy -: -J -R -O -P -Sa0.3 -G255/156/0 -Wthinnest -K  >> $outputps");
            pw.println("awk '{print $3, $4}' " + observerFileName + " | gmt psxy -: -J -R -O -P -Si0.3 -G71/187/243 -Wthinnest -K >> $outputps");
            pw.println("");

            pw.println("#------- Raypath");

            // raypathOutside
            if (cutAtPiercePoint && drawOutsides) {
                pw.println("while read line");
                pw.println("do");
                if (colorMode > 0) {
                    int nSections = outsideColorBin.getNSections();
                    if (nSections == 1) {
                        pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                        pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest," + outsideColorBin.getColorFor(0) + " >> $outputps");
                    } else {
                        pw.println("  valueForBin=$(echo $line | awk '{print $" + columnFor(colorMode) + "}')");
                        for (int i = 0; i < nSections + 2; i++) {
                            if (i == 0) {
                                pw.println("  if [ $valueForBin -lt " + outsideColorBin.getStartValueFor(i) + " ]; then");
                                pw.println("    echo \"value $valueForBin out of range\"");
                            } else if (i < nSections + 1) {
                                pw.println("  elif [ $valueForBin -lt " + outsideColorBin.getStartValueFor(i) + " ]; then");
                                pw.println("    echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                                pw.println("    gmt psxy -: -J -R -O -P -K -Wthinnest," + outsideColorBin.getColorFor(i - 1) + " >> $outputps");
                            } else {
                                pw.println("  else");
                                pw.println("    echo \"value $valueForBin out of range\"");
                            }
                        }
                        pw.println("  fi");
                    }
                } else {
                    pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                    pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest,lavender >> $outputps");
                }
                pw.println("done < " + outsideFileName);
                pw.println("");
            }

            // raypathInside
            pw.println("while read line");
            pw.println("do");
            if (colorMode > 0) {
                int nSections = colorBin.getNSections();
                if (nSections == 1) {
                    pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                    pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest," + colorBin.getColorFor(0) + " >> $outputps");
                } else {
                    pw.println("  valueForBin=$(echo $line | awk '{print $" + columnFor(colorMode) + "}')");
                    for (int i = 0; i < nSections + 2; i++) {
                        if (i == 0) {
                            pw.println("  if [ $valueForBin -lt " + colorBin.getStartValueFor(i) + " ]; then");
                            pw.println("    echo \"value $valueForBin out of range\"");
                        } else if (i < nSections + 1) {
                            pw.println("  elif [ $valueForBin -lt " + colorBin.getStartValueFor(i) + " ]; then");
                            pw.println("    echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                            pw.println("    gmt psxy -: -J -R -O -P -K -Wthinnest," + colorBin.getColorFor(i - 1) + " >> $outputps");
                        } else {
                            pw.println("  else");
                            pw.println("    echo \"value $valueForBin out of range\"");
                        }
                    }
                    pw.println("  fi");
                }
            } else {
                pw.println("  echo $line | awk '{print $1, $2, \"\\n\", $3, $4}' | \\");
                pw.println("  gmt psxy -: -J -R -O -P -K -Wthinnest,red >> $outputps");
            }
            if (cutAtPiercePoint) {
                pw.println("done < " + insideFileName);
            } else {
                pw.println("done < " + raypathFileName);
            }
            pw.println("");

            // turning points
            if (cutAtPiercePoint) {
                pw.println("awk '{print $1, $2}' " + turningPointFileName + " | gmt psxy -: -J -R -O -Sx0.3 -Wthinnest,black -K >> $outputps");
                pw.println("");
            }

            // perturbation points
            if (voxelPath != null) {
                pw.println("#------- Perturbation");
                pw.println("gmt psxy " + perturbationFileName + " -: -J -R -O -P -Sc0.2 -G0/255/0 -Wthinnest -K >> $outputps");
                pw.println("");
            }

            // legend
            if (colorMode > 0 && legendJustification.equals("none") == false) {
                pw.println("#------- Legend");
                pw.println("gmt pslegend -Dj" + legendJustification + "+w6c -F+g#FFFFFF+p1p,black -J -R -O -K << END >> $outputps");
                // header of legend
                pw.println("H - - " + headerFor(colorMode));
                // contents
                int nSections = colorBin.getNSections();
                for (int i = 0; i < nSections; i++) {
                    switch (colorMode) {
                    case COLOR_BY_PHASE:
                        // list up all phases for that color (in case one color is used for several consecutive phases)
                        String text = String.join(",", Arrays.copyOfRange(piercePhases, colorBin.getStartValueFor(i), colorBin.getStartValueFor(i + 1)));
                        pw.println("S 0.8c - 0.8c - 0.4p," + colorBin.getColorFor(i) + " 1.5c " + text);
                        break;
                    default:
                        // print degree range
                        pw.println("S 0.8c - 0.8c - 0.4p," + colorBin.getColorFor(i) + " 1.5c "
                                + colorBin.getStartValueFor(i) + "@.~" + colorBin.getStartValueFor(i + 1) + "@.");
                    }
                }
                pw.println("END");
                pw.println("");
            }

            pw.println("#------- Finalize");
            pw.println("gmt pstext $J $R -O -N -F+jLM+f30p,Helvetica,black << END >> $outputps");
            pw.println("END");
            pw.println("");
            pw.println("gmt psconvert $outputps -A -Tf -Qg4 -E100");
            pw.println("gmt psconvert $outputps -A -Tg -Qg4 -E100");
            pw.println("");
            pw.println("#-------- Clear");
            pw.println("rm -rf cp.cpt gmt.conf gmt.history");
            pw.println("echo \"Done!\"");
        }
        gmtPath.toFile().setExecutable(true);

    }

    /**
     * Get the colunm number in the raypath segment files that information for binning exists in.
     * @param colorMode
     * @return
     */
    private static int columnFor(int colorMode) {
        int binColumn;
        switch (colorMode) {
        case COLOR_BY_PHASE: binColumn = 5; break;
        case BIN_DISTANCE: binColumn = 6; break;
        case BIN_AZIMUTH: binColumn = 7; break;
        case BIN_BACKAZIMUTH: binColumn = 8; break;
        case BIN_MIDAZIMUTH: binColumn = 9; break;
        default: throw new IllegalArgumentException("colorMode out of range");
        }
        return binColumn;
    }

    private static String headerFor(int colorMode) {
        String header;
        switch (colorMode) {
        case COLOR_BY_PHASE: header = "Phase"; break;
        case BIN_DISTANCE: header = "Distance"; break;
        case BIN_AZIMUTH: header = "Azimuth"; break;
        case BIN_BACKAZIMUTH: header = "Back azimuth"; break;
        case BIN_MIDAZIMUTH: header = "Turn azimuth"; break;
        default: throw new IllegalArgumentException("colorMode out of range");
        }
        return header;
    }

    private String decideMapRegion() throws IOException {
        if (mapRegion != null) {
            return mapRegion;
        } else {
            double latMin = Double.MAX_VALUE;
            double latMax = -Double.MAX_VALUE;
            double lonMin = Double.MAX_VALUE;
            double lonMax = -Double.MAX_VALUE;

            Set<GlobalCMTID> events = EventListFile.read(outPath.resolve(eventFileName));
            for (GlobalCMTID event : events) {
                HorizontalPosition pos = event.getEventData().getCmtPosition();
                if (pos.getLatitude() < latMin) latMin = pos.getLatitude();
                if (pos.getLatitude() > latMax) latMax = pos.getLatitude();
                if (pos.getLongitude() < lonMin) lonMin = pos.getLongitude();
                if (pos.getLongitude() > lonMax) lonMax = pos.getLongitude();
            }

            Set<Observer> observers = ObserverListFile.read(outPath.resolve(observerFileName));
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
