package io.github.kensuke1984.kibrary.voxel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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

    private int lowerLatitude;
    private int upperLatitude;
    private int lowerLongitude;
    private int upperLongitude;
    private int lowerRadius;
    private int upperRadius;

//    private double dLatitudeKm;
    private double dLatitudeDeg;
//    private boolean setLatitudeByKm;
//    private double latitudeOffset;

//    private double dLongitudeKm;
    private double dLongitudeDeg;
//    private boolean setLongitudeByKm;
//    private double longitudeOffset;
//    private boolean crossDateLine;

    private double dRadius;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##########Parameters for the center positions of voxels to create.");
            pw.println("##(int) Lower limit of latitude [deg] [-90:upperLatitude) (0)");
            pw.println("#lowerLatitude ");
            pw.println("##(int) Upper limit of latitude [deg] (lowerLatitude:90] (0)");
            pw.println("#upperLatitude ");
            pw.println("##(int) Lower limit of longitude [deg] [-180:upperLongitude) (0)");
            pw.println("#lowerLongitude ");
            pw.println("##(int) Upper limit of longitude [deg] (lowerLongitude:360] (180)");
            pw.println("#upperLongitude ");
            pw.println("##(int) Lower limit of radius [km] [0:upperRadius) (0)");
            pw.println("#lowerRadius ");
            pw.println("##(int) Upper limit of radius [km] (lowerRadius:) (6371)");
            pw.println("#upperRadius ");
//            pw.println("##(double) Latitude spacing [km]. If this is unset, the following dLatitudeDeg will be used.");
//            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees.");
//            pw.println("#dLatitudeKm ");
            pw.println("##(double) Latitude spacing [deg] (5)");
            pw.println("#dLatitudeDeg ");
//            pw.println("##(double) Offset of boundary latitude [deg], must be positive (2.5)");
//            pw.println("#latitudeOffset ");
//            pw.println("##(double) Longitude spacing [km]. If this is unset, the following dLongitudeDeg will be used.");
//            pw.println("##  The (roughly) median radius of target region will be used to convert this to degrees at each latitude.");
//            pw.println("#dLongitudeKm ");
            pw.println("##(double) Longitude spacing [deg] (5)");
            pw.println("#dLongitudeDeg ");
//            pw.println("##(double) Offset of boundary longitude, when dLongitudeDeg is used [deg] [0:dLongitudeDeg) (2.5)");
//            pw.println("#longitudeOffset ");
//            pw.println("##(boolean) Use longitude range [0:360) instead of [-180:180) (false)");
//            pw.println("#crossDateLine ");
            pw.println("##(double) Radius spacing [km] (50)");
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

        lowerLatitude = property.parseInt("lowerLatitude", "0");
        upperLatitude = property.parseInt("upperLatitude", "0");
        if (lowerLatitude < -90 || lowerLatitude > upperLatitude || 90 < upperLatitude)
            throw new IllegalArgumentException("Latitude range " + lowerLatitude + " , " + upperLatitude + " is invalid.");

        lowerLongitude = property.parseInt("lowerLongitude", "0");
        upperLongitude = property.parseInt("upperLongitude", "180");
        if (lowerLongitude < -180 || lowerLongitude > upperLongitude || 360 < upperLongitude)
            throw new IllegalArgumentException("Longitude range " + lowerLongitude + " , " + upperLongitude + " is invalid.");

        lowerRadius = property.parseInt("lowerRadius", "0");
        upperRadius = property.parseInt("upperRadius", "6371");
        if (lowerRadius < 0 || lowerRadius > upperRadius)
            throw new IllegalArgumentException("Radius range " + lowerRadius + " , " + upperRadius + " is invalid.");

//        if (property.containsKey("dLatitudeKm")) {
//            dLatitudeKm = property.parseDouble("dLatitudeKm", null);
//            if (dLatitudeKm <= 0)
//                throw new IllegalArgumentException("dLatitudeKm must be positive");
//            setLatitudeByKm = true;
//        } else {
            dLatitudeDeg = property.parseDouble("dLatitudeDeg", "5");
            if (dLatitudeDeg <= 0)
                throw new IllegalArgumentException("dLatitudeDeg must be positive");
//            setLatitudeByKm = false;
//        }
//        latitudeOffset = property.parseDouble("latitudeOffset", "2.5");
//        if (latitudeOffset < 0)
//            throw new IllegalArgumentException("latitudeOffset must be positive");
//
//        if (property.containsKey("dLongitudeKm")) {
//            dLongitudeKm = property.parseDouble("dLongitudeKm", null);
//            if (dLongitudeKm <= 0)
//                throw new IllegalArgumentException("dLongitudeKm must be positive");
//            setLongitudeByKm = true;
//        } else {
            dLongitudeDeg = property.parseDouble("dLongitudeDeg", "5");
            if (dLongitudeDeg <= 0)
                throw new IllegalArgumentException("dLongitudeDeg must be positive");
//            longitudeOffset = property.parseDouble("longitudeOffset", "2.5");
//            if (longitudeOffset < 0 || dLongitudeDeg <= longitudeOffset)
//                throw new IllegalArgumentException("longitudeOffset must be in [0:dLongitudeDeg)");
//            setLongitudeByKm = false;
//        }
//        crossDateLine = property.parseBoolean("crossDateLine", "false");

            dRadius = property.parseDouble("dRadius", "50");
            if (dRadius <= 0)
                throw new IllegalArgumentException("dRadius must be positive");
    }

    @Override
    public void run() throws IOException {

        // decide horizontal distribution of voxels
        List<HorizontalPixel> horizontalPixels = designHorizontalPixels();

        // set voxel layer information
        int nRadius = (int) Math.floor((upperRadius - lowerRadius) / dRadius);
        double[] layerThicknesses = new double[nRadius];
        double[] voxelRadii = new double[nRadius];
        for (int i = 0; i < nRadius; i++) {
            layerThicknesses[i] = dRadius;
            voxelRadii[i] = lowerRadius + (i + 0.5) * dRadius;
        }

        // output
        Path outputPath = workPath.resolve(DatasetAid.generateOutputFileName("voxel", fileTag, GadgetAid.getTemporaryString(), ".inf"));
        VoxelInformationFile.write(layerThicknesses, voxelRadii, horizontalPixels, outputPath);
    }

    private List<HorizontalPixel> designHorizontalPixels() {
        List<HorizontalPixel> horizontalPixels = new ArrayList<>();

        double dLatitude = dLatitudeDeg;
        int lowerLatitudeIndex = (int) Math.ceil(lowerLatitude / dLatitude);
        int upperLatitudeIndex = (int) Math.floor(upperLatitude / dLatitude);

        for (int i = lowerLatitudeIndex; i <= upperLatitudeIndex; i++) {
            // compute center latitude of the voxel row
            double latitude = i * dLatitude;

            double dLongitude = dLongitudeDeg;
            int lowerLongitudeIndex = (int) Math.ceil(lowerLongitude / dLongitude);
            int upperLongitudeIndex = (int) Math.floor(upperLongitude / dLongitude);

            for (int j = lowerLongitudeIndex; j <= upperLongitudeIndex; j++) {
                // compute center longitude of the voxel row
                double longitude = j * dLongitude;

                // add horizontal pixel to list
                horizontalPixels.add(new HorizontalPixel(new HorizontalPosition(latitude, longitude), dLatitude, dLongitude));
            }
        }
        return horizontalPixels;
    }

}
