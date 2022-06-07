package io.github.kensuke1984.kibrary.tests;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Creates a checkerboard model file.
 * @author otsuru
 * @since 2022/3/4
 */
public class CheckerboardMaker {

    private Path voxelPath;
    private double percentVs;
    private boolean flipSignVs;
    private double percentVp;
    private boolean flipSignVp;
    private PolynomialStructure prem;

    String dateStr;

    List<Double> perturbationList = new ArrayList<>();
    List<Double> perturbationList2 = new ArrayList<>();
    List<Double> radiusList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 3 && args.length !=5) {
            System.err.println("Usage: voxelInformationFile(Path) percentVs(double) flipSign(boolean)");
            System.err.println("Usage: voxelInformationFile(Path) percentVs(double) flipSignVs(boolean) percentVp(double) flipSignVp(boolean)");
        }

        CheckerboardMaker cm;

        Path voxelPath = Paths.get(args[0]);
        double percentVs = Double.parseDouble(args[1]);
        boolean flipSignVs = Boolean.parseBoolean(args[2]);
        PolynomialStructure prem = PolynomialStructure.PREM;
        if (args.length == 3) {
            cm = new CheckerboardMaker(voxelPath, percentVs, flipSignVs, 0, false, prem);
        } else {
            double percentVp = Double.parseDouble(args[3]);
            boolean flipSignVp = Boolean.parseBoolean(args[4]);
            cm = new CheckerboardMaker(voxelPath, percentVs, flipSignVs, percentVp, flipSignVp, prem);
        }

        cm.velocityCheckerboard();
        cm.writeModel();
    }

    public CheckerboardMaker(Path voxelPath, double percentVs, boolean flipSignVs, double percentVp, boolean flipSignVp, PolynomialStructure prem) {
        this.voxelPath = voxelPath;
        this.percentVs = percentVs;
        this.flipSignVs = flipSignVs;
        this.percentVp = percentVp;
        this.flipSignVp = flipSignVp;
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

                if ((numDiff % 2 == 1) ^ flipSignVs) { // ^ is XOR
                    perturbationList.add(-percentVs);
                } else {
                    perturbationList.add(percentVs);
                }
                radiusList.add(radii[i]);
            }
        }

        Path vsPath = Paths.get("vs" + dateStr + ".inf");
        System.err.println("Outputting shear velocity perturbations in " + vsPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(vsPath))) {
            perturbationList.forEach(pw::println);
        }

        // for Vp
        if (percentVp != 0) {
             Path vpPath = Paths.get("vp" + dateStr + ".inf");
             System.err.println("Outputting compressional velocity perturbations in " + vpPath);
             try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(vpPath))) {
                 if (flipSignVs ^ flipSignVp) {
                     for (int i = 0; i < perturbationList.size(); i++) {
                         double pl = perturbationList.get(i);
                         perturbationList2.add(-pl);
                     }
                 } else {
                     perturbationList2 = perturbationList;

                 }
                 perturbationList2.forEach(pw::println);
             }
        }

    }

    private void writeModel() throws IOException {
        Path modelPath = Paths.get("model" + dateStr + ".inf");
        System.err.println("Outputting modulus perturbations in " + modelPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(modelPath))) {
            for (int i = 0; i < perturbationList.size(); i++) {
                pw.println(VdashToDeltaMu(perturbationList.get(i), radiusList.get(i)));
            }
            //for Vp
            if (percentVp != 0) {
                for (int i = 0; i < perturbationList2.size(); i++) {
                    pw.println(VdashToDeltaLambda2Mu(perturbationList2.get(i), radiusList.get(i)));
                }
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

    private double VdashToDeltaLambda2Mu(double deltaVp, double radius){
        double vp = prem.getVphAt(radius);
        double rho = prem.getRhoAt(radius);
        double vDash = (1 + deltaVp * 0.01) * vp;
        double deltaLambda2Mu = (rho * vDash * vDash) - (prem.computeLambda(radius) + 2*prem.computeMu(radius));
        return deltaLambda2Mu;
    }

}
