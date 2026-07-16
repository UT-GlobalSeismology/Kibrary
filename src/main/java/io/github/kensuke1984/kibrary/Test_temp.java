package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.Precision;
import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {
        Path inPath = Paths.get(args[0]);

        double centerLat = 22.27265;
        double centerLon = -175 + 360;
        double centerRad = 3680;

        List<String> inLines = Files.readAllLines(inPath);
        List<String> outLines = new ArrayList<>();
        for (String inLine : inLines) {
            String[] parts = inLine.split("\\s+");

            double lat = Double.parseDouble(parts[2]);
            double lon = Double.parseDouble(parts[3]);
            if (lon < 0) lon += 360;
            double rad = Double.parseDouble(parts[4]);

            lat = Precision.round(centerLat * 2 - lat, 4);
            lon = Precision.round(centerLon * 2 - lon, 4);
            rad = Precision.round(centerRad * 2 - rad, 4);

            String outLine = parts[0] + " " + parts[1] + " " + lat + " " + lon + " " + rad + " " + parts[5] + " " + parts[6];
            outLines.add(outLine);
        }

        Path outPath = Paths.get("tmp.txt");
        Files.write(outPath, outLines);





        HorizontalPosition pos0 = new HorizontalPosition(0,0);
        HorizontalPosition pos1 = new HorizontalPosition(0,50);
        HorizontalPosition pos2 = new HorizontalPosition(30,-50);

        System.err.println(Earth.computeMidpoint(pos0, pos1));
        System.err.println(Earth.computeMidpoint(pos1, pos0));
        System.err.println(Earth.computeMidpoint(pos1, pos2));

    }

    public static Path getTimeWindowPath_temp(Property property, Path workPath) throws IOException {
        if (property.containsKey("timewindowPath"))
            return property.parsePath("timewindowPath", null, true, workPath);
        else
            return property.parsePath("timeWindowPath", null, true, workPath);
    }
    public static String getDataCenter_temp(Property property) throws IOException {
        if (property.containsKey("datacenter"))
            return property.parseStringSingle("datacenter", "EarthScope");
        else
            return property.parseStringSingle("dataCenter", "EarthScope");
    }
}
