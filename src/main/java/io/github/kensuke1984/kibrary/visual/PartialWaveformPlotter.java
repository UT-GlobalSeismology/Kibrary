package io.github.kensuke1984.kibrary.visual;

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
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 *
 * @author otsuru
 * @since 2022/7/24
 */
public class PartialWaveformPlotter extends Operation {

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
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * path of partial ID file
     */
    private Path partialIDPath;
    /**
     * path of partial data
     */
    private Path partialPath;
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
     * The time length to plot
     */
    private double timeLength;
    /**
     * How much to scale down the residual waveform
     */
    private double residualScale;

    /**
     * Set of information of travel times
     */
    private Set<TravelTimeInformation> travelTimeInfoSet;
    private BasicID[] basicIDs;


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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of a basic ID file, if plotting residual waveform");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, if plotting residual waveform");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a travel time information file, if plotting travel times");
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
            pw.println("##(double) Time length of each plot [s] (150)");
            pw.println("#timeLength ");
            pw.println("##(double) How much to scale down the residual waveform (1e7)");
            pw.println("#residualScale ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialWaveformPlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        if (property.containsKey("basicIDPath") && property.containsKey("basicPath")) {
            basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
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
       PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
       partialIDs = Arrays.stream(partialIDs).filter(id ->
               components.contains(id.getSacComponent())
               && tendEvents.contains(id.getGlobalCMTID())
               && tendObserverNames.contains(id.getObserver().toString())
               && checkPosition(id.getVoxelPosition()))
               .toArray(PartialID[]::new);

       // read basicIDs
       if (basicIDPath != null && basicPath != null) {
           basicIDs = BasicIDFile.read(basicIDPath, basicPath);
       }

       // read travel time information
       if (travelTimePath != null) {
           travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
       }

       // prepare output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "partialPlot", folderTag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       for (GlobalCMTID event : tendEvents) {
           for (String observerName : tendObserverNames) {
               Path rayPath = outPath.resolve(event + "_" + observerName);
               Files.createDirectories(rayPath);

               for (SACComponent component : components) {
                   PartialID[] useIDs = Arrays.stream(partialIDs).filter(id ->
                           id.getSacComponent().equals(component)
                           && id.getGlobalCMTID().equals(event)
                           && id.getObserver().toString().equals(observerName))
                           .sorted(Comparator.comparing(PartialID::getVoxelPosition))
                           .toArray(PartialID[]::new);
                   if (useIDs.length == 0) continue;

                   String fileNameRoot = "plot_" + event + "_" + observerName + "_" + component;
                   createPlot(rayPath, useIDs, fileNameRoot);
               }
           }
       }
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

   private void createPlot(Path rayPath, PartialID[] ids, String fileNameRoot) throws IOException {
       if (ids.length == 0) {
           return;
       }

       // output partial data in text file
       String fileName = "partialWaveforms." + ids[0].getSacComponent() + ".txt";
       outputWaveformTxt(rayPath.resolve(fileName), ids);

       // output waveform data to text file
       boolean wroteBasic = false;
       String basicFileName = null;
       if (basicIDs != null) {
           BasicID[] pairIDs = Arrays.stream(basicIDs).filter(basic -> BasicID.isPair(basic, ids[0])).toArray(BasicID[]::new);
           if (pairIDs.length != 2) {
               System.err.println(pairIDs.length);
               for (BasicID basic : pairIDs) System.err.println(basic);
               System.err.println("Failed to find basicIDs for " + ids[0].getGlobalCMTID() + " " + ids[0].getObserver() + " " + ids[0].getSacComponent());
           } else {
               BasicID obsID = (pairIDs[0].getWaveformType() == WaveformType.OBS) ? pairIDs[0] : pairIDs[1];
               BasicID synID = (pairIDs[0].getWaveformType() == WaveformType.SYN) ? pairIDs[0] : pairIDs[1];
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
       gnuplot.setKey(true, "top right");

       int i;
       for (i = 0; i < ids.length; i++) {
           PartialID id = ids[i];

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
           if ((i + 1) < ids.length) {
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
    private static void outputWaveformTxt(Path outputPath, PartialID[] ids) throws IOException {
       double startTime = ids[0].getStartTime();
       double samplingHz = ids[0].getSamplingHz();
       List<double[]> dataList = Arrays.stream(ids).map(PartialID::getData).collect(Collectors.toList());

       try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath))){

           // each time step
           for (int j = 0; j < dataList.get(0).length; j++) {
               double time = startTime + j / samplingHz;
               pwTrace.print(time);

               // each Partial ID
               for (int k = 0; k < ids.length; k++) {
                   pwTrace.print(" " + dataList.get(k)[j]);
               }
               pwTrace.println();
           }
       }
   }

}
