package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformation;
import io.github.kensuke1984.kibrary.timewindow.TravelTimeInformationFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * @author otsuru
 * @since 2022/7/24
 */
public class PartialWaveformPlotter extends Operation {

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
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
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
        }
        System.err.println(outPath + " is created.");
    }

    public PartialWaveformPlotter(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);

        if (property.containsKey("travelTimePath"))
            travelTimePath = property.parsePath("travelTimePath", null, true, workPath);

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObserverNames = Arrays.stream(property.parseStringArray("tendObserverNames", null)).collect(Collectors.toSet());
        tendVoxelLatitudes = property.parseDoubleArray("tendVoxelLatitudes", null);
        tendVoxelLongitudes = property.parseDoubleArray("tendVoxelLongitudes", null);
        tendVoxelRadii = property.parseDoubleArray("tendVoxelRadii", null);

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

       // read travel time information
       if (travelTimePath != null) {
           travelTimeInfoSet = TravelTimeInformationFile.read(travelTimePath);
       }

       // prepare output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "partialPlot", tag, GadgetAid.getTemporaryString());
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

   }
}
