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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Operation to make partial derivative waveforms more accurate for each voxel.
 * This conducts a pseudo-integration of partial derivative waveforms across the voxel volume.
 *
 * @author otsuru
 * @since 2025/8/18
 */
public class PartialsRefiner extends Operation {

    private static final double halfWindowLength = 20;
    private static final double USABLE_AMP_RATIO_THRESHOLD = 0.2;
    private static final double PEAK_AMP_RATIO_THRESHOLD = 0.9;
    private static final int N_INTERPOLATE = 5;

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
//
//    /**
//     * Events to work for.
//     */
//    private Set<GlobalCMTID> tendEvents;
//    /**
//     * Names of observers to work for, in the form "net_sta".
//     */
//    private Set<String> tendObserverNames;

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
     * Number of processed parameters.
     */
    private AtomicInteger nProcessedEntry = new AtomicInteger();

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
//            pw.println("##GlobalCMTIDs of events to work for, listed using spaces, must be set.");
//            pw.println("#tendEvents ");
//            pw.println("##Observers to work for, in form \"sta_net\", listed using spaces, must be set.");
//            pw.println("#tendObserverNames ");
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

//        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
//                .collect(Collectors.toSet());
//        tendObserverNames = Arrays.stream(property.parseStringArray("tendObserverNames", null)).collect(Collectors.toSet());
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
//                        components.contains(id.getSacComponent())
//                         && tendEvents.contains(id.getGlobalCMTID())
//                         && tendObserverNames.contains(id.getObserver().toString()))
                components.contains(id.getSacComponent()))
                .collect(Collectors.toSet());
        Set<DataEntry> dataEntries = partialIDs.stream().map(PartialID::toDataEntry).collect(Collectors.toSet());

        // work for each data entry
        ExecutorService es = ThreadAid.createFixedThreadPool();
        int nEntry = dataEntries.size();
        for (DataEntry dataEntry : dataEntries) {
            es.execute(process(dataEntry, partialIDs));
        }
        es.shutdown();
        System.err.println("Refining partials ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + nProcessedEntry + " of " + nEntry + " data entries done.");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all data entries.");

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "partialRefined", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        PartialIDFile.write(refinedPartialIDs, outPath);
    }

    private Runnable process(DataEntry dataEntry, Set<PartialID> partialIDs) {
        return () -> {
            // process for each data entry, time window, variable, and radius
            try {
                Set<PartialID> idsForEntry = partialIDs.stream().filter(id -> id.toDataEntry().equals(dataEntry)).collect(Collectors.toSet());
                Set<TimeWindow> timeWindows = idsForEntry.stream().map(id -> id.toTimeWindow()).collect(Collectors.toSet());

                for (TimeWindow timeWindow : timeWindows) {
                    Set<PartialID> idsForWindow = idsForEntry.stream().filter(id -> id.toTimeWindow().equals(timeWindow)).collect(Collectors.toSet());
                    Set<VariableType> variables = idsForWindow.stream().map(PartialID::getVariableType).collect(Collectors.toSet());

                    for (VariableType variable : variables) {
                        for (double radius : voxelRadii) {
                            Set<PartialID> useIDs = idsForWindow.stream().filter(id ->
                                    id.getVariableType().equals(variable)
                                    && Precision.equals(id.getVoxelPosition().getR(), radius, FullPosition.RADIUS_EPSILON))
                                    .collect(Collectors.toSet());
                            if (useIDs.size() == 0) continue;

                            processLayer(useIDs);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("!!! Error on " + dataEntry);
                e.printStackTrace();
            } finally {
                nProcessedEntry.incrementAndGet();
            }
        };
    }

    private void processLayer(Set<PartialID> ids) throws IOException {
        if (ids.size() == 0) {
            return;
        }

        // find maximum amplitude of all partials
        double maxAmp = ids.stream().mapToDouble(id -> id.toTrace().getYVector().getLInfNorm()).max().getAsDouble();

        // arrange partialIDs into array based on iLatitude and iLongitude
        PartialID[][] orderedIDs = new PartialID[nILatitude][nILongitude];
        for (PartialID id : ids) {

            // partials that have small amplitude (the peak of the waveform is out of the time window) should not be used for cross-correlation
            // thus, it is used as is without refining
            if (id.toTrace().getYVector().getLInfNorm() < maxAmp * USABLE_AMP_RATIO_THRESHOLD) {
//                refinedPartialIDs.add(id);
                refinedPartialIDs.add(id.withData(id.toTrace().withZeroes().getY()));

            } else {
                HorizontalPosition position = id.getVoxelPosition().toHorizontalPosition();

                // something is wrong if multiple partials exist for the same voxel
                if (orderedIDs[iLatitudeMap.get(position)][iLongitudeMap.get(position)] != null)
                    throw new IllegalStateException("Partial already exists for " + id);

                orderedIDs[iLatitudeMap.get(position)][iLongitudeMap.get(position)] = id;
            }
        }

        // process for each pixel
        for (int i = 0; i < nILatitude; i++) {
            for (int j = 0; j < nILongitude; j++) {

                // make sure this pixel has partials
                if (orderedIDs[i][j] == null) continue;

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

                        // when out of range, set NaN
                        if (i + i2 < 0 || nILatitude <= i + i2 || j + j2 < 0 || nILongitude <= j + j2) {
                            shifts[i2 + 1][j2 + 1] = Double.NaN;
                            ampRatios[i2 + 1][j2 + 1] = Double.NaN;
                            continue;
                        }

                        PartialID id = orderedIDs[i + i2][j + j2];

                        // when partialID does not exist, set NaN
                        if (id == null) {
                            shifts[i2 + 1][j2 + 1] = Double.NaN;
                            ampRatios[i2 + 1][j2 + 1] = Double.NaN;
                            continue;
                        }

                        // at center, shift is 0 and amp ratio is 1
                        if (i2 == 0 && j2 == 0) {
                            shifts[i2 + 1][j2 + 1] = 0.0;
                            ampRatios[i2 + 1][j2 + 1] = 1.0;
                            continue;
                        }

                        double[] shiftResults = baseTraceCut.findBestShift(cutFirstPeakWindowTrace(id.toTrace()), true, true, samplingHz);
                        // Here, shift is the amount to move the x axis of baseTrace to the left (data points to the right) to fit this trace.
                        shifts[i2 + 1][j2 + 1] = -shiftResults[0];
                        ampRatios[i2 + 1][j2 + 1] = shiftResults[2];
                    }
                }

                // interpolate at nxn points in range (-0.5:0.5, -0.5:0.5)
                int n = N_INTERPOLATE;
                shifts = interpolateBiquadratic(n, shifts);
                ampRatios = interpolateBiquadratic(n, ampRatios);

                // if interpolation failed (e.g. voxels at edge of region with usable partials), use it as is without refining
                if (Double.isNaN(shifts[0][0]) || Double.isNaN(ampRatios[0][0])) {
//                    refinedPartialIDs.add(baseID);
                    refinedPartialIDs.add(baseID.withData(baseID.toTrace().withZeroes().getY()));
                    continue;
                }

                // sum up the nxn traces applying shift and ampRatio
                Trace sumTrace = null;
                int minNegativeNShift = 0;
                for (int i2 = 0; i2 < n; i2++) {
                    for (int j2 = 0; j2 < n; j2++) {
                        if (Double.isNaN(shifts[i2][j2]) || Double.isNaN(ampRatios[i2][j2])) {
                            throw new IllegalArgumentException("Cannot interpolate at " + baseID.getVoxelPosition().toHorizontalPosition());
                        }
                        // Here, the data points of baseTrace is moved to the right for positive shift.
                        int nShift = (int) Math.round(shifts[i2][j2] * samplingHz);
                        Trace pseudoTrace = baseTrace.shiftYInXDirection(nShift).multiply(ampRatios[i2][j2]);
                        sumTrace = (sumTrace == null) ? pseudoTrace : sumTrace.add(pseudoTrace);
                        // keep record of largest negative shift
                        if (nShift < minNegativeNShift) minNegativeNShift = nShift;
                    }
                }
                // divide by number to get average
                sumTrace = sumTrace.multiply(1.0 / n / n);

                // The end of the waveform can be jagged when not added at all points. Thus, set that part to 0.
                double[] yArray = sumTrace.getY();
                if (yArray.length + minNegativeNShift < 0) {
                    System.err.println(" " + baseID.toDataEntry() + "  " + baseID.getVoxelPosition());
                    for (int i2 = 0; i2 < n; i2++) {
                        for (int j2 = 0; j2 < n; j2++) {
                            System.err.println("  " + shifts[i2][j2]);
                        }
                    }
                }
                for (int s = yArray.length + minNegativeNShift; s < yArray.length; s++) {
                    yArray[s] = 0.0;
                }

                refinedPartialIDs.add(baseID.withData(yArray));
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
     * If values at only two adjacent points are given, linear interpolation is conducted.
     * Otherwise, returns NaN.
     * @param n (int) Number of points to interpolate at.
     * @param valN (double) Value at x=-1. Can be NaN when undefined.
     * @param val0 (double) Value at x=0.
     * @param valP (double) Value at x=1. Can be NaN when undefined.
     * @return (size-n Array of double) Interpolated values. NaN when interpolation cannot be conducted.
     */
    private double[] interpolateQuadratic(int n, double valN, double val0, double valP) {
        double a, b, c;
        if (!Double.isNaN(valN) && !Double.isNaN(val0) && !Double.isNaN(valP)) {
            // find coefficients of ax^2+bx+c
            a = (valN + valP) / 2 - val0;
            b = (valP - valN) / 2;
            c = val0;
        } else if (!Double.isNaN(valN) && !Double.isNaN(val0)) {
            // find coefficients of bx+c
            a = 0.0;
            b = (val0 - valN) / 2;
            c = val0;
        } else if (!Double.isNaN(val0) && !Double.isNaN(valP)) {
            // find coefficients of bx+c
            a = 0.0;
            b = (valP - val0) / 2;
            c = val0;
        } else {
            a = Double.NaN;
            b = Double.NaN;
            c = Double.NaN;
        }

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
            if (Math.abs(trace.getYAt(indicesOfPeak[i])) > PEAK_AMP_RATIO_THRESHOLD * max && indicesOfPeak[i] < firstPeakIndex)
                firstPeakIndex = indicesOfPeak[i];
        }
        // return Trace in window that includes first peak
        double peakX = trace.getXAt(firstPeakIndex);
        return trace.cutWindow(peakX - halfWindowLength, peakX + halfWindowLength);
    }

}
