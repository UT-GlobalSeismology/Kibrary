package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * File defining 1D polynomial structure of a planet.
 * @author otsuru
 * @since 2022/6/15 extracted some parts of PolynomialStructure
 */
public class PolynomialStructureFile {
    private PolynomialStructureFile() {}

    public static void write(PolynomialStructure structure, Path outputPath, OpenOption... options) throws IOException {
        int nZone = structure.getNZone();
        int nCoreZone = structure.getNCoreZone();
        double[] rmin = structure.getRmin();
        double[] rmax = structure.getRmax();
        PolynomialFunction[] rho = structure.getRho();
        PolynomialFunction[] vpv = structure.getVpv();
        PolynomialFunction[] vph = structure.getVpv();
        PolynomialFunction[] vsv = structure.getVsv();
        PolynomialFunction[] vsh = structure.getVsv();
        PolynomialFunction[] eta = structure.getEta();
        double[] qMu = structure.getQMu();
        double[] qKappa = structure.getQKappa();

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("c nZone nCoreZone");
            pw.println(nZone + " " + nCoreZone);
            pw.println("c  - Radius (km) -    --- Density (g/cm^3) ---");
            pw.println("c                     ---   Vpv     (km/s) ---");
            pw.println("c                     ---   Vph     (km/s) ---");
            pw.println("c                     ---   Vsv     (km/s) ---");
            pw.println("c                     ---   Vsh     (km/s) ---");
            pw.println("c                     ---   eta     (ND  ) ---             - Qmu -  - Qkappa -");
            for (int i = 0; i < nZone; i++) {
                pw.println(rmin[i] + " " + rmax[i] + " " + PolynomialStructure.stringFor(rho[i]));
                pw.println("          " + PolynomialStructure.stringFor(vpv[i]));
                pw.println("          " + PolynomialStructure.stringFor(vph[i]));
                pw.println("          " + PolynomialStructure.stringFor(vsv[i]));
                pw.println("          " + PolynomialStructure.stringFor(vsh[i]));
                pw.println("          " + PolynomialStructure.stringFor(eta[i]) + " " + qMu[i] + " " + qKappa[i]);
            }
        }
    }

    public static PolynomialStructure read(Path inputPath) throws IOException {
        InformationFileReader reader = new InformationFileReader(inputPath, false);
        String[] structureLines = reader.getNonCommentLines();

        String[] headerParts = structureLines[0].split("\\s+");
        int nZone = Integer.parseInt(headerParts[0]);
        int nCoreZone = Integer.parseInt(headerParts[1]);
        if (nZone < 1)
            throw new IllegalStateException("nzone is invalid.");
        if (structureLines.length != (nZone * 6 + 1))
            throw new IllegalStateException("Invalid number of lines");

        double[] rmin = new double[nZone];
        double[] rmax = new double[nZone];
        PolynomialFunction[] rho = new PolynomialFunction[nZone];
        PolynomialFunction[] vpv = new PolynomialFunction[nZone];
        PolynomialFunction[] vph = new PolynomialFunction[nZone];
        PolynomialFunction[] vsv = new PolynomialFunction[nZone];
        PolynomialFunction[] vsh = new PolynomialFunction[nZone];
        PolynomialFunction[] eta = new PolynomialFunction[nZone];
        double[] qMu = new double[nZone];
        double[] qKappa = new double[nZone];

        for (int i = 0; i < nZone; i++) {
            String[] rangeRhoParts = structureLines[i * 6 + 1].split("\\s+");
            String[] vpvParts = structureLines[i * 6 + 2].split("\\s+");
            String[] vphParts = structureLines[i * 6 + 3].split("\\s+");
            String[] vsvParts = structureLines[i * 6 + 4].split("\\s+");
            String[] vshParts = structureLines[i * 6 + 5].split("\\s+");
            String[] etaParts = structureLines[i * 6 + 6].split("\\s+");

            rmin[i] = Double.parseDouble(rangeRhoParts[0]);
            rmax[i] = Double.parseDouble(rangeRhoParts[1]);

            double[] rhoCoeffs = new double[4];
            double[] vpvCoeffs = new double[4];
            double[] vphCoeffs = new double[4];
            double[] vsvCoeffs = new double[4];
            double[] vshCoeffs = new double[4];
            double[] etaCoeffs = new double[4];
            for (int j = 0; j < 4; j++) {
                rhoCoeffs[j] = Double.parseDouble(rangeRhoParts[j + 2]);
                vpvCoeffs[j] = Double.parseDouble(vpvParts[j]);
                vphCoeffs[j] = Double.parseDouble(vphParts[j]);
                vsvCoeffs[j] = Double.parseDouble(vsvParts[j]);
                vshCoeffs[j] = Double.parseDouble(vshParts[j]);
                etaCoeffs[j] = Double.parseDouble(etaParts[j]);
            }
            rho[i] = new PolynomialFunction(rhoCoeffs);
            vpv[i] = new PolynomialFunction(vpvCoeffs);
            vph[i] = new PolynomialFunction(vphCoeffs);
            vsv[i] = new PolynomialFunction(vsvCoeffs);
            vsh[i] = new PolynomialFunction(vshCoeffs);
            eta[i] = new PolynomialFunction(etaCoeffs);

            qMu[i] = Double.parseDouble(etaParts[4]);
            qKappa[i] = Double.parseDouble(etaParts[5]);
        }

        return new PolynomialStructure(nZone, nCoreZone, rmin, rmax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa);
    }

    /**
     * Create a polynomial structure file under the working folder.
     * The structure can be specified by its name or by using a DSM PSV input file.
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

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("n").longOpt("name").hasArg().argName("name")
                .desc("Specify name of structure").build());
        inputOption.addOption(Option.builder("d").longOpt("dsmPsv").hasArg().argName("dsmPsvInputFile")
                .desc("Use DSM PSV input file as input").build());
        inputOption.setRequired(true);
        options.addOptionGroup(inputOption);

        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        PolynomialStructure structure;
        String structureName;

        if (cmdLine.hasOption("n")) {
            structureName = cmdLine.getOptionValue("n");
            structure = PolynomialStructure.of(structureName);
        } else {
            System.err.println("not supported yet"); // TODO
            return;
        }

        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get(structureName + ".structure");

        write(structure, outputPath);
        System.err.println(outputPath + " is created.");

    }
}
