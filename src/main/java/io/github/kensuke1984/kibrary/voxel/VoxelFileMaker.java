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

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Operation to create a {@link VoxelInformationFile} by deciding the position range of voxels manually.
 * <p>
 * Use {@link VoxelLayoutDesigner} to decide the positions of voxels based on a dataset.
 *
 * @author otsuru
 * @since 2023/6/5
 */
public class VoxelFileMaker extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

    private double lowerLatitude;
    private double upperLatitude;
    private double lowerLongitude;
    private double upperLongitude;

    private double dLatitudeKm;
    private double dLatitudeDeg;
    private boolean setLatitudeByKm;
    private double latitudeOffset;

    private double dLongitudeKm;
    private double dLongitudeDeg;
    private boolean setLongitudeByKm;
    private double longitudeOffset;

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
            pw.println("##########Parameters for the CENTER positions of voxels to create.");
            pw.println("##(double) Lower limit of latitude [deg]; [-90:upperLatitude). (0)");
            pw.println("#lowerLatitude ");
            pw.println("##(double) Upper limit of latitude [deg]; (lowerLatitude:90]. (0)");
            pw.println("#upperLatitude ");
            pw.println("##(double) Lower limit of longitude [deg]; [-180:upperLongitude). (0)");
            pw.println("#lowerLongitude ");
            pw.println("##(double) Upper limit of longitude [deg]; (lowerLongitude:360]. (180)");
            pw.println("#upperLongitude ");
            pw.println("##(double) Latitude spacing [km]; (0:). If this is unset, the following dLatitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees.");
            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg]; (0:). (5)");
            pw.println("#dLatitudeDeg ");
            pw.println("##(double) Offset of voxel-CENTER latitude [deg]; [0:). (0)");
            pw.println("#latitudeOffset ");
            pw.println("##(double) Longitude spacing [km]; (0:). If this is unset, the following dLongitudeDeg will be used.");
            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg]; (0:). (5)");
            pw.println("#dLongitudeDeg ");
            pw.println("##(double) Offset of voxel-CENTER longitude [deg], when dLongitudeDeg is used; [0:). (0)");
            pw.println("#longitudeOffset ");
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

    public VoxelFileMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);

        lowerLatitude = property.parseDouble("lowerLatitude", "0");
        upperLatitude = property.parseDouble("upperLatitude", "0");
        LinearRange.checkValidity("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        lowerLongitude = property.parseDouble("lowerLongitude", "0");
        upperLongitude = property.parseDouble("upperLongitude", "180");
        LinearRange.checkValidity("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);

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
        latitudeOffset = property.parseDouble("latitudeOffset", "0");
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
            longitudeOffset = property.parseDouble("longitudeOffset", "0");
            if (longitudeOffset < 0.0)
                throw new IllegalArgumentException("longitudeOffset must be non-negative.");
            setLongitudeByKm = false;
        }

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

        // decide horizontal distribution of voxels
        List<HorizontalPixel> horizontalPixels = designHorizontalPixels();

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

    private List<HorizontalPixel> designHorizontalPixels() {
        List<HorizontalPixel> horizontalPixels = new ArrayList<>();

        // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
        double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / centerRadius) : dLatitudeDeg;

        int lowerLatitudeIndex = getLowerIndex(lowerLatitude, dLatitude, latitudeOffset);
        int upperLatitudeIndex = getUpperIndex(upperLatitude, dLatitude, latitudeOffset);

        for (int i = lowerLatitudeIndex; i <= upperLatitudeIndex; i++) {
            // compute center latitude of the voxel row
            double latitude = i * dLatitude + latitudeOffset;

            if (setLongitudeByKm) {
                // voxel points are aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm
                // the min and max voxel-centers are set close to the longitude bounds
                double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
                double dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
                int nLongitude = (int) MathAid.ceil((upperLongitude - lowerLongitude) / dLongitudeForRow);
                double centerLongitude = (lowerLongitude + upperLongitude) / 2;

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
                // all longitudes are set on (n * dLongitudeDeg + longitudeOffset)
                // voxel longitudes are set so that all voxel-centers are included in range
                int lowerLongitudeIndex = getLowerIndex(lowerLongitude, dLongitudeDeg, longitudeOffset);
                int upperLongitudeIndex = getUpperIndex(upperLongitude, dLongitudeDeg, longitudeOffset);
                for (int j = lowerLongitudeIndex; j <= upperLongitudeIndex; j++) {
                    // compute center longitude of the voxel
                    double longitude = j * dLongitudeDeg + longitudeOffset;

                    // add horizontal pixel to list
                    horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitudeDeg));
                }
            }

        }
        return horizontalPixels;
    }

    private static int getLowerIndex(double lowerValue, double interval, double offset) {
        return (int) MathAid.ceil((lowerValue - offset) / interval);
    }
    private static int getUpperIndex(double upperValue, double interval, double offset) {
        return (int) MathAid.floor((upperValue - offset) / interval);
    }

}
