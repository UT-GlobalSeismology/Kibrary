package io.github.kensuke1984.kibrary.tests;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Creates a checkerboard model file.
 * @author otsuru
 * @since 2022/3/4
 */
public class CheckerboardMaker {

    private Path voxelPath;
    private double amplitude;
    private boolean flipSign;
    private PolynomialStructure prem;

    String dateStr;

    List<Double> perturbationList = new ArrayList<>();
    List<Double> radiusList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: voxelInformationFile(Path) amplitude(double) flipSign(boolean)");
        }

        Path voxelPath = Paths.get(args[0]);
        double amplitude = Double.parseDouble(args[1]);
        boolean flipSign = Boolean.parseBoolean(args[2]);
        PolynomialStructure prem = PolynomialStructure.PREM;

        CheckerboardMaker cm = new CheckerboardMaker(voxelPath, amplitude, flipSign, prem);
        cm.velocityCheckerboard();
        cm.writeModel();
    }

    public CheckerboardMaker(Path voxelPath, double amplitude, boolean flipSign, PolynomialStructure prem) {
        this.voxelPath = voxelPath;
        this.amplitude = amplitude;
        this.flipSign = flipSign;
        this.prem = prem;
        dateStr = GadgetAid.getTemporaryString();
    }

    private void velocityCheckerboard() throws IOException {
        // read voxel file
        VoxelInformationFile file = new VoxelInformationFile(voxelPath);
        //double[] layerThicknesses = file.getThicknesses();
        double[] radii = file.getRadii();
        double dLatitude = file.getSpacingLatitude();
        double dLongitude = file.getSpacingLongitude();
        HorizontalPosition[] positions = file.getHorizontalPositions();

        HorizontalPosition referencePosition = positions[0];
        for (HorizontalPosition position : positions) {
            for (int i = 0; i < radii.length; i++) {
                int numDiff = (int) Math.round((position.getLatitude() - referencePosition.getLatitude()) / dLatitude
                        + (position.getLongitude() - referencePosition.getLongitude()) / dLongitude) + i;

                if ((numDiff % 2 == 1) ^ flipSign) { // ^ is XOR
                    perturbationList.add(-amplitude);
                } else {
                    perturbationList.add(amplitude);
                }
                radiusList.add(radii[i]);
            }
        }

        Path velPath = Paths.get("velocity" + dateStr + ".inf");
        System.err.println("Outputting velocity perturbations in " + velPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(velPath))) {
            perturbationList.forEach(pw::println);
        }

    }

    private void writeModel() throws IOException {
        Path modelPath = Paths.get("model" + dateStr + ".inf");
        System.err.println("Outputting modulus perturbations in " + modelPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(modelPath))) {
            for (int i = 0; i < perturbationList.size(); i++) {
                pw.println(VdashToDeltaMu(perturbationList.get(i), radiusList.get(i)));
            }
        }
    }

    private double VdashToDeltaMu(double deltaVs, double radius){
        double v = prem.getVshAt(radius);
        double rho = prem.getRhoAt(radius);
        double vDash = (1 + deltaVs * 0.01) * v;
        double deltaMu = (rho * vDash * vDash) - prem.computeMu(radius);
        return deltaMu;
    }



}
