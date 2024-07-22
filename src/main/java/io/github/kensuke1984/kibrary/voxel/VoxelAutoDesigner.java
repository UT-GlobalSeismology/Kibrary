package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.TauPPierceWrapper;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.data.Raypath;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Operation that decides horizontal distribution of voxels based on raypath lines.
 * <p>
 * Given a set of {@link DataEntry}s, raypath segments that run through a layer of specified radius range are computed.
 * Voxels are distributed so that they cover the region these raypath segments sample.
 * Voxels are set along latitude lines, with spacing of either equal longitude angle [deg] or equal distance [km].
 * When using distance [km], it is converted to degrees at the (roughly) median radius of the target region.
 * <p>
 * Voxel radii will be decided separately based on given settings. Voxel radii will be set at the center of each radius range.
 * <p>
 * Use {@link VoxelManualDesigner} to decide the position range of voxels manually.
 *
 * @author otsuru
 * @since 2022/2/11
 */
public class VoxelAutoDesigner extends Operation {

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
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * Path of the input data entry list file.
     */
    private Path dataEntryPath;
    private String[] piercePhases;
    private double lowerPierceRadius;
    private double upperPierceRadius;
    private String structureName;

    private double dLatitudeKm;
    private double dLatitudeDeg;
    private boolean setLatitudeByKm;
    private double latitudeOffset;

    private double dLongitudeKm;
    private double dLongitudeDeg;
    private boolean setLongitudeByKm;
    private double longitudeOffset;
    private boolean crossDateLine;

    private double[] borderRadii;
    private double lowerRadius;
    private double upperRadius;
    private double dRadius;

    /**
     * The (roughly) median radius of target region.
     */
    private double centerRadius;

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
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##########Information to design horzontal positions of voxels.");
            pw.println("##Path of a data entry list file, must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##Phases to compute pierce points for, listed using spaces. (ScS)");
            pw.println("#piercePhases ");
            pw.println("##(double) Lower radius to compute pierce points for [km]. (3480)");
            pw.println("#lowerPierceRadius ");
            pw.println("##(double) Upper radius to compute pierce points for [km]. (3880)");
            pw.println("#upperPierceRadius ");
            pw.println("##(String) Name of structure to use for calculating pierce points. (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Latitude spacing [km]; (0:). If unset, the following dLatitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees.");
            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg]; (0:). (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(double) Offset of voxel-center latitude [deg]; [0:). (0)");
            pw.println("#latitudeOffset ");
            pw.println("##(double) Longitude spacing [km]; (0:). If unset, the following dLongitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg]; (0:). (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Offset of voxel-center longitude [deg]. (0)");
            pw.println("#longitudeOffset ");
            pw.println("##(boolean) Whether to use longitude range [0:360) instead of [-180:180). (false)");
            pw.println("#crossDateLine true");
            pw.println("##########Parameters for the BORDER radii of voxels to create.");
            pw.println("##(double[]) Radii of layer borders, listed using spaces [km]; [0:).");
            pw.println("##  If unset, the subsequent parameters are used.");
            pw.println("#borderRadii 3480 3530 3580 3630 3680 3730 3780 3830 3880");
            pw.println("##(double) Lower limit of radius [km]; [0:upperRadius). (3480)");
            pw.println("#lowerRadius ");
            pw.println("##(double) Upper limit of radius [km]; (lowerRadius:). (3880)");
            pw.println("#upperRadius ");
            pw.println("##(double) Radius spacing [km]; (0:). (50)");
            pw.println("#dRadius ");
        }
        System.err.println(outPath + " is created.");
    }

    public VoxelAutoDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        piercePhases = property.parseStringArray("piercePhases", "ScS");
        lowerPierceRadius = property.parseDouble("lowerPierceRadius", "3480");
        upperPierceRadius = property.parseDouble("upperPierceRadius", "3880");
        structureName = property.parseString("structureName", "prem");

        if (property.containsKey("dLatitudeKm")) {
            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
            if (dLatitudeKm <= 0.0) throw new IllegalArgumentException("dLatitudeKm must be positive.");
            setLatitudeByKm = true;
        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0.0) throw new IllegalArgumentException("dLatitudeDeg must be positive.");
            setLatitudeByKm = false;
        }
        latitudeOffset = property.parseDouble("latitudeOffset", "0");
        if (latitudeOffset < 0.0) throw new IllegalArgumentException("latitudeOffset must be non-negative.");

        if (property.containsKey("dLongitudeKm")) {
            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
            if (dLongitudeKm <= 0.0) throw new IllegalArgumentException("dLongitudeKm must be positive.");
            setLongitudeByKm = true;
        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0.0) throw new IllegalArgumentException("dLongitudeDeg must be positive.");
            setLongitudeByKm = false;
        }
        longitudeOffset = property.parseDouble("longitudeOffset", "0");
        crossDateLine = property.parseBoolean("crossDateLine", "false");

        if (property.containsKey("borderRadii")) {
            borderRadii = Arrays.stream(property.parseDoubleArray("borderRadii", null))
                    .sorted().toArray();
            if (borderRadii.length < 2) throw new IllegalArgumentException("There must be at least 2 values for borderRadii.");
        } else {
            lowerRadius = property.parseDouble("lowerRadius", "3480");
            upperRadius = property.parseDouble("upperRadius", "3880");
            LinearRange.checkValidity("Radius", lowerRadius, upperRadius, 0.0);

            dRadius = property.parseDouble("dRadius", "50");
            if (dRadius <= 0.0)
                throw new IllegalArgumentException("dRadius must be non-negative.");
        }
    }

    @Override
    public void run() throws IOException {
        centerRadius = (borderRadii != null) ? borderRadii[borderRadii.length / 2] : (lowerRadius + upperRadius) / 2.0;
        Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);

        // compute pierce points
        TauPPierceWrapper pierceTool = null;
        try {
            double[] pierceRadii = {lowerPierceRadius, upperPierceRadius};
            pierceTool = new TauPPierceWrapper(structureName, piercePhases, pierceRadii);
            pierceTool.compute(entrySet);
        } catch (TauModelException e) {
            throw new RuntimeException(e);
        }

        // collect all raypath segments that run through the target region
        List<Raypath> allRaypaths = pierceTool.getAll();
        List<Raypath> insideSegments = allRaypaths.stream()
                .flatMap(raypath -> raypath.clipInsideLayer(lowerPierceRadius, upperPierceRadius).stream())
                .collect(Collectors.toList());

        // decide horizontal distribution of voxels
        List<HorizontalPixel> horizontalPixels = designHorizontalPixels(insideSegments);

        // set voxel layer information
        double[] layerThicknesses;
        double[] layerRadii;
        if (borderRadii != null) {
            layerThicknesses = new double[borderRadii.length - 1];
            layerRadii = new double[borderRadii.length - 1];
            for (int i = 0; i < borderRadii.length - 1; i++) {
                layerThicknesses[i] = borderRadii[i + 1] - borderRadii[i];
                layerRadii[i] = (borderRadii[i] + borderRadii[i + 1]) / 2.0;
            }
        } else {
            int nRadius = (int) MathAid.floor((upperRadius - lowerRadius) / dRadius);
            layerThicknesses = new double[nRadius];
            layerRadii = new double[nRadius];
            for (int i = 0; i < nRadius; i++) {
                layerThicknesses[i] = dRadius;
                layerRadii[i] = lowerRadius + (i + 0.5) * dRadius;
            }
        }

        // output
        Path outputPath = DatasetAid.generateOutputFilePath(workPath, "voxel", fileTag, appendFileDate, null, ".inf");
        VoxelInformationFile.write(layerThicknesses, layerRadii, horizontalPixels, outputPath);
    }

    private List<HorizontalPixel> designHorizontalPixels(List<Raypath> insideSegments) {

        // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
        double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / centerRadius) : dLatitudeDeg;

        //~decide longitude ranges for each (co)latitude band
        // decide the number of colatitude bands that sample points can be categorized into
        // Colatitude is used because its range is [0:180] and is easier to decide intervals.
        // Note that this is not necessarily the same as the number of voxel-center points that appear on the sphere.
        // Also, when latitudeOffset > dLatitude/2, intervals for the extra part hidden beyond the pole is also counted.
        int nLatitude = (int) Math.round((180.0 + latitudeOffset) / dLatitude) + 1;
        double minLongitudes[] = new double[nLatitude];
        double maxLongitudes[] = new double[nLatitude];
        for (int i = 0; i < nLatitude; i++) {
            minLongitudes[i] = Double.MAX_VALUE;
            maxLongitudes[i] = -Double.MAX_VALUE;
        }
        // the approximate interval to sample points along raypath segments
        double dDistanceRef = dLatitude / 2.0;
        // work for each raypath segment
        for (Raypath segment : insideSegments) {
            FullPosition startPosition = segment.getSource();
            HorizontalPosition endPosition = segment.getReceiver();
            double segmentDistanceDeg = startPosition.computeEpicentralDistanceDeg(endPosition);
            double azimuthDeg = startPosition.computeAzimuthDeg(endPosition);

            // distribute sample points equally along the raypath segment
            // Note: Math.ceil() is used to prevent 0 division in the next line.
            int nSamplePointInterval = (int) Math.ceil(segmentDistanceDeg / dDistanceRef);
            double dDistanceDeg = segmentDistanceDeg / nSamplePointInterval;

            // sample points along the raypath segment, including startPosition and endPosition
            for (int i = 0; i <= nSamplePointInterval; i++) {
                double sampleDistanceDeg = i * dDistanceDeg;
                HorizontalPosition samplePosition = startPosition.pointAlongAzimuth(azimuthDeg, sampleDistanceDeg);
                // get latitude and longitude of sample position
                double sampleColatitude = switchLatitudeColatitude(samplePosition.getLatitude());
                double sampleLongitude = samplePosition.getLongitude();
                if (crossDateLine && sampleLongitude < 0.0) sampleLongitude += 360.0;
                // decide which colatitude band the sample point is in
                // latitudeOffset moves latitude bands to positive side, so moves colatitude bands to negative side.
                // This way, sampleInterval will never become negative even after shifting.
                int colatitudeIndex = (int) Math.round((sampleColatitude + latitudeOffset) / dLatitude);
                // reflect this sample point on longitude ranges
                if (sampleLongitude < minLongitudes[colatitudeIndex]) minLongitudes[colatitudeIndex] = sampleLongitude;
                if (sampleLongitude > maxLongitudes[colatitudeIndex]) maxLongitudes[colatitudeIndex] = sampleLongitude;
            }
        }

        //~decide the longitude at which to align voxels
        double baseLongitude;
        if (setLongitudeByKm) {
            double minLongitude = Arrays.stream(minLongitudes).min().getAsDouble();
            double maxLongitude = Arrays.stream(maxLongitudes).max().getAsDouble();
            baseLongitude = (minLongitude + maxLongitude) / 2 + longitudeOffset;
        } else {
            baseLongitude = longitudeOffset;
        }

        //~decide horizontal positions of voxels
        List<HorizontalPixel> horizontalPixels = new ArrayList<>();
        for (int i = 0; i < nLatitude; i++) {
            // compute center latitude of the voxel row
            double latitude = switchLatitudeColatitude(i * dLatitude) + latitudeOffset;
            // when using latitudeOffset, the center latitude of first or last interval may be out of legal range; in that case, skip
            if (latitude <= -90.0 || 90.0 <= latitude) continue;
            // if no raypath segments entered this colatitude band, skip
            if (minLongitudes[i] > maxLongitudes[i]) continue;

            // decide longitude interval for current latitude
            double dLongitudeForRow;
            if (setLongitudeByKm) {
                // Voxel points will be aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm.
                double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
                dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
            } else {
                // All longitudes will be set on (j * dLongitudeDeg + longitudeOffset).
                dLongitudeForRow = dLongitudeDeg;
            }

            // the min and max longitudes are set so that all sample points are included
            double minLongitude = Math.round((minLongitudes[i] - baseLongitude) / dLongitudeForRow) * dLongitudeForRow + baseLongitude;
            double maxLongitude = Math.round((maxLongitudes[i] - baseLongitude) / dLongitudeForRow) * dLongitudeForRow + baseLongitude;
            int nLongitude = (int) ((maxLongitude - minLongitude) / dLongitudeForRow) + 1;
            for (int j = 0; j < nLongitude; j++) {
                // center longitude of each horizontal pixel
                double longitude = minLongitude + j * dLongitudeForRow;
                // add horizontal pixel to list
                horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitudeForRow));
            }
        }

        return horizontalPixels;
    }

    /**
     * Switch between latitude and colatitude.
     * @param latitude (double) Latitude or colatitude to convert.
     * @return (double) Converted colatitude or latitude.
     */
    private static double switchLatitudeColatitude(double latitude) {
        return 90.0 - latitude;
    }

}
