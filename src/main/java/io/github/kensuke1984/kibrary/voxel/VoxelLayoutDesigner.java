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
import io.github.kensuke1984.kibrary.util.GadgetAid;
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
 * Radii of voxel boundaries must be decided manually. Voxel radii will be set at the center of each radius range.
 * <p>
 * Use {@link VoxelFileMaker} to decide the position range of voxels manually.
 *
 * @author otsuru
 * @since 2022/2/11
 */
public class VoxelLayoutDesigner extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

    /**
     * Path of the input data entry list file
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
     * (roughly) median radius of target region
     */
    private double centerRadius;

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
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
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
            pw.println("##(double) Offset of boundary latitude [deg]; [0:). (2.5)");
            pw.println("#latitudeOffset ");
            pw.println("##(double) Longitude spacing [km]; (0:). If this is unset, the following dLongitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg]; (0:). (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Offset of boundary longitude, when dLongitudeDeg is used [deg]; [0:dLongitudeDeg). (2.5)");
            pw.println("#longitudeOffset ");
            pw.println("##(boolean) Use longitude range [0:360) instead of [-180:180)? (false)");
            pw.println("#crossDateLine ");
            pw.println("##########Parameters for the BORDER radii of voxels to create.");
            pw.println("##(double[]) Radii of layer borders, listed using spaces [km]; [0:).");
            pw.println("##  If unset, the following parameters are used.");
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

    public VoxelLayoutDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        piercePhases = property.parseStringArray("piercePhases", "ScS");
        lowerPierceRadius = property.parseDouble("lowerPierceRadius", "3480");
        upperPierceRadius = property.parseDouble("upperPierceRadius", "3880");
        structureName = property.parseString("structureName", "prem");

        if (property.containsKey("dLatitudeKm")) {
            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
            if (dLatitudeKm <= 0.0)
                throw new IllegalArgumentException("dLatitudeKm must be positive.");
            setLatitudeByKm = true;
        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0.0)
                throw new IllegalArgumentException("dLatitudeDeg must be positive.");
            setLatitudeByKm = false;
        }
        latitudeOffset = property.parseDouble("latitudeOffset", "2.5");
        if (latitudeOffset < 0.0)
            throw new IllegalArgumentException("latitudeOffset must be non-negative.");

        if (property.containsKey("dLongitudeKm")) {
            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
            if (dLongitudeKm <= 0.0)
                throw new IllegalArgumentException("dLongitudeKm must be positive.");
            setLongitudeByKm = true;
        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0.0)
                throw new IllegalArgumentException("dLongitudeDeg must be positive.");
            longitudeOffset = property.parseDouble("longitudeOffset", "2.5");
            if (longitudeOffset < 0.0 || dLongitudeDeg <= longitudeOffset)
                throw new IllegalArgumentException("longitudeOffset must be in [0:dLongitudeDeg).");
            setLongitudeByKm = false;
        }
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
            int nRadius = (int) Math.floor((upperRadius - lowerRadius) / dRadius);
            layerThicknesses = new double[nRadius];
            layerRadii = new double[nRadius];
            for (int i = 0; i < nRadius; i++) {
                layerThicknesses[i] = dRadius;
                layerRadii[i] = lowerRadius + (i + 0.5) * dRadius;
            }
        }

        // output
        Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName("voxel", fileTag, GadgetAid.getTemporaryString(), ".inf"));
        VoxelInformationFile.write(layerThicknesses, layerRadii, horizontalPixels, outputPath);
    }

    private List<HorizontalPixel> designHorizontalPixels(List<Raypath> insideSegments) {

        // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
        double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / centerRadius) : dLatitudeDeg;

        //~decide longitude ranges for each (co)latitude
        // decide number of colatitude intervals
        // Colatitude is used because its range is [0:180] and is easier to decide intervals.
        // When latitudeOffset > 0, intervals for that extra part is needed.
        int nLatitude = (int) Math.ceil((180.0 + latitudeOffset) / dLatitude);
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
            int nSamplePointInterval = (int) Math.round(segmentDistanceDeg / dDistanceRef);
            double dDistanceDeg = segmentDistanceDeg / nSamplePointInterval;

            // sample points along the raypath segment, including startPosition and endPosition
            for (int i = 0; i <= nSamplePointInterval; i++) {
                double sampleDistanceDeg = i * dDistanceDeg;
                HorizontalPosition samplePosition = startPosition.pointAlongAzimuth(azimuthDeg, sampleDistanceDeg);
                // get latitude and longitude of sample position
                double sampleColatitude = switchLatitudeColatitude(samplePosition.getLatitude());
                double sampleLongitude = samplePosition.getLongitude();
                if (crossDateLine && sampleLongitude < 0.0) sampleLongitude += 360.0;
                // decide which colatitude interval the sample point is in
                // latitudeOffset moves border latitudes to positive side, so moves border colatitudes to negative side.
                // This way, sampleInterval will never become negative.
                int colatitudeIndex = (int) Math.floor((sampleColatitude + latitudeOffset) / dLatitude);
                // reflect this sample point on longitude ranges
                if (sampleLongitude < minLongitudes[colatitudeIndex]) minLongitudes[colatitudeIndex] = sampleLongitude;
                if (sampleLongitude > maxLongitudes[colatitudeIndex]) maxLongitudes[colatitudeIndex] = sampleLongitude;
            }
        }

        //~decide horizontal positions of voxels
        List<HorizontalPixel> horizontalPixels = new ArrayList<>();
        for (int i = 0; i < nLatitude; i++) {
            // compute center latitude of the voxel row
            double latitude = switchLatitudeColatitude((i + 0.5) * dLatitude) + latitudeOffset;
            // when using latitudeOffset, the center latitude of first or last interval may be out of legal range; in that case, skip
            if (latitude <= -90.0 || 90.0 <= latitude) continue;
            // if no raypath segments entered this colatitude interval, skip
            if (minLongitudes[i] > maxLongitudes[i]) continue;

            if (setLongitudeByKm) {
                // voxel points are aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm
                // the min and max borders are set close to the min and max longitudes of sample points
                double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
                double dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
                int nLongitude = (int) Math.round((maxLongitudes[i] - minLongitudes[i]) / dLongitudeForRow);
                double centerLongitude = (minLongitudes[i] + maxLongitudes[i]) / 2.0;

                double startLongitude;
                if (nLongitude % 2 == 0) {
                    // when even number, align evenly on both sides of center longitude
                    startLongitude = centerLongitude - (nLongitude / 2 - 0.5) * dLongitudeForRow;
                } else {
                    // when odd number, set one pixel at center longitude
                    // This is same equation as above but is rewritten for clarity.
                    startLongitude = centerLongitude - (nLongitude - 1) / 2.0 * dLongitudeForRow;
                }
                for (int j = 0; j < nLongitude; j++) {
                    double longitude = startLongitude + j * dLongitudeForRow;
                    // add horizontal pixel to list
                    horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitudeForRow));
                }

            } else {
                // all longitudes are set on ((n + 0.5) * dLongitudeDeg + longitudeOffset)
                // the min and max borders of longitude are set so that all sample points are included
                double minLongitude = Math.floor((minLongitudes[i] - longitudeOffset) / dLongitudeDeg) * dLongitudeDeg + longitudeOffset;
                double maxLongitude = Math.ceil((maxLongitudes[i] - longitudeOffset) / dLongitudeDeg) * dLongitudeDeg + longitudeOffset;
                int nLongitude = (int) ((maxLongitude - minLongitude) / dLongitudeDeg);
                for (int j = 0; j < nLongitude; j++) {
                    // center longitude of each horizontal pixel
                    double longitude = minLongitude + (j + 0.5) * dLongitudeDeg;
                    // add horizontal pixel to list
                    horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitudeDeg));
                }
            }
        }

        return horizontalPixels;
    }

    /**
     * Switch between latitude and colatitude.
     * @param latitude (double) latitude or colatitude to convert
     * @return (double) converted colatitude or latitude
     */
    private static double switchLatitudeColatitude(double latitude) {
        return 90.0 - latitude;
    }

}
