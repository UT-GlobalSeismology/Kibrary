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
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Operation to create a {@link VoxelInformationFile} by deciding the position range of voxels manually.
 * <p>
 * Use {@link VoxelAutoDesigner} to decide the positions of voxels based on a dataset.
 *
 * @author otsuru
 * @since 2023/6/5
 */
public class VoxelManualDesigner extends Operation {

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
            pw.println("##########Parameters for the CENTER positions of voxels to create.");
            pw.println("##(double) Lower limit of latitude [deg]; [-90:upperLatitude). (0)");
            pw.println("#lowerLatitude ");
            pw.println("##(double) Upper limit of latitude [deg]; (lowerLatitude:90]. (0)");
            pw.println("#upperLatitude ");
            pw.println("##(double) Lower limit of longitude [deg]; [-180:upperLongitude). (0)");
            pw.println("#lowerLongitude ");
            pw.println("##(double) Upper limit of longitude [deg]; (lowerLongitude:360]. (180)");
            pw.println("#upperLongitude ");
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

    public VoxelManualDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

        lowerLatitude = property.parseDouble("lowerLatitude", "0");
        upperLatitude = property.parseDouble("upperLatitude", "0");
        LinearRange.checkValidity("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        lowerLongitude = property.parseDouble("lowerLongitude", "0");
        upperLongitude = property.parseDouble("upperLongitude", "180");
        LinearRange.checkValidity("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);

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

    private List<HorizontalPixel> designHorizontalPixels() {

        // when using dLatitudeKm, set dLatitude in degrees using the (roughly) median radius of target region
        double dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / centerRadius) : dLatitudeDeg;

        int lowerLatitudeIndex = getLowerIndex(lowerLatitude, dLatitude, latitudeOffset);
        int upperLatitudeIndex = getUpperIndex(upperLatitude, dLatitude, latitudeOffset);

        //~decide the longitude at which to align voxels
        double baseLongitude;
        if (setLongitudeByKm) {
            baseLongitude = (lowerLongitude + upperLongitude) / 2 + longitudeOffset;
        } else {
            baseLongitude = longitudeOffset;
        }

        //~decide horizontal positions of voxels
        List<HorizontalPixel> horizontalPixels = new ArrayList<>();
        for (int i = lowerLatitudeIndex; i <= upperLatitudeIndex; i++) {
            // compute center latitude of the voxel row
            double latitude = i * dLatitude + latitudeOffset;

            // decide longitude interval for current latitude
            double dLongitudeForRow;
            if (setLongitudeByKm) {
                // Voxel points will be aligned on latitude lines so that their spacing (at the median radius) is dLongitudeKm.
                double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
                dLongitudeForRow = Math.toDegrees(dLongitudeKm / smallCircleRadius);
            } else {
                // All longitudes will be set on (n * dLongitudeDeg + longitudeOffset).
                dLongitudeForRow = dLongitudeDeg;
            }

            // voxel longitudes are set so that all voxel-centers are included in range
            int lowerLongitudeIndex = getLowerIndex(lowerLongitude, dLongitudeForRow, baseLongitude);
            int upperLongitudeIndex = getUpperIndex(upperLongitude, dLongitudeForRow, baseLongitude);
            for (int j = lowerLongitudeIndex; j <= upperLongitudeIndex; j++) {
                // compute center longitude of the voxel
                double longitude = baseLongitude + j * dLongitudeForRow;

                // add horizontal pixel to list
                horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitudeForRow));
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
