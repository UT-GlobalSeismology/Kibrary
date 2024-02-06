package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.perturbation.PerturbationListFile;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Creates a movie of partials inside a cross section.
 * <p>
 * NOTE: the voxel volume is NOT multiplied.
 *
 * @author otsuru
 * @since 2023/5/29
 */
public class PartialsMovieMaker extends Operation {

    /**
     * How much finer to make the grid
     */
    public static final int GRID_SMOOTHING_FACTOR = 5;
    /**
     * Size of vertical grid with respect to horizontal grid
     */
    public static final int VERTICAL_ENLARGE_FACTOR = 2;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * components to make maps for
     */
    private Set<SACComponent> components;
    /**
     * variable types to make maps for
     */
    private Set<VariableType> variableTypes;
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<String> tendObservers = new HashSet<>();

    /**
     * partial waveform folder
     */
    private Path partialPath;

    private double pos0Latitude;
    private double pos0Longitude;
    private double pos1Latitude;
    private double pos1Longitude;
    /**
     * Distance of the starting point along arc before position 0.
     */
    private double beforePos0Deg;
    /**
     * Distance of the ending point along arc after either position 0 or position 1.
     */
    private double afterPosDeg;
    /**
     * Whether the ending point should be decided with respect to position 0 or position 1.
     */
    private boolean useAfterPos1;

    /**
     * Radius of zero point of vertical axis.
     */
    private double zeroPointRadius;
    /**
     * Name of zero point of vertical axis. (ex. "CMB")
     */
    private String zeroPointName;
    /**
     * Whether to flip vertical axis.
     */
    private boolean flipVerticalAxis;

    /**
     * Latitude margin at both ends of region.
     */
    private double marginLatitudeRaw;
    /**
     * Whether marginLatitudeRaw is set in [km] or [deg].
     */
    private boolean setMarginLatitudeByKm;
    /**
     * Longitude margin at both ends of region.
     */
    private double marginLongitudeRaw;
    /**
     * Whether marginLongitudeRaw is set in [km] or [deg].
     */
    private boolean setMarginLongitudeByKm;
    /**
     * Radius margin at both ends of region [km].
     */
    private double marginRadius;

    private double scale;
    /**
     * Whether to display map as mosaic without smoothing
     */
    private boolean mosaic;

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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
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
            pw.println("##########Settings of great circle arc to display in the cross section.");
            pw.println("##(double) Latitude of position 0, must be set.");
            pw.println("#pos0Latitude ");
            pw.println("##(double) Longitude of position 0, must be set.");
            pw.println("#pos0Longitude ");
            pw.println("##(double) Latitude of position 1, must be set.");
            pw.println("#pos1Latitude ");
            pw.println("##(double) Longitude of position 1, must be set.");
            pw.println("#pos1Longitude ");
            pw.println("##(double) Distance along arc before position 0 [deg]. (0)");
            pw.println("#beforePos0Deg ");
            pw.println("##(double) Distance along arc after position 0 [deg]. If not set, the following afterPos1Deg will be used.");
            pw.println("#afterPos0Deg ");
            pw.println("##(double) Distance along arc after position 1 [deg]. (0)");
            pw.println("#afterPos1Deg ");
            pw.println("##########Radius display settings.");
            pw.println("##(double) Radius of zero point of vertical axis [km]. (0)");
            pw.println("#zeroPointRadius 3480");
            pw.println("##Name of zero point of vertical axis. (0)");
            pw.println("#zeroPointName CMB");
            pw.println("##(boolean) Whether to flip vertical axis. (false)");
            pw.println("#flipVerticalAxis true");
            pw.println("##########The following should be set to half of dLatitude, dLongitude, and dRadius used to design voxels (or smaller).");
            pw.println("##(double) Latitude margin at both ends of region [km]. If this is unset, the following marginLatitudeDeg will be used.");
            pw.println("#marginLatitudeKm ");
            pw.println("##(double) Latitude margin at both ends of region [deg]. (2.5)");
            pw.println("#marginLatitudeDeg ");
            pw.println("##(double) Longitude margin at both ends of region [km]. If this is unset, the following marginLongitudeDeg will be used.");
            pw.println("#marginLongitudeKm ");
            pw.println("##(double) Longitude margin at both ends of region [deg]. (2.5)");
            pw.println("#marginLongitudeDeg ");
            pw.println("##(double) Radius margin at both ends of region [km]. (25)");
            pw.println("#marginRadiusKm ");
            pw.println("##########Parameters for perturbation values.");
            pw.println("##(double) Range of scale. (1)");
            pw.println("#scale ");
            pw.println("##(boolean) Whether to display map as mosaic without smoothing. (false)");
            pw.println("#mosaic ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsMovieMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialPath = property.parsePath("partialPath", null, true, workPath);
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU"))
                .map(VariableType::valueOf).collect(Collectors.toSet());
        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObservers = Arrays.stream(property.parseStringArray("tendObservers", null)).collect(Collectors.toSet());

        pos0Latitude = property.parseDouble("pos0Latitude", null);
        pos0Longitude = property.parseDouble("pos0Longitude", null);
        pos1Latitude = property.parseDouble("pos1Latitude", null);
        pos1Longitude = property.parseDouble("pos1Longitude", null);
        beforePos0Deg = property.parseDouble("beforePos0Deg", "0");
        if (property.containsKey("afterPos0Deg")) {
            afterPosDeg = property.parseDouble("afterPos0Deg", null);
            useAfterPos1 = false;
        } else {
            afterPosDeg = property.parseDouble("afterPos1Deg", "0");
            useAfterPos1 = true;
        }

        zeroPointRadius = property.parseDouble("zeroPointRadius", "0");
        zeroPointName = property.parseString("zeroPointName", "0");
        if (zeroPointRadius < 0) throw new IllegalArgumentException("zeroPointRadius must be positive.");
        flipVerticalAxis = property.parseBoolean("flipVerticalAxis", "false");

        if (property.containsKey("marginLatitudeKm")) {
            marginLatitudeRaw = property.parseDouble("marginLatitudeKm", null);
            setMarginLatitudeByKm = true;
        } else {
            marginLatitudeRaw = property.parseDouble("marginLatitudeDeg", "2.5");
            setMarginLatitudeByKm = false;
        }
        if (marginLatitudeRaw <= 0) throw new IllegalArgumentException("marginLatitude must be positive.");
        if (property.containsKey("marginLongitudeKm")) {
            marginLongitudeRaw = property.parseDouble("marginLongitudeKm", null);
            setMarginLongitudeByKm = true;
        } else {
            marginLongitudeRaw = property.parseDouble("marginLongitudeDeg", "2.5");
            setMarginLongitudeByKm = false;
        }
        if (marginLongitudeRaw <= 0) throw new IllegalArgumentException("marginLongitude must be positive.");
        marginRadius = property.parseDouble("marginRadiusKm", "25");
        if (marginRadius <= 0) throw new IllegalArgumentException("marginRadius must be positive.");

//        amplification = property.parseDouble("amplification", "1e29");
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

        Set<FullPosition> discretePositions = partialIDs.stream().map(partialID -> partialID.getVoxelPosition()).collect(Collectors.toSet());

        // create output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "movie", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        for (SACComponent component : components) {
            for (VariableType variableType : variableTypes) {
                // root for names of file of this variable type, regardless of timeiwndow or timestep
                String fileNameRoot = "d" + variableType + "Normalized";

                for (GlobalCMTID event : tendEvents) {
                    for (String observerName : tendObservers) {
                        List<PartialID> partialsForEntry = partialIDs.stream().filter(partial ->
                                partial.getSacComponent().equals(component)
                                && partial.getVariableType().equals(variableType)
                                && partial.getGlobalCMTID().equals(event)
                                && partial.getObserver().toString().equals(observerName))
                                .collect(Collectors.toList());
                        if (partialsForEntry.size() == 0) continue;
                        System.err.println("Working for " + component  + " " + variableType + " " + event + " " + observerName);

                        double[] startTimes = partialsForEntry.stream().mapToDouble(PartialID::getStartTime).distinct().sorted().toArray();

                        // for each timewindow
                        for (double startTime : startTimes) {
                            List<PartialID> partialsForWindow = partialsForEntry.stream()
                                    .filter(partial -> partial.getStartTime() == startTime).collect(Collectors.toList());

                            // create folder
                            String seriesName = event + "_" + observerName + "_" + component + "_" + variableType + "_w"
                                    + MathAid.padToString(startTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.PRECISION, true, "d");
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

                            CrossSectionWorker worker = new CrossSectionWorker(pos0Latitude, pos0Longitude, pos1Latitude, pos1Longitude,
                                    beforePos0Deg, afterPosDeg, useAfterPos1, zeroPointRadius, zeroPointName, flipVerticalAxis,
                                    marginLatitudeRaw, setMarginLatitudeByKm, marginLongitudeRaw, setMarginLongitudeByKm, marginRadius,
                                    scale, mosaic, false, 0, fileNameRoot, discretePositions);

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
                                        + MathAid.padToString(time, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.PRECISION, true, "d");
                                Path outSnapshotPath = seriesPath.resolve(snapshotName);
                                Files.createDirectories(outSnapshotPath);

                                // output discrete perturbation file
                                Path outputDiscretePath = outSnapshotPath.resolve(fileNameRoot + ".lst");
                                PerturbationListFile.write(discreteMap, outputDiscretePath);

                                // output data for cross section
                                worker.computeCrossSection(discreteMap, null, outSnapshotPath);
                            }

                            // write shellscript to map each snapshot and convert them to gif movie
                            String scaleLabel = "@%12%\\266@%%U/@%12%\\266@%%" + variableType + " (normalized)";
                            worker.writeScripts(scaleLabel, seriesPath);
                            writeParentShellScript(fileNameRoot, seriesPath.resolve(fileNameRoot + "Movie.sh"));
                        }
                    }
                }
            }
        }
        System.err.println("After this finishes, please enter each " + outPath
                + "/event_observer_component_variable(Folder)/ and run d*NormailzedMovie.sh");
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
            pw.println("    ln -s ../" + fileNameRoot + "Section.sh .");
            pw.println("    ln -s ../rAnnotation.txt .");
            pw.println("    ln -s ../cp_master.cpt .");
            pw.println("    sh " + fileNameRoot + "Section.sh");
            pw.println("    wait");
            pw.println("    unlink " + fileNameRoot + "Section.sh");
            pw.println("    unlink rAnnotation.txt");
            pw.println("    unlink cp_master.cpt");
            pw.println("    cd ..");
            pw.println("done");
            pw.println("");
            pw.println("echo \"Making movie\"");
            pw.println("convert -delay 30 -loop 1 snapshot_*/" + fileNameRoot + "Section.png " + fileNameRoot + "Movie.gif");
            pw.println("echo \"Done!\"");
            pw.println("");
        }
    }

}
