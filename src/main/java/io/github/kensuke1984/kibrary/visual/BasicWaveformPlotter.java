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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformation;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Plots waveform data from a set of {@link BasicIDFile}.
 * For each event, a pdf file with waveforms for all observers will be created.
 * Each plot includes the original observed waveform, the shifted observed waveform, and the synthetic waveform.
 * The residual waveform can also be plotted.
 * Vertical lines of travel times can be displayed if a {@link TravelTimeInformationFile} is set as input.
 * <p>
 * Waveform data for each observer will be written in txt files under event directories, if they do not exist already.
 * Output pdf files will be created under each of the event directories.
 *
 * @author otsuru
 * @since 2021/12/10
 */
public class BasicWaveformPlotter extends Operation {

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
     * Path of a travel time information file
     */
    private Path travelTimePath;

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

    private int unshiftedObsStyle;
    private String unshiftedObsName;
    private int shiftedObsStyle;
    private String shiftedObsName;
    private int mainSynStyle;
    private String mainSynName;
    private int residualStyle;
    private String residualName;
    private int refSynStyle1;
    private String refSynName1;
    private int refSynStyle2;
    private String refSynName2;

    /**
     * Set of information of travel times
     */
    private Set<TravelTimeInformation> travelTimeInfoSet;

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
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic waveform folder (.)");
            pw.println("#mainBasicPath ");
            pw.println("##Path of reference basic waveform folder 1, when plotting their waveforms");
            pw.println("#refBasicPath1 ");
            pw.println("##Path of reference basic waveform folder 2, when plotting their waveforms");
            pw.println("#refBasicPath2 ");
            pw.println("##Path of a travel time information file, if plotting travel times");
            pw.println("#travelTimePath travelTime.inf");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##(boolean) Whether to export individual files for each component (true)");
            pw.println("#splitComponents ");
            pw.println("##(double) Time length of each plot [s] (150)");
            pw.println("#timeLength ");
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
            pw.println("##Plot style for main residual waveform, from {0:no plot, 1:purple} (1)");
            pw.println("#residualStyle 0");
            pw.println("##Name for main residual waveform (residual)");
            pw.println("#residualName ");
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

    public BasicWaveformPlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        mainBasicPath = property.parsePath("mainBasicPath", ".", true, workPath);
        if (property.containsKey("refBasicPath1"))
            refBasicPath1 = property.parsePath("refBasicPath1", ".", true, workPath);
        if (property.containsKey("refBasicPath2"))
            refBasicPath2 = property.parsePath("refBasicPath2", ".", true, workPath);
        if (property.containsKey("travelTimePath"))
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }
        splitComponents = property.parseBoolean("splitComponents", "true");
        timeLength = property.parseDouble("timeLength", "150");

        unshiftedObsStyle = property.parseInt("unshiftedObsStyle", "1");
        unshiftedObsName = property.parseString("unshiftedObsName", "unshifted");
        shiftedObsStyle = property.parseInt("shiftedObsStyle", "2");
        shiftedObsName = property.parseString("shiftedObsName", "shifted");
        mainSynStyle = property.parseInt("mainSynStyle", "1");
        mainSynName = property.parseString("mainSynName", "synthetic");
        residualStyle = property.parseInt("residualStyle", "1");
        residualName = property.parseString("residualName", "residual");
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
       String dateStr = GadgetAid.getTemporaryString();

       // read main basic waveform folders
       List<BasicID> mainBasicIDs = BasicIDFile.read(mainBasicPath, true).stream()
               .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());

       // collect all events included in mainBasicIDs
       Set<GlobalCMTID> events;
       if (tendEvents.isEmpty()) {
           events = mainBasicIDs.stream().map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
       } else {
           events = mainBasicIDs.stream().map(id -> id.getGlobalCMTID())
                   .filter(event -> tendEvents.contains(event)).distinct().collect(Collectors.toSet());
       }
       if (!DatasetAid.checkNum(events.size(), "event", "events")) {
           return;
       }

       // read reference basic waveform folders
       List<BasicID> refBasicIDs1 = null;
       if (refBasicPath1 != null && refSynStyle1 != 0) {
           refBasicIDs1 = BasicIDFile.read(refBasicPath1, true).stream()
                   .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());
       }
       List<BasicID> refBasicIDs2 = null;
       if (refBasicPath2 != null && refSynStyle2 != 0) {
           refBasicIDs2 = BasicIDFile.read(refBasicPath2, true).stream()
                   .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());
       }

       // read travel time information
       if (travelTimePath != null) {
           travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
       }

       for (GlobalCMTID event : events) {
           // write out waveforms in txt files under each basic waveform folder
           if (refBasicPath1 != null && refSynStyle1 != 0) {
               List<BasicID> correspondingRefBasicIDs1 = refBasicIDs1.stream()
                       .filter(id -> id.getGlobalCMTID().equals(event)).collect(Collectors.toList());
               Files.createDirectories(refBasicPath1.resolve(event.toString()));
               outputWaveforms(correspondingRefBasicIDs1, refBasicPath1.resolve(event.toString()));
           }
           if (refBasicPath2 != null && refSynStyle2 != 0) {
               List<BasicID> correspondingRefBasicIDs2 = refBasicIDs2.stream()
                       .filter(id -> id.getGlobalCMTID().equals(event)).collect(Collectors.toList());
               Files.createDirectories(refBasicPath2.resolve(event.toString()));
               outputWaveforms(correspondingRefBasicIDs2, refBasicPath2.resolve(event.toString()));
           }
           List<BasicID> correspondingMainBasicIDs = mainBasicIDs.stream()
                   .filter(id -> id.getGlobalCMTID().equals(event)).collect(Collectors.toList());
           Files.createDirectories(mainBasicPath.resolve(event.toString()));
           outputWaveforms(correspondingMainBasicIDs, mainBasicPath.resolve(event.toString()));

           // create plots under workPath
           Path eventPath = workPath.resolve(event.toString());
           Files.createDirectories(eventPath);
           if (splitComponents) {
               for (SACComponent component : components) {
                   List<BasicID> useIds = correspondingMainBasicIDs.stream().filter(id -> id.getSacComponent().equals(component))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .collect(Collectors.toList());

                   String fileNameRoot = "plot" + dateStr + "_" + component.toString();
                   createPlot(eventPath, useIds, fileNameRoot);
               }
           } else {
               List<BasicID> useIds = correspondingMainBasicIDs.stream()
                       .sorted(Comparator.comparing(BasicID::getObserver).thenComparing(BasicID::getSacComponent))
                       .collect(Collectors.toList());

               String fileNameRoot = "plot" + dateStr;
               createPlot(eventPath, useIds, fileNameRoot);
           }

       }
   }

   private void outputWaveforms(List<BasicID> ids, Path outPath) throws IOException {
       BasicIDPairUp pairer = new BasicIDPairUp(ids);
       List<BasicID> obsList = pairer.getObsList();
       List<BasicID> synList = pairer.getSynList();

       for (int i = 0; i < obsList.size(); i++) {
           BasicID obsID = obsList.get(i);
           BasicID synID = synList.get(i);

           // output waveform data to text file if it has not already been done so
           String fileName = BasicIDFile.getWaveformTxtFileName(obsID);
           if (!Files.exists(outPath.resolve(fileName))) {
               BasicIDFile.outputWaveformTxt(outPath, obsID, synID);
           }
       }
   }

    /**
     * @param eventDir (EventFolder)
     * @param ids (BasicID) IDs to be plotted
     * @param fileNameRoot (String) The root of file names of output plot and graph files
     * @throws IOException
     */
    private void createPlot(Path eventPath, List<BasicID> ids, String fileNameRoot) throws IOException {
        if (ids.size() == 0) {
            return;
        }

        BasicIDPairUp pairer = new BasicIDPairUp(ids);
        List<BasicID> obsList = pairer.getObsList();
        List<BasicID> synList = pairer.getSynList();

        GnuplotFile gnuplot = new GnuplotFile(eventPath.resolve(fileNameRoot + ".plt"));

        GnuplotLineAppearance originalAppearance = new GnuplotLineAppearance(2, GnuplotColorName.gray, 1);
        GnuplotLineAppearance shiftedAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);
        GnuplotLineAppearance resultAppearance = new GnuplotLineAppearance(1, GnuplotColorName.web_blue, 1);
        GnuplotLineAppearance resAppearance = new GnuplotLineAppearance(1, GnuplotColorName.skyblue, 1);
        GnuplotLineAppearance zeroAppearance = new GnuplotLineAppearance(1, GnuplotColorName.light_gray, 1);
        GnuplotLineAppearance usePhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.turquoise, 1);
        GnuplotLineAppearance avoidPhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.violet, 1);

        gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
        gnuplot.setMarginH(15, 5);
        gnuplot.setFont("Arial", 10, 8, 8, 8, 8);
        gnuplot.setCommonKey(true, false, "top right");

        int i;
        for (i = 0; i < obsList.size(); i++) {
            BasicID obsID = obsList.get(i);
            BasicID synID = synList.get(i);
            String txtFileName = BasicIDFile.getWaveformTxtFileName(obsID);

            // set xrange
            gnuplot.setXrange(synID.getStartTime() - FRONT_MARGIN, synID.getStartTime() - FRONT_MARGIN + timeLength);

            // display data of timewindow
            gnuplot.addLabel(obsID.getObserver().toPaddedInfoString() + " " + obsID.getSacComponent().toString(), "graph", 0.01, 0.95);
            gnuplot.addLabel(obsID.getGlobalCMTID().toString(), "graph", 0.01, 0.85);

            // plot waveforms
            gnuplot.addLine("0", zeroAppearance, "");
            if (unshiftedObsStyle != 0)
                gnuplot.addLine(txtFileName, 1, 2, originalAppearance, unshiftedObsName);
            if (shiftedObsStyle != 0)
                gnuplot.addLine(txtFileName, 3, 2, originalAppearance, shiftedObsName);
            if (mainSynStyle != 0)
                gnuplot.addLine(txtFileName, 3, 4, originalAppearance, mainSynName);
            if (residualStyle != 0)
                gnuplot.addLine(txtFileName, "3:($2-$4)", originalAppearance, residualName);
            if (refSynStyle1 != 0) {
                Path refFilePath1 = refBasicPath1.toAbsolutePath().resolve(txtFileName);
                gnuplot.addLine(refFilePath1.toString(), 3, 4, originalAppearance, refSynName1);
            }
            if (refSynStyle2 != 0) {
                Path refFilePath2 = refBasicPath2.toAbsolutePath().resolve(txtFileName);
                gnuplot.addLine(refFilePath2.toString(), 3, 4, originalAppearance, refSynName2);
            }

            // add vertical lines and labels of travel times
            if (travelTimeInfoSet != null) {
                travelTimeInfoSet.stream()
                        .filter(info -> info.getEvent().equals(obsID.getGlobalCMTID()) && info.getObserver().equals(obsID.getObserver()))
                        .forEach(info -> {
                            Map<Phase, Double> usePhaseMap = info.getUsePhases();
                            for (Map.Entry<Phase, Double> entry : usePhaseMap.entrySet()) {
                                gnuplot.addVerticalLine(entry.getValue(), usePhaseAppearance);
                                gnuplot.addLabel(entry.getKey().toString(), "first", entry.getValue(), "graph", 0.95, GnuplotColorName.turquoise);
                            }
                            Map<Phase, Double> avoidPhaseMap = info.getAvoidPhases();
                            for (Map.Entry<Phase, Double> entry : avoidPhaseMap.entrySet()) {
                                gnuplot.addVerticalLine(entry.getValue(), avoidPhaseAppearance);
                                gnuplot.addLabel(entry.getKey().toString(), "first", entry.getValue(), "graph", 0.95, GnuplotColorName.violet);
                            }
                        });
            }

            // this is not done for the last obsID because we don't want an extra blank page to be created
            if ((i + 1) < obsList.size()) {
                if ((i + 1) % NUM_PER_PAGE == 0) {
                    gnuplot.nextPage();
                } else {
                    gnuplot.nextField();
                }
            }
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
