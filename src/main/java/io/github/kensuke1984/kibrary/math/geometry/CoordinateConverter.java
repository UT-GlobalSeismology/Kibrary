package io.github.kensuke1984.kibrary.math.geometry;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.HorizontalPixel;

/**
 * Class to recast {@link HorizontalPosition} to {@link XY} on a curvilinear grid.
 * <p>
 * The x-axis is in the longitude direction. The y-axis is in the COLATITUDE direction.
 *
 * TODO: coordinate rotation for regions near pole
 *
 * @since 2026/2/6
 * @author otsuru
 */
public class CoordinateConverter {

    private final double dLatitude;
    private final double baseLatitude;

    private final double dLongitudeVal;
    private final boolean setLongitudeByKm;
    private final double baseLongitude;

    /**
     * The (roughly) median radius of target region.
     */
    private final double centerRadius;
    private final boolean crossDateLine;

    public void writeToFile(Path outputPath, OpenOption... options) throws IOException {
        System.err.println("Writing " + outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("#rotation");
            pw.println(0.0 + " " + 0.0 + " " + 0.0);
            pw.println("#dLatitude baseLatitude");
            pw.println(dLatitude + " " + baseLatitude);
            pw.println("#dLongitudeVal setLongitudeByKm baseLongitude");
            pw.println(dLongitudeVal + " " + setLongitudeByKm + " " + baseLongitude);
            pw.println("#centerRadius crossDateLine");
            pw.println(centerRadius + " " + crossDateLine);
        }
    }

    public CoordinateConverter(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath, true);

        String[] parts = reader.next().split("\\s+");
        // TODO: coordinate rotation for regions near pole

        parts = reader.next().split("\\s+");
        dLatitude = Double.parseDouble(parts[0]);
        baseLatitude = Double.parseDouble(parts[1]);

        parts = reader.next().split("\\s+");
        dLongitudeVal = Double.parseDouble(parts[0]);
        setLongitudeByKm = Boolean.parseBoolean(parts[1]);
        baseLongitude = Double.parseDouble(parts[2]);

        parts = reader.next().split("\\s+");
        centerRadius = Double.parseDouble(parts[0]);
        crossDateLine = Boolean.parseBoolean(parts[1]);

        System.err.println("Read values from " + filePath);
    }

    public CoordinateConverter(double dLatitudeKm, double dLatitudeDeg, boolean setLatitudeByKm, double baseLatitude,
            double dLongitudeKm, double dLongitudeDeg, boolean setLongitudeByKm, double baseLongitude,
            double centerRadius, boolean crossDateLine) {
        this.dLatitude = setLatitudeByKm ? Math.toDegrees(dLatitudeKm / centerRadius) : dLatitudeDeg;
        this.baseLatitude = baseLatitude;
        this.dLongitudeVal = (setLongitudeByKm ? dLongitudeKm : dLongitudeDeg);
        this.setLongitudeByKm = setLongitudeByKm;
        this.baseLongitude = baseLongitude;
        this.centerRadius = centerRadius;
        this.crossDateLine = crossDateLine;
    }

    public CoordinateConverter(double dLatitude, double baseLatitude, double dLongitudeVal, boolean setLongitudeByKm, double baseLongitude,
            double centerRadius, boolean crossDateLine) {
        this.dLatitude = dLatitude;
        this.baseLatitude = baseLatitude;
        this.dLongitudeVal = dLongitudeVal;
        this.setLongitudeByKm = setLongitudeByKm;
        this.baseLongitude = baseLongitude;
        this.centerRadius = centerRadius;
        this.crossDateLine = crossDateLine;
    }

    public CoordinateConverter withDeltas(double dLatitude, double dLongitudeVal) {
        return new CoordinateConverter(dLatitude, baseLatitude, dLongitudeVal, setLongitudeByKm, baseLongitude, centerRadius, crossDateLine);
    }

    public XY computeXY(HorizontalPosition position) {
        double latitude = position.getLatitude();
        double y = -(latitude - baseLatitude) / dLatitude;
        double x = (position.getLongitude(crossDateLine) - baseLongitude) / computeDLongitudeForLat(latitude);
        return new XY(x, y);
    }

    /**
     * Create Pixel at the given index coordinate.
     * @param integerXY ({@link IntegerXY}) Coordinate of indices.
     * @return ({@link HorizontalPixel}) Pixel at input coordinate.
     */
    public HorizontalPixel createHorizontalPixel(IntegerXY integerXY) {
        // Note: y increases in colatitude direction
        double latitude = -integerXY.y() * dLatitude + baseLatitude;
        double dLongitude = computeDLongitudeForLat(latitude);
        double longitude = integerXY.x() * dLongitude + baseLongitude;
        HorizontalPosition horizontalPosition = new HorizontalPosition(latitude, longitude);
        return new HorizontalPixel(horizontalPosition, dLatitude, dLongitude, integerXY.y(), integerXY.x());
    }

    private double computeDLongitudeForLat(double latitude) {
        if (setLongitudeByKm) {
            double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
            return Math.toDegrees(dLongitudeVal / smallCircleRadius);
        } else {
            return dLongitudeVal;
        }
    }

    public double getDLatitude() {
        return dLatitude;
    }

    public double getDLongitudeVal() {
        return dLongitudeVal;
    }

    public double getLargestDLongitude() {
        if (setLongitudeByKm) {
            return Math.toDegrees(dLongitudeVal / centerRadius);
        } else {
            return dLongitudeVal;
        }
    }

    public boolean isCrossDateLine() {
        return crossDateLine;
    }

}
