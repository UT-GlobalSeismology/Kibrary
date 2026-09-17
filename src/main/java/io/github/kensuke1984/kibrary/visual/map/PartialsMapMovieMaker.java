package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Interpolation;
import io.github.kensuke1984.kibrary.math.geometry.CoordinateConverter;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.perturbation.ScalarType;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformation;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Creates a movie of partials drawn as horizontal maps.
 * <p>
 * This does almost the same thing as {@link PartialsMovieMaker}, but each snapshot is drawn
 * as a set of horizontal map layers (as in {@link SensitivityKernelMapper3D} with map=true)
 * instead of as a cross section.
 * <p>
 * The elapsed time is written at the bottom left of each snapshot, and the phases arriving at that time
 * (when a travel time information file is given) are written at the bottom right.
 * <p>
 * The "convert" command of ImageMagick must be installed to run the script produced by this program.
 * <p>
 * NOTE: the voxel volume is NOT multiplied.
 *
 * @author AI by otsuru
 * @since 2026/9/4
 */
public class PartialsMapMovieMaker extends Operation {

    /**
     * Half of the time to display arriving phase.
     */
    private static final double HALF_PHASE_TIME = 5.0;
    /**
     * Font size of the time and phase annotations placed on each snapshot [pt].
     */
    private static final int ANNOTATION_FONT_SIZE = 200;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;
    /**
     * Partial waveform folder.
     */
    private Path partialPath;
    /**
     * Variable types to use.
     */
    private Set<VariableType> variableTypes;
    /**
     * Events to work for.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Observers to work for.
     */
    private Set<String> tendObservers = new HashSet<>();
    /**
     * Path of a travel time information file.
     */
    private Path travelTimePath;

    /**
     * Path of coordinate converter file to be used when interpolating.
     */
    private Path converterPath;
    private double[] boundaries;
    /**
     * Indices of layers to display in the figure. Listed from the inside. Layers are numbered 0, 1, 2, ... from the inside.
     */
    private int[] displayLayers;
    private int nPanelsPerRow;
    /**
     * Map region in the form lonMin/lonMax/latMin/latMax, when it is set manually.
     */
    private String mapRegion;
    /**
     * Code for GMT map projection following '-J'. (ex.: Q15).
     */
    private String mapProjectionCode;
    private boolean forSlides;

    private double marginLatitude;
    private boolean setLatitudeByKm;
    private double marginLongitude;
    private boolean setLongitudeByKm;
    private double scale;
    /**
     * Whether to display map as mosaic without smoothing.
     */
    private boolean mosaic;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##VariableTypes to be used, listed using spaces. (MU)");
            pw.println("#variableTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces, must be set.");
            pw.println("#tendObservers ");
            pw.println("##Path of a travel time information file, if displaying travel times.");
            pw.println("#travelTimePath travelTime.inf");
            pw.println("##########The following are parameters for the map.");
            pw.println("##Path of coordinate converter file, when interpolating on curvilinear grid.");
            pw.println("#converterPath converter.inf");
            pw.println("##(double[]) The display values of each layer boundary, listed from the inside using spaces. (0 50 100 150 200 250 300 350 400)");
            pw.println("#boundaries ");
            pw.println("##(int[]) Indices of layers to display, listed from the inside using spaces, when specific layers are to be displayed.");
            pw.println("##  Layers are numbered 0, 1, 2, ... from the inside.");
            pw.println("#displayLayers ");
            pw.println("##(int) Number of panels to display in each row. (4)");
            pw.println("#nPanelsPerRow ");
            pw.println("##To specify the map region, set it in the form lonMin/lonMax/latMin/latMax.");
            pw.println("#mapRegion -180/180/-90/90");
            pw.println("##Code for GMT map projection following '-J'. (Q15)");
            pw.println("#mapProjectionCode ");
            pw.println("##(boolean) Whether to enlarge labels and use stronger colors for slides. (true)");
            pw.println("#forSlides ");
            pw.println("##########The following should be set to half of dLatitude and dLongitude used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##########Parameters for perturbation values.");
            pw.println("##(double) Range of scale. (1)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing. (false)");
            pw.println("#mosaic ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsMapMovieMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialPath = property.parsePath("partialPath", null, true, workPath);
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU"))
                .map(VariableType::valueOf).collect(Collectors.toSet());
        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());
        if (property.containsKey("travelTimePath"))
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);

        if (property.containsKey("converterPath")) {
            converterPath = property.parsePath("converterPath", null, true, workPath);
        }
        boundaries = property.parseDoubleArray("boundaries", "0 50 100 150 200 250 300 350 400");
        if (property.containsKey("displayLayers")) displayLayers = property.parseIntArray("displayLayers", null);
        nPanelsPerRow = property.parseInt("nPanelsPerRow", "4");
        if (property.containsKey("mapRegion")) mapRegion = property.parseString("mapRegion", null);
        mapProjectionCode = property.parseString("mapProjectionCode", "Q15");
        forSlides = property.parseBoolean("forSlides", "true");

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitude = property.parseDouble("marginLatitudeKm", null);
            setLatitudeByKm = true;
        } else {
            marginLatitude = property.parseDouble("marginLatitudeDeg", "2.5");
            setLatitudeByKm = false;
        }
        if (marginLatitude <= 0) throw new IllegalArgumentException("marginLatitude must be positive");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitude = property.parseDouble("marginLongitudeKm", null);
            setLongitudeByKm = true;
        } else {
            marginLongitude = property.parseDouble("marginLongitudeDeg", "2.5");
            setLongitudeByKm = false;
        }
        if (marginLongitude <= 0) throw new IllegalArgumentException("marginLongitude must be positive");

        scale = property.parseDouble("scale", "1");
        mosaic = property.parseBoolean("mosaic", "false");
    }

    @Override
    public void run() throws IOException {

        // read input
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream()
                .filter(id -> id.getParameterType().equals(ParameterType.VOXEL)).collect(Collectors.toList());
        if (partialIDs.size() == 0) {
            System.err.println("No 3-D partials.");
            return;
        }

        Set<FullPosition> discretePositions = partialIDs.stream().map(partial -> partial.getVoxelPosition())
                .collect(Collectors.toSet());
        double[] radii = discretePositions.stream().mapToDouble(FullPosition::getR).distinct().sorted().toArray();

        // read travel time information
        Set<TravelTimeInformation> travelTimeInfoSet = null;
        if (travelTimePath != null) {
            travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
        }

        // read coordinate converter file
        CoordinateConverter converter = (converterPath != null) ? new CoordinateConverter(converterPath) : null;

        // decide map region
        String coveredRegion = ScalarMapShellscript.decideMapRegion(discretePositions);
        if (mapRegion == null) mapRegion = coveredRegion;
        boolean crossDateLine = HorizontalPosition.crossesDateLine(discretePositions);
        double gridInterval = ScalarMapShellscript.decideGridSampling(discretePositions);

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "mapMovie", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (SACComponent component : components) {
            ScalarType scalarType = ScalarType.partialOf(component);

            for (VariableType variable : variableTypes) {
                // name of scalar file of this variable type, regardless of timewindow or timestep
                String scalarFileName = ScalarListFile.generateFileName(variable, scalarType, "normalized");
                String scalarFileNameXY = ScalarListFile.generateFileName(variable, scalarType, "normalized_XY");

                for (GlobalCMTID event : tendEvents) {

                    for (String observerName : tendObservers) {
                        List<PartialID> partialsForEntry = partialIDs.stream().filter(partial -> partial.getSacComponent().equals(component)
                                && partial.getVariableType().equals(variable)
                                && partial.getGlobalCMTID().equals(event)
                                && partial.getObserver().toString().equals(observerName))
                                .collect(Collectors.toList());
                        if (partialsForEntry.size() == 0) continue;
                        System.err.println("Working for " + component + " " + variable + " " + event + " " + observerName);

                        // find travel time info for this event and observer
                        TravelTimeInformation travelTimeInfo = null;
                        if (travelTimeInfoSet != null) {
                            travelTimeInfo = travelTimeInfoSet.stream()
                                    .filter(info -> info.getEvent().equals(event) && info.getObserver().toString().equals(observerName))
                                    .findFirst().orElse(null);
                        }

                        double[] startTimes = partialsForEntry.stream().mapToDouble(PartialID::getStartTime).distinct().sorted().toArray();

                        // for each time window
                        for (double startTime : startTimes) {
                            List<PartialID> partialsForWindow = partialsForEntry.stream()
                                    .filter(partial -> partial.getStartTime() == startTime).collect(Collectors.toList());

                            // create folder
                            String seriesName = event + "_" + observerName + "_" + component + "_" + variable + "_w"
                                    + MathAid.padToString(startTime, TimeWindow.TYPICAL_MAX_INTEGER_DIGITS, TimeWindow.DECIMALS, true, "d");
                            Path seriesPath = outPath.resolve(seriesName);
                            Files.createDirectories(seriesPath);

                            // get npts and samplingHz for this window
                            double[] nptsTmp = partialsForWindow.stream().mapToDouble(PartialID::getNpts).distinct().sorted().toArray();
                            if (nptsTmp.length != 1) throw new IllegalStateException("npts mismatch in partial IDs for " + event + " " + observerName);
                            double npts = nptsTmp[0];
                            double[] samplingHzTmp = partialsForWindow.stream().mapToDouble(PartialID::getSamplingHz).distinct().sorted().toArray();
                            if (samplingHzTmp.length != 1) throw new IllegalStateException("samplingHz mismatch in partial IDs for " + event + " " + observerName);
                            double samplingHz = samplingHzTmp[0];

                            // normalize by maximum value
                            double normalization = partialsForWindow.stream()
                                    .mapToDouble(partialID -> partialID.toTrace().getYVector().getLInfNorm()).max().getAsDouble();
                            System.err.println("  Normalizing by maximum value " + normalization);

                            // write shellscripts for mapping each snapshot
                            ScalarMapShellscript script = new ScalarMapShellscript(variable, scalarType, "normalized", radii, boundaries,
                                    coveredRegion, mapRegion, mapProjectionCode, gridInterval, scale, nPanelsPerRow);
                            if (displayLayers != null) script.setDisplayLayers(displayLayers);
                            script.setForSlides(forSlides);
                            script.write(seriesPath);
                            String plotFileNameRoot = script.getPlotFileNameRoot();

                            // for each time step
                            for (int i = 0; i < npts; i++) {
                                double time = startTime + i / samplingHz;

                                // value of partial derivative at each voxel
                                Map<FullPosition, Double> discreteMap = new HashMap<>();
                                for (PartialID partial : partialsForWindow) {
                                    double[] data = partial.getData();
                                    discreteMap.put(partial.getVoxelPosition(), data[i] / normalization);
                                }

                                // create folder for each snapshot
                                // The number part of output file names has to be padded with 0 for the "convert" command to work.
                                String snapshotName = "snapshot_t"
                                        + MathAid.padToString(time, TimeWindow.TYPICAL_MAX_INTEGER_DIGITS, TimeWindow.DECIMALS, true, "d");
                                Path outSnapshotPath = seriesPath.resolve(snapshotName);
                                Files.createDirectories(outSnapshotPath);

                                // output discrete perturbation file
                                ScalarListFile.write(discreteMap, outSnapshotPath.resolve(scalarFileName));

                                // interpolate
                                Map<FullPosition, Double> interpolatedMap;
                                if (converter != null) {
                                    interpolatedMap = Interpolation.curvilinearInEachMapLayer(discreteMap, gridInterval, converter, mosaic);
                                } else {
                                    interpolatedMap = Interpolation.inEachMapLayer(discreteMap, gridInterval,
                                            marginLatitude, setLatitudeByKm, marginLongitude, setLongitudeByKm, crossDateLine, mosaic);
                                }

                                // output interpolated perturbation file, in range [0:360) when crossDateLine==true so that mapping will succeed
                                ScalarListFile.write(interpolatedMap, crossDateLine, outSnapshotPath.resolve(scalarFileNameXY));

                                // write out time, to be placed at bottom left of map
                                Files.write(outSnapshotPath.resolve("textL.txt"), ("t = " + time).getBytes());

                                // write out phases arriving at this time, to be placed at bottom right of map
                                if (travelTimeInfo != null) {
                                    List<String> arrivingPhases = new ArrayList<>();
                                    for (Map.Entry<Phase, Double> entry : travelTimeInfo.getUsePhases().entrySet()) {
                                        if (Math.abs(time - entry.getValue()) < HALF_PHASE_TIME)
                                            arrivingPhases.add(entry.getKey().toString());
                                    }
                                    for (Map.Entry<Phase, Double> entry : travelTimeInfo.getAvoidPhases().entrySet()) {
                                        if (Math.abs(time - entry.getValue()) < HALF_PHASE_TIME)
                                            arrivingPhases.add(entry.getKey().toString());
                                    }
                                    Files.write(outSnapshotPath.resolve("textR.txt"), String.join(", ", arrivingPhases).getBytes());
                                } else {
                                    Files.write(outSnapshotPath.resolve("textR.txt"), "".getBytes());
                                }
                            }

                            // write shellscript to map each snapshot and convert them to gif movie
                            writeParentShellScript(plotFileNameRoot, seriesPath.resolve(plotFileNameRoot + "Movie.sh"));
                        }
                    }
                }
            }
        }
        System.err.println("After this finishes, please enter each " + outPath
                + "/event_observer_component_variable_window(Folder)/ and run *Movie.sh");
    }

    private void writeParentShellScript(String fileNameRoot, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("");
            pw.println("echo \"Making snapshots\"");
            pw.println("for i in $(ls -d snapshot_*)");
            pw.println("do");
            pw.println("    cd $i");
            pw.println("    echo \"$i\"");
            pw.println("    ln -s ../" + fileNameRoot + "Grid.sh .");
            pw.println("    ln -s ../" + fileNameRoot + "Map.sh .");
            pw.println("    ln -s ../cp_master.cpt .");
            pw.println("    sh " + fileNameRoot + "Grid.sh");
            pw.println("    wait");
            pw.println("    sh " + fileNameRoot + "Map.sh");
            pw.println("    wait");
            pw.println("    convert " + fileNameRoot + "Map.png \\");
            pw.println("        -gravity SouthWest -pointsize " + ANNOTATION_FONT_SIZE + " -fill black -annotate +50+50 \"$(cat textL.txt)\" \\");
            pw.println("        -gravity SouthEast -pointsize " + ANNOTATION_FONT_SIZE + " -fill black -annotate +50+50 \"$(cat textR.txt)\" \\");
            pw.println("        " + fileNameRoot + "Map.png");
            pw.println("    unlink " + fileNameRoot + "Grid.sh");
            pw.println("    unlink " + fileNameRoot + "Map.sh");
            pw.println("    unlink cp_master.cpt");
            pw.println("    cd ..");
            pw.println("done");
            pw.println("");
            pw.println("echo \"Making movie\"");
            pw.println("convert -delay 30 -loop 1 snapshot_*/" + fileNameRoot + "Map.png " + fileNameRoot + "Movie.gif");
            pw.println("echo \"Done!\"");
            pw.println("");
        }
    }

}
