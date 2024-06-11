package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Creates a record section for each event included in dataset folders.
 * Waveforms can be aligned on a specific phase or by a certain reduction slowness.
 * Travel time curves can be drawn on the graph.
 *
 *
 * @author otsuru
 * @since 2024/6/11 Created based on SacRecordSectionCreator in syozemi branch.
 */
public class SyntheticRecordSection extends Operation {

    /**
     * The interval of deciding graph size; should be a multiple of TRAVEL_TIME_INTERVAL.
     */
    private static final int GRAPH_SIZE_INTERVAL = 2;
    /**
     * How much space to provide at the rim of the graph in the y axis.
     */
    private static final int Y_AXIS_RIM = 2;
    /**
     * How much space to provide at the rim of the graph in the time axis.
     */
    private static final int TIME_RIM = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

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
     * Whether to plot the figure with azimuth as the Y-axis.
     */
    private boolean byAzimuth;
    /**
     * Whether to set the azimuth range to [-180:180) instead of [0:360).
     */
    private boolean flipAzimuth;
    /**
     * Names of phases to plot travel time curves.
     */
    private String[] displayPhases;
    /**
     * Names of phases to use to align the record section. The fastest of these arrivals is used.
     */
    private String[] alignPhases;
    /**
     * Apparent slowness to use when reducing time [s/deg].
     */
    private double reductionSlowness;
    /**
     * Name of structure to compute travel times.
     */
    private String structureName;

    private double lowerTime;
    private double upperTime;
    private LinearRange distanceRange;
    private CircularRange azimuthRange;

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
     * Instance of tool to use to compute travel times.
     */
    private TauP_Time timeTool;
    private String dateString;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a root folder containing synthetic dataset (.)");
            pw.println("#mainSynPath ");
            pw.println("##Path of a reference root folder 1 containing synthetic dataset, when plotting their waveforms");
            pw.println("#refSynPath1 ");
            pw.println("##Path of a reference root folder 2 containing synthetic dataset, when plotting their waveforms");
            pw.println("#refSynPath2 ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##Method for standarization of synthetic waveform amplitude, from {synEach,synMean}. (synEach)");
            pw.println("#ampStyle ");
            pw.println("##(double) Coefficient to multiply to all waveforms. (1.0)");
            pw.println("#ampScale ");
            pw.println("##(boolean) Whether to plot the figure with azimuth as the Y-axis. (false)");
            pw.println("#byAzimuth ");
            pw.println("##(boolean) Whether to set the azimuth range to [-180:180) instead of [0:360). (false)");
            pw.println("##  This is effective when using south-to-north raypaths in byAzimuth mode.");
            pw.println("#flipAzimuth ");
            pw.println("##Names of phases to plot travel time curves, listed using spaces. Only when byAzimuth is false.");
            pw.println("#displayPhases ");
            pw.println("##Names of phases to use for alignment, listed using spaces. When unset, the following reductionSlowness will be used.");
            pw.println("##  When multiple phases are set, the fastest arrival of them will be used for alignment.");
            pw.println("#alignPhases ");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg]. (0)");
            pw.println("#reductionSlowness ");
            pw.println("##(String) Name of structure to compute travel times using TauP. (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Lower limit of time range to plot [sec]. To use whole range, leave this unset.");
            pw.println("#lowerTime ");
            pw.println("##(double) Upper limit of time range to plot [sec]. To use whole range, leave this unset.");
            pw.println("#upperTime ");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [deg], inclusive; [0:upperDistance). (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to be used [deg], exclusive; (lowerDistance:180]. (180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to be used [deg], inclusive; [-180:360]. (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to be used [deg], exclusive; [-180:360]. (360)");
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

    public SyntheticRecordSection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        mainSynPath = property.parsePath("mainSynPath", ".", true, workPath);
        if (property.containsKey("refSynPath1"))
            refSynPath1 = property.parsePath("refSynPath1", ".", true, workPath);
        if (property.containsKey("refSynPath2"))
            refSynPath2 = property.parsePath("refSynPath2", ".", true, workPath);

        ampStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("ampStyle", "synEach"));
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

        double lowerDistance = property.parseDouble("lowerDistance", "0");
        double upperDistance = property.parseDouble("upperDistance", "180");
        distanceRange = new LinearRange("Distance", lowerDistance, upperDistance, 0.0, 180.0);

        double lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        double upperAzimuth = property.parseDouble("upperAzimuth", "360");
        azimuthRange = new CircularRange("Azimuth", lowerAzimuth, upperAzimuth, -180.0, 360.0);

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
        if ((refSynStyle1 != 0 || residualStyle1 != 0) && refSynPath1 == null)
            throw new IllegalArgumentException("refSynPath1 must be set when refSynStyle1 != 0");
        if ((refSynStyle2 != 0 || residualStyle2 != 0) && refSynPath2 == null)
            throw new IllegalArgumentException("refSynPath2 must be set when refSynStyle2 != 0");
    }

   @Override
   public void run() throws IOException {
       dateString = GadgetAid.getTemporaryString();

       // read main synthetic dataset and write waveforms to be used into txt files
       Set<EventFolder> mainEventDirs = DatasetAid.eventFolderSet(mainSynPath);
       if (!tendEvents.isEmpty())
           mainEventDirs = mainEventDirs.stream().filter(dirs -> tendEvents.contains(dirs.getGlobalCMTID())).collect(Collectors.toSet());
       SACUtil.outputSacFileTxt(mainEventDirs);

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
           SACUtil.outputSacFileTxt(refEventDirs1);
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
           SACUtil.outputSacFileTxt(refEventDirs2);
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
                   sacNames = sacNames.stream().filter(name -> name.isSYN() && name.getComponent().equals(component)).collect(Collectors.toSet());

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
       private double synMeanMax = 0;
       private boolean firstPlot = true;

       /**
        *
        * @param eventPath
        * @param sacNames (SACFileName) sac files to be plotted
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
           if (ampStyle.equals(BasicPlotAid.AmpStyle.synMean)) calculateSynMeanMax(sacNames);

           // variables to find the minimum and maximum distance for this event
           double minTime = Double.isNaN(lowerTime) ?  Double.MAX_VALUE : lowerTime;
           double maxTime = Double.isNaN(upperTime) ? -Double.MAX_VALUE : upperTime;
           double minDistance = Double.MAX_VALUE;
           double maxDistance = -Double.MAX_VALUE;

           // for each SAC file
           for (SACFileName sacName : sacNames) {

               SACFileAccess sacData = sacName.read();
               double distance = sacData.getValue(SACHeaderEnum.GCARC);
               double azimuth = sacData.getValue(SACHeaderEnum.AZ);

               // skip waveform if distance or azimuth is out of bounds
               if (distanceRange.check(distance) == false || azimuthRange.check(azimuth) == false) {
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
               if (Double.isNaN(lowerTime) && (startTime < minTime)) minTime = startTime;
               if (Double.isNaN(upperTime) && (endTime > maxTime)) maxTime = endTime;
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
           if (minDistance > maxDistance || minTime > maxTime) return;
           int startDistance = (int) Math.floor(minDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL - Y_AXIS_RIM;
           int endDistance = (int) Math.ceil(maxDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL + Y_AXIS_RIM;
           gnuplot.setCommonYrange(startDistance, endDistance);
           gnuplot.setCommonXrange(minTime - TIME_RIM, maxTime + TIME_RIM);

           // add travel time curves
           if (displayPhases != null) {
               BasicPlotAid.plotTravelTimeCurve(timeTool, displayPhases, alignPhases, reductionSlowness, startDistance, endDistance,
                       fileTag, dateString, eventPath, component, gnuplot);
           }

           // plot
           gnuplot.write();
           if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
       }

       private void profilePlotSetup() {
           // Here, generateOutputFilePath() is used in an irregular way, adding the component along with the file extension.
           Path plotPath = DatasetAid.generateOutputFilePath(eventPath, "recordSection", fileTag, true, dateString, "_" + component.toString() + ".plt");

           gnuplot = new GnuplotFile(plotPath);
           gnuplot.setOutput("pdf", plotPath.getFileName().toString().replace(".plt", ".pdf"), 21, 29.7, true);
           gnuplot.setMarginH(15, 25);
           gnuplot.setMarginV(15, 15);
           gnuplot.setFont("Arial", 20, 15, 15, 15, 10);
           gnuplot.setCommonKey(true, false, "top right");

           gnuplot.setCommonTitle(eventPath.getFileName().toString());
           if (alignPhases != null) {
               gnuplot.setCommonXlabel("Time aligned on " + String.join(",", alignPhases) + "-phase arrival (s)");
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
           double synAmp;
           if (ampStyle.equals(BasicPlotAid.AmpStyle.synMean)) synAmp = synMeanMax / ampScale;
           else if (ampStyle.equals(BasicPlotAid.AmpStyle.synEach)) synAmp = calculateSynMax(sacData, reduceTime);
           else throw new IllegalArgumentException("ampStyle must be set synEach or synMean.");

           // Set "using" part. For x values, reduce time by distance or phase travel time. For y values, add either distance or azimuth.
           String synUsingString;
           String residualUsingString;
           if (!byAzimuth) {
               gnuplot.addLabel(sacData.getObserver().toPaddedString() + " " + MathAid.padToString(azimuth, 3, 2, false),
                       "graph", 1.01, "first", distance);
               synUsingString = String.format("($1-%.3f):($2/%.3e+%.2f) ", reduceTime, synAmp, distance);
               residualUsingString = String.format("($1-%.3f):(($2-$4)/%.3e+%.2f) ", reduceTime, synAmp, distance);
           } else {
               gnuplot.addLabel(sacData.getObserver().toPaddedString() + " " + MathAid.padToString(distance, 3, 2, false),
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
           if (refSynStyle2 != 0) {
               Path refFilePath2 = refSynPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
               gnuplot.addLine(refFilePath2.toString(), synUsingString, BasicPlotAid.switchSyntheticAppearance(refSynStyle2),
                       (firstPlot ? refSynName2 : ""));
           }
           firstPlot = false;
       }

       private void calculateSynMeanMax(Set<SACFileName> names) throws IOException, TauModelException {
           for (SACFileName name : names) {
               SACFileAccess data = name.read();
               double distance = data.getValue(SACHeaderEnum.GCARC);
               double rdTime = 0;
               if (alignPhases != null) {
                   timeTool.setPhaseNames(alignPhases);
                   timeTool.calculate(distance);
                   if (timeTool.getNumArrivals() < 1) {
                       System.err.println("Could not get arrival time of " + String.join(",", alignPhases) + " for " + name.toString() + " , skipping.");
                       return;
                   }
                   rdTime = timeTool.getArrival(0).getTime();
               } else {
                   rdTime = reductionSlowness * distance;
               }
               Trace trace = data.createTrace();
               double startTime = (Double.isNaN(lowerTime)) ? trace.getMinX() : lowerTime + rdTime;
               double endTime = (Double.isNaN(upperTime)) ? trace.getMaxX() : upperTime + rdTime;
               trace = trace.cutWindow(startTime, endTime);
               synMeanMax = synMeanMax + trace.getYVector().getLInfNorm();
           }
           synMeanMax = synMeanMax / names.size();
       }

       private double calculateSynMax(SACFileAccess data, double rdTime) {
           Trace trace = data.createTrace();
           double startTime = (Double.isNaN(lowerTime)) ? trace.getMinX() : lowerTime + rdTime;
           double endTime = (Double.isNaN(upperTime)) ? trace.getMaxX() : upperTime + rdTime;
           trace = trace.cutWindow(startTime, endTime);
           double max = trace.getYVector().getLInfNorm();
           return max / ampScale;
       }
   }

}
