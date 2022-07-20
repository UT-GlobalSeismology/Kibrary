package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Checkerboard test
 * <p>
 * Creates born-waveforms for checkerboard tests
 *
 * @author Kensuke Konishi
 * @since version 0.2.2
 * @version 2022/2/23 Moved & renamed from inversion.CheckerBoardTest to waveform.PseudoWaveformGenerator
 */
public class PseudoWaveformGenerator extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * Path of a {@link BasicIDFile} file (id part)
     */
    private Path basicIDPath;
    /**
     * Path of a {@link BasicIDFile} file (data part)
     */
    private Path basicPath;
    /**
     * Path of the partialID
     */
    private Path partialIDPath;
    /**
     * Path of the partial data
     */
    private Path partialPath;
    /**
     * Path of a {@link KnownParameterFile} file containing psudoM
     */
    private Path modelPath;

    /**
     * Which to set the psuedo waveform as. true: synthetic, false: observed
     */
    private boolean setPseudoAsSyn;
    private boolean noise;
    private double noisePower;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a partial ID file, must be defined");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be defined");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of a model file, must be defined");
            pw.println("#modelPath model.lst");
            pw.println("##(boolean) Whether to set the psuedo waveform as synthetic. If false, observed. (false)");
            pw.println("#setPseudoAsSyn ");
            pw.println("##(boolean) Whether to add noise (false)");
            pw.println("#noise ");
            pw.println("##(double) Noise power [ ] (1000)"); // TODO what is the unit?
            pw.println("#noisePower ");
        }
        System.err.println(outPath + " is created.");
    }

    public PseudoWaveformGenerator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        modelPath = property.parsePath("modelPath", null, true, workPath);

        setPseudoAsSyn = property.parseBoolean("setPseudoAsSyn", "false");
        noise = property.parseBoolean("noise", "false");
        if (noise) {
            noisePower = property.parseDouble("noisePower", "1000");
        }
    }

    @Override
    public void run() throws IOException {

        // read input
        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        List<UnknownParameter> params = KnownParameter.extractParameterList(knowns);

        // assemble matrices (they should not be weighted)
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, params, WeightingType.IDENTITY);
        ParallelizedMatrix a = assembler.getA();
        RealVector m = new ArrayRealVector(KnownParameter.extractValueArray(knowns), false);

        // calculate pseudo waveform
        RealVector pseudoD = a.operate(m);
        DVectorBuilder dVectorBuilder = assembler.getDVectorBuilder();
        RealVector pseudoObs = dVectorBuilder.fullSynVec().add(pseudoD);

        // output
        String dateStr = GadgetAid.getTemporaryString();
        Path pseudoIDPath = workPath.resolve(DatasetAid.generateOutputFileName("pseudoID", tag, dateStr, ".dat"));
        Path pseudoPath = workPath.resolve(DatasetAid.generateOutputFileName("pseudo", tag, dateStr, ".dat"));
        System.err.println("Outputting in " + pseudoIDPath + " , " + pseudoPath);
        output(pseudoObs, dVectorBuilder, pseudoIDPath, pseudoPath);
    }

    public void output(RealVector pseudoVec, DVectorBuilder dVectorBuilder, Path outIDPath, Path outDataPath) throws IOException {
        RealVector[] pseudoObsParts = dVectorBuilder.separate(pseudoVec);

        List<BasicID> basicIDs = new ArrayList<>();
        for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
            BasicID obsID = dVectorBuilder.getObsID(i);
            BasicID synID = dVectorBuilder.getSynID(i);
            if (setPseudoAsSyn) {
                // replace synthetic data with pseudo waveform
                basicIDs.add(obsID);
                basicIDs.add(synID.withData(pseudoObsParts[i].toArray()));
            } else {
                // replace observed data with pseudo waveform
                basicIDs.add(obsID.withData(pseudoObsParts[i].toArray()));
                basicIDs.add(synID);
            }
        }

        BasicIDFile.write(basicIDs, outIDPath, outDataPath);
    }

//    public RealVector computeRandomNoise() {
//        Dvector dVector = eq.getDVector();
//        RealVector[] noiseV = new RealVector[dVector.getNTimeWindow()];
//        int[] pts = dVector.getLengths();
//        double minFreq = 0.05;
//        double maxFreq = 0.01;
//        int np = 6;
//        ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * 0.05 * minFreq, 2 * Math.PI * 0.05 * maxFreq, np);
//        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
//            // System.out.println(i);
//            double[] u = RandomNoiseMaker.create(noisePower, 20, 3276.8, 512).getY();
//            u = bpf.applyFilter(u);
//            int startT = (int) dVector.getObsIDs()[i].getStartTime() * 20; // 6*4=20
//            noiseV[i] = new ArrayRealVector(pts[i]);
////			System.out.println(new ArrayRealVector(u).getLInfNorm());
//            for (int j = 0; j < pts[i]; j++)
//                noiseV[i].setEntry(j, u[j * 20 + startT]);
//        }
//        // pseudoD = pseudoD.add(randomD);
//        return dVector.combine(noiseV);
//    }

}
