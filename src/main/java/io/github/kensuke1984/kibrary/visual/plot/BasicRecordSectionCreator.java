package io.github.kensuke1984.kibrary.visual.plot;

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
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Creates a record section for each event included in a {@link BasicIDFile}.
 * Time-shift from corrections can be applied to observed waveform when being plotted.
 * Waveforms can be aligned on a specific phase or by a certain reduction slowness.
 * Travel time curves can be drawn on the graph.
 * <p>
 * A basic waveform folder is required as input.
 * Additional basic waveform folders can be given when plotting multiple synthetic seismograms.
 * <p>
 * The entries to plot will be determined by whether they are included in mainBasicPath.
 * When plotting additional synthetic waveforms, their amplitudes will be adjusted with parameters for the main synthetic waveforms.
 * <p>
 * Text files of waveform data will be created in event folders under their corresponding basic waveform folders.
 * Output pdf files and their corresponding plt files will be created in event directories under workPath.
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
     * Path of a basic waveform folder
     */
    private Path mainBasicPath;
    /**
     * Path of reference waveform folder 1
     */
    private Path refBasicPath1;
    /**
     * Path of reference waveform folder 2
     */
    private Path refBasicPath2;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private BasicPlotAid.AmpStyle obsAmpStyle;
    private BasicPlotAid.AmpStyle synAmpStyle;
    private double ampScale;

    /**
     * Whether to plot the figure with azimuth as the Y-axis
     */
    private boolean byAzimuth;
    /**
     * Whether to set the azimuth range to [-180:180) instead of [0:360)
     */
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
     * Path of structure file to compute travel times instead of structureName. This is referred only for anisotimeMode
     */
    private Path structurePath;
    /**
     * Name of structure to compute travel times
     */
    private String structureName;
    /**
     * Use anisotime to compute travel times instead of TauP
     */
    private boolean anisotimeMode;

    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    private int unshiftedObsStyle;
    private String unshiftedObsName;
    private int shiftedObsStyle;
    private String shiftedObsName;
    private int mainSynStyle;
    private String mainSynName;
    private int refSynStyle1;
    private String refSynName1;
    private int refSynStyle2;
    private String refSynName2;

    /**
     * Inxtance of tool to use to compute travel times
     */
    private TauP_Time timeTool;
    String dateStr;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic waveform folder (.)");
            pw.println("#mainBasicPath ");
            pw.println("##Path of reference basic waveform folder 1, when plotting their waveforms");
            pw.println("#refBasicPath1 ");
            pw.println("##Path of reference basic waveform folder 2, when plotting their waveforms");
            pw.println("#refBasicPath2 ");
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
            pw.println("##Path of a structure file you want to use. If this is unset, the following structureName will be referenced.");
            pw.println("##This option is valid when the floowing anisotimeMode is true");
            pw.println("#structurePath ");
            pw.println("##(String) Name of structure to compute travel times (prem)");
            pw.println("#structureName ");
            pw.println("##Whether you use anisotime to compute TRAVEL TIME CURVES instead of TauP (false).");
            pw.println("##Note that alignPhases are computed using TauP even if this is ture.");
            pw.println("#anisotimeMode true");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to be used [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to be used [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to be used [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth ");
            pw.println("##Plot style for unshifted observed waveform, from {0:no plot, 1:gray, 2:black} (1)");
            pw.println("#unshiftedObsStyle 0");
            pw.println("##Name for unshifted observed waveform (unshifted)");
            pw.println("#unshiftedObsName ");
            pw.println("##Plot style for shifted observed waveform, from {0:no plot, 1:gray, 2:black} (2)");
            pw.println("#shiftedObsStyle ");
            pw.println("##Name for shifted observed waveform (shifted)");
            pw.println("#shiftedObsName observed");
            pw.println("##Plot style for main synthetic waveform, from {0:no plot, 1:red, 2:green, 3:blue} (1)");
            pw.println("#mainSynStyle 2");
            pw.println("##Name for main synthetic waveform (synthetic)");
            pw.println("#mainSynName recovered");
            pw.println("##Plot style for reference synthetic waveform 1, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle1 1");
            pw.println("##Name for reference synthetic waveform 1 (reference1)");
            pw.println("#refSynName1 initial");
            pw.println("##Plot style for reference synthetic waveform 2, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle2 ");
            pw.println("##Name for reference synthetic waveform 2 (reference2)");
            pw.println("#refSynName2 ");
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

        mainBasicPath = property.parsePath("mainBasicPath", ".", true, workPath);
        if (property.containsKey("refBasicPath1"))
            refBasicPath1 = property.parsePath("refBasicPath1", ".", true, workPath);
        if (property.containsKey("refBasicPath2"))
            refBasicPath2 = property.parsePath("refBasicPath2", ".", true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        obsAmpStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("obsAmpStyle", "synEach"));
        synAmpStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");

        if (property.containsKey("displayPhases") && byAzimuth == false)
            displayPhases = property.parseStringArray("displayPhases", null);
        if (property.containsKey("alignPhases"))
            alignPhases = property.parseStringArray("alignPhases", null);
        reductionSlowness = property.parseDouble("reductionSlowness", "0");
        anisotimeMode = property.parseBoolean("anisotimeMode", "false");
        if (anisotimeMode)
            structurePath = property.parsePath("structurePath", null, true, workPath);
        structureName = property.parseString("structureName", "prem").toLowerCase();

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");

        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");

        unshiftedObsStyle = property.parseInt("unshiftedObsStyle", "1");
        unshiftedObsName = property.parseString("unshiftedObsName", "unshifted");
        shiftedObsStyle = property.parseInt("shiftedObsStyle", "2");
        shiftedObsName = property.parseString("shiftedObsName", "shifted");
        mainSynStyle = property.parseInt("mainSynStyle", "1");
        mainSynName = property.parseString("mainSynName", "synthetic");
        refSynStyle1 = property.parseInt("refSynStyle1", "0");
        refSynName1 = property.parseString("refSynName1", "reference1");
        refSynStyle2 = property.parseInt("refSynStyle2", "0");
        refSynName2 = property.parseString("refSynName2", "reference2");
        if (refSynStyle1 != 0 && refBasicPath1 == null)
            throw new IllegalArgumentException("refBasicPath1 must be set when refSynStyle1 != 0");
        if (refSynStyle2 != 0 && refBasicPath2 == null)
            throw new IllegalArgumentException("refBasicPath2 must be set when refSynStyle2 != 0");
    }

   @Override
   public void run() throws IOException {
       dateStr = GadgetAid.getTemporaryString();

       // read main basic waveform folders and write waveforms to be used into txt files
       List<BasicID> mainBasicIDs = BasicIDFile.read(mainBasicPath, true).stream()
               .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());
       if (!tendEvents.isEmpty()) {
           mainBasicIDs = mainBasicIDs.stream().filter(id -> tendEvents.contains(id.getGlobalCMTID())).collect(Collectors.toList());
       }
       BasicIDFile.outputWaveformTxts(mainBasicIDs, mainBasicPath);

       // collect events included in mainBasicIDs
       Set<GlobalCMTID> events = mainBasicIDs.stream().map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
       if (!DatasetAid.checkNum(events.size(), "event", "events")) {
           return;
       }

       // read reference basic waveform folders and write waveforms to be used into txt files
       List<BasicID> refBasicIDs1 = null;
       if (refBasicPath1 != null) {
           refBasicIDs1 = BasicIDFile.read(refBasicPath1, true).stream()
                   .filter(id -> components.contains(id.getSacComponent()) && events.contains(id.getGlobalCMTID()))
                   .collect(Collectors.toList());
           BasicIDFile.outputWaveformTxts(refBasicIDs1, refBasicPath1);
       }
       List<BasicID> refBasicIDs2 = null;
       if (refBasicPath2 != null) {
           refBasicIDs2 = BasicIDFile.read(refBasicPath2, true).stream()
                   .filter(id -> components.contains(id.getSacComponent()) && events.contains(id.getGlobalCMTID()))
                   .collect(Collectors.toList());
           BasicIDFile.outputWaveformTxts(refBasicIDs2, refBasicPath2);
       }

       try {
           // set up taup_time tool
           if (alignPhases != null || displayPhases != null) {
               timeTool = new TauP_Time(structureName);
           }

           for (GlobalCMTID event : events) {

               // set event to taup_time tool
               // The same instance is reused for all observers because computation takes time when changing source depth (see TauP manual).
               if (alignPhases != null || displayPhases != null) {
                   timeTool.setSourceDepth(event.getEventData().getCmtPosition().getDepth());
               }

               // create plots under workPath
               Path eventPath = workPath.resolve(event.toString());
               Files.createDirectories(eventPath);
               for (SACComponent component : components) {
                   List<BasicID> useIds = mainBasicIDs.stream()
                           .filter(id -> id.getSacComponent().equals(component) && id.getGlobalCMTID().equals(event))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .collect(Collectors.toList());

                   Plotter plotter = new Plotter(eventPath, useIds, component);
                   plotter.plot();
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

    private class Plotter {
        private final Path eventPath;
        private final List<BasicID> ids;
        private final SACComponent component;

        private GnuplotFile gnuplot;
        private double obsMeanMax;
        private double synMeanMax;
        private boolean firstPlot = true;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(Path eventPath, List<BasicID> ids, SACComponent component) {
            this.eventPath = eventPath;
            this.ids = ids;
            this.component = component;
        }

        private void plot() throws IOException, TauModelException {
            if (ids.size() == 0) {
                return;
            }

            // prepare IDs
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

                // compute reduce time by distance or phase travel time
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
            gnuplot.setCommonYrange(startDistance, endDistance);
            gnuplot.setCommonXrange(minTime - TIME_RIM, maxTime + TIME_RIM);

            // add travel time curves
            if (displayPhases != null) {
                if (anisotimeMode) {
                    plotTravelTimeCurveAnisotime(startDistance, endDistance);
                } else {
                    plotTravelTimeCurve(startDistance, endDistance);
                }
            }

            // plot
            gnuplot.write();
            if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
        }

        private void profilePlotSetup() {
            // Here, generateOutputFileName() is used in an irregular way, without adding the file extension but adding the component.
            String fileNameRoot = DatasetAid.generateOutputFileName("recordSection", fileTag, dateStr, "_" + component.toString());

            gnuplot = new GnuplotFile(eventPath.resolve(fileNameRoot + ".plt"));
            gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
            gnuplot.setMarginH(15, 25);
            gnuplot.setMarginV(15, 15);
            gnuplot.setFont("Arial", 20, 15, 15, 15, 10);
            gnuplot.setCommonKey(true, false, "top right");

            gnuplot.setCommonTitle(eventPath.getFileName().toString());
            if (alignPhases != null) {
                gnuplot.setCommonXlabel("Time aligned on " + String.join(",", alignPhases) + "-wave arrival (s)");
            } else {
                gnuplot.setCommonXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
            }
            if (!byAzimuth) {
                gnuplot.setCommonYlabel("Distance (deg)");
                gnuplot.addLabel("station network azimuth", "graph", 1.0, 1.0);
            } else {
                gnuplot.setCommonYlabel("Azimuth (deg)");
                gnuplot.addLabel("station network distance", "graph", 1.0, 1.0);
            }
        }

        private void profilePlotContent(BasicID obsID, BasicID synID, double distance, double azimuth, double reduceTime)
                throws IOException, TauModelException {
            String txtFileName = BasicIDFile.getWaveformTxtFileName(obsID);

            // decide the coefficient to amplify each waveform
            RealVector obsDataVector = new ArrayRealVector(obsID.getData());
            RealVector synDataVector = new ArrayRealVector(synID.getData());
            double obsMax = obsDataVector.getLInfNorm();
            double synMax = synDataVector.getLInfNorm();
            double obsAmp = BasicPlotAid.selectAmp(obsAmpStyle, ampScale, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = BasicPlotAid.selectAmp(synAmpStyle, ampScale, obsMax, synMax, obsMeanMax, synMeanMax);

            // Set "using" part. For x values, reduce time by distance or phase travel time. For y values, add either distance or azimuth.
            String shiftedUsingString;
            String unshiftedUsingString;
            String synUsingString;
            if (!byAzimuth) {
                gnuplot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(azimuth, 3, 2, false),
                        "graph", 1.01, "first", distance);
                unshiftedUsingString = String.format("($1-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, distance);
                shiftedUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, distance);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, distance);
            } else {
                gnuplot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(distance, 3, 2, false),
                        "graph", 1.01, "first", azimuth);
                unshiftedUsingString = String.format("($1-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, azimuth);
                shiftedUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, azimuth);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, azimuth);
            }

            // plot waveforms
            // Absolute paths are used here because relative paths are hard to construct when workPath != mainBasicPath.
            String eventName = eventPath.getFileName().toString();
            Path mainFilePath = mainBasicPath.toAbsolutePath().resolve(eventName).resolve(txtFileName);
            if (unshiftedObsStyle != 0)
                gnuplot.addLine(mainFilePath.toString(), unshiftedUsingString, BasicPlotAid.switchObservedAppearance(unshiftedObsStyle),
                        (firstPlot ? unshiftedObsName : ""));
            if (shiftedObsStyle != 0)
                gnuplot.addLine(mainFilePath.toString(), shiftedUsingString, BasicPlotAid.switchObservedAppearance(shiftedObsStyle),
                        (firstPlot ? shiftedObsName : ""));
            if (mainSynStyle != 0)
                gnuplot.addLine(mainFilePath.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(mainSynStyle),
                        (firstPlot ? mainSynName : ""));
            if (refSynStyle1 != 0) {
                Path refFilePath1 = refBasicPath1.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath1.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(refSynStyle1),
                        (firstPlot ? refSynName1 : ""));
            }
            if (refSynStyle2 != 0) {
                Path refFilePath2 = refBasicPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath2.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(refSynStyle2),
                        (firstPlot ? refSynName2 : ""));
            }
            firstPlot = false;
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
                String curveFileName = DatasetAid.generateOutputFileName("curve", fileTag, dateStr, "_" + component + "_" + phase + ".txt");
                Path curvePath = eventPath.resolve(curveFileName);
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
                                    gnuplot.addLabel(phase, "first", travelTimes[p][i] - alignTimes[i], distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            } else {
                                double reduceTime = reductionSlowness * distance;
                                pw.println(distance + " " + (travelTimes[p][i] - reduceTime));
                                // add label at first appearance
                                if (wrotePhaseLabel == false) {
                                    gnuplot.addLabel(phase, "first", travelTimes[p][i] - reduceTime, distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            }
                        }
                    }
                }
                gnuplot.addLine(curveFileName, 2, 1, BasicPlotAid.USE_PHASE_APPEARANCE, "");
            }
        }

        private void plotTravelTimeCurveAnisotime(double startDistance, double endDistance) throws TauModelException {

        }
    }

}
