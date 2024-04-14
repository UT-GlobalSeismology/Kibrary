package io.github.kensuke1984.kibrary.inversion.setup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Class for assembling A<sup>T</sup>A and A<sup>T</sup>d.
 * <p>
 * The size of A matrix will be decided by the input {@DVectorBuilder} and the input List of {@UnknownParameter}s.
 * The input {@PartialID} array can have extra IDs, but all needed IDs must be included.
 *
 * @author otsuru
 * @since 2022/7/4
 */
public class MatrixAssembly {

    private final DVectorBuilder dVectorBuilder;
    private final ParallelizedMatrix a;
    private final RealVector d;
    private final RealVector obs;
    private final double normalizedVariance;
    private RealMatrix ata;
    private RealVector atd;

    /**
     * Compute A<sup>T</sup>A and A<sup>T</sup>d.
     * <p>
     * Note that A<sup>T</sup>d can be calculated as follows: <br>
     * A<sup>T</sup>d = v <br>
     * then <br>
     * v<sup>T</sup> = (A<sup>T</sup>d)<sup>T</sup>= d<sup>T</sup>A
     *
     * @param basicIDs
     * @param partialIDs
     * @param parameterList
     * @param weightingType
     */
    public MatrixAssembly(Path basicPath, Path partialPath, List<UnknownParameter> parameterList,
            WeightingHandler weightingHandler) throws IOException {
        this(basicPath, partialPath, parameterList, weightingHandler, false);
    }

    /**
     * Compute A<sup>T</sup>A and A<sup>T</sup>d.
     * <p>
     * Note that A<sup>T</sup>d can be calculated as follows: <br>
     * A<sup>T</sup>d = v <br>
     * then <br>
     * v<sup>T</sup> = (A<sup>T</sup>d)<sup>T</sup>= d<sup>T</sup>A
     *
     * <p>
     * Note: This method receives 'basicPath' and 'partialPath' instead of 'basicIDs' and 'partialIDs' so that
     * the memory can be released after this method is completed.
     *
     * @param basicPath
     * @param partialPath
     * @param parameterList
     * @param weightingType
     * @param fillEmptyPartial (boolean)
     */
    public MatrixAssembly(Path basicPath, Path partialPath, List<UnknownParameter> parameterList,
            WeightingHandler weightingHandler, boolean fillEmptyPartial) throws IOException {
        // read input files
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // set DVector
        System.err.println("Setting data for d vector");
        dVectorBuilder = new DVectorBuilder(basicIDs);

        // set weighting
        System.err.println("Setting weighting");
        RealVector[] weighting = weightingHandler.weightWaveforms(dVectorBuilder);

        // assemble A and d
        System.err.println("Assembling A matrix");
        AMatrixBuilder aMatrixBuilder = new AMatrixBuilder(parameterList, dVectorBuilder);
        a = aMatrixBuilder.buildWithWeight(partialIDs, weighting, fillEmptyPartial);
        System.err.println("Assembling d vector");
        d = dVectorBuilder.buildWithWeight(weighting);

        // compute variance
        obs = dVectorBuilder.fullObsVecWithWeight(weighting);
        normalizedVariance = MathAid.computeVariance(d, obs);
    }

    public double getNumIndependent() {
        return dVectorBuilder.getNumIndependent();
    }

    public DVectorBuilder getDVectorBuilder() {
        return dVectorBuilder;
    }

    public ParallelizedMatrix getA() {
        return a;
    }

    public RealVector getD() {
        return d;
    }

    public RealVector getObs() {
        return obs;
    }

    public double getNormalizedVariance() {
        return normalizedVariance;
    }

    public RealVector getAtd() {
        if (atd == null) {
            System.err.println("Assembling Atd");
            atd = a.preMultiply(d);
        }
        return atd;
    }

    public RealMatrix getAta() {
        if (ata == null) {
            System.err.println("Assembling AtA");
            ata = a.computeAtA();
        }
        return ata;
    }

    public static void writeDInfo(double numIndependent, double dNorm, double obsNorm, Path outputPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath, options))) {
            pw.println("# numIndependent dNorm obsNorm");
            pw.println(numIndependent + " " + dNorm + " " + obsNorm);
        }
    }

    public static double[] readDInfo(Path path) throws IOException {
        InformationFileReader reader = new InformationFileReader(path, true);
        return Arrays.stream(reader.next().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
    }

}
