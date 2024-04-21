package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformation;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Plots partial waveforms.
 *
 * @author otsuru
 * @since 2022/7/24
 */
public class PartialsPlotter extends Operation {

    /**
     * Number of fields per page on output pdf file.
     */
    private static final int NUM_PER_PAGE = 12;
    /**
     * The time margin in the plot before the start time of the synthetic waveform.
     */
    private static final double FRONT_MARGIN = 10;

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
     * Basic waveform folder.
     */
    private Path basicPath;
    /**
     * Path of a travel time information file.
     */
    private Path travelTimePath;

    /**
     * Events to work for.
     */
    private Set<GlobalCMTID> tendEvents;
    /**
     * Names of observers to work for, in the form "net_sta".
     */
    private Set<String> tendObserverNames;
    private double[] tendVoxelLatitudes;
    private double[] tendVoxelLongitudes;
    private double[] tendVoxelRadii;
    /**
     * The time length to plot.
     */
    private double timeLength;
    /**
     * How much to scale down the residual waveform.
     */
    private double residualScale;

    /**
     * Set of information of travel times.
     */
    private Set<TravelTimeInformation> travelTimeInfoSet;
    private List<BasicID> basicIDs;

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
            pw.println("##Path of a basic waveform folder, if plotting residual waveform.");
            pw.println("#basicPath actual");
            pw.println("##Path of a travel time information file, if plotting travel times.");
            pw.println("#travelTimePath travelTime.inf");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in form \"sta_net\", listed using spaces, must be set.");
            pw.println("#tendObserverNames ");
            pw.println("##(double) Latitudes of voxels to work for, listed using spaces, must be set.");
            pw.println("#tendVoxelLatitudes ");
            pw.println("##(double) Longitudes of voxels to work for, listed using spaces, must be set.");
            pw.println("#tendVoxelLongitudes ");
            pw.println("##(double) Radii of voxels to work for, listed using spaces, must be set.");
            pw.println("#tendVoxelRadii ");
            pw.println("##(double) Time length of each plot [s]. (150)");
            pw.println("#timeLength ");
            pw.println("##(double) How much to scale down the residual waveform. (1e7)");
            pw.println("#residualScale ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsPlotter(Property property) throws IOException {
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
        if (property.containsKey("basicPath")) {
            basicPath = property.parsePath("basicPath", null, true, workPath);
        }
        if (property.containsKey("travelTimePath"))
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObserverNames = Arrays.stream(property.parseStringArray("tendObserverNames", null)).collect(Collectors.toSet());
        tendVoxelLatitudes = property.parseDoubleArray("tendVoxelLatitudes", null);
        tendVoxelLongitudes = property.parseDoubleArray("tendVoxelLongitudes", null);
        tendVoxelRadii = property.parseDoubleArray("tendVoxelRadii", null);

        timeLength = property.parseDouble("timeLength", "150");
        residualScale = property.parseDouble("residualScale", "1e7");
    }

   @Override
   public void run() throws IOException {
       List<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream().filter(id ->
               components.contains(id.getSacComponent())
               && tendEvents.contains(id.getGlobalCMTID())
               && tendObserverNames.contains(id.getObserver().toString())
               && checkPosition(id.getVoxelPosition()))
               .collect(Collectors.toList());

       // read basicIDs
       if (basicPath != null) {
           basicIDs = BasicIDFile.read(basicPath, true);
       }

       // read travel time information
       if (travelTimePath != null) {
           travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
       }

       // prepare output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "partialPlot", folderTag, appendFolderDate, null);
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       int num = 0;
       for (GlobalCMTID event : tendEvents) {
           for (String observerName : tendObserverNames) {
               Path eventObserverPath = outPath.resolve(event + "_" + observerName);

               for (SACComponent component : components) {
                   List<PartialID> useIDs = partialIDs.stream().filter(id ->
                           id.getSacComponent().equals(component)
                           && id.getGlobalCMTID().equals(event)
                           && id.getObserver().toString().equals(observerName))
                           .sorted(Comparator.comparing(PartialID::getVoxelPosition))
                           .collect(Collectors.toList());
                   if (useIDs.size() == 0) continue;

                   Files.createDirectories(eventObserverPath);
                   String fileNameRoot = "plot_" + event + "_" + observerName + "_" + component;
                   createPlot(eventObserverPath, useIDs, fileNameRoot);
                   num++;
               }
           }
       }
       System.err.println("Created " + num + " plots.");
   }

   private boolean checkPosition(FullPosition position) {

       // check latitude
       double latitude = position.getLatitude();
       boolean flag = false;
       for (double tendLatitude : tendVoxelLatitudes) {
           if (Precision.equals(latitude, tendLatitude, FullPosition.LATITUDE_EPSILON)) {
               flag = true;
               break;
           }
       }
       if (flag == false) return false;

       // check longitude
       double longitude = position.getLongitude();
       flag = false;
       for (double tendLongitude : tendVoxelLongitudes) {
           if (Precision.equals(longitude, tendLongitude, FullPosition.LONGITUDE_EPSILON)) {
               flag = true;
               break;
           }
       }
       if (flag == false) return false;

       // check radius
       double radius = position.getR();
       flag = false;
       for (double tendRadius : tendVoxelRadii) {
           if (Precision.equals(radius, tendRadius, FullPosition.RADIUS_EPSILON)) {
               flag = true;
               break;
           }
       }
       return flag;
   }

   private void createPlot(Path rayPath, List<PartialID> ids, String fileNameRoot) throws IOException {
       if (ids.size() == 0) {
           return;
       }

       // output partial data in text file
       String fileName = "partialWaveforms." + ids.get(0).getSacComponent() + ".txt";
       outputWaveformTxt(rayPath.resolve(fileName), ids);

       // output waveform data to text file
       boolean wroteBasic = false;
       String basicFileName = null;
       if (basicIDs != null) {
           List<BasicID> pairIDs = basicIDs.stream().filter(basic -> BasicID.isPair(basic, ids.get(0))).collect(Collectors.toList());
           if (pairIDs.size() != 2) {
               System.err.println(pairIDs.size());
               for (BasicID basic : pairIDs) System.err.println(basic);
               System.err.println("Failed to find basicIDs for " + ids.get(0).getGlobalCMTID() + " " + ids.get(0).getObserver()
                       + " " + ids.get(0).getSacComponent());
           } else {
               BasicID obsID = (pairIDs.get(0).getWaveformType() == WaveformType.OBS) ? pairIDs.get(0) : pairIDs.get(1);
               BasicID synID = (pairIDs.get(0).getWaveformType() == WaveformType.SYN) ? pairIDs.get(0) : pairIDs.get(1);
               basicFileName = BasicIDFile.getWaveformTxtFileName(obsID);
               BasicIDFile.outputWaveformTxt(rayPath, obsID, synID);
               wroteBasic = true;
           }
       }

       GnuplotFile gnuplot = new GnuplotFile(rayPath.resolve(fileNameRoot + ".plt"));

       GnuplotLineAppearance partialAppearance = new GnuplotLineAppearance(1, GnuplotColorName.dark_green, 1);
       GnuplotLineAppearance resAppearance = new GnuplotLineAppearance(1, GnuplotColorName.skyblue, 1);
       GnuplotLineAppearance zeroAppearance = new GnuplotLineAppearance(1, GnuplotColorName.light_gray, 1);
       GnuplotLineAppearance usePhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.turquoise, 1);
       GnuplotLineAppearance avoidPhaseAppearance = new GnuplotLineAppearance(1, GnuplotColorName.violet, 1);

       gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
       gnuplot.setMarginH(15, 5);
       gnuplot.setFont("Arial", 10, 8, 8, 8, 8);
       gnuplot.setCommonKey(true, false, "top right");

       int i;
       for (i = 0; i < ids.size(); i++) {
           PartialID id = ids.get(i);

           // set xrange
           gnuplot.setXrange(id.getStartTime() - FRONT_MARGIN, id.getStartTime() - FRONT_MARGIN + timeLength);

           // display data of timewindow
           gnuplot.addLabel(id.getObserver().toPaddedInfoString() + " " + id.getSacComponent().toString(), "graph", 0.01, 0.95);
           gnuplot.addLabel(id.getGlobalCMTID().toString(), "graph", 0.01, 0.85);
           gnuplot.addLabel(id.getVoxelPosition().toString(), "graph", 0.01, 0.75);

           // plot waveforms
           gnuplot.addLine("0", zeroAppearance, "");
           gnuplot.addLine(fileName, 1, i + 2, partialAppearance, "partial");
           if (wroteBasic) gnuplot.addLine(basicFileName, "3:(($2-$4)/" + residualScale + ")", resAppearance, "residual/" + residualScale);

           // add vertical lines and labels of travel times
           if (travelTimeInfoSet != null) {
               travelTimeInfoSet.stream()
                       .filter(info -> info.getEvent().equals(id.getGlobalCMTID()) && info.getObserver().equals(id.getObserver()))
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
           if ((i + 1) < ids.size()) {
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

   /**
     * @param outputPath (Path) Output text file
     * @param ids (PartialID[]) Partial IDs, must have same start time and sampling Hz.
     * @throws IOException
     */
    private static void outputWaveformTxt(Path outputPath, List<PartialID> ids) throws IOException {
       double startTime = ids.get(0).getStartTime();
       double samplingHz = ids.get(0).getSamplingHz();
       List<double[]> dataList = ids.stream().map(PartialID::getData).collect(Collectors.toList());

       try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath))){

           // each time step
           for (int j = 0; j < dataList.get(0).length; j++) {
               double time = startTime + j / samplingHz;
               pwTrace.print(time);

               // each Partial ID
               for (int k = 0; k < ids.size(); k++) {
                   pwTrace.print(" " + dataList.get(k)[j]);
               }
               pwTrace.println();
           }
       }
   }

}
