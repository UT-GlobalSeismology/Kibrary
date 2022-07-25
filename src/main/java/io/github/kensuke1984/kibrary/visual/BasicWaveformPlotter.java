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
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Plots waveform data.
 * For each event, a pdf file with waveforms for all observers will be created.
 * Each plot includes the original observed waveform, the shifted observed waveform, and the synthetic waveform.
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
     * Path of a basic ID file
     */
    private Path basicIDPath;
    /**
     * Path of a basic waveform file
     */
    private Path basicPath;
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
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a travel time information file, if plotting travel times");
            pw.println("#travelTimePath travelTime.inf");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##(boolean) Whether to export individual files for each component (true)");
            pw.println("#splitComponents ");
            pw.println("##(double) Time length of each plot [s] (150)");
            pw.println("#timeLength ");
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

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);

        if (property.containsKey("travelTimePath"))
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }
        splitComponents = property.parseBoolean("splitComponents", "true");
        timeLength = property.parseDouble("timeLength", "150");
    }

   @Override
   public void run() throws IOException {
       BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
       basicIDs = Arrays.stream(basicIDs).filter(id -> components.contains(id.getSacComponent())).toArray(BasicID[]::new);

       // get all events included in basicIDs
       Set<GlobalCMTID> allEvents = Arrays.stream(basicIDs).map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
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

       // read travel time information
       if (travelTimePath != null) {
           travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
       }

       for (EventFolder eventDir : eventDirs) {
           // create event directory if it does not exist
           Files.createDirectories(eventDir.toPath());

           if (splitComponents) {
               for (SACComponent component : components) {
                   BasicID[] useIds = Arrays.stream(basicIDs).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                           && id.getSacComponent().equals(component))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .toArray(BasicID[]::new);

                   String fileNameRoot = "plot_" + eventDir.toString() + "_" + component.toString();
                   createPlot(eventDir, useIds, fileNameRoot);
               }
           } else {
               BasicID[] useIds = Arrays.stream(basicIDs).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID()))
                       .sorted(Comparator.comparing(BasicID::getObserver).thenComparing(BasicID::getSacComponent))
                       .toArray(BasicID[]::new);

               String fileNameRoot = "plot_" + eventDir.toString();
               createPlot(eventDir, useIds, fileNameRoot);
           }
       }

   }

    /**
     * @param eventDir (EventFolder)
     * @param ids (BasicID) IDs to be plotted
     * @param fileNameRoot (String) The root of file names of output plot and graph files
     * @throws IOException
     */
    private void createPlot(EventFolder eventDir, BasicID[] ids, String fileNameRoot) throws IOException {
        if (ids.length == 0) {
            return;
        }

        BasicIDPairUp pairer = new BasicIDPairUp(ids);
        List<BasicID> obsList = pairer.getObsList();
        List<BasicID> synList = pairer.getSynList();

        GnuplotFile gnuplot = new GnuplotFile(eventDir.toPath().resolve(fileNameRoot + ".plt"));

        GnuplotLineAppearance originalAppearance = new GnuplotLineAppearance(2, GnuplotColorName.gray, 1);
        GnuplotLineAppearance shiftedAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);
        GnuplotLineAppearance resAppearance = new GnuplotLineAppearance(1, GnuplotColorName.skyblue, 1);
        GnuplotLineAppearance zeroAppearance = new GnuplotLineAppearance(1, GnuplotColorName.light_gray, 1);
        GnuplotLineAppearance usePhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.turquoise, 1);
        GnuplotLineAppearance avoidPhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.violet, 1);

        gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
        gnuplot.setMarginH(15, 5);
        gnuplot.setFont("Arial", 10, 8, 8, 8, 8);
        gnuplot.setKey(true, "top right");

        //gnuplot.setXlabel("time");
        //gnuplot.setYlabel("value");
        //gnuplot.setTitle("Test");

        int i;
        for (i = 0; i < obsList.size(); i++) {
            BasicID obsID = obsList.get(i);
            BasicID synID = synList.get(i);

            // output waveform data to text file if it has not already been done so
            String filename = BasicIDFile.getWaveformTxtFileName(obsID);
            if (!Files.exists(eventDir.toPath().resolve(filename))) {
                BasicIDFile.outputWaveformTxt(eventDir.toPath(), obsID, synID);
            }

            // set xrange
            gnuplot.setXrange(synID.getStartTime() - FRONT_MARGIN, synID.getStartTime() - FRONT_MARGIN + timeLength);

            // display data of timewindow
            gnuplot.addLabel(obsID.getObserver().toPaddedInfoString() + " " + obsID.getSacComponent().toString(), "graph", 0.01, 0.95);
            gnuplot.addLabel(obsID.getGlobalCMTID().toString(), "graph", 0.01, 0.85);

            // plot waveforms
            gnuplot.addLine("0", zeroAppearance, "");
            gnuplot.addLine(filename, 1, 2, originalAppearance, "original");
            gnuplot.addLine(filename, 3, 2, shiftedAppearance, "shifted");
            gnuplot.addLine(filename, 3, 4, synAppearance, "synthetic");
            gnuplot.addLine(filename, "3:($2-$4)", resAppearance, "residual");

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
