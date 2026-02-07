package io.github.kensuke1984.kibrary.math.geometry;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Class to recast {@link HorizontalPosition} to {@link XY} on a curvilinear grid.
 *
 * TODO: coordinate rotation for regions near pole
 *
 * @author otsuru
 * @since 2026/2/6
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

    public XY computeXY(HorizontalPosition position) {
        double latitude = position.getLatitude();
        double y = (position.getLatitude() - baseLatitude) / dLatitude;

        double dLongitudeForLat;
        if (setLongitudeByKm) {
            double smallCircleRadius = centerRadius * Math.cos(Math.toRadians(latitude));
            dLongitudeForLat = Math.toDegrees(dLongitudeVal / smallCircleRadius);
        } else {
            dLongitudeForLat = dLongitudeVal;
        }
        double x = (position.getLongitude(crossDateLine) - baseLongitude) / dLongitudeForLat;

        return new XY(x, y);
    }

    public double getDLatitude() {
        return dLatitude;
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
