package io.github.kensuke1984.kibrary.tests;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.elasticparameter.ElasticMedium;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure_old;
import io.github.kensuke1984.kibrary.voxel.VoxelInformationFile;

/**
 * Creates a checkerboard model file.
 * @author otsuru
 * @since 2022/3/4
 */
public class CheckerboardMaker {

    private Path voxelPath;
    private double percentVs;
    private boolean flipSign;
    private PolynomialStructure_old initialStructure;

    private String tag;
    private Path outPath;

    private List<Double> perturbationList = new ArrayList<>();
    private List<Double> radiusList = new ArrayList<>();

    /**
     * Create checkerboard input model
     * Usage: voxelInformationFile(Path) percentVs(double) flipSign(boolean)
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder("v").longOpt("voxel").hasArg().argName("voxelFile").required()
                .desc("Path of input voxel file").build());
        options.addOption(Option.builder("p").longOpt("percent").hasArg().argName("percentVs").required() //TODO: choose parameter to perturb
                .desc("Percent of Vs perturbation").build());
        options.addOption(Option.builder("f").longOpt("flip")
                .desc("Flip sign of perturbation").build());
        options.addOption(Option.builder("t").longOpt("tag").hasArg().argName("tag")
                .desc("A tag to include in output folder name.").build());

        options.addOption(Option.builder("x").longOpt("longitude").hasArg().argName("longitude")
                .desc("Flip sign of perturbation above the given longitude.").build()); //TODO erase or sophisticate

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        Path voxelPath = Paths.get(cmdLine.getOptionValue("v"));
        double percentVs = Double.parseDouble(cmdLine.getOptionValue("p"));
        boolean flipSign = cmdLine.hasOption("f");
        String tag = cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : null;

        Double switchingLongitude = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("x")) : null; //TODO erase or sophisticate

        PolynomialStructure_old prem = PolynomialStructure_old.PREM;

        CheckerboardMaker cm = new CheckerboardMaker(voxelPath, percentVs, flipSign, prem, tag);
        cm.velocityCheckerboard(switchingLongitude);
        cm.writeModel();
    }

    public CheckerboardMaker(Path voxelPath, double percentVs, boolean flipSign, PolynomialStructure_old prem, String tag) {
        this.voxelPath = voxelPath;
        this.percentVs = percentVs;
        this.flipSign = flipSign;
        this.initialStructure = prem;
        this.tag = tag;
    }

    private void velocityCheckerboard(Double switchingLongitude) throws IOException {
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

                boolean switchSignHere = (switchingLongitude != null) ? (position.getLongitude() > switchingLongitude) : false; //TODO erase or sophisticate

                if ((numDiff % 2 == 1) ^ flipSign ^ switchSignHere) { // ^ is XOR
                    perturbationList.add(-percentVs);
                } else {
                    perturbationList.add(percentVs);
                }
                radiusList.add(radii[i]);
            }
        }

        outPath = DatasetAid.createOutputFolder(Paths.get(""), "checkerboard", tag, GadgetAid.getTemporaryString());

        Path velPath = outPath.resolve("velocity.lst");
        System.err.println("Outputting velocity perturbations in " + velPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(velPath))) {
            perturbationList.forEach(pw::println);
        }

    }

    private void writeModel() throws IOException {
        Path modelPath = outPath.resolve("model.lst");
        System.err.println("Outputting modulus perturbations in " + modelPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(modelPath))) {
            for (int i = 0; i < perturbationList.size(); i++) {
                pw.println(VdashToDeltaMu(perturbationList.get(i), radiusList.get(i)));
            }
        }
    }

    private double VdashToDeltaMu(double deltaVs, double radius){
        ElasticMedium initialMedium = initialStructure.getMediumAt(radius);
        double v = initialMedium.get(ParameterType.Vsh);
        double rho = initialMedium.get(ParameterType.RHO);
        double vDash = (1 + deltaVs * 0.01) * v;
        double deltaMu = (rho * vDash * vDash) - initialMedium.get(ParameterType.MU);
        return deltaMu;
    }



}
