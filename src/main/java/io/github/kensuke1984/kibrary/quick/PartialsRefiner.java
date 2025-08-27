package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

public class PartialsRefiner extends Operation {

    private static final double halfWindowLength = 20;

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
     * Path of a voxel information file.
     */
    private Path voxelPath;

    /**
     * Events to work for.
     */
    private Set<GlobalCMTID> tendEvents;
    /**
     * Names of observers to work for, in the form "net_sta".
     */
    private Set<String> tendObserverNames;

    /**
     * Horizontal pixels.
     */
    private List<HorizontalPixel> horizontalPixels;
    /**
     * Radii of center points of voxels.
     */
    private double[] voxelRadii;
    private int nILatitude;
    private int nILongitude;
    private Map<HorizontalPosition, Integer> iLatitudeMap = new HashMap<>();
    private Map<HorizontalPosition, Integer> iLongitudeMap = new HashMap<>();

    /**
     * Created {@link PartialID}s.
     */
    private List<PartialID> refinedPartialIDs = Collections.synchronizedList(new ArrayList<>());

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
            pw.println("##Path of a voxel information file for perturbation points, must be set.");
            pw.println("#voxelPath voxel.inf");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in form \"sta_net\", listed using spaces, must be set.");
            pw.println("#tendObserverNames ");
        }
        System.err.println(outPath + " is created.");
    }

    public PartialsRefiner(Property property) throws IOException {
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
        voxelPath = property.parsePath("voxelPath", null, true, workPath);

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                .collect(Collectors.toSet());
        tendObserverNames = Arrays.stream(property.parseStringArray("tendObserverNames", null)).collect(Collectors.toSet());
    }

    @Override
    public void run() throws IOException {
        // read voxel information
        VoxelInformationFile vif = new VoxelInformationFile(voxelPath);
        voxelRadii = vif.getRadii();
        horizontalPixels = vif.getHorizontalPixels();
        // check iLatitude range
        int[] iLatitudes = horizontalPixels.stream().mapToInt(HorizontalPixel::getILatitude).toArray();
        int minILatitude = Arrays.stream(iLatitudes).min().getAsInt();
        int maxILatitude = Arrays.stream(iLatitudes).max().getAsInt();
        nILatitude = maxILatitude - minILatitude + 1;
        // check iLongitude range
        int[] iLongitudes = horizontalPixels.stream().mapToInt(HorizontalPixel::getILongitude).toArray();
        int minILongitude = Arrays.stream(iLongitudes).min().getAsInt();
        int maxILongitude = Arrays.stream(iLongitudes).max().getAsInt();
        nILongitude = maxILongitude - minILongitude + 1;
        // create mapping from position to iLatitude and iLongitude
        // The ranges of iLatitude and iLongitude are shifted so that the first index is 0.
        for (HorizontalPixel pixel : horizontalPixels) {
            iLatitudeMap.put(pixel.getPosition(), pixel.getILatitude() - minILatitude);
            iLongitudeMap.put(pixel.getPosition(), pixel.getILongitude() - minILongitude);
        }

        // read partials
        Set<PartialID> partialIDs = PartialIDFile.read(partialPath, true).stream().filter(id ->
                components.contains(id.getSacComponent())
                && tendEvents.contains(id.getGlobalCMTID())
                && tendObserverNames.contains(id.getObserver().toString()))
                .collect(Collectors.toSet());
        Set<DataEntry> dataEntries = partialIDs.stream().map(PartialID::toDataEntry).collect(Collectors.toSet());

        // process for each data entry, time window, variable, and radius
        int num = 0;
        for (DataEntry dataEntry : dataEntries) {
            Set<PartialID> idsForEntry = partialIDs.stream().filter(id -> id.toDataEntry().equals(dataEntry)).collect(Collectors.toSet());
            Set<TimeWindow> timeWindows = idsForEntry.stream().map(id -> id.toTimeWindow()).collect(Collectors.toSet());

            System.err.println("Entry: " + dataEntry);

            for (TimeWindow timeWindow : timeWindows) {
                Set<PartialID> idsForWindow = idsForEntry.stream().filter(id -> id.toTimeWindow().equals(timeWindow)).collect(Collectors.toSet());
                Set<VariableType> variables = idsForWindow.stream().map(PartialID::getVariableType).collect(Collectors.toSet());

                System.err.println(" Window: " + timeWindow);

                for (VariableType variable : variables) {
                    for (double radius : voxelRadii) {
                        Set<PartialID> useIDs = partialIDs.stream().filter(id ->
                                id.getVariableType().equals(variable)
                                && Precision.equals(id.getVoxelPosition().getR(), radius, FullPosition.RADIUS_EPSILON))
                                .collect(Collectors.toSet());
                        if (useIDs.size() == 0) continue;

                        System.err.println("  Variable, radius: " + variable + " " + radius);

                        process(useIDs);
                        num++;
                    }
                }
            }
        }

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "partial", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        PartialIDFile.write(refinedPartialIDs, outPath);
    }

    private void process(Set<PartialID> ids) throws IOException {
        if (ids.size() == 0) {
            return;
        }

        // arrange partialIDs into array based on iLatitude and iLongitude
        PartialID[][] orderedIDs = new PartialID[nILatitude][nILongitude];
        for (PartialID id : ids) {
            HorizontalPosition position = id.getVoxelPosition().toHorizontalPosition();
            orderedIDs[iLatitudeMap.get(position)][iLongitudeMap.get(position)] = id;
        }

        // process for each pixel
        for (int i = 1; i < nILatitude - 1; i++) {
            for (int j = 1; j < nILongitude - 1; j++) {

                // make sure all 9 adjacent pixels have partials
                boolean computable = true;
                for (int i2 = -1; i2 <= 1; i2++) {
                    for (int j2 = -1; j2 <= 1; j2++) {
                        if (orderedIDs[i + i2][j + j2] == null) computable = false;
                    }
                }
                if (computable == false) continue;

                // get base partial
                PartialID baseID = orderedIDs[i][j];
                Trace baseTrace = baseID.toTrace();
                Trace baseTraceCut = cutFirstPeakWindowTrace(baseTrace);
                double samplingHz = baseID.getSamplingHz();

                // compute shift for adjacent partials
                double[][] shifts = new double[3][3];
                double[][] ampRatios = new double[3][3];
                for (int i2 = -1; i2 <= 1; i2++) {
                    for (int j2 = -1; j2 <= 1; j2++) {
                        PartialID id = orderedIDs[i + i2][j + j2];

                        double[] shiftResults = baseTraceCut.findBestShift(cutFirstPeakWindowTrace(id.toTrace()), true, true, samplingHz);
                        // Here, shift is the amount to move the x axis of baseTrace to the left (data points to the right) to fit this trace.
                        shifts[i2 + 1][j2 + 1] = -shiftResults[0];
                        ampRatios[i2 + 1][j2 + 1] = shiftResults[2];
                    }
                }

                // interpolate at nxn points in range (-0.5:0.5, -0.5:0.5)
                int n = 5;
                shifts = interpolateBiquadratic(n, shifts);
                ampRatios = interpolateBiquadratic(n, ampRatios);

                // sum up the nxn traces applying shift and ampRatio
                Trace sumTrace = null;
                for (int i2 = 0; i2 < n; i2++) {
                    for (int j2 = 0; j2 < n; j2++) {
                        // Here, the data points of baseTrace is moved to the right for positive shift.
                        int nShift = (int) Math.round(shifts[i2][j2] * samplingHz);
                        Trace pseudoTrace = baseTrace.shiftYInXDirection(nShift).multiply(ampRatios[i2][j2]);
                        sumTrace = (sumTrace == null) ? pseudoTrace : sumTrace.add(pseudoTrace);
                    }
                }
                // divide by number to get average
                sumTrace = sumTrace.multiply(1.0 / n / n);

                refinedPartialIDs.add(baseID.withData(sumTrace.getY()));
            }
        }
    }

    /**
     * Given 9 values at x=-1, 0, 1 and y=-1, 0, 1, conduct biquadratic interpolation to get values at nxn points in range (-0.5:0.5, -0.5:0.5).
     * @param n (int) Number of points to interpolate at in each direction.
     * @param input (3x3 Array of double) Values at 9 input points.
     * @return (nxn Array of double) Interpolated values.
     */
    private double[][] interpolateBiquadratic(int n, double[][] input) {
        // interpolate at 3 longitudes
        double interpolated[][] = new double[3][n];  // [lon][lat]
        for (int j = 0; j < 3; j++) {
            interpolated[j] = interpolateQuadratic(n, input[0][j], input[1][j], input[2][j]);
        }

        // interpolate at n latitudes
        double results[][] = new double[n][n];  // [lat][lon]
        for (int i = 0; i < n; i++) {
            results[i] = interpolateQuadratic(n, interpolated[0][i], interpolated[1][i], interpolated[2][i]);
        }
        return results;
    }

    /**
     * Given values at x=-1, 0, 1, conduct quadratic interpolation to get values at n points in range (-0.5:0.5).
     * @param n (int) Number of points to interpolate at.
     * @param valN (double) Value at x=-1.
     * @param val0 (double) Value at x=0.
     * @param valP (double) Value at x=1.
     * @return (size-n Array of double) Interpolated values.
     */
    private double[] interpolateQuadratic(int n, double valN, double val0, double valP) {
       // find coefficients of ax^2+bx+c
       double a = (valN + valP) / 2 - val0;
       double b = (valP - valN) / 2;
       double c = val0;

       // interpolate at n points in (-0.5:0.5)
       double[] results = new double[n];
       for (int i = 0; i < n; i++) {
           double x = -0.5 + (i + 0.5) / n;
           results[i] = a*x*x + b*x + c;
       }
       return results;
   }

    private Trace cutFirstPeakWindowTrace(Trace trace) {
        // get indices of peaks
        int[] indicesOfPeak = trace.getIndicesOfPeak();
        // max absolute value
        double max = Math.abs(trace.getYAt(indicesOfPeak[0]));
        // find index of first peak that exceeds 0.9*max
        int firstPeakIndex = indicesOfPeak[0];
        for (int i = 1; i < indicesOfPeak.length; i++) {
            if (Math.abs(trace.getYAt(indicesOfPeak[i])) > 0.9 * max && indicesOfPeak[i] < firstPeakIndex) firstPeakIndex = indicesOfPeak[i];
        }
        // return Trace in window that includes first peak
        double peakX = trace.getXAt(firstPeakIndex);
        return trace.cutWindow(peakX - halfWindowLength, peakX + halfWindowLength);
    }

}
