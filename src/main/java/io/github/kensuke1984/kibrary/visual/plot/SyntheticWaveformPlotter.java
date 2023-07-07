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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformation;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
     * Number of fields per page on output pdf file
     */
    private static final int NUM_PER_PAGE = 12;
    /**
     * The time margin in the plot before the start time of the synthetic waveform.
     */
    private static final double FRONT_MARGIN = 10;

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
     * Path of a travel time information file
     */
    private Path travelTimePath;
    /**
     * Names of phases to plot travel time curves
     */
    private String[] displayPhases;
    /**
     * Name of structure to compute travel times
     */
    private String structureName;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Whether to export individual files for each component
     */
    private boolean splitComponents;
    /**
     * The time length to plot
     */
    private double timeLength;

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
     * Set of information of travel times
     */
    private Set<TravelTimeInformation> travelTimeInfoSet;
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
            pw.println("##Path of a travel time information file, if plotting travel times");
            pw.println("#travelTimePath travelTime.inf");
            pw.println("##Names of phases to plot travel time curves, listed using spaces. Only when byAzimuth is false.");
            pw.println("#displayPhases ");
            pw.println("##(String) Name of structure to compute travel times using TauP (prem)");
            pw.println("#structureName ");
            pw.println("##(boolean) Whether to export individual files for each component (true)");
            pw.println("#splitComponents ");
            pw.println("##(double) Time length of each plot [s] (150)");
            pw.println("#timeLength ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
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

        if (property.containsKey("travelTimePath")) {
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);
        }
        else if (property.containsKey("displayPhases")) {
            displayPhases = property.parseStringArray("displayPhases", null);
            structureName = property.parseString("structureName", "prem").toLowerCase();
        }

        splitComponents = property.parseBoolean("splitComponents", "true");
        timeLength = property.parseDouble("timeLength", "150");
        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

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
           // set up to plot travel times
           // read travel time information
           if (travelTimePath != null) {
               travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
           }
           // set up taup_time tool
           else if (displayPhases != null) {
               timeTool = new TauP_Time(structureName);
           }

           for (GlobalCMTID event : events) {

               // set event to taup_time tool
               // The same instance is reused for all observers because computation takes time when changing source depth (see TauP manual).
               if (displayPhases != null) {
                   timeTool.setSourceDepth(event.getEventData().getCmtPosition().getDepth());
               }

               // create plots under workPath
               Path eventPath = workPath.resolve(event.toString());
               Files.createDirectories(eventPath);
               if (splitComponents) {
                   for (SACComponent component : components) {
                       Set<SACFileName> sacNames = new EventFolder(mainSynPath.resolve(event.toString())).sacFileSet();
                       sacNames = sacNames.stream().filter(name -> name.getComponent().equals(component)).collect(Collectors.toSet());

                       // Here, generateOutputFileName() is used in an irregular way, without adding the file extension but adding the component.
                       String fileNameRoot = DatasetAid.generateOutputFileName("plot", fileTag, dateStr, "_" + component.toString());
                       createPlot(eventPath, sacNames, fileNameRoot);
                   }
               }
               else {
                   Set<SACFileName> sacNames = new EventFolder(mainSynPath.resolve(event.toString())).sacFileSet();

                   // Here, generateOutputFileName() is used in an irregular way, without adding the file extension.
                   String fileNameRoot = DatasetAid.generateOutputFileName("plot", fileTag, dateStr, "");
                   createPlot(eventPath, sacNames, fileNameRoot);
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

   /**
    * @param eventDir (EventFolder)
    * @param sacNames (SACFileName) sac files to be plotted
    * @param fileNameRoot (String) The root of file names of output plot and graph files
    * @throws IOException
    * @throws TauModelException
    */
   private void createPlot(Path eventPath, Set<SACFileName> sacNames, String fileNameRoot) throws IOException, TauModelException {
       if (sacNames.size() == 0) {
           return;
       }

       GnuplotFile gnuplot = new GnuplotFile(eventPath.resolve(fileNameRoot + ".plt"));

       gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
       gnuplot.setMarginH(15, 5);
       gnuplot.setFont("Arial", 10, 8, 8, 8, 8);
       gnuplot.setCommonKey(true, false, "top right");

       int i = 0;
       for (SACFileName sacName : sacNames) {
           SACFileAccess sacData = sacName.read();
           String txtFileName = sacName.toPath().getFileName().toString() + ".txt";

           double delta = sacData.getValue(SACHeaderEnum.DELTA);
           int npts = sacData.getInt(SACHeaderEnum.NPTS);
           double b = (int) (sacData.getValue(SACHeaderEnum.B) / delta) * delta;
           double startTime = b;
           double endTime = b + npts * delta;

           // set xrange
           gnuplot.setXrange(startTime - FRONT_MARGIN, startTime - FRONT_MARGIN + timeLength);

           // display data of timewindow
           gnuplot.addLabel(sacData.getObserver().toPaddedInfoString() + " " + sacData.getComponent().toString(), "graph", 0.01, 0.95);
           gnuplot.addLabel(sacData.getGlobalCMTID().toString(), "graph", 0.01, 0.85);

           // plot waveforms
           // Absolute paths are used here because relative paths are hard to construct when workPath != mainBasicPath.
           String eventName = eventPath.getFileName().toString();
           gnuplot.addLine("0", BasicPlotAid.ZERO_APPEARANCE, "");
           Path mainFilePath = mainSynPath.toAbsolutePath().resolve(eventName).resolve(txtFileName);
           if (mainSynStyle != 0)
               gnuplot.addLine(mainFilePath.toString(), 3, 4, BasicPlotAid.switchSyntheticAppearance(mainSynStyle), mainSynName);
           if (refSynStyle1 != 0) {
               Path refFilePath1 = refSynPath1.toAbsolutePath().resolve(eventName).resolve(txtFileName);
               gnuplot.addLine(refFilePath1.toString(), 3, 4, BasicPlotAid.switchSyntheticAppearance(refSynStyle1), refSynName1);
           }
           if (residualStyle1 != 0) {
               Path refFilePath1 = refSynPath1.toAbsolutePath().resolve(eventName).resolve(txtFileName);
               gnuplot.addLine(refFilePath1.toString(), mainFilePath.toString(), "1:($2-$4)", BasicPlotAid.switchResidualAppearance(residualStyle1), residualName1);
           }
           if (refSynStyle2 != 0) {
               Path refFilePath2 = refSynPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
               gnuplot.addLine(refFilePath2.toString(), 3, 4, BasicPlotAid.switchSyntheticAppearance(refSynStyle2), refSynName2);
           }
           if (residualStyle2 != 0) {
               Path refFilePath2 = refSynPath2.toAbsolutePath().resolve(eventName).resolve(txtFileName);
               gnuplot.addLine(refFilePath2.toString(), mainFilePath.toString(), "1:($2-$4)", BasicPlotAid.switchResidualAppearance(residualStyle2), residualName2);
           }

           // add vertical lines and labels of travel times listed in travelTimeFile
           if (travelTimePath != null) {
               travelTimeInfoSet.stream()
                       .filter(info -> info.getEvent().equals(sacData.getGlobalCMTID()) && info.getObserver().equals(sacData.getObserver()))
                       .forEach(info -> {
                           Map<Phase, Double> usePhaseMap = info.getUsePhases();
                           for (Map.Entry<Phase, Double> entry : usePhaseMap.entrySet()) {
                               gnuplot.addVerticalLine(entry.getValue(), BasicPlotAid.USE_PHASE_APPEARANCE);
                               gnuplot.addLabel(entry.getKey().toString(), "first", entry.getValue(), "graph", 0.95, GnuplotColorName.turquoise);
                           }
                           Map<Phase, Double> avoidPhaseMap = info.getAvoidPhases();
                           for (Map.Entry<Phase, Double> entry : avoidPhaseMap.entrySet()) {
                               gnuplot.addVerticalLine(entry.getValue(), BasicPlotAid.AVOID_PHASE_APPEARANCE);
                               gnuplot.addLabel(entry.getKey().toString(), "first", entry.getValue(), "graph", 0.95, GnuplotColorName.violet);
                           }
                       });
           }
           // add vertical lines and labels of travel times computed by TauP
           else if (displayPhases != null) {
               timeTool.setPhaseNames(displayPhases);
               timeTool.calculate(sacData.getValue(SACHeaderEnum.GCARC));
               List<Arrival> arrivals = timeTool.getArrivals();
               for (String phase : displayPhases) {
                   Optional<Arrival> arrivalOpt = arrivals.stream().filter(arrival -> arrival.getPhase().getName().equals(phase)).findFirst();
                   if (arrivalOpt.isPresent()) {
                       gnuplot.addVerticalLine(arrivalOpt.get().getTime(), BasicPlotAid.USE_PHASE_APPEARANCE);
                       gnuplot.addLabel(phase, "first", arrivalOpt.get().getTime(), "graph", 0.95, GnuplotColorName.turquoise);
                   }
               }
           }

           // this is not done for the last obsID because we don't want an extra blank page to be created
           if ((i + 1) < sacNames.size()) {
               if ((i + 1) % NUM_PER_PAGE == 0) {
                   gnuplot.nextPage();
               } else {
                   gnuplot.nextField();
               }
           }
           i++;
       }
       // fill the last page with blank fields so that fields on the last page will get the same size as those on other pages
       while(i % NUM_PER_PAGE != 0) {
           i++;
           gnuplot.nextField();
       }

       gnuplot.write();
       if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
   }

}
