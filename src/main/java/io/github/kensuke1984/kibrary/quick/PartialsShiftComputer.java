package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.perturbation.ScalarListFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

public class PartialsShiftComputer extends Operation {

    private static final double halfWindowLength = 20;
    private static final double PEAK_AMP_RATIO_THRESHOLD = 0.9;

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

    Map<FullPosition, Double> shiftMap = new LinkedHashMap<>();
    Map<FullPosition, Double> varMap = new LinkedHashMap<>();
    Map<FullPosition, Double> ampMap = new LinkedHashMap<>();

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

    public PartialsShiftComputer(Property property) throws IOException {
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

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObserverNames = Arrays.stream(property.parseStringArray("tendObserverNames", null)).collect(Collectors.toSet());
        tendVoxelLatitudes = property.parseDoubleArray("tendVoxelLatitudes", null);
        tendVoxelLongitudes = property.parseDoubleArray("tendVoxelLongitudes", null);
        tendVoxelRadii = property.parseDoubleArray("tendVoxelRadii", null);
    }

   @Override
   public void run() throws IOException {
       List<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream().filter(id ->
               components.contains(id.getSacComponent())
               && tendEvents.contains(id.getGlobalCMTID())
               && tendObserverNames.contains(id.getObserver().toString()))
               .collect(Collectors.toList());

       int num = 0;
       for (GlobalCMTID event : tendEvents) {
           for (String observerName : tendObserverNames) {
//               Path eventObserverPath = outPath.resolve(event + "_" + observerName);

               for (SACComponent component : components) {
                   List<PartialID> useIDs = partialIDs.stream().filter(id ->
                           id.getSacComponent().equals(component)
                           && id.getGlobalCMTID().equals(event)
                           && id.getObserver().toString().equals(observerName))
                           .sorted(Comparator.comparing(PartialID::getVoxelPosition))
                           .collect(Collectors.toList());
                   if (useIDs.size() == 0) continue;

                   process(useIDs);
                   num++;
               }
           }
       }

       // prepare output folder
       Path outPath = DatasetAid.createOutputFolder(workPath, "partialShift", folderTag, appendFolderDate, null);
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       ScalarListFile.write(ampMap, outPath.resolve("scalar_amp..ABSOLUTE.lst"));
       ScalarListFile.write(varMap, outPath.resolve("scalar_var..ABSOLUTE.lst"));
       ScalarListFile.write(shiftMap, outPath.resolve("scalar_shift..ABSOLUTE.lst"));
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

   private void process(List<PartialID> ids) throws IOException {
       if (ids.size() == 0) {
           return;
       }

       double baseLatitude = tendVoxelLatitudes[0];
       double baseLongitude = tendVoxelLongitudes[0];
       double baseRadius = tendVoxelRadii[0];
       FullPosition basePosition = new FullPosition(baseLatitude, baseLongitude, baseRadius);
       PartialID baseID = ids.stream().filter(id -> id.getVoxelPosition().equals(basePosition)).findFirst().get();
       Trace baseTrace = cutFirstPeakWindowTrace(baseID.toTrace());
       double samplingHz = baseID.getSamplingHz();

       int i;
       for (i = 0; i < ids.size(); i++) {
           PartialID id = ids.get(i);
           FullPosition position = id.getVoxelPosition();
           if (position.getR() > 3506) continue;

//           System.err.println(id.getVoxelPosition().toString());

//           double[] shiftResults = baseTrace.findBestShift(cutFirstPeakWindowTrace(id.toTrace()), true, true, samplingHz);
           double[] shiftResults = id.toTrace().findBestVarianceShift(baseTrace, true, true, samplingHz);
           shiftMap.put(position, -shiftResults[0]);
           varMap.put(position, shiftResults[1]);
           ampMap.put(position, shiftResults[2]);
//           System.err.println(" shift: " + -shiftResults[0] + " corr: " + shiftResults[1] + " amp: " + shiftResults[2]);
       }
   }

   private Trace cutFirstPeakWindowTrace(Trace trace) {
       // get indices of peaks
       int[] indicesOfPeak = trace.getIndicesOfPeak();
       // max absolute value
       double max = Math.abs(trace.getYAt(indicesOfPeak[0]));
       // find index of first peak that exceeds 0.9*max
       int firstPeakIndex = indicesOfPeak[0];
       for (int i = 1; i < indicesOfPeak.length; i++) {
           if (Math.abs(trace.getYAt(indicesOfPeak[i])) > PEAK_AMP_RATIO_THRESHOLD * max && indicesOfPeak[i] < firstPeakIndex)
               firstPeakIndex = indicesOfPeak[i];
       }
       // return Trace in window that includes first peak
       double peakX = trace.getXAt(firstPeakIndex);
       return trace.cutWindow(peakX - halfWindowLength, peakX + halfWindowLength);
   }

}
