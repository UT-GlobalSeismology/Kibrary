package io.github.kensuke1984.kibrary.util.earth;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * File defining 1D polynomial structure of a planet.
 * @author otsuru
 * @since 2022/6/15 extracted some parts of PolynomialStructure
 */
public class PolynomialStructureFile {
    private PolynomialStructureFile() {}

    public static void write(PolynomialStructure_new structure, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            String[] structureLines = structure.toPSVlines();
            Arrays.stream(structureLines).forEach(pw::println);
        }
    }

    public static PolynomialStructure_new read(Path inputPath) throws IOException {
        InformationFileReader reader = new InformationFileReader(inputPath, false);
        String[] structureLines = reader.getNonCommentLines();

        int nzone = Integer.parseInt(structureLines[0].split("\\s+")[0]);
        if (nzone < 1)
            throw new IllegalStateException("nzone is invalid.");
        if (structureLines.length != (nzone * 6 + 1))
            throw new IllegalStateException("Invalid number of lines");

        double[] rmin = new double[nzone];
        double[] rmax = new double[nzone];
        PolynomialFunction[] rho = new PolynomialFunction[nzone];
        PolynomialFunction[] vpv = new PolynomialFunction[nzone];
        PolynomialFunction[] vph = new PolynomialFunction[nzone];
        PolynomialFunction[] vsv = new PolynomialFunction[nzone];
        PolynomialFunction[] vsh = new PolynomialFunction[nzone];
        PolynomialFunction[] eta = new PolynomialFunction[nzone];
        double[] qMu = new double[nzone];
        double[] qKappa = new double[nzone];

        for (int i = 0; i < nzone; i++) {
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

        return new PolynomialStructure_new(nzone, rmin, rmax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa);
    }

    public static void main(String[] args) {
        // TODO 自動生成されたメソッド・スタブ

    }

}
