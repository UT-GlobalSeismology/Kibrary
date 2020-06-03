package io.github.kensuke1984.anisotime;

import io.github.kensuke1984.kibrary.util.Utilities;
import net.sf.epsgraphics.ColorMode;
import net.sf.epsgraphics.EpsGraphics;
import org.apache.commons.cli.*;
import org.apache.commons.math3.util.Precision;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class is only for CLI use of ANISOtime.
 * <p>
 * TODO customize for catalog ddelta dR
 * <p>
 * <p>
 * java io.github.kensuke1984.anisotime.ANISOtime -rc iprem85.cat -h 571 -ph P -dec 5 --time -deg 88.7
 *
 * @author Kensuke Konishi, Anselme Borgeaud
 * @version 0.3.19.1
 */
final class ANISOtimeCLI {

    /**
     * Options
     */
    private final static Options options = new Options();

    /**
     * Help Formatter
     */
    private final static HelpFormatter helpFormatter = new HelpFormatter();

    static {
        // add options
        setBooleanOptions();
        setArgumentOptions();
    }

    private final String INPUT;
    /**
     * Input for ANISOtime
     */
    private final CommandLine cmd;
    private RaypathCatalog catalog;
    /**
     * [rad]
     */
    private double targetDelta;
    private VelocityStructure structure;
    private double eventR;
    private Phase[] targetPhases;
    private boolean relativeAngleMode;
    /**
     * [s/rad] dT/d&Delta;
     */
    private double rayParameter;
    /**
     * Number of decimal places. Default:2
     */
    private int decimalPlaces;
    /**
     * 1:ray parameter 2:&Delta; 4:T
     */
    private int showFlag;
    /**
     * String for a file name.
     */
    private String tmpStr = Utilities.getTemporaryString();

    /**
     * @param args arguments
     * @throws ParseException if any
     */
    private ANISOtimeCLI(String[] args) throws ParseException {
        cmd = new DefaultParser().parse(options, args);
        INPUT = String.join(" ", args);
    }

    /**
     * @param args [commands]
     * @throws ParseException if any
     */
    public static void main(String[] args) throws ParseException {
        if (args.length == 0) {
            About.main(null);
            return;
        }

        for (String o : args)
            if (o.equals("-help") || o.equals("--help")) {
                printHelp();
                return;
            }

        for (String o : args)
            if (o.equals("-v") || o.equals("--version") || o.equals("-version")) {
                About.main(null);
                return;
            }

        try {
            new ANISOtimeCLI(args).run();
        } catch (MissingArgumentException pe) {
            System.err.println(pe.getMessage());
        }
    }

    static void printHelp() {
        helpFormatter.printHelp("ANISOtime (CLI)", options);
    }

    private static void setBooleanOptions() {
        options.addOption("SV", false, "Computes travel time for SV. (default:SH)");
        options.addOption("SH", false, "Computes travel time for SH. (default:SH)");
        options.addOption("help", "Shows this message. This option has the highest priority.");
        options.addOption("eps", false, "Outputs path figures.");
        options.addOption(null, "rayp", false, "Shows ray parameters.");
        options.addOption(null, "time", false, "Shows travel times.");
        options.addOption(null, "delta", false, "Shows epicentral distances.");
        options.addOption("v", "version", false,
                "Shows information of the tool. This option has the 2nd highest priority.");
        options.addOption("s", "send", false,
                "If you find any problem with a set of commands, add this argument to send the situation to Kensuke Konishi.");
        options.addOption(null, "relative", false, "Relative angle mode. (default:absolute)");
        options.addOption(null, "absolute", false, "Absolute angle mode. (default:absolute)");
    }

    private static void setArgumentOptions() {
        options.addOption("h", true, "Depth of source [km] (default:0)");
        options.addOption("deg", "epicentral-distance", true, "Epicentral distance \u0394 [deg]");
        options.addOption("ph", "phase", true, "Seismic phase (default:P,PCP,PKiKP,S,ScS,SKiKS)");
        options.addOption("mod", true, "Structure (default:prem)");
        options.addOption("dec", true, "Number of decimal places (default:2)");
        options.addOption("p", true, "Ray parameter [s/deg]");
        options.addOption("dR", true, "Integral interval [km] (default:10)");
        options.addOption("dD", true, "Parameter \u03b4\u0394 [deg] for a catalog creation. (default:0.1)");
        options.addOption("rc", "read-catalog", true, "Path of a catalog for which travel times are computed.");
        options.addOption("rs", "record-section", true,
                "start, end (,interval) [deg]\n Computes a table of a record section for the range.");
        options.addOption("o", true, "Directory for output files");
    }

    /**
     * Sets parameters according to the input arguments.
     */
    private void setParameters() {

        decimalPlaces = Integer.parseInt(cmd.getOptionValue("dec", "2"));
        if (decimalPlaces < 0)
            throw new IllegalArgumentException(cmd.getOptionValue("dec") + " is an invalid value for \"dec\"");

        if (cmd.hasOption("rayp")) showFlag = 1;
        if (cmd.hasOption("delta")) showFlag |= 2;
        if (cmd.hasOption("time")) showFlag |= 4;
        if (showFlag == 0) showFlag = 7;

        relativeAngleMode = cmd.hasOption("relative");

        if (cmd.hasOption("rc")) {
            try {
                Path catalogPath = Paths.get(cmd.getOptionValue("rc"));
                catalog = RaypathCatalog.read(catalogPath);
                structure = catalog.getStructure();
                eventR = structure.earthRadius() - Double.parseDouble(cmd.getOptionValue("h", "0"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!cmd.hasOption("mod"))
                throw new RuntimeException("You must specify a velocity model (e.g. -mod prem).");
            structure = createVelocityStructure();
            eventR = structure.earthRadius() - Double.parseDouble(cmd.getOptionValue("h", "0"));
            if (structure.equals(VelocityStructure.iprem())) catalog = RaypathCatalog.iprem();
            else if (structure.equals(VelocityStructure.prem())) catalog = RaypathCatalog.prem();
            else if (structure.equals(VelocityStructure.ak135())) catalog = RaypathCatalog.ak135();
            else {
                ComputationalMesh mesh = ComputationalMesh.simple(structure);
                catalog = RaypathCatalog.computeCatalog(structure, mesh, RaypathCatalog.DEFAULT_MAXIMUM_D_DELTA);
            }
        }

        if (cmd.hasOption("ph")) targetPhases =
                Arrays.stream(cmd.getOptionValues("ph")).flatMap(arg -> Arrays.stream(arg.split(",")))
                        .map(n -> Phase.create(n, cmd.hasOption("SV"))).distinct().toArray(Phase[]::new);
        else targetPhases = cmd.hasOption("SV") ?
                new Phase[]{Phase.p, Phase.pP, Phase.P, Phase.PcP, Phase.PKP, Phase.PKiKP, Phase.PKIKP, Phase.Pdiff,
                        Phase.SV, Phase.SVcS, Phase.SKS, Phase.SKiKS, Phase.SKIKS, Phase.SVdiff} :
                new Phase[]{Phase.p, Phase.pP, Phase.P, Phase.PcP, Phase.PKP, Phase.PKiKP, Phase.PKIKP, Phase.Pdiff,
                        Phase.s, Phase.sS, Phase.S, Phase.ScS, Phase.SKS, Phase.SKiKS, Phase.SKIKS, Phase.Sdiff};

        targetDelta = Math.toRadians(Double.parseDouble(cmd.getOptionValue("deg", "NaN")));

        if (targetDelta < 0) throw new RuntimeException("A value for the option -deg must be non-negative.");
        if (relativeAngleMode && Math.PI < targetDelta)
            throw new RuntimeException("In the relative angle mode, a value for the option -deg must be 180 or less.");

        double rayParameterDegree = Double.parseDouble(cmd.getOptionValue("p", "NaN"));
        rayParameter = Math.toDegrees(rayParameterDegree);
    }

    /**
     * @return if all the argument options have values.
     */
    private boolean checkArgumentOption() {
        boolean hasProblem = false;
        if (Arrays.stream(cmd.getOptions()).filter(Option::hasArg).anyMatch(o -> o.getValue().startsWith("-"))) {
            System.err.println("Some options are missing arguments.");
            hasProblem = true;
        }
        return hasProblem;
    }

    /**
     * If the option '-rs' is passed, ANISOtime creates a record section.
     * <p>
     * File name is given by -o option. If it is not given, the file name is 'anisotime.rcs'
     * If the file of the name already exists, this method does nothing.
     * The number of Phases selected by -ph must be 1.
     *
     * @throws IOException if any
     */
    private void printRecordSection() throws IOException {
        Path outfile = Paths.get(cmd.getOptionValue("o", "anisotime.rcs"));

        if (Files.exists(outfile))
            throw new RuntimeException(outfile + " already exists. Option '-o' allows changing the output file name.");

        double[] ranges = Arrays.stream(cmd.getOptionValue("rs").split(",")).mapToDouble(Double::parseDouble).toArray();
        double min = ranges[0];
        double max = ranges[1];
        double interval = 2 < ranges.length ? ranges[2] : 1;
        double[] targets = new double[(int) Math.ceil((max - min) / interval) + 1];
        for (int i = 0; i < targets.length; i++)
            targets[i] = min + interval * i;

        targets[targets.length - 1] = max;
        try (PrintStream ps = new PrintStream(Files.newOutputStream(outfile, StandardOpenOption.CREATE_NEW))) {
            ps.println("#created by " + INPUT);
            Map<Raypath, Double> deltaPathMap = new HashMap<>();
            for (Phase phase : targetPhases) {
                if (phase.isDiffracted()) {
                    Raypath diff = catalog.searchPath(phase, eventR, 0, relativeAngleMode)[0];
                    double sAngle = Math.toDegrees(diff.computeDelta(phase, eventR));
                    for (double d : targets) {
                        double deltaOnBoundary = d - sAngle;
                        if (deltaOnBoundary < 0) continue;
                        Phase diffPhase = Phase.create(phase.toString() + deltaOnBoundary, phase.isPSV());
                        printResults(-1, diff, diffPhase, ps);
                    }
                    continue;
                }
                deltaPathMap.clear();
                for (double d : targets)
                    for (Raypath p : catalog.searchPath(phase, eventR, Math.toRadians(d), relativeAngleMode))
                        deltaPathMap.put(p, d);

                deltaPathMap.keySet().stream().sorted(Comparator.comparingDouble(Raypath::getRayParameter).reversed())
                        .forEach(r -> printResults(deltaPathMap.get(r), r, phase, ps));
            }
        }
    }

    /**
     * @param out          Path for a file
     * @param phase        to be shown
     * @param rayparameter p
     * @param delta        [deg] to be shown.
     * @param time         [s] to be printed
     * @param eventR       radius of the event [km]
     */
    void createEPS(RaypathPanel panel, Path out, Phase phase, double rayparameter, double delta, double time,
                   double eventR) {
        try (BufferedOutputStream bos = new BufferedOutputStream(
                Files.newOutputStream(out, StandardOpenOption.CREATE_NEW))) {
            EpsGraphics epsGraphics = new EpsGraphics(phase.toString(), bos, 0, 0, panel.getWidth(), panel.getHeight(),
                    ColorMode.COLOR_RGB);
            panel.paintComponent(epsGraphics);
            String rayp = Utilities.fixDecimalPlaces(decimalPlaces, rayparameter);
            String delt = Utilities.fixDecimalPlaces(decimalPlaces, delta);
            String tra = Utilities.fixDecimalPlaces(decimalPlaces, time);
            String depth = Utilities.fixDecimalPlaces(decimalPlaces, structure.earthRadius() - eventR);
            String line = phase + ", Ray parameter: " + rayp + ", Depth[km]:" + depth + ", Epicentral distance[deg]: " +
                    delt + ", Travel time[s]: " + tra;
            int startInt = (int) panel.changeX(-line.length() / 2 * 6371 / 45);
            epsGraphics.drawString(line, startInt, (int) panel.changeY(structure.earthRadius()) - 25);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() {
        try {
            if (checkArgumentOption()) throw new RuntimeException("Input arguments have problems.");
            hasConflict();

            setParameters();

            if (cmd.hasOption("rs")) {
                printRecordSection();
                return;
            }

            // only create a catalog
            if (!cmd.hasOption("p") && !cmd.hasOption("deg")) throw new RuntimeException(
                    "You must specify a rayparameter (e.g. -p 10) or epicentral distance [deg] (e.g. -deg 60)");

            Path outDir = Paths.get(cmd.getOptionValue("o", ""));
            Files.createDirectories(outDir);

            // When the ray parameter is given
            if (cmd.hasOption("p")) {
                Raypath raypath = new Raypath(rayParameter, structure);
                raypath.compute();
                for (Phase targetPhase : targetPhases) {
                    double delta = Math.toDegrees(raypath.computeDelta(targetPhase, eventR));
                    double time = raypath.computeT(targetPhase, eventR);
                    if (Double.isNaN(delta)) {
                        System.err.println(targetPhase + " does not exist.");
                        continue;
                    }
                    double rayParameterDegree = Math.toRadians(rayParameter);
                    printLine(targetPhase, System.out, decimalPlaces, rayParameterDegree, delta, time);
                    if (cmd.hasOption("eps")) createEPS(raypath.createPanel(targetPhase, eventR),
                            outDir.resolve(targetPhase + "." + tmpStr + ".eps"), targetPhase, rayParameterDegree, delta,
                            time, eventR);
                }
                return;
            }

            for (Phase targetPhase : targetPhases) {
                Raypath[] raypaths = catalog.searchPath(targetPhase, eventR, targetDelta, relativeAngleMode);
                if (raypaths.length == 0) {
                    System.err.println("No raypaths satisfying the input condition: " + targetPhase);
                    continue;
                }
                if (targetPhase.isDiffracted()) {
                    Raypath raypath = raypaths[0];
                    double deltaOnBoundary =
                            Math.toDegrees(targetDelta - raypaths[0].computeDelta(targetPhase, eventR));
                    if (deltaOnBoundary < 0) {
                        System.err.println(targetPhase + " would have longer distance than " + Precision
                                .round(Math.toDegrees(raypath.computeDelta(targetPhase, eventR)), decimalPlaces) +
                                "\u00B0 (Your input: " + Precision.round(Math.toDegrees(targetDelta), decimalPlaces) +
                                "\u00B0)");
                        continue;
                    }
                    targetPhase = Phase.create(targetPhase.toString() + deltaOnBoundary, targetPhase.isPSV());
                    double[] results = printResults(-1, raypath, targetPhase, System.out);
                    if (cmd.hasOption("eps")) createEPS(raypath.createPanel(targetPhase, eventR),
                            outDir.resolve(targetPhase + "." + tmpStr + ".eps"), targetPhase, raypath.getRayParameter(),
                            targetDelta, results[1], eventR);
                    continue;
                }
                int j = 0;
                for (Raypath raypath : raypaths) {
                    Phase actualPhase = RaypathCatalog
                            .getActualTargetPhase(raypath, targetPhase, eventR, targetDelta, relativeAngleMode);
                    double[] results = printResults(Math.toDegrees(targetDelta), raypath, actualPhase, System.out);
                    if (cmd.hasOption("eps")) if (raypaths.length == 1)
                        createEPS(raypath.createPanel(actualPhase, eventR),
                                outDir.resolve(actualPhase + "." + tmpStr + ".eps"), actualPhase,
                                raypath.getRayParameter(), targetDelta, results[1], eventR);
                    else createEPS(raypath.createPanel(actualPhase, eventR),
                                outDir.resolve(actualPhase + "." + j++ + "." + tmpStr + ".eps"), actualPhase,
                                raypath.getRayParameter(), targetDelta, results[1], eventR);
                }
            }
        } catch (Exception e) {
            if (!cmd.hasOption("s")) {
                System.err.println(
                        "improper usage or some other problems. If you have no idea about this, you try the same order with '-s' option to send me the situation.");
                System.err.println(e.getMessage());
            }
        } finally {
            if (cmd.hasOption("s")) try {
                Utilities.sendMail(io.github.kensuke1984.kibrary.About.EMAIL_ADDRESS, "ANISOtime problem",
                        "Input: " + INPUT, "If you use your original structure, please attach it.\n",
                        "OS: " + System.getProperty("os.name"), "Java VERSION: " + System.getProperty("java.version"));
            } catch (Exception e2) {
                System.err.println("Sorry could not send an Email. Please inform me of your situation.");
                System.err.println("Email address: " + io.github.kensuke1984.kibrary.About.EMAIL_ADDRESS);
            }
        }
    }

    /**
     * TODO
     *
     * @param phase        target phase
     * @param out          target out
     * @param decimalPlace the numer of decimal places with which the values are shown
     * @param values       according to {@link #showFlag}, the values are shown. ray parameter, delta, time
     */
    private void printLine(Phase phase, PrintStream out, int decimalPlace, double... values) {
        out.println(phase.getDISPLAY_NAME() + " " +
                IntStream.range(0, values.length).filter(i -> (1 << i & showFlag) != 0)
                        .mapToObj(i -> Utilities.fixDecimalPlaces(decimalPlace, values[i]))
                        .collect(Collectors.joining(" ")));
    }

    /**
     * @param targetDelta [deg] a target &Delta; for the interpolation. If it is 0, the interpolation will not be done.
     * @param raypath     Raypath
     * @param targetPhase phase to be printed
     * @param out         resource to print in
     * @return [deg] delta, [s] time
     */
    private double[] printResults(double targetDelta, Raypath raypath, Phase targetPhase, PrintStream out) {
        double p0 = raypath.getRayParameter();
        double delta0 = raypath.computeDelta(targetPhase, eventR);
        double time0 = raypath.computeT(targetPhase, eventR);
        if (Double.isNaN(delta0) || Double.isNaN(time0)) return new double[]{Double.NaN, Double.NaN};

        delta0 = Math.toDegrees(delta0);
        if (0 < targetDelta) {
            double p1 = catalog.rayParameterByThreePointInterpolate(targetPhase, eventR, Math.toRadians(targetDelta),
                    relativeAngleMode, raypath);
            Raypath raypath1 = new Raypath(p1, raypath.getStructure());
            raypath1.compute();
            double time1 = raypath1.computeT(targetPhase, eventR);
            double delta1 = Math.toDegrees(raypath1.computeDelta(targetPhase, eventR));
            if (!Double.isNaN(time1)) {
                time0 = time1;
                delta0 = delta1;
                p0 = p1;
            }
        }
        double p0Degree = Math.toRadians(p0);
        printLine(targetPhase, out, decimalPlaces, p0Degree, delta0, time0);
        return new double[]{delta0, time0};
    }

    /**
     * According to the arguments in the command line(cmd), a velocity structure
     * is given.
     *
     * @return velocity structure according to the input. Default: PREM
     */
    private VelocityStructure createVelocityStructure() {
        Path modelPath;
        if (cmd.hasOption("mod")) switch (cmd.getOptionValue("mod")) {
            case "ak135":
            case "AK135":
                return PolynomialStructure.AK135;
            case "prem":
            case "PREM":
                return PolynomialStructure.PREM;
            case "iprem":
            case "iPREM":
                return PolynomialStructure.ISO_PREM;
            default:
                modelPath = Paths.get(cmd.getOptionValue("mod"));
        }
        else return PolynomialStructure.PREM;

        if (Files.exists(modelPath)) {
            try {
                return new PolynomialStructure(modelPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                return new NamedDiscontinuityStructure(modelPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
            throw new RuntimeException("Input model file is invalid.");
        }
        throw new RuntimeException(modelPath + " (input model) is not found.");
    }

    /**
     * check if there are conflicts
     */
    private void hasConflict() {
        if (cmd.hasOption("absolute") && cmd.hasOption("relative"))
            throw new IllegalArgumentException("Only one of either --absolute or --relative can be chosen.");

        if (cmd.hasOption("h") && 1 < cmd.getOptionValues("h").length)
            throw new IllegalArgumentException("Option -h (depth) can have only one value [km].");

        if (cmd.hasOption("SH")) {
            if (cmd.hasOption("SV")) throw new IllegalArgumentException("Only one of either -SV or -SH can be chosen.");
            if (cmd.hasOption("ph")) {
                String phase = cmd.getOptionValue("ph");
                if (phase.contains("P") || phase.contains("p") || phase.contains("K"))
                    throw new IllegalArgumentException(phase + " should be P-SV mode.");
            }
        }

        if (cmd.hasOption("p") && cmd.hasOption("deg"))
            throw new IllegalArgumentException("You can not use both option -p and -deg simultaneously.");

        if (cmd.hasOption("rs")) {
            if (cmd.hasOption("p") || cmd.hasOption("deg")) throw new IllegalArgumentException(
                    "When you compute record sections, neither option -p nor -deg can be specified.");

            if (!cmd.hasOption("ph"))
                throw new IllegalArgumentException("When you compute record sections, -ph must be specified.");

            if (cmd.hasOption("eps"))
                throw new IllegalArgumentException("When you compute record sctions, -eps can not be set.");
        } else if (cmd.hasOption("o") && !cmd.hasOption("eps")) throw new IllegalArgumentException(
                "-o can be set, only when you compute record sections or make ray path figures.");

        if (cmd.hasOption("rc") && cmd.hasOption("mod"))
            throw new IllegalArgumentException("When you read a catalog, you cannot specify a velocity model.");

    }
}
