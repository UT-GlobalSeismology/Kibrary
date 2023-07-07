package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
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
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 *
 * Plots waveform data from a root folder including synthetic waveform data.
 * For each event, a pdf file with waveforms for all observers will be created.
 * In each plot, the main synthetic waveform, the reference synthetic waveform, and the residual waveform can be plotted.
 * Vertical lines of travel times can be displayed from {@link TravelTimeInformationFile} or the culculation of TauP.
 * <p>
 * Text files of waveform data will be created in event folders under their the input root folders.
 * Output pdf files and their corresponding plt files will be created in event directories under workPath.
 *
 * @author rei
 * @since 2023/07/07
 */
public class SyntheticWaveformPlotter extends Operation {

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
     * If this is true, a time stamp is included in output folder name.
     */
    private boolean timeStamp;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;
    /**
     * sampling Hz of SAC file （skip SAC of which sampling Hz is different form this one）
     */
    private double sacSamplingHz;

    /**
     * Path of a root folder containing synthetic dataset
     */
    private Path mainSynPath;
    /**
     * Path of a reference root folder1 containing synthetic dataset
     */
    private Path refSynPath1;
    /**
     * Path of a reference root folder2 containing synthetic dataset
     */
    private Path refSynPath2;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private BasicPlotAid.AmpStyle ampStyle;
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
     * Name of structure to compute travel times
     */
    private String structureName;

    private double lowerTime;
    private double upperTime;
    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    private int mainSynStyle;
    private String mainSynName;
    private int refSynStyle1;
    private String refSynName1;
    private int residualStyle1;
    private String residualName1;
    private int refSynStyle2;
    private String refSynName2;
    private int residualStyle2;
    private String residualName2;

    /**
     * Inxtance of tool to use to compute travel times
     */
    private TauP_Time timeTool;
    private String dateStr;

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
            pw.println("##(boolean) Whether to include time stamp in output folder name (true)");
            pw.println("#timeStamp false");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(double) Value of sac sampling Hz (20) can't be changed now");
            pw.println("#sacSamplingHz the value will be ignored");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#mainSynPath ");
            pw.println("##Path of a reference root folder 1 containing synthetic dataset, when plotting their waveforms");
            pw.println("#refSynPath1 ");
            pw.println("##Path of a reference root folder 2 containing synthetic dataset, when plotting their waveforms");
            pw.println("#refSynPath2 ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##Method for standarization of synthetic waveform amplitude, from {synEach,synMean} (synEach)");
            pw.println("#ampStyle ");
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
            pw.println("##(double) Lower limit of range of time to be used [sec]. To use all time, leave this unset");
            pw.println("#lowerTime ");
            pw.println("##(double) Upper limit of range of time to be used [sec]. To use all time, leave this unset");
            pw.println("#upperTime ");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to be used [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to be used [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to be used [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth ");
            pw.println("##Plot style for main synthetic waveform, from {0:no plot, 1:red, 2:green, 3:blue} (1)");
            pw.println("#mainSynStyle 2");
            pw.println("##Name for main synthetic waveform (synthetic)");
            pw.println("#mainSynName recovered");
            pw.println("##Plot style for reference synthetic waveform 1, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle1 1");
            pw.println("##Name for reference synthetic waveform 1 (reference1)");
            pw.println("#refSynName1 ");
            pw.println("##Plot style for residual waveform between main and reference1, from {0:no plot, 1:sky blue} (0)");
            pw.println("#residualStyle1 1");
            pw.println("##Name for residual1 waveform (residual1)");
            pw.println("#residualName1 ");
            pw.println("##Plot style for reference synthetic waveform 2, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle2 ");
            pw.println("##Name for reference synthetic waveform 2 (reference2)");
            pw.println("#refSynName2 ");
            pw.println("##Plot style for residual waveform between main and reference2, from {0:no plot, 1:sky blue} (0)");
            pw.println("#residualStyle2 1");
            pw.println("##Name for residual2 waveform (residual2)");
            pw.println("#residualName2 ");
        }
        System.err.println(outPath + " is created.");
    }

    public SyntheticWaveformPlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        timeStamp = property.parseBoolean("timeStamp", "true");
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        sacSamplingHz = 20;  // TODO property.parseDouble("sacSamplingHz", "20");

        mainSynPath = property.parsePath("mainSynPath", ".", true, workPath);
        if (property.containsKey("refSynPath1"))
            refSynPath1 = property.parsePath("refSynPath1", ".", true, workPath);
        if (property.containsKey("refSynPath2"))
            refSynPath2 = property.parsePath("refSynPath2", ".", true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        ampStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");

        if (property.containsKey("displayPhases") && byAzimuth == false)
            displayPhases = property.parseStringArray("displayPhases", null);
        if (property.containsKey("alignPhases"))
            alignPhases = property.parseStringArray("alignPhases", null);
        reductionSlowness = property.parseDouble("reductionSlowness", "0");
        structureName = property.parseString("structureName", "prem").toLowerCase();

        lowerTime = property.parseDouble("lowerTime", "NaN");
        upperTime = property.parseDouble("upperTime", "NaN");

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");

        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");

        mainSynStyle = property.parseInt("mainSynStyle", "1");
        mainSynName = property.parseString("mainSynName", "synthetic");
        refSynStyle1 = property.parseInt("refSynStyle1", "0");
        refSynName1 = property.parseString("refSynName1", "reference1");
        residualStyle1 = property.parseInt("residualStyle1", "0");
        residualName1 = property.parseString("residualName1", "residual1");
        refSynStyle2 = property.parseInt("refSynStyle2", "0");
        refSynName2 = property.parseString("refSynName2", "reference2");
        residualStyle2 = property.parseInt("residualStyle2", "0");
        residualName2 = property.parseString("residualName2", "residual2");
        if (refSynStyle1 != 0 || residualStyle1 != 0 && refSynPath1 == null)
            throw new IllegalArgumentException("refBasicPath1 must be set when refSynStyle1 != 0");
        if (refSynStyle2 != 0 || residualStyle2 != 0&& refSynPath2 == null)
            throw new IllegalArgumentException("refBasicPath2 must be set when refSynStyle2 != 0");
    }

   @Override
   public void run() throws IOException {
       if (timeStamp) dateStr = GadgetAid.getTemporaryString();
       else dateStr = null;

       // read main synthetic dataset and write waveforms to be used into txt files
       Set<EventFolder> mainEventDirs = DatasetAid.eventFolderSet(mainSynPath);
       if (!tendEvents.isEmpty())
           mainEventDirs = mainEventDirs.stream().filter(dirs -> tendEvents.contains(dirs.getGlobalCMTID())).collect(Collectors.toSet());
       SACFileAccess.outputSacFileTxt(mainEventDirs);

       Set<GlobalCMTID> events = new HashSet<>();
       mainEventDirs.forEach(dirs -> events.add(dirs.getGlobalCMTID()));

       // read reference synthetic dataset and write waveforms to be used into txt files
       Set<EventFolder> refEventDirs1 = null;
       if (refSynPath1 != null) {
           refEventDirs1 = DatasetAid.eventFolderSet(refSynPath1);
           if (!tendEvents.isEmpty())
               refEventDirs1 = refEventDirs1.stream().filter(dirs -> tendEvents.contains(dirs.getGlobalCMTID())).collect(Collectors.toSet());
           // check the event directories are same as mainSynPath
           Set<GlobalCMTID> refEvents1 = new HashSet<>();
           refEventDirs1.forEach(dirs -> refEvents1.add(dirs.getGlobalCMTID()));
           if (!refEvents1.equals(events))
               throw new IllegalArgumentException("The number of event directories in mainSynPath and in refSynPath1 is different");
           // output text file
           SACFileAccess.outputSacFileTxt(refEventDirs1);
       }
       Set<EventFolder> refEventDirs2 = null;
       if (refSynPath2 != null) {
           refEventDirs2 = DatasetAid.eventFolderSet(refSynPath2);
           if (!tendEvents.isEmpty())
               refEventDirs2 = refEventDirs2.stream().filter(dirs -> tendEvents.contains(dirs.getGlobalCMTID())).collect(Collectors.toSet());
           // check the event directories are same as mainSynPath
           Set<GlobalCMTID> refEvents2 = new HashSet<>();
           refEventDirs2.forEach(dirs -> refEvents2.add(dirs.getGlobalCMTID()));
           if (!refEvents2.equals(events))
               throw new IllegalArgumentException("The number of event directories in mainSynPath and in refSynPath2 is different");
           // output text file
           SACFileAccess.outputSacFileTxt(refEventDirs2);
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
                   Set<SACFileName> sacNames = new EventFolder(mainSynPath.resolve(event.toString())).sacFileSet();
//                   List<BasicID> useIds = mainBasicIDs.stream()
//                           .filter(id -> id.getSacComponent().equals(component) && id.getGlobalCMTID().equals(event))
//                           .sorted(Comparator.comparing(BasicID::getObserver))
//                           .collect(Collectors.toList());
                   sacNames = sacNames.stream().filter(name -> name.getComponent().equals(component)).collect(Collectors.toSet());

                   Plotter plotter = new Plotter(eventPath, sacNames, component);
                   plotter.plot();
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

    private class Plotter {
        private final Path eventPath;
        private final Set<SACFileName> sacNames;
        private final SACComponent component;

        private GnuplotFile gnuplot;
        private double synMeanMax;
        private boolean firstPlot = true;

        /**
         *
         * @param eventPath
         * @param sacNames (Set<SACFileName>)
         * @param component
         */
        private Plotter(Path eventPath, Set<SACFileName> sacNames, SACComponent component) {
            this.eventPath = eventPath;
            this.sacNames = sacNames;
            this.component = component;
        }

        private void plot() throws IOException, TauModelException {
            if (sacNames.size() == 0) {
                return;
            }

            // set up plotter
            profilePlotSetup();

            // calculate the average of the maximum amplitudes of waveforms
            Set<SACFileAccess> sacDataSet = new HashSet<>();
            sacNames.forEach(name -> {
                try {
                    sacDataSet.add(name.read());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            synMeanMax = sacDataSet.stream().collect(Collectors.averagingDouble(data -> new ArrayRealVector(data.getData()).getLInfNorm()));

            // variables to find the minimum and maximum distance for this event
            double minTime = Double.MAX_VALUE;
            double maxTime = -Double.MAX_VALUE;
            double minDistance = Double.MAX_VALUE;
            double maxDistance = -Double.MAX_VALUE;

            // for each SAC file
            for (SACFileName sacName : sacNames) {

                SACFileAccess sacData = sacName.read();
                double distance = sacData.getValue(SACHeaderEnum.GCARC);
                double azimuth = sacData.getValue(SACHeaderEnum.AZ);

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
                        System.err.println("Could not get arrival time of " + String.join(",", alignPhases) + " for " + sacName.toString() + " , skipping.");
                        return;
                    }
                    reduceTime = timeTool.getArrival(0).getTime();
                } else {
                    reduceTime = reductionSlowness * distance;
                }

                // update ranges
                double delta = sacData.getValue(SACHeaderEnum.DELTA);
                int npts = sacData.getInt(SACHeaderEnum.NPTS);
                double b = (int) (sacData.getValue(SACHeaderEnum.B) / delta) * delta;
                double startTime = b - reduceTime;
                double endTime = b + npts * delta - reduceTime;
                if (startTime < minTime) minTime = startTime;
                if (endTime > maxTime) maxTime = endTime;
                if (distance < minDistance) minDistance = distance;
                if (distance > maxDistance) maxDistance = distance;

                // in flipAzimuth mode, change azimuth range from [0:360) to [-180:180)
                if (flipAzimuth == true && 180 <= azimuth) {
                    profilePlotContent(sacName, distance, azimuth - 360, reduceTime);
                } else {
                    profilePlotContent(sacName, distance, azimuth, reduceTime);
                }
            }

            // set ranges
            if (!Double.isNaN(lowerTime)) minTime = lowerTime;
            if (!Double.isNaN(upperTime)) maxTime = upperTime;
            if (minDistance > maxDistance || minTime > maxTime) return;
            int startDistance = (int) Math.floor(minDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL - Y_AXIS_RIM;
            int endDistance = (int) Math.ceil(maxDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL + Y_AXIS_RIM;
            gnuplot.setCommonYrange(startDistance, endDistance);
            gnuplot.setCommonXrange(minTime - TIME_RIM, maxTime + TIME_RIM);

            // add travel time curves
            if (displayPhases != null) {
                plotTravelTimeCurve(startDistance, endDistance);
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

        private void profilePlotContent(SACFileName sacName, double distance, double azimuth, double reduceTime)
                throws IOException, TauModelException {
            String txtFileName = sacName.toPath().getFileName().toString() + ".txt";
            SACFileAccess sacData = sacName.read();

            // decide the coefficient to amplify each waveform
            RealVector synDataVector = new ArrayRealVector(sacData.getData());
            double synMax = synDataVector.getLInfNorm();
            double synAmp = BasicPlotAid.selectAmp(ampStyle, ampScale, synMax, synMax, synMeanMax, synMeanMax);

            // Set "using" part. For x values, reduce time by distance or phase travel time. For y values, add either distance or azimuth.
            String synUsingString;
            String residualUsingString;
            if (!byAzimuth) {
                gnuplot.addLabel(sacData.getObserver().toPaddedString() + " " + MathAid.padToString(azimuth, 3, 2, " "),
                        "graph", 1.01, "first", distance);
                synUsingString = String.format("($1-%.3f):($2/%.3e+%.2f) ", reduceTime, synAmp, distance);
                residualUsingString = String.format("($1-%.3f):(($2-$4)/%.3e+%.2f) ", reduceTime, synAmp, distance);
            } else {
                gnuplot.addLabel(sacData.getObserver().toPaddedString() + " " + MathAid.padToString(distance, 3, 2, " "),
                        "graph", 1.01, "first", azimuth);
                synUsingString = String.format("($1-%.3f):($2/%.3e+%.2f) ", reduceTime, synAmp, azimuth);
                residualUsingString = String.format("($1-%.3f):(($2-$4)/%.3e+%.2f) ", reduceTime, synAmp, azimuth);
            }

            // plot waveforms
            // Absolute paths are used here because relative paths are hard to construct when workPath != mainBasicPath.
            String eventName = eventPath.getFileName().toString();
            Path mainFilePath = mainSynPath.toAbsolutePath().resolve(eventName).resolve(txtFileName);
            if (mainSynStyle != 0)
                gnuplot.addLine(mainFilePath.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(mainSynStyle),
                        (firstPlot ? mainSynName : ""));
            if (refSynStyle1 != 0) {
                Path refFilePath1 = refSynPath1.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath1.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(refSynStyle1),
                        (firstPlot ? refSynName1 : ""));
            }
            if (residualStyle1 != 0) {
                Path refFilePath1 = refSynPath1.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath1.toString(), mainFilePath.toString(), residualUsingString,
                        BasicPlotAid.switchResidualAppearance(residualStyle1), (firstPlot ? residualName1 : ""));
            }
            if (refSynStyle2 != 0) {
                Path refFilePath2 = refSynPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath2.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(refSynStyle2),
                        (firstPlot ? refSynName2 : ""));
            }
            if (residualStyle2 != 0) {
                Path refFilePath2 = refSynPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
                gnuplot.addLine(refFilePath2.toString(), mainFilePath.toString(), residualUsingString,
                        BasicPlotAid.switchResidualAppearance(residualStyle2), (firstPlot ? residualName2 : ""));
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
    }
}
