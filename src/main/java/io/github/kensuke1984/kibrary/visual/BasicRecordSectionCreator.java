package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Creates record section for each event included in a {@link BasicIDFile}.
 * Time-shift from corrections is applied to observed waveform when being plotted.
 * Waveforms can be aligned on a specific phase or by a certain reduction slowness.
 * Travel time curves can be drawn on the graph.
 * <p>
 * A pair of a basic ID file and basic waveform file is required as input.
 * <p>
 * Event directories will be created under workPath if they do not already exist.
 * Output files will be created under these event directories.
 * If text files of waveform data for each observer exist under event directories, they will be reused;
 * if not, they will be generated using {@link BasicIDFile}.
 * Rewriting should not be needed because the content of these files should always be the same.
 * <p>
 * Output pdf files and their corresponding plt files will be created under each of the event directories.
 *
 * @author otsuru
 * @since 2021/12/11
 * @version 2022/7/27 renamed from visual.RecordSectionCreater to visual.BasicRecordSectionCreator and separated visual.BasicBinnedStackCreator
 */
public class BasicRecordSectionCreator extends Operation {

    /**
     * The interval of exporting travel times
     */
    private static final double TRAVEL_TIME_INTERVAL = 1;
    /**
     * The interval of deciding graph size; should be a multiple of TRAVEL_TIME_INTERVAL
     */
    private static final int GRAPH_SIZE_INTERVAL = 2;
    /**
     * How much space to provide at the rim of the graph in the y axis
     */
    private static final int Y_AXIS_RIM = 2;
    /**
     * How much space to provide at the rim of the graph in the time axis
     */
    private static final int TIME_RIM = 10;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * Path of a basic ID file
     */
    private Path basicIDPath;
    /**
     * Path of a basic file
     */
    private Path basicPath;
    /**
     * Path of folder containing event folders with waveform txt files
     */
    private Path referencePath;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private AmpStyle obsAmpStyle;
    private AmpStyle synAmpStyle;
    private double ampScale;

    private boolean byAzimuth;
    private boolean flipAzimuth;
    /**
     * Names of phases to plot travel time curves
     */
    private String[] displayPhases;
    /**
     * Names of phases to use to align the record section. The fastest of these arrivals is used.
     */
    private String[] alignPhases;
    /**
     * apparent slowness to use when reducing time [s/deg]
     */
    private double reductionSlowness;
    /**
     * Name of structure to compute travel times
     */
    private String structureName;

    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    private TauP_Time timeTool;

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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a waveform folder, when also plotting reference waveforms");
            pw.println("##  It must contain event folders with waveform txt files. Only observed waveforms will be plotted.");
            pw.println("#referencePath ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##Method for standarization of observed waveform amplitude, from {obsEach,synEach,obsMean,synMean} (synEach)");
            pw.println("#obsAmpStyle ");
            pw.println("##Method for standarization of synthetic waveform amplitude, from {obsEach,synEach,obsMean,synMean} (synEach)");
            pw.println("#synAmpStyle ");
            pw.println("##(double) Coefficient to multiply to all waveforms (1.0)");
            pw.println("#ampScale ");
            pw.println("##(boolean) Whether to plot the figure with azimuth as the Y-axis (false)");
            pw.println("#byAzimuth ");
            pw.println("##(boolean) Whether to set the azimuth range to [-180:180) instead of [0:360) (false)");
            pw.println("##  This is effective when using south-to-north raypaths in byAzimuth mode.");
            pw.println("#flipAzimuth ");
            pw.println("##Names of phases to plot travel time curves, listed using spaces. Only when byAzimuth is false.");
            pw.println("#displayPhases ");
            pw.println("##Names of phases to use for alignment, listed using spaces. When unset, the following reductionSlowness will be used.");
            pw.println("##  When multiple phases are set, the fastest arrival of them will be used for alignment.");
            pw.println("#alignPhases ");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg] (0)");
            pw.println("#reductionSlowness ");
            pw.println("##(String) Name of structure to compute travel times using TauP (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to be used [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to be used [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to be used [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth ");
        }
        System.err.println(outPath + " is created.");
    }

    public BasicRecordSectionCreator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        if (property.containsKey("referencePath"))
            referencePath = property.parsePath("referencePath", null, true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        obsAmpStyle = AmpStyle.valueOf(property.parseString("obsAmpStyle", "synEach"));
        synAmpStyle = AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");

        if (property.containsKey("displayPhases") && byAzimuth == false)
            displayPhases = property.parseStringArray("displayPhases", null);
        if (property.containsKey("alignPhases"))
            alignPhases = property.parseStringArray("alignPhases", null);
        reductionSlowness = property.parseDouble("reductionSlowness", "0");
        structureName = property.parseString("structureName", "prem").toLowerCase();

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");

        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");
    }

   @Override
   public void run() throws IOException {
       try {
           List<BasicID> ids = BasicIDFile.readAsList(basicIDPath, basicPath);

           // get all events included in basicIDs
           Set<GlobalCMTID> allEvents = ids.stream().filter(id -> components.contains(id.getSacComponent()))
                   .map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
           // eventDirs of events to be used
           Set<EventFolder> eventDirs;
           if (tendEvents.isEmpty()) {
               eventDirs = allEvents.stream()
                       .map(event -> new EventFolder(workPath.resolve(event.toString()))).collect(Collectors.toSet());
           } else {
               // choose only events that are included in tendEvents
               eventDirs = allEvents.stream().filter(event -> tendEvents.contains(event))
                       .map(event -> new EventFolder(workPath.resolve(event.toString()))).collect(Collectors.toSet());
           }
           if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
               return;
           }

           // set up taup_time tool
           if (alignPhases != null || displayPhases != null) {
               timeTool = new TauP_Time(structureName);
           }

           for (EventFolder eventDir : eventDirs) {
               // create event directory if it does not exist
               Files.createDirectories(eventDir.toPath());

               // set event to taup_time tool
               // The same instance is reused for all observers because computation takes time when changing source depth (see TauP manual).
               if (alignPhases != null || displayPhases != null) {
                   timeTool.setSourceDepth(eventDir.getGlobalCMTID().getEventData().getCmtPosition().getDepth());
               }

               // create plot for each component
               for (SACComponent component : components) {
                   List<BasicID> useIds = ids.stream().filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                           && id.getSacComponent().equals(component))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .collect(Collectors.toList());

                   Plotter plotter = new Plotter(eventDir, useIds, component);
                   plotter.plot();
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

    private static enum AmpStyle {
        obsEach,
        synEach,
        obsMean,
        synMean
    }

    private class Plotter {
        private final GnuplotLineAppearance obsAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        private final GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);
        private final GnuplotLineAppearance resultAppearance = new GnuplotLineAppearance(1, GnuplotColorName.blue, 1);
        private final GnuplotLineAppearance phaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.turquoise, 1);

        private final EventFolder eventDir;
        private final List<BasicID> ids;
        private final SACComponent component;

        private GnuplotFile profilePlot;
        private double obsMeanMax;
        private double synMeanMax;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(EventFolder eventDir, List<BasicID> ids, SACComponent component) {
            this.eventDir = eventDir;
            this.ids = ids;
            this.component = component;
        }

        public void plot() throws IOException, TauModelException {
            // prepare IDs
            if (ids.size() == 0) {
                return;
            }
            BasicIDPairUp pairer = new BasicIDPairUp(ids);
            List<BasicID> obsList = pairer.getObsList();
            List<BasicID> synList = pairer.getSynList();

            // set up plotter
            profilePlotSetup();

            // calculate the average of the maximum amplitudes of waveforms
            obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
            synMeanMax = synList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

            // variables to find the minimum and maximum distance for this event
            double minTime = Double.MAX_VALUE;
            double maxTime = -Double.MAX_VALUE;
            double minDistance = Double.MAX_VALUE;
            double maxDistance = -Double.MAX_VALUE;

            // for each pair of observed and synthetic waveforms
            for (int i = 0; i < obsList.size(); i++) {
                BasicID obsID = obsList.get(i);
                BasicID synID = synList.get(i);

                double distance = Math.toDegrees(obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeEpicentralDistanceRad(obsID.getObserver().getPosition()));
                double azimuth = Math.toDegrees(obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeAzimuthRad(obsID.getObserver().getPosition()));

                // skip waveform if distance or azimuth is out of bounds
                if (distance < lowerDistance || upperDistance < distance
                        || MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth) == false) {
                    continue;
                }

                // Compute reduce time by distance or phase travel time.
                double reduceTime = 0;
                if (alignPhases != null) {
                    timeTool.setPhaseNames(alignPhases);
                    timeTool.calculate(distance);
                    if (timeTool.getNumArrivals() < 1) {
                        System.err.println("Could not get arrival time of " + String.join(",", alignPhases) + " for " + obsID + " , skipping.");
                        return;
                    }
                    reduceTime = timeTool.getArrival(0).getTime();
                } else {
                    reduceTime = reductionSlowness * distance;
                }

                // update ranges
                double startTime = synID.getStartTime() - reduceTime;
                double endTime = synID.getStartTime() + synID.getNpts() / synID.getSamplingHz() - reduceTime;
                if (startTime < minTime) minTime = startTime;
                if (endTime > maxTime) maxTime = endTime;
                if (distance < minDistance) minDistance = distance;
                if (distance > maxDistance) maxDistance = distance;

                // in flipAzimuth mode, change azimuth range from [0:360) to [-180:180)
                if (flipAzimuth == true && 180 <= azimuth) {
                    profilePlotContent(obsID, synID, distance, azimuth - 360, reduceTime);
                } else {
                    profilePlotContent(obsID, synID, distance, azimuth, reduceTime);
                }
            }

            // set ranges
            if (minDistance > maxDistance || minTime > maxTime) return;
            int startDistance = (int) Math.floor(minDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL - Y_AXIS_RIM;
            int endDistance = (int) Math.ceil(maxDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL + Y_AXIS_RIM;
            profilePlot.setCommonYrange(startDistance, endDistance);
            profilePlot.setCommonXrange(minTime - TIME_RIM, maxTime + TIME_RIM);

            // add  travel time curves
            if (displayPhases != null) {
                plotTravelTimeCurve(startDistance, endDistance);
            }

            // plot
            profilePlot.write();
            if (!profilePlot.execute()) System.err.println("gnuplot failed!!");
        }

        private void profilePlotSetup() {
            String fileNameRoot;
            if (fileTag == null) {
                fileNameRoot = "profile_" + eventDir.toString() + "_" + component.toString();
            } else {
                fileNameRoot = "profile_" + fileTag + "_" + eventDir.toString() + "_" + component.toString();
            }
            profilePlot = new GnuplotFile(eventDir.toPath().resolve(fileNameRoot + ".plt"));

            profilePlot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
            profilePlot.setMarginH(15, 25);
            profilePlot.setMarginV(15, 15);
            profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
            profilePlot.unsetCommonKey();

            profilePlot.setCommonTitle(eventDir.toString());
            if (alignPhases != null) {
                profilePlot.setCommonXlabel("Time aligned on " + String.join(",", alignPhases) + "-wave arrival (s)");
            } else {
                profilePlot.setCommonXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
            }
            if (!byAzimuth) {
                profilePlot.setCommonYlabel("Distance (deg)");
                profilePlot.addLabel("station network azimuth", "graph", 1.0, 1.0);
            } else {
                profilePlot.setCommonYlabel("Azimuth (deg)");
                profilePlot.addLabel("station network distance", "graph", 1.0, 1.0);
            }
        }

        private void profilePlotContent(BasicID obsID, BasicID synID, double distance, double azimuth, double reduceTime)
                throws IOException, TauModelException {

            // output waveform data to text file if it has not already been done so
            String fileName = BasicIDFile.getWaveformTxtFileName(obsID);
            if (!Files.exists(eventDir.toPath().resolve(fileName))) {
                BasicIDFile.outputWaveformTxt(eventDir.toPath(), obsID, synID);
            }

            // decide the coefficient to amplify each waveform
            RealVector obsDataVector = new ArrayRealVector(obsID.getData());
            RealVector synDataVector = new ArrayRealVector(synID.getData());
            double obsMax = obsDataVector.getLInfNorm();
            double synMax = synDataVector.getLInfNorm();
            double obsAmp = selectAmp(obsAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = selectAmp(synAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);

            // Set "using" part. For x values, reduce time by distance or phase travel time. For y values, add either distance or azimuth.
            String obsUsingString;
            String synUsingString;
            if (!byAzimuth) {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(azimuth, 3, 2, " "),
                        "graph", 1.01, "first", distance);
                obsUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, distance);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, distance);
            } else {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(distance, 3, 2, " "),
                        "graph", 1.01, "first", azimuth);
                obsUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, azimuth);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, azimuth);
            }

            // add plot
            if (referencePath != null) {
                // when reference (the actual observed) exists, plot the actual (shifted) observed in black and the new (obtained) in blue
                Path referenceFilePath = Paths.get("..").resolve(referencePath).resolve(eventDir.toString()).resolve(fileName);
                profilePlot.addLine(referenceFilePath.toString(), obsUsingString, obsAppearance, "observed"); //TODO: this is not valid when ampStyle is not synEach
                profilePlot.addLine(fileName, synUsingString, synAppearance, "initial");
                profilePlot.addLine(fileName, obsUsingString, resultAppearance, "result");
            } else {
                profilePlot.addLine(fileName, obsUsingString, obsAppearance, "observed");
                profilePlot.addLine(fileName, synUsingString, synAppearance, "synthetic");
            }
        }

        private double selectAmp(AmpStyle style, double obsEachMax, double synEachMax, double obsMeanMax, double synMeanMax) {
            switch (style) {
            case obsEach:
                return obsEachMax / ampScale;
            case synEach:
                return synEachMax / ampScale;
            case obsMean:
                return obsMeanMax / ampScale;
            case synMean:
                return synMeanMax / ampScale;
            default:
                throw new IllegalArgumentException("Input AmpStyle is unknown.");
            }
        }

        private void plotTravelTimeCurve(double startDistance, double endDistance) throws IOException, TauModelException {
            int iNum = (int) Math.round((endDistance - startDistance) / TRAVEL_TIME_INTERVAL) + 1;

            // set names of all phases to display, and the phase to align if it is specified
            timeTool.setPhaseNames(displayPhases);
            if (alignPhases != null) {
                for (String phase : alignPhases) timeTool.appendPhaseName(phase);
            }

            // calculate travel times and store in arrays
            Double[][] travelTimes = new Double[displayPhases.length][iNum];
            Double[] alignTimes = new Double[iNum];
            for (int i = 0; i < iNum; i++) {
                double distance = startDistance + i * TRAVEL_TIME_INTERVAL;
                timeTool.calculate(distance);
                List<Arrival> arrivals = timeTool.getArrivals();
                for (int p = 0; p < displayPhases.length; p++) {
                    String phase = displayPhases[p];
                    Optional<Arrival> arrivalOpt = arrivals.stream().filter(arrival -> arrival.getPhase().getName().equals(phase)).findFirst();
                    if (arrivalOpt.isPresent()) {
                        travelTimes[p][i] = arrivalOpt.get().getTime();
                    }
                }
                if (alignPhases != null) {
                    List<String> alignPhaseList = Arrays.asList(alignPhases);
                    Optional<Arrival> arrivalOpt = arrivals.stream().filter(arrival -> alignPhaseList.contains(arrival.getPhase().getName())).findFirst();
                    if (arrivalOpt.isPresent()) {
                        alignTimes[i] = arrivalOpt.get().getTime();
                    }
                }
            }

            // output file and add curve
            for (int p = 0; p < displayPhases.length; p++) {
                String phase = displayPhases[p];
                String curveFileName = "curve_" + eventDir.toString() + "_" + component + "_" + phase + ".txt";
                Path curvePath = eventDir.toPath().resolve(curveFileName);
                boolean wrotePhaseLabel = false;
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(curvePath))) {
                    for (int i = 0; i < iNum; i++) {
                        // write only at distances where travel time exists
                        if (travelTimes[p][i] != null) {
                            double distance = startDistance + i * TRAVEL_TIME_INTERVAL;
                            // reduce time by alignPhase or reductionSlowness
                            if (alignPhases != null) {
                                // write only at distances where travel time of alignPhase exists
                                if (alignTimes[i] != null) {
                                    pw.println(distance + " " + (travelTimes[p][i] - alignTimes[i]));
                                }
                                // add label at first appearance
                                if (wrotePhaseLabel == false) {
                                    profilePlot.addLabel(phase, "first", travelTimes[p][i] - alignTimes[i], distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            } else {
                                double reduceTime = reductionSlowness * distance;
                                pw.println(distance + " " + (travelTimes[p][i] - reduceTime));
                                // add label at first appearance
                                if (wrotePhaseLabel == false) {
                                    profilePlot.addLabel(phase, "first", travelTimes[p][i] - reduceTime, distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            }
                        }
                    }
                }
                profilePlot.addLine(curveFileName, 2, 1, phaseAppearance, phase);
            }
        }
    }

}
