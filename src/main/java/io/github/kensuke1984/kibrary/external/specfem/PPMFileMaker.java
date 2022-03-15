package io.github.kensuke1984.kibrary.external.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.MathAid;

public class PPMFileMaker {

    private static final PolynomialStructure PREM = PolynomialStructure.PREM;
    private Path workPath;
    private double[] radii;
    private double dLatitude;
    private double dLongitude;
    private double percentVs;
    private boolean flipSign;

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: percentVs dLatitude dLongitude radii...");
        }

        Path workPath = Paths.get("");
        double percentVs = Double.parseDouble(args[0]);
        double dLatitude = Double.parseDouble(args[1]);
        double dLongitude = Double.parseDouble(args[2]);
        double[] radii = new double[args.length-3];
        for (int i = 3; i < args.length; i++) {
            radii[i-3] =  Double.parseDouble(args[i]);
        }

        PPMFileMaker maker = new PPMFileMaker(workPath, radii, dLatitude, dLongitude, percentVs, false);
        maker.run();

    }

    public PPMFileMaker(Path workPath, double[] radii, double dLatitude, double dLongitude, double percentVs,
            boolean flipSign) {
        this.workPath = workPath;
        this.radii = radii;
        this.dLatitude = dLatitude;
        this.dLongitude = dLongitude;
        this.percentVs = percentVs;
        this.flipSign = flipSign;
    }


    private void run() throws IOException {

        double divLongitude = 360 / dLongitude;
        if (!MathAid.equalWithinEpsilon(divLongitude, Math.round(divLongitude), 0.01)) {
            throw new IllegalArgumentException("dLongitude must divide 360");
        }
        int numLongitude = (int) Math.round(divLongitude);

        double divLatitude = 180 / dLatitude;
        if (!MathAid.equalWithinEpsilon(divLatitude, Math.round(divLatitude), 0.01)) {
            throw new IllegalArgumentException("dLatitude must divide 180");
        }
        int numLatitude = (int) Math.round(divLatitude);

        Path ppmPath = workPath.resolve("model.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ppmPath))) {
            pw.println("#lon(deg), lat(deg), depth(km), Vs-perturbation_wrt_PREM(%), Vs-PREM (km/s)");

            // radii are called in reverse order so that depths will be in order
            for (int i = radii.length - 1; i >= 0; i--) {
                double depth = toDepth(radii[i]);

/*                for (int j = 0; j < numLongitude; j++) {
                    double longitude = dLongitude * (j+0.5) - 180;
                    for (int k = 0; k < numLatitude; k++) {
                        double latitude = dLatitude * (k+0.5) - 90;
*/
                for (int j = 0; j < numLongitude; j++) {
                    double longitude = dLongitude * (j) - 180;
                    for (int k = 0; k < numLatitude - 1; k++) {
                        double latitude = dLatitude * (k+1) - 90;

                        int numDiff = i + j + k;
                        double value = percentVs * (((numDiff % 2 == 1) ^ flipSign) ? 1 : -1); // ^ is XOR
                        pw.println(longitude + " " + latitude + " " + depth + " " + value + " " + PREM.computeVs(radii[i]));
                    }
                }
            }
        }
    }

    private static double toDepth(double radius) {
        return 6371 - radius;
    }

}
