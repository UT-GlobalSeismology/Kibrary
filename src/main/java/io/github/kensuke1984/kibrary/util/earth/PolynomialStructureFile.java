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
 * File defining 1D polynomial structure of the Earth.
 * @author otsuru
 * @since 2022/6/15
 */
public class PolynomialStructureFile {
    private PolynomialStructureFile() {}

    public static void write(PolynomialStructure structure, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            String[] structureLines = structure.toPSVlines();
            Arrays.stream(structureLines).forEach(pw::println);
        }
    }

    public static PolynomialStructure read(Path inputPath) throws IOException {
        InformationFileReader reader = new InformationFileReader(inputPath, false);
        String[] structureLines = reader.getNonCommentLines();

        int nzone;
        double[] rmin;
        double[] rmax;
        PolynomialFunction[] rho;
        PolynomialFunction[] vpv;
        PolynomialFunction[] vph;
        PolynomialFunction[] vsv;
        PolynomialFunction[] vsh;
        PolynomialFunction[] eta;
        double[] qMu;
        double[] qKappa;

        nzone = Integer.parseInt(structureLines[0].split("\\s+")[0]);
        if (structureLines.length != (nzone * 6 + 1))
            throw new IllegalArgumentException("Invalid lines");

        if (nzone < 1)
            throw new IllegalStateException("nzone is invalid.");
        rmin = new double[nzone];
        rmax = new double[nzone];
        rho = new PolynomialFunction[nzone];
        vpv = new PolynomialFunction[nzone];
        vph = new PolynomialFunction[nzone];
        vsv = new PolynomialFunction[nzone];
        vsh = new PolynomialFunction[nzone];
        eta = new PolynomialFunction[nzone];
        qMu = new double[nzone];
        qKappa = new double[nzone];

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

        return new PolynomialStructure(nzone, rmin, rmax, rho, vpv, vph, vsv, vsh, eta, qMu, qKappa);
    }

    public static void main(String[] args) {
        // TODO 自動生成されたメソッド・スタブ

    }

}
